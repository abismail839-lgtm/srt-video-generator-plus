package com.bahaa.srtvideogenerator.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.Closeable
import java.nio.ByteOrder
import kotlin.math.floor

const val TARGET_RATE = 44100

/**
 * ===== NEW CODE =====
 * Streams any Android-decodable audio file as 44.1 kHz stereo float PCM (interleaved L,R),
 * resampling (linear) and converting mono/multichannel to stereo on the fly.
 * With [loop] = true the file restarts at the end; otherwise silence is returned after the end.
 */
class DecodedAudioSource(context: Context, uri: Uri, private val loop: Boolean) : Closeable {

    private val extractor = MediaExtractor()
    private val codec: MediaCodec
    private val info = MediaCodec.BufferInfo()

    private var srcRate = TARGET_RATE
    private var srcChannels = 2
    private var pcmFloat = false

    private var inputDone = false
    private var outputDone = false
    private var producedAnything = false

    // output queue (interleaved stereo @ TARGET_RATE)
    private var queue = FloatArray(TARGET_RATE * 4)
    private var qStart = 0
    private var qEnd = 0

    // resampler state
    private var phase = 0.0
    private var prevL = 0f
    private var prevR = 0f

    init {
        extractor.setDataSource(context, uri, null)
        var track = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                track = i; format = f; break
            }
        }
        require(track >= 0 && format != null) { "No audio track in file" }
        extractor.selectTrack(track)
        srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        srcChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
    }

    /** Fills dst[0 until frames*2]; zero-pads when the source has ended. */
    fun read(dst: FloatArray, frames: Int) {
        val need = frames * 2
        while (qEnd - qStart < need && !outputDone) pump()
        val avail = minOf(need, qEnd - qStart)
        System.arraycopy(queue, qStart, dst, 0, avail)
        qStart += avail
        if (avail < need) java.util.Arrays.fill(dst, avail, need, 0f)
        if (qStart == qEnd) { qStart = 0; qEnd = 0 }
    }

    private fun pump() {
        if (!inputDone) {
            val idx = codec.dequeueInputBuffer(0)
            if (idx >= 0) {
                val buf = codec.getInputBuffer(idx)!!
                val size = extractor.readSampleData(buf, 0)
                if (size < 0) {
                    codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputDone = true
                } else {
                    codec.queueInputBuffer(idx, 0, size, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }
        }
        val out = codec.dequeueOutputBuffer(info, 5_000)
        when {
            out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                val f = codec.outputFormat
                srcRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                srcChannels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                pcmFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                    f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
            }
            out >= 0 -> {
                if (info.size > 0) {
                    val bb = codec.getOutputBuffer(out)!!
                    bb.position(info.offset)
                    bb.limit(info.offset + info.size)
                    bb.order(ByteOrder.nativeOrder())
                    val channels = srcChannels.coerceAtLeast(1)
                    val frames: Int
                    val src: FloatArray
                    if (pcmFloat) {
                        val fb = bb.asFloatBuffer()
                        frames = fb.remaining() / channels
                        src = FloatArray(frames * 2)
                        val tmp = FloatArray(channels)
                        for (i in 0 until frames) {
                            fb.get(tmp)
                            toStereo(tmp, channels, src, i)
                        }
                    } else {
                        val sb = bb.asShortBuffer()
                        frames = sb.remaining() / channels
                        src = FloatArray(frames * 2)
                        val tmp = FloatArray(channels)
                        for (i in 0 until frames) {
                            for (c in 0 until channels) tmp[c] = sb.get() / 32768f
                            toStereo(tmp, channels, src, i)
                        }
                    }
                    if (frames > 0) { resample(src, frames); producedAnything = true }
                }
                val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                codec.releaseOutputBuffer(out, false)
                if (eos) {
                    if (loop && producedAnything) {
                        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                        codec.flush()
                        inputDone = false
                    } else outputDone = true
                }
            }
        }
    }

    private fun toStereo(tmp: FloatArray, ch: Int, dst: FloatArray, frame: Int) {
        val l: Float
        val r: Float
        when (ch) {
            1 -> { l = tmp[0]; r = tmp[0] }
            2 -> { l = tmp[0]; r = tmp[1] }
            else -> { l = tmp[0]; r = tmp[1] } // ignore surround extras
        }
        dst[frame * 2] = l
        dst[frame * 2 + 1] = r
    }

    private fun resample(src: FloatArray, n: Int) {
        if (srcRate == TARGET_RATE) {
            ensure(n * 2)
            System.arraycopy(src, 0, queue, qEnd, n * 2)
            qEnd += n * 2
            return
        }
        val step = srcRate.toDouble() / TARGET_RATE
        while (true) {
            val i0 = floor(phase).toInt()
            if (i0 + 1 >= n) break
            val frac = (phase - i0).toFloat()
            val aL = if (i0 < 0) prevL else src[i0 * 2]
            val aR = if (i0 < 0) prevR else src[i0 * 2 + 1]
            val bL = src[(i0 + 1) * 2]
            val bR = src[(i0 + 1) * 2 + 1]
            ensure(2)
            queue[qEnd++] = aL + (bL - aL) * frac
            queue[qEnd++] = aR + (bR - aR) * frac
            phase += step
        }
        phase -= n
        prevL = src[(n - 1) * 2]
        prevR = src[(n - 1) * 2 + 1]
    }

    private fun ensure(extra: Int) {
        if (qEnd + extra <= queue.size) return
        val live = qEnd - qStart
        if (live + extra <= queue.size && qStart > 0) {
            System.arraycopy(queue, qStart, queue, 0, live)
        } else {
            val bigger = FloatArray(maxOf(queue.size * 2, live + extra))
            System.arraycopy(queue, qStart, bigger, 0, live)
            queue = bigger
        }
        qStart = 0
        qEnd = live
    }

    override fun close() {
        try { codec.stop() } catch (_: Exception) {}
        try { codec.release() } catch (_: Exception) {}
        try { extractor.release() } catch (_: Exception) {}
    }

    companion object {
        /** Decodes a short clip completely (used for the user's own transition sound). */
        fun decodeAll(context: Context, uri: Uri, maxSeconds: Int = 6): FloatArray {
            DecodedAudioSource(context, uri, loop = false).use { src ->
                val frames = maxSeconds * TARGET_RATE
                val out = FloatArray(frames * 2)
                src.read(out, frames)
                var last = frames - 1
                while (last > 0 && out[last * 2] == 0f && out[last * 2 + 1] == 0f) last--
                return out.copyOf((last + 1) * 2)
            }
        }
    }
}
