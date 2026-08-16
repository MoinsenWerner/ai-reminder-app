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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jarvis.assistant.data.*
import com.jarvis.assistant.data.Settings as JarvisPrefs
import com.jarvis.assistant.services.JarvisVoiceService
import com.jarvis.assistant.services.LogOverlayService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        JarvisLogger.log(this, "permissions", it.toString())
    }
    private val overlayLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(this)) startService(Intent(this, LogOverlayService::class.java))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions += listOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) permissions += Manifest.permission.WRITE_EXTERNAL_STORAGE
        permissionLauncher.launch(permissions.toTypedArray())
        ContextCompat.startForegroundService(this, Intent(this, JarvisVoiceService::class.java))
        setContent {
            JarvisApp(
                repo = JarvisSettings(this),
                appsRepo = InstalledAppsRepository(this),
                openAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                requestFiles = { startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))) },
                setOverlay = ::setOverlay
            )
        }
    }

    private fun setOverlay(enabled: Boolean) {
        if (!enabled) return stopService(Intent(this, LogOverlayService::class.java)).let { }
        if (Settings.canDrawOverlays(this)) startService(Intent(this, LogOverlayService::class.java))
        else overlayLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }
}

@Composable
fun JarvisApp(
    repo: JarvisSettings,
    appsRepo: InstalledAppsRepository,
    openAccessibility: () -> Unit,
    requestFiles: () -> Unit,
    setOverlay: (Boolean) -> Unit
) {
    var screen by remember { mutableStateOf("home") }
    val settings by repo.flow.collectAsState(initial = JarvisPrefs())
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            when (screen) {
                "chat" -> ChatScreen { screen = "home" }
                "settings" -> SettingsScreen(repo, settings, openAccessibility, requestFiles, setOverlay, { screen = "apps" }, { screen = "hfCatalog" }) { screen = "home" }
                "apps" -> InstalledAppsScreen(appsRepo, repo, settings, requestFiles) { screen = "settings" }
                "hfCatalog" -> HuggingFaceCatalogScreen(requestFiles) { screen = "settings" }
                else -> HomeScreen(settings, { screen = "chat" }, { screen = "settings" }) { screen = "detail:$it" }
            }
        }
    }
}

@Composable
fun HomeScreen(settings: JarvisPrefs, chat: () -> Unit, settingsClick: () -> Unit, detail: (String) -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Row { Button(chat) { Text("💬") }; Spacer(Modifier.weight(1f)); Button(settingsClick) { Text("Einstellungen") } }
        Text("Jarvis Dashboard für ${settings.userName}", style = MaterialTheme.typography.headlineSmall)
        listOf("Erkannte Infos", "Reminder", "Tasker-Intents", "Modell", "actions.log").forEach {
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { detail(it) }) {
                Column(Modifier.padding(16.dp)) { Text(it, style = MaterialTheme.typography.titleMedium); Text("Tippen für Details") }
            }
        }
    }
}

