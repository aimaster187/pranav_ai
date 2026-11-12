package com.pranav.ai.service

import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlin.math.cos
import kotlin.math.sin

class HologramWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = HologramEngine()

    inner class HologramEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var angle = 0.0
        private var touchX = -1f
        private var touchY = -1f
        private var visible = true
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        private val drawRunner = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) handler.postDelayed(this, 33)
            }
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) handler.post(drawRunner) else handler.removeCallbacks(drawRunner)
        }

        override fun onTouchEvent(event: MotionEvent) {
            touchX = event.x; touchY = event.y
        }

        private fun drawFrame() {
            val holder: SurfaceHolder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas() ?: return
                val w = canvas.width.toFloat(); val h = canvas.height.toFloat()
                val cx = if (touchX > 0) (w / 2f + (touchX - w / 2f) * 0.15f) else w / 2f
                val cy = if (touchY > 0) (h / 2f + (touchY - h / 2f) * 0.15f) else h / 2f

                canvas.drawColor(Color.parseColor("#060913"))
                angle += 0.04

                for (i in 0 until 36) {
                    val theta = angle + (i * Math.PI / 18.0)
                    val rx = (cos(theta) * 220).toFloat()
                    val ry = (sin(theta * 1.5) * 220).toFloat()
                    paint.color = if (i % 2 == 0) Color.parseColor("#FF1744") else Color.parseColor("#00E5FF")
                    paint.alpha = 180
                    canvas.drawCircle(cx + rx, cy + ry, 5f, paint)
                    paint.strokeWidth = 1.5f; paint.alpha = 60
                    canvas.drawLine(cx, cy, cx + rx, cy + ry, paint)
                }
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f
                paint.color = Color.parseColor("#FF1744"); paint.alpha = 200
                canvas.drawCircle(cx, cy, 240f, paint)
                paint.style = Paint.Style.FILL
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
