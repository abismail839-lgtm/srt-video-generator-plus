package com.bahaa.srtvideogenerator

import android.net.Uri

// ===== NEW CODE (reconstruction) =====

/**
 * shaderId == ordinal for every type before RANDOM (RANDOM is a selection mode and never
 * reaches the shader). New types are inserted before RANDOM so existing ids never change.
 */
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
    DIP_WHITE("Dip to white"),
    FLASH("Flash"),
    CIRCLE("Circle reveal"),
    PIXELATE("Pixelate"),
    SHAKE_CUT("Shake cut"),
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
    CLICK("Click"),
    POP("Pop"),
    ZAP("Zap"),
    PAGE("Page turn"),
    BOOM("Deep boom"),
    CUSTOM("My own sound file"),
    NONE("No sound")
}

enum class Resolution(val label: String, val w: Int, val h: Int) {
    HD_LANDSCAPE("720p landscape 1280x720", 1280, 720),
    FHD_LANDSCAPE("1080p landscape 1920x1080", 1920, 1080),
    HD_PORTRAIT("720p portrait 720x1280", 720, 1280),
    FHD_PORTRAIT("1080p portrait 1080x1920", 1080, 1920),
    SQUARE("Square 1080x1080", 1080, 1080)
}

/** Overlay effects drawn on top of one image. Ids are used by the overlay shader. */
enum class EffectType(val label: String) {
    NONE("None"),
    WHITE_EDGE("Shaky white edge"),
    SNOW("Snow"),
    RAIN("Rain"),
    DUST("Floating dust"),
    EMBERS("Sparks / embers"),
    FILM("Old film"),
    VIGNETTE("Dark vignette"),
    GLOW_EDGE("Soft white glow edge"),
    FLASH("Flash pulses"),
    SHAKE("Screen shake"),
    ZOOM_PULSE("Zoom pulse")
}

/**
 * One image's overlay effect. [onlyFirstSec] == null means the effect stays for the whole image;
 * otherwise it fades out after that many seconds.
 */
data class EffectSettings(
    val type: EffectType = EffectType.NONE,
    val intensity: Float = 0.6f,
    val speed: Float = 1f,
    val onlyFirstSec: Float? = null
)

data class ImageItem(
    val uri: Uri,
    val name: String,
    /** null = use the default transition. Image 0 has no incoming transition. */
    val transition: TransitionType? = null,
    /** null = use the default transition sound. NONE = silent for this image's scene change. */
    val sfx: SfxChoice? = null,
    val effect: EffectSettings = EffectSettings()
)

data class TransitionSettings(
    val enabled: Boolean = true,
    val default: TransitionType = TransitionType.FADE,
    val durationMs: Int = 600
)

data class AudioSettings(
    val voiceUri: Uri? = null,
    val voiceVolume: Float = 1f,
    /** Automatically raises a quiet voice-over to a normal loudness before mixing. */
    val voiceBoost: Boolean = true,

    val musicUri: Uri? = null,
    val musicVolume: Float = 0.10f,
    val musicFadeInSec: Float = 1.5f,
    val musicFadeOutSec: Float = 3f,
    /** true = loop until the video ends; false = play once (silence afterwards). Always trimmed to video length. */
    val musicLoop: Boolean = true,

    val sfxEnabled: Boolean = true,
    val sfxChoice: SfxChoice = SfxChoice.AUTO,
    val sfxCustomUri: Uri? = null,
    val sfxVolume: Float = 0.03f
)

enum class CaptionPreset(val label: String) {
    CLASSIC("Classic (white, dark box)"),
    BOLD_YELLOW("Bold yellow"),
    CLEAN("Clean outline"),
    BLACK_BOX("White on black box"),
    PARCHMENT("History parchment")
}

enum class CaptionPosition(val label: String) {
    BOTTOM("Bottom"), MIDDLE("Middle"), TOP("Top")
}

data class CaptionSettings(
    val preset: CaptionPreset = CaptionPreset.CLASSIC,
    val sizeScale: Float = 1f,
    val position: CaptionPosition = CaptionPosition.BOTTOM
)

/** Applied to every picture before it is drawn. 0 / 1 / 1 means "off". */
data class EnhanceSettings(
    val sharpness: Float = 0f,
    val contrast: Float = 1f,
    val colour: Float = 1f
)

data class RenderRequest(
    val images: List<ImageItem>,
    val srtUri: Uri?,
    val wordSrtUri: Uri?,
    val resolution: Resolution = Resolution.HD_LANDSCAPE,
    val fps: Int = 24,
    val zoomMode: ZoomMode = ZoomMode.IN,
    val zoomStrength: Float = 0.12f,
    val showSubtitles: Boolean = true,
    val transitions: TransitionSettings = TransitionSettings(),
    val audio: AudioSettings = AudioSettings(),
    val caption: CaptionSettings = CaptionSettings(),
    val enhance: EnhanceSettings = EnhanceSettings(),
    val fallbackSceneMs: Int = 3000
)
