package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jarvis.assistant.data.JarvisLogger
import com.jarvis.assistant.data.JarvisSettings
import com.jarvis.assistant.data.Settings as JarvisPrefs
import com.jarvis.assistant.services.JarvisVoiceService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { JarvisLogger.log(this, "permissions", it.toString()) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO))
        ContextCompat.startForegroundService(this, Intent(this, JarvisVoiceService::class.java))
        setContent { JarvisApp(JarvisSettings(this)) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }
    }
}

@Composable fun JarvisApp(repo: JarvisSettings, openAccessibility: () -> Unit) {
    var screen by remember { mutableStateOf("home") }
    val settings by repo.flow.collectAsState(initial = JarvisPrefs())
    MaterialTheme { Surface(Modifier.fillMaxSize()) { when (screen) {
        "chat" -> ChatScreen { screen = "home" }
        "settings" -> SettingsScreen(repo, settings, openAccessibility) { screen = "home" }
        else -> HomeScreen(settings, { screen = "chat" }, { screen = "settings" }) { screen = "detail:$it" }
    } } }
}

@Composable fun HomeScreen(settings: JarvisPrefs, chat: () -> Unit, settingsClick: () -> Unit, detail: (String) -> Unit) {
    Column(Modifier.padding(16.dp)) { Row { Button(chat) { Text("💬") }; Spacer(Modifier.weight(1f)); Button(settingsClick) { Text("Einstellungen") } }
        Text("Jarvis Dashboard für ${settings.userName}", style = MaterialTheme.typography.headlineSmall)
        listOf("Erkannte Infos", "Reminder", "Tasker-Intents", "Modell", "actions.log").forEach { Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { detail(it) }) { Column(Modifier.padding(16.dp)) { Text(it, style = MaterialTheme.typography.titleMedium); Text("Tippen für Details") } } }
    }
}

@Composable fun ChatScreen(back: () -> Unit) { var text by remember { mutableStateOf("") }; val history = remember { mutableStateListOf<String>() }; Column(Modifier.padding(16.dp)) { Button(back){Text("Zurück")}; LazyColumn(Modifier.weight(1f)){ items(history){ Text(it) } }; OutlinedTextField(text,{text=it}, label={Text("Nachricht")}); Button({ if(text.isNotBlank()){ history += "Du: $text"; history += "Jarvis: Verstanden."; text="" } }){Text("Senden")} } }

@Composable fun SettingsScreen(repo: JarvisSettings, s: JarvisPrefs, openAccessibility: () -> Unit, back: () -> Unit) { val scope = rememberCoroutineScope(); var name by remember(s.userName){ mutableStateOf(s.userName) }; var mode by remember(s.modelMode){ mutableStateOf(s.modelMode) }; Column(Modifier.padding(16.dp)) { Button(back){Text("Zurück")}; Text("Einstellungen", style=MaterialTheme.typography.headlineSmall); OutlinedTextField(name,{name=it}, label={Text("Name")}); Text("Modellmodus"); listOf("local ollama model","openai-api","local custom model").forEach { Row { RadioButton(mode==it,{mode=it}); Text(it) } }; when(mode){ "openai-api" -> Text("API-Key wird lokal gespeichert."); "local ollama model" -> Text("Modelle: tinyllama, phi3:mini, qwen2.5:0.5b"); else -> Text("Optionen: trainiere neu, Backup & Training fortsetzen, load backup, retrain single behavior") }; Button(openAccessibility){Text("Screen Observer in Android aktivieren")}; Button({ scope.launch { repo.save(s.copy(userName=name, modelMode=mode)) } }){Text("Speichern")} } }
