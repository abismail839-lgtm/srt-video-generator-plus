package com.abulolo.srtvideogenerator.gl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.abulolo.srtvideogenerator.CaptionPosition
import com.abulolo.srtvideogenerator.CaptionPreset
import com.abulolo.srtvideogenerator.CaptionSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.min

// ===== RECONSTRUCTED CLASS NAME (original: NativeMediaGenerator$SurfaceRenderer).
// Method names draw/drawCaption/compileShader/createTexture/release mirror the original;
// all bodies are NEW. =====

class GlTexture(val id: Int, val width: Int, val height: Int)

class SurfaceRenderer(private val width: Int, private val height: Int) {

    private var transProgram = 0
    private var capProgram = 0
    private var ovProgram = 0
    private var popProgram = 0
    private val popCache = HashMap<String, Int>()
    private val ovCache = HashMap<String, Int>()

    private var sharp = 0f
    private var contrast = 1f
    private var colour = 1f

    private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply { put(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)); position(0) }

    private val uniformCache = HashMap<String, Int>()

    private var captionKey: String? = null
    private var captionTex = 0
    private var captionW = 0
    private var captionH = 0

    fun init() {
        transProgram = link(VERTEX, TRANSITION_FRAGMENT)
        capProgram = link(VERTEX, CAPTION_FRAGMENT)
        ovProgram = link(VERTEX, OVERLAY_FRAGMENT)
        popProgram = link(POP_VERTEX, POP_FRAGMENT)
    }

    /**
     * Draws a pop picture: [cx], [cy] = centre in NDC, [wPx] x [hPx] = size in output pixels,
     * [rot] = radians counter-clockwise, premultiplied-alpha texture.
     */
    fun drawPop(tex: GlTexture, cx: Float, cy: Float, wPx: Float, hPx: Float, rot: Float, alpha: Float) {
        GLES20.glViewport(0, 0, width, height)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(popProgram)
        fun u(name: String) = popCache.getOrPut(name) { GLES20.glGetUniformLocation(popProgram, name) }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex.id)
        GLES20.glUniform1i(u("uTex"), 0)
        GLES20.glUniform2f(u("uCenter"), cx, cy)
        GLES20.glUniform2f(u("uSizePx"), wPx, hPx)
        GLES20.glUniform2f(u("uView"), width.toFloat(), height.toFloat())
        GLES20.glUniform1f(u("uRot"), rot)
        GLES20.glUniform1f(u("uAlpha"), alpha.coerceIn(0f, 1f))
        drawQuad(popProgram)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    /** Draws a logo in a corner: [corner] 0 TL, 1 TR, 2 BL, 3 BR; [sizeFrac] = width / frame width. */
    fun drawLogo(tex: GlTexture, corner: Int, sizeFrac: Float, opacity: Float) {
        val wPx = sizeFrac * width
        val hPx = wPx * tex.height / tex.width
        val m = 0.03f * min(width, height)
        val cx = if (corner == 0 || corner == 2) -1f + (m + wPx / 2f) / (width / 2f) else 1f - (m + wPx / 2f) / (width / 2f)
        val cy = if (corner < 2) 1f - (m + hPx / 2f) / (height / 2f) else -1f + (m + hPx / 2f) / (height / 2f)
        drawPop(tex, cx, cy, wPx, hPx, 0f, opacity)
    }

    /** Sharpen / contrast / colour applied to every picture. 0 / 1 / 1 = off. */
    fun setEnhance(sharpness: Float, contrastValue: Float, colourValue: Float) {
        sharp = sharpness; contrast = contrastValue; colour = colourValue
    }

    // ---------- textures ----------

    fun createTexture(bitmap: Bitmap): GlTexture {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return GlTexture(ids[0], bitmap.width, bitmap.height)
    }

    /** Puts a new picture into an existing texture (used for the frames of animated clips). */
    fun replaceTexture(old: GlTexture, bitmap: Bitmap): GlTexture {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, old.id)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return GlTexture(old.id, bitmap.width, bitmap.height)
    }

    fun deleteTexture(t: GlTexture) {
        GLES20.glDeleteTextures(1, intArrayOf(t.id), 0)
    }

    /** "Cover" crop so the picture fills the frame without distortion. */
    private fun crop(t: GlTexture): FloatArray {
        val screen = width.toFloat() / height
        val img = t.width.toFloat() / t.height
        return if (img > screen) floatArrayOf(screen / img, 1f) else floatArrayOf(1f, img / screen)
    }

    // ---------- frame drawing ----------

    /**
     * Draws one frame. [from] == null means "just show [to]".
     * [type] is TransitionType.shaderId, [progress] 0..1, zoom values are scale factors (1 = none).
     */
    fun draw(
        from: GlTexture?, to: GlTexture, progress: Float, type: Int,
        zoomFrom: Float, zoomTo: Float, timeSec: Float,
        offAx: Float = 0f, offAy: Float = 0f, offBx: Float = 0f, offBy: Float = 0f
    ) {
        val a = from ?: to
        val za = if (from == null) zoomTo else zoomFrom
        val t = if (from == null) 0 else type
        val p = if (from == null) 1f else progress

        GLES20.glViewport(0, 0, width, height)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(transProgram)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, a.id)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, to.id)

        fun u(name: String) = uniformCache.getOrPut(name) { GLES20.glGetUniformLocation(transProgram, name) }
        GLES20.glUniform1i(u("uTexA"), 0)
        GLES20.glUniform1i(u("uTexB"), 1)
        val ca = crop(a)
        val cb = crop(to)
        GLES20.glUniform2f(u("uCropA"), ca[0], ca[1])
        GLES20.glUniform2f(u("uCropB"), cb[0], cb[1])
        GLES20.glUniform1f(u("uZoomA"), za)
        GLES20.glUniform1f(u("uZoomB"), zoomTo)
        GLES20.glUniform1f(u("uP"), p)
        GLES20.glUniform1f(u("uTime"), timeSec)
        GLES20.glUniform1f(u("uAspect"), width.toFloat() / height)
        GLES20.glUniform1i(u("uType"), t)
        GLES20.glUniform2f(u("uOffA"), offAx, offAy)
        GLES20.glUniform2f(u("uOffB"), offBx, offBy)
        GLES20.glUniform2f(u("uTexelA"), 1f / a.width, 1f / a.height)
        GLES20.glUniform2f(u("uTexelB"), 1f / to.width, 1f / to.height)
        GLES20.glUniform1f(u("uSharp"), sharp)
        GLES20.glUniform1f(u("uContrast"), contrast)
        GLES20.glUniform1f(u("uSat"), colour)
        GLES20.glUniform4f(u("uRect"), -1f, -1f, 1f, 1f)
        drawQuad(transProgram)
    }

    /** Draws an overlay effect (EffectType ordinal 1..9) over the current frame. */
    fun drawOverlay(
        effect: Int, intensity: Float, timeSec: Float, alpha: Float,
        rx: Float = 0.5f, ry: Float = 0.5f, rw: Float = 1f, rh: Float = 1f
    ) {
        // 10 = screen shake and 11 = zoom pulse are done by moving the picture, not by this shader
        if (effect <= 0 || effect == 10 || effect == 11 || effect > 27 || alpha <= 0.001f) return
        GLES20.glViewport(0, 0, width, height)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(ovProgram)
        fun u(name: String) = ovCache.getOrPut(name) { GLES20.glGetUniformLocation(ovProgram, name) }
        val w0 = rw.coerceIn(0.05f, 1f)
        val h0 = rh.coerceIn(0.05f, 1f)
        val left = 2f * (rx - w0 / 2f) - 1f
        val right = 2f * (rx + w0 / 2f) - 1f
        val top = 1f - 2f * (ry - h0 / 2f)
        val bottom = 1f - 2f * (ry + h0 / 2f)
        GLES20.glUniform4f(u("uRect"), left, bottom, right, top)
        GLES20.glUniform1i(u("uEffect"), effect)
        GLES20.glUniform1f(u("uIntensity"), intensity.coerceIn(0f, 1f))
        GLES20.glUniform1f(u("uTime"), timeSec)
        GLES20.glUniform1f(u("uAlpha"), alpha.coerceIn(0f, 1f))
        GLES20.glUniform1f(u("uAspect"), (width * w0) / (height * h0))
        drawQuad(ovProgram)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    /** word == true: big centred word-by-word style; false: normal subtitle. */
    fun drawCaption(text: String, word: Boolean, style: CaptionSettings = CaptionSettings()) {
        if (text.isBlank()) return
        val key = "$word|${style.preset}|${style.sizeScale}|$text"
        if (key != captionKey) {
            if (captionTex != 0) GLES20.glDeleteTextures(1, intArrayOf(captionTex), 0)
            buildCaption(text, word, style)
            captionKey = key
        }
        val wNdc = 2f * captionW / width
        val hNdc = 2f * captionH / height
        val bottom = when (style.position) {
            CaptionPosition.TOP -> 1f - 2f * 0.06f - hNdc
            CaptionPosition.MIDDLE -> -hNdc / 2f
            CaptionPosition.BOTTOM ->
                if (word) (1f - 2f * 0.78f) - hNdc / 2f else (1f - 2f * 0.93f)
        }
        GLES20.glViewport(0, 0, width, height)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA) // premultiplied bitmap
        GLES20.glUseProgram(capProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, captionTex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(capProgram, "uTex"), 0)
        GLES20.glUniform4f(
            GLES20.glGetUniformLocation(capProgram, "uRect"),
            -wNdc / 2f, bottom, wNdc / 2f, bottom + hNdc
        )
        drawQuad(capProgram)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun buildCaption(text: String, word: Boolean, style: CaptionSettings) {
        val minSide = min(width, height).toFloat()
        val preset = style.preset
        val baseSize = if (word) minSide * 0.13f else minSide * 0.055f
        val size = baseSize * style.sizeScale * (if (preset == CaptionPreset.BOLD_YELLOW) 1.1f else 1f)
        val pad = (size * 0.45f).toInt()
        val maxW = (width * 0.88f).toInt()

        val fillColor = when (preset) {
            CaptionPreset.BOLD_YELLOW -> Color.rgb(255, 212, 0)
            CaptionPreset.PARCHMENT -> Color.rgb(59, 36, 18)
            else -> Color.WHITE
        }
        val strokeFactor = when (preset) {
            CaptionPreset.CLASSIC -> 0.16f
            CaptionPreset.BOLD_YELLOW -> 0.24f
            CaptionPreset.CLEAN -> 0.10f
            else -> 0f
        }
        val boxColor = when (preset) {
            CaptionPreset.CLASSIC -> if (word) 0 else Color.argb(140, 0, 0, 0)
            CaptionPreset.BLACK_BOX -> Color.argb(225, 0, 0, 0)
            CaptionPreset.PARCHMENT -> Color.argb(235, 235, 217, 168)
            else -> 0
        }
        val face = if (preset == CaptionPreset.PARCHMENT)
            Typeface.create(Typeface.SERIF, Typeface.BOLD) else Typeface.DEFAULT_BOLD

        val fillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face; textSize = size; color = fillColor
        }
        val strokePaint = TextPaint(fillPaint).apply {
            this.style = Paint.Style.STROKE; strokeWidth = size * strokeFactor; color = Color.BLACK
            strokeJoin = Paint.Join.ROUND
        }
        fun layout(p: TextPaint) = StaticLayout.Builder.obtain(text, 0, text.length, p, maxW)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 1.05f).build()

        val fill = layout(fillPaint)
        val bw = maxW + pad * 2
        val bh = fill.height + pad * 2
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (boxColor != 0) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = boxColor }
            val r = if (preset == CaptionPreset.BLACK_BOX) size * 0.15f else size * 0.4f
            c.drawRoundRect(RectF(0f, 0f, bw.toFloat(), bh.toFloat()), r, r, bg)
            if (preset == CaptionPreset.PARCHMENT) {
                val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(120, 82, 40); this.style = Paint.Style.STROKE; strokeWidth = size * 0.06f
                }
                c.drawRoundRect(RectF(2f, 2f, bw - 2f, bh - 2f), r, r, edge)
            }
        }
        c.translate(pad.toFloat(), pad.toFloat())
        if (strokeFactor > 0f) layout(strokePaint).draw(c)
        fill.draw(c)

        val tex = createTexture(bmp)
        captionTex = tex.id
        captionW = bw
        captionH = bh
        bmp.recycle()
    }

    private fun drawQuad(program: Int) {
        val loc = GLES20.glGetAttribLocation(program, "aPos")
        GLES20.glEnableVertexAttribArray(loc)
        quad.position(0)
        GLES20.glVertexAttribPointer(loc, 2, GLES20.GL_FLOAT, false, 8, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(loc)
    }

    // ---------- shaders ----------

    private fun link(vs: String, fs: String): Int {
        val v = compileShader(GLES20.GL_VERTEX_SHADER, vs)
        val f = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, v)
        GLES20.glAttachShader(prog, f)
        GLES20.glLinkProgram(prog)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, ok, 0)
        check(ok[0] == GLES20.GL_TRUE) { "Link failed: " + GLES20.glGetProgramInfoLog(prog) }
        GLES20.glDeleteShader(v)
        GLES20.glDeleteShader(f)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        check(ok[0] == GLES20.GL_TRUE) { "Shader compile failed: " + GLES20.glGetShaderInfoLog(s) }
        return s
    }

    fun release() {
        if (captionTex != 0) GLES20.glDeleteTextures(1, intArrayOf(captionTex), 0)
        captionTex = 0
        captionKey = null
        if (transProgram != 0) GLES20.glDeleteProgram(transProgram)
        if (capProgram != 0) GLES20.glDeleteProgram(capProgram)
        if (ovProgram != 0) GLES20.glDeleteProgram(ovProgram)
        if (popProgram != 0) GLES20.glDeleteProgram(popProgram)
        transProgram = 0
        capProgram = 0
        ovProgram = 0
        popProgram = 0
    }

    private companion object {
        // aPos is 0..1 with (0,0) = TOP-left, so bitmap row 0 maps to the top of the frame.
        // uRect = (left, bottom, right, top) in NDC.
        const val VERTEX = """
            attribute vec2 aPos;
            uniform vec4 uRect;
            varying vec2 vUv;
            void main() {
                vUv = aPos;
                float x = mix(uRect.x, uRect.z, aPos.x);
                float y = mix(uRect.w, uRect.y, aPos.y);
                gl_Position = vec4(x, y, 0.0, 1.0);
            }
        """

        const val CAPTION_FRAGMENT = """
            precision mediump float;
            varying vec2 vUv;
            uniform sampler2D uTex;
            void main() { gl_FragColor = texture2D(uTex, vUv); }
        """

        // Type ids == TransitionType.ordinal:
        // 0 cut, 1 fade, 2 dissolve, 3 slide L, 4 slide R, 5 slide U, 6 slide D,
        // 7 spin, 8 blur, 9 wipe, 10 glitch, 11 zoom, 12 dip white, 13 flash, 14 circle,
        // 15 pixelate, 16 shake cut

        // Effect ids == EffectType.ordinal: 1 white edge, 2 snow, 3 rain, 4 dust, 5 embers,
        // 6 old film, 7 vignette, 8 glow edge, 9 flash pulses. Output is premultiplied alpha.
        const val POP_VERTEX = """
            attribute vec2 aPos;
            uniform vec2 uCenter;
            uniform vec2 uSizePx;
            uniform vec2 uView;
            uniform float uRot;
            varying vec2 vUv;
            void main() {
                vUv = aPos;
                vec2 p = (aPos - 0.5) * uSizePx;
                p.y = -p.y;
                float c = cos(uRot);
                float s = sin(uRot);
                p = vec2(c * p.x - s * p.y, s * p.x + c * p.y);
                gl_Position = vec4(uCenter + p / (uView * 0.5), 0.0, 1.0);
            }
        """

        const val POP_FRAGMENT = """
            precision mediump float;
            varying vec2 vUv;
            uniform sampler2D uTex;
            uniform float uAlpha;
            void main() {
                gl_FragColor = texture2D(uTex, vUv) * uAlpha;
            }
        """

        const val OVERLAY_FRAGMENT = """
            precision highp float;
            varying vec2 vUv;
            uniform int uEffect;
            uniform float uIntensity;
            uniform float uTime;
            uniform float uAlpha;
            uniform float uAspect;

            float hash(vec2 p) {
                return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
            }
            float vnoise(vec2 p) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
                           mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
            }
            float fbm(vec2 p) {
                float v = 0.0;
                float a = 0.5;
                for (int i = 0; i < 4; i++) {
                    v += a * vnoise(p);
                    p = p * 2.0 + vec2(1.7, 9.2);
                    a *= 0.5;
                }
                return v;
            }
            void over(inout vec4 acc, vec3 c, float a) {
                acc.rgb = acc.rgb * (1.0 - a) + c * a;
                acc.a = acc.a + a * (1.0 - acc.a);
            }

            // dots on a grid, falling (dir = -1) or rising (dir = +1)
            float particles(vec2 uv, float scale, float speed, float dir, float size, float density, float sway) {
                vec2 p = uv * vec2(uAspect, 1.0) * scale;
                p.y += dir * uTime * speed * scale * 0.12;
                vec2 id0 = floor(p);
                p.x += sin(uTime * 0.7 + id0.y * 1.7) * sway;
                vec2 id = floor(p);
                vec2 f = fract(p);
                float h = hash(id);
                vec2 c = vec2(0.25 + 0.5 * hash(id + 3.1), 0.25 + 0.5 * hash(id + 7.7));
                float d = length(f - c);
                float present = step(1.0 - density, h);
                float sz = size * (0.5 + 0.5 * hash(id + 1.3));
                return present * (1.0 - smoothstep(sz * 0.4, sz, d));
            }

            void main() {
                vec2 uv = vUv;
                float I = uIntensity;
                float t = uTime;
                vec4 acc = vec4(0.0);

                if (uEffect == 1) {                       // shaky white hand-drawn edge
                    float dxs = min(uv.x, 1.0 - uv.x) * uAspect;
                    float dys = min(uv.y, 1.0 - uv.y);
                    float depth; float along; float cornerD; float side;
                    if (dxs < dys) {
                        depth = dxs; along = uv.y; cornerD = min(uv.y, 1.0 - uv.y);
                        side = uv.x < 0.5 ? 0.0 : 1.0;
                    } else {
                        depth = dys; along = uv.x * uAspect; cornerD = min(uv.x, 1.0 - uv.x) * uAspect;
                        side = uv.y < 0.5 ? 2.0 : 3.0;
                    }
                    float corner = 1.0 - smoothstep(0.0, 0.40, cornerD);
                    float dens = 70.0;
                    float q = along * dens + depth * dens * 0.35 * (side < 1.5 ? 1.0 : -1.0);
                    float id = floor(q);
                    float f = fract(q);
                    float tick = floor(t * 9.0);
                    float h = hash(vec2(id, side * 17.0));
                    float hl = hash(vec2(id + tick * 0.37, side * 5.0 + tick));
                    float wob = sin(t * 7.0 + h * 6.28) * 0.12;
                    float baseLen = 0.015 + 0.085 * hl;
                    float len = baseLen * (0.5 + I * 1.1) * (1.0 + 2.4 * corner);
                    float width = 0.13 + 0.12 * corner;
                    float present = step(h, 0.55 + 0.4 * I + 0.25 * corner);
                    float line = 1.0 - smoothstep(width * 0.7, width * 1.2, abs(f - 0.5 - wob));
                    float reach = 1.0 - smoothstep(len * 0.55, len, depth);
                    // thin continuous wobbling line hugging the very edge
                    float wob2 = sin(along * 38.0 + t * 6.0) * 0.004 + sin(along * 17.0 - t * 4.0) * 0.003;
                    float rim = 1.0 - smoothstep(0.004, 0.012, abs(depth - 0.006 - wob2));
                    float a = clamp(present * line * reach + rim * 0.6 * I, 0.0, 1.0);
                    over(acc, vec3(0.98), a);
                } else if (uEffect == 2) {                // snow
                    float a = 0.0;
                    float dn = 0.10 + 0.30 * I;
                    a += particles(uv, 14.0, 1.2, -1.0, 0.16, dn, 0.35);
                    a += particles(uv + 0.31, 24.0, 1.7, -1.0, 0.14, dn, 0.30) * 0.85;
                    a += particles(uv + 0.67, 40.0, 2.3, -1.0, 0.12, dn, 0.25) * 0.7;
                    over(acc, vec3(1.0), clamp(a, 0.0, 1.0) * 0.9);
                } else if (uEffect == 3) {                // rain
                    float a = 0.0;
                    for (int k = 0; k < 2; k++) {
                        float fk = float(k);
                        vec2 p = vec2(uv.x * uAspect * (55.0 + fk * 35.0) + uv.y * 7.0,
                                      uv.y * (7.0 + fk * 3.0) - t * (7.0 + fk * 3.0));
                        vec2 id = floor(p);
                        vec2 f = fract(p);
                        float h = hash(id + fk * 9.0);
                        float present = step(1.0 - (0.05 + 0.25 * I), h);
                        float xo = (hash(id + 2.0) - 0.5) * 0.5;
                        float line = 1.0 - smoothstep(0.03, 0.08, abs(f.x - 0.5 - xo));
                        float tail = smoothstep(0.0, 0.25, f.y) * (1.0 - smoothstep(0.55, 1.0, f.y));
                        a += present * line * tail * (0.55 - fk * 0.15);
                    }
                    over(acc, vec3(0.78, 0.86, 1.0), clamp(a, 0.0, 1.0));
                    over(acc, vec3(0.0, 0.03, 0.08), 0.10 * I);
                } else if (uEffect == 4) {                // floating dust
                    float a = 0.0;
                    float dn = 0.08 + 0.25 * I;
                    a += particles(uv, 9.0, 0.35, 1.0, 0.22, dn, 0.9);
                    a += particles(uv + 0.4, 17.0, 0.5, 1.0, 0.18, dn, 0.7) * 0.8;
                    a += particles(uv + 0.8, 30.0, 0.7, 1.0, 0.14, dn, 0.5) * 0.6;
                    over(acc, vec3(1.0, 0.96, 0.85), clamp(a, 0.0, 1.0) * 0.45);
                } else if (uEffect == 5) {                // sparks / embers
                    float dn = 0.05 + 0.22 * I;
                    float a1 = particles(uv, 18.0, 3.0, 1.0, 0.11, dn, 0.8);
                    float a2 = particles(uv + 0.5, 32.0, 4.2, 1.0, 0.09, dn, 0.6);
                    float fl = 0.7 + 0.3 * sin(t * 18.0 + uv.x * 40.0);
                    over(acc, mix(vec3(1.0, 0.45, 0.08), vec3(1.0, 0.85, 0.3), a2), clamp(a1 + a2, 0.0, 1.0) * fl);
                } else if (uEffect == 6) {                // old film
                    float fr = floor(t * 24.0);
                    float g = hash(uv * vec2(uAspect * 420.0, 420.0) + fr) - 0.5;
                    over(acc, g > 0.0 ? vec3(1.0) : vec3(0.0), abs(g) * (0.25 + 0.5 * I));
                    float fl = hash(vec2(floor(t * 13.0), 4.0));
                    over(acc, vec3(0.0), 0.10 * I * fl);
                    for (int k = 0; k < 3; k++) {
                        float fk = float(k);
                        float on = step(0.72, hash(vec2(floor(t * 10.0) + fk, 9.0 + fk)));
                        float x = hash(vec2(floor(t * 10.0), fk + 2.0));
                        float line = 1.0 - smoothstep(0.0006, 0.0022, abs(uv.x - x));
                        over(acc, vec3(0.95), on * line * (0.25 + 0.5 * I));
                    }
                    float sp = hash(floor(uv * vec2(uAspect * 40.0, 40.0)) + floor(t * 8.0));
                    vec2 sf = fract(uv * vec2(uAspect * 40.0, 40.0)) - 0.5;
                    over(acc, vec3(0.05), step(0.985, sp) * (1.0 - smoothstep(0.1, 0.3, length(sf))) * I);
                    float d = length((uv - 0.5) * vec2(uAspect * 0.8, 1.0));
                    over(acc, vec3(0.08, 0.05, 0.0), smoothstep(0.4, 0.95, d) * 0.55 * I);
                } else if (uEffect == 7) {                // dark vignette
                    float d = length((uv - 0.5) * vec2(uAspect * 0.75, 1.0));
                    float a = smoothstep(0.30, 0.85, d) * (0.45 + 0.5 * I);
                    over(acc, vec3(0.0), a);
                } else if (uEffect == 8) {                // soft white glow edge
                    float de = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
                    float breathe = 0.85 + 0.15 * sin(t * 1.6);
                    float a = 1.0 - smoothstep(0.0, 0.05 + 0.17 * I, de);
                    over(acc, vec3(1.0), a * a * 0.85 * breathe);
                } else if (uEffect == 9) {                // flash pulses
                    float period = 1.5;
                    float n = floor(t / period);
                    float ph = fract(t / period);
                    float on = step(0.3, hash(vec2(n, 5.0)));
                    float a = exp(-ph * 9.0) * (0.25 + 0.55 * I) * on;
                    over(acc, vec3(1.0), a);
                } else if (uEffect == 12) {               // falling leaves
                    for (int k = 0; k < 2; k++) {
                        float fk = float(k);
                        vec2 p = uv * vec2(uAspect, 1.0) * (7.0 + fk * 5.0 + 0.0);
                        p.y -= t * (0.9 + fk * 0.5);
                        vec2 id0 = floor(p);
                        p.x += sin(t * 0.9 + id0.y * 2.1 + fk) * 0.7;
                        vec2 id = floor(p);
                        vec2 f = fract(p) - vec2(0.3 + 0.4 * hash(id + 3.0), 0.3 + 0.4 * hash(id + 5.0));
                        float ang = t * (0.8 + hash(id)) + hash(id + 9.0) * 6.28;
                        vec2 d = vec2(cos(ang) * f.x - sin(ang) * f.y, sin(ang) * f.x + cos(ang) * f.y);
                        float present = step(1.0 - (0.10 + 0.28 * I), hash(id));
                        float leaf = 1.0 - smoothstep(0.08, 0.14, length(d * vec2(1.0, 2.6)));
                        float h = hash(id + 1.7);
                        vec3 c = h < 0.33 ? vec3(0.78, 0.35, 0.08) : (h < 0.66 ? vec3(0.85, 0.62, 0.12) : vec3(0.55, 0.22, 0.06));
                        over(acc, c, present * leaf * 0.95);
                    }
                } else if (uEffect == 13) {               // lightning flashes
                    float period = 3.2;
                    float n = floor(t / period);
                    float ph = fract(t / period) * period;
                    float on = step(0.35, hash(vec2(n, 21.0)));
                    float a = exp(-ph * 11.0) + 0.8 * step(0.22, ph) * exp(-(ph - 0.22) * 13.0);
                    over(acc, vec3(0.86, 0.9, 1.0), clamp(a * on * (0.3 + 0.6 * I), 0.0, 1.0));
                } else if (uEffect == 14) {               // fog / mist
                    float f = fbm(uv * vec2(uAspect * 2.0, 1.6) + vec2(t * 0.06, t * 0.02));
                    float a = smoothstep(0.25, 0.85, f) * (0.25 + 0.6 * I) * (0.55 + 0.7 * uv.y);
                    over(acc, vec3(0.86, 0.88, 0.9), clamp(a, 0.0, 0.9));
                } else if (uEffect == 15) {               // light rays
                    vec2 d = (uv - vec2(0.12, -0.12)) * vec2(uAspect, 1.0);
                    float ang = atan(d.y, d.x);
                    float rays = pow(0.5 + 0.5 * sin(ang * 14.0 + t * 0.35 + sin(ang * 5.0 + t * 0.2) * 2.0), 3.0);
                    float a = rays * (1.0 - smoothstep(0.2, 1.5, length(d))) * (0.15 + 0.4 * I);
                    over(acc, vec3(1.0, 0.93, 0.7), a);
                } else if (uEffect == 16) {               // film burn
                    float x = fract(t * 0.16) * 1.8 - 0.4;
                    float gx = (uv.x - x) * 2.6;
                    float g = exp(-gx * gx);
                    float flick = 0.7 + 0.3 * sin(t * 21.0) * sin(t * 13.0);
                    vec3 c = mix(vec3(1.0, 0.25, 0.02), vec3(1.0, 0.8, 0.2), uv.y);
                    over(acc, c, g * flick * (0.25 + 0.5 * I));
                } else if (uEffect == 17) {               // camera REC frame
                    float cx = min(uv.x, 1.0 - uv.x) * uAspect - 0.05;
                    float cy = min(uv.y, 1.0 - uv.y) - 0.05;
                    float br = 0.0;
                    if (abs(cy) < 0.0025 && cx >= 0.0 && cx < 0.07) br = 1.0;
                    if (abs(cx) < 0.0025 && cy >= 0.0 && cy < 0.07) br = 1.0;
                    over(acc, vec3(1.0), br * 0.85);
                    float d = length(vec2(uv.x * uAspect, uv.y) - vec2(0.13, 0.12));
                    float blink = step(0.5, fract(t * 0.8));
                    over(acc, vec3(0.95, 0.05, 0.05), blink * (1.0 - smoothstep(0.016, 0.02, d)));
                } else if (uEffect == 18) {               // falling ash
                    float dn = 0.10 + 0.30 * I;
                    float a = particles(uv, 20.0, 0.8, -1.0, 0.13, dn, 0.8);
                    a += particles(uv + 0.37, 34.0, 1.1, -1.0, 0.11, dn, 0.6) * 0.8;
                    over(acc, vec3(0.72, 0.70, 0.68), clamp(a, 0.0, 1.0) * 0.8);
                } else if (uEffect == 19) {               // fireflies
                    float dn = 0.04 + 0.12 * I;
                    float a = particles(uv, 8.0, 0.25, 1.0, 0.42, dn, 1.4);
                    a += particles(uv + 0.5, 13.0, 0.35, 1.0, 0.36, dn, 1.1);
                    float pulse = 0.45 + 0.55 * sin(t * 1.6 + uv.x * 5.0 + uv.y * 3.0);
                    over(acc, vec3(0.85, 1.0, 0.4), clamp(a, 0.0, 1.0) * pulse);
                } else if (uEffect == 20) {               // sun glare
                    vec2 d = (uv - vec2(0.9, 0.1)) * vec2(uAspect, 1.0);
                    float a = exp(-length(d) * 2.8) * (0.3 + 0.5 * I) * (0.92 + 0.08 * sin(t * 1.2));
                    a += exp(-abs(uv.y - 0.1) * 25.0) * exp(-abs(uv.x - 0.9) * 1.5) * 0.35 * I;
                    over(acc, vec3(1.0, 0.95, 0.8), clamp(a, 0.0, 0.95));
                } else if (uEffect == 21) {               // cracked glass corners
                    for (int k = 0; k < 2; k++) {
                        vec2 d = (k == 0) ? vec2(uv.x * uAspect, uv.y) : vec2((1.0 - uv.x) * uAspect, 1.0 - uv.y);
                        float r = length(d);
                        float ang = atan(d.y, d.x) / 1.5708;
                        float kk = ang * 7.0 + sin(r * 32.0) * 0.18 + (k == 0 ? 0.0 : 0.4);
                        float line = 1.0 - smoothstep(0.0, 0.05, abs(fract(kk) - 0.5) * (0.4 + r * 3.0));
                        float fade = 1.0 - smoothstep(0.15, 0.85, r);
                        float ring = 1.0 - smoothstep(0.0, 0.02, abs(fract(r * 7.0 + hash(vec2(floor(kk), 3.0)) * 0.5) - 0.5) * 0.4);
                        over(acc, vec3(0.95), clamp(line * fade * 0.8 + ring * fade * 0.12, 0.0, 1.0) * (0.4 + 0.6 * I));
                    }
                } else if (uEffect == 22) {               // night blue tint
                    over(acc, vec3(0.03, 0.07, 0.26), 0.22 + 0.4 * I);
                    float d = length((uv - 0.5) * vec2(uAspect * 0.7, 1.0));
                    over(acc, vec3(0.0, 0.0, 0.05), smoothstep(0.35, 0.9, d) * 0.4 * I);
                } else if (uEffect == 23) {               // sepia old photo
                    over(acc, vec3(0.46, 0.30, 0.10), 0.18 + 0.4 * I);
                    float g = hash(uv * vec2(uAspect * 300.0, 300.0) + floor(t * 12.0)) - 0.5;
                    over(acc, g > 0.0 ? vec3(1.0) : vec3(0.0), abs(g) * 0.12 * I);
                    float d = length((uv - 0.5) * vec2(uAspect * 0.8, 1.0));
                    over(acc, vec3(0.1, 0.05, 0.0), smoothstep(0.4, 0.95, d) * 0.5 * I);
                } else if (uEffect == 24) {               // drifting smoke
                    float f = fbm(vec2(uv.x * uAspect * 2.2, uv.y * 2.0 + t * 0.15) + vec2(t * 0.05, 0.0));
                    float a = smoothstep(0.38, 0.85, f) * (0.3 + 0.55 * I) * (0.4 + 0.8 * uv.y);
                    over(acc, vec3(0.34, 0.33, 0.32), clamp(a, 0.0, 0.9));
                } else if (uEffect == 25) {               // candlelight flicker
                    float flick = 0.55 + 0.45 * sin(t * 13.0) * sin(t * 7.3 + 1.0) * sin(t * 3.1);
                    float d = length((uv - 0.5) * vec2(uAspect * 0.8, 1.0));
                    over(acc, vec3(0.0), smoothstep(0.22 + 0.06 * flick, 0.85, d) * (0.45 + 0.4 * I));
                    over(acc, vec3(1.0, 0.55, 0.15), (0.06 + 0.12 * I) * (0.5 + 0.5 * flick));
                } else if (uEffect == 26) {               // cinema black bars
                    float h = 0.08 + 0.07 * I;
                    float a = max(step(uv.y, h), step(1.0 - h, uv.y));
                    over(acc, vec3(0.0), a);
                } else if (uEffect == 27) {               // flying birds
                    float flock = 0.0;
                    for (int bi = 0; bi < 8; bi++) {
                        float fi = float(bi);
                        float seed = hash(vec2(fi, 3.7));
                        float spd = 0.30 + 0.30 * hash(vec2(fi, 9.1));
                        float ph = fract(t * spd + seed);
                        float bx = (mod(fi, 2.0) > 0.5) ? (1.15 - 1.3 * ph) : (-0.15 + 1.3 * ph);
                        float by = 0.15 + 0.55 * hash(vec2(fi, 5.3)) + 0.03 * sin(t * 1.3 + fi * 2.0);
                        float sz = 0.045 + 0.04 * hash(vec2(fi, 1.9));
                        vec2 bp = (uv - vec2(bx, by)) * vec2(uAspect, 1.0) / sz;
                        float flap = sin(t * (7.0 + 3.0 * seed) + fi * 1.7) * 0.5;
                        float yw = flap * abs(bp.x);
                        float th = 0.16 * (1.0 - abs(bp.x) * 0.8);
                        float m = (1.0 - smoothstep(th * 0.5, max(th, 0.02), abs(bp.y - yw))) * step(abs(bp.x), 1.2);
                        m = max(m, 1.0 - smoothstep(0.1, 0.2, length(bp * vec2(1.0, 2.0))));
                        float show = step(fi, 1.0 + I * 6.0);
                        flock = max(flock, m * show);
                    }
                    over(acc, vec3(0.04, 0.04, 0.05), flock * 0.9);
                }
                gl_FragColor = acc * uAlpha;
            }
        """

        const val TRANSITION_FRAGMENT = """
            precision highp float;
            varying vec2 vUv;
            uniform sampler2D uTexA;
            uniform sampler2D uTexB;
            uniform vec2 uCropA;
            uniform vec2 uCropB;
            uniform float uZoomA;
            uniform float uZoomB;
            uniform float uP;
            uniform float uTime;
            uniform float uAspect;
            uniform int uType;
            uniform vec2 uOffA;
            uniform vec2 uOffB;
            uniform vec2 uTexelA;
            uniform vec2 uTexelB;
            uniform float uSharp;
            uniform float uContrast;
            uniform float uSat;

            const float PI = 3.14159265;

            // alpha = 0 outside the picture
            vec4 sampleA(vec2 uv, float extra) {
                if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return vec4(0.0);
                vec2 c = (uv - 0.5) / (uZoomA * extra) + 0.5 - uOffA;
                c = (c - 0.5) * uCropA + 0.5;
                vec3 col = texture2D(uTexA, c).rgb;
                if (uSharp > 0.0) {
                    vec3 n = texture2D(uTexA, c + vec2(uTexelA.x, 0.0)).rgb
                           + texture2D(uTexA, c - vec2(uTexelA.x, 0.0)).rgb
                           + texture2D(uTexA, c + vec2(0.0, uTexelA.y)).rgb
                           + texture2D(uTexA, c - vec2(0.0, uTexelA.y)).rgb;
                    col += (col - n * 0.25) * uSharp * 1.6;
                }
                return vec4(col, 1.0);
            }
            vec4 sampleB(vec2 uv, float extra) {
                if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return vec4(0.0);
                vec2 c = (uv - 0.5) / (uZoomB * extra) + 0.5 - uOffB;
                c = (c - 0.5) * uCropB + 0.5;
                vec3 col = texture2D(uTexB, c).rgb;
                if (uSharp > 0.0) {
                    vec3 n = texture2D(uTexB, c + vec2(uTexelB.x, 0.0)).rgb
                           + texture2D(uTexB, c - vec2(uTexelB.x, 0.0)).rgb
                           + texture2D(uTexB, c + vec2(0.0, uTexelB.y)).rgb
                           + texture2D(uTexB, c - vec2(0.0, uTexelB.y)).rgb;
                    col += (col - n * 0.25) * uSharp * 1.6;
                }
                return vec4(col, 1.0);
            }
            vec2 rotScale(vec2 uv, float ang, float sc) {
                vec2 d = uv - 0.5;
                d.x *= uAspect;
                float cs = cos(ang);
                float sn = sin(ang);
                d = vec2(cs * d.x - sn * d.y, sn * d.x + cs * d.y);
                d.x /= uAspect;
                return d / sc + 0.5;
            }
            float hash(vec2 p) {
                return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
            }
            vec3 blurA(vec2 uv, float r) {
                vec3 acc = vec3(0.0);
                for (int i = -2; i <= 2; i++) {
                    for (int j = -2; j <= 2; j++) {
                        vec2 o = vec2(float(i) / uAspect, float(j)) * r;
                        acc += sampleA(clamp(uv + o, 0.0, 1.0), 1.0).rgb;
                    }
                }
                return acc / 25.0;
            }
            vec3 blurB(vec2 uv, float r) {
                vec3 acc = vec3(0.0);
                for (int i = -2; i <= 2; i++) {
                    for (int j = -2; j <= 2; j++) {
                        vec2 o = vec2(float(i) / uAspect, float(j)) * r;
                        acc += sampleB(clamp(uv + o, 0.0, 1.0), 1.0).rgb;
                    }
                }
                return acc / 25.0;
            }

            void main() {
                vec2 uv = vUv;
                float p = uP;
                float e = p * p * (3.0 - 2.0 * p);
                vec3 col = vec3(0.0);

                if (uType == 1) {                         // fade through black
                    if (p < 0.5) col = sampleA(uv, 1.0).rgb * (1.0 - p * 2.0);
                    else col = sampleB(uv, 1.0).rgb * (p * 2.0 - 1.0);
                } else if (uType == 2) {                  // cross dissolve
                    col = mix(sampleA(uv, 1.0).rgb, sampleB(uv, 1.0).rgb, e);
                } else if (uType == 3) {                  // slide left (push)
                    float x = uv.x + e;
                    if (x < 1.0) col = sampleA(vec2(x, uv.y), 1.0).rgb;
                    else col = sampleB(vec2(x - 1.0, uv.y), 1.0).rgb;
                } else if (uType == 4) {                  // slide right
                    float x = uv.x - e;
                    if (x >= 0.0) col = sampleA(vec2(x, uv.y), 1.0).rgb;
                    else col = sampleB(vec2(x + 1.0, uv.y), 1.0).rgb;
                } else if (uType == 5) {                  // slide up
                    float y = uv.y + e;
                    if (y < 1.0) col = sampleA(vec2(uv.x, y), 1.0).rgb;
                    else col = sampleB(vec2(uv.x, y - 1.0), 1.0).rgb;
                } else if (uType == 6) {                  // slide down
                    float y = uv.y - e;
                    if (y >= 0.0) col = sampleA(vec2(uv.x, y), 1.0).rgb;
                    else col = sampleB(vec2(uv.x, y + 1.0), 1.0).rgb;
                } else if (uType == 7) {                  // spin
                    vec4 a = sampleA(rotScale(uv, e * 1.5708, 1.0 - 0.6 * e), 1.0);
                    vec4 b = sampleB(rotScale(uv, (e - 1.0) * 1.5708, 0.4 + 0.6 * e), 1.0);
                    col = mix(col, b.rgb, b.a * smoothstep(0.0, 0.5, e));
                    col = mix(col, a.rgb, a.a * (1.0 - smoothstep(0.5, 1.0, e)));
                } else if (uType == 8) {                  // blur
                    float r = sin(p * PI) * 0.04;
                    col = mix(blurA(uv, r), blurB(uv, r), smoothstep(0.3, 0.7, p));
                } else if (uType == 9) {                  // wipe (left -> right, soft edge)
                    float edge = e * 1.1 - 0.05;
                    float m = smoothstep(edge - 0.05, edge + 0.05, uv.x);
                    col = mix(sampleB(uv, 1.0).rgb, sampleA(uv, 1.0).rgb, m);
                } else if (uType == 10) {                 // glitch
                    float amp = sin(p * PI);
                    float tt = floor(uTime * 24.0);
                    float band = floor(uv.y * 18.0);
                    float h = hash(vec2(band, tt));
                    float on = step(0.55, hash(vec2(band + 7.0, tt)));
                    float shift = (h - 0.5) * 0.25 * amp * on;
                    vec2 u = vec2(uv.x + shift, uv.y);
                    float split = 0.015 * amp;
                    bool useB = p > 0.5 + (hash(vec2(band, tt + 3.0)) - 0.5) * 0.3;
                    if (useB) {
                        col = vec3(sampleB(u + vec2(split, 0.0), 1.0).r,
                                   sampleB(u, 1.0).g,
                                   sampleB(u - vec2(split, 0.0), 1.0).b);
                    } else {
                        col = vec3(sampleA(u + vec2(split, 0.0), 1.0).r,
                                   sampleA(u, 1.0).g,
                                   sampleA(u - vec2(split, 0.0), 1.0).b);
                    }
                    col += (hash(uv * vec2(uAspect * 400.0, 300.0) + tt) - 0.5) * 0.15 * amp;
                } else if (uType == 11) {                 // zoom through
                    vec3 a = sampleA(uv, 1.0 + e * 0.8).rgb;
                    vec3 b = sampleB(uv, 1.4 - 0.4 * e).rgb;
                    col = mix(a, b, e);
                } else if (uType == 12) {                 // dip to white
                    if (p < 0.5) col = mix(sampleA(uv, 1.0).rgb, vec3(1.0), p * 2.0);
                    else col = mix(vec3(1.0), sampleB(uv, 1.0).rgb, p * 2.0 - 1.0);
                } else if (uType == 13) {                 // flash
                    vec3 base = (p < 0.5) ? sampleA(uv, 1.0).rgb : sampleB(uv, 1.0).rgb;
                    float f = 1.0 - abs(2.0 * p - 1.0);
                    col = mix(base, vec3(1.0), f * f);
                } else if (uType == 14) {                 // circle reveal
                    vec2 d = (uv - 0.5) * vec2(uAspect, 1.0);
                    float maxR = length(vec2(uAspect, 1.0) * 0.5) * 1.05;
                    float r = e * maxR;
                    float m = 1.0 - smoothstep(r - 0.02, r + 0.02, length(d));
                    col = mix(sampleA(uv, 1.0).rgb, sampleB(uv, 1.0).rgb, m);
                } else if (uType == 15) {                 // pixelate
                    float amp = sin(p * PI);
                    float cells = mix(480.0, 14.0, amp);
                    vec2 q = (floor(uv * vec2(cells * uAspect, cells)) + 0.5) / vec2(cells * uAspect, cells);
                    col = (p < 0.5) ? sampleA(q, 1.0).rgb : sampleB(q, 1.0).rgb;
                } else if (uType == 16) {                 // shake cut
                    float amp = sin(p * PI);
                    float tt = floor(uTime * 30.0);
                    vec2 o = (vec2(hash(vec2(tt, 1.0)), hash(vec2(tt, 2.0))) - 0.5) * 0.09 * amp;
                    float split = 0.012 * amp;
                    vec2 u = uv + o;
                    if (p < 0.5) {
                        col = vec3(sampleA(u + vec2(split, 0.0), 1.15).r, sampleA(u, 1.15).g, sampleA(u - vec2(split, 0.0), 1.15).b);
                    } else {
                        col = vec3(sampleB(u + vec2(split, 0.0), 1.15).r, sampleB(u, 1.15).g, sampleB(u - vec2(split, 0.0), 1.15).b);
                    }
                } else {                                  // 0: hard cut
                    col = (p < 0.5) ? sampleA(uv, 1.0).rgb : sampleB(uv, 1.0).rgb;
                }
                float lum = dot(col, vec3(0.299, 0.587, 0.114));
                col = mix(vec3(lum), col, uSat);
                col = (col - 0.5) * uContrast + 0.5;
                gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
            }
        """
    }
}
