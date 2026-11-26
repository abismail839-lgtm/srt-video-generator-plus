package com.abulolo.srtvideogenerator

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
import com.abulolo.srtvideogenerator.audio.AudioMixer
import com.abulolo.srtvideogenerator.gl.EglCore
import com.abulolo.srtvideogenerator.gl.GlTexture
import com.abulolo.srtvideogenerator.gl.PopLayer
import com.abulolo.srtvideogenerator.gl.SurfaceRenderer
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

// ===== RECONSTRUCTED CLASS (original: NativeMediaGenerator). Method names renderImages / muxAudio /
// decodeBitmap / publishVideo mirror the original; ALL bodies are NEW and unverified against it. =====

class NativeMediaGenerator(private val context: Context) {

    class Cancelled : RuntimeException("Cancelled")

    /** Whole pipeline. Blocking – call from a background thread. Returns the saved video's Uri. */
    fun generate(req: RenderRequest, onProgress: (Float, String) -> Unit, cancelled: () -> Boolean): Uri {
        val cues = req.srtUri?.let { SrtParser.parse(context, it) } ?: emptyList()
        val wordCues = req.wordSrtUri?.let { SrtParser.parse(context, it) } ?: emptyList()
        val voiceUs = req.audio.voiceUri?.let { mediaDurationUs(it) } ?: 0L

        val fullTimeline = TimelinePlanner.plan(
            imageCount = req.images.size,
            cues = cues,
            perImage = req.images.map { it.transition },
            transitions = req.transitions,
            minTotalUs = voiceUs,
            fallbackSceneMs = req.fallbackSceneMs
        )

        // Test render: cut the timeline down to the chosen scenes and move it to start at 0.
        var timeline = fullTimeline
        var rq = req
        var cuesR = cues
        var wordR = wordCues
        var voiceOffsetUs = 0L
        val tf = req.testFirst
        if (tf != null) {
            val n = req.images.size
            val first = tf.coerceIn(0, n - 1)
            val last = (req.testLast ?: first).coerceIn(first, n - 1)
            val winStart = fullTimeline.scenes[first].startUs
            val winEnd = fullTimeline.scenes[last].endUs
            fun shift(list: List<SrtCue>) = list.filter { it.endUs > winStart && it.startUs < winEnd }
                .map { it.copy(startUs = it.startUs - winStart, endUs = it.endUs - winStart) }
            timeline = Timeline(
                scenes = (first..last).map { Scene(it - first, fullTimeline.scenes[it].startUs - winStart, fullTimeline.scenes[it].endUs - winStart) },
                boundaries = (first..last).map { idx ->
                    if (idx == first) null else fullTimeline.boundaries.getOrNull(idx)?.let {
                        Boundary(idx - first, it.type, it.centerUs - winStart, it.durUs)
                    }
                },
                totalUs = winEnd - winStart
            )
            rq = req.copy(images = req.images.subList(first, last + 1), testFirst = null, testLast = null)
            cuesR = shift(cues)
            wordR = shift(wordCues)
            voiceOffsetUs = winStart
        }

        val work = File(context.cacheDir, "render").apply { deleteRecursively(); mkdirs() }
        val videoOnly = File(work, "video.mp4")
        val audioFile = File(work, "audio.m4a")
        val finalFile = File(work, "final.mp4")

        try {
            onProgress(0f, "Rendering video…")
            renderImages(rq, timeline, cuesR, wordR, videoOnly, { onProgress(it * 0.75f, "Rendering video…") }, cancelled)

            onProgress(0.75f, "Mixing audio…")
            val mixed = try {
                AudioMixer(context).mix(rq.audio, timeline, audioFile, voiceUs, rq.images.map { it.sfx }, rq.images.map { it.ambience }, rq.images.map { it.pops }, voiceOffsetUs, cancelled) {
                    onProgress(0.75f + it * 0.2f, "Mixing audio…")
                }
            } catch (e: InterruptedException) { throw Cancelled() }

            onProgress(0.95f, "Finishing…")
            val toPublish = if (mixed != null) {
                muxAudio(videoOnly, mixed, finalFile)
                finalFile
            } else videoOnly

            val name = (if (tf != null) "SRT_Video_TEST_" else "SRT_Video_") + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
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
        var pops: PopLayer? = null
        var logoTex: GlTexture? = null
        val textures = HashMap<Int, GlTexture>()
        val clips = HashMap<Int, VideoClip>()
        val clipKey = HashMap<Int, Long>()
        val frameUs = 1_000_000L / fps
        try {
            val e0 = EglCore(inputSurface)
            egl = e0
            e0.makeCurrent()
            val r = SurfaceRenderer(w, h).also {
                it.init()
                it.setEnhance(req.enhance.sharpness, req.enhance.contrast, req.enhance.colour)
            }
            renderer = r
            val popLayer = PopLayer(context, r, w, h)
            pops = popLayer
            val wmUri = req.watermark.uri
            if (wmUri != null) {
                logoTex = try {
                    val b = BitmapLoader.decode(context, wmUri, 512)
                    try { r.createTexture(b) } finally { b.recycle() }
                } catch (_: Exception) { null }
            }

            fun tex(i: Int): GlTexture = textures.getOrPut(i) {
                val bmp = decodeBitmap(req.images[i].uri, max(w, h))
                try { r.createTexture(bmp) } finally { bmp.recycle() }
            }

            // Animated clips: fetch the right frame for this moment and put it into the scene's texture.
            fun updateVideo(i: Int, t: Long) {
                if (!req.images[i].isVideo) return
                val clip = clips.getOrPut(i) { VideoClip(context, req.images[i].uri, max(w, h), sequential = true) }
                val local = (t - timeline.scenes[i].startUs).coerceAtLeast(0L)
                val shown = minOf(local, (clip.durationUs - 1L).coerceAtLeast(0L)) // freeze on the last frame
                val key = shown / frameUs
                if (clipKey[i] == key) return
                val bmp = clip.frame(shown) ?: return
                try { textures[i] = r.replaceTexture(tex(i), bmp) } finally { if (!clip.ownsFrames) bmp.recycle() }
                clipKey[i] = key
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
                    updateVideo(from, t)
                    updateVideo(to, t)
                    val ma = motion(req, timeline, from, t)
                    val mb = motion(req, timeline, to, t)
                    r.draw(
                        tex(from), tex(to), p, b.type.shaderId,
                        ma[0], mb[0], timeSec, ma[1], ma[2], mb[1], mb[2]
                    )
                    drawSceneOverlay(r, req, timeline, from, t, 1f - p)
                    drawSceneOverlay(r, req, timeline, to, t, p)
                    popLayer.drawScene(req.images[from].pops, (t - timeline.scenes[from].startUs) / 1_000_000f, 1f - p)
                    popLayer.drawScene(req.images[to].pops, (t - timeline.scenes[to].startUs) / 1_000_000f, p)
                } else {
                    val s = timeline.sceneAt(t)
                    keepLow = s
                    updateVideo(s, t)
                    val m = motion(req, timeline, s, t)
                    r.draw(null, tex(s), 1f, 0, 1f, m[0], timeSec, 0f, 0f, m[1], m[2])
                    drawSceneOverlay(r, req, timeline, s, t, 1f)
                    popLayer.drawScene(req.images[s].pops, (t - timeline.scenes[s].startUs) / 1_000_000f, 1f)
                }
                // free textures we will not need again
                val iter = textures.entries.iterator()
                while (iter.hasNext()) {
                    val e = iter.next()
                    if (e.key < keepLow) {
                        r.deleteTexture(e.value); iter.remove()
                        clips.remove(e.key)?.close(); clipKey.remove(e.key)
                    }
                }

                val word = wordCues.activeAt(t)
                if (word != null) r.drawCaption(word.text, word = true, style = req.caption)
                if (req.showSubtitles) cues.activeAt(t)?.let { r.drawCaption(it.text, word = false, style = req.caption) }
                logoTex?.let { r.drawLogo(it, req.watermark.corner.ordinal, req.watermark.sizePct, req.watermark.opacity) }

                e0.setPresentationTimeNs(t * 1000L)
                e0.swapBuffers()
                drain.drain(false)
                if (f % 10 == 0) onProgress(f / totalFrames.toFloat())
            }
            encoder.signalEndOfInputStream()
            drain.drain(true)
        } finally {
            clips.values.forEach { it.close() }
            try { pops?.release() } catch (_: Exception) {}
            try { logoTex?.let { renderer?.deleteTexture(it) } } catch (_: Exception) {}
            try { renderer?.release() } catch (_: Exception) {}
            egl?.release()
            try { encoder.stop() } catch (_: Exception) {}
            encoder.release()
            inputSurface.release()
            if (drain.started) try { muxer.stop() } catch (_: Exception) {}
            muxer.release()
        }
    }

