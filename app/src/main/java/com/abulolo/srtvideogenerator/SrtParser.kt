package com.abulolo.srtvideogenerator

import android.content.Context
import android.net.Uri

// ===== RECONSTRUCTED: class names match the original APK (SrtCue, SrtParser); bodies are new. =====

data class SrtCue(val index: Int, val startUs: Long, val endUs: Long, val text: String)

object SrtParser {
    private val TIME = Regex(
        """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})"""
    )
    private val TAGS = Regex("<[^>]+>")

    fun parse(context: Context, uri: Uri): List<SrtCue> {
        val text = context.contentResolver.openInputStream(uri)?.use {
            String(it.readBytes(), Charsets.UTF_8)
        } ?: return emptyList()
        return parse(text)
    }

    fun parse(raw: String): List<SrtCue> {
        val text = raw.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        val cues = ArrayList<SrtCue>()
        for (block in text.split(Regex("\\n\\s*\\n"))) {
            val lines = block.trim().split('\n')
            val ti = lines.indexOfFirst { TIME.containsMatchIn(it) }
            if (ti < 0) continue
            val m = TIME.find(lines[ti])!!.groupValues
            val start = toUs(m[1], m[2], m[3], m[4])
            val end = toUs(m[5], m[6], m[7], m[8])
            val body = lines.drop(ti + 1).joinToString("\n").replace(TAGS, "").trim()
            if (body.isEmpty()) continue
            cues += SrtCue(cues.size + 1, start, maxOf(end, start + 1), body)
        }
        return cues.sortedBy { it.startUs }
    }

    private fun toUs(h: String, m: String, s: String, ms: String): Long =
        (h.toLong() * 3600 + m.toLong() * 60 + s.toLong()) * 1_000_000L +
            ms.padEnd(3, '0').toLong() * 1000L
}

fun List<SrtCue>.activeAt(tUs: Long): SrtCue? = firstOrNull { tUs >= it.startUs && tUs < it.endUs }
