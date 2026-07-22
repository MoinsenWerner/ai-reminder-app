package com.jarvis.assistant.services

import android.app.*
import android.content.Intent
import android.os.IBinder
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.R
import com.jarvis.assistant.data.JarvisLogger

class JarvisVoiceService : Service() {
    override fun onCreate() { super.onCreate(); startForeground(7, notification()); JarvisLogger.log(this, "voice_service", "started; wake phrase pipeline ready for user enrollment") }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    private fun notification(): Notification {
        val id = "jarvis_voice"; val nm = getSystemService(NotificationManager::class.java); nm.createNotificationChannel(NotificationChannel(id, "Jarvis Hintergrunddienst", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this,0,Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,id).setSmallIcon(R.drawable.ic_stat_jarvis).setContentTitle("Jarvis läuft").setContentText("Wartet auf erlaubte Sprach- und Tasker-Ereignisse").setContentIntent(pi).build()
    }
}
