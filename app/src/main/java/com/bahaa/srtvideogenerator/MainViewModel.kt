package com.bahaa.srtvideogenerator

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ===== NEW CODE =====

data class UiState(
    val images: List<ImageItem> = emptyList(),
    val imageSource: String? = null,
    val srtUri: Uri? = null, val srtName: String? = null,
    val wordSrtUri: Uri? = null, val wordSrtName: String? = null,
    val voiceName: String? = null, val musicName: String? = null, val sfxName: String? = null,
    val resolution: Resolution = Resolution.HD_LANDSCAPE,
    val fps: Int = 24,
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

/** Lists every image directly inside a folder chosen with "Use this folder". */
fun loadImagesFromFolder(context: Context, tree: Uri): List<ImageItem> {
    val folderId = DocumentsContract.getTreeDocumentId(tree)
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId)
    val out = ArrayList<ImageItem>()
    context.contentResolver.query(
        children,
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        ),
        null, null, null
    )?.use { c ->
        while (c.moveToNext()) {
            val mime = c.getString(2) ?: continue
            if (!mime.startsWith("image/")) continue
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
            out += ImageItem(uri, c.getString(1) ?: "image")
        }
    }
    return out.sortedWith(Comparator { a, b -> naturalCompare(a.name, b.name) })
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val ctx get() = getApplication<Application>()

    init {
        val saved = store.load()
        _state.update {
            it.copy(
                resolution = saved.resolution, fps = saved.fps, zoomMode = saved.zoomMode,
                showSubtitles = saved.showSubtitles, transitions = saved.transitions, audio = saved.audio
            )
        }
        // mirror the background renderer's progress into the screen state
        viewModelScope.launch {
            RenderController.state.collect { r ->
                _state.update {
                    it.copy(rendering = r.rendering, progress = r.progress, status = r.status,
                        resultUri = r.resultUri, error = r.error)
                }
            }
        }
    }

    private fun save() {
        val s = _state.value
        store.save(SavedSettings(s.resolution, s.fps, s.zoomMode, s.showSubtitles, s.transitions, s.audio))
    }

    private fun persist(uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}
    }

    // ---------------- files ----------------

    fun setImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        uris.forEach(::persist)
        _state.update {
            it.copy(images = loadImageUris(ctx, uris), imageSource = "${uris.size} selected (sorted by name)",
                resultUri = null, error = null)
        }
    }

    fun setImageFolder(tree: Uri?) {
        if (tree == null) return
        persist(tree)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val items = loadImagesFromFolder(ctx, tree)
                _state.update {
                    it.copy(
                        images = items,
                        imageSource = if (items.isEmpty()) "No images found in that folder"
                        else "Folder: ${items.size} images (sorted by name)",
                        resultUri = null, error = null
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Could not read folder: ${e.message}") }
            }
        }
    }

    fun setSrt(uri: Uri?) { uri?.let(::persist); _state.update { it.copy(srtUri = uri, srtName = uri?.let { u -> displayName(ctx, u) }) } }

    fun setWordSrt(uri: Uri?) {
        uri?.let(::persist)
        _state.update {
            it.copy(
                wordSrtUri = uri, wordSrtName = uri?.let { u -> displayName(ctx, u) },
                showSubtitles = uri == null // word captions replace full subtitles by default
            )
        }
        save()
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
        save()
    }

    // ---------------- settings (remembered) ----------------

    fun setResolution(r: Resolution) { _state.update { it.copy(resolution = r) }; save() }
    fun setFps(f: Int) { _state.update { it.copy(fps = f) }; save() }
    fun setZoom(z: ZoomMode) { _state.update { it.copy(zoomMode = z) }; save() }
    fun setShowSubtitles(v: Boolean) { _state.update { it.copy(showSubtitles = v) }; save() }
    fun setTransitions(t: TransitionSettings) { _state.update { it.copy(transitions = t) }; save() }
    fun setAudio(a: AudioSettings) { _state.update { it.copy(audio = a) }; save() }

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

    // ---------------- rendering (runs in RenderService) ----------------

    // RECONSTRUCTED name (original: SrtVideoGeneratorScreen$generateVideo)
    fun generateVideo() {
        val s = _state.value
        if (s.rendering) return
        if (s.images.isEmpty()) {
            _state.update { it.copy(error = "Pick some images first.") }
            return
        }
        val req = RenderRequest(
            images = s.images, srtUri = s.srtUri, wordSrtUri = s.wordSrtUri,
            resolution = s.resolution, fps = s.fps, zoomMode = s.zoomMode, showSubtitles = s.showSubtitles,
            transitions = s.transitions, audio = s.audio
        )
        if (RenderController.begin(req)) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, RenderService::class.java))
        }
    }

    fun cancel() { RenderController.cancel() }
}
