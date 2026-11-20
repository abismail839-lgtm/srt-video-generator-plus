package com.bahaa.srtvideogenerator

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.MediaStore
import com.bahaa.srtvideogenerator.audio.AudioMixer
import com.bahaa.srtvideogenerator.gl.EglCore
import com.bahaa.srtvideogenerator.gl.GlTexture
import com.bahaa.srtvideogenerator.gl.SurfaceRenderer
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max

// ===== RECONSTRUCTED CLASS (original: NativeMediaGenerator). Method names renderImages / muxAudio /
// decodeBitmap / publishVideo mirror the original; ALL bodies are NEW and unverified against it. =====

class NativeMediaGenerator(private val context: Context) {

    class Cancelled : RuntimeException("Cancelled")

    /** Whole pipeline. Blocking – call from a background thread. Returns the saved video's Uri. */
    fun generate(req: RenderRequest, onProgress: (Float, String) -> Unit, cancelled: () -> Boolean): Uri {
        val cues = req.srtUri?.let { SrtParser.parse(context, it) } ?: emptyList()
        val wordCues = req.wordSrtUri?.let { SrtParser.parse(context, it) } ?: emptyList()
        val voiceUs = req.audio.voiceUri?.let { mediaDurationUs(it) } ?: 0L

        val timeline = TimelinePlanner.plan(
            imageCount = req.images.size,
            cues = cues,
            perImage = req.images.map { it.transition },
            transitions = req.transitions,
            minTotalUs = voiceUs,
            fallbackSceneMs = req.fallbackSceneMs
        )

        val work = File(context.cacheDir, "render").apply { deleteRecursively(); mkdirs() }
        val videoOnly = File(work, "video.mp4")
        val audioFile = File(work, "audio.m4a")
        val finalFile = File(work, "final.mp4")

        try {
            onProgress(0f, "Rendering video…")
            renderImages(req, timeline, cues, wordCues, videoOnly, { onProgress(it * 0.75f, "Rendering video…") }, cancelled)

            onProgress(0.75f, "Mixing audio…")
            val mixed = try {
                AudioMixer(context).mix(req.audio, timeline, audioFile, voiceUs, cancelled) {
                    onProgress(0.75f + it * 0.2f, "Mixing audio…")
                }
            } catch (e: InterruptedException) { throw Cancelled() }

            onProgress(0.95f, "Finishing…")
            val toPublish = if (mixed != null) {
                muxAudio(videoOnly, mixed, finalFile)
                finalFile
            } else videoOnly

            val name = "SRT_Video_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                .format(java.util.Date()) + ".mp4"
            val uri = publishVideo(toPublish, name)
            onProgress(1f, "Saved to Movies/SRT Video Generator")
            return uri
        } finally {
            work.deleteRecursively()
        }
    }

    // ------------------------------------------------------------------ video

    private fun renderImages(
        req: RenderRequest, timeline: Timeline, cues: List<SrtCue>, wordCues: List<SrtCue>,
        out: File, onProgress: (Float) -> Unit, cancelled: () -> Boolean
    ) {
        val w = req.resolution.w
        val h = req.resolution.h
        val fps = req.fps
        val bitrate = (w.toLong() * h * fps * 0.09).toInt().coerceIn(4_000_000, 16_000_000)

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val drain = EncoderDrain(encoder, muxer)

        var egl: EglCore? = null
        var renderer: SurfaceRenderer? = null
        val textures = HashMap<Int, GlTexture>()
        try {
            val e0 = EglCore(inputSurface)
            egl = e0
            e0.makeCurrent()
            val r = SurfaceRenderer(w, h).also { it.init() }
            renderer = r

            fun tex(i: Int): GlTexture = textures.getOrPut(i) {
                val bmp = decodeBitmap(req.images[i].uri, max(w, h))
                try { r.createTexture(bmp) } finally { bmp.recycle() }
            }

            val totalFrames = ((timeline.totalUs * fps + 999_999L) / 1_000_000L).toInt().coerceAtLeast(1)
            for (f in 0 until totalFrames) {
                if (cancelled()) throw Cancelled()
                val t = f * 1_000_000L / fps
                val timeSec = t / 1_000_000f

                val b = timeline.activeBoundary(t)
                val keepLow: Int
                if (b != null) {
                    val p = ((t - b.startUs).toFloat() / b.durUs).coerceIn(0f, 1f)
                    val from = b.sceneIndex - 1
                    val to = b.sceneIndex
                    keepLow = from
                    r.draw(
                        tex(from), tex(to), p, b.type.shaderId,
                        zoomScale(req, timeline, from, t), zoomScale(req, timeline, to, t), timeSec
                    )
                } else {
                    val s = timeline.sceneAt(t)
                    keepLow = s
                    r.draw(null, tex(s), 1f, 0, 1f, zoomScale(req, timeline, s, t), timeSec)
                }
                // free textures we will not need again
                val iter = textures.entries.iterator()
                while (iter.hasNext()) {
                    val e = iter.next()
                    if (e.key < keepLow) { r.deleteTexture(e.value); iter.remove() }
                }

                val word = wordCues.activeAt(t)
                if (word != null) r.drawCaption(word.text, word = true)
                if (req.showSubtitles) cues.activeAt(t)?.let { r.drawCaption(it.text, word = false) }

                e0.setPresentationTimeNs(t * 1000L)
                e0.swapBuffers()
                drain.drain(false)
                if (f % 10 == 0) onProgress(f / totalFrames.toFloat())
            }
            encoder.signalEndOfInputStream()
            drain.drain(true)
        } finally {
            try { renderer?.release() } catch (_: Exception) {}
            egl?.release()
            try { encoder.stop() } catch (_: Exception) {}
            encoder.release()
            inputSurface.release()
            if (drain.started) try { muxer.stop() } catch (_: Exception) {}
            muxer.release()
        }
    }

