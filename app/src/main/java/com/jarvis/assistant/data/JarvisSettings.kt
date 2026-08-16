package com.jarvis.assistant.data

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings.Secure
import android.text.TextUtils
import com.jarvis.assistant.services.JarvisAccessibilityService
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.store by preferencesDataStore("jarvis_settings")

data class Settings(
    val userName: String = "Boss",
    val modelMode: String = "local custom model",
    val observedApps: Set<String> = emptySet(),
    val controlledApps: Set<String> = emptySet(),
    val defaultAppPolicy: String = "ask_before_action",
    val openAiKey: String = "",
    val ollamaModel: String = "tinyllama",
    val logOverlayEnabled: Boolean = false,
    val useHuggingFace: Boolean = false,
    val huggingFaceModel: String = "",
    val huggingFaceModelPath: String = ""
)

class JarvisSettings(private val context: Context) {
    private val key = stringPreferencesKey("settings_json")
    val flow: Flow<Settings> = context.store.data.map { parse(it[key]) }

    suspend fun save(settings: Settings) {
        val encoded = serialize(settings)
        context.store.edit { it[key] = encoded }
        JarvisLogger.log(context, "settings", encoded)
    }

    companion object {
        internal fun serialize(s: Settings): String = JSONObject().apply {
            put("userName", s.userName)
            put("modelMode", s.modelMode)
            put("observedApps", JSONArray(s.observedApps.toList()))
            put("controlledApps", JSONArray(s.controlledApps.toList()))
            put("defaultAppPolicy", s.defaultAppPolicy)
            put("openAiKey", s.openAiKey)
            put("ollamaModel", s.ollamaModel)
            put("logOverlayEnabled", s.logOverlayEnabled)
            put("useHuggingFace", s.useHuggingFace)
            put("huggingFaceModel", s.huggingFaceModel)
            put("huggingFaceModelPath", s.huggingFaceModelPath)
        }.toString()

        internal fun parse(value: String?): Settings {
            if (value.isNullOrBlank()) return Settings()
            return runCatching {
                val json = JSONObject(value)
                Settings(
                    userName = json.optString("userName", "Boss"),
                    modelMode = json.optString("modelMode", "local custom model"),
                    observedApps = json.optJSONArray("observedApps").toSet(),
                    controlledApps = json.optJSONArray("controlledApps").toSet(),
                    defaultAppPolicy = json.optString("defaultAppPolicy", "ask_before_action"),
                    openAiKey = json.optString("openAiKey"),
                    ollamaModel = json.optString("ollamaModel", "tinyllama"),
                    logOverlayEnabled = json.optBoolean("logOverlayEnabled"),
                    useHuggingFace = json.optBoolean("useHuggingFace"),
                    huggingFaceModel = json.optString("huggingFaceModel"),
                    huggingFaceModelPath = json.optString("huggingFaceModelPath")
                )
            }.getOrDefault(Settings())
        }

        private fun JSONArray?.toSet(): Set<String> = if (this == null) emptySet() else
            (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }.toSet()
    }
}
