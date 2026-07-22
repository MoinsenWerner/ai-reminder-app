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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import java.time.Instant

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

@Composable fun SettingsScreen(repo: JarvisSettings, s: JarvisPrefs, openAccessibility: () -> Unit, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    var draft by remember(s) { mutableStateOf(s) }
    var commandText by remember { mutableStateOf("") }
    var behaviorText by remember { mutableStateOf(s.retrainedBehavior) }
    val apps = remember { repo.installedApps().take(50) }
    fun persist(next: JarvisPrefs) { draft = next; scope.launch { repo.save(next) } }
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Button(back){Text("Zurück")}
        Text("Einstellungen", style=MaterialTheme.typography.headlineSmall)
        OutlinedTextField(draft.userName, { draft = draft.copy(userName = it) }, label={Text("Name")})
        Button({ persist(draft) }) { Text("Name speichern") }

        Text("Bildschirm beobachten")
        apps.forEach { (pkg, label) -> Row(Modifier.fillMaxWidth().clickable {
            val set = if (pkg in draft.observedApps) draft.observedApps - pkg else draft.observedApps + pkg
            persist(draft.copy(observedApps = set))
        }) { Checkbox(pkg in draft.observedApps, null); Text("$label ($pkg)") } }
        Button(openAccessibility){Text("Screen Observer in Android aktivieren")}

        Text("Apps steuern")
        apps.forEach { (pkg, label) -> Row(Modifier.fillMaxWidth().clickable {
            val set = if (pkg in draft.controlledApps) draft.controlledApps - pkg else draft.controlledApps + pkg
            persist(draft.copy(controlledApps = set))
        }) { Checkbox(pkg in draft.controlledApps, null); Text("$label steuern") } }
        Text("Standardberechtigung für neue Apps")
        listOf("ask_before_action", "read_only", "allow_safe_actions").forEach { policy ->
            FilterChip(selected = draft.defaultAppPolicy == policy, onClick = { persist(draft.copy(defaultAppPolicy = policy)) }, label = { Text(policy) })
        }

        Text("Stimmprofil")
        Button({ persist(draft.copy(voiceProfileTrained = true)) }) { Text(if (draft.voiceProfileTrained) "Stimme neu trainieren" else "Stimme trainieren") }
        Text(if (draft.voiceProfileTrained) "Stimmprofil gespeichert" else "Noch kein Stimmprofil")
        OutlinedTextField(commandText, { commandText = it }, label={Text("Optionaler Voice-Command")})
        Button({ if (commandText.isNotBlank() && draft.trainedVoiceCommands.size < 10) { persist(draft.copy(trainedVoiceCommands = draft.trainedVoiceCommands + commandText.trim())); commandText = "" } }) { Text("Voice-Command speichern") }
        draft.trainedVoiceCommands.forEach { cmd -> Button({ persist(draft.copy(trainedVoiceCommands = draft.trainedVoiceCommands - cmd)) }) { Text("Löschen: $cmd") } }

        Text("Modellmodus")
        listOf("local ollama model","openai-api","local custom model").forEach { Row(Modifier.clickable { persist(draft.copy(modelMode = it)) }) { RadioButton(draft.modelMode==it,{ persist(draft.copy(modelMode=it)) }); Text(it) } }
        when(draft.modelMode){
            "openai-api" -> OutlinedTextField(draft.openAiKey, { draft = draft.copy(openAiKey = it) }, label={Text("OpenAI API-Key")})
            "local ollama model" -> listOf("tinyllama", "phi3:mini", "qwen2.5:0.5b").forEach { model -> Button({ persist(draft.copy(ollamaModel = model)) }) { Text(if (draft.ollamaModel == model) "✓ $model" else model) } }
            else -> {
                Button({ persist(draft.copy(modelBackups = emptyList(), retrainedBehavior = "")) }) { Text("Trainiere das Modell neu") }
                Button({ persist(draft.copy(modelBackups = draft.modelBackups + "backup-${Instant.now().epochSecond}")) }) { Text("Backup & Training fortsetzen/verfeinern") }
                draft.modelBackups.forEach { backup -> Button({ persist(draft.copy(retrainedBehavior = "loaded:$backup")) }) { Text("Load backup: $backup") } }
                OutlinedTextField(behaviorText, { behaviorText = it }, label={Text("Retrain single behavior")})
                Button({ persist(draft.copy(retrainedBehavior = behaviorText.trim())) }) { Text("Verhalten neu trainieren") }
            }
        }
        Button({ persist(draft) }){Text("Alle Einstellungen speichern")}
    }
}
