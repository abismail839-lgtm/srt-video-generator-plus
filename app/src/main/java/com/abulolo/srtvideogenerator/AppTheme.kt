package com.abulolo.srtvideogenerator

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Colours of the app itself (not the icon). Chosen in the "App colours" section and remembered. */
object AppTheme {
    class Choice(val name: String, val color: Color)

    val accents = listOf(
        Choice("Blue", Color(0xFF3D8BFF)),
        Choice("Sky", Color(0xFF38C6F4)),
        Choice("Teal", Color(0xFF1FBFA8)),
        Choice("Green", Color(0xFF4CCB6A)),
        Choice("Gold", Color(0xFFF2B632)),
        Choice("Orange", Color(0xFFFF8A3D)),
        Choice("Red", Color(0xFFF0524F)),
        Choice("Pink", Color(0xFFF06EAA)),
        Choice("Purple", Color(0xFFA77BFF))
    )

    /** Last one is the light theme (white background). */
    val backgrounds = listOf(
        Choice("Black", Color(0xFF0B0B0D)),
        Choice("Dark grey", Color(0xFF1E1F22)),
        Choice("Navy", Color(0xFF0C1424)),
        Choice("Forest", Color(0xFF0D1A14)),
        Choice("Wine", Color(0xFF1C0F16)),
        Choice("Brown", Color(0xFF1D150F)),
        Choice("Light", Color(0xFFF6F6F8))
    )

    var accentIndex by mutableIntStateOf(0)
    var backgroundIndex by mutableIntStateOf(2)

    fun load(context: Context) {
        val p = context.getSharedPreferences("theme", Context.MODE_PRIVATE)
        accentIndex = p.getInt("accent", 0).coerceIn(0, accents.lastIndex)
        backgroundIndex = p.getInt("background", 2).coerceIn(0, backgrounds.lastIndex)
    }

    fun save(context: Context) {
        context.getSharedPreferences("theme", Context.MODE_PRIVATE).edit()
            .putInt("accent", accentIndex).putInt("background", backgroundIndex).apply()
    }

    fun scheme(): ColorScheme {
        val accent = accents[accentIndex].color
        val bg = backgrounds[backgroundIndex].color
        val light = backgroundIndex == backgrounds.lastIndex
        return if (light) {
            lightColorScheme(
                primary = lerp(accent, Color.Black, 0.2f), onPrimary = Color.White,
                secondary = accent, background = bg, surface = bg, onSurface = Color(0xFF1A1A1C),
                onBackground = Color(0xFF1A1A1C), surfaceVariant = Color(0xFFE6E6EB),
                surfaceContainerHighest = Color(0xFFE9E9EE), surfaceContainerHigh = Color(0xFFEDEDF1),
                surfaceContainer = Color(0xFFF0F0F4), surfaceContainerLow = Color(0xFFF3F3F6),
                primaryContainer = lerp(accent, Color.White, 0.7f), onPrimaryContainer = Color(0xFF1A1A1C)
            )
        } else {
            val card = lerp(bg, Color.White, 0.09f)
            darkColorScheme(
                primary = accent, onPrimary = Color(0xFF0B0B0D),
                secondary = accent, background = bg, surface = bg,
                onSurface = Color(0xFFEDEDF0), onBackground = Color(0xFFEDEDF0),
                surfaceVariant = card, onSurfaceVariant = Color(0xFFC9C9CF),
                surfaceContainerHighest = card, surfaceContainerHigh = lerp(bg, Color.White, 0.075f),
                surfaceContainer = lerp(bg, Color.White, 0.06f), surfaceContainerLow = lerp(bg, Color.White, 0.04f),
                primaryContainer = lerp(bg, accent, 0.35f), onPrimaryContainer = Color.White,
                outline = lerp(bg, Color.White, 0.4f)
            )
        }
    }
}
