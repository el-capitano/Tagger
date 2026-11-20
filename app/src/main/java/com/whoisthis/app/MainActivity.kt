package com.whoisthis.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { TaggerTheme { RecognitionApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionApp(model: RecognitionViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var front by rememberSaveable { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var registering by rememberSaveable { mutableStateOf(false) }
    var registrationPersonId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingPersonId by rememberSaveable { mutableStateOf<String?>(null) }
    var displaySheet by rememberSaveable { mutableStateOf(false) }
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,
        Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var requestedPermission by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission = it
        requestedPermission = true
    }
    val snackbar = remember { SnackbarHostState() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permission = ContextCompat.checkSelfPermission(context,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (event == Lifecycle.Event.ON_STOP) model.resetTracking()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(fullscreen) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (fullscreen) controller?.hide(WindowInsetsCompat.Type.systemBars()) else controller?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    BackHandler(fullscreen) { fullscreen = false }
    LaunchedEffect(state.loading) {
        if (!state.loading && !state.fatal && !permission && !requestedPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); model.clearNotice() } }
    LaunchedEffect(state.error) { if (!state.fatal) state.error?.let { snackbar.showSnackbar(it); model.clearError() } }
    fun startRegistration(personId: String?) {
        registrationPersonId = personId
        registering = true
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        Scaffold(containerColor = Palette.Background, snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!fullscreen && !(landscape && tab == 0)) {
                    NavigationBar(containerColor = Palette.Background, tonalElevation = 0.dp) {
                        listOf("Scan" to Symbol.Scan, "People" to Symbol.People, "Controls" to Symbol.Controls)
                            .forEachIndexed { index, (label, symbol) ->
                                NavigationBarItem(selected = tab == index, onClick = { tab = index },
                                    enabled = state.enrollmentName == null,
                                    icon = { Glyph(symbol, if (tab == index) Palette.Accent else Palette.Muted) },
                                    label = { Text(label, fontSize = 12.sp) }, colors = NavigationBarItemDefaults.colors(
                                        selectedTextColor = Palette.Accent, indicatorColor = Palette.Raised, unselectedTextColor = Palette.Muted))
                            }
                    }
                }
            }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = if (fullscreen) 0.dp else 18.dp)) {
                if (!fullscreen && !(landscape && tab == 0)) AppHeader()
                when {
                    state.loading -> CenterMessage(Symbol.Scan, "Preparing detection", "Loading the on-device models…", Modifier.weight(1f))
                    state.fatal -> CenterMessage(Symbol.Shield, "Detection unavailable", state.error.orEmpty(), Modifier.weight(1f))
                    tab == 0 -> ScanScreen(model, state, landscape, fullscreen, paused, front, permission,
                        requestedPermission, onPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        onSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            "package:${context.packageName}".toUri())) },
                        onPause = { paused = !paused }, onFlip = { front = !front },
                        onFullscreen = { fullscreen = !fullscreen }, onDisplay = { displaySheet = true },
                        onRegister = { startRegistration(null) }, onNavigate = { tab = it; fullscreen = false },
                        modifier = Modifier.weight(1f))
                    tab == 1 -> PeopleScreen(state.profiles, onRegister = { startRegistration(it) },
                        onEdit = { editingPersonId = it.id }, onDelete = model::delete,
                        onDeleteAppearance = model::deleteAppearance, modifier = Modifier.weight(1f))
                    else -> ControlsScreen(state, model::setThreshold, model::setDisplay, Modifier.weight(1f))
                }
            }
        }
    }
    if (registering) RegistrationDialog(state.profiles.find { it.id == registrationPersonId },
        onDismiss = { registering = false }, onStart = { capture ->
            registering = false
            tab = 0
            paused = false
            model.register(capture.name, capture.detail, capture.notes, capture.appearanceLabel,
                capture.personId, capture.gender, capture.ageGroup)
        })
    state.profiles.find { it.id == editingPersonId }?.let { person ->
        EditPersonDialog(person, onDismiss = { editingPersonId = null }, onSave = {
            model.updatePerson(it)
            editingPersonId = null
        })
    }
    if (displaySheet) ModalBottomSheet(onDismissRequest = { displaySheet = false }, containerColor = Palette.Surface) {
        DisplayControls(state.display, model::setDisplay, Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 22.dp))
    }
}

@Composable
private fun AppHeader() {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(Symbol.Scan, Palette.Accent, Modifier.size(28.dp))
        Text(stringResource(R.string.app_name), Modifier.padding(start = 10.dp).weight(1f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Surface(shape = RoundedCornerShape(50), color = Palette.Raised) {
            Text("On device", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Palette.Muted, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun CenterMessage(symbol: Symbol, title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Glyph(symbol, Palette.Accent, Modifier.size(38.dp))
        Text(title, Modifier.padding(top = 18.dp), fontSize = 21.sp, fontWeight = FontWeight.Medium)
        Text(body, Modifier.padding(top = 12.dp), color = Palette.Muted, fontSize = 14.sp)
    }
}
