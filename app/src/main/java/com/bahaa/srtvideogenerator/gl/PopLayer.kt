package com.bahaa.srtvideogenerator.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.bahaa.srtvideogenerator.BitmapLoader
import com.bahaa.srtvideogenerator.PopAnim
import com.bahaa.srtvideogenerator.PopItem
import com.bahaa.srtvideogenerator.PopKind
import com.bahaa.srtvideogenerator.PopStyle
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * ===== NEW CODE (update 5) =====
 * Draws pop-in text and pictures on top of a scene. Used by the video exporter and by the live preview,
 * so what you see while editing is what you get in the video. Must be used on the GL thread.
 */
class PopLayer(private val context: Context, private val r: SurfaceRenderer, private val w: Int, private val h: Int) {

    private val cache = LinkedHashMap<String, GlTexture?>()

    /** [sceneLocalSec] = seconds since this scene started; [mix] = fade of the whole scene (during transitions). */
    fun drawScene(items: List<PopItem>, sceneLocalSec: Float, mix: Float) {
        for (item in items) draw(item, sceneLocalSec, mix)
    }

    fun draw(item: PopItem, sceneLocalSec: Float, mix: Float) {
        val local = sceneLocalSec - item.startSec
        if (local < 0f || local > item.durSec || mix <= 0.001f) return

        val inDur = 0.35f
        val outDur = 0.25f
        val a = min(1f, local / inDur)
        val out = if (local > item.durSec - outDur) ((item.durSec - local) / outDur).coerceIn(0f, 1f) else 1f

        var scale = 1f
        var alpha = 1f
        var dx = 0f
        var dy = 0f
        var rot = item.rotation * (PI.toFloat() / 180f)
        when (item.anim) {
            PopAnim.BOUNCE -> { scale = easeOutBack(a); alpha = min(1f, local / 0.12f) }
            PopAnim.ZOOM -> { scale = 0.2f + 0.8f * easeOutCubic(a); alpha = a }
            PopAnim.SLIDE_UP -> { dy = 0.12f * (1f - easeOutCubic(a)); alpha = a }
            PopAnim.SLIDE_SIDE -> { dx = 0.25f * (1f - easeOutCubic(a)); alpha = a }
            PopAnim.WOBBLE -> {
                scale = easeOutBack(a)
                rot += sin(local * 16f) * exp(-local * 3.5f) * 0.18f
                alpha = min(1f, local / 0.12f)
            }
            PopAnim.FADE -> alpha = a
            PopAnim.TYPEWRITER -> alpha = min(1f, local / 0.1f)
        }
        alpha *= out * mix
        scale *= 0.9f + 0.1f * out
        if (scale <= 0.01f || alpha <= 0.01f) return

        val tex: GlTexture?
        val wPx: Float
        val hPx: Float
        if (item.kind == PopKind.TEXT) {
            val shown = if (item.anim == PopAnim.TYPEWRITER)
                item.text.take((local / 0.06f).toInt().coerceIn(0, item.text.length)) else item.text
            if (shown.isEmpty()) return
            val key = "T${item.id}|$shown|${item.style}|${item.color}|${item.size}|${item.anim == PopAnim.TYPEWRITER}"
            tex = texture(key) { textBitmap(item, shown) }
            wPx = (tex?.width ?: 0) * scale
            hPx = (tex?.height ?: 0) * scale
        } else {
            val uri = item.imageUri ?: return
            tex = texture("I$uri") {
                try { BitmapLoader.decode(context, uri, 900) } catch (_: Exception) { null }
            }
            if (tex == null) return
            wPx = item.size * w * scale
            hPx = wPx * tex.height / tex.width
        }
        if (tex == null) return
        r.drawPop(tex, (item.x + dx) * 2f - 1f, 1f - (item.y + dy) * 2f, wPx, hPx, rot, alpha)
    }

    fun release() {
        for (t in cache.values) if (t != null) r.deleteTexture(t)
        cache.clear()
    }

    private fun texture(key: String, build: () -> Bitmap?): GlTexture? {
        if (cache.containsKey(key)) return cache[key]
        val bmp = build()
        val tex = if (bmp != null) { try { r.createTexture(bmp) } finally { bmp.recycle() } } else null
        cache[key] = tex
        while (cache.size > 40) {
            val eldest = cache.entries.first()
            eldest.value?.let { r.deleteTexture(it) }
            cache.remove(eldest.key)
        }
        return tex
    }

