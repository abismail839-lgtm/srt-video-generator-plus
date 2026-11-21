package com.bahaa.srtvideogenerator.audio

import android.content.Context
import com.bahaa.srtvideogenerator.AmbienceSettings
import com.bahaa.srtvideogenerator.AmbienceType
import com.bahaa.srtvideogenerator.AudioSettings
import com.bahaa.srtvideogenerator.SfxChoice
import com.bahaa.srtvideogenerator.Timeline
import android.net.Uri
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * ===== NEW CODE =====
 * Mixes voice-over + background music (volume, fades, loop/trim) + transition sound effects
 * into one AAC track exactly as long as the video.
 */
class AudioMixer(private val context: Context) {

    private val AMB_FADE_FRAMES = (0.4 * TARGET_RATE).toLong()

    private class SfxEvent(val startFrame: Long, val clip: FloatArray, val gain: Float)

    /** A looping scene sound between [startFrame] and [endFrame], faded at both ends. */
    private class AmbEvent(val startFrame: Long, val endFrame: Long, val clip: FloatArray, val gain: Float)

    fun hasAnyAudio(a: AudioSettings, timeline: Timeline): Boolean =
        a.voiceUri != null || a.musicUri != null ||
            (a.sfxEnabled && timeline.scenes.size > 1)

    /** @return the finished .m4a, or null if there is nothing to mix. */
    fun mix(
        a: AudioSettings,
        timeline: Timeline,
        out: File,
        voiceUs: Long,
        perImageSfx: List<SfxChoice?>,
        ambience: List<AmbienceSettings>,
        cancelled: () -> Boolean,
        onProgress: (Float) -> Unit
    ): File? {
        if (!hasAnyAudio(a, timeline) && ambience.none { it.type != AmbienceType.NONE }) return null

        val totalFrames = timeline.totalUs * TARGET_RATE / 1_000_000L
        val events = if (a.sfxEnabled) planSfx(a, timeline, perImageSfx) else emptyList()
        val ambEvents = planAmbience(timeline, ambience, totalFrames)

        var voice: DecodedAudioSource? = null
        var music: DecodedAudioSource? = null
        try {
            val voiceGain: Float = if (a.voiceUri != null && a.voiceBoost)
                measureVoiceGain(a.voiceUri, voiceUs, cancelled) else 1f
            voice = a.voiceUri?.let { DecodedAudioSource(context, it, loop = false) }
            music = a.musicUri?.let { DecodedAudioSource(context, it, loop = a.musicLoop) }

            val chunk = 4096
            val vBuf = FloatArray(chunk * 2)
            val mBuf = FloatArray(chunk * 2)
            val fadeInFrames = (a.musicFadeInSec * TARGET_RATE).toLong().coerceAtLeast(0)
            val fadeOutFrames = (a.musicFadeOutSec * TARGET_RATE).toLong().coerceAtLeast(0)

            AudioTranscoder().transcodeToAac(out, totalFrames, cancelled, onProgress) { start, n, dst ->
                java.util.Arrays.fill(dst, 0, n * 2, 0f)

                voice?.let { v ->
                    v.read(vBuf, n)
                    for (i in 0 until n * 2) dst[i] += vBuf[i] * a.voiceVolume * voiceGain
                }

                music?.let { m ->
                    m.read(mBuf, n)
                    for (i in 0 until n) {
                        val pos = start + i
                        var g = a.musicVolume
                        if (fadeInFrames > 0 && pos < fadeInFrames) g *= pos / fadeInFrames.toFloat()
                        val left = totalFrames - pos
                        if (fadeOutFrames > 0 && left < fadeOutFrames) g *= left / fadeOutFrames.toFloat()
                        dst[i * 2] += mBuf[i * 2] * g
                        dst[i * 2 + 1] += mBuf[i * 2 + 1] * g
                    }
                }

                val end = start + n
                for (ev in events) {
                    val evEnd = ev.startFrame + ev.clip.size / 2
                    if (ev.startFrame >= end || evEnd <= start) continue
                    val from = maxOf(start, ev.startFrame)
                    val to = minOf(end, evEnd)
                    for (pos in from until to) {
                        val ci = ((pos - ev.startFrame) * 2).toInt()
                        val di = ((pos - start) * 2).toInt()
                        dst[di] += ev.clip[ci] * ev.gain
                        dst[di + 1] += ev.clip[ci + 1] * ev.gain
                    }
                }

                for (ev in ambEvents) {
                    if (ev.startFrame >= end || ev.endFrame <= start) continue
                    val from = maxOf(start, ev.startFrame)
                    val to = minOf(end, ev.endFrame)
                    val clipFrames = (ev.clip.size / 2).toLong()
                    val fade = minOf(AMB_FADE_FRAMES, (ev.endFrame - ev.startFrame) / 2).coerceAtLeast(1)
                    for (pos in from until to) {
                        val ci = (((pos - ev.startFrame) % clipFrames) * 2).toInt()
                        val di = ((pos - start) * 2).toInt()
                        val g = ev.gain *
                            minOf(1f, (pos - ev.startFrame) / fade.toFloat()) *
                            minOf(1f, (ev.endFrame - pos) / fade.toFloat())
                        dst[di] += ev.clip[ci] * g
                        dst[di + 1] += ev.clip[ci + 1] * g
                    }
                }

                for (i in 0 until n * 2) dst[i] = softClip(dst[i])
            }
        } finally {
            voice?.close()
            music?.close()
        }
        return out
    }

