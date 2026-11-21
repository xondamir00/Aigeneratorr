package com.example.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.R
import com.example.ai.FolkloreAiService
import com.example.data.FolkloreCategory
import com.example.data.ShortStatus
import com.example.data.ShortsRepository
import com.example.media.HorrorAudioEngine
import com.example.media.ShortsVideoRenderer
import com.example.youtube.YouTubeAuthResult
import com.example.youtube.YouTubeUploaderService
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val TAG = "DailyShortWorker"
private const val CHANNEL_ID = "dusklore_automation_channel"

class DailyShortWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (Firebase.auth.currentUser == null) {
            Log.w(TAG, "Skipping daily Short run: user not signed in.")
            return Result.success()
        }

        val repository = ShortsRepository(applicationContext)
        val aiService = FolkloreAiService()
        val audioEngine = HorrorAudioEngine(applicationContext)
        val videoRenderer = ShortsVideoRenderer(applicationContext)
        val youtubeService = YouTubeUploaderService(applicationContext)

        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        return try {
            val schedule = repository.getScheduleOnce().getOrNull()
            if (schedule != null && !schedule.autoEnabled) {
                return Result.success()
            }
            val publishHour = (schedule?.publishHour ?: 19L).toInt()
            val publishMinute = (schedule?.publishMinute ?: 0L).toInt()
            val category = FolkloreCategory.fromId(schedule?.defaultFolkloreCategory)

            // 1. Generate 15s American English Mystical Folklore Script + SEO
            val blueprint = aiService.generateFolkloreScript(category)
            val shortId = repository.createShort(
                folkloreTheme = blueprint.folkloreTheme,
                youtubeTitle = blueprint.youtubeTitle,
                youtubeDescription = blueprint.youtubeDescription,
                hashtags = blueprint.hashtags,
                scenes = blueprint.scenes,
                status = ShortStatus.GENERATING
            ).getOrThrow()

            // 2. Generate 9:16 AI Horror Keyframes
            val bitmaps = blueprint.scenes.mapIndexed { idx, scene ->
                aiService.generateHorrorKeyframe(scene.imagePrompt, idx)
            }

            // 3. Synthesize US English Horror Voice + Dark Folklore Soundscape
            val audioPcm = audioEngine.synthesizeHorrorSoundtrackPcm(blueprint.scenes)

            // 4. Render 15s 9:16 MP4 with kinetic captions
            val rendered = videoRenderer.renderShortMp4(
                shortId = shortId,
                scenes = blueprint.scenes,
                sceneBitmaps = bitmaps,
                audioPcm = audioPcm
            )
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }

            repository.updateShortPipelineState(
                shortId = shortId,
                status = ShortStatus.READY,
                localVideoPath = rendered.videoFile.absolutePath,
                localThumbPath = rendered.thumbnailFile.absolutePath,
                durationSeconds = rendered.durationSeconds
            )

            // 5. Silently upload to YouTube scheduled for exact 7:00 publishAt if OAuth is granted
            when (val authRes = youtubeService.requestYouTubeAuthorization()) {
                is YouTubeAuthResult.Authorized -> {
                    val publishAtIso = youtubeService.computeNextPublishSlotIso(publishHour, publishMinute)
                    repository.updateShortPipelineState(
                        shortId = shortId,
                        status = ShortStatus.UPLOADING,
                        scheduledPublishAtIso = publishAtIso
                    )
                    val uploadRes = youtubeService.uploadShortToYouTube(
                        accessToken = authRes.accessToken,
                        videoFile = rendered.videoFile,
                        title = blueprint.youtubeTitle,
                        description = blueprint.youtubeDescription,
                        hashtags = blueprint.hashtags,
                        scheduledPublishAtIso = publishAtIso
                    )
                    uploadRes.onSuccess { outcome ->
                        repository.updateShortPipelineState(
                            shortId = shortId,
                            status = ShortStatus.SCHEDULED,
                            scheduledPublishAtIso = outcome.scheduledPublishAtIso,
                            youtubeVideoId = outcome.videoId
                        )
                        val summary = "Soat %02d:%02d ga YouTube'ga joylandi (${blueprint.youtubeTitle})".format(
                            publishHour,
                            publishMinute
                        )
                        repository.saveChannelAndRunStatus(lastRunStatus = summary, lastRunAtIso = nowIso)
                        postNotification("DuskLore Shorts • 7:00 Rejasi", summary)
                    }.onFailure { err ->
                        val msg = err.localizedMessage ?: "YouTube yuklashda xatolik"
                        repository.updateShortPipelineState(
                            shortId = shortId,
                            status = ShortStatus.READY,
                            errorMessage = msg
                        )
                        repository.saveChannelAndRunStatus(
                            lastRunStatus = "Video tayyor, YouTube xatosi: $msg",
                            lastRunAtIso = nowIso
                        )
                        postNotification("DuskLore Shorts • Video tayyor", "YouTube'ga qo'lda yuklang: $msg")
                    }
                }
                else -> {
                    val summary = "15s Horror Short tayyor! YouTube ruxsatini tasdiqlab soat %02d:%02d ga yuklang.".format(
                        publishHour,
                        publishMinute
                    )
                    repository.saveChannelAndRunStatus(lastRunStatus = summary, lastRunAtIso = nowIso)
                    postNotification("DuskLore Shorts • Video tayyor", summary)
                }
            }

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Daily automated Short generation failed", e)
            repository.saveChannelAndRunStatus(
                lastRunStatus = "Xatolik: ${e.localizedMessage ?: "Noma'lum xato"}",
                lastRunAtIso = nowIso
            )
            Result.retry()
        }
    }

    private fun postNotification(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DuskLore Avtomatlashtirish",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(7001, notif)
        }
    }
}