    private fun zoomScale(req: RenderRequest, tl: Timeline, scene: Int, t: Long): Float {
        val p = tl.sceneProgress(scene, t)
        val s = req.zoomStrength
        return when (req.zoomMode) {
            ZoomMode.OFF -> 1f
            ZoomMode.IN -> 1f + s * p
            ZoomMode.OUT -> 1f + s * (1f - p)
            ZoomMode.ALTERNATE -> if (scene % 2 == 0) 1f + s * p else 1f + s * (1f - p)
        }
    }

    private class EncoderDrain(val encoder: MediaCodec, val muxer: MediaMuxer) {
        var started = false
        private var track = -1
        private val info = MediaCodec.BufferInfo()

        fun drain(untilEnd: Boolean) {
            var idle = 0
            while (true) {
                val idx = encoder.dequeueOutputBuffer(info, if (untilEnd) 10_000 else 0)
                when {
                    idx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!untilEnd) return
                        if (++idle > 1000) return
                    }
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        started = true
                    }
                    idx >= 0 -> {
                        idle = 0
                        val buf = encoder.getOutputBuffer(idx)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && started) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buf, info)
                        }
                        encoder.releaseOutputBuffer(idx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ images

    /** Decodes a picture so its longest side is about [maxSide] px, honouring EXIF rotation. */
    fun decodeBitmap(uri: Uri, maxSide: Int): Bitmap = BitmapLoader.decode(context, uri, maxSide)

    // ------------------------------------------------------------------ mux / publish

    /** Combines the silent video with the mixed audio track (samples copied, no re-encode). */
    private fun muxAudio(video: File, audio: File, out: File) {
        val vEx = MediaExtractor().apply { setDataSource(video.path) }
        val aEx = MediaExtractor().apply { setDataSource(audio.path) }
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val vTrack = firstTrack(vEx, "video/")
            val aTrack = firstTrack(aEx, "audio/")
            vEx.selectTrack(vTrack)
            aEx.selectTrack(aTrack)
            val vOut = muxer.addTrack(vEx.getTrackFormat(vTrack))
            val aOut = muxer.addTrack(aEx.getTrackFormat(aTrack))
            muxer.start()

            val buf = ByteBuffer.allocateDirect(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val vt = vEx.sampleTime
                val at = aEx.sampleTime
                if (vt < 0 && at < 0) break
                val useVideo = at < 0 || (vt in 0..at)
                val ex = if (useVideo) vEx else aEx
                val tr = if (useVideo) vOut else aOut
                buf.clear()
                val size = ex.readSampleData(buf, 0)
                if (size < 0) { ex.advance(); continue }
                val flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                    MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(0, size, ex.sampleTime, flags)
                muxer.writeSampleData(tr, buf, info)
                ex.advance()
            }
            muxer.stop()
        } finally {
            muxer.release()
            vEx.release()
            aEx.release()
        }
    }

    private fun firstTrack(ex: MediaExtractor, prefix: String): Int {
        for (i in 0 until ex.trackCount) {
            if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true) return i
        }
        error("No $prefix track")
    }

    /** Copies the finished file into the gallery (Movies/SRT Video Generator). */
    private fun publishVideo(file: File, displayName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SRT Video Generator")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create gallery entry")
        resolver.openOutputStream(uri)!!.use { o -> file.inputStream().use { it.copyTo(o) } }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    /** Duration of an audio/video file in microseconds. */
    fun mediaDurationUs(uri: Uri): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000L
        } finally { r.release() }
    }
}

object BitmapLoader {
    fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bmp = resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("Cannot decode image")

        val orientation = try {
            resolver.openInputStream(uri)!!.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }

        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            else -> return bmp
        }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (rotated !== bmp) bmp.recycle()
        bmp = rotated
        return bmp
    }
}
