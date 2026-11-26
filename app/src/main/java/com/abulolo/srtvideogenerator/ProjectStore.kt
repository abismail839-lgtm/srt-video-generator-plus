package com.abulolo.srtvideogenerator

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// ===== NEW CODE (update 4): auto-saves your whole project so nothing is ever lost =====

/** Everything you picked and every per-scene setting. Files themselves are not copied, only their addresses. */
data class ProjectData(
    val images: List<ImageItem> = emptyList(),
    val imageSource: String? = null,
    val srtUri: Uri? = null, val srtName: String? = null,
    val wordSrtUri: Uri? = null, val wordSrtName: String? = null,
    val voiceUri: Uri? = null, val voiceName: String? = null,
    val musicUri: Uri? = null, val musicName: String? = null,
    val sfxUri: Uri? = null, val sfxName: String? = null
)

class ProjectStore(context: Context) {
    private val file = File(context.filesDir, "project.json")
    private val backup = File(context.filesDir, "project.previous.json")

    @Synchronized
    fun save(p: ProjectData) {
        try {
            val tmp = File(file.parentFile, "project.tmp")
            tmp.writeText(toJson(p).toString())
            if (file.exists()) file.copyTo(backup, overwrite = true) // keep one older copy
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        } catch (_: Exception) {}
    }

    @Synchronized
    fun load(): ProjectData? {
        for (f in listOf(file, backup)) {
            try {
                if (f.exists()) return fromJson(JSONObject(f.readText()))
            } catch (_: Exception) {}
        }
        return null
    }

    /** Text form of a project, for the "save backup" button. */
    fun encode(p: ProjectData): String = toJson(p).toString()

    fun decode(text: String): ProjectData? = try { fromJson(JSONObject(text)) } catch (_: Exception) { null }

    // ---------- JSON ----------

    private fun JSONObject.putUri(key: String, u: Uri?) { if (u != null) put(key, u.toString()) }
    private fun JSONObject.getUri(key: String): Uri? = optString(key, "").takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
    private fun JSONObject.getStr(key: String): String? = optString(key, "").takeIf { it.isNotEmpty() }

    private inline fun <reified E : Enum<E>> JSONObject.getEnum(key: String, default: E): E =
        try { getStr(key)?.let { enumValueOf<E>(it) } ?: default } catch (_: Exception) { default }

    private fun toJson(p: ProjectData): JSONObject {
        val o = JSONObject()
        o.put("v", 1)
        p.imageSource?.let { o.put("source", it) }
        o.putUri("srt", p.srtUri); p.srtName?.let { o.put("srtName", it) }
        o.putUri("wordSrt", p.wordSrtUri); p.wordSrtName?.let { o.put("wordSrtName", it) }
        o.putUri("voice", p.voiceUri); p.voiceName?.let { o.put("voiceName", it) }
        o.putUri("music", p.musicUri); p.musicName?.let { o.put("musicName", it) }
        o.putUri("sfx", p.sfxUri); p.sfxName?.let { o.put("sfxName", it) }
        val arr = JSONArray()
        for (im in p.images) {
            val j = JSONObject()
            j.put("uri", im.uri.toString()); j.put("name", im.name); j.put("video", im.isVideo)
            im.transition?.let { j.put("tr", it.name) }
            im.sfx?.let { j.put("sfx", it.name) }
            val e = im.effect
            if (e.type != EffectType.NONE) {
                j.put("fx", JSONObject().put("t", e.type.name).put("i", e.intensity.toDouble())
                    .put("s", e.speed.toDouble()).put("rx", e.regionX.toDouble()).put("ry", e.regionY.toDouble())
                    .put("rw", e.regionW.toDouble()).put("rh", e.regionH.toDouble()).put("sc", e.scale.toDouble()).put("ct", e.count)
                    .apply { e.onlyFirstSec?.let { put("o", it.toDouble()) } })
            }
            val a = im.ambience
            if (a.type != AmbienceType.NONE) {
                j.put("amb", JSONObject().put("t", a.type.name).put("v", a.volume.toDouble()).apply {
                    a.onlyFirstSec?.let { put("o", it.toDouble()) }
                    a.customUri?.let { put("u", it.toString()) }
                    a.customName?.let { put("n", it) }
                })
            }
            if (im.pops.isNotEmpty()) {
                val pa = JSONArray()
                for (p in im.pops) {
                    pa.put(JSONObject().put("id", p.id).put("k", p.kind.name).put("text", p.text).put("st", p.style.name)
                        .put("an", p.anim.name).put("s", p.startSec.toDouble()).put("d", p.durSec.toDouble())
                        .put("x", p.x.toDouble()).put("y", p.y.toDouble()).put("z", p.size.toDouble())
                        .put("r", p.rotation.toDouble()).put("c", p.color).put("snd", p.sound.name)
                        .put("sv", p.soundVolume.toDouble())
                        .put("mv", p.move.name).put("md", p.moveDist.toDouble()).put("ms", p.moveSmooth)
                        .put("mb", p.moveBack).put("mg", p.moveGrow.toDouble()).apply {
                            p.imageUri?.let { put("iu", it.toString()) }
                            p.imageName?.let { put("in", it) }
                            p.soundUri?.let { put("su", it.toString()) }
                        })
                }
                j.put("pops", pa)
            }
            arr.put(j)
        }
        o.put("images", arr)
        return o
    }

