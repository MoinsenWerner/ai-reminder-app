package com.jarvis.assistant.data

import android.content.Context
import android.os.Environment
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object JarvisLogger {
    internal const val EXTERNAL_FLUSH_INTERVAL_SECONDS = 3L
    @Volatile private var applicationContext: Context? = null
    private val pendingExternalLines = ConcurrentLinkedQueue<String>()
    private val schedulerStarted = AtomicBoolean(false)
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "jarvis-action-log").apply { isDaemon = true }
    }

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        if (schedulerStarted.compareAndSet(false, true)) {
            scheduler.scheduleWithFixedDelay(::flushExternal, EXTERNAL_FLUSH_INTERVAL_SECONDS, EXTERNAL_FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS)
        }
    }

    @Synchronized fun log(context: Context, trigger: String, detail: String) {
        initialize(context)
        val line = "${Instant.now()}\ttrigger=$trigger\tdetail=${detail.replace('\n', ' ')}\n"
        context.openFileOutput("actions.log", Context.MODE_APPEND).use { it.write(line.toByteArray()) }
        pendingExternalLines.add(line)
    }

    @Synchronized fun flushExternal() {
        if (pendingExternalLines.isEmpty()) return
        val file = File(Environment.getExternalStorageDirectory(), "Jarvis/logs/actions.log")
        val batch = mutableListOf<String>()
        while (true) batch += pendingExternalLines.poll() ?: break
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText(batch.joinToString(""))
        }.onFailure { batch.forEach(pendingExternalLines::add) }
    }

    fun recentSummary(maxLines: Int = 5): String =
        applicationContext?.let { recentSummary(it, maxLines) }.orEmpty()

    @Synchronized fun recentSummary(context: Context, maxLines: Int = 5): String {
        if (maxLines <= 0) return ""
        val lines = runCatching { context.openFileInput("actions.log").bufferedReader().use { it.readLines() } }
            .getOrDefault(emptyList())
        return summarize(lines, maxLines)
    }

    internal fun summarize(lines: List<String>, maxLines: Int): String = lines
        .filter(String::isNotBlank)
        .takeLast(maxLines.coerceAtLeast(0))
        .joinToString("\n")
}
