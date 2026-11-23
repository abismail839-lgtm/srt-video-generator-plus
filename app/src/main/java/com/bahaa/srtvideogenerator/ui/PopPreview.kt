package com.bahaa.srtvideogenerator.ui

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import com.bahaa.srtvideogenerator.PopItem
import com.bahaa.srtvideogenerator.gl.GlTexture
import com.bahaa.srtvideogenerator.gl.PopLayer
import com.bahaa.srtvideogenerator.gl.SurfaceRenderer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

// ===== NEW CODE (update 5): live preview of a pop item on its scene (same drawing code as the export) =====

class PopPreviewParams {
    @Volatile var item: PopItem? = null
}

class PopPreviewView(
    context: Context,
    bitmap: Bitmap,
    params: PopPreviewParams
) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        setRenderer(Rend(context, bitmap, params))
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    private class Rend(val ctx: Context, val bmp: Bitmap, val params: PopPreviewParams) : Renderer {
        private var r: SurfaceRenderer? = null
        private var layer: PopLayer? = null
        private var tex: GlTexture? = null
        private val start = SystemClock.uptimeMillis()

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {}

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            layer?.release()
            r?.release()
            val rr = SurfaceRenderer(width, height)
            rr.init()
            tex = rr.createTexture(bmp)
            layer = PopLayer(ctx, rr, width, height)
            r = rr
        }

        override fun onDrawFrame(gl: GL10?) {
            val rr = r ?: return
            val t = tex ?: return
            val now = (SystemClock.uptimeMillis() - start) / 1000f
            rr.draw(null, t, 1f, 0, 1f, 1f, now)
            val item = params.item
            if (item != null) {
                val loop = item.startSec + item.durSec + 1f
                layer?.draw(item, now % loop, 1f)
            }
            GLES20.glFlush()
        }
    }
}
