package com.jarvis.assistant.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("jarvis_settings")

data class Settings(val userName: String = "Boss", val modelMode: String = "local custom model", val observedApps: Set<String> = emptySet(), val controlledApps: Set<String> = emptySet(), val defaultAppPolicy: String = "ask_before_action", val openAiKey: String = "", val ollamaModel: String = "tinyllama")

class JarvisSettings(private val context: Context) {
    private val key = stringPreferencesKey("settings_blob")
    val flow: Flow<Settings> = context.store.data.map { parse(it[key].orEmpty()) }
    suspend fun save(s: Settings) { context.store.edit { it[key] = serialize(s) }; JarvisLogger.log(context, "settings", serialize(s)) }
    fun installedApps(): List<Pair<String,String>> = context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA).filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }.map { it.packageName to (context.packageManager.getApplicationLabel(it).toString()) }.sortedBy { it.second.lowercase() }
    companion object {
        private fun serialize(s: Settings) = listOf(s.userName,s.modelMode,s.observedApps.joinToString(","),s.controlledApps.joinToString(","),s.defaultAppPolicy,s.openAiKey,s.ollamaModel).joinToString("|") { it.replace("|", " ") }
        private fun parse(v: String): Settings { val p = v.split("|"); return if (p.size < 7) Settings() else Settings(p[0],p[1],p[2].split(',').filter{it.isNotBlank()}.toSet(),p[3].split(',').filter{it.isNotBlank()}.toSet(),p[4],p[5],p[6]) }
    }
}