    /**
     * Reads the whole voice-over once and works out how much louder it can be made
     * (target about -17 dBFS speech level, up to +18 dB, short peaks caught by the soft limiter).
     */
    private fun measureVoiceGain(uri: Uri, durationUs: Long, cancelled: () -> Boolean): Float {
        val frames = durationUs * TARGET_RATE / 1_000_000L
        if (frames <= 0) return 1f
        var peak = 0f
        var sumSq = 0.0
        var counted = 0L
        DecodedAudioSource(context, uri, loop = false).use { src ->
            val chunk = 8192
            val buf = FloatArray(chunk * 2)
            var done = 0L
            while (done < frames) {
                if (cancelled()) throw InterruptedException("cancelled")
                val n = min(chunk.toLong(), frames - done).toInt()
                src.read(buf, n)
                for (i in 0 until n) {
                    val v = max(abs(buf[2 * i]), abs(buf[2 * i + 1]))
                    if (v > peak) peak = v
                    if (v > 0.01f) { sumSq += (v * v).toDouble(); counted++ }
                }
                done += n
            }
        }
        if (counted < TARGET_RATE || peak < 0.001f) return 1f
        val rms = sqrt(sumSq / counted).toFloat()
        var gain = 0.14f / rms
        gain = min(gain, 2.5f / peak)
        return gain.coerceIn(1f, 8f)
    }

    private fun planSfx(a: AudioSettings, timeline: Timeline, perImage: List<SfxChoice?>): List<SfxEvent> {
        val builtIn = HashMap<SfxChoice, FloatArray>()
        val needsCustom = a.sfxChoice == SfxChoice.CUSTOM || perImage.any { it == SfxChoice.CUSTOM }
        val custom: FloatArray? =
            if (needsCustom && a.sfxCustomUri != null) DecodedAudioSource.decodeAll(context, a.sfxCustomUri) else null

        val events = ArrayList<SfxEvent>()
        for (b in timeline.boundaries) {
            if (b == null) continue
            val chosen = perImage.getOrNull(b.sceneIndex) ?: a.sfxChoice
            if (chosen == SfxChoice.NONE) continue
            val choice = if (chosen == SfxChoice.AUTO) SfxSynth.autoChoiceFor(b.type) else chosen
            val clip = if (choice == SfxChoice.CUSTOM) (custom ?: continue)
            else builtIn.getOrPut(choice) { SfxSynth.generate(choice) }
            val startUs = b.centerUs - (SfxSynth.leadSeconds(choice) * 1_000_000L).toLong()
            val startFrame = (startUs * TARGET_RATE / 1_000_000L).coerceAtLeast(0)
            events += SfxEvent(startFrame, clip, a.sfxVolume)
        }
        return events
    }

    private fun planAmbience(timeline: Timeline, ambience: List<AmbienceSettings>, totalFrames: Long): List<AmbEvent> {
        val builtIn = HashMap<AmbienceType, FloatArray>()
        val customCache = HashMap<Uri, FloatArray>()
        val out = ArrayList<AmbEvent>()
        for ((i, amb) in ambience.withIndex()) {
            if (amb.type == AmbienceType.NONE || i >= timeline.scenes.size) continue
            val clip: FloatArray = if (amb.type == AmbienceType.CUSTOM) {
                val uri = amb.customUri ?: continue
                customCache.getOrPut(uri) { DecodedAudioSource.decodeAll(context, uri, 30) }
            } else builtIn.getOrPut(amb.type) { AmbienceSynth.generate(amb.type) }
            if (clip.size < 4) continue
            val scene = timeline.scenes[i]
            val startUs = timeline.boundaries.getOrNull(i)?.startUs ?: scene.startUs
            val endUs = amb.onlyFirstSec?.let { scene.startUs + (it * 1_000_000L).toLong() }
                ?: (timeline.boundaries.getOrNull(i + 1)?.endUs ?: scene.endUs)
            val sf = (startUs.coerceAtLeast(0L) * TARGET_RATE / 1_000_000L)
            val ef = minOf(endUs * TARGET_RATE / 1_000_000L, totalFrames)
            if (ef > sf) out += AmbEvent(sf, ef, clip, amb.volume)
        }
        return out
    }

    private fun softClip(x: Float): Float {
        val ax = abs(x)
        if (ax < 0.85f) return x
        val y = 0.85f + 0.15f * tanh((ax - 0.85f) / 0.15f)
        return if (x < 0) -y else y
    }
}
