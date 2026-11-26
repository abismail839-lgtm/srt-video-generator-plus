package com.abulolo.srtvideogenerator.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import com.abulolo.srtvideogenerator.allEffects
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.abulolo.srtvideogenerator.AmbienceSettings
import com.abulolo.srtvideogenerator.AppTheme
import androidx.compose.foundation.shape.CircleShape
import com.abulolo.srtvideogenerator.AmbienceType
import com.abulolo.srtvideogenerator.AudioSettings
import com.abulolo.srtvideogenerator.displayName
import com.abulolo.srtvideogenerator.BitmapLoader
import com.abulolo.srtvideogenerator.CaptionPosition
import com.abulolo.srtvideogenerator.CaptionPreset
import com.abulolo.srtvideogenerator.EffectSettings
import com.abulolo.srtvideogenerator.EffectType
import com.abulolo.srtvideogenerator.EnhanceSettings
import com.abulolo.srtvideogenerator.MainViewModel
import com.abulolo.srtvideogenerator.UiState
import com.abulolo.srtvideogenerator.WatermarkCorner
import com.abulolo.srtvideogenerator.PopAnim
import com.abulolo.srtvideogenerator.PopItem
import com.abulolo.srtvideogenerator.PopMove
import com.abulolo.srtvideogenerator.PopKind
import com.abulolo.srtvideogenerator.PopStyle
import java.util.Locale
import com.abulolo.srtvideogenerator.Resolution
import com.abulolo.srtvideogenerator.SfxChoice
import com.abulolo.srtvideogenerator.TransitionType
import com.abulolo.srtvideogenerator.ZoomMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ===== NEW CODE. Name SrtVideoGeneratorScreen mirrors the original composable; the body is a rewrite. =====

