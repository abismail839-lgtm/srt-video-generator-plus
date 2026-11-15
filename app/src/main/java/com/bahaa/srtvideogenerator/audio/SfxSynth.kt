package com.bahaa.srtvideogenerator.audio

import com.bahaa.srtvideogenerator.SfxChoice
import com.bahaa.srtvideogenerator.TransitionType
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * ===== NEW CODE =====
 * Built-in transition sounds are synthesised at runtime (no audio assets to ship).
 * Each clip is interleaved stereo float @ 44.1 kHz, peak-normalised to 0.9.
 */
object SfxSynth {

    /** Seconds the clip should START before the cut so its "hit" lands on the cut. */
    fun leadSeconds(choice: SfxChoice): Float = when (choice) {
        SfxChoice.WHOOSH -> 0.30f
        SfxChoice.SWIPE -> 0.12f
        SfxChoice.IMPACT -> 0.0f
        else -> 0.10f
    }

    fun autoChoiceFor(type: TransitionType): SfxChoice = when (type) {
        TransitionType.SLIDE_LEFT, TransitionType.SLIDE_RIGHT,
        TransitionType.SLIDE_UP, TransitionType.SLIDE_DOWN -> SfxChoice.SWIPE
        TransitionType.GLITCH, TransitionType.ZOOM -> SfxChoice.IMPACT
        else -> SfxChoice.WHOOSH
    }

    fun generate(choice: SfxChoice): FloatArray = when (choice) {
        SfxChoice.WHOOSH -> whoosh()
        SfxChoice.SWIPE -> swipe()
        SfxChoice.IMPACT -> impact()
        else -> error("Not a built-in sound: $choice")
    }

    /** Band-passed noise whose centre frequency sweeps up then down under a bell envelope. */
    private fun whoosh(): FloatArray {
        val n = (0.75f * TARGET_RATE).toInt()
        val rnd = Random(1)
        val mono = FloatArray(n)
        var low = 0.0
        var band = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val env = sin(PI * t).let { it * it }
            val fc = 250.0 + 2600.0 * sin(PI * t)
            val f = 2.0 * sin(PI * fc / TARGET_RATE)
            val noise = rnd.nextGaussian()
            low += f * band
            val high = noise - low - 0.35 * band
            band += f * high
            mono[i] = (band * env).toFloat()
        }
        return stereo(mono)
    }

    /** Short, bright, fast-attack noise burst sweeping upward. */
    private fun swipe(): FloatArray {
        val n = (0.38f * TARGET_RATE).toInt()
        val rnd = Random(2)
        val mono = FloatArray(n)
        var low = 0.0
        var band = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val env = (1.0 - exp(-t * 40.0)) * exp(-t * 5.0)
            val fc = 900.0 + 7000.0 * t
            val f = 2.0 * sin(PI * fc / TARGET_RATE)
            val noise = rnd.nextGaussian()
            low += f * band
            val high = noise - low - 0.5 * band
            band += f * high
            mono[i] = (high * 0.6 * env + band * 0.4 * env).toFloat()
        }
        return stereo(mono)
    }

    /** Pitch-dropping sub thump plus a low-passed noise hit. */
    private fun impact(): FloatArray {
        val n = (1.0f * TARGET_RATE).toInt()
        val rnd = Random(3)
        val mono = FloatArray(n)
        var phase = 0.0
        var lp = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / TARGET_RATE
            val freq = 40.0 + 90.0 * exp(-t * 14.0)
            phase += 2.0 * PI * freq / TARGET_RATE
            val thump = sin(phase) * exp(-t * 4.5)
            lp += 0.12 * (rnd.nextGaussian() - lp)
            val hit = lp * 3.0 * exp(-t * 11.0)
            val click = if (i < 60) (1.0 - i / 60.0) * 0.4 else 0.0
            mono[i] = (thump + hit + click).toFloat()
        }
        return stereo(mono)
    }

    private fun stereo(mono: FloatArray): FloatArray {
        var peak = 1e-6f
        for (v in mono) peak = max(peak, abs(v))
        val g = 0.9f / peak
        val fade = (0.01f * TARGET_RATE).toInt()
        val out = FloatArray(mono.size * 2)
        for (i in mono.indices) {
            var v = mono[i] * g
            val tail = mono.size - 1 - i
            if (tail < fade) v *= tail / fade.toFloat() // no click at the end
            out[i * 2] = v
            out[i * 2 + 1] = v
        }
        return out
    }
}
