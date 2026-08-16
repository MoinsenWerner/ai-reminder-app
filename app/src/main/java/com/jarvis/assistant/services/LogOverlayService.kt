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

class LogOverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var view: TextView? = null
    private val updater = object : Runnable {
        override fun run() {
            val summary = recentSummary(3)
            view?.text = "J${if (summary.isBlank()) "" else "\n$summary"}"
            handler.postDelayed(this, 3_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) return stopSelf()
        view = TextView(this).apply {
            text = "J"
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC263238.toInt())
            gravity = Gravity.CENTER
            setPadding(18, 12, 18, 12)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 16; y = 120 }
        getSystemService(WindowManager::class.java).addView(view, params)
        updater.run()
    }

    override fun onDestroy() {
        handler.removeCallbacks(updater)
        view?.let { getSystemService(WindowManager::class.java).removeView(it) }
        view = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