    private fun effectsOf(req: RenderRequest, scene: Int): List<EffectSettings> =
        req.images.getOrNull(scene)?.allEffects() ?: emptyList()

    /** Fades the effect out after "only first N seconds" (0.6 s fade). */
    private fun effectAlpha(e: EffectSettings, tl: Timeline, scene: Int, t: Long): Float {
        val only = e.onlyFirstSec ?: return 1f
        val local = (t - tl.scenes[scene].startUs) / 1_000_000f
        return ((only - local) / 0.6f).coerceIn(0f, 1f)
    }

    /** Returns [zoom, offsetX, offsetY] including screen-shake / zoom-pulse effects. */
    private fun motion(req: RenderRequest, tl: Timeline, scene: Int, t: Long): FloatArray {
        var zoom = zoomScale(req, tl, scene, t)
        var ox = 0f
        var oy = 0f
        for (e in effectsOf(req, scene)) {
            if (e.type != EffectType.SHAKE && e.type != EffectType.ZOOM_PULSE) continue
            val a = effectAlpha(e, tl, scene, t)
            val local = (t - tl.scenes[scene].startUs) / 1_000_000f * e.speed
            if (e.type == EffectType.SHAKE) {
                val amp = (0.004f + 0.014f * e.intensity) * a
                val ts = local * 11f
                ox = amp * (sin(ts * 3.7f) + sin(ts * 2.3f + 1.3f)) * 0.5f
                oy = amp * (sin(ts * 3.1f + 0.7f) + sin(ts * 2.9f + 2.1f)) * 0.5f
                zoom *= 1f + (amp * 2f + 0.004f)
            } else {
                val ph = (local / 1.2f).let { it - kotlin.math.floor(it) }
                zoom *= 1f + 0.09f * e.intensity * a * exp(-ph * 6f)
            }
        }
        return floatArrayOf(zoom, ox, oy)
    }

