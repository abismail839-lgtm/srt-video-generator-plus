package com.abulolo.srtvideogenerator.audio

import com.abulolo.srtvideogenerator.AmbienceType
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * ===== NEW CODE (part 3) =====
 * Loopable scene sounds synthesised at runtime. Each clip is interleaved stereo float @ 44.1 kHz,
 * about 8 s long, peak-normalised to 0.9, with its ends cross-faded so it loops without a click.
 */
object AmbienceSynth {
    private const val SECONDS = 8
    private const val XFADE = 0.6f

    fun generate(type: AmbienceType): FloatArray {
        val n = ((SECONDS + XFADE) * TARGET_RATE).toInt()
        val l = FloatArray(n)
        val r = FloatArray(n)
        when (type) {
            AmbienceType.RAIN -> rain(l, r, 1.0f, false)
            AmbienceType.STORM -> storm(l, r)
            AmbienceType.FIRE -> fire(l, r)
            AmbienceType.MIC_CRACKLE -> micCrackle(l, r)
            AmbienceType.HOOVES -> hooves(l, r)
            AmbienceType.WIND -> wind(l, r, 1.0f)
            else -> error("Not a built-in scene sound: $type")
        }
        return loopify(l, r)
    }

    /** Cross-fades the tail into the head so the clip repeats seamlessly. */
    private fun loopify(l: FloatArray, r: FloatArray): FloatArray {
        val xf = (XFADE * TARGET_RATE).toInt()
        val len = l.size - xf
        var peak = 1e-6f
        val out = FloatArray(len * 2)
        for (i in 0 until len) {
            var a = l[i]
            var b = r[i]
            if (i < xf) {
                val w = i / xf.toFloat()
                a = a * w + l[len + i] * (1f - w)
                b = b * w + r[len + i] * (1f - w)
            }
            out[i * 2] = a
            out[i * 2 + 1] = b
            peak = max(peak, max(abs(a), abs(b)))
        }
        val g = 0.9f / peak
        for (i in out.indices) out[i] *= g
        return out
    }

    private fun rain(l: FloatArray, r: FloatArray, level: Float, heavy: Boolean) {
        val rnd = Random(11)
        var lpL = 0.0; var lpR = 0.0
        var hpL = 0.0; var hpR = 0.0
        for (i in l.indices) {
            val nl = rnd.nextGaussian()
            val nr = rnd.nextGaussian()
            lpL += 0.35 * (nl - lpL); lpR += 0.35 * (nr - lpR)
            hpL += 0.02 * (lpL - hpL); hpR += 0.02 * (lpR - hpR)
            l[i] += ((lpL - hpL) * 0.8 * level).toFloat()
            r[i] += ((lpR - hpR) * 0.8 * level).toFloat()
        }
        // individual drops
        val drops = if (heavy) 900 else 500
        repeat(drops) {
            val at = rnd.nextInt(l.size - 400)
            val a = (0.05 + rnd.nextDouble() * 0.12) * level
            val pan = rnd.nextDouble()
            for (k in 0 until 220) {
                val e = exp(-k / 35.0)
                val v = (rnd.nextGaussian() * e * a).toFloat()
                l[at + k] += v * (1f - pan.toFloat())
                r[at + k] += v * pan.toFloat()
            }
        }
    }

    private fun wind(l: FloatArray, r: FloatArray, level: Float) {
        val rnd = Random(12)
        var lpL = 0.0; var lpR = 0.0
        for (i in l.indices) {
            val t = i.toDouble() / TARGET_RATE
            val swell = 0.55 + 0.45 * sin(2.0 * PI * t / 4.0) * sin(2.0 * PI * t / 6.5 + 1.0)
            val cut = 0.004 + 0.012 * (0.5 + 0.5 * sin(2.0 * PI * t / 5.0))
            lpL += cut * (rnd.nextGaussian() - lpL)
            lpR += cut * (rnd.nextGaussian() - lpR)
            l[i] += (lpL * swell * 9.0 * level).toFloat()
            r[i] += (lpR * swell * 9.0 * level).toFloat()
        }
    }

