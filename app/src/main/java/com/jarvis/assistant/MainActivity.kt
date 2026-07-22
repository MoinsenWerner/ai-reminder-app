package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jarvis.assistant.actions.JarvisActionExecutor
import com.jarvis.assistant.data.JarvisLogger
import com.jarvis.assistant.data.JarvisSettings
import com.jarvis.assistant.data.Settings as JarvisPrefs
import com.jarvis.assistant.model.JarvisModel
import com.jarvis.assistant.services.JarvisOverlayService
import com.jarvis.assistant.services.JarvisVoiceService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { JarvisLogger.log(this, "permissions", it.toString()) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        ContextCompat.startForegroundService(this, Intent(this, JarvisVoiceService::class.java))
        if (!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
        startService(Intent(this, JarvisOverlayService::class.java))
        JarvisLogger.startPeriodicExternalFlush(this)
        setContent { JarvisApp(JarvisSettings(this)) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }
    }
}

@Composable fun JarvisApp(repo: JarvisSettings, openAccessibility: () -> Unit) {
    var screen by remember { mutableStateOf("home") }
    val settings by repo.flow.collectAsState(initial = JarvisPrefs())
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                when (screen) {
                    "chat" -> ChatScreen { screen = "home" }
                    "settings" -> JarvisSettingsScreen(repo, settings, openAccessibility) { screen = "home" }
                    else -> HomeScreen(settings, { screen = "chat" }, { screen = "settings" }, repo.isAccessibilityServiceEnabled()) { screen = "detail:$it" }
                
                ActionPopup(Modifier.align(Alignment.TopStart))
            }
        }
    }
}


@Composable fun ActionPopup(modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(JarvisLogger.recentSummary()) }
    LaunchedEffect(Unit) {
        while (true) {
            text = JarvisLogger.recentSummary()
            delay(1_000L)
        }
    }
    Surface(
        modifier = modifier.padding(4.dp).widthIn(max = 320.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Text(
            text = text,
            modifier = Modifier.background(Color.Transparent).padding(6.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable fun HomeScreen(settings: JarvisPrefs, chat: () -> Unit, settingsClick: () -> Unit, screenObserverActive: Boolean, detail: (String) -> Unit) {
    Column(Modifier.padding(16.dp)) { Row { Button(chat) { Text("💬") }; Spacer(Modifier.weight(1f)); Button(settingsClick) { Text("Einstellungen") } }
        AssistChip(onClick = settingsClick, label = { Text(if (screenObserverActive) "🟢 Bildschirm-Mitlesen aktiv" else "⚪ Bildschirm-Mitlesen inaktiv") })
        Text("Jarvis Dashboard für ${settings.userName}", style = MaterialTheme.typography.headlineSmall)
        listOf("Erkannte Infos", "Reminder", "Tasker-Intents", "Modell", "actions.log").forEach { Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { detail(it) }) { Column(Modifier.padding(16.dp)) { Text(it, style = MaterialTheme.typography.titleMedium); Text("Tippen für Details") } } }
    }
}

@Composable fun ChatScreen(back: () -> Unit) {
    val context = LocalContext.current
    val model = remember { JarvisModel() }
    var text by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<String>() }
    Column(Modifier.padding(16.dp)) {
        Button(back){Text("Zurück")}
        LazyColumn(Modifier.weight(1f)){ items(history){ Text(it) } }
        OutlinedTextField(text,{text=it}, label={Text("Nachricht oder direkte Aktion")})
        Button({
            if(text.isNotBlank()){
                val input = text
                val action = model.infer(input)
                history += "Du: $input"
                history += "Jarvis: ${JarvisActionExecutor.execute(context, action, input)}"
                text=""
            }
        }){Text("Ausführen")}
    }
}

@Composable fun JarvisSettingsScreen(repo: JarvisSettings, s: JarvisPrefs, openAccessibility: () -> Unit, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    var draft by remember(s) { mutableStateOf(s) }
    var commandText by remember { mutableStateOf("") }
    var behaviorText by remember { mutableStateOf(s.retrainedBehavior) }
    val apps = remember { repo.installedApps() }
    fun persist(next: JarvisPrefs) { draft = next; scope.launch { repo.save(next) } }
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Button(back){Text("Zurück")}
        Text("Einstellungen", style=MaterialTheme.typography.headlineSmall)
        OutlinedTextField(draft.userName, { draft = draft.copy(userName = it) }, label={Text("Name")})
        Button({ persist(draft) }) { Text("Name speichern") }

        Text("App-Berechtigungen")
        Text("Beobachten | Interagieren | App")
        LazyColumn(Modifier.height(320.dp)) {
            items(apps) { (pkg, label) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Checkbox(pkg in draft.observedApps, { checked ->
                        val set = if (checked) draft.observedApps + pkg else draft.observedApps - pkg
                        persist(draft.copy(observedApps = set))
                    })
                    Checkbox(pkg in draft.controlledApps, { checked ->
                        val set = if (checked) draft.controlledApps + pkg else draft.controlledApps - pkg
                        persist(draft.copy(controlledApps = set))
                    })
                    Text("$label ($pkg)", Modifier.weight(1f))
                }
            }
        }
        Button(openAccessibility){Text("Screen Observer in Android aktivieren")}
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


