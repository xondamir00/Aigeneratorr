package com.example.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.FolkloreAiService
import com.example.data.AutomationSchedule
import com.example.data.FolkloreCategory
import com.example.data.HorrorShortProject
import com.example.data.PipelineProgress
import com.example.data.PipelineStage
import com.example.data.ShortStatus
import com.example.data.ShortsRepository
import com.example.data.UiState
import com.example.media.HorrorAudioEngine
import com.example.media.ShortsVideoRenderer
import com.example.worker.AutomationScheduler
import com.example.youtube.YouTubeAuthResult
import com.example.youtube.YouTubeUploaderService
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val TAG = "DuskLoreViewModel"

class DuskLoreViewModel(
    private val appContext: Context,
    private val repository: ShortsRepository,
    private val aiService: FolkloreAiService,
    private val audioEngine: HorrorAudioEngine,
    private val videoRenderer: ShortsVideoRenderer,
    private val youtubeService: YouTubeUploaderService
) : ViewModel() {

    val shortsState: StateFlow<UiState<List<HorrorShortProject>>> = repository.observeShorts()
        .map<List<HorrorShortProject>, UiState<List<HorrorShortProject>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            emit(UiState.Error(rootError.message ?: "Arxivni yuklashda xatolik yuz berdi."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val scheduleState: StateFlow<UiState<AutomationSchedule>> = repository.observeSchedule()
        .map<AutomationSchedule, UiState<AutomationSchedule>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            emit(UiState.Error(rootError.message ?: "Jadval sozlamalarini yuklashda xatolik."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private val _selectedCategory = MutableStateFlow(FolkloreCategory.FOREST_SPIRITS)
    val selectedCategory: StateFlow<FolkloreCategory> = _selectedCategory.asStateFlow()

    private val _customTopic = MutableStateFlow("")
    val customTopic: StateFlow<String> = _customTopic.asStateFlow()

    private val _autoUploadAfterGenerate = MutableStateFlow(true)
    val autoUploadAfterGenerate: StateFlow<Boolean> = _autoUploadAfterGenerate.asStateFlow()

    private val _pipelineProgress = MutableStateFlow(PipelineProgress())
    val pipelineProgress: StateFlow<PipelineProgress> = _pipelineProgress.asStateFlow()

    private val _youtubeAccessToken = MutableStateFlow<String?>(null)
    val youtubeAccessToken: StateFlow<String?> = _youtubeAccessToken.asStateFlow()

    private val _pendingConsentIntent = MutableStateFlow<PendingIntent?>(null)
    val pendingConsentIntent: StateFlow<PendingIntent?> = _pendingConsentIntent.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Float?>(null)
    val uploadProgress: StateFlow<Float?> = _uploadProgress.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private var activePipelineJob: Job? = null
    private var pendingUploadAfterConsent: (() -> Unit)? = null

    fun selectCategory(category: FolkloreCategory) {
        _selectedCategory.value = category
    }

    fun updateCustomTopic(topic: String) {
        _customTopic.value = topic.take(120)
    }

    fun setAutoUploadAfterGenerate(enabled: Boolean) {
        _autoUploadAfterGenerate.value = enabled
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun consumePendingConsentIntent() {
        _pendingConsentIntent.value = null
    }

    fun resetPipeline() {
        activePipelineJob?.cancel()
        _pipelineProgress.value = PipelineProgress()
    }

    fun connectYouTubeChannel(onConnected: (() -> Unit)? = null) {
        viewModelScope.launch {
            when (val res = youtubeService.requestYouTubeAuthorization()) {
                is YouTubeAuthResult.Authorized -> {
                    _youtubeAccessToken.value = res.accessToken
                    syncConnectedChannelInfo(res.accessToken)
                    onConnected?.invoke()
                }
                is YouTubeAuthResult.NeedsConsent -> {
                    pendingUploadAfterConsent = onConnected
                    _pendingConsentIntent.value = res.pendingIntent
                }
                is YouTubeAuthResult.Error -> {
                    _snackbarMessage.value = res.message
                }
            }
        }
    }

    fun onYouTubeConsentActivityResult(resultCode: Int, data: Intent?) {
        val parsed = youtubeService.extractTokenFromConsentResult(data)
        parsed.onSuccess { token ->
            _youtubeAccessToken.value = token
            viewModelScope.launch {
                syncConnectedChannelInfo(token)
                pendingUploadAfterConsent?.invoke()
                pendingUploadAfterConsent = null
            }
        }.onFailure { err ->
            pendingUploadAfterConsent = null
            _snackbarMessage.value = err.localizedMessage ?: "YouTube ruxsati bekor qilindi."
        }
    }

    private suspend fun syncConnectedChannelInfo(accessToken: String) {
        val channelRes = youtubeService.fetchConnectedChannel(accessToken)
        channelRes.onSuccess { channel ->
            repository.saveChannelAndRunStatus(
                connectedChannelTitle = channel.title,
                connectedChannelId = channel.channelId
            )
            _snackbarMessage.value = "YouTube kanali ulandi: ${channel.title}"
        }.onFailure { err ->
            _snackbarMessage.value = err.localizedMessage ?: "YouTube kanal ma'lumoti olinmadi."
        }
    }

    fun generateHorrorShort() {
        if (_pipelineProgress.value.running) return

        activePipelineJob = viewModelScope.launch {
            val category = _selectedCategory.value
            val topic = _customTopic.value.trim()
            var createdShortId: String? = null

            try {
                // Stage 1: Gemini 3.8 Flash structured Folklore Horror script & SEO
                _pipelineProgress.value = PipelineProgress(
                    stage = PipelineStage.SCRIPT,
                    progressFraction = 0.08f,
                    detailMessage = "Amerika inglizcha mistik xalq afsonasi va SEO yozilmoqda..."
                )
                val blueprint = aiService.generateFolkloreScript(category, topic)

                createdShortId = repository.createShort(
                    folkloreTheme = blueprint.folkloreTheme,
                    youtubeTitle = blueprint.youtubeTitle,
                    youtubeDescription = blueprint.youtubeDescription,
                    hashtags = blueprint.hashtags,
                    scenes = blueprint.scenes,
                    status = ShortStatus.GENERATING
                ).getOrThrow()

                // Stage 2: Gemini Nano Banana 2.1 9:16 Horror Keyframes
                val bitmaps = mutableListOf<android.graphics.Bitmap>()
                val totalScenes = blueprint.scenes.size
                blueprint.scenes.forEachIndexed { idx, scene ->
                    val frac = 0.16f + (idx.toFloat() / totalScenes.coerceAtLeast(1)) * 0.44f
                    _pipelineProgress.value = PipelineProgress(
                        stage = PipelineStage.IMAGES,
                        progressFraction = frac,
                        detailMessage = "9:16 AI kadr yaratilmoqda (${idx + 1}/$totalScenes)... 30-60 soniya olishi mumkin",
                        activeShortId = createdShortId
                    )
                    bitmaps.add(aiService.generateHorrorKeyframe(scene.imagePrompt, idx))
                }

                // Stage 3: US English Deep Horror TTS + Procedural Dark Folklore Soundscape
                _pipelineProgress.value = PipelineProgress(
                    stage = PipelineStage.AUDIO,
                    progressFraction = 0.65f,
                    detailMessage = "Amerika talaffuzida dahshatli ovoz va horror fon musiqasi yig'ilmoqda...",
                    activeShortId = createdShortId
                )
                val audioPcm = audioEngine.synthesizeHorrorSoundtrackPcm(blueprint.scenes)

                // Stage 4: On-device 15s 9:16 MP4 video + kinetic subtitles render
                _pipelineProgress.value = PipelineProgress(
                    stage = PipelineStage.VIDEO,
                    progressFraction = 0.74f,
                    detailMessage = "15 soniyalik 9:16 MP4 video va subtitrlar render qilinmoqda...",
                    activeShortId = createdShortId
                )
                val rendered = videoRenderer.renderShortMp4(
                    shortId = createdShortId,
                    scenes = blueprint.scenes,
                    sceneBitmaps = bitmaps,
                    audioPcm = audioPcm,
                    onRenderProgress = { renderFrac ->
                        _pipelineProgress.value = _pipelineProgress.value.copy(
                            progressFraction = 0.74f + renderFrac * 0.18f
                        )
                    }
                )
                bitmaps.forEach { if (!it.isRecycled) it.recycle() }

                repository.updateShortPipelineState(
                    shortId = createdShortId,
                    status = ShortStatus.READY,
                    localVideoPath = rendered.videoFile.absolutePath,
                    localThumbPath = rendered.thumbnailFile.absolutePath,
                    durationSeconds = rendered.durationSeconds
                )

                // Stage 5: Optional Auto-Upload to YouTube for 7:00 slot if connected
                val token = _youtubeAccessToken.value
                val schedule = (scheduleState.value as? UiState.Success)?.data
                val publishHour = (schedule?.publishHour ?: 19L).toInt()
                val publishMinute = (schedule?.publishMinute ?: 0L).toInt()

                if (_autoUploadAfterGenerate.value && !token.isNullOrBlank()) {
                    _pipelineProgress.value = PipelineProgress(
                        stage = PipelineStage.UPLOAD,
                        progressFraction = 0.93f,
                        detailMessage = "YouTube'ga soat %02d:%02d ga joylanmoqda...".format(publishHour, publishMinute),
                        activeShortId = createdShortId
                    )
                    val publishAtIso = youtubeService.computeNextPublishSlotIso(publishHour, publishMinute)
                    repository.updateShortPipelineState(
                        shortId = createdShortId,
                        status = ShortStatus.UPLOADING,
                        scheduledPublishAtIso = publishAtIso
                    )
                    val uploadRes = youtubeService.uploadShortToYouTube(
                        accessToken = token,
                        videoFile = rendered.videoFile,
                        title = blueprint.youtubeTitle,
                        description = blueprint.youtubeDescription,
                        hashtags = blueprint.hashtags,
                        scheduledPublishAtIso = publishAtIso,
                        onProgress = { upFrac ->
                            _pipelineProgress.value = _pipelineProgress.value.copy(
                                progressFraction = 0.93f + upFrac * 0.07f
                            )
                        }
                    )
                    uploadRes.onSuccess { outcome ->
                        repository.updateShortPipelineState(
                            shortId = createdShortId,
                            status = ShortStatus.SCHEDULED,
                            scheduledPublishAtIso = outcome.scheduledPublishAtIso,
                            youtubeVideoId = outcome.videoId
                        )
                        recordLastRun("Soat %02d:%02d ga YouTube'ga qo'yildi".format(publishHour, publishMinute))
                        _pipelineProgress.value = PipelineProgress(
                            stage = PipelineStage.DONE,
                            progressFraction = 1f,
                            detailMessage = "Soat %02d:%02d da YouTube'ga avtomatik chiqishga qo'yildi!".format(publishHour, publishMinute),
                            activeShortId = createdShortId
                        )
                    }.onFailure { err ->
                        val msg = err.localizedMessage ?: "YouTube yuklashda xato"
                        repository.updateShortPipelineState(
                            shortId = createdShortId,
                            status = ShortStatus.READY,
                            errorMessage = msg
                        )
                        _pipelineProgress.value = PipelineProgress(
                            stage = PipelineStage.DONE,
                            progressFraction = 1f,
                            detailMessage = "15s video tayyor! (YouTube yuklash xatosi: $msg)",
                            activeShortId = createdShortId
                        )
                    }
                } else {
                    _pipelineProgress.value = PipelineProgress(
                        stage = PipelineStage.DONE,
                        progressFraction = 1f,
                        detailMessage = "15 soniyalik Horror Short tayyor! Ko'rib chiqing va YouTube'ga joylang.",
                        activeShortId = createdShortId
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Horror Short pipeline failed", e)
                val msg = e.localizedMessage ?: "Video yaratishda xatolik yuz berdi."
                if (createdShortId != null) {
                    repository.updateShortPipelineState(
                        shortId = createdShortId,
                        status = ShortStatus.FAILED,
                        errorMessage = msg
                    )
                }
                _pipelineProgress.value = PipelineProgress(
                    stage = PipelineStage.IDLE,
                    progressFraction = 0f,
                    activeShortId = createdShortId,
                    errorMessage = msg
                )
            }
        }
    }

    fun updateShortSeo(
        shortId: String,
        title: String,
        description: String,
        hashtagsRaw: String
    ) {
        viewModelScope.launch {
            val tags = hashtagsRaw
                .split(Regex("[\\s,]+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { if (it.startsWith("#")) it else "#$it" }
                .distinct()
                .take(15)

            val res = repository.updateShortSeo(shortId, title, description, tags)
            res.onSuccess {
                _snackbarMessage.value = "YouTube nomi, tavsifi va heshteglari saqlandi."
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Saqlashda xatolik yuz berdi."
            }
        }
    }

    fun uploadShortToYouTube(project: HorrorShortProject, scheduleForSeven: Boolean) {
        val token = _youtubeAccessToken.value
        if (token.isNullOrBlank()) {
            connectYouTubeChannel(onConnected = {
                uploadShortToYouTube(project, scheduleForSeven)
            })
            return
        }

        val videoPath = project.localVideoPath
        if (videoPath.isNullOrBlank() || !File(videoPath).exists()) {
            _snackbarMessage.value = "MP4 video fayli topilmadi. Yangi Short yarating."
            return
        }

        viewModelScope.launch {
            val schedule = (scheduleState.value as? UiState.Success)?.data
            val publishHour = (schedule?.publishHour ?: 19L).toInt()
            val publishMinute = (schedule?.publishMinute ?: 0L).toInt()
            val publishAtIso = if (scheduleForSeven) {
                youtubeService.computeNextPublishSlotIso(publishHour, publishMinute)
            } else {
                null
            }

            _uploadProgress.value = 0.02f
            repository.updateShortPipelineState(
                shortId = project.id,
                status = ShortStatus.UPLOADING,
                scheduledPublishAtIso = publishAtIso
            )

            val res = youtubeService.uploadShortToYouTube(
                accessToken = token,
                videoFile = File(videoPath),
                title = project.youtubeTitle,
                description = project.youtubeDescription,
                hashtags = project.hashtags,
                scheduledPublishAtIso = publishAtIso,
                immediatePrivacy = "public",
                onProgress = { frac -> _uploadProgress.value = frac }
            )

            _uploadProgress.value = null
            res.onSuccess { outcome ->
                val nextStatus = if (scheduleForSeven) ShortStatus.SCHEDULED else ShortStatus.PUBLISHED
                repository.updateShortPipelineState(
                    shortId = project.id,
                    status = nextStatus,
                    scheduledPublishAtIso = outcome.scheduledPublishAtIso,
                    youtubeVideoId = outcome.videoId
                )
                val msg = if (scheduleForSeven) {
                    "YouTube'ga yuklandi! Soat %02d:%02d da avtomatik chiqadi.".format(publishHour, publishMinute)
                } else {
                    "YouTube Shorts'ga muvaffaqiyatli joylandi!"
                }
                recordLastRun(msg)
                _snackbarMessage.value = msg
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "YouTube'ga yuklashda xatolik."
                repository.updateShortPipelineState(
                    shortId = project.id,
                    status = ShortStatus.READY,
                    errorMessage = msg
                )
                _snackbarMessage.value = msg
            }
        }
    }

    fun deleteShort(project: HorrorShortProject) {
        viewModelScope.launch {
            val res = repository.deleteShort(project.id)
            res.onSuccess {
                project.localVideoPath?.let { runCatching { File(it).delete() } }
                project.localThumbPath?.let { runCatching { File(it).delete() } }
                _snackbarMessage.value = "Short arxivdan o'chirildi."
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "O'chirishda xatolik."
            }
        }
    }

    fun updateAutomationSchedule(
        autoEnabled: Boolean,
        publishHour: Long,
        publishMinute: Long,
        defaultCategory: FolkloreCategory
    ) {
        viewModelScope.launch {
            val res = repository.saveSchedulePreferences(
                autoEnabled = autoEnabled,
                publishHour = publishHour,
                publishMinute = publishMinute,
                defaultFolkloreCategory = defaultCategory.id
            )
            res.onSuccess {
                AutomationScheduler.syncDailySchedule(
                    context = appContext,
                    enabled = autoEnabled,
                    publishHour = publishHour.toInt(),
                    publishMinute = publishMinute.toInt()
                )
                val timeLabel = "%02d:%02d".format(publishHour.toInt(), publishMinute.toInt())
                _snackbarMessage.value = if (autoEnabled) {
                    "Kunlik avto-reja yoqildi: har kuni soat $timeLabel da!"
                } else {
                    "Kunlik avto-reja to'xtatildi."
                }
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Jadvalni saqlashda xatolik."
            }
        }
    }

    private suspend fun recordLastRun(statusText: String) {
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        repository.saveChannelAndRunStatus(
            lastRunStatus = statusText,
            lastRunAtIso = nowIso
        )
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appCtx = context.applicationContext
                DuskLoreViewModel(
                    appContext = appCtx,
                    repository = ShortsRepository(appCtx),
                    aiService = FolkloreAiService(),
                    audioEngine = HorrorAudioEngine(appCtx),
                    videoRenderer = ShortsVideoRenderer(appCtx),
                    youtubeService = YouTubeUploaderService(appCtx)
                )
            }
        }
    }
}
