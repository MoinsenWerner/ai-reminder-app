package com.jarvis.assistant.data

import android.content.Context
import java.time.Instant

object JarvisLogger {
    @Synchronized fun log(context: Context, trigger: String, detail: String) {
        val line = "${Instant.now()}\ttrigger=$trigger\tdetail=${detail.replace('\n', ' ')}\n"
        context.openFileOutput("actions.log", Context.MODE_APPEND).use { it.write(line.toByteArray()) }
    }
}
