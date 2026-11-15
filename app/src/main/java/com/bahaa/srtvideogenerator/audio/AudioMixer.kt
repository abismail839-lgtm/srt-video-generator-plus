package com.bahaa.srtvideogenerator.audio

import android.content.Context
import com.bahaa.srtvideogenerator.AudioSettings
import com.bahaa.srtvideogenerator.SfxChoice
import com.bahaa.srtvideogenerator.Timeline
import java.io.File
import kotlin.math.abs
import kotlin.math.tanh

/**
 * ===== NEW CODE =====
 * Mixes voice-over + background music (volume, fades, loop/trim) + transition sound effects
 * into one AAC track exactly as long as the video.
 */
class AudioMixer(private val context: Context) {

    private class SfxEvent(val startFrame: Long, val clip: FloatArray, val gain: Float)

    fun hasAnyAudio(a: AudioSettings, timeline: Timeline): Boolean =
        a.voiceUri != null || a.musicUri != null ||
            (a.sfxEnabled && timeline.scenes.size > 1)

    /** @return the finished .m4a, or null if there is nothing to mix. */
    fun mix(
        a: AudioSettings,
        timeline: Timeline,
        out: File,
        cancelled: () -> Boolean,
        onProgress: (Float) -> Unit
    ): File? {
        if (!hasAnyAudio(a, timeline)) return null

        val totalFrames = timeline.totalUs * TARGET_RATE / 1_000_000L
        val events = if (a.sfxEnabled) planSfx(a, timeline) else emptyList()

        var voice: DecodedAudioSource? = null
        var music: DecodedAudioSource? = null
        try {
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
                    for (i in 0 until n * 2) dst[i] += vBuf[i] * a.voiceVolume
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

                for (i in 0 until n * 2) dst[i] = softClip(dst[i])
            }
        } finally {
            voice?.close()
            music?.close()
        }
        return out
    }

    private fun planSfx(a: AudioSettings, timeline: Timeline): List<SfxEvent> {
        val builtIn = HashMap<SfxChoice, FloatArray>()
        val custom: FloatArray? =
            if (a.sfxChoice == SfxChoice.CUSTOM && a.sfxCustomUri != null)
                DecodedAudioSource.decodeAll(context, a.sfxCustomUri) else null
        if (a.sfxChoice == SfxChoice.CUSTOM && custom == null) return emptyList()

        val events = ArrayList<SfxEvent>()
        for (b in timeline.boundaries) {
            if (b == null) continue
            val choice = if (a.sfxChoice == SfxChoice.AUTO) SfxSynth.autoChoiceFor(b.type) else a.sfxChoice
            val clip = if (choice == SfxChoice.CUSTOM) custom!! else builtIn.getOrPut(choice) { SfxSynth.generate(choice) }
            val startUs = b.centerUs - (SfxSynth.leadSeconds(choice) * 1_000_000L).toLong()
            val startFrame = (startUs * TARGET_RATE / 1_000_000L).coerceAtLeast(0)
            events += SfxEvent(startFrame, clip, a.sfxVolume)
        }
        return events
    }

    private fun softClip(x: Float): Float {
        val ax = abs(x)
        if (ax < 0.85f) return x
        val y = 0.85f + 0.15f * tanh((ax - 0.85f) / 0.15f)
        return if (x < 0) -y else y
    }
}