@Composable
fun SrtVideoGeneratorScreen(vm: MainViewModel) {
    val s by vm.state.collectAsState()
    val ctx = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.setImages(it) }
    val srtPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::setSrt) }
    val wordPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::setWordSrt) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::setVoice) }
    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::setMusic) }
    val sfxPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::setSfxFile) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { vm.setImageFolder(it) }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> vm.setWatermarkFile(u) } }
    val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { vm.exportProject(it) }
    val backupOpener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.importProject(it) }
    var testFrom by remember { mutableStateOf("1") }
    var testTo by remember { mutableStateOf("5") }
    // Android 13+: ask once for the notification that shows render progress, then start anyway.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.generateVideo() }
    val startRender = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else vm.generateVideo()
    }

    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var effectEdit by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var ambienceIndex by remember { mutableStateOf<Int?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var selected by rememberSaveable { mutableStateOf(0) }
    var popEdit by remember { mutableStateOf<Pair<Int, PopItem>?>(null) }
    var pickForScene by remember { mutableStateOf(0) }
    val popImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.grantRead(uri)
            popEdit = pickForScene to PopItem(
                id = System.nanoTime(), kind = PopKind.IMAGE, imageUri = uri, imageName = displayName(ctx, uri),
                size = 0.35f, startSec = 0.5f, durSec = 2.5f
            )
        }
    }
    var tab by rememberSaveable { mutableStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var applyAllEffect by remember { mutableStateOf<EffectSettings?>(null) }
    var applyAllAmbience by remember { mutableStateOf<AmbienceSettings?>(null) }

    if (showSettings) {
        androidx.activity.compose.BackHandler { showSettings = false }
        SettingsPage(
            s = s, onBack = { showSettings = false },
            onSaveBackup = { backupSaver.launch("HU_project_backup.json") },
            onRestoreBackup = { backupOpener.launch(arrayOf("*/*")) },
            onNewProject = { confirm = "Start a new project? Your pictures and per-scene settings will be cleared (you can still Undo)." to { vm.newProject(); showSettings = false } }
        )
    } else Column(Modifier.fillMaxSize().statusBarsPadding()) {
        val ver = remember {
            try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("HU Video Maker", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                if (ver.isNotEmpty()) Text("v$ver", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { vm.undo() }, enabled = s.undoCount > 0) { Text("↩ ${s.undoCount}") }
            TextButton(onClick = { showSettings = true }) { Text("⚙") }
            Button(onClick = { tab = 4; startRender() }, enabled = !s.rendering && s.images.isNotEmpty()) { Text("Export") }
        }
        if (s.rendering) {
            LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
        }
        if (tab == 1) {
            if (s.images.isEmpty()) {
                Text("Add your pictures first (Story tab), then your scenes appear here.", Modifier.padding(16.dp))
                Spacer(Modifier.weight(1f))
            } else {
                val sel = selected.coerceIn(0, s.images.lastIndex)
                // The player stays here, pinned. Only what is below it scrolls.
                Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    ScenePlayer(vm = vm, s = s, index = sel, onFollow = { selected = it })
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SceneStrip(
                        s = s, selected = sel,
                        onPick = { i -> selected = i; s.sceneStartsMs.getOrNull(i)?.let { vm.seekAll(it) } }
                    )
                    SceneEditor(
                        vm = vm, s = s, index = sel,
                        onSelect = { i -> selected = i; s.sceneStartsMs.getOrNull(i)?.let { vm.seekAll(it) } },
                        onEffect = { scene, k -> effectEdit = scene to k },
                        onAmbience = { ambienceIndex = it },
                        onPreview = { previewIndex = it },
                        onConfirm = { msg, action -> confirm = msg to action },
                        onAddPopText = { idx ->
                            popEdit = idx to PopItem(id = System.nanoTime(), kind = PopKind.TEXT, text = "Your text", size = 1f)
                        },
                        onAddPopImage = { idx -> pickForScene = idx; popImagePicker.launch(arrayOf("image/*")) },
                        onEditPop = { idx, pop -> popEdit = idx to pop }
                    )
                }
            }
        } else LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        if (tab == 0) {
        item {
            if (s.images.isNotEmpty()) {
                val total = s.totalSecs.roundToInt()
                Text(
                    "${s.images.size} scenes" + (if (s.sceneSecs.isNotEmpty()) " · total ${total / 60}:${"%02d".format(total % 60)}" else "") +
                        " · your work is saved automatically",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        // ---------------- 1. your story (own design: tiles) ----------------
        item {
            var pickPictures by remember { mutableStateOf(false) }
            Section("Your story") {
                Text("Tap a tile to add it. A ticked tile is ready.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StoryTile("🖼", "Pictures", if (s.images.isEmpty()) null else (s.imageSource ?: "${s.images.size} scenes"),
                        Modifier.weight(1f), { pickPictures = true }, null)
                    StoryTile("🎙", "Voice-over", s.voiceName, Modifier.weight(1f),
                        { audioPicker.launch(arrayOf("audio/*")) }, if (s.audio.voiceUri != null) ({ vm.setVoice(null) }) else null)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StoryTile("📝", "Subtitles (SRT)", s.srtName, Modifier.weight(1f),
                        { srtPicker.launch(arrayOf("*/*")) }, if (s.srtUri != null) ({ vm.setSrt(null) }) else null)
                    StoryTile("✨", "Word by word", s.wordSrtName, Modifier.weight(1f),
                        { wordPicker.launch(arrayOf("*/*")) }, if (s.wordSrtUri != null) ({ vm.setWordSrt(null) }) else null)
                }
                Text("Full subtitles on the video", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(selected = s.showSubtitles, onClick = { vm.setShowSubtitles(true) }, label = { Text("Show") })
                    androidx.compose.material3.FilterChip(selected = !s.showSubtitles, onClick = { vm.setShowSubtitles(false) }, label = { Text("Hide") })
                }
                val missing = buildList {
                    if (s.images.isEmpty()) add("pictures")
                    if (s.srtUri == null) add("subtitles (SRT)")
                }
                Text(
                    if (missing.isEmpty()) "✓ Ready to render" else "Still needed: " + missing.joinToString(", "),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (missing.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (pickPictures) {
                AlertDialog(
                    onDismissRequest = { pickPictures = false },
                    title = { Text("Add pictures") },
                    text = { Text("Pick single pictures and clips, or a whole folder (loaded in name order).") },
                    confirmButton = {
                        TextButton(onClick = { pickPictures = false; imagePicker.launch(arrayOf("image/*", "video/*")) }) { Text("Pick pictures") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pickPictures = false; folderPicker.launch(null) }) { Text("Pick a folder") }
                    }
                )
            }
        }

        // ---------------- video ----------------
        item {
            Section("Video") {
                Dropdown("Size", s.resolution.label, Resolution.entries, { it.label }) { vm.setResolution(it) }
                Dropdown("Frame rate (24 renders faster)", "${s.fps} fps", listOf(24, 30), { "$it fps" }) { vm.setFps(it) }
                Dropdown("Zoom effect", s.zoomMode.label, ZoomMode.entries, { it.label }) { vm.setZoom(it) }
            }
        }

        }

        if (tab == 2) {
        // ---------------- transitions ----------------
        item {
            Section("Transitions") {
                LabeledSwitch("Enable transitions", s.transitions.enabled) { vm.setTransitions(s.transitions.copy(enabled = it)) }
                if (s.transitions.enabled) {
                    Dropdown("Default for all images", s.transitions.default.label, TransitionType.entries, { it.label }) {
                        vm.setTransitions(s.transitions.copy(default = it))
                    }
                    LabeledSlider("Duration", "${s.transitions.durationMs} ms", s.transitions.durationMs / 1000f, 0.2f..2f) {
                        vm.setTransitions(s.transitions.copy(durationMs = ((it * 10).roundToInt() * 100)))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            confirm = "Give all ${s.images.size} scenes a random transition? This replaces the transitions you chose (you can Undo)." to { vm.shuffleTransitions() }
                        }) { Text("Shuffle each image") }
                        OutlinedButton(onClick = {
                            confirm = "Reset the transitions you chose on ${s.images.count { it.transition != null }} scenes back to the default? (you can Undo)" to { vm.clearOverrides() }
                        }) { Text("Reset overrides") }
                    }
                    Text(
                        "Tip: tap an image row below to give it its own transition. Image 1 has no transition (it starts the video).",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // ---------------- subtitle style ----------------
        item {
            Section("Subtitle style") {
                Dropdown("Style", s.caption.preset.label, CaptionPreset.entries, { it.label }) {
                    vm.setCaption(s.caption.copy(preset = it))
                }
                LabeledSlider("Text size", "${(s.caption.sizeScale * 100).roundToInt()}%", s.caption.sizeScale, 0.6f..1.6f) {
                    vm.setCaption(s.caption.copy(sizeScale = (it * 20).roundToInt() / 20f))
                }
                Dropdown("Position", s.caption.position.label, CaptionPosition.entries, { it.label }) {
                    vm.setCaption(s.caption.copy(position = it))
                }
            }
        }

        // ---------------- enhance ----------------
        item {
            Section("Sharpen and colour") {
                LabeledSlider("Sharpness", "${(s.enhance.sharpness * 100).roundToInt()}%", s.enhance.sharpness, 0f..1f) {
                    vm.setEnhance(s.enhance.copy(sharpness = (it * 20).roundToInt() / 20f))
                }
                LabeledSlider("Contrast", "${(s.enhance.contrast * 100).roundToInt()}%", s.enhance.contrast, 0.8f..1.4f) {
                    vm.setEnhance(s.enhance.copy(contrast = (it * 20).roundToInt() / 20f))
                }
                LabeledSlider("Colour", "${(s.enhance.colour * 100).roundToInt()}%", s.enhance.colour, 0.6f..1.6f) {
                    vm.setEnhance(s.enhance.copy(colour = (it * 20).roundToInt() / 20f))
                }
                OutlinedButton(onClick = { vm.setEnhance(EnhanceSettings()) }) { Text("Reset to normal") }
                Text("This only sharpens and adjusts colour. It does not add detail like AI upscaling.", style = MaterialTheme.typography.bodySmall)
            }
        }

        // ---------------- logo ----------------
        item {
            Section("Logo on the video") {
                FileRow("Logo picture", s.watermark.name, { logoPicker.launch(arrayOf("image/*")) },
                    if (s.watermark.uri != null) ({ vm.setWatermarkFile(null) }) else null)
                if (s.watermark.uri != null) {
                    Dropdown("Corner", s.watermark.corner.label, WatermarkCorner.entries, { it.label }) {
                        vm.setWatermark(s.watermark.copy(corner = it))
                    }
                    LabeledSlider("Size", "${(s.watermark.sizePct * 100).roundToInt()}%", s.watermark.sizePct, 0.05f..0.4f) {
                        vm.setWatermark(s.watermark.copy(sizePct = (it * 100).roundToInt() / 100f))
                    }
                    LabeledSlider("Opacity", "${(s.watermark.opacity * 100).roundToInt()}%", s.watermark.opacity, 0.2f..1f) {
                        vm.setWatermark(s.watermark.copy(opacity = (it * 20).roundToInt() / 20f))
                    }
                }
            }
        }

        }

        if (tab == 3) {
        // ---------------- music ----------------
        item {
            Section("Voice and background music") {
                LabeledSlider("Voice volume", "${(s.audio.voiceVolume * 100).roundToInt()}%", s.audio.voiceVolume, 0f..3f) {
                    vm.setAudio(s.audio.copy(voiceVolume = it))
                }
                LabeledSwitch("Auto-boost a quiet voice-over", s.audio.voiceBoost) {
                    vm.setAudio(s.audio.copy(voiceBoost = it))
                }
                LabeledSwitch("Lower music and scene sounds while the voice speaks", s.audio.duck) {
                    vm.setAudio(s.audio.copy(duck = it))
                }
                if (s.audio.duck) {
                    LabeledSlider("How much to lower them", "${(s.audio.duckAmount * 100).roundToInt()}%", s.audio.duckAmount, 0.1f..0.9f) {
                        vm.setAudio(s.audio.copy(duckAmount = (it * 20).roundToInt() / 20f))
                    }
                }
                FileRow("Background music", s.musicName, { musicPicker.launch(arrayOf("audio/*")) }, if (s.audio.musicUri != null) ({ vm.setMusic(null) }) else null)
                if (s.audio.musicUri != null) {
                    LabeledSlider("Music volume", "${(s.audio.musicVolume * 100).roundToInt()}%", s.audio.musicVolume, 0f..1f) {
                        vm.setAudio(s.audio.copy(musicVolume = it))
                    }
                    LabeledSlider("Fade in", "%.1f s".format(s.audio.musicFadeInSec), s.audio.musicFadeInSec, 0f..10f) {
                        vm.setAudio(s.audio.copy(musicFadeInSec = it))
                    }
                    LabeledSlider("Fade out", "%.1f s".format(s.audio.musicFadeOutSec), s.audio.musicFadeOutSec, 0f..10f) {
                        vm.setAudio(s.audio.copy(musicFadeOutSec = it))
                    }
                    LabeledSwitch("Loop music to fill the video (off = play once)", s.audio.musicLoop) {
                        vm.setAudio(s.audio.copy(musicLoop = it))
                    }
                }
            }
        }

        // ---------------- sfx ----------------
        item {
            Section("Transition sounds") {
                LabeledSwitch("Play a sound at each scene change", s.audio.sfxEnabled) { vm.setAudio(s.audio.copy(sfxEnabled = it)) }
                if (s.audio.sfxEnabled) {
                    Dropdown("Sound", s.audio.sfxChoice.label, SfxChoice.entries.filter { it != SfxChoice.CUSTOM || s.audio.sfxCustomUri != null }, { it.label }) {
                        vm.setAudio(s.audio.copy(sfxChoice = it))
                    }
                    FileRow("Your own sound file", s.sfxName, { sfxPicker.launch(arrayOf("audio/*")) }, if (s.audio.sfxCustomUri != null) ({ vm.setSfxFile(null) }) else null)
                    LabeledSlider("Sound volume", "${(s.audio.sfxVolume * 100).roundToInt()}%", s.audio.sfxVolume, 0f..0.5f) {
                        vm.setAudio(s.audio.copy(sfxVolume = (it * 100).roundToInt() / 100f))
                    }
                }
            }
        }

        }

        if (tab == 4) {
        // ---------------- test render ----------------
        if (s.images.isNotEmpty() && !s.rendering) {
            item {
                Section("Quick test render") {
                    Text("Render only some scenes to check your effects and pops in a minute, instead of the whole video.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = testFrom, onValueChange = { testFrom = it.filter(Char::isDigit).take(5) }, label = { Text("From scene") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = testTo, onValueChange = { testTo = it.filter(Char::isDigit).take(5) }, label = { Text("To scene") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f)
                        )
                    }
                    Button(onClick = {
                        val a = (testFrom.toIntOrNull() ?: 1).coerceIn(1, s.images.size)
                        val b = (testTo.toIntOrNull() ?: a).coerceIn(1, s.images.size)
                        vm.generateVideo(minOf(a, b), maxOf(a, b))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Render this test") }
                }
            }
        }

        // ---------------- generate ----------------
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.rendering) {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Text("${(s.progress * 100).roundToInt()}%  ${s.status}")
                    OutlinedButton(onClick = { vm.cancel() }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                    Text("You can leave the app or turn the screen off. Progress shows in the notification.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Button(onClick = { startRender() }, modifier = Modifier.fillMaxWidth()) { Text("Generate video") }
                    if (s.status.isNotEmpty()) Text(s.status)
                    s.resultUri?.let { uri ->
                        OutlinedButton(onClick = {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        }, modifier = Modifier.fillMaxWidth()) { Text("Play video") }
                    }
                    s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        }

        }
        NavigationBar {
            val tabs = listOf("🖼" to "Story", "🎞" to "Scenes", "🎨" to "Look", "🔊" to "Audio", "📤" to "Export")
            tabs.forEachIndexed { i, (icon, label) ->
                NavigationBarItem(
                    selected = tab == i, onClick = { tab = i },
                    icon = { Text(icon) }, label = { Text(label, maxLines = 1) }
                )
            }
        }
    }

    previewIndex?.let { i ->
        PreviewDialog(vm, i) { previewIndex = null }
    }
    effectEdit?.let { (i, k) ->
        EffectDialog(vm, i, k, onAll = { applyAllEffect = it }) { effectEdit = null }
    }
    ambienceIndex?.let { i ->
        AmbienceDialog(vm, i, onAll = { applyAllAmbience = it }) { ambienceIndex = null }
    }

    popEdit?.let { (scene, item) ->
        PopDialog(vm, scene, item) { popEdit = null }
    }
    confirm?.let { (msg, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Are you sure?") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { confirm = null; action() }) { Text("Yes, do it") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }
        )
    }
    applyAllEffect?.let { e ->
        val filled = s.images.count { it.allEffects().isNotEmpty() }
        ApplyAllDialog(
            what = if (e.type == EffectType.NONE) "REMOVE the effect from" else "put \"${e.type.label}\" on",
            total = s.images.size, filled = filled, filledWhat = "already have an effect",
            onlyEmpty = { vm.applyEffectToAll(e, true); applyAllEffect = null },
            overwrite = { vm.applyEffectToAll(e, false); applyAllEffect = null },
            cancel = { applyAllEffect = null }
        )
    }
    applyAllAmbience?.let { a ->
        val filled = s.images.count { it.ambience.type != AmbienceType.NONE }
        ApplyAllDialog(
            what = if (a.type == AmbienceType.NONE) "REMOVE the scene sound from" else "put \"${a.type.label}\" on",
            total = s.images.size, filled = filled, filledWhat = "already have a scene sound",
            onlyEmpty = { vm.applyAmbienceToAll(a, true); applyAllAmbience = null },
            overwrite = { vm.applyAmbienceToAll(a, false); applyAllAmbience = null },
            cancel = { applyAllAmbience = null }
        )
    }
}

@Composable
private fun ApplyAllDialog(
    what: String, total: Int, filled: Int, filledWhat: String,
    onlyEmpty: () -> Unit, overwrite: () -> Unit, cancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = cancel,
        title = { Text("Apply to all scenes?") },
        confirmButton = {},
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This will $what scenes. $filled of your $total scenes $filledWhat.")
                Button(onClick = onlyEmpty, modifier = Modifier.fillMaxWidth()) { Text("Only scenes that are empty (safe)") }
                OutlinedButton(onClick = overwrite, modifier = Modifier.fillMaxWidth()) { Text("Overwrite ALL $total scenes") }
                TextButton(onClick = cancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                Text("You can still tap Undo afterwards.", style = MaterialTheme.typography.bodySmall)
            }
        }
    )
}

@Composable
private fun AmbienceDialog(vm: MainViewModel, index: Int, onAll: (AmbienceSettings) -> Unit, onClose: () -> Unit) {
    val s by vm.state.collectAsState()
    val ctx = LocalContext.current
    if (index !in s.images.indices) { onClose(); return }
    var a by remember { mutableStateOf(s.images[index].ambience) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.grantRead(uri)
            a = a.copy(type = AmbienceType.CUSTOM, customUri = uri, customName = displayName(ctx, uri))
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Scene sound for image ${index + 1}") },
        confirmButton = { TextButton(onClick = { vm.setImageAmbience(index, a); onClose() }) { Text("Apply") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onAll(a); onClose() }) { Text("Apply to all") }
                TextButton(onClick = onClose) { Text("Cancel") }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Plays under this picture. It is separate from the transition sound.", style = MaterialTheme.typography.bodySmall)
                OptionGrid("Sound", AmbienceType.entries.filter { it != AmbienceType.CUSTOM || a.customUri != null }, a.type, { it.label }, 3) { a = a.copy(type = it) }
                OutlinedButton(onClick = { picker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(a.customName?.let { "Own file: $it" } ?: "Choose my own sound file", maxLines = 1)
                }
                if (a.type != AmbienceType.NONE) {
                    LabeledSlider("Volume", "${(a.volume * 100).roundToInt()}%", a.volume, 0f..1f) {
                        a = a.copy(volume = (it * 100).roundToInt() / 100f)
                    }
                    LabeledSwitch("Only at the start of the image", a.onlyFirstSec != null) {
                        a = a.copy(onlyFirstSec = if (it) 3f else null)
                    }
                    a.onlyFirstSec?.let { sec ->
                        LabeledSlider("Stays for", "%.1f s".format(sec), sec, 1f..10f) {
                            a = a.copy(onlyFirstSec = (it * 2).roundToInt() / 2f)
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun EffectDialog(vm: MainViewModel, index: Int, effIdx: Int, onAll: (EffectSettings) -> Unit, onClose: () -> Unit) {
    val s by vm.state.collectAsState()
    val ctx = LocalContext.current
    if (index !in s.images.indices) { onClose(); return }
    val effs = s.images[index].allEffects()
    var e by remember { mutableStateOf(effs.getOrNull(effIdx) ?: EffectSettings()) }
    val params = remember { EffectPreviewParams() }
    params.settings = e
    params.others = effs.filterIndexed { n, _ -> n != effIdx }
    val bmp by produceState<Bitmap?>(null, s.images[index].uri) {
        value = withContext(Dispatchers.IO) { try { BitmapLoader.decode(ctx, s.images[index].uri, 640) } catch (_: Exception) { null } }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (effIdx < effs.size) "Effect ${effIdx + 1} of scene ${index + 1}" else "New effect for scene ${index + 1}") },
        confirmButton = {
            TextButton(onClick = {
                val l = effs.toMutableList()
                if (effIdx < l.size) l[effIdx] = e else l.add(e)
                vm.setSceneEffects(index, l)
                onClose()
            }) { Text("Apply") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onAll(e); onClose() }) { Text("Apply to all") }
                TextButton(onClick = onClose) { Text("Cancel") }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // The preview stays pinned here; only the controls below scroll.
                val b = bmp
                if (b == null) Text("Loading…")
                else {
                    val showBox = e.type != EffectType.NONE && e.type != EffectType.SHAKE && e.type != EffectType.ZOOM_PULSE
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                            .pointerInput(showBox) {
                                if (showBox) detectTransformGestures { _, pan, zoom, _ ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    e = e.copy(
                                        regionX = (e.regionX + pan.x / w).coerceIn(0f, 1f),
                                        regionY = (e.regionY + pan.y / h).coerceIn(0f, 1f),
                                        regionW = (e.regionW * zoom).coerceIn(0.1f, 1f),
                                        regionH = (e.regionH * zoom).coerceIn(0.1f, 1f)
                                    )
                                }
                            }
                            .drawWithContent {
                                drawContent()
                                if (showBox && (e.regionW < 0.99f || e.regionH < 0.99f)) {
                                    drawRect(
                                        Color.White,
                                        topLeft = Offset((e.regionX - e.regionW / 2f) * size.width, (e.regionY - e.regionH / 2f) * size.height),
                                        size = Size(e.regionW * size.width, e.regionH * size.height),
                                        style = Stroke(width = 3f)
                                    )
                                }
                            }
                    ) {
                        AndroidView(
                            factory = { EffectPreviewView(it, b, params) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (showBox) Text("Drag the picture to move the effect. Pinch with two fingers to make it bigger or smaller.", style = MaterialTheme.typography.bodySmall)
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionGrid("Effect", EffectType.entries, e.type, { it.label }, 3) { e = e.copy(type = it) }
                    if (e.type != EffectType.NONE) {
                        LabeledSlider("Strength", "${(e.intensity * 100).roundToInt()}%", e.intensity, 0f..1f) { e = e.copy(intensity = it) }
                        LabeledSlider("Speed", "%.1fx".format(e.speed), e.speed, 0.3f..3f) { e = e.copy(speed = (it * 10).roundToInt() / 10f) }
                        LabeledSwitch("Only at the start of the image", e.onlyFirstSec != null) {
                            e = e.copy(onlyFirstSec = if (it) 3f else null)
                        }
                        e.onlyFirstSec?.let { sec ->
                            LabeledSlider("Stays for", "%.1f s".format(sec), sec, 1f..10f) {
                                e = e.copy(onlyFirstSec = (it * 2).roundToInt() / 2f)
                            }
                        }
                        if (e.type == EffectType.BIRDS) {
                            LabeledSlider("Number of birds", "${e.count}", e.count.toFloat(), 1f..40f) { e = e.copy(count = it.roundToInt()) }
                            LabeledSlider("Bird size (zoom)", "${(e.scale * 100).roundToInt()}%", e.scale, 0.4f..3f) { e = e.copy(scale = (it * 20).roundToInt() / 20f) }
                        }
                        if (e.type != EffectType.SHAKE && e.type != EffectType.ZOOM_PULSE) {
                            Text("Size and place of the effect", style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { e = e.copy(regionX = 0.5f, regionY = 0.5f, regionW = 1f, regionH = 1f) }) { Text("Fill the whole screen") }
                            LabeledSlider("Width", "${(e.regionW * 100).roundToInt()}%", e.regionW, 0.1f..1f) { e = e.copy(regionW = (it * 100).roundToInt() / 100f) }
                            LabeledSlider("Height", "${(e.regionH * 100).roundToInt()}%", e.regionH, 0.1f..1f) { e = e.copy(regionH = (it * 100).roundToInt() / 100f) }
                            LabeledSlider("Left to right", "${(e.regionX * 100).roundToInt()}%", e.regionX, 0f..1f) { e = e.copy(regionX = (it * 100).roundToInt() / 100f) }
                            LabeledSlider("Up to down", "${(e.regionY * 100).roundToInt()}%", e.regionY, 0f..1f) { e = e.copy(regionY = (it * 100).roundToInt() / 100f) }
                        }
                    }
                }
            }
        },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.96f)
    )
}

// ---------------------------------------------------------------- dialogs

@Composable
private fun PreviewDialog(vm: MainViewModel, index: Int, onClose: () -> Unit) {
    val s by vm.state.collectAsState()
    val ctx = LocalContext.current
    val images = s.images
    if (index !in 1 until images.size) { onClose(); return }
    val type = if (!s.transitions.enabled) TransitionType.NONE else images[index].transition ?: s.transitions.default
    val pair by produceState<Pair<Bitmap, Bitmap>?>(null, images[index].uri, images[index - 1].uri) {
        value = withContext(Dispatchers.IO) {
            BitmapLoader.decode(ctx, images[index - 1].uri, 640) to BitmapLoader.decode(ctx, images[index].uri, 640)
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        title = { Text("Image $index → ${index + 1}: ${type.label}") },
        text = {
            val p = pair
            if (p == null) Text("Loading…")
            else key(type, s.transitions.durationMs) {
                AndroidView(
                    factory = { TransitionPreviewView(it, p.first, p.second, type, s.transitions.durationMs) },
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                )
            }
        }
    )
}

// ---------------------------------------------------------------- small components

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun FileRow(label: String, name: String?, onPick: () -> Unit, onClear: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(name ?: "none", style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        OutlinedButton(onClick = onPick) { Text(if (name == null) "Choose" else "Change") }
        if (onClear != null) TextButton(onClick = onClear) { Text("Clear") }
    }
}

@Composable
private fun LabeledSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LabeledSlider(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row { Text(label, Modifier.weight(1f)); Text(valueText) }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun <T> Dropdown(label: String, current: String, options: List<T>, labelOf: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(current, maxLines = 1) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { o ->
                    DropdownMenuItem(text = { Text(labelOf(o)) }, onClick = { open = false; onSelect(o) })
                }
            }
        }
    }
}

private object ThumbCache {
    val cache = android.util.LruCache<String, Bitmap>(120)
}

@Composable
private fun Thumb(uri: Uri, size: Dp = 64.dp) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(ThumbCache.cache.get(uri.toString()), uri) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                try { BitmapLoader.decode(ctx, uri, 200).also { ThumbCache.cache.put(uri.toString(), it) } } catch (_: Exception) { null }
            }
        }
    }
    val b = bmp
    if (b != null) Image(b.asImageBitmap(), null, Modifier.size(size), contentScale = ContentScale.Crop)
    else Box(Modifier.size(size).background(Color(0x33888888)))
}

// ---------------------------------------------------------------- scene editor

@Composable
private fun ScenePlayer(vm: MainViewModel, s: UiState, index: Int, onFollow: (Int) -> Unit) {
    val ctx = LocalContext.current
    val pos by vm.playPos.collectAsState()
    val starts = s.sceneStartsMs
    val totalMs = (s.totalSecs * 1000f).toLong().coerceAtLeast(1L)
    var scrubbed by remember { mutableStateOf(false) }
    LaunchedEffect(pos) {
        if ((s.playingAll || scrubbed) && starts.isNotEmpty()) {
            val idx = starts.indexOfLast { it <= pos }.coerceAtLeast(0).coerceAtMost(s.images.lastIndex)
            if (idx != index) onFollow(idx)
        }
    }
    val img = s.images[index]
    val params = remember { EffectPreviewParams() }
    params.settings = img.effect
    params.others = img.extraEffects
    val bmp by produceState<Bitmap?>(null, img.uri) {
        value = withContext(Dispatchers.IO) { try { BitmapLoader.decode(ctx, img.uri, 640) } catch (_: Exception) { null } }
    }
    fun mmss(ms: Long): String { val sec = (ms / 1000).toInt(); return "${sec / 60}:${"%02d".format(sec % 60)}" }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.fillMaxWidth(0.62f).align(Alignment.CenterHorizontally).aspectRatio(16f / 9f).background(Color.Black)) {
            if (img.isVideo) {
                // A clip plays here (effects are added in the final video, not in this small preview).
                key(img.uri) {
                    AndroidView(
                        factory = { c ->
                            android.widget.VideoView(c).apply {
                                setVideoURI(img.uri)
                                setOnPreparedListener { mp -> mp.isLooping = true; mp.setVolume(0f, 0f); start() }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                val b = bmp
                if (b != null) {
                    key(index, b) {
                        AndroidView(
                            factory = { EffectPreviewView(it, b, params) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else Text("Loading preview…", color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
        }
        if (s.audio.voiceUri == null || starts.isEmpty()) {
            Text("Add the voice-over and the SRT (Story tab) to listen to your whole voice-over here.", style = MaterialTheme.typography.bodySmall)
        } else {
            Slider(
                value = pos.coerceIn(0L, totalMs).toFloat(),
                onValueChange = { scrubbed = true; vm.seekAll(it.toLong()) },
                valueRange = 0f..totalMs.toFloat()
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    scrubbed = true
                    vm.seekAll(starts.getOrNull((index - 1).coerceAtLeast(0)) ?: 0L)
                }) { Text("⏮") }
                Button(onClick = {
                    if (s.playingAll) vm.pauseAll()
                    else { scrubbed = true; vm.playAll() }
                }, modifier = Modifier.weight(1f)) { Text(if (s.playingAll) "⏸ Pause" else "▶ Play voice-over") }
                OutlinedButton(onClick = {
                    scrubbed = true
                    vm.seekAll(starts.getOrNull((index + 1).coerceAtMost(s.images.lastIndex)) ?: 0L)
                }) { Text("⏭") }
            }
        }
        Text(
            "Scene ${index + 1} of ${s.images.size}" + (if (starts.isNotEmpty()) "  ·  ${mmss(pos)} / ${mmss(totalMs)}" else ""),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SceneStrip(s: UiState, selected: Int, onPick: (Int) -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var tile by rememberSaveable { mutableStateOf(84f) }
    var jump by remember { mutableStateOf("") }
    LaunchedEffect(selected) { // keep the chosen scene in view
        val first = listState.firstVisibleItemIndex
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: first
        if (selected < first || selected > last) listState.animateScrollToItem(selected)
    }
    Section("Scenes (swipe sideways, pinch to zoom)") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = jump, onValueChange = { jump = it.filter(Char::isDigit).take(5) },
                label = { Text("Go to scene") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                val n = (jump.toIntOrNull() ?: return@Button).coerceIn(1, s.images.size) - 1
                onPick(n)
                scope.launch { listState.animateScrollToItem(n) }
            }) { Text("Go") }
        }
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        if (ev.changes.size >= 2) {
                            val z = ev.calculateZoom()
                            if (z != 1f) {
                                tile = (tile * z).coerceIn(52f, 180f)
                                ev.changes.forEach { it.consume() }
                            }
                        }
                    } while (ev.changes.any { it.pressed })
                }
            },
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(s.images) { i, img ->
                val sel = i == selected
                Box(
                    Modifier
                        .size(tile.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else Color(0x55888888), RoundedCornerShape(8.dp))
                        .clickable { onPick(i) }
                ) {
                    Thumb(img.uri, 180.dp)
                    val marks = (if (img.allEffects().isNotEmpty()) "✨" else "") +
                        (if (img.ambience.type != AmbienceType.NONE) "🎧" else "") +
                        (if (img.pops.isNotEmpty()) "💬" else "")
                    Text(
                        "${i + 1}${if (img.isVideo) "🎬" else ""}" + (if (tile >= 70f) marks else ""),
                        Modifier.align(Alignment.BottomStart).background(Color(0xAA000000)).padding(horizontal = 4.dp),
                        color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1
                    )
                }
            }
        }
        Text("✨ effect   🎧 scene sound   💬 pop   🎬 clip", style = MaterialTheme.typography.bodySmall)
    }
}

/** A square-tile picker: several choices per row, the chosen one is outlined. */
@Composable
private fun <T> OptionGrid(label: String, options: List<T>, selected: T, labelOf: (T) -> String, columns: Int = 3, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        options.chunked(columns).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowItems.forEach { o ->
                    val sel = o == selected
                    Box(
                        Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(10.dp))
                            .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else Color(0x55888888), RoundedCornerShape(10.dp))
                            .clickable { onSelect(o) }.padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(labelOf(o), style = MaterialTheme.typography.labelSmall, maxLines = 3, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SettingsPage(s: UiState, onBack: () -> Unit, onSaveBackup: () -> Unit, onRestoreBackup: () -> Unit, onNewProject: () -> Unit) {
    val ctx = LocalContext.current
    val ver = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("◀ Back") }
            Text("Settings", style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Section("App colours") {
                OptionGrid("Accent colour (buttons, sliders, highlights)", AppTheme.accents.indices.toList(), AppTheme.accentIndex,
                    { AppTheme.accents[it].name }, 3) { AppTheme.accentIndex = it; AppTheme.save(ctx) }
                OptionGrid("Background", AppTheme.backgrounds.indices.toList(), AppTheme.backgroundIndex,
                    { AppTheme.backgrounds[it].name }, 3) { AppTheme.backgroundIndex = it; AppTheme.save(ctx) }
            }
            Section("Project") {
                Text("Your project is already saved automatically. A backup file is an extra copy you can keep.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSaveBackup, enabled = s.images.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Save backup") }
                    OutlinedButton(onClick = onRestoreBackup, modifier = Modifier.weight(1f)) { Text("Restore backup") }
                }
                s.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(onClick = onNewProject, enabled = s.images.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Start a new project") }
            }
            Section("About") { Text("HU Video Maker v$ver") }
        }
    }
}

// ---------------------------------------------------------------- scene editor

@Composable
private fun SceneEditor(
    vm: MainViewModel, s: UiState, index: Int,
    onSelect: (Int) -> Unit, onEffect: (Int, Int) -> Unit, onAmbience: (Int) -> Unit, onPreview: (Int) -> Unit,
    onConfirm: (String, () -> Unit) -> Unit,
    onAddPopText: (Int) -> Unit, onAddPopImage: (Int) -> Unit, onEditPop: (Int, PopItem) -> Unit
) {
    val img = s.images[index]
    val replacePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.replaceImage(index, it) }
    var part by rememberSaveable { mutableStateOf(0) }
    Section("Edit scene ${index + 1} of ${s.images.size}") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onSelect((index - 1).coerceAtLeast(0)) }, enabled = index > 0) { Text("◀ Back") }
            OutlinedButton(onClick = { onSelect((index + 1).coerceAtMost(s.images.lastIndex)) }, enabled = index < s.images.lastIndex) { Text("Next ▶") }
        }
        Text(
            "${if (img.isVideo) "🎬 " else ""}${img.name}" + (s.sceneSecs.getOrNull(index)?.let { "  ·  %.1f s".format(it) } ?: ""),
            style = MaterialTheme.typography.bodyMedium, maxLines = 1
        )
        OutlinedButton(onClick = { replacePicker.launch(arrayOf("image/*", "video/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text("Replace this scene's picture (keeps all its settings)")
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Effect", "Transition", "Sound", "Pops", "Copy").forEachIndexed { i, name ->
                androidx.compose.material3.FilterChip(selected = part == i, onClick = { part = i }, label = { Text(name) })
            }
        }

        if (part == 0) {
            val effs = img.allEffects()
            effs.forEachIndexed { k, fx ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✨ ${fx.type.label}", modifier = Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { onEffect(index, k) }) { Text("Edit") }
                    TextButton(onClick = {
                        onConfirm("Remove \"${fx.type.label}\" from scene ${index + 1}? (you can Undo)") {
                            vm.setSceneEffects(index, effs.filterIndexed { n, _ -> n != k })
                        }
                    }) { Text("Remove") }
                }
            }
            OutlinedButton(onClick = { onEffect(index, effs.size) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (effs.isEmpty()) "+ Add effect" else "+ Add another effect")
            }
            Text("You can put several effects on one scene. The player above shows them together.", style = MaterialTheme.typography.bodySmall)
        }

        if (part == 1) {
            if (index == 0) {
                Text("Opening scene (no transition or sound before it).", style = MaterialTheme.typography.bodySmall)
            } else {
                if (s.transitions.enabled) {
                    val label = img.transition?.label ?: "Default (${s.transitions.default.label})"
                    Dropdown("Transition in", label, listOf<TransitionType?>(null) + TransitionType.entries, { it?.label ?: "Default" }) {
                        vm.setImageTransition(index, it)
                    }
                    TextButton(onClick = { onPreview(index) }) { Text("Preview this transition") }
                } else Text("Transitions are off (Look tab).", style = MaterialTheme.typography.bodySmall)
                if (s.audio.sfxEnabled) {
                    val opts = listOf<SfxChoice?>(null) + SfxChoice.entries.filter { it != SfxChoice.CUSTOM || s.audio.sfxCustomUri != null }
                    Dropdown("Transition sound", img.sfx?.label ?: "Default (${s.audio.sfxChoice.label})", opts, { it?.label ?: "Default" }) {
                        vm.setImageSfx(index, it)
                    }
                }
            }
        }

        if (part == 2) {
            OutlinedButton(onClick = { onAmbience(index) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (img.ambience.type == AmbienceType.NONE) "Add scene sound" else "Scene sound: ${img.ambience.type.label}", maxLines = 1)
            }
        }

        if (part == 3) {
            Text("Pop text and pictures on this scene", style = MaterialTheme.typography.titleSmall)
            img.pops.forEach { pop ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (pop.kind == PopKind.TEXT) "💬 ${pop.text.take(22)}" else "🖼 ${pop.imageName ?: "picture"}") +
                            "  @ ${String.format(Locale.US, "%.1f", pop.startSec)} s",
                        modifier = Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { onEditPop(index, pop) }) { Text("Edit") }
                    TextButton(onClick = { onConfirm("Delete this pop from scene ${index + 1}? (you can Undo)") { vm.deletePop(index, pop.id) } }) { Text("Delete") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onAddPopText(index) }, modifier = Modifier.weight(1f)) { Text("+ Pop text") }
                OutlinedButton(onClick = { onAddPopImage(index) }, modifier = Modifier.weight(1f)) { Text("+ Pop picture") }
            }
        }

        if (part == 4) {
            var from by remember(index) { mutableStateOf((index + 2).coerceAtMost(s.images.size).toString()) }
            var to by remember(index) { mutableStateOf((index + 2).coerceAtMost(s.images.size).toString()) }
            var cT by remember { mutableStateOf(true) }
            var cS by remember { mutableStateOf(true) }
            var cE by remember { mutableStateOf(true) }
            var cA by remember { mutableStateOf(true) }
            var cP by remember { mutableStateOf(true) }
            Text("Copy this scene's settings to other scenes", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = from, onValueChange = { from = it.filter(Char::isDigit).take(5) }, label = { Text("From scene") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = to, onValueChange = { to = it.filter(Char::isDigit).take(5) }, label = { Text("To scene") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(cT, { cT = it }); Text("Transition") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(cS, { cS = it }); Text("Transition sound") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(cE, { cE = it }); Text("Effect") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(cA, { cA = it }); Text("Scene sound") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(cP, { cP = it }); Text("Pop text and pictures") }
            Button(onClick = {
                val a = ((from.toIntOrNull() ?: 1).coerceIn(1, s.images.size)) - 1
                val z = ((to.toIntOrNull() ?: a + 1).coerceIn(1, s.images.size)) - 1
                val first = minOf(a, z)
                val last = maxOf(a, z)
                val parts = buildSet {
                    if (cT) add(MainViewModel.CopyPart.TRANSITION)
                    if (cS) add(MainViewModel.CopyPart.SOUND)
                    if (cE) add(MainViewModel.CopyPart.EFFECT)
                    if (cA) add(MainViewModel.CopyPart.SCENE_SOUND)
                    if (cP) add(MainViewModel.CopyPart.POPS)
                }
                if (parts.isNotEmpty()) {
                    onConfirm("Copy the chosen settings of scene ${index + 1} onto scenes ${first + 1} to ${last + 1}? This replaces what those scenes have now (you can Undo).") {
                        vm.copySceneSettings(index, first, last, parts)
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Copy to these scenes") }
        }
    }
}


// ---------------------------------------------------------------- pop editor

private val POP_COLOURS = listOf(
    "Style default" to 0,
    "White" to 0xFFFFFFFF.toInt(),
    "Yellow" to 0xFFFFD400.toInt(),
    "Red" to 0xFFD42020.toInt(),
    "Orange" to 0xFFFF8A1E.toInt(),
    "Cyan" to 0xFF30D5F0.toInt(),
    "Green" to 0xFF3CC85A.toInt(),
    "Black" to 0xFF000000.toInt()
)

@Composable
private fun PopDialog(vm: MainViewModel, scene: Int, initial: PopItem, onClose: () -> Unit) {
    val s by vm.state.collectAsState()
    val ctx = LocalContext.current
    if (scene !in s.images.indices) { onClose(); return }
    val img = s.images[scene]
    var p by remember { mutableStateOf(initial) }
    var startTxt by remember { mutableStateOf(String.format(Locale.US, "%.1f", initial.startSec)) }
    var durTxt by remember { mutableStateOf(String.format(Locale.US, "%.1f", initial.durSec)) }
    val params = remember { PopPreviewParams() }
    params.item = p
    val bg by produceState<Bitmap?>(null, img.uri) {
        value = withContext(Dispatchers.IO) { try { BitmapLoader.decode(ctx, img.uri, 640) } catch (_: Exception) { null } }
    }
    val imgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { vm.grantRead(uri); p = p.copy(imageUri = uri, imageName = displayName(ctx, uri)) }
    }
    val sndPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { vm.grantRead(uri); p = p.copy(sound = SfxChoice.CUSTOM, soundUri = uri) }
    }
    fun num(t: String): Float? = t.replace(',', '.').toFloatOrNull()
    val sceneLen = s.sceneSecs.getOrNull(scene)

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (p.kind == PopKind.TEXT) "Pop text (scene ${scene + 1})" else "Pop picture (scene ${scene + 1})") },
        confirmButton = { TextButton(onClick = { vm.upsertPop(scene, p); onClose() }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val b = bg
                if (b == null) Text("Loading preview…")
                else AndroidView(
                    factory = { PopPreviewView(it, b, params) },
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                )
                Text("The preview repeats and shows exactly how it will look.", style = MaterialTheme.typography.bodySmall)

                if (p.kind == PopKind.TEXT) {
                    OutlinedTextField(
                        value = p.text, onValueChange = { p = p.copy(text = it) }, label = { Text("Type your text") },
                        minLines = 2, modifier = Modifier.fillMaxWidth()
                    )
                    Dropdown("Style", p.style.label, PopStyle.entries, { it.label }) { p = p.copy(style = it) }
                    Dropdown("Colour", POP_COLOURS.firstOrNull { it.second == p.color }?.first ?: "Custom", POP_COLOURS, { it.first }) {
                        p = p.copy(color = it.second)
                    }
                    LabeledSlider("Text size", "${(p.size * 100).roundToInt()}%", p.size, 0.3f..3f) { p = p.copy(size = (it * 20).roundToInt() / 20f) }
                } else {
                    Text("Picture: ${p.imageName ?: "none"}", maxLines = 1)
                    OutlinedButton(onClick = { imgPicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Change picture") }
                    LabeledSlider("Picture size", "${(p.size * 100).roundToInt()}%", p.size, 0.05f..1f) { p = p.copy(size = (it * 100).roundToInt() / 100f) }
                }

                Dropdown("Animation", p.anim.label, PopAnim.entries.filter { it != PopAnim.TYPEWRITER || p.kind == PopKind.TEXT }, { it.label }) {
                    p = p.copy(anim = it)
                }

                Text(
                    "Timing (seconds counted from the start of this scene" + (sceneLen?.let { ", the scene is ${String.format(Locale.US, "%.1f", it)} s long" } ?: "") + ")",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startTxt, onValueChange = { t -> startTxt = t.filter { it.isDigit() || it == '.' || it == ',' }.take(6); num(startTxt)?.let { p = p.copy(startSec = it.coerceAtLeast(0f)) } },
                        label = { Text("Appears at (s)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = durTxt, onValueChange = { t -> durTxt = t.filter { it.isDigit() || it == '.' || it == ',' }.take(6); num(durTxt)?.let { p = p.copy(durSec = it.coerceAtLeast(0.3f)) } },
                        label = { Text("Stays for (s)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f)
                    )
                }

                Text("Position", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { p = p.copy(x = 0.5f, y = 0.14f) }) { Text("Top") }
                    TextButton(onClick = { p = p.copy(x = 0.5f, y = 0.5f) }) { Text("Centre") }
                    TextButton(onClick = { p = p.copy(x = 0.5f, y = 0.84f) }) { Text("Bottom") }
                }
                LabeledSlider("Left to right", "${(p.x * 100).roundToInt()}%", p.x, 0f..1f) { p = p.copy(x = (it * 100).roundToInt() / 100f) }
                LabeledSlider("Up to down", "${(p.y * 100).roundToInt()}%", p.y, 0f..1f) { p = p.copy(y = (it * 100).roundToInt() / 100f) }
                LabeledSlider("Tilt", "${p.rotation.roundToInt()}°", p.rotation, -45f..45f) { p = p.copy(rotation = it.roundToInt().toFloat()) }

                Text("Movement (keyframe style)", style = MaterialTheme.typography.titleSmall)
                Dropdown("Move", p.move.label, PopMove.entries, { it.label }) { p = p.copy(move = it) }
                if (p.move != PopMove.NONE) {
                    LabeledSlider("How far", "${(p.moveDist * 100).roundToInt()}%", p.moveDist, 0.05f..0.9f) { p = p.copy(moveDist = (it * 100).roundToInt() / 100f) }
                    LabeledSwitch("Smooth start and stop", p.moveSmooth) { p = p.copy(moveSmooth = it) }
                    LabeledSwitch("Go there and come back", p.moveBack) { p = p.copy(moveBack = it) }
                }
                LabeledSlider("Size change while moving", if (p.moveGrow == 1f) "none" else "${(p.moveGrow * 100).roundToInt()}% at the end", p.moveGrow, 0.4f..2f) {
                    p = p.copy(moveGrow = if (kotlin.math.abs(it - 1f) < 0.04f) 1f else (it * 20).roundToInt() / 20f)
                }
                Text("It starts at the position above and travels during the whole time it stays.", style = MaterialTheme.typography.bodySmall)

                Text("Sound when it pops", style = MaterialTheme.typography.titleSmall)
                Dropdown(
                    "Sound", p.sound.label,
                    SfxChoice.entries.filter { it != SfxChoice.AUTO && (it != SfxChoice.CUSTOM || p.soundUri != null) }, { it.label }
                ) { p = p.copy(sound = it) }
                OutlinedButton(onClick = { sndPicker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Choose my own sound file") }
                if (p.sound != SfxChoice.NONE) {
                    LabeledSlider("Sound volume", "${(p.soundVolume * 100).roundToInt()}%", p.soundVolume, 0f..1f) {
                        p = p.copy(soundVolume = (it * 100).roundToInt() / 100f)
                    }
                }
            }
        }
    )
}

@Composable
private fun ColourDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(color)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color(0x66888888), CircleShape)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun StoryTile(icon: String, title: String, value: String?, modifier: Modifier, onTap: () -> Unit, onClear: (() -> Unit)?) {
    val filled = value != null
    val line = if (filled) MaterialTheme.colorScheme.primary else Color(0x66888888)
    Box(
        modifier.height(104.dp).clip(RoundedCornerShape(16.dp))
            .border(if (filled) 2.dp else 1.dp, line, RoundedCornerShape(16.dp))
            .clickable(onClick = onTap).padding(10.dp)
    ) {
        Column(Modifier.align(Alignment.CenterStart), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (filled) "$icon  ✓" else "$icon  +", style = MaterialTheme.typography.titleMedium)
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(value ?: "tap to add", style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        if (onClear != null) {
            Text("✕", Modifier.align(Alignment.TopEnd).clickable(onClick = onClear).padding(4.dp), style = MaterialTheme.typography.titleMedium)
        }
    }
}