    private fun storm(l: FloatArray, r: FloatArray) {
        rain(l, r, 1.1f, true)
        wind(l, r, 0.9f)
        val rnd = Random(13)
        // two rolling thunder claps
        for (start in intArrayOf((1.5 * TARGET_RATE).toInt(), (5.2 * TARGET_RATE).toInt())) {
            var lp = 0.0
            val len = (2.6 * TARGET_RATE).toInt()
            for (k in 0 until len) {
                if (start + k >= l.size) break
                val t = k.toDouble() / TARGET_RATE
                lp += 0.012 * (rnd.nextGaussian() - lp)
                val env = (1.0 - exp(-t * 25.0)) * exp(-t * 1.5) * (0.7 + 0.3 * sin(t * 9.0))
                val v = (lp * 40.0 * env).toFloat()
                l[start + k] += v
                r[start + k] += v
            }
        }
    }

    private fun fire(l: FloatArray, r: FloatArray) {
        val rnd = Random(14)
        var lp = 0.0
        for (i in l.indices) {
            val t = i.toDouble() / TARGET_RATE
            lp += 0.03 * (rnd.nextGaussian() - lp)
            val flicker = 0.6 + 0.4 * sin(2.0 * PI * t * 1.7) * sin(2.0 * PI * t * 0.9 + 2.0)
            val v = (lp * 2.2 * flicker).toFloat()
            l[i] += v; r[i] += v
        }
        // crackles and pops
        repeat(220) {
            val at = rnd.nextInt(l.size - 900)
            val a = 0.15 + rnd.nextDouble() * 0.7
            val decay = 25.0 + rnd.nextDouble() * 120.0
            val pan = rnd.nextDouble().toFloat()
            var prev = 0.0
            for (k in 0 until 800) {
                val n = rnd.nextGaussian()
                val high = n - prev
                prev = n
                val v = (high * exp(-k / decay) * a * 0.5).toFloat()
                l[at + k] += v * (1f - pan)
                r[at + k] += v * pan
            }
        }
    }

    /** Broken-microphone style: faint hiss with sudden crackles and a few crunchy bursts. */
    private fun micCrackle(l: FloatArray, r: FloatArray) {
        val rnd = Random(15)
        for (i in l.indices) {
            val v = (rnd.nextGaussian() * 0.01).toFloat()
            l[i] += v; r[i] += v
        }
        repeat(60) {
            val at = rnd.nextInt(l.size - 3000)
            val len = 40 + rnd.nextInt(700)
            val a = 0.2 + rnd.nextDouble() * 0.8
            var prev = 0.0
            for (k in 0 until len) {
                val n = rnd.nextGaussian()
                val v = ((n - prev * 0.6) * a * exp(-k / (len / 3.0))).toFloat()
                prev = n
                l[at + k] += v; r[at + k] += v
            }
        }
        repeat(6) {
            val at = rnd.nextInt(l.size - 12000)
            val len = 2000 + rnd.nextInt(6000)
            for (k in 0 until len) {
                val g = if (rnd.nextInt(4) == 0) rnd.nextGaussian() * 0.7 else 0.0
                val v = (g * exp(-k / (len / 2.0))).toFloat()
                l[at + k] += v; r[at + k] += v
            }
        }
    }

    /** Galloping: groups of four hoof beats, repeating, on a soft dirt-track bed. */
    private fun hooves(l: FloatArray, r: FloatArray) {
        val rnd = Random(16)
        val period = 0.5 // seconds per gallop cycle; 8 s = 16 cycles, so it loops exactly
        val offsets = doubleArrayOf(0.0, 0.11, 0.2, 0.33)
        val gains = doubleArrayOf(1.0, 0.7, 0.85, 0.95)
        var cycle = 0.0
        while (cycle < SECONDS + XFADE) {
            for (j in offsets.indices) {
                val at = ((cycle + offsets[j]) * TARGET_RATE).toInt()
                if (at + 3000 >= l.size) continue
                var lp = 0.0
                var phase = 0.0
                for (k in 0 until 3000) {
                    val t = k.toDouble() / TARGET_RATE
                    phase += 2.0 * PI * (150.0 + 90.0 * exp(-t * 60.0)) / TARGET_RATE
                    lp += 0.3 * (rnd.nextGaussian() - lp)
                    val v = ((sin(phase) * exp(-t * 55.0) + lp * 0.5 * exp(-t * 90.0)) * gains[j] * 0.8).toFloat()
                    l[at + k] += v; r[at + k] += v
                }
            }
            cycle += period
        }
        for (i in l.indices) {
            val v = (rnd.nextGaussian() * 0.004).toFloat()
            l[i] += v; r[i] += v
        }
    }
}
