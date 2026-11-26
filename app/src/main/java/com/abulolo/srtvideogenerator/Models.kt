package com.abulolo.srtvideogenerator

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
    DING("Ding"),
    STAMP("Stamp thud"),
    TYPEWRITER("Typewriter"),
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
    ZOOM_PULSE("Zoom pulse"),
    LEAVES("Falling leaves"),
    LIGHTNING("Lightning flashes"),
    FOG("Fog / mist"),
    LIGHT_RAYS("Light rays"),
    FILM_BURN("Film burn (orange flare)"),
    REC_DOT("Camera REC frame"),
    ASH("Falling ash"),
    FIREFLIES("Fireflies"),
    SUN_GLARE("Sun glare"),
    CRACKED_EDGE("Cracked glass corners"),
    NIGHT_TINT("Night blue tint"),
    SEPIA("Sepia old photo"),
    SMOKE("Drifting smoke"),
    CANDLE("Candlelight flicker"),
    LETTERBOX("Cinema black bars"),
    BIRDS("Flying birds")
}

/**
 * One image's overlay effect. [onlyFirstSec] == null means the effect stays for the whole image;
 * otherwise it fades out after that many seconds.
 */
data class EffectSettings(
    val type: EffectType = EffectType.NONE,
    val intensity: Float = 0.6f,
    val speed: Float = 1f,
    val onlyFirstSec: Float? = null,
    /** Where the effect is drawn: centre and size as fractions of the frame (1 x 1 = whole screen). */
    val regionX: Float = 0.5f,
    val regionY: Float = 0.5f,
    val regionW: Float = 1f,
    val regionH: Float = 1f,
    /** Size of the effect's own elements (used by the birds: flock size). 1 = normal. */
    val scale: Float = 1f,
    /** How many birds fly in the flock (birds effect only). */
    val count: Int = 8
)

/** Sound that plays under one scene (not a transition sound). */
enum class AmbienceType(val label: String) {
    NONE("None"),
    RAIN("Rain"),
    STORM("Storm (rain, wind, thunder)"),
    FIRE("Fire burning"),
    MIC_CRACKLE("Microphone crackle"),
    HOOVES("Horse hooves galloping"),
    WIND("Wind"),
    CUSTOM("My own sound file")
}

/**
 * [onlyFirstSec] == null: plays for the whole image; otherwise stops (with a fade) after that many seconds.
 * [customUri] is only used when [type] is CUSTOM.
 */
data class AmbienceSettings(
    val type: AmbienceType = AmbienceType.NONE,
    val volume: Float = 0.25f,
    val onlyFirstSec: Float? = null,
    val customUri: Uri? = null,
    val customName: String? = null
)

enum class PopKind { TEXT, IMAGE }

enum class PopAnim(val label: String) {
    BOUNCE("Bounce pop"),
    ZOOM("Zoom in"),
    SLIDE_UP("Slide up"),
    SLIDE_SIDE("Slide in from the side"),
    WOBBLE("Pop and wobble"),
    FADE("Fade in"),
    TYPEWRITER("Typewriter (text only)")
}

/** Direction a pop item travels while it is on screen (keyframe-style move from its start position). */
enum class PopMove(val label: String, val dx: Float, val dy: Float) {
    NONE("No movement", 0f, 0f),
    RIGHT("Move right", 1f, 0f),
    LEFT("Move left", -1f, 0f),
    UP("Move up", 0f, -1f),
    DOWN("Move down", 0f, 1f),
    UP_RIGHT("Move up and right", 0.7071f, -0.7071f),
    UP_LEFT("Move up and left", -0.7071f, -0.7071f),
    DOWN_RIGHT("Move down and right", 0.7071f, 0.7071f),
    DOWN_LEFT("Move down and left", -0.7071f, 0.7071f)
}

enum class PopStyle(val label: String) {
    PLAIN("Plain white"),
    BOLD_YELLOW("Bold yellow"),
    SCRIPT("Handwriting"),
    DATE_LABEL("Date label (parchment)"),
    STAMP_RED("Red stamp"),
    NAME_TAG("Name tag (lower third)"),
    HIGHLIGHTER("Highlighter")
}

/**
 * A pop-in text or picture on one scene. [startSec] and [durSec] are seconds counted from the start of
 * the scene. [x] / [y] are the centre as a fraction of the frame (y from the top). For text [size] is a
 * multiplier (1 = normal); for pictures it is the width as a fraction of the frame width.
 * [color] 0 = the style's own colour. [sound] NONE = silent (AUTO is treated as Pop).
 */
data class PopItem(
    val id: Long,
    val kind: PopKind,
    val text: String = "",
    val style: PopStyle = PopStyle.PLAIN,
    val imageUri: Uri? = null,
    val imageName: String? = null,
    val anim: PopAnim = PopAnim.BOUNCE,
    val startSec: Float = 0.5f,
    val durSec: Float = 2.5f,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val size: Float = 1f,
    val rotation: Float = 0f,
    val color: Int = 0,
    val sound: SfxChoice = SfxChoice.POP,
    val soundVolume: Float = 0.25f,
    val soundUri: Uri? = null,
    /** Movement: starts at [x],[y], travels [moveDist] (fraction of the frame) in [move] direction over the time it is shown. */
    val move: PopMove = PopMove.NONE,
    val moveDist: Float = 0.3f,
    val moveSmooth: Boolean = true,
    /** true = goes there and comes back. */
    val moveBack: Boolean = false,
    /** Size at the end of the move relative to the start (1 = unchanged). */
    val moveGrow: Float = 1f
)

data class ImageItem(
    val uri: Uri,
    val name: String,
    /** null = use the default transition. Image 0 has no incoming transition. */
    val transition: TransitionType? = null,
    /** null = use the default transition sound. NONE = silent for this image's scene change. */
    val sfx: SfxChoice? = null,
    val effect: EffectSettings = EffectSettings(),
    val ambience: AmbienceSettings = AmbienceSettings(),
    /** An animated clip instead of a still picture (its own sound is not used). */
    val isVideo: Boolean = false,
    val pops: List<PopItem> = emptyList(),
    /** More effects on the same scene, besides [effect]. */
    val extraEffects: List<EffectSettings> = emptyList()
)

/** Every effect that is really on this scene (main one first). */
fun ImageItem.allEffects(): List<EffectSettings> =
    (listOf(effect) + extraEffects).filter { it.type != EffectType.NONE }

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
    val sfxVolume: Float = 0.03f,
    /** Lower the music and scene sounds while the voice-over is speaking. */
    val duck: Boolean = true,
    val duckAmount: Float = 0.6f
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

enum class WatermarkCorner(val label: String) {
    TOP_LEFT("Top left"), TOP_RIGHT("Top right"), BOTTOM_LEFT("Bottom left"), BOTTOM_RIGHT("Bottom right")
}

/** A logo drawn on every frame of the video. [sizePct] = width as a fraction of the frame width. */
data class WatermarkSettings(
    val uri: Uri? = null,
    val name: String? = null,
    val corner: WatermarkCorner = WatermarkCorner.TOP_RIGHT,
    val sizePct: Float = 0.15f,
    val opacity: Float = 0.8f
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
    val fallbackSceneMs: Int = 3000,
    val watermark: WatermarkSettings = WatermarkSettings(),
    /** Test render: only scenes [testFirst]..[testLast] (0-based) are rendered. */
    val testFirst: Int? = null,
    val testLast: Int? = null
)
