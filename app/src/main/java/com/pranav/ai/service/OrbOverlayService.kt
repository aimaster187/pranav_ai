package com.pranav.ai.service

import android.app.*
import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator

class OrbOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var orbView: OrbCanvasView

    override fun onCreate() {
        super.onCreate()
        val channelId = "pranav_orb_v2"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "PRANAV AI Core", NotificationManager.IMPORTANCE_LOW)
            )
        }
        startForeground(101, Notification.Builder(this, channelId)
            .setContentTitle("PRANAV AI Active")
            .setContentText("Tap bottom orb to command")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).build())

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val prefs = getSharedPreferences("pranav_prefs", Context.MODE_PRIVATE)
        val orbColor = prefs.getInt("orb_color", Color.parseColor("#FF1744"))
        val sizeDp = prefs.getInt("orb_size", 46)
        val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
        orbView = OrbCanvasView(this, orbColor)

        val params = WindowManager.LayoutParams(
            sizePx, sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (16 * resources.displayMetrics.density).toInt()
        }

        orbView.setOnClickListener {
            orbView.isListening = !orbView.isListening
            sendBroadcast(Intent("com.pranav.ai.TOGGLE_VOICE"))
        }
        windowManager.addView(orbView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::orbView.isInitialized) windowManager.removeView(orbView)
    }
    override fun onBind(intent: Intent?): IBinder? = null

    class OrbCanvasView(context: Context, var coreColor: Int) : View(context) {
        var isListening = false
        private var phase = 0f
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f }
        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        init {
            ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 2000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
                addUpdateListener { phase = it.animatedValue as Float; invalidate() }
                start()
            }
        }
        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f; val cy = height / 2f; val r = (width / 2f) - 8f
            val pulse = (Math.sin(Math.toRadians(phase.toDouble())).toFloat() + 1f) / 2f
            ringPaint.color = coreColor; ringPaint.alpha = if (isListening) 255 else (140 + (pulse * 115).toInt())
            canvas.drawCircle(cx, cy, r - (pulse * 4f), ringPaint)
            corePaint.color = coreColor; corePaint.alpha = if (isListening) 240 else 170
            canvas.drawCircle(cx, cy, (r * 0.55f) + (pulse * 3f), corePaint)
        }
    }
}
