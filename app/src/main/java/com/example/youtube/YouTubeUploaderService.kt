package com.example.youtube

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

private const val TAG = "YouTubeUploader"

sealed interface YouTubeAuthResult {
    data class Authorized(val accessToken: String) : YouTubeAuthResult
    data class NeedsConsent(val pendingIntent: PendingIntent) : YouTubeAuthResult
    data class Error(val message: String) : YouTubeAuthResult
}

data class YouTubeChannelInfo(
    val channelId: String,
    val title: String
)

data class YouTubeUploadOutcome(
    val videoId: String,
    val scheduledPublishAtIso: String?
)

class YouTubeUploaderService(private val context: Context) {

    companion object {
        private const val SCOPE_YOUTUBE_UPLOAD = "https://www.googleapis.com/auth/youtube.upload"
        private const val SCOPE_YOUTUBE_READONLY = "https://www.googleapis.com/auth/youtube.readonly"
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .build()
    }

    suspend fun requestYouTubeAuthorization(): YouTubeAuthResult {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(
                    listOf(
                        Scope(SCOPE_YOUTUBE_UPLOAD),
                        Scope(SCOPE_YOUTUBE_READONLY)
                    )
                )
                .build()
            val result = Identity.getAuthorizationClient(context)
                .authorize(request)
                .await()

            when {
                result.hasResolution() && result.pendingIntent != null -> {
                    YouTubeAuthResult.NeedsConsent(result.pendingIntent!!)
                }
                !result.accessToken.isNullOrBlank() -> {
                    YouTubeAuthResult.Authorized(result.accessToken!!)
                }
                else -> {
                    YouTubeAuthResult.Error("YouTube access token olinmadi. Qayta urinib ko'ring.")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "YouTube OAuth authorization failed", e)
            YouTubeAuthResult.Error(
                "YouTube ruxsatini olishda xatolik: ${e.localizedMessage ?: "Google Play Services tekshiring"}"
            )
        }
    }

