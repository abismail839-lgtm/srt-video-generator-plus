package com.bahaa.srtvideogenerator.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import com.bahaa.srtvideogenerator.AmbienceSettings
import com.bahaa.srtvideogenerator.AmbienceType
import com.bahaa.srtvideogenerator.AudioSettings
import com.bahaa.srtvideogenerator.displayName
import com.bahaa.srtvideogenerator.BitmapLoader
import com.bahaa.srtvideogenerator.CaptionPosition
import com.bahaa.srtvideogenerator.CaptionPreset
import com.bahaa.srtvideogenerator.EffectSettings
import com.bahaa.srtvideogenerator.EffectType
import com.bahaa.srtvideogenerator.EnhanceSettings
import com.bahaa.srtvideogenerator.MainViewModel
import com.bahaa.srtvideogenerator.UiState
import com.bahaa.srtvideogenerator.Resolution
import com.bahaa.srtvideogenerator.SfxChoice
import com.bahaa.srtvideogenerator.TransitionType
import com.bahaa.srtvideogenerator.ZoomMode
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
    // Android 13+: ask once for the notification that shows render progress, then start anyway.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.generateVideo() }
    val startRender = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else vm.generateVideo()
    }

    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var effectIndex by remember { mutableStateOf<Int?>(null) }
    var ambienceIndex by remember { mutableStateOf<Int?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var selected by rememberSaveable { mutableStateOf(0) }
    var applyAllEffect by remember { mutableStateOf<EffectSettings?>(null) }
    var applyAllAmbience by remember { mutableStateOf<AmbienceSettings?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("HU Video Maker", style = MaterialTheme.typography.headlineSmall) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { vm.undo() }, enabled = s.undoCount > 0) { Text("↩ Undo (${s.undoCount})") }
                    OutlinedButton(onClick = {
                        confirm = "Start a new project? Your pictures and per-scene settings will be cleared (you can still Undo)." to { vm.newProject() }
                    }, enabled = s.images.isNotEmpty()) { Text("New project") }
                }
                if (s.images.isNotEmpty()) {
                    val total = s.totalSecs.roundToInt()
                    Text(
                        "${s.images.size} scenes" + (if (s.sceneSecs.isNotEmpty()) " · total ${total / 60}:${"%02d".format(total % 60)}" else "") +
                            " · your work is saved automatically",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // ---------------- files ----------------
        item {
            Section("1. Files") {
                FileRow("Images and video clips", if (s.images.isEmpty()) null else (s.imageSource ?: "${s.images.size} images"),
                    { imagePicker.launch(arrayOf("image/*", "video/*")) }, null)
                OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose a whole folder (images and clips)")
                }
                FileRow("SRT subtitles", s.srtName, { srtPicker.launch(arrayOf("*/*")) }, if (s.srtUri != null) ({ vm.setSrt(null) }) else null)
                FileRow("Voice-over (optional)", s.voiceName, { audioPicker.launch(arrayOf("audio/*")) }, if (s.audio.voiceUri != null) ({ vm.setVoice(null) }) else null)
                FileRow("Word-by-word SRT (optional)", s.wordSrtName, { wordPicker.launch(arrayOf("*/*")) }, if (s.wordSrtUri != null) ({ vm.setWordSrt(null) }) else null)
                LabeledSwitch("Also show full subtitles", s.showSubtitles) { vm.setShowSubtitles(it) }
            }
        }

        // ---------------- video ----------------
        item {
            Section("2. Video") {
                Dropdown("Size", s.resolution.label, Resolution.entries, { it.label }) { vm.setResolution(it) }
                Dropdown("Frame rate (24 renders faster)", "${s.fps} fps", listOf(24, 30), { "$it fps" }) { vm.setFps(it) }
                Dropdown("Zoom effect", s.zoomMode.label, ZoomMode.entries, { it.label }) { vm.setZoom(it) }
            }
        }

        // ---------------- transitions ----------------
        item {
            Section("3. Transitions") {
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

        // ---------------- scene editor ----------------
        if (s.images.isNotEmpty()) {
            item {
                SceneStrip(
                    s = s, selected = selected.coerceIn(0, s.images.lastIndex),
                    onSelect = { selected = it }
                )
            }
            item {
                SceneEditor(
                    vm = vm, s = s, index = selected.coerceIn(0, s.images.lastIndex),
                    onSelect = { selected = it },
                    onEffect = { effectIndex = it },
                    onAmbience = { ambienceIndex = it },
                    onPreview = { previewIndex = it },
                    onConfirm = { msg, action -> confirm = msg to action }
                )
            }
        }

        // ---------------- music ----------------
        item {
            Section("4. Voice and background music") {
                LabeledSlider("Voice volume", "${(s.audio.voiceVolume * 100).roundToInt()}%", s.audio.voiceVolume, 0f..3f) {
                    vm.setAudio(s.audio.copy(voiceVolume = it))
                }
                LabeledSwitch("Auto-boost a quiet voice-over", s.audio.voiceBoost) {
                    vm.setAudio(s.audio.copy(voiceBoost = it))
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
            Section("5. Transition sounds") {
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

        // ---------------- subtitle style ----------------
        item {
            Section("6. Subtitle style") {
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
            Section("7. Sharpen and colour") {
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

    previewIndex?.let { i ->
        PreviewDialog(vm, i) { previewIndex = null }
    }
    effectIndex?.let { i ->
        EffectDialog(vm, i, onAll = { applyAllEffect = it }) { effectIndex = null }
    }
    ambienceIndex?.let { i ->
        AmbienceDialog(vm, i, onAll = { applyAllAmbience = it }) { ambienceIndex = null }
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
        val filled = s.images.count { it.effect.type != EffectType.NONE }
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
                Dropdown("Sound", a.type.label,
                    AmbienceType.entries.filter { it != AmbienceType.CUSTOM || a.customUri != null }, { it.label }) { a = a.copy(type = it) }
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
private fun EffectDialog(vm: MainViewModel, index: Int, onAll: (EffectSettings) -> Unit, onClose: () -> Unit) {
    val s by vm.state.collectAsState()
    val ctx = LocalContext.current
    if (index !in s.images.indices) { onClose(); return }
    var e by remember { mutableStateOf(s.images[index].effect) }
    val params = remember { EffectPreviewParams() }
    params.settings = e
    val bmp by produceState<Bitmap?>(null, s.images[index].uri) {
        value = withContext(Dispatchers.IO) { try { BitmapLoader.decode(ctx, s.images[index].uri, 640) } catch (_: Exception) { null } }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Effect for image ${index + 1}") },
        confirmButton = {
            TextButton(onClick = { vm.setImageEffect(index, e); onClose() }) { Text("Apply") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onAll(e); onClose() }) { Text("Apply to all") }
                TextButton(onClick = onClose) { Text("Cancel") }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val b = bmp
                if (b == null) Text("Loading…")
                else AndroidView(
                    factory = { EffectPreviewView(it, b, params) },
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                )
                Dropdown("Effect", e.type.label, EffectType.entries, { it.label }) { e = e.copy(type = it) }
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
                }
            }
        }
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
private fun SceneStrip(s: UiState, selected: Int, onSelect: (Int) -> Unit) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var jump by remember { mutableStateOf("") }
    Section("Scenes (scroll sideways, tap one to edit it)") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = jump, onValueChange = { jump = it.filter(Char::isDigit).take(5) },
                label = { Text("Go to scene") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                val n = (jump.toIntOrNull() ?: return@Button).coerceIn(1, s.images.size) - 1
                onSelect(n)
                scope.launch { state.animateScrollToItem(n) }
            }) { Text("Go") }
        }
        LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(s.images) { i, img ->
                val sel = i == selected
                Column(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else Color(0x55888888), RoundedCornerShape(8.dp))
                        .clickable { onSelect(i) }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Thumb(img.uri, 92.dp)
                    val marks = (if (img.effect.type != EffectType.NONE) "✨" else "") +
                        (if (img.ambience.type != AmbienceType.NONE) "🎧" else "") +
                        (if (img.transition != null) "↔" else "") + (if (img.sfx != null) "♪" else "")
                    Text("${i + 1}${if (img.isVideo) " 🎬" else ""}", style = MaterialTheme.typography.labelMedium)
                    Text(
                        (s.sceneSecs.getOrNull(i)?.let { "%.1fs".format(it) } ?: "") + " " + marks,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1
                    )
                }
            }
        }
        Text("✨ effect   🎧 scene sound   ↔ own transition   ♪ own sound", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SceneEditor(
    vm: MainViewModel, s: UiState, index: Int,
    onSelect: (Int) -> Unit, onEffect: (Int) -> Unit, onAmbience: (Int) -> Unit, onPreview: (Int) -> Unit,
    onConfirm: (String, () -> Unit) -> Unit
) {
    val img = s.images[index]
    val ctx = LocalContext.current
    val params = remember { EffectPreviewParams() }
    params.settings = img.effect
    val bmp by produceState<Bitmap?>(null, img.uri) {
        value = withContext(Dispatchers.IO) { try { BitmapLoader.decode(ctx, img.uri, 640) } catch (_: Exception) { null } }
    }
    Section("Edit scene ${index + 1} of ${s.images.size}") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onSelect((index - 1).coerceAtLeast(0)) }, enabled = index > 0) { Text("◀ Back") }
            OutlinedButton(onClick = { onSelect((index + 1).coerceAtMost(s.images.lastIndex)) }, enabled = index < s.images.lastIndex) { Text("Next ▶") }
            TextButton(onClick = { vm.playScene(index) }) { Text(if (s.playingScene == index) "■ Stop" else "▶ Voice") }
        }
        Text(
            "${if (img.isVideo) "🎬 " else ""}${img.name}" + (s.sceneSecs.getOrNull(index)?.let { "  ·  %.1f s".format(it) } ?: ""),
            style = MaterialTheme.typography.bodyMedium, maxLines = 1
        )
        val b = bmp
        if (b != null) {
            key(index, b) {
                AndroidView(
                    factory = { EffectPreviewView(it, b, params) },
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                )
            }
        } else Text("Loading preview…")
        Text("The preview shows this scene's picture with its effect.", style = MaterialTheme.typography.bodySmall)

        if (index == 0) {
            Text("Opening scene (no transition or sound before it).", style = MaterialTheme.typography.bodySmall)
        } else {
            if (s.transitions.enabled) {
                val label = img.transition?.label ?: "Default (${s.transitions.default.label})"
                Dropdown("Transition in", label, listOf<TransitionType?>(null) + TransitionType.entries, { it?.label ?: "Default" }) {
                    vm.setImageTransition(index, it)
                }
                TextButton(onClick = { onPreview(index) }) { Text("Preview this transition") }
            }
            if (s.audio.sfxEnabled) {
                val opts = listOf<SfxChoice?>(null) + SfxChoice.entries.filter { it != SfxChoice.CUSTOM || s.audio.sfxCustomUri != null }
                Dropdown("Transition sound", img.sfx?.label ?: "Default (${s.audio.sfxChoice.label})", opts, { it?.label ?: "Default" }) {
                    vm.setImageSfx(index, it)
                }
            }
        }
        OutlinedButton(onClick = { onEffect(index) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (img.effect.type == EffectType.NONE) "Add effect" else "Effect: ${img.effect.type.label}", maxLines = 1)
        }
        OutlinedButton(onClick = { onAmbience(index) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (img.ambience.type == AmbienceType.NONE) "Add scene sound" else "Scene sound: ${img.ambience.type.label}", maxLines = 1)
        }

        // ---- copy this scene's settings to other scenes ----
        var from by remember(index) { mutableStateOf((index + 2).coerceAtMost(s.images.size).toString()) }
        var to by remember(index) { mutableStateOf((index + 2).coerceAtMost(s.images.size).toString()) }
        var cT by remember { mutableStateOf(true) }
        var cS by remember { mutableStateOf(true) }
        var cE by remember { mutableStateOf(true) }
        var cA by remember { mutableStateOf(true) }
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
            }
            if (parts.isNotEmpty()) {
                onConfirm("Copy the chosen settings of scene ${index + 1} onto scenes ${first + 1} to ${last + 1}? This replaces what those scenes have now (you can Undo).") {
                    vm.copySceneSettings(index, first, last, parts)
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Copy to these scenes") }
    }
}
