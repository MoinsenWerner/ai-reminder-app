package com.jarvis.assistant.data

import android.content.Context
import java.time.Instant

object JarvisLogger {
    @Volatile private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    @Synchronized fun log(context: Context, trigger: String, detail: String) {
        initialize(context)
        val line = "${Instant.now()}\ttrigger=$trigger\tdetail=${detail.replace('\n', ' ')}\n"
        context.openFileOutput("actions.log", Context.MODE_APPEND).use { it.write(line.toByteArray()) }
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
