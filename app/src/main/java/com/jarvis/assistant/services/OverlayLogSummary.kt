package com.jarvis.assistant.services

import android.content.Context
import com.jarvis.assistant.data.JarvisLogger

fun Context.recentSummary(maxLines: Int = 5): String =
    JarvisLogger.recentSummary(this, maxLines)