    private fun fromJson(o: JSONObject): ProjectData {
        val list = ArrayList<ImageItem>()
        val arr = o.optJSONArray("images") ?: JSONArray()
        for (k in 0 until arr.length()) {
            val j = arr.getJSONObject(k)
            val fx = j.optJSONObject("fx")
            val amb = j.optJSONObject("amb")
            val popArr = j.optJSONArray("pops")
            val pops = ArrayList<PopItem>()
            if (popArr != null) for (q in 0 until popArr.length()) {
                val p = popArr.getJSONObject(q)
                pops += PopItem(
                    id = p.optLong("id", q.toLong()), kind = p.getEnum("k", PopKind.TEXT), text = p.optString("text", ""),
                    style = p.getEnum("st", PopStyle.PLAIN), imageUri = p.getUri("iu"), imageName = p.getStr("in"),
                    anim = p.getEnum("an", PopAnim.BOUNCE), startSec = p.optDouble("s", 0.5).toFloat(),
                    durSec = p.optDouble("d", 2.5).toFloat(), x = p.optDouble("x", 0.5).toFloat(),
                    y = p.optDouble("y", 0.5).toFloat(), size = p.optDouble("z", 1.0).toFloat(),
                    rotation = p.optDouble("r", 0.0).toFloat(), color = p.optInt("c", 0),
                    sound = p.getEnum("snd", SfxChoice.POP), soundVolume = p.optDouble("sv", 0.25).toFloat(),
                    soundUri = p.getUri("su"),
                    move = p.getEnum("mv", PopMove.NONE), moveDist = p.optDouble("md", 0.3).toFloat(),
                    moveSmooth = p.optBoolean("ms", true), moveBack = p.optBoolean("mb", false),
                    moveGrow = p.optDouble("mg", 1.0).toFloat()
                )
            }
            list += ImageItem(
                uri = Uri.parse(j.getString("uri")),
                name = j.optString("name", "image"),
                isVideo = j.optBoolean("video", false),
                pops = pops,
                transition = j.getStr("tr")?.let { try { enumValueOf<TransitionType>(it) } catch (_: Exception) { null } },
                sfx = j.getStr("sfx")?.let { try { enumValueOf<SfxChoice>(it) } catch (_: Exception) { null } },
                effect = if (fx == null) EffectSettings() else EffectSettings(
                    type = fx.getEnum("t", EffectType.NONE),
                    intensity = fx.optDouble("i", 0.6).toFloat(),
                    speed = fx.optDouble("s", 1.0).toFloat(),
                    onlyFirstSec = if (fx.has("o")) fx.optDouble("o").toFloat() else null,
                    regionX = fx.optDouble("rx", 0.5).toFloat(), regionY = fx.optDouble("ry", 0.5).toFloat(),
                    regionW = fx.optDouble("rw", 1.0).toFloat(), regionH = fx.optDouble("rh", 1.0).toFloat(), scale = fx.optDouble("sc", 1.0).toFloat(), count = fx.optInt("ct", 6)
                ),
                ambience = if (amb == null) AmbienceSettings() else AmbienceSettings(
                    type = amb.getEnum("t", AmbienceType.NONE),
                    volume = amb.optDouble("v", 0.25).toFloat(),
                    onlyFirstSec = if (amb.has("o")) amb.optDouble("o").toFloat() else null,
                    customUri = amb.getUri("u"),
                    customName = amb.getStr("n")
                )
            )
        }
        return ProjectData(
            images = list, imageSource = o.getStr("source"),
            srtUri = o.getUri("srt"), srtName = o.getStr("srtName"),
            wordSrtUri = o.getUri("wordSrt"), wordSrtName = o.getStr("wordSrtName"),
            voiceUri = o.getUri("voice"), voiceName = o.getStr("voiceName"),
            musicUri = o.getUri("music"), musicName = o.getStr("musicName"),
            sfxUri = o.getUri("sfx"), sfxName = o.getStr("sfxName")
        )
    }
}
