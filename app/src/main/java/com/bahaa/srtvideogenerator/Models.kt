package com.bahaa.srtvideogenerator

import android.net.Uri

// ===== NEW CODE (reconstruction) =====

/** shaderId == ordinal; RANDOM is a selection mode and never reaches the shader. */
enum class TransitionType(val label: String, val concrete: Boolean = true) {
    NONE("Cut (no transition)"),
    FADE("Fade"),
    CROSS_DISSOLVE("Cross dissolve"),
    SLIDE_LEFT("Slide left"),
    SLIDE_RIGHT("Slide right"),
    SLIDE_UP("Slide up"),
    SLIDE_DOWN("Slide down"),
    SPIN("Spin"),
    BLUR("Blur"),
    WIPE("Wipe"),
    GLITCH("Glitch"),
    ZOOM("Zoom"),
    RANDOM("Random", concrete = false);

    val shaderId: Int get() = ordinal

    companion object {
        /** Types a RANDOM pick can choose from. */
        val pool: List<TransitionType> = entries.filter { it.concrete && it != NONE }
    }
}

enum class ZoomMode(val label: String) {
    OFF("Off"), IN("Zoom in"), OUT("Zoom out"), ALTERNATE("Alternate in/out")
}

enum class SfxChoice(val label: String) {
    AUTO("Auto (match transition)"),
    WHOOSH("Whoosh"),
    SWIPE("Swipe"),
    IMPACT("Impact"),
    CUSTOM("My own sound file")
}

enum class Resolution(val label: String, val w: Int, val h: Int) {
    HD_LANDSCAPE("720p landscape 1280x720", 1280, 720),
    FHD_LANDSCAPE("1080p landscape 1920x1080", 1920, 1080),
    HD_PORTRAIT("720p portrait 720x1280", 720, 1280),
    FHD_PORTRAIT("1080p portrait 1080x1920", 1080, 1920),
    SQUARE("Square 1080x1080", 1080, 1080)
}

data class ImageItem(
    val uri: Uri,
    val name: String,
    /** null = use the default transition. Image 0 has no incoming transition. */
    val transition: TransitionType? = null
)

data class TransitionSettings(
    val enabled: Boolean = true,
    val default: TransitionType = TransitionType.FADE,
    val durationMs: Int = 600
)

data class AudioSettings(
    val voiceUri: Uri? = null,
    val voiceVolume: Float = 1f,

    val musicUri: Uri? = null,
    val musicVolume: Float = 0.10f,
    val musicFadeInSec: Float = 1.5f,
    val musicFadeOutSec: Float = 3f,
    /** true = loop until the video ends; false = play once (silence afterwards). Always trimmed to video length. */
    val musicLoop: Boolean = true,

    val sfxEnabled: Boolean = true,
    val sfxChoice: SfxChoice = SfxChoice.AUTO,
    val sfxCustomUri: Uri? = null,
    val sfxVolume: Float = 0.35f
)

data class RenderRequest(
    val images: List<ImageItem>,
    val srtUri: Uri?,
    val wordSrtUri: Uri?,
    val resolution: Resolution = Resolution.HD_LANDSCAPE,
    val fps: Int = 30,
    val zoomMode: ZoomMode = ZoomMode.IN,
    val zoomStrength: Float = 0.12f,
    val showSubtitles: Boolean = true,
    val transitions: TransitionSettings = TransitionSettings(),
    val audio: AudioSettings = AudioSettings(),
    val fallbackSceneMs: Int = 3000
)
