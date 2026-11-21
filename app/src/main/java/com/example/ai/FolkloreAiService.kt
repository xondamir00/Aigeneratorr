package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.FolkloreCategory
import com.example.data.ShortScene
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "FolkloreAiService"

data class GeneratedShortBlueprint(
    val folkloreTheme: String,
    val youtubeTitle: String,
    val youtubeDescription: String,
    val hashtags: List<String>,
    val scenes: List<ShortScene>
)

class FolkloreAiService {

    private val scriptSchema = Schema.obj(
        properties = mapOf(
            "youtubeTitle" to Schema.string(
                description = "Viral American English YouTube Shorts title under 85 chars ending with #Shorts"
            ),
            "youtubeDescription" to Schema.string(
                description = "Chilling American English YouTube Shorts description (2-3 sentences + call to action + folklore hashtags)"
            ),
            "hashtags" to Schema.array(
                items = Schema.string(),
                description = "6 to 8 relevant hashtags starting with #, including #Shorts and #FolkloreHorror"
            ),
            "scenes" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "narrationLine" to Schema.string(
                            description = "Short, eerie American English spoken sentence (~7-10 words, spoken in ~3.5 seconds)"
                        ),
                        "subtitleText" to Schema.string(
                            description = "Punchy 3-6 word uppercase kinetic caption for on-screen horror subtitle"
                        ),
                        "imagePrompt" to Schema.string(
                            description = "Detailed atmospheric dark folklore horror vertical 9:16 visual prompt, no text in image"
                        ),
                        "cameraMotion" to Schema.enumeration(
                            values = listOf("ZOOM_IN", "ZOOM_OUT", "PAN_LEFT", "PAN_RIGHT"),
                            description = "Slow cinematic Ken Burns motion direction"
                        )
                    )
                ),
                description = "Exactly 4 sequential horror scenes totaling 15 seconds"
            )
        )
    )

    private val scriptModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = scriptSchema
                temperature = 0.95f
            }
        )
    }

    private val imageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
                imageConfig = ImageConfig.builder()
                    .setAspectRatio(AspectRatio.PORTRAIT_9x16)
                    .setImageSize(ImageSize.SIZE_1K)
                    .build()
            }
        )
    }

    suspend fun generateFolkloreScript(
        category: FolkloreCategory,
        customTopic: String = ""
    ): GeneratedShortBlueprint {
        val topicLine = if (customTopic.isNotBlank()) {
            "Specific legend focus: ${customTopic.trim()}"
        } else {
            "Folklore inspiration: ${category.promptSeedEn}"
        }

        val prompt = """
            You are an elite American horror YouTube Shorts director specializing in Mystical & Folklore Horror.
            Create a chilling, original 15-second vertical YouTube Short script in natural American English.

            Category: ${category.id}
            $topicLine

            Strict creative rules:
            1. Write exactly 4 sequential scenes (each ~3.75 seconds = 15 seconds total).
            2. Scene 1 must be an instant 3-second hook; Scene 4 must deliver a spine-chilling twist ending.
            3. Every narrationLine must be natural American English, 7 to 10 words max, dramatic, eerie, and easy to speak clearly in 3.5 seconds.
            4. Every subtitleText must be 3 to 6 punchy words matching the narration hook.
            5. Every imagePrompt must describe a photorealistic dark mystical folklore horror vertical 9:16 frame with moody cinematic lighting, deep obsidian shadows, crimson/amber lantern rim light, and ZERO text, letters, or watermarks inside the image.
            6. youtubeTitle must be catchy American English under 85 characters and include #Shorts.
            7. youtubeDescription must be atmospheric American English with a hook, brief folklore lore note, and CTA ("Subscribe before midnight...").
            8. hashtags must contain 6 to 8 tags starting with #, always including #Shorts, #FolkloreHorror, #HorrorStories, #ScaryShorts.
        """.trimIndent()

        try {
            val response = withTimeout(60_000L) {
                scriptModel.generateContent(prompt)
            }
            val rawJson = response.text
                ?: throw IllegalStateException("Gemini bo'sh javob qaytardi.")

            val json = JSONObject(rawJson)
            val title = json.getString("youtubeTitle").trim().take(100)
            val description = json.getString("youtubeDescription").trim().take(1500)

            val tagsJson = json.getJSONArray("hashtags")
            val hashtags = buildList {
                for (i in 0 until tagsJson.length()) {
                    val tag = tagsJson.getString(i).trim()
                    if (tag.isNotBlank()) {
                        add(if (tag.startsWith("#")) tag else "#$tag")
                    }
                }
            }.ifEmpty {
                listOf("#Shorts", "#FolkloreHorror", "#HorrorStories", "#ScaryShorts")
            }.take(15)

            val scenesJson = json.getJSONArray("scenes")
            val parsedScenes = mutableListOf<ShortScene>()
            for (i in 0 until scenesJson.length().coerceAtMost(4)) {
                val obj = scenesJson.getJSONObject(i)
                parsedScenes.add(
                    ShortScene(
                        narrationLine = obj.getString("narrationLine").trim(),
                        subtitleText = obj.getString("subtitleText").trim().uppercase(),
                        imagePrompt = obj.getString("imagePrompt").trim(),
                        cameraMotion = obj.optString("cameraMotion", "ZOOM_IN"),
                        durationMs = 3750L
                    )
                )
            }
            if (parsedScenes.isEmpty()) {
                throw IllegalStateException("Ssenariy sahnalari yaratilmadi.")
            }

            // Ensure exact 15_000ms distribution across generated scenes
            val perSceneMs = 15_000L / parsedScenes.size
            val timedScenes = parsedScenes.mapIndexed { idx, scene ->
                val dur = if (idx == parsedScenes.lastIndex) {
                    15_000L - perSceneMs * idx
                } else {
                    perSceneMs
                }
                scene.copy(durationMs = dur)
            }

            return GeneratedShortBlueprint(
                folkloreTheme = category.id,
                youtubeTitle = title,
                youtubeDescription = description,
                hashtags = hashtags,
                scenes = timedScenes
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Script generation timed out", e)
            throw IllegalStateException("Ssenariy yaratish 60 soniyadan oshib ketdi. Qayta urinib ko'ring.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Script generation failed", e)
            throw IllegalStateException(
                "AI ssenariy yaratishda xatolik: ${e.localizedMessage ?: "Noma'lum xato"}",
                e
            )
        }
    }

    suspend fun generateHorrorKeyframe(scenePrompt: String, sceneIndex: Int): Bitmap {
        val enrichedPrompt = """
            Dark mystical folklore horror cinematography, vertical 9:16 composition,
            $scenePrompt,
            dramatic chiaroscuro lighting, misty nocturnal atmosphere, subtle crimson and candlelit amber rim glow,
            35mm film aesthetic, ultra detailed, strictly no text, no subtitles, no logos, no watermarks.
        """.trimIndent()

        try {
            val response = withTimeout(60_000L) {
                imageModel.generateContent(enrichedPrompt)
            }
            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()

            return imagePart?.image
                ?: throw IllegalStateException("AI ${sceneIndex + 1}-kadr uchun rasm qaytarmadi.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image generation timed out for scene $sceneIndex", e)
            throw IllegalStateException(
                "${sceneIndex + 1}-kadrni yaratish 60 soniyadan oshdi. Qayta urinib ko'ring.",
                e
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image generation failed for scene $sceneIndex", e)
            throw IllegalStateException(
                "AI rasm yaratishda xatolik (${sceneIndex + 1}-kadr): ${e.localizedMessage ?: "Blaze rejasi yoki App Check holatini tekshiring."}",
                e
            )
        }
    }
}