    private fun drawSceneOverlay(r: SurfaceRenderer, req: RenderRequest, tl: Timeline, scene: Int, t: Long, mix: Float) {
        for (e in effectsOf(req, scene)) {
            if (e.type == EffectType.SHAKE || e.type == EffectType.ZOOM_PULSE) continue
            val local = (t - tl.scenes[scene].startUs) / 1_000_000f * e.speed + 50f
            r.drawOverlay(e.type.ordinal, e.intensity, local, effectAlpha(e, tl, scene, t) * mix, e.regionX, e.regionY, e.regionW, e.regionH, e.scale, e.count)
        }
    }

    private fun zoomScale(req: RenderRequest, tl: Timeline, scene: Int, t: Long): Float {
        if (req.images.getOrNull(scene)?.isVideo == true) return 1f // clips already move
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

/**
 * Frames of an animated clip (sound is ignored). With [sequential] the clip is decoded once from start to end
 * (fast, used by the exporter); if that fails it falls back to asking for each frame separately (slow but safe).
 */
class VideoClip(context: Context, uri: Uri, private val maxSide: Int, sequential: Boolean = false) {
    private val retriever = MediaMetadataRetriever().apply { setDataSource(context, uri) }
    val durationUs: Long = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        ?.toLongOrNull() ?: 0L) * 1000L
    private var dec: ClipDecoder? = if (sequential) try { ClipDecoder(context, uri, maxSide) } catch (_: Exception) { null } else null

    /** true = the returned bitmap belongs to the clip, the caller must not recycle it. */
    val ownsFrames: Boolean get() = dec != null

    fun frame(timeUs: Long): Bitmap? {
        val d = dec
        if (d != null) {
            val b = try { d.frameAt(timeUs) } catch (_: Exception) { null }
            if (b != null) return b
            try { d.close() } catch (_: Exception) {}
            dec = null
        }
        val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: return null
        val big = max(raw.width, raw.height)
        if (big <= maxSide) return raw
        val k = maxSide.toFloat() / big
        val scaled = Bitmap.createScaledBitmap(raw, (raw.width * k).toInt().coerceAtLeast(1), (raw.height * k).toInt().coerceAtLeast(1), true)
        if (scaled !== raw) raw.recycle()
        return scaled
    }

    fun close() {
        try { dec?.close() } catch (_: Exception) {}
        dec = null
        try { retriever.release() } catch (_: Exception) {}
    }
}

/** Plays a clip forward with MediaCodec and turns the frames into bitmaps. Only moves forward in time. */
class ClipDecoder(context: Context, uri: Uri, private val maxSide: Int) {
    private val extractor = MediaExtractor()
    private val codec: MediaCodec
    private val info = MediaCodec.BufferInfo()
    private var rotation = 0
    private var inputDone = false
    private var eos = false
    private var cur: Bitmap? = null
    private var ahead: Bitmap? = null
    private var aheadPts = Long.MAX_VALUE

