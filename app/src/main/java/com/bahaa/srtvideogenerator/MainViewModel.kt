package com.bahaa.srtvideogenerator

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

// ===== NEW CODE =====

data class UiState(
    val images: List<ImageItem> = emptyList(),
    val srtUri: Uri? = null, val srtName: String? = null,
    val wordSrtUri: Uri? = null, val wordSrtName: String? = null,
    val voiceName: String? = null, val musicName: String? = null, val sfxName: String? = null,
    val resolution: Resolution = Resolution.HD_LANDSCAPE,
    val zoomMode: ZoomMode = ZoomMode.IN,
    val showSubtitles: Boolean = true,
    val transitions: TransitionSettings = TransitionSettings(),
    val audio: AudioSettings = AudioSettings(),
    val rendering: Boolean = false,
    val progress: Float = 0f,
    val status: String = "",
    val resultUri: Uri? = null,
    val error: String? = null
)

fun displayName(context: Context, uri: Uri): String {
    val found: String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }
    return found ?: (uri.lastPathSegment ?: "file")
}

private val NUM = Regex("(\\d+)|(\\D+)")

/** a-2 < a-10 style ordering, so "a-01 … z-04" files come out in storyboard order. */
private fun naturalCompare(a: String, b: String): Int {
    val pa = NUM.findAll(a.lowercase()).map { it.value }.toList()
    val pb = NUM.findAll(b.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(pa.size, pb.size)) {
        val x = pa[i]
        val y = pb[i]
        val c = if (x[0].isDigit() && y[0].isDigit()) {
            val nx = x.trimStart('0').ifEmpty { "0" }
            val ny = y.trimStart('0').ifEmpty { "0" }
            if (nx.length != ny.length) nx.length - ny.length else nx.compareTo(ny)
        } else x.compareTo(y)
        if (c != 0) return c
    }
    return pa.size - pb.size
}

/** RECONSTRUCTED name (original: MainActivityKt.loadImageUris). Sorts naturally by file name. */
fun loadImageUris(context: Context, uris: List<Uri>): List<ImageItem> =
    uris.map { ImageItem(it, displayName(context, it)) }
        .sortedWith(Comparator { a, b -> naturalCompare(a.name, b.name) })

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var job: Job? = null

    private val ctx get() = getApplication<Application>()

    private fun persist(uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}
    }

    fun setImages(uris: List<Uri>) {
        uris.forEach(::persist)
        _state.update { it.copy(images = loadImageUris(ctx, uris), resultUri = null, error = null) }
    }
    fun setSrt(uri: Uri?) { uri?.let(::persist); _state.update { it.copy(srtUri = uri, srtName = uri?.let { u -> displayName(ctx, u) }) } }
    fun setWordSrt(uri: Uri?) {
        uri?.let(::persist)
        _state.update {
            it.copy(
                wordSrtUri = uri, wordSrtName = uri?.let { u -> displayName(ctx, u) },
                showSubtitles = if (uri != null) false else true // word captions replace full subtitles by default
            )
        }
    }
    fun setVoice(uri: Uri?) { uri?.let(::persist); _state.update { it.copy(audio = it.audio.copy(voiceUri = uri), voiceName = uri?.let { u -> displayName(ctx, u) }) } }
    fun setMusic(uri: Uri?) { uri?.let(::persist); _state.update { it.copy(audio = it.audio.copy(musicUri = uri), musicName = uri?.let { u -> displayName(ctx, u) }) } }
    fun setSfxFile(uri: Uri?) {
        uri?.let(::persist)
        _state.update {
            it.copy(
                audio = it.audio.copy(sfxCustomUri = uri, sfxChoice = if (uri != null) SfxChoice.CUSTOM else SfxChoice.AUTO),
                sfxName = uri?.let { u -> displayName(ctx, u) }
            )
        }
    }

    fun setResolution(r: Resolution) = _state.update { it.copy(resolution = r) }
    fun setZoom(z: ZoomMode) = _state.update { it.copy(zoomMode = z) }
    fun setShowSubtitles(v: Boolean) = _state.update { it.copy(showSubtitles = v) }
    fun setTransitions(t: TransitionSettings) = _state.update { it.copy(transitions = t) }
    fun setAudio(a: AudioSettings) = _state.update { it.copy(audio = a) }

    fun setImageTransition(index: Int, type: TransitionType?) = _state.update { s ->
        s.copy(images = s.images.mapIndexed { i, im -> if (i == index) im.copy(transition = type) else im })
    }

    fun clearOverrides() = _state.update { s -> s.copy(images = s.images.map { it.copy(transition = null) }) }

    /** Gives every image its own random transition (no two neighbours the same). */
    fun shuffleTransitions() = _state.update { s ->
        var prev: TransitionType? = null
        s.copy(images = s.images.map {
            val t = TransitionType.pool.filter { p -> p != prev }.random()
            prev = t
            it.copy(transition = t)
        })
    }

    // RECONSTRUCTED name (original: SrtVideoGeneratorScreen$generateVideo)
    fun generateVideo() {
        val s = _state.value
        if (s.rendering) return
        if (s.images.isEmpty()) {
            _state.update { it.copy(error = "Pick some images first.") }
            return
        }
        _state.update { it.copy(rendering = true, progress = 0f, status = "Starting…", resultUri = null, error = null) }
        job = viewModelScope.launch(Dispatchers.Default) {
            val self = coroutineContext[Job]
            val req = RenderRequest(
                images = s.images, srtUri = s.srtUri, wordSrtUri = s.wordSrtUri,
                resolution = s.resolution, zoomMode = s.zoomMode, showSubtitles = s.showSubtitles,
                transitions = s.transitions, audio = s.audio
            )
            try {
                val uri = NativeMediaGenerator(ctx).generate(
                    req,
                    onProgress = { p, msg -> _state.update { it.copy(progress = p, status = msg) } },
                    cancelled = { self?.isActive != true }
                )
                _state.update { it.copy(rendering = false, progress = 1f, status = "Done – saved to Movies/SRT Video Generator", resultUri = uri) }
            } catch (_: NativeMediaGenerator.Cancelled) {
                _state.update { it.copy(rendering = false, status = "Cancelled") }
            } catch (e: Throwable) {
                _state.update { it.copy(rendering = false, error = "${e.javaClass.simpleName}: ${e.message}") }
            }
        }
    }

    fun cancel() { job?.cancel() }
}
