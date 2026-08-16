package com.jarvis.assistant.services

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.assistant.data.JarvisLogger
import com.jarvis.assistant.data.JarvisSettings
import com.jarvis.assistant.data.Settings
import com.jarvis.assistant.model.AssistantEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class JarvisAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var settings = Settings()
    @Volatile private var lastProcessedText = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch { JarvisSettings(this@JarvisAccessibilityService).flow.collectLatest { settings = it } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString().orEmpty()
        if (pkg !in settings.observedApps) return
        val text = collect(rootInActiveWindow).ifBlank { event?.text?.joinToString(" ").orEmpty() }
        if (text.isNotBlank() && text != lastProcessedText) {
            lastProcessedText = text
            scope.launch {
                runCatching { AssistantEngine.process(this@JarvisAccessibilityService, text, settings) }
                    .onFailure { JarvisLogger.log(this@JarvisAccessibilityService, "screen:$pkg:error", it.message.orEmpty()) }
            }
        }
    }
    override fun onInterrupt() { JarvisLogger.log(this, "accessibility", "interrupted") }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    private fun collect(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val own = listOfNotNull(node.text, node.contentDescription).joinToString(" ")
        return (0 until node.childCount).joinToString(" ", prefix = "$own ") { collect(node.getChild(it)) }.trim()
    }
}
