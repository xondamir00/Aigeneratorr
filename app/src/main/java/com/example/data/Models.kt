package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ShortStatus(val labelUz: String) {
    GENERATING("Yaratilmoqda"),
    READY("Tayyor"),
    UPLOADING("Yuklanmoqda"),
    SCHEDULED("7:00 ga qo'yilgan"),
    PUBLISHED("Joylangan"),
    FAILED("Xatolik");

    companion object {
        fun fromRaw(raw: String?): ShortStatus {
            return entries.firstOrNull { it.name == raw } ?: READY
        }
    }
}

enum class FolkloreCategory(
    val id: String,
    val labelUz: String,
    val promptSeedEn: String
) {
    FOREST_SPIRITS(
        id = "Forest Spirits",
        labelUz = "O'rmon Ruhlari",
        promptSeedEn = "Appalachian and ancient woodland spirits, whispering pines, red lanterns in mist"
    ),
    CURSED_VILLAGE(
        id = "Cursed Villages",
        labelUz = "La'natlangan Qishloq",
        promptSeedEn = "Abandoned foggy folklore village, bells ringing at midnight, ancient curse"
    ),
    MIDNIGHT_CROSSROADS(
        id = "Midnight Crossroads",
        labelUz = "Tungi Chorraha",
        promptSeedEn = "Crossroads folklore deals, shadowy hooded traveler, flickering oil lamp"
    ),
    ANCIENT_RELICS(
        id = "Ancient Relics",
        labelUz = "Qadimiy Tumor",
        promptSeedEn = "Cursed heirloom mirror, haunted amulet, forbidden attic folklore legend"
    ),
    WHISPERING_WATERS(
        id = "Whispering Waters",
        labelUz = "Sirli Suvlar",
        promptSeedEn = "Misty black river spirit, sunken bridge folklore, eerie ripples under full moon"
    );

    companion object {
        fun fromId(id: String?): FolkloreCategory {
            return entries.firstOrNull { it.id == id } ?: FOREST_SPIRITS
        }
    }
}

@Keep
@IgnoreExtraProperties
data class ShortScene(
    val narrationLine: String = "",
    val subtitleText: String = "",
    val imagePrompt: String = "",
    val cameraMotion: String = "ZOOM_IN",
    val durationMs: Long = 3750L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "narrationLine" to narrationLine,
        "subtitleText" to subtitleText,
        "imagePrompt" to imagePrompt,
        "cameraMotion" to cameraMotion,
        "durationMs" to durationMs
    )
}

@Keep
@IgnoreExtraProperties
data class HorrorShortProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val folkloreTheme: String = FolkloreCategory.FOREST_SPIRITS.id,
    val youtubeTitle: String = "",
    val youtubeDescription: String = "",
    val hashtags: List<String> = emptyList(),
    val scenes: List<ShortScene> = emptyList(),
    val status: String = ShortStatus.READY.name,
    val localVideoPath: String? = null,
    val localThumbPath: String? = null,
    val scheduledPublishAtIso: String? = null,
    val youtubeVideoId: String? = null,
    val errorMessage: String? = null,
    val durationSeconds: Long = 15L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedStatus: ShortStatus
        get() = ShortStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class AutomationSchedule(
    @DocumentId val id: String = "schedule",
    val userId: String = "",
    val autoEnabled: Boolean = true,
    val publishHour: Long = 19L,
    val publishMinute: Long = 0L,
    val defaultFolkloreCategory: String = FolkloreCategory.FOREST_SPIRITS.id,
    val autoUploadPrivacy: String = "private",
    val connectedChannelTitle: String? = null,
    val connectedChannelId: String? = null,
    val lastRunStatus: String? = null,
    val lastRunAtIso: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val formattedPublishTime: String
        get() = "%02d:%02d".format(publishHour.toInt(), publishMinute.toInt())
}

enum class PipelineStage(val stepIndex: Int, val labelUz: String) {
    IDLE(0, "Tayyor"),
    SCRIPT(1, "1/5 • Mistik ssenariy va SEO yozilmoqda (Gemini 3.8)..."),
    IMAGES(2, "2/5 • 9:16 AI horror kadrlar chizilmoqda..."),
    AUDIO(3, "3/5 • Amerika inglizcha dahshatli ovoz va fon musiqasi..."),
    VIDEO(4, "4/5 • 15 soniyalik MP4 video va subtitrlar render qilinmoqda..."),
    UPLOAD(5, "5/5 • YouTube'ga soat 7:00 ga joylanmoqda..."),
    DONE(6, "Tayyor! 15 soniyalik Horror Short yaratildi")
}

data class PipelineProgress(
    val stage: PipelineStage = PipelineStage.IDLE,
    val progressFraction: Float = 0f,
    val detailMessage: String = "",
    val activeShortId: String? = null,
    val errorMessage: String? = null
) {
    val running: Boolean
        get() = stage != PipelineStage.IDLE && stage != PipelineStage.DONE && errorMessage == null
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
