package com.jarvis.assistant.services

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.jarvis.assistant.data.JarvisLogger

class JarvisOverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: TextView? = null
    private var windowManager: WindowManager? = null

    private val updater = object : Runnable {
        override fun run() {
            overlay?.text = JarvisLogger.recentSummary()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlay = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 10f
            setPadding(8, 8, 8, 8)
            text = JarvisLogger.recentSummary()
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        windowManager?.addView(overlay, params)
        handler.post(updater)
        JarvisLogger.log(this, "overlay", "started")
    }

    override fun onDestroy() {
        handler.removeCallbacks(updater)
        overlay?.let { windowManager?.removeView(it) }
        overlay = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
