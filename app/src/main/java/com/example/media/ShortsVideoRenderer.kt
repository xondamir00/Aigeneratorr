package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.example.data.ShortScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

private const val TAG = "ShortsVideoRenderer"

data class RenderedShortFiles(
    val videoFile: File,
    val thumbnailFile: File,
    val durationSeconds: Long
)

/**
 * Hardware-accelerated on-device 9:16 YouTube Shorts MP4 renderer (720x1280, 24fps, 15.0s).
 * Draws Ken Burns motion over Gemini-generated folklore keyframes, applies atmospheric dark
 * vignette + eerie flicker, burns in kinetic horror subtitles, and muxes AAC horror soundtrack.
 */
class ShortsVideoRenderer(private val context: Context) {

    companion object {
        private const val WIDTH = 720
        private const val HEIGHT = 1280
        private const val FPS = 24
        private const val DURATION_SECONDS = 15
        private const val TOTAL_FRAMES = FPS * DURATION_SECONDS
        private const val VIDEO_BITRATE = 3_500_000
        private const val AUDIO_BITRATE = 128_000
        private const val TIMEOUT_US = 10_000L
    }

    suspend fun renderShortMp4(
        shortId: String,
        scenes: List<ShortScene>,
        sceneBitmaps: List<Bitmap>,
        audioPcm: ShortArray,
        onRenderProgress: (Float) -> Unit = {}
    ): RenderedShortFiles = withContext(Dispatchers.Default) {
        require(scenes.isNotEmpty() && sceneBitmaps.isNotEmpty()) {
            "Video render uchun kamida 1 ta sahna va rasm kerak."
        }

        val outDir = File(context.filesDir, "shorts").apply { mkdirs() }
        val videoFile = File(outDir, "dusklore_$shortId.mp4")
        val thumbFile = File(outDir, "dusklore_${shortId}_thumb.jpg")
        if (videoFile.exists()) videoFile.delete()

        // Save vertical poster thumbnail for Archive grid
        FileOutputStream(thumbFile).use { fos ->
            sceneBitmaps.first().compress(Bitmap.CompressFormat.JPEG, 88, fos)
        }

        val scaledBitmaps = sceneBitmaps.map { bmp ->
            scaleCenterCrop(bmp, (WIDTH * 1.16f).toInt(), (HEIGHT * 1.16f).toInt())
        }

        val muxer = MediaMuxer(videoFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoEncoder: MediaCodec? = null
        var audioEncoder: MediaCodec? = null

        try {
            // 1. Configure H.264 video encoder with Surface input
            val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            val inputSurface = videoEncoder.createInputSurface()
            videoEncoder.start()

            // 2. Configure AAC audio encoder
            val audioFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                HorrorAudioEngine.SAMPLE_RATE,
                1
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            // Encode both tracks cleanly into buffered packets, then mux once both formats are ready
            val encodedVideoPackets = mutableListOf<EncodedPacket>()
            val encodedAudioPackets = mutableListOf<EncodedPacket>()
            var resolvedVideoFormat: MediaFormat? = null
            var resolvedAudioFormat: MediaFormat? = null

            val bufferInfo = MediaCodec.BufferInfo()
            val paints = createRenderPaints()

            // Pass A: Render 360 frames (15s @ 24fps) to video encoder surface
            val framesPerScene = TOTAL_FRAMES / scenes.size
            for (frameIdx in 0 until TOTAL_FRAMES) {
                val sceneIdx = (frameIdx / framesPerScene).coerceIn(0, scenes.lastIndex)
                val localFrame = frameIdx - sceneIdx * framesPerScene
                val localProgress = (localFrame.toFloat() / framesPerScene.coerceAtLeast(1)).coerceIn(0f, 1f)

                val currentBmp = scaledBitmaps[sceneIdx.coerceIn(0, scaledBitmaps.lastIndex)]
                val prevBmp = if (sceneIdx > 0 && localFrame < 10) {
                    scaledBitmaps[(sceneIdx - 1).coerceIn(0, scaledBitmaps.lastIndex)]
                } else {
                    null
                }

                val canvas = inputSurface.lockCanvas(null)
                try {
                    drawHorrorFrame(
                        canvas = canvas,
                        bitmap = currentBmp,
                        prevBitmap = prevBmp,
                        crossfadeAlpha = if (prevBmp != null) (localFrame / 10f).coerceIn(0f, 1f) else 1f,
                        scene = scenes[sceneIdx],
                        sceneProgress = localProgress,
                        globalFrame = frameIdx,
                        paints = paints
                    )
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }

                drainEncoderToPackets(
                    encoder = videoEncoder,
                    bufferInfo = bufferInfo,
                    packets = encodedVideoPackets,
                    onFormatChanged = { resolvedVideoFormat = it },
                    endOfStream = false
                )

                if (frameIdx % 12 == 0) {
                    onRenderProgress(0.1f + (frameIdx.toFloat() / TOTAL_FRAMES) * 0.65f)
                }
            }

            videoEncoder.signalEndOfInputStream()
            drainEncoderToPackets(
                encoder = videoEncoder,
                bufferInfo = bufferInfo,
                packets = encodedVideoPackets,
                onFormatChanged = { resolvedVideoFormat = it },
                endOfStream = true
            )
            onRenderProgress(0.80f)

            // Pass B: Encode 15.0s PCM horror audio to AAC packets
            val samplesPerChunk = 1024
            var sampleCursor = 0
            while (sampleCursor < audioPcm.size) {
                val inIdx = audioEncoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIdx >= 0) {
                    val inBuf = audioEncoder.getInputBuffer(inIdx)!!
                    inBuf.clear()
                    val count = minOf(samplesPerChunk, audioPcm.size - sampleCursor, inBuf.remaining() / 2)
                    val bb = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
                    for (j in 0 until count) {
                        bb.putShort(audioPcm[sampleCursor + j])
                    }
                    inBuf.put(bb.array())
                    val ptsUs = (sampleCursor.toLong() * 1_000_000L) / HorrorAudioEngine.SAMPLE_RATE
                    sampleCursor += count
                    val flags = if (sampleCursor >= audioPcm.size) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                    audioEncoder.queueInputBuffer(inIdx, 0, count * 2, ptsUs, flags)
                }
                drainEncoderToPackets(
                    encoder = audioEncoder,
                    bufferInfo = bufferInfo,
                    packets = encodedAudioPackets,
                    onFormatChanged = { resolvedAudioFormat = it },
                    endOfStream = false
                )
            }
            drainEncoderToPackets(
                encoder = audioEncoder,
                bufferInfo = bufferInfo,
                packets = encodedAudioPackets,
                onFormatChanged = { resolvedAudioFormat = it },
                endOfStream = true
            )
            onRenderProgress(0.92f)

            // Pass C: Mux H.264 video + AAC audio into final MP4
            val vFmt = resolvedVideoFormat
                ?: throw IllegalStateException("Video kodek formati aniqlanmadi.")
            val aFmt = resolvedAudioFormat
                ?: throw IllegalStateException("Audio kodek formati aniqlanmadi.")

            val videoTrackIdx = muxer.addTrack(vFmt)
            val audioTrackIdx = muxer.addTrack(aFmt)
            muxer.start()

            // Assign exact monotonic timestamps (1_000_000L / 24 per frame) to video samples
            val frameStepUs = 1_000_000L / FPS
            var vFrameCount = 0L
            for (pkt in encodedVideoPackets) {
                val info = MediaCodec.BufferInfo().apply {
                    offset = 0
                    size = pkt.data.size
                    flags = pkt.flags
                    presentationTimeUs = vFrameCount * frameStepUs
                }
                if ((pkt.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    vFrameCount++
                }
                muxer.writeSampleData(videoTrackIdx, ByteBuffer.wrap(pkt.data), info)
            }

            for (pkt in encodedAudioPackets) {
                val info = MediaCodec.BufferInfo().apply {
                    offset = 0
                    size = pkt.data.size
                    flags = pkt.flags
                    presentationTimeUs = pkt.ptsUs
                }
                muxer.writeSampleData(audioTrackIdx, ByteBuffer.wrap(pkt.data), info)
            }

            muxer.stop()
            onRenderProgress(1.0f)

            RenderedShortFiles(
                videoFile = videoFile,
                thumbnailFile = thumbFile,
                durationSeconds = DURATION_SECONDS.toLong()
            )
        } catch (e: Exception) {
            Log.e(TAG, "MP4 render failed", e)
            throw IllegalStateException("15s MP4 video yig'ishda xatolik: ${e.localizedMessage}", e)
        } finally {
            runCatching { videoEncoder?.stop() }
            runCatching { videoEncoder?.release() }
            runCatching { audioEncoder?.stop() }
            runCatching { audioEncoder?.release() }
            runCatching { muxer.release() }
            scaledBitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    private data class EncodedPacket(
        val data: ByteArray,
        val ptsUs: Long,
        val flags: Int
    )

    private fun drainEncoderToPackets(
        encoder: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        packets: MutableList<EncodedPacket>,
        onFormatChanged: (MediaFormat) -> Unit,
        endOfStream: Boolean
    ) {
        var idleCycles = 0
        while (true) {
            val outIdx = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                    idleCycles++
                    if (idleCycles > 60) break
                }
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    onFormatChanged(encoder.outputFormat)
                }
                outIdx >= 0 -> {
                    val outBuf = encoder.getOutputBuffer(outIdx)
                    if (outBuf != null && bufferInfo.size > 0 &&
                        (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                    ) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        val bytes = ByteArray(bufferInfo.size)
                        outBuf.get(bytes)
                        packets.add(EncodedPacket(bytes, bufferInfo.presentationTimeUs, bufferInfo.flags))
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }
    }

    private class RenderPaints(
        val bitmapPaint: Paint,
        val vignettePaint: Paint,
        val bottomGradPaint: Paint,
        val flickerPaint: Paint,
        val captionStrokePaint: Paint,
        val captionFillPaint: Paint,
        val captionAccentPaint: Paint,
        val brandTagPaint: Paint
    )

    private fun createRenderPaints(): RenderPaints {
        val vignette = Paint().apply {
            shader = RadialGradient(
                WIDTH / 2f,
                HEIGHT / 2f,
                HEIGHT * 0.62f,
                intArrayOf(Color.TRANSPARENT, Color.argb(120, 6, 4, 8), Color.argb(225, 5, 4, 7)),
                floatArrayOf(0.38f, 0.78f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val bottomGrad = Paint().apply {
            shader = LinearGradient(
                0f,
                HEIGHT * 0.56f,
                0f,
                HEIGHT.toFloat(),
                intArrayOf(Color.TRANSPARENT, Color.argb(215, 7, 6, 9)),
                null,
                Shader.TileMode.CLAMP
            )
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 54f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeJoin = Paint.Join.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(244, 239, 230)
            textSize = 54f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            style = Paint.Style.FILL
        }
        val accent = Paint(fill).apply {
            color = Color.rgb(239, 68, 68)
        }
        val brandTag = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(175, 244, 239, 230)
            textSize = 24f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.22f
        }
        return RenderPaints(
            bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG),
            vignettePaint = vignette,
            bottomGradPaint = bottomGrad,
            flickerPaint = Paint(),
            captionStrokePaint = stroke,
            captionFillPaint = fill,
            captionAccentPaint = accent,
            brandTagPaint = brandTag
        )
    }

    private fun drawHorrorFrame(
        canvas: Canvas,
        bitmap: Bitmap,
        prevBitmap: Bitmap?,
        crossfadeAlpha: Float,
        scene: ShortScene,
        sceneProgress: Float,
        globalFrame: Int,
        paints: RenderPaints
    ) {
        canvas.drawColor(Color.rgb(9, 9, 11))

        if (prevBitmap != null && crossfadeAlpha < 1f) {
            paints.bitmapPaint.alpha = 255
            drawKenBurnsBitmap(canvas, prevBitmap, "ZOOM_IN", 1f, paints.bitmapPaint)
            paints.bitmapPaint.alpha = (crossfadeAlpha * 255).toInt().coerceIn(0, 255)
        } else {
            paints.bitmapPaint.alpha = 255
        }

        drawKenBurnsBitmap(canvas, bitmap, scene.cameraMotion, sceneProgress, paints.bitmapPaint)
        paints.bitmapPaint.alpha = 255

        // Atmospheric vignette & subtle candlelit flicker
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paints.vignettePaint)
        canvas.drawRect(0f, HEIGHT * 0.56f, WIDTH.toFloat(), HEIGHT.toFloat(), paints.bottomGradPaint)

        val flickerAlpha = (12 + 12 * sin(globalFrame * 0.73)).toInt().coerceIn(0, 32)
        paints.flickerPaint.color = Color.argb(flickerAlpha, 180, 24, 24)
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paints.flickerPaint)

        // Burned-in kinetic horror captions (wrapped cleanly to 2 lines max)
        drawKineticCaptions(canvas, scene.subtitleText, sceneProgress, paints)

        // Subtle broadcast timecode / folklore header
        val sec = globalFrame / FPS
        val frac = ((globalFrame % FPS) * 100) / FPS
        canvas.drawText(
            "DUSKLORE  •  00:%02d.%02d".format(sec, frac),
            WIDTH / 2f,
            92f,
            paints.brandTagPaint
        )
    }

    private fun drawKenBurnsBitmap(
        canvas: Canvas,
        bmp: Bitmap,
        motion: String,
        progress: Float,
        paint: Paint
    ) {
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val scale = when (motion) {
            "ZOOM_OUT" -> 1.12f - 0.12f * progress
            else -> 1.00f + 0.12f * progress
        }
        val viewW = w / scale
        val viewH = h / scale
        val maxDx = (w - viewW).coerceAtLeast(0f)
        val maxDy = (h - viewH).coerceAtLeast(0f)

        val left = when (motion) {
            "PAN_LEFT" -> maxDx * (1f - progress)
            "PAN_RIGHT" -> maxDx * progress
            else -> maxDx * 0.5f
        }
        val top = maxDy * 0.5f

        val src = Rect(
            left.toInt().coerceIn(0, bmp.width - 1),
            top.toInt().coerceIn(0, bmp.height - 1),
            (left + viewW).toInt().coerceIn(1, bmp.width),
            (top + viewH).toInt().coerceIn(1, bmp.height)
        )
        val dst = RectF(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat())
        canvas.drawBitmap(bmp, src, dst, paint)
    }

    private fun drawKineticCaptions(
        canvas: Canvas,
        subtitleText: String,
        progress: Float,
        paints: RenderPaints
    ) {
        val clean = subtitleText.trim().ifBlank { return }
        val words = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return

        // Split into 1 or 2 crisp lines
        val lines = mutableListOf<String>()
        val sb = StringBuilder()
        for (word in words) {
            val candidate = if (sb.isEmpty()) word else "$sb $word"
            if (paints.captionFillPaint.measureText(candidate) > WIDTH - 96f && sb.isNotEmpty()) {
                lines.add(sb.toString())
                sb.clear()
                sb.append(word)
            } else {
                sb.clear()
                sb.append(candidate)
            }
        }
        if (sb.isNotEmpty()) lines.add(sb.toString())

        val popScale = 0.94f + 0.06f * (progress * 4f).coerceIn(0f, 1f)
        canvas.save()
        val baseY = HEIGHT * 0.77f
        canvas.scale(popScale, popScale, WIDTH / 2f, baseY)

        lines.take(3).forEachIndexed { idx, line ->
            val y = baseY + idx * 68f
            canvas.drawText(line, WIDTH / 2f, y, paints.captionStrokePaint)
            val fillPaint = if (idx == lines.lastIndex && lines.size > 1) {
                paints.captionAccentPaint
            } else {
                paints.captionFillPaint
            }
            canvas.drawText(line, WIDTH / 2f, y, fillPaint)
        }
        canvas.restore()
    }

    private fun scaleCenterCrop(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val srcRatio = src.width.toFloat() / src.height.toFloat()
        val dstRatio = targetW.toFloat() / targetH.toFloat()
        val cropW: Int
        val cropH: Int
        if (srcRatio > dstRatio) {
            cropH = src.height
            cropW = (src.height * dstRatio).toInt().coerceAtMost(src.width)
        } else {
            cropW = src.width
            cropH = (src.width / dstRatio).toInt().coerceAtMost(src.height)
        }
        val x = ((src.width - cropW) / 2).coerceAtLeast(0)
        val y = ((src.height - cropH) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(src, x, y, cropW, cropH)
        return Bitmap.createScaledBitmap(cropped, targetW, targetH, true).also {
            if (cropped !== src && cropped !== it) cropped.recycle()
        }
    }
}
