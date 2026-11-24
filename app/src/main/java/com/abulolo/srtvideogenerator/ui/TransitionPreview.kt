package com.abulolo.srtvideogenerator.ui

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import com.abulolo.srtvideogenerator.TransitionType
import com.abulolo.srtvideogenerator.gl.GlTexture
import com.abulolo.srtvideogenerator.gl.SurfaceRenderer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

// ===== NEW CODE: live preview that runs the SAME shader the exporter uses =====

class TransitionPreviewView(
    context: Context,
    private val a: Bitmap,
    private val b: Bitmap,
    type: TransitionType,
    durationMs: Int
) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        setRenderer(Rend(a, b, type, durationMs))
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    private class Rend(
        val bmpA: Bitmap, val bmpB: Bitmap, val type: TransitionType, val durMs: Int
    ) : Renderer {
        private var r: SurfaceRenderer? = null
        private var ta: GlTexture? = null
        private var tb: GlTexture? = null
        private val start = SystemClock.uptimeMillis()
        private var cycle = -1L
        private var current = TransitionType.FADE

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {}

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            r?.release()
            r = SurfaceRenderer(width, height).also {
                it.init()
                ta = it.createTexture(bmpA)
                tb = it.createTexture(bmpB)
            }
        }

        override fun onDrawFrame(gl: GL10?) {
            val rr = r ?: return
            val hold = 500L
            val dur = durMs.toLong().coerceAtLeast(100L)
            val total = hold + dur + hold
            val now = SystemClock.uptimeMillis() - start
            val c = now / total
            if (c != cycle) {
                cycle = c
                current = if (type == TransitionType.RANDOM) TransitionType.pool.random() else type
            }
            val local = now % total
            val p = ((local - hold).toFloat() / dur).coerceIn(0f, 1f)
            rr.draw(ta, tb!!, p, current.shaderId, 1f, 1f, now / 1000f)
            GLES20.glFlush()
        }
    }
}
