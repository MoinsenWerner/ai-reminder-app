package com.jarvis.assistant.actions

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import com.jarvis.assistant.data.JarvisLogger
import com.jarvis.assistant.model.JarvisAction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

object JarvisActionExecutor {
    fun execute(context: Context, action: JarvisAction, sourceText: String): String {
        JarvisLogger.log(context, "execute:${action.type}", sourceText)
        return when (action.type) {
            "calendar_event" -> openCalendarInsert(context, action, sourceText)
            "alarm" -> openAlarmInsert(context, sourceText)
            "reminder" -> openCalendarInsert(context, action.copy(title = "Reminder: ${action.title}"), sourceText)
            "voice_command" -> executeVoiceCommand(context, action, sourceText)
            else -> "Ich habe die Information als Notiz erkannt: ${action.title}"
        }
    }

    private fun executeVoiceCommand(context: Context, action: JarvisAction, sourceText: String): String {
        val command = action.title.lowercase(Locale.ROOT)
        return when {
            listOf("wecker", "alarm").any { it in command } -> openAlarmInsert(context, sourceText)
            listOf("kalender", "termin", "meeting", "geburtstag", "treffen").any { it in command } -> openCalendarInsert(context, action.copy(type = "calendar_event"), sourceText)
            else -> "Ich habe den Befehl erkannt, brauche dafür aber noch eine passende installierte App: ${action.title}"
        }
    }

    private fun openCalendarInsert(context: Context, action: JarvisAction, sourceText: String): String {
        val start = parseDateTime(sourceText)
        val end = start.plusHours(1)
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, action.title.ifBlank { "Jarvis Termin" })
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            putExtra(CalendarContract.Events.DESCRIPTION, "Erstellt durch Jarvis aus: $sourceText")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startInstalledApp(context, intent, "Kalender-App geöffnet, damit du den Termin speichern kannst.", "Keine Kalender-App gefunden.")
    }

    private fun openAlarmInsert(context: Context, sourceText: String): String {
        val time = parseTime(sourceText) ?: LocalTime.now().plusHours(1).withSecond(0).withNano(0)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, time.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, time.minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvis Wecker")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startInstalledApp(context, intent, "Wecker-App geöffnet, damit der Wecker gesetzt wird.", "Keine Wecker-App gefunden.")
    }

    private fun startInstalledApp(context: Context, intent: Intent, ok: String, missing: String): String {
        val handler = intent.resolveActivity(context.packageManager)
        return if (handler != null) {
            context.startActivity(intent)
            JarvisLogger.log(context, "installed_app:${handler.packageName}", intent.action.orEmpty())
            ok
        } else {
            JarvisLogger.log(context, "missing_app", intent.action.orEmpty())
            missing
        }
    }

    private fun parseDateTime(text: String): LocalDateTime {
        val lower = text.lowercase(Locale.ROOT)
        val date = when {
            "übermorgen" in lower -> LocalDate.now().plusDays(2)
            "morgen" in lower -> LocalDate.now().plusDays(1)
            else -> LocalDate.now()
        }
        return LocalDateTime.of(date, parseTime(text) ?: LocalTime.of(9, 0))
    }

    private fun parseTime(text: String): LocalTime? {
        val match = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(?:uhr)?\b""", RegexOption.IGNORE_CASE).find(text) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
        return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
    }
}
