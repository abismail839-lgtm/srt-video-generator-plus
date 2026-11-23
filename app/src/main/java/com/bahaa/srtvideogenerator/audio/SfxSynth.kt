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
        SfxChoice.BOOM -> 0.0f
        SfxChoice.CLICK -> 0.02f
        SfxChoice.POP -> 0.03f
        SfxChoice.ZAP -> 0.10f
        SfxChoice.PAGE -> 0.20f
        SfxChoice.DING, SfxChoice.STAMP, SfxChoice.TYPEWRITER -> 0.0f
        else -> 0.10f
    }

    fun autoChoiceFor(type: TransitionType): SfxChoice = when (type) {
        TransitionType.SLIDE_LEFT, TransitionType.SLIDE_RIGHT,
        TransitionType.SLIDE_UP, TransitionType.SLIDE_DOWN -> SfxChoice.SWIPE
        TransitionType.GLITCH, TransitionType.ZOOM -> SfxChoice.IMPACT
        TransitionType.FLASH, TransitionType.CIRCLE -> SfxChoice.POP
        TransitionType.PIXELATE -> SfxChoice.ZAP
        TransitionType.SHAKE_CUT -> SfxChoice.BOOM
        TransitionType.NONE -> SfxChoice.CLICK
        else -> SfxChoice.WHOOSH
    }

    fun generate(choice: SfxChoice): FloatArray = when (choice) {
        SfxChoice.WHOOSH -> whoosh()
        SfxChoice.SWIPE -> swipe()
        SfxChoice.IMPACT -> impact()
        SfxChoice.CLICK -> click()
        SfxChoice.POP -> pop()
        SfxChoice.ZAP -> zap()
        SfxChoice.PAGE -> page()
        SfxChoice.BOOM -> boom()
        SfxChoice.DING -> ding()
        SfxChoice.STAMP -> stamp()
        SfxChoice.TYPEWRITER -> typewriter()
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

    /** Very short, dry tick. */
    private fun click(): FloatArray {
        val n = (0.12f * TARGET_RATE).toInt()
        val rnd = Random(4)
        val mono = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / TARGET_RATE
            val env = exp(-t * 90.0)
            mono[i] = ((sin(2.0 * PI * 1800.0 * t) * 0.6 + rnd.nextGaussian() * 0.35) * env).toFloat()
        }
        return stereo(mono)
    }

    /** Soft bubble pop: quick downward sine sweep. */
    private fun pop(): FloatArray {
        val n = (0.20f * TARGET_RATE).toInt()
        val rnd = Random(5)
        val mono = FloatArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / TARGET_RATE
            val freq = 220.0 + 700.0 * exp(-t * 30.0)
            phase += 2.0 * PI * freq / TARGET_RATE
            mono[i] = ((sin(phase) * exp(-t * 22.0)) + rnd.nextGaussian() * 0.03 * exp(-t * 60.0)).toFloat()
        }
        return stereo(mono)
    }

    /** Digital glitch zap: stepped random pitch with a harsh square-ish tone. */
    private fun zap(): FloatArray {
        val n = (0.32f * TARGET_RATE).toInt()
        val rnd = Random(6)
        val mono = FloatArray(n)
        var phase = 0.0
        var freq = 800.0
        val step = (0.015f * TARGET_RATE).toInt()
        for (i in 0 until n) {
            if (i % step == 0) freq = 300.0 + rnd.nextDouble() * 2700.0
            val t = i.toDouble() / TARGET_RATE
            phase += 2.0 * PI * freq / TARGET_RATE
            val square = if (sin(phase) >= 0.0) 1.0 else -1.0
            mono[i] = (square * 0.6 * exp(-t * 8.0)).toFloat()
        }
        return stereo(mono)
    }

    /** Paper flip: two quick bursts of high-passed noise. */
    private fun page(): FloatArray {
        val n = (0.45f * TARGET_RATE).toInt()
        val rnd = Random(7)
        val mono = FloatArray(n)
        var prev = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / TARGET_RATE
            val noise = rnd.nextGaussian()
            val high = noise - prev
            prev = noise
            val flap1 = exp(-t * 14.0)
            val flap2 = if (t > 0.12) exp(-(t - 0.12) * 10.0) * 0.7 else 0.0
            mono[i] = (high * (flap1 + flap2)).toFloat()
        }
        return stereo(mono)
    }

    /** Long, deep cinematic boom. */
    private fun boom(): FloatArray {
        val n = (1.6f * TARGET_RATE).toInt()
        val rnd = Random(8)
        val mono = FloatArray(n)
        var phase = 0.0
        var lp = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / TARGET_RATE
            val freq = 30.0 + 40.0 * exp(-t * 5.0)
            phase += 2.0 * PI * freq / TARGET_RATE
            lp += 0.03 * (rnd.nextGaussian() - lp)
            mono[i] = (sin(phase) * exp(-t * 2.2) + lp * 4.0 * exp(-t * 3.0)).toFloat()
        }
        return stereo(mono)
    }

    /** Bright bell ding. */
    private fun ding(): FloatArray {
        val n = (1.0f * TARGET_RATE).toInt()
        val mono = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / TARGET_RATE
            mono[i] = (sin(2.0 * PI * 1318.5 * t) * exp(-t * 5.0) +
                0.5 * sin(2.0 * PI * 2637.0 * t) * exp(-t * 8.0) +
                0.25 * sin(2.0 * PI * 3951.0 * t) * exp(-t * 12.0)).toFloat()
        }
        return stereo(mono)
    }

    /** Rubber stamp thud. */
    private fun stamp(): FloatArray {
        val n = (0.4f * TARGET_RATE).toInt()
        val rnd = Random(9)
        val mono = FloatArray(n)
        var phase = 0.0
        var lp = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / TARGET_RATE
            phase += 2.0 * PI * (70.0 + 110.0 * exp(-t * 45.0)) / TARGET_RATE
            lp += 0.25 * (rnd.nextGaussian() - lp)
            mono[i] = (sin(phase) * exp(-t * 13.0) + lp * 0.9 * exp(-t * 40.0)).toFloat()
        }
        return stereo(mono)
    }

    /** About 3 seconds of typewriter keys; the mixer cuts it to the length of the typing. */
    private fun typewriter(): FloatArray {
        val n = (3.0f * TARGET_RATE).toInt()
        val rnd = Random(10)
        val mono = FloatArray(n)
        val step = (0.06f * TARGET_RATE).toInt()
        var at = 0
        while (at + 400 < n) {
            val amp = 0.6 + rnd.nextDouble() * 0.4
            val pitch = 1400.0 + rnd.nextDouble() * 900.0
            for (k in 0 until 400) {
                val t = k.toDouble() / TARGET_RATE
                val e = exp(-k / 45.0)
                mono[at + k] += ((rnd.nextGaussian() * 0.6 + sin(2.0 * PI * pitch * t) * 0.5) * e * amp).toFloat()
            }
            at += step + rnd.nextInt(step / 3 + 1) - step / 6
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