@Composable
fun ChatScreen(back: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<String>() }
    Column(Modifier.padding(16.dp)) {
        Button(back) { Text("Zurück") }
        LazyColumn(Modifier.weight(1f)) { items(history) { Text(it) } }
        OutlinedTextField(text, { text = it }, label = { Text("Nachricht") })
        Button({ if (text.isNotBlank()) { history += "Du: $text"; history += "Jarvis: Verstanden."; text = "" } }) { Text("Senden") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repo: JarvisSettings,
    s: JarvisPrefs,
    openAccessibility: () -> Unit,
    requestFiles: () -> Unit,
    setOverlay: (Boolean) -> Unit,
    openApps: () -> Unit,
    openCatalog: () -> Unit,
    back: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember(s.userName) { mutableStateOf(s.userName) }
    var useHf by remember(s.useHuggingFace) { mutableStateOf(s.useHuggingFace) }
    var overlay by remember(s.logOverlayEnabled) { mutableStateOf(s.logOverlayEnabled) }
    var selected by remember(s.huggingFaceModel) { mutableStateOf(s.huggingFaceModel) }
    var expanded by remember { mutableStateOf(false) }
    var passwordDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }
    var downloadStatus by remember { mutableStateOf("") }
    var downloaded by remember { mutableStateOf<List<HuggingFaceModel>>(emptyList()) }

    LaunchedEffect(useHf) {
        if (useHf && canManageJarvisFiles()) downloaded = HuggingFaceModels.downloaded()
    }

    fun persist(next: JarvisPrefs) { scope.launch { repo.save(next) } }
    fun selectPreset(model: HuggingFaceModel) {
        selected = model.id
        if (HuggingFaceModels.isDownloaded(model)) {
            downloadStatus = "Heruntergeladen · wird beim Speichern aktiviert"
            return
        }
        if (!canManageJarvisFiles()) { requestFiles(); return }
        scope.launch {
            downloadStatus = "Download ${model.title}: 0 %"
            runCatching { HuggingFaceModels.download(model) { downloadStatus = "Download ${model.title}: $it %" } }
                .onSuccess { directory ->
                    downloaded = HuggingFaceModels.downloaded()
                    downloadStatus = "Heruntergeladen: ${directory.path} · Einstellungen speichern, um es zu aktivieren"
                }
                .onFailure { downloadStatus = "Fehler: ${it.message}" }
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Button(back) { Text("Zurück") }; Text("Einstellungen", style = MaterialTheme.typography.headlineSmall) }
        item { OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth()) }
        item {
            Button(openApps, Modifier.fillMaxWidth()) { Text("Installierte Apps & Berechtigungen") }
            Text("Bildschirmbeobachtung und selbstständige Interaktion pro App konfigurieren.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Log-Overlay anzeigen", Modifier.weight(1f))
                Switch(overlay, { enabled -> overlay = enabled; setOverlay(enabled); persist(s.copy(userName = name, logOverlayEnabled = enabled)) })
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text("Lokales/Custom Modell")
                Switch(useHf, { enabled -> useHf = enabled }, Modifier.padding(horizontal = 12.dp))
                Text("Huggingface-Modell")
            }
        }
        if (useHf) item {
            ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                OutlinedTextField(
                    value = HuggingFaceModels.presets.firstOrNull { it.id == selected }?.title ?: selected.ifBlank { "Modell auswählen" },
                    onValueChange = {}, readOnly = true, label = { Text("Hugging-Face-Modell") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(expanded, { expanded = false }) {
                    (HuggingFaceModels.presets + downloaded).distinctBy { it.id }.forEach { model ->
                        DropdownMenuItem(
                            { Text("${model.title}${if (HuggingFaceModels.isDownloaded(model)) " ✓" else " · Download"}") },
                            { expanded = false; selectPreset(model) }
                        )
                    }
                    DropdownMenuItem({ Text("Custom Huggingface") }, { expanded = false; passwordDialog = true })
                }
            }
            if (downloadStatus.isNotBlank()) Text(downloadStatus, style = MaterialTheme.typography.bodySmall)
        }
        item { Button(openAccessibility, Modifier.fillMaxWidth()) { Text("Screen Observer in Android aktivieren") } }
        item { Button({
            val model = (HuggingFaceModels.presets + downloaded).firstOrNull { it.id == selected }
            if (useHf && (model == null || !HuggingFaceModels.isDownloaded(model))) {
                downloadStatus = "Bitte zuerst ein vollständig heruntergeladenes Modell auswählen."
            } else {
                persist(s.copy(
                    userName = name,
                    useHuggingFace = useHf,
                    huggingFaceModel = if (useHf) selected else "",
                    huggingFaceModelPath = if (useHf) HuggingFaceModels.directoryFor(checkNotNull(model)).path else "",
                    logOverlayEnabled = overlay
                ))
                downloadStatus = if (useHf) "Aktives Modell gespeichert: ${model?.title}" else "Lokales/Custom Modell aktiviert"
            }
        }, Modifier.fillMaxWidth()) { Text("Einstellungen speichern") } }
    }

    if (passwordDialog) AlertDialog(
        onDismissRequest = { passwordDialog = false },
        title = { Text("Custom Huggingface entsperren") },
        text = { Column { OutlinedTextField(password, { password = it; passwordError = false }, label = { Text("Passwort") }, visualTransformation = PasswordVisualTransformation()); if (passwordError) Text("Falsches Passwort", color = MaterialTheme.colorScheme.error) } },
        confirmButton = { Button({ if (password == "112358") { passwordDialog = false; openCatalog() } else passwordError = true }) { Text("Öffnen") } },
        dismissButton = { TextButton({ passwordDialog = false }) { Text("Abbrechen") } }
    )
}

