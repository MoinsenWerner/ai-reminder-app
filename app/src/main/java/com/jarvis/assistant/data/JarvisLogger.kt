package com.jarvis.assistant.data

import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import java.io.File
import java.time.Instant

object JarvisLogger {
    private const val EXTERNAL_LOG_NAME = "app_actions.log"
    private val pendingExternalLines = mutableListOf<String>()
    private val recentLines = ArrayDeque<Pair<Long, String>>()
    private var flushStarted = false

    @Synchronized fun log(context: Context, trigger: String, detail: String) {
        val line = "${Instant.now()}\ttrigger=$trigger\tdetail=${detail.replace('\n', ' ')}\n"
        pendingExternalLines += line
        recentLines += System.currentTimeMillis() to line.trim()
        trimRecentLocked()
        context.openFileOutput("actions.log", Context.MODE_APPEND).use { it.write(line.toByteArray()) }
    }

    fun startPeriodicExternalFlush(context: Context) {
        synchronized(this) {
            if (flushStarted) return
            flushStarted = true
        }
        val appContext = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        fun schedule() {
            handler.postDelayed({
                flushExternal(appContext)
                schedule()
            }, 3_000L)
        }
        schedule()
    }

    @Synchronized fun recentSummary(windowMillis: Long = 5_000L): String {
        trimRecentLocked(windowMillis)
        return recentLines.joinToString("\n") { it.second }.ifBlank { "Jarvis: keine Aktion in den letzten 5 Sekunden" }
    }

    @Synchronized private fun flushExternal(context: Context) {
        val lines = if (pendingExternalLines.isEmpty()) {
            listOf("${Instant.now()}\ttrigger=periodic_flush\tdetail=no actions in last 3 seconds\n")
        } else {
            pendingExternalLines.toList().also { pendingExternalLines.clear() }
        }
        val target = File(Environment.getExternalStorageDirectory(), EXTERNAL_LOG_NAME)
        val payload = lines.joinToString(separator = "")
        runCatching { target.appendText(payload) }
            .onFailure { context.openFileOutput(EXTERNAL_LOG_NAME, Context.MODE_APPEND).use { file -> file.write(payload.toByteArray()) } }
    }

    private fun trimRecentLocked(windowMillis: Long = 5_000L) {
        val cutoff = System.currentTimeMillis() - windowMillis
        while (recentLines.firstOrNull()?.first?.let { it < cutoff } == true) recentLines.removeFirst()
    }
}
