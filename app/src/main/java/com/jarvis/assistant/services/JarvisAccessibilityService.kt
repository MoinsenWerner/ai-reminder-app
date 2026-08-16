package com.jarvis.assistant.services

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.assistant.data.JarvisLogger
import com.jarvis.assistant.model.JarvisModel

class JarvisAccessibilityService : AccessibilityService() {
    private val model = JarvisModel()
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString().orEmpty()
        val text = collect(rootInActiveWindow).ifBlank { event?.text?.joinToString(" ").orEmpty() }
        if (text.isNotBlank()) {
            val action = model.infer(text)
            if (action.confidence >= .8) JarvisLogger.log(this, "screen:$pkg", "${action.type}:${action.title}:${action.reminders}")
        }
    }
    override fun onInterrupt() { JarvisLogger.log(this, "accessibility", "interrupted") }
    private fun collect(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val own = listOfNotNull(node.text, node.contentDescription).joinToString(" ")
        return (0 until node.childCount).joinToString(" ", prefix = "$own ") { collect(node.getChild(it)) }.trim()
    }
}