@Composable
fun InstalledAppsScreen(appsRepo: InstalledAppsRepository, settingsRepo: JarvisSettings, settings: JarvisPrefs, requestFiles: () -> Unit, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var refreshing by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        apps = appsRepo.loadCached()
        if (!canManageJarvisFiles()) { refreshing = false; return@LaunchedEffect }
        runCatching { appsRepo.refresh(apps) }.onSuccess { apps = it }.onFailure { error = it.message }
        refreshing = false
    }

    fun update(app: InstalledApp) {
        apps = apps.map { if (it.packageName == app.packageName) app else it }
        scope.launch {
            appsRepo.write(apps)
            settingsRepo.save(settings.copy(
                observedApps = apps.filter(InstalledApp::observeScreen).map { it.packageName }.toSet(),
                controlledApps = apps.filter(InstalledApp::allowInteraction).map { it.packageName }.toSet()
            ))
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Button(back) { Text("Zurück") }; Spacer(Modifier.width(12.dp)); Text("Installierte Apps", style = MaterialTheme.typography.headlineSmall) }
        if (!canManageJarvisFiles()) {
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Column(Modifier.padding(12.dp)) { Text("Dateizugriff wird für /storage/emulated/0/Jarvis benötigt."); Button(requestFiles) { Text("Dateizugriff erlauben") } } }
        }
        Text("${apps.size} Apps aus Cache geladen${if (refreshing) " · Aktualisierung läuft …" else ""}")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("App", Modifier.weight(1f)); Text("Lesen"); Spacer(Modifier.width(14.dp)); Text("Schreiben") }
        LazyColumn {
            items(apps, key = { it.packageName }) { app ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(app.label); Text(app.packageName, style = MaterialTheme.typography.bodySmall) }
                    Checkbox(app.observeScreen, { update(app.copy(observeScreen = it)) })
                    Checkbox(app.allowInteraction, { update(app.copy(allowInteraction = it)) })
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun HuggingFaceCatalogScreen(requestFiles: () -> Unit, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf(HuggingFaceModels.presets) }
    var searching by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }

    fun download(model: HuggingFaceModel) {
        if (!canManageJarvisFiles()) { requestFiles(); return }
        scope.launch {
            status = "Download ${model.title}: 0 %"
            runCatching { HuggingFaceModels.download(model) { status = "Download ${model.title}: $it %" } }
                .onSuccess { file -> status = "Heruntergeladen: ${file.path}. In den Einstellungen auswählen und speichern." }
                .onFailure { status = "Fehler: ${it.message}" }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row { Button(back) { Text("Zurück") }; Spacer(Modifier.width(12.dp)); Text("Hugging Face", style = MaterialTheme.typography.headlineSmall) }
        OutlinedTextField(query, { query = it }, label = { Text("Modelle filtern") }, modifier = Modifier.fillMaxWidth())
        Button({ scope.launch { searching = true; runCatching { HuggingFaceModels.search() }.onSuccess { models = it }.onFailure { status = "Suche fehlgeschlagen: ${it.message}" }; searching = false } }, Modifier.fillMaxWidth()) { Text(if (searching) "Suche läuft …" else "Alle geeigneten Textmodelle laden") }
        if (!canManageJarvisFiles()) Button(requestFiles) { Text("Dateizugriff für Modelldownload erlauben") }
        Text(status, style = MaterialTheme.typography.bodySmall)
        LazyColumn { items(models.filter { it.id.contains(query, true) }, key = { it.id }) { model ->
            ListItem(headlineContent = { Text(model.title) }, supportingContent = { Text(model.id) }, trailingContent = { Button({ download(model) }) { Text("Download") } })
            HorizontalDivider()
        } }
    }
}

private fun canManageJarvisFiles(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
