package com.jarvis.assistant.model

import java.util.Locale

data class JarvisAction(val type: String, val title: String, val confidence: Double, val reminders: List<String> = emptyList())

class JarvisModel {
    fun infer(text: String): JarvisAction {
        val t = text.lowercase(Locale.ROOT)
        val scoreCalendar = listOf("treffen", "termin", "geburtstag", "meeting", "morgen", "uhr").count { it in t }
        val scoreReminder = listOf("erinnere", "nicht vergessen", "deadline", "abgabe", "todo").count { it in t }
        return when {
            scoreCalendar >= 2 -> JarvisAction("calendar_event", summarize(text), .86, listOf("1 day before", "1 hour before", "15 minutes before"))
            scoreReminder >= 1 -> JarvisAction("reminder", summarize(text), .82, listOf("contextual"))
            "hey jarvis" in t -> JarvisAction("voice_command", text.substringAfter("hey jarvis").trim().ifBlank { "Bereit" }, .9)
            else -> JarvisAction("note", summarize(text), .55)
        }
    }
    private fun summarize(text: String) = text.trim().replace(Regex("\\s+"), " ").take(80).ifBlank { "Unbenannte Information" }
}
