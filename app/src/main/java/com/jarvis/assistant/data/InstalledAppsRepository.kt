package com.jarvis.assistant.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class InstalledApp(
    val packageName: String,
    val label: String,
    val observeScreen: Boolean,
    val allowInteraction: Boolean
)

class InstalledAppsRepository(private val context: Context) {
    val cacheFile = File(Environment.getExternalStorageDirectory(), "Jarvis/cache/installed_apps.json")

    suspend fun loadCached(): List<InstalledApp> = withContext(Dispatchers.IO) {
        runCatching { decode(cacheFile.readText()) }.getOrDefault(emptyList())
    }

    suspend fun refresh(current: List<InstalledApp>): List<InstalledApp> = withContext(Dispatchers.IO) {
        val choices = current.associateBy { it.packageName }
        val refreshed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        }
            .map { info ->
                val old = choices[info.packageName]
                InstalledApp(
                    packageName = info.packageName,
                    label = context.packageManager.getApplicationLabel(info).toString(),
                    observeScreen = old?.observeScreen ?: false,
                    allowInteraction = old?.allowInteraction ?: false
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        write(refreshed)
        refreshed
    }

    suspend fun write(apps: List<InstalledApp>) = withContext(Dispatchers.IO) {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(encode(apps))
        JarvisLogger.log(context, "installed_apps_cache", "saved=${apps.size}; path=${cacheFile.path}")
    }

    companion object {
        internal fun encode(apps: List<InstalledApp>): String = JSONObject().apply {
            put("updatedAt", System.currentTimeMillis())
            put("apps", JSONArray().apply { apps.forEach { app -> put(JSONObject().apply {
                put("packageName", app.packageName)
                put("label", app.label)
                put("observeScreen", app.observeScreen)
                put("allowInteraction", app.allowInteraction)
            }) } })
        }.toString(2)

        internal fun decode(value: String): List<InstalledApp> {
            val apps = JSONObject(value).getJSONArray("apps")
            return (0 until apps.length()).map { index -> apps.getJSONObject(index).let {
                InstalledApp(it.getString("packageName"), it.getString("label"), it.optBoolean("observeScreen"), it.optBoolean("allowInteraction"))
            } }
        }
    }
}
