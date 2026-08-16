package com.jarvis.assistant.services

import android.content.Context
import com.jarvis.assistant.data.JarvisLogger

fun recentSummary(context: Context, maxLines: Int = 5): String =
    JarvisLogger.recentSummary(context, maxLines)