    fun extractTokenFromConsentResult(data: Intent?): Result<String> {
        return try {
            val authResult = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            val token = authResult.accessToken
            if (!token.isNullOrBlank()) {
                Result.success(token)
            } else {
                Result.failure(IllegalStateException("YouTube ruxsati tasdiqlanmadi."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse YouTube consent result", e)
            Result.failure(e)
        }
    }

    suspend fun fetchConnectedChannel(accessToken: String): Result<YouTubeChannelInfo> =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("https://www.googleapis.com/youtube/v3/channels?part=snippet&mine=true")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException(formatYouTubeApiError(resp.code, bodyStr))
                        )
                    }
                    val root = JSONObject(bodyStr)
                    val items = root.optJSONArray("items")
                    if (items == null || items.length() == 0) {
                        return@withContext Result.failure(
                            IllegalStateException("Ushbu Google hisobida YouTube kanal topilmadi. Avval YouTube'da kanal yarating.")
                        )
                    }
                    val first = items.getJSONObject(0)
                    val id = first.optString("id")
                    val title = first.optJSONObject("snippet")?.optString("title") ?: "YouTube Kanal"
                    Result.success(YouTubeChannelInfo(channelId = id, title = title))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch YouTube channel", e)
                Result.failure(e)
            }
        }

    /**
     * Computes the next occurrence of [publishHour]:[publishMinute] in local time,
     * advancing to tomorrow if fewer than 10 minutes remain, and formats it as UTC ISO-8601
     * for YouTube Data API v3 `status.publishAt`.
     */
    fun computeNextPublishSlotIso(publishHour: Int, publishMinute: Int = 0): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, publishHour.coerceIn(0, 23))
            set(Calendar.MINUTE, publishMinute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // YouTube requires publishAt to be in the future; enforce at least 10 minutes buffer
        if (target.timeInMillis <= now.timeInMillis + 10 * 60 * 1000L) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return isoFormat.format(target.time)
    }

    /**
     * Performs a genuine YouTube Data API v3 Resumable Upload for a 15-second Short MP4.
     * When [scheduledPublishAtIso] is non-null, sets privacyStatus="private" + publishAt,
     * instructing YouTube servers to publish automatically at the exact 7:00 slot.
     */
    suspend fun uploadShortToYouTube(
        accessToken: String,
        videoFile: File,
        title: String,
        description: String,
        hashtags: List<String>,
        scheduledPublishAtIso: String?,
        immediatePrivacy: String = "public",
        onProgress: (Float) -> Unit = {}
    ): Result<YouTubeUploadOutcome> = withContext(Dispatchers.IO) {
        try {
            if (!videoFile.exists() || videoFile.length() <= 0L) {
                return@withContext Result.failure(
                    IllegalStateException("Video fayl topilmadi. Avval Short videoni yarating.")
                )
            }

            val cleanTags = hashtags
                .map { it.trim().removePrefix("#") }
                .filter { it.isNotBlank() }
                .distinct()
                .take(15)

            val hashtagFooter = hashtags
                .map { if (it.startsWith("#")) it else "#$it" }
                .joinToString(" ")

            val fullDescription = buildString {
                append(description.trim())
                if (hashtagFooter.isNotBlank() && !description.contains("#Shorts", ignoreCase = true)) {
                    append("\n\n")
                    append(hashtagFooter)
                }
            }.take(4900)

            val safeTitle = title.trim().let {
                if (it.contains("#Shorts", ignoreCase = true)) it else "$it #Shorts"
            }.take(100)

            val snippet = JSONObject().apply {
                put("title", safeTitle)
                put("description", fullDescription)
                put("tags", JSONArray(cleanTags))
                put("categoryId", "24") // Entertainment
                put("defaultLanguage", "en-US")
                put("defaultAudioLanguage", "en-US")
            }

            val status = JSONObject().apply {
                put("selfDeclaredMadeForKids", false)
                if (!scheduledPublishAtIso.isNullOrBlank()) {
                    // YouTube requires privacyStatus="private" when scheduling with publishAt
                    put("privacyStatus", "private")
                    put("publishAt", scheduledPublishAtIso)
                } else {
                    put(
                        "privacyStatus",
                        if (immediatePrivacy in listOf("public", "unlisted", "private")) {
                            immediatePrivacy
                        } else {
                            "public"
                        }
                    )
                }
            }

            val metadataJson = JSONObject().apply {
                put("snippet", snippet)
                put("status", status)
            }

            // Step 1: Initiate resumable upload session
            onProgress(0.05f)
            val initRequest = Request.Builder()
                .url("https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("X-Upload-Content-Length", videoFile.length().toString())
                .addHeader("X-Upload-Content-Type", "video/mp4")
                .post(metadataJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()

            val uploadUrl = httpClient.newCall(initRequest).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException(formatYouTubeApiError(resp.code, respBody))
                    )
                }
                resp.header("Location")
                    ?: return@withContext Result.failure(
                        IllegalStateException("YouTube resumable upload manzili (Location) qaytarmadi.")
                    )
            }

            // Step 2: Stream MP4 bytes to resumable session URL with live progress
            val totalBytes = videoFile.length().coerceAtLeast(1L)
            val progressBody = object : RequestBody() {
                override fun contentType() = "video/mp4".toMediaType()
                override fun contentLength() = totalBytes
                override fun writeTo(sink: BufferedSink) {
                    val buffer = ByteArray(64 * 1024)
                    var uploaded = 0L
                    FileInputStream(videoFile).use { fis ->
                        while (true) {
                            val read = fis.read(buffer)
                            if (read == -1) break
                            sink.write(buffer, 0, read)
                            uploaded += read
                            val frac = 0.08f + (uploaded.toFloat() / totalBytes.toFloat()) * 0.88f
                            onProgress(frac.coerceIn(0.08f, 0.96f))
                        }
                    }
                }
            }

            val putRequest = Request.Builder()
                .url(uploadUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .put(progressBody)
                .build()

            httpClient.newCall(putRequest).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException(formatYouTubeApiError(resp.code, respBody))
                    )
                }
                val json = JSONObject(respBody)
                val videoId = json.optString("id")
                if (videoId.isBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException("YouTube yuklash javobida video ID topilmadi.")
                    )
                }
                onProgress(1.0f)
                Result.success(
                    YouTubeUploadOutcome(
                        videoId = videoId,
                        scheduledPublishAtIso = scheduledPublishAtIso
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "YouTube resumable upload failed", e)
            Result.failure(e)
        }
    }

    private fun formatYouTubeApiError(httpCode: Int, rawBody: String): String {
        val apiMessage = runCatching {
            JSONObject(rawBody).optJSONObject("error")?.optString("message")
        }.getOrNull().orEmpty()

        return when {
            httpCode == 401 -> "YouTube sessiyasi eskirgan (401). Qaytadan YouTube hisobini ulang."
            httpCode == 403 && apiMessage.contains("not been used", ignoreCase = true) ->
                "Google Cloud loyihasida 'YouTube Data API v3' yoqilmagan (403). Console'da YouTube Data API v3 ni yoqing yoki pastdagi MP4 Ulashish tugmasidan foydalaning."
            httpCode == 403 && apiMessage.contains("quota", ignoreCase = true) ->
                "YouTube Data API kunlik kvotasi tugadi (403). Ertaga yangilanadi."
            apiMessage.isNotBlank() -> "YouTube API xatoligi ($httpCode): $apiMessage"
            else -> "YouTube yuklash xatoligi (HTTP $httpCode)."
        }
    }
}
