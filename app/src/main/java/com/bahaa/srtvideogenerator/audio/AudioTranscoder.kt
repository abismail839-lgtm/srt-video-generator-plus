package com.bahaa.srtvideogenerator.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import kotlin.math.roundToInt

/**
 * ===== RECONSTRUCTED CLASS (original: AudioTranscoder). Method names mirror the original
 * (transcodeToAac, queuePcm, drainEncoder, drainEncoderUntilEnd, signalEncoderEnd); bodies are NEW.
 *
 * Pull-based: [producer] is asked for consecutive chunks of mixed float PCM and the result is
 * encoded to AAC-LC in an .m4a container.
 */
class AudioTranscoder {

    private lateinit var encoder: MediaCodec
    private lateinit var muxer: MediaMuxer
    private val info = MediaCodec.BufferInfo()
    private var track = -1
    private var muxerStarted = false

    fun transcodeToAac(
        out: File,
        totalFrames: Long,
        cancelled: () -> Boolean,
        onProgress: (Float) -> Unit,
        producer: (startFrame: Long, frames: Int, dst: FloatArray) -> Unit
    ) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, TARGET_RATE, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 160_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65_536)
        }
        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        try {
            val chunk = 4096
            val floats = FloatArray(chunk * 2)
            val bytes = ByteArray(chunk * 4)
            var done = 0L
            while (done < totalFrames) {
                if (cancelled()) throw InterruptedException("cancelled")
                val n = minOf(chunk.toLong(), totalFrames - done).toInt()
                producer(done, n, floats)
                for (i in 0 until n * 2) {
                    val s = (floats[i].coerceIn(-1f, 1f) * 32767f).roundToInt()
                    bytes[i * 2] = (s and 0xFF).toByte()
                    bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                }
                queuePcm(bytes, n * 4, done * 1_000_000L / TARGET_RATE)
                done += n
                onProgress(done.toFloat() / totalFrames)
            }
            signalEncoderEnd(done * 1_000_000L / TARGET_RATE)
            drainEncoderUntilEnd()
        } finally {
            try { encoder.stop() } catch (_: Exception) {}
            encoder.release()
            if (muxerStarted) try { muxer.stop() } catch (_: Exception) {}
            muxer.release()
        }
    }

    /** Feeds [length] bytes of 16-bit little-endian stereo PCM into the encoder. */
    private fun queuePcm(pcm: ByteArray, length: Int, ptsUs: Long) {
        var off = 0
        while (off < length) {
            drainEncoder()
            val idx = encoder.dequeueInputBuffer(10_000)
            if (idx < 0) continue
            val ib = encoder.getInputBuffer(idx)!!
            ib.clear()
            var take = minOf(ib.remaining(), length - off)
            take -= take % 4
            ib.put(pcm, off, take)
            val pts = ptsUs + (off / 4L) * 1_000_000L / TARGET_RATE
            encoder.queueInputBuffer(idx, 0, take, pts, 0)
            off += take
        }
    }

    /** Non-blocking: moves whatever encoded data is ready into the muxer. */
    private fun drainEncoder() {
        while (true) {
            val out = encoder.dequeueOutputBuffer(info, 0)
            if (!handleOutput(out)) return
        }
    }

    private fun drainEncoderUntilEnd() {
        var idle = 0
        while (true) {
            val out = encoder.dequeueOutputBuffer(info, 10_000)
            if (out == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (++idle > 500) return // 5 s safety
                continue
            }
            idle = 0
            val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            handleOutput(out)
            if (eos) return
        }
    }

    /** @return false when there is nothing more to read right now. */
    private fun handleOutput(out: Int): Boolean {
        when {
            out == MediaCodec.INFO_TRY_AGAIN_LATER -> return false
            out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                track = muxer.addTrack(encoder.outputFormat)
                muxer.start()
                muxerStarted = true
            }
            out >= 0 -> {
                val buf = encoder.getOutputBuffer(out)!!
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                if (info.size > 0 && muxerStarted) {
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    muxer.writeSampleData(track, buf, info)
                }
                encoder.releaseOutputBuffer(out, false)
            }
        }
        return true
    }

    private fun signalEncoderEnd(ptsUs: Long) {
        while (true) {
            val idx = encoder.dequeueInputBuffer(10_000)
            if (idx >= 0) {
                encoder.queueInputBuffer(idx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                return
            }
            drainEncoder()
        }
    }
}
