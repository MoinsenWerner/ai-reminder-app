package com.jarvis.assistant.model

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.jarvis.assistant.data.JarvisLogger
import com.jarvis.assistant.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class AssistantResult(val answer: String, val engine: String, val executedAction: String? = null)

object AssistantEngine {
    const val HUGGING_FACE_SYSTEM_INSTRUCTION = """Du bist Jarvis, ein persönlicher Android-Assistent. Antworte auf Deutsch. Erkenne direkte Wünsche nach Weckern, Kalenderterminen und Erinnerungen. Antworte am Anfang exakt mit einer Aktionszeile im Format ACTION: alarm, ACTION: calendar, ACTION: reminder oder ACTION: none. Danach folgt eine kurze Bestätigung. Erfinde keine Ausführung; wähle eine Aktion nur, wenn der Nutzer sie verlangt."""

    suspend fun process(context: Context, input: String, settings: Settings): AssistantResult {
        val modelResult = if (settings.useHuggingFace) {
            require(settings.huggingFaceModel.isNotBlank()) { "Kein Hugging-Face-Modell ausgewählt" }
            val answer = HuggingFaceInference.generate(settings.huggingFaceModel, settings.huggingFaceToken, input)
            val type = Regex("ACTION:\\s*(alarm|calendar|reminder|none)", RegexOption.IGNORE_CASE)
                .find(answer)?.groupValues?.get(1)?.lowercase() ?: "none"
            JarvisAction(type, input, if (type == "none") .5 else .9) to answer
        } else {
            val action = JarvisModel().infer(input)
            action to localAnswer(action)
        }
        val execution = execute(context, modelResult.first, input)
        JarvisLogger.log(context, "assistant:${if (settings.useHuggingFace) settings.huggingFaceModel else "local-custom"}", "input=$input action=${modelResult.first.type} result=$execution")
        return AssistantResult(modelResult.second, if (settings.useHuggingFace) settings.huggingFaceModel else "local-custom", execution)
    }

    private fun localAnswer(action: JarvisAction): String = when (action.type) {
        "alarm" -> "Ich stelle den gewünschten Wecker."
        "calendar_event" -> "Ich trage den Termin in deinen Kalender ein."
        "reminder" -> "Ich habe die Erinnerung erkannt."
        "voice_command" -> "Ich führe den Sprachbefehl aus."
        else -> "Ich habe die Information als Notiz erfasst."
    }

    private fun execute(context: Context, action: JarvisAction, input: String): String? = when (action.type) {
        "alarm" -> setAlarm(context, input)
        "calendar", "calendar_event" -> createCalendarEvent(context, input)
        "reminder" -> setAlarm(context, input, "Jarvis-Erinnerung")
        else -> null
    }

    private fun setAlarm(context: Context, input: String, fallbackLabel: String = "Jarvis-Wecker"): String {
        val time = parseTime(input) ?: return "Wecker nicht gesetzt: keine Uhrzeit erkannt"
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, time.first)
            putExtra(AlarmClock.EXTRA_MINUTES, time.second)
            putExtra(AlarmClock.EXTRA_MESSAGE, input.take(120).ifBlank { fallbackLabel })
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            "Wecker auf %02d:%02d gesetzt".format(time.first, time.second)
        } else "Wecker nicht gesetzt: keine Wecker-App verfügbar"
    }

    private fun createCalendarEvent(context: Context, input: String): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return "Kalendereintrag nicht erstellt: Kalenderberechtigung fehlt"
        }
        val time = parseTime(input) ?: (12 to 0)
        val date = when {
            "übermorgen" in input.lowercase() -> LocalDate.now().plusDays(2)
            "morgen" in input.lowercase() -> LocalDate.now().plusDays(1)
            else -> LocalDate.now()
        }
        val start = LocalDateTime.of(date, java.time.LocalTime.of(time.first, time.second)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val calendarId = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.VISIBLE}=1",
            null,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC"
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }
            ?: return "Kalendereintrag nicht erstellt: kein beschreibbarer Kalender"
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, input.take(160))
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, start + 60 * 60 * 1000)
            put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        return if (uri != null) "Kalendereintrag erstellt" else "Kalendereintrag konnte nicht erstellt werden"
    }

    internal fun parseTime(input: String): Pair<Int, Int>? {
        val match = Regex("(?:um\\s*)?(\\d{1,2})(?:(?::|\\.)(\\d{2}))?\\s*(?:uhr)?", RegexOption.IGNORE_CASE).find(input) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        return (hour to minute).takeIf { hour in 0..23 && minute in 0..59 }
    }
}

private object HuggingFaceInference {
    suspend fun generate(model: String, token: String, input: String): String = withContext(Dispatchers.IO) {
        require(token.isNotBlank()) { "Für die Hugging-Face-Inference wird ein Access-Token benötigt" }
        val encoded = model.split('/').joinToString("/") { URLEncoder.encode(it, Charsets.UTF_8.name()) }
        val connection = URL("https://router.huggingface.co/hf-inference/models/$encoded").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $token")
        val prompt = "<|system|>\n${AssistantEngine.HUGGING_FACE_SYSTEM_INSTRUCTION}\n<|user|>\n$input\n<|assistant|>\n"
        val payload = JSONObject().put("inputs", prompt).put("parameters", JSONObject().put("max_new_tokens", 180).put("return_full_text", false))
        connection.outputStream.use { it.write(payload.toString().toByteArray()) }
        val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
            .bufferedReader().use { it.readText() }
        check(connection.responseCode in 200..299) { "Hugging Face HTTP ${connection.responseCode}: $body" }
        val response = JSONArray(body).optJSONObject(0)?.optString("generated_text").orEmpty()
        check(response.isNotBlank()) { "Hugging Face lieferte keine Antwort" }
        response
    }
}
