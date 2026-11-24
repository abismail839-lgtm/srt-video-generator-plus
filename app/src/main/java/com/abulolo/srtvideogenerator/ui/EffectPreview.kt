package com.abulolo.srtvideogenerator.ui

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import com.abulolo.srtvideogenerator.EffectSettings
import com.abulolo.srtvideogenerator.EffectType
import com.abulolo.srtvideogenerator.gl.GlTexture
import com.abulolo.srtvideogenerator.gl.SurfaceRenderer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

// ===== NEW CODE (part 2): live effect preview, same shader as the exporter =====

class EffectPreviewParams {
    @Volatile var settings: EffectSettings = EffectSettings()
}

class EffectPreviewView(
    context: Context,
    bitmap: Bitmap,
    params: EffectPreviewParams
) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        setRenderer(Rend(bitmap, params))
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    private class Rend(val bmp: Bitmap, val params: EffectPreviewParams) : Renderer {
        private var r: SurfaceRenderer? = null
        private var tex: GlTexture? = null
        private val start = SystemClock.uptimeMillis()

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {}

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            r?.release()
            r = SurfaceRenderer(width, height).also {
                it.init()
                tex = it.createTexture(bmp)
            }
        }

        override fun onDrawFrame(gl: GL10?) {
            val rr = r ?: return
            val t = tex ?: return
            val e = params.settings
            val loopLen = (e.onlyFirstSec ?: 4f) + 2f
            val now = (SystemClock.uptimeMillis() - start) / 1000f
            val local = now % loopLen
            val alpha = e.onlyFirstSec?.let { ((it - local) / 0.6f).coerceIn(0f, 1f) } ?: 1f

            var zoom = 1.0f
            var ox = 0f
            var oy = 0f
            val sp = local * e.speed
            if (e.type == EffectType.SHAKE) {
                val amp = (0.004f + 0.014f * e.intensity) * alpha
                val ts = sp * 11f
                ox = amp * (sin(ts * 3.7f) + sin(ts * 2.3f + 1.3f)) * 0.5f
                oy = amp * (sin(ts * 3.1f + 0.7f) + sin(ts * 2.9f + 2.1f)) * 0.5f
                zoom = 1f + amp * 2f + 0.004f
            } else if (e.type == EffectType.ZOOM_PULSE) {
                val x = sp / 1.2f
                val ph = x - floor(x)
                zoom = 1f + 0.09f * e.intensity * alpha * exp(-ph * 6f)
            }
            rr.draw(null, t, 1f, 0, 1f, zoom, now, 0f, 0f, ox, oy)
            if (e.type != EffectType.SHAKE && e.type != EffectType.ZOOM_PULSE) {
                rr.drawOverlay(e.type.ordinal, e.intensity, sp + 50f, alpha)
            }
            GLES20.glFlush()
        }
    }
}