    init {
        extractor.setDataSource(context, uri, null)
        var track = -1
        var fmt: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) { track = i; fmt = f; break }
        }
        require(track >= 0 && fmt != null) { "no video track" }
        extractor.selectTrack(track)
        rotation = if (fmt.containsKey(MediaFormat.KEY_ROTATION)) fmt.getInteger(MediaFormat.KEY_ROTATION) else 0
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0)
        codec.start()
    }

    /** The newest frame that is not later than [timeUs]. The bitmap belongs to the decoder. */
    fun frameAt(timeUs: Long): Bitmap? {
        while (true) {
            if (ahead != null && aheadPts <= timeUs) {
                cur?.recycle(); cur = ahead; ahead = null; aheadPts = Long.MAX_VALUE
            }
            if (ahead != null) break
            if (eos) break
            val f = decodeOne() ?: break
            ahead = f.first; aheadPts = f.second
        }
        return cur ?: ahead
    }

    private fun decodeOne(): Pair<Bitmap, Long>? {
        if (eos) return null
        var spins = 0
        while (spins < 3000) {
            if (!inputDone) {
                val ii = codec.dequeueInputBuffer(0)
                if (ii >= 0) {
                    val buf = codec.getInputBuffer(ii)!!
                    val n = extractor.readSampleData(buf, 0)
                    if (n < 0) {
                        codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(ii, 0, n, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val oi = codec.dequeueOutputBuffer(info, 10_000)
            if (oi >= 0) {
                val last = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                var result: Pair<Bitmap, Long>? = null
                if (info.size > 0) {
                    val img = codec.getOutputImage(oi)
                    if (img != null) {
                        try { result = toBitmap(img) to info.presentationTimeUs } finally { img.close() }
                    }
                }
                codec.releaseOutputBuffer(oi, false)
                if (last) eos = true
                if (result != null) return result
                if (eos) return null
            } else if (oi == MediaCodec.INFO_TRY_AGAIN_LATER) {
                spins++
            }
        }
        eos = true
        return null
    }

    private fun toBitmap(img: android.media.Image): Bitmap {
        val crop = img.cropRect
        val cw = crop.width()
        val ch = crop.height()
        val step = ((max(cw, ch) + maxSide - 1) / maxSide).coerceAtLeast(1)
        val ow = (cw / step).coerceAtLeast(1)
        val oh = (ch / step).coerceAtLeast(1)
        val yP = img.planes[0]; val uP = img.planes[1]; val vP = img.planes[2]
        val yB = yP.buffer; val uB = uP.buffer; val vB = vP.buffer
        val yRow = yP.rowStride; val yPix = yP.pixelStride
        val uvRow = uP.rowStride; val uvPix = uP.pixelStride
        val px = IntArray(ow * oh)
        for (j in 0 until oh) {
            val y = crop.top + j * step
            for (i in 0 until ow) {
                val x = crop.left + i * step
                val yy = (yB.get(y * yRow + x * yPix).toInt() and 0xFF) - 16
                val ui = (y shr 1) * uvRow + (x shr 1) * uvPix
                val u = (uB.get(ui).toInt() and 0xFF) - 128
                val v = (vB.get(ui).toInt() and 0xFF) - 128
                val y298 = 298 * yy + 128
                val r = ((y298 + 459 * v) shr 8).coerceIn(0, 255)
                val g = ((y298 - 55 * u - 136 * v) shr 8).coerceIn(0, 255)
                val b = ((y298 + 541 * u) shr 8).coerceIn(0, 255)
                px[j * ow + i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val bmp = Bitmap.createBitmap(px, ow, oh, Bitmap.Config.ARGB_8888)
        if (rotation == 0) return bmp
        val m = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (rotated !== bmp) bmp.recycle()
        return rotated
    }

    fun close() {
        try { codec.stop() } catch (_: Exception) {}
        try { codec.release() } catch (_: Exception) {}
        try { extractor.release() } catch (_: Exception) {}
        try { cur?.recycle() } catch (_: Exception) {}
        try { ahead?.recycle() } catch (_: Exception) {}
        cur = null; ahead = null
    }
}

object BitmapLoader {
    fun isVideo(context: Context, uri: Uri): Boolean =
        try { context.contentResolver.getType(uri)?.startsWith("video/") == true } catch (_: Exception) { false }

    fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap {
        if (isVideo(context, uri)) {
            val clip = VideoClip(context, uri, maxSide)
            try { return clip.frame(0L) ?: error("Cannot read the clip") } finally { clip.close() }
        }
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
