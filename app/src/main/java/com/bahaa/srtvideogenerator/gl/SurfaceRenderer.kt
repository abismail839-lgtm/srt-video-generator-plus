package com.bahaa.srtvideogenerator.gl

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

    private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply { put(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)); position(0) }

    private var captionKey: String? = null
    private var captionTex = 0
    private var captionW = 0
    private var captionH = 0

    fun init() {
        transProgram = link(VERTEX, TRANSITION_FRAGMENT)
        capProgram = link(VERTEX, CAPTION_FRAGMENT)
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
        zoomFrom: Float, zoomTo: Float, timeSec: Float
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

        fun u(name: String) = GLES20.glGetUniformLocation(transProgram, name)
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
        GLES20.glUniform4f(u("uRect"), -1f, -1f, 1f, 1f)
        drawQuad(transProgram)
    }

    /** word == true: big centred word-by-word style; false: normal bottom subtitle. */
    fun drawCaption(text: String, word: Boolean) {
        if (text.isBlank()) return
        val key = "$word|$text"
        if (key != captionKey) {
            if (captionTex != 0) GLES20.glDeleteTextures(1, intArrayOf(captionTex), 0)
            buildCaption(text, word)
            captionKey = key
        }
        val wNdc = 2f * captionW / width
        val hNdc = 2f * captionH / height
        val bottom = if (word) (1f - 2f * 0.78f) - hNdc / 2f else (1f - 2f * 0.93f)
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

    private fun buildCaption(text: String, word: Boolean) {
        val minSide = min(width, height).toFloat()
        val size = if (word) minSide * 0.13f else minSide * 0.055f
        val pad = (size * 0.45f).toInt()
        val maxW = (width * 0.88f).toInt()

        val fillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD; textSize = size; color = Color.WHITE
        }
        val strokePaint = TextPaint(fillPaint).apply {
            style = Paint.Style.STROKE; strokeWidth = size * 0.16f; color = Color.BLACK
            strokeJoin = Paint.Join.ROUND
        }
        fun layout(p: TextPaint) = StaticLayout.Builder.obtain(text, 0, text.length, p, maxW)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 1.05f).build()

        val fill = layout(fillPaint)
        val stroke = layout(strokePaint)
        val bw = maxW + pad * 2
        val bh = fill.height + pad * 2
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (!word) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 0, 0, 0) }
            c.drawRoundRect(RectF(0f, 0f, bw.toFloat(), bh.toFloat()), size * 0.4f, size * 0.4f, bg)
        }
        c.translate(pad.toFloat(), pad.toFloat())
        stroke.draw(c)
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
        transProgram = 0
        capProgram = 0
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
        // 7 spin, 8 blur, 9 wipe, 10 glitch, 11 zoom
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

            const float PI = 3.14159265;

            // alpha = 0 outside the picture
            vec4 sampleA(vec2 uv, float extra) {
                if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return vec4(0.0);
                vec2 c = (uv - 0.5) / (uZoomA * extra) + 0.5;
                c = (c - 0.5) * uCropA + 0.5;
                return vec4(texture2D(uTexA, c).rgb, 1.0);
            }
            vec4 sampleB(vec2 uv, float extra) {
                if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return vec4(0.0);
                vec2 c = (uv - 0.5) / (uZoomB * extra) + 0.5;
                c = (c - 0.5) * uCropB + 0.5;
                return vec4(texture2D(uTexB, c).rgb, 1.0);
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
                } else {                                  // 0: hard cut
                    col = (p < 0.5) ? sampleA(uv, 1.0).rgb : sampleB(uv, 1.0).rgb;
                }
                gl_FragColor = vec4(col, 1.0);
            }
        """
    }
}
