package com.example.media

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.ShortScene
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private const val TAG = "HorrorAudioEngine"

/**
 * Generates an exact 15-second (44.1 kHz, mono, 16-bit PCM) horror soundtrack combining:
 * 1. American English (en-US) deep eerie narration synthesized via Google TextToSpeech
 * 2. Ghostly vocal echo/reverb DSP
 * 3. Procedural dark mystical folklore ambient soundscape (sub-bass drone, tritone shimmer,
 *    distant slow heartbeat, and misty twilight wind).
 */
class HorrorAudioEngine(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 44100
        const val DURATION_SECONDS = 15
        const val TOTAL_SAMPLES = SAMPLE_RATE * DURATION_SECONDS
    }

    suspend fun synthesizeHorrorSoundtrackPcm(scenes: List<ShortScene>): ShortArray =
        withContext(Dispatchers.IO) {
            val mixed = FloatArray(TOTAL_SAMPLES)

            // 1. Procedural dark mystical folklore horror ambient music (full 15.0 seconds)
            generateDarkFolkloreBed(mixed)

            // 2. Synthesize American English (en-US) eerie narration for each scene
            val sceneVoiceClips = synthesizeScenesWithUsTts(scenes)

            var cursorSample = (SAMPLE_RATE * 0.25).toInt()
            for ((index, scene) in scenes.withIndex()) {
                val sceneSamples = ((scene.durationMs / 1000.0) * SAMPLE_RATE).toInt()
                val clip = sceneVoiceClips.getOrNull(index)
                if (clip != null && clip.isNotEmpty()) {
                    // Apply ghostly dark echo + mix voice over ducked ambient bed
                    val processedVoice = applyGhostlyEcho(clip)
                    val maxLen = minOf(processedVoice.size, TOTAL_SAMPLES - cursorSample)
                    for (i in 0 until maxLen) {
                        val pos = cursorSample + i
                        // Gently duck ambient bed under active horror narration
                        mixed[pos] = mixed[pos] * 0.62f + processedVoice[i] * 0.92f
                    }
                }
                cursorSample = (cursorSample + sceneSamples).coerceAtMost(TOTAL_SAMPLES - 1)
            }

            // 3. Soft limiter + fade-in/fade-out mastering to 16-bit PCM
            val output = ShortArray(TOTAL_SAMPLES)
            val fadeInSamples = (SAMPLE_RATE * 0.35).toInt()
            val fadeOutSamples = (SAMPLE_RATE * 0.65).toInt()
            for (i in 0 until TOTAL_SAMPLES) {
                var sample = mixed[i]
                if (i < fadeInSamples) {
                    sample *= (i.toFloat() / fadeInSamples)
                } else if (i > TOTAL_SAMPLES - fadeOutSamples) {
                    sample *= ((TOTAL_SAMPLES - i).toFloat() / fadeOutSamples).coerceAtLeast(0f)
                }
                // Soft saturation limiter prevents harsh clipping
                val limited = kotlin.math.tanh(sample.toDouble() * 1.15).toFloat()
                output[i] = (limited * Short.MAX_VALUE * 0.94f).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
            output
        }

    private fun generateDarkFolkloreBed(buffer: FloatArray) {
        val rng = Random(42_666)
        var filteredNoise = 0.0
        val twoPi = 2.0 * PI

        for (i in buffer.indices) {
            val t = i.toDouble() / SAMPLE_RATE

            // Deep ominous sub-bass drone (A1 55Hz + eerie flat-fifth 77.78Hz + slow LFO swell)
            val swell = 0.55 + 0.45 * sin(twoPi * 0.14 * t)
            val droneLow = sin(twoPi * 55.0 * t) * 0.22 +
                sin(twoPi * 110.0 * t + sin(twoPi * 0.25 * t) * 0.6) * 0.09
            val tritone = sin(twoPi * 77.78 * t) * 0.08 * swell

            // High ghostly folklore glass-bell overtone near finale (last 4 seconds)
            val finaleFactor = ((t - 10.5) / 4.5).coerceIn(0.0, 1.0)
            val eerieShimmer = sin(twoPi * 466.16 * t) * 0.035 * finaleFactor *
                (0.5 + 0.5 * sin(twoPi * 2.8 * t))

            // Distant slow heartbeat thump every 1.25 seconds
            val beatPhase = t % 1.25
            val heartThump = when {
                beatPhase < 0.14 -> {
                    val env = sin(PI * (beatPhase / 0.14))
                    sin(twoPi * 48.0 * beatPhase) * env * 0.24
                }
                beatPhase in 0.20..0.32 -> {
                    val localT = beatPhase - 0.20
                    val env = sin(PI * (localT / 0.12))
                    sin(twoPi * 42.0 * localT) * env * 0.16
                }
                else -> 0.0
            }

            // Low-pass filtered twilight wind whisper
            val white = rng.nextDouble(-1.0, 1.0)
            filteredNoise = filteredNoise * 0.975 + white * 0.025
            val windLfo = 0.5 + 0.5 * sin(twoPi * 0.22 * t + 1.1)
            val wind = filteredNoise * 0.28 * windLfo

            buffer[i] = (droneLow + tritone + eerieShimmer + heartThump + wind).toFloat()
        }
    }

    private fun applyGhostlyEcho(input: FloatArray): FloatArray {
        val delay1 = (SAMPLE_RATE * 0.16).toInt()
        val delay2 = (SAMPLE_RATE * 0.34).toInt()
        val out = FloatArray(input.size + delay2)
        for (i in input.indices) {
            out[i] += input[i]
            if (i + delay1 < out.size) {
                out[i + delay1] += input[i] * 0.26f
            }
            if (i + delay2 < out.size) {
                out[i + delay2] += input[i] * 0.13f
            }
        }
        return out
    }

    private suspend fun synthesizeScenesWithUsTts(scenes: List<ShortScene>): List<FloatArray?> {
        var tts: TextToSpeech? = null
        val tempFiles = mutableListOf<File>()
        return try {
            tts = withTimeout(12_000L) { initUsEnglishTts() }
            if (tts == null) {
                Log.w(TAG, "US English TTS unavailable; proceeding with horror soundscape only.")
                return List(scenes.size) { null }
            }

            scenes.mapIndexed { idx, scene ->
                val line = scene.narrationLine.trim()
                if (line.isBlank()) {
                    null
                } else {
                    val wavFile = File(context.cacheDir, "scene_tts_${System.currentTimeMillis()}_$idx.wav")
                    tempFiles.add(wavFile)
                    val ok = withTimeout(15_000L) {
                        synthesizeSingleUtterance(tts, line, wavFile, "short_scene_$idx")
                    }
                    if (ok && wavFile.exists() && wavFile.length() > 44L) {
                        decodeWavToResampledFloat(wavFile, SAMPLE_RATE)
                    } else {
                        null
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "TTS synthesis encountered an error; continuing with horror ambient bed.", e)
            List(scenes.size) { null }
        } finally {
            tts?.stop()
            tts?.shutdown()
            tempFiles.forEach { runCatching { it.delete() } }
        }
    }

    private suspend fun initUsEnglishTts(): TextToSpeech? =
        suspendCancellableCoroutine { cont ->
            var instance: TextToSpeech? = null
            instance = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = instance
                    if (engine != null) {
                        engine.language = Locale.US
                        // Prefer a deep/male American English voice when installed
                        runCatching {
                            val usVoices = engine.voices
                                ?.filter { it.locale.language == "en" && it.locale.country == "US" }
                                .orEmpty()
                            val preferred = usVoices.firstOrNull {
                                it.name.contains("iom", ignoreCase = true) ||
                                    it.name.contains("tpd", ignoreCase = true) ||
                                    it.name.contains("male", ignoreCase = true)
                            } ?: usVoices.firstOrNull { !it.isNetworkConnectionRequired }
                                ?: usVoices.firstOrNull()
                            if (preferred != null) {
                                engine.voice = preferred
                            }
                        }
                        // Low eerie horror narrator pitch & deliberate pace
                        engine.setPitch(0.68f)
                        engine.setSpeechRate(0.86f)
                    }
                    if (cont.isActive) cont.resume(engine)
                } else {
                    Log.w(TAG, "TextToSpeech init failed with status=$status")
                    if (cont.isActive) cont.resume(null)
                }
            }
            cont.invokeOnCancellation {
                runCatching {
                    instance?.stop()
                    instance?.shutdown()
                }
            }
        }

    private suspend fun synthesizeSingleUtterance(
        tts: TextToSpeech,
        text: String,
        outputFile: File,
        utteranceId: String
    ): Boolean = suspendCancellableCoroutine { cont ->
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId && cont.isActive) cont.resume(true)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId && cont.isActive) cont.resume(false)
            }
            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId && cont.isActive) cont.resume(false)
            }
        })
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val result = tts.synthesizeToFile(text, params, outputFile, utteranceId)
        if (result != TextToSpeech.SUCCESS && cont.isActive) {
            cont.resume(false)
        }
    }

    private fun decodeWavToResampledFloat(wavFile: File, targetRate: Int): FloatArray? {
        val bytes = wavFile.readBytes()
        if (bytes.size < 44) return null

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val numChannels = buf.getShort(22).toInt().coerceAtLeast(1)
        val srcSampleRate = buf.getInt(24).coerceAtLeast(8000)
        val bitsPerSample = buf.getShort(34).toInt().coerceAtLeast(16)

        // Locate "data" subchunk dynamically
        var offset = 12
        var dataOffset = 44
        var dataSize = bytes.size - 44
        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = buf.getInt(offset + 4)
            if (chunkId == "data") {
                dataOffset = offset + 8
                dataSize = chunkSize.coerceAtMost(bytes.size - dataOffset)
                break
            }
            offset += 8 + chunkSize.coerceAtLeast(0)
        }

        if (bitsPerSample != 16 || dataSize <= 2) return null

        val frameCount = dataSize / (2 * numChannels)
        val monoSrc = FloatArray(frameCount)
        val dataBuf = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frameCount) {
            var sum = 0f
            for (ch in 0 until numChannels) {
                sum += dataBuf.short / 32768f
            }
            monoSrc[i] = sum / numChannels
        }

        if (srcSampleRate == targetRate) return monoSrc

        val ratio = srcSampleRate.toDouble() / targetRate.toDouble()
        val outCount = (frameCount / ratio).toInt().coerceAtLeast(1)
        val resampled = FloatArray(outCount)
        for (i in 0 until outCount) {
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, frameCount - 1)
            val idx1 = (idx0 + 1).coerceIn(0, frameCount - 1)
            val frac = (srcPos - idx0).toFloat()
            resampled[i] = monoSrc[idx0] * (1f - frac) + monoSrc[idx1] * frac
        }
        return resampled
    }
}
