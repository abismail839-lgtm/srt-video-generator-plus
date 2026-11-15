package com.bahaa.srtvideogenerator

import kotlin.random.Random

// ===== NEW CODE: scene + transition planning =====

data class Scene(val index: Int, val startUs: Long, val endUs: Long) {
    val durationUs get() = endUs - startUs
}

/** Transition INTO scene [sceneIndex], centred on that scene's start time. */
data class Boundary(val sceneIndex: Int, val type: TransitionType, val centerUs: Long, val durUs: Long) {
    val startUs get() = centerUs - durUs / 2
    val endUs get() = centerUs + durUs / 2
}

class Timeline(
    val scenes: List<Scene>,
    /** boundaries[i] = transition into scene i (index 0 is always null). */
    val boundaries: List<Boundary?>,
    val totalUs: Long
) {
    fun sceneAt(tUs: Long): Int {
        var lo = 0
        var hi = scenes.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (scenes[mid].startUs <= tUs) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** Returns the running transition at time t, or null. */
    fun activeBoundary(tUs: Long): Boundary? {
        val s = sceneAt(tUs)
        for (i in intArrayOf(s, s + 1)) {
            val b = boundaries.getOrNull(i) ?: continue
            if (b.type != TransitionType.NONE && tUs >= b.startUs && tUs < b.endUs) return b
        }
        return null
    }

    fun sceneProgress(index: Int, tUs: Long): Float {
        val sc = scenes[index]
        return ((tUs - sc.startUs).toFloat() / sc.durationUs.coerceAtLeast(1)).coerceIn(0f, 1f)
    }
}

object TimelinePlanner {
    private const val MIN_SCENE_US = 300_000L

    /**
     * ASSUMPTION (original behaviour could not be verified): image i is shown from cue i's start
     * until cue i+1's start. Images beyond the number of cues use [fallbackSceneMs].
     */
    fun plan(
        imageCount: Int,
        cues: List<SrtCue>,
        perImage: List<TransitionType?>,
        transitions: TransitionSettings,
        minTotalUs: Long,
        fallbackSceneMs: Int,
        seed: Long = System.nanoTime()
    ): Timeline {
        require(imageCount > 0) { "No images" }
        val fb = fallbackSceneMs * 1000L

        val starts = LongArray(imageCount)
        for (i in 1 until imageCount) {
            val wanted = cues.getOrNull(i)?.startUs ?: (starts[i - 1] + fb)
            starts[i] = maxOf(wanted, starts[i - 1] + MIN_SCENE_US)
        }
        val lastEndWanted = maxOf(
            cues.lastOrNull()?.endUs ?: 0L,
            starts[imageCount - 1] + fb.coerceAtLeast(MIN_SCENE_US),
            minTotalUs
        )
        val total = maxOf(lastEndWanted, starts[imageCount - 1] + MIN_SCENE_US)

        val scenes = List(imageCount) { i ->
            Scene(i, starts[i], if (i + 1 < imageCount) starts[i + 1] else total)
        }

        val rnd = Random(seed)
        var previous: TransitionType? = null
        val boundaries = ArrayList<Boundary?>(imageCount)
        boundaries += null
        for (i in 1 until imageCount) {
            var type = when {
                !transitions.enabled -> TransitionType.NONE
                else -> perImage.getOrNull(i) ?: transitions.default
            }
            if (type == TransitionType.RANDOM) {
                val options = TransitionType.pool.filter { it != previous }
                type = options[rnd.nextInt(options.size)]
            }
            val maxDur = (minOf(scenes[i - 1].durationUs, scenes[i].durationUs) * 0.45).toLong()
            val dur = minOf(transitions.durationMs * 1000L, maxDur)
            if (dur < 100_000L) type = TransitionType.NONE
            previous = type
            boundaries += Boundary(i, type, scenes[i].startUs, dur)
        }
        return Timeline(scenes, boundaries, total)
    }
}