    private fun easeOutCubic(t: Float) = 1f - (1f - t).pow(3)
    private fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        return 1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
    }

    // ---------------------------------------------------------------- text pictures

    private fun textBitmap(item: PopItem, shown: String): Bitmap {
        val style = item.style
        val size = min(w, h) * 0.075f * item.size
        val maxW = (w * 0.9f).toInt()
        val fillColor = if (item.color != 0) item.color else when (style) {
            PopStyle.PLAIN, PopStyle.SCRIPT, PopStyle.NAME_TAG -> Color.WHITE
            PopStyle.BOLD_YELLOW -> Color.rgb(255, 212, 0)
            PopStyle.DATE_LABEL -> Color.rgb(59, 36, 18)
            PopStyle.STAMP_RED -> Color.rgb(200, 25, 25)
            PopStyle.HIGHLIGHTER -> Color.BLACK
        }
        val face = when (style) {
            PopStyle.SCRIPT -> Typeface.create("cursive", Typeface.BOLD_ITALIC)
            PopStyle.DATE_LABEL -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
            else -> Typeface.DEFAULT_BOLD
        }
        val strokeFactor = when (style) {
            PopStyle.PLAIN -> 0.14f
            PopStyle.BOLD_YELLOW -> 0.22f
            PopStyle.SCRIPT -> 0.12f
            else -> 0f
        }
        val fill = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = size; color = fillColor }
        val stroke = TextPaint(fill).apply {
            this.style = Paint.Style.STROKE; strokeWidth = size * strokeFactor; color = Color.BLACK
            strokeJoin = Paint.Join.ROUND
        }
        val typing = item.anim == PopAnim.TYPEWRITER
        val align = if (typing) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_CENTER

        fun layout(t: String, p: TextPaint, width: Int, al: Layout.Alignment) =
            StaticLayout.Builder.obtain(t, 0, t.length, p, width).setAlignment(al).setLineSpacing(0f, 1.05f).build()

        val full = layout(item.text, fill, maxW, Layout.Alignment.ALIGN_CENTER)
        var contentW = 0f
        for (i in 0 until full.lineCount) contentW = max(contentW, full.getLineWidth(i))
        val cw = ceil(contentW).toInt().coerceAtLeast(1) + 2
        val shownFill = layout(shown, fill, cw, align)
        val padX = (size * 0.45f).toInt()
        val padY = (size * 0.30f).toInt()
        val accent = if (style == PopStyle.NAME_TAG) (size * 0.25f).toInt() else 0
        val bw = cw + padX * 2 + accent
        val bh = full.height + padY * 2

        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rect = RectF(0f, 0f, bw.toFloat(), bh.toFloat())
        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        when (style) {
            PopStyle.DATE_LABEL -> {
                bg.color = Color.argb(240, 235, 217, 168)
                c.drawRoundRect(rect, size * 0.25f, size * 0.25f, bg)
                val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(120, 82, 40); this.style = Paint.Style.STROKE; strokeWidth = size * 0.06f
                }
                c.drawRoundRect(RectF(2f, 2f, bw - 2f, bh - 2f), size * 0.25f, size * 0.25f, edge)
            }
            PopStyle.NAME_TAG -> {
                bg.color = Color.argb(225, 20, 20, 20)
                c.drawRoundRect(rect, size * 0.15f, size * 0.15f, bg)
                bg.color = Color.rgb(255, 170, 0)
                c.drawRect(0f, 0f, accent.toFloat(), bh.toFloat(), bg)
            }
            PopStyle.HIGHLIGHTER -> {
                bg.color = Color.argb(235, 255, 230, 0)
                c.drawRoundRect(rect, size * 0.08f, size * 0.08f, bg)
            }
            PopStyle.STAMP_RED -> {
                val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = fillColor; this.style = Paint.Style.STROKE; strokeWidth = size * 0.09f
                }
                val inset = size * 0.05f
                c.drawRoundRect(RectF(inset, inset, bw - inset, bh - inset), size * 0.12f, size * 0.12f, border)
            }
            else -> {}
        }
        c.translate((padX + accent).toFloat(), padY.toFloat())
        if (strokeFactor > 0f) layout(shown, stroke, cw, align).draw(c)
        shownFill.draw(c)
        if (style == PopStyle.STAMP_RED) bmp.setHasAlpha(true)
        return bmp
    }
}
