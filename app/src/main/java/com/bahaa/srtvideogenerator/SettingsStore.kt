package com.bahaa.srtvideogenerator

import android.content.Context

// ===== NEW CODE (update, part 1): remembers your last settings =====

/** Settings that are remembered between launches (files are never remembered). */
data class SavedSettings(
    val resolution: Resolution = Resolution.HD_LANDSCAPE,
    val fps: Int = 24,
    val zoomMode: ZoomMode = ZoomMode.IN,
    val showSubtitles: Boolean = true,
    val transitions: TransitionSettings = TransitionSettings(),
    val audio: AudioSettings = AudioSettings()
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private inline fun <reified E : Enum<E>> enumOf(key: String, default: E): E =
        try {
            prefs.getString(key, null)?.let { enumValueOf<E>(it) } ?: default
        } catch (_: Exception) { default }

    fun load(): SavedSettings {
        val d = SavedSettings()
        val a = d.audio
        val sfxChoice = enumOf("sfxChoice", a.sfxChoice).let {
            if (it == SfxChoice.CUSTOM) SfxChoice.AUTO else it // files are not remembered
        }
        return SavedSettings(
            resolution = enumOf("resolution", d.resolution),
            fps = prefs.getInt("fps", d.fps),
            zoomMode = enumOf("zoomMode", d.zoomMode),
            showSubtitles = prefs.getBoolean("showSubtitles", d.showSubtitles),
            transitions = TransitionSettings(
                enabled = prefs.getBoolean("trEnabled", d.transitions.enabled),
                default = enumOf("trDefault", d.transitions.default),
                durationMs = prefs.getInt("trDuration", d.transitions.durationMs)
            ),
            audio = AudioSettings(
                voiceVolume = prefs.getFloat("voiceVolume", a.voiceVolume),
                voiceBoost = prefs.getBoolean("voiceBoost", a.voiceBoost),
                musicVolume = prefs.getFloat("musicVolume", a.musicVolume),
                musicFadeInSec = prefs.getFloat("musicFadeIn", a.musicFadeInSec),
                musicFadeOutSec = prefs.getFloat("musicFadeOut", a.musicFadeOutSec),
                musicLoop = prefs.getBoolean("musicLoop", a.musicLoop),
                sfxEnabled = prefs.getBoolean("sfxEnabled", a.sfxEnabled),
                sfxChoice = sfxChoice,
                sfxVolume = prefs.getFloat("sfxVolume", a.sfxVolume)
            )
        )
    }

    fun save(s: SavedSettings) {
        prefs.edit()
            .putString("resolution", s.resolution.name)
            .putInt("fps", s.fps)
            .putString("zoomMode", s.zoomMode.name)
            .putBoolean("showSubtitles", s.showSubtitles)
            .putBoolean("trEnabled", s.transitions.enabled)
            .putString("trDefault", s.transitions.default.name)
            .putInt("trDuration", s.transitions.durationMs)
            .putFloat("voiceVolume", s.audio.voiceVolume)
            .putBoolean("voiceBoost", s.audio.voiceBoost)
            .putFloat("musicVolume", s.audio.musicVolume)
            .putFloat("musicFadeIn", s.audio.musicFadeInSec)
            .putFloat("musicFadeOut", s.audio.musicFadeOutSec)
            .putBoolean("musicLoop", s.audio.musicLoop)
            .putBoolean("sfxEnabled", s.audio.sfxEnabled)
            .putString("sfxChoice", s.audio.sfxChoice.name)
            .putFloat("sfxVolume", s.audio.sfxVolume)
            .apply()
    }
}
