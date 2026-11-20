package com.whoisthis.app

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
internal fun ScanScreen(
    model: RecognitionViewModel, state: AppState, landscape: Boolean, fullscreen: Boolean,
    paused: Boolean, front: Boolean, permission: Boolean, permissionRequested: Boolean,
    onPermission: () -> Unit, onSettings: () -> Unit, onPause: () -> Unit, onFlip: () -> Unit,
    onFullscreen: () -> Unit, onDisplay: () -> Unit, onRegister: () -> Unit, onNavigate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(paused) {
        while (!paused) { now = SystemClock.elapsedRealtime(); delay(250) }
    }
    val fresh = permission && !paused && state.frame.processingMs > 0 && now - state.frame.completedAt < 1_500
    val camera: @Composable (Modifier) -> Unit = { cameraModifier ->
        if (permission) CameraSurface(model, state, !paused, front, onFlip, onDisplay, onFullscreen, fullscreen, cameraModifier)
        else Column(cameraModifier.background(Palette.Surface, RoundedCornerShape(20.dp)).padding(24.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Glyph(Symbol.Camera, Palette.Accent, Modifier.size(36.dp))
            Text("Open your viewfinder", Modifier.padding(top = 16.dp), fontSize = 20.sp)
            Text("Allow camera access for on-device detection.", Modifier.padding(vertical = 12.dp), color = Palette.Muted, fontSize = 13.sp)
            Button(onClick = onPermission) { Text("Allow camera") }
            if (permissionRequested) TextButton(onClick = onSettings) { Text("Open app settings") }
        }
    }
    if (fullscreen) {
        camera(modifier.fillMaxSize())
    } else if (landscape) {
        Row(modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            camera(Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.width(230.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                ScanTitle(state, model::cancelEnrollment, compact = true)
                ScanMetrics(state, fresh, stacked = true)
                SceneSummary(state, fresh)
                ScanActions(state, paused, permission, onRegister, onPause)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onNavigate(1) }, enabled = state.enrollmentName == null) { Text("People") }
                    TextButton(onClick = { onNavigate(2) }, enabled = state.enrollmentName == null) { Text("Controls") }
                }
            }
        }
    } else {
        Column(modifier.fillMaxWidth()) {
            ScanTitle(state, model::cancelEnrollment, compact = false)
            Spacer(Modifier.height(14.dp))
            camera(Modifier.fillMaxWidth().weight(1f))
            ScanMetrics(state, fresh, stacked = false)
            SceneSummary(state, fresh)
            ScanActions(state, paused, permission, onRegister, onPause)
            Text(if (state.enrollmentName != null) "Keep the selected appearance while following the prompts."
                else "On screen controls choose what the camera detects and displays.",
                Modifier.padding(top = 9.dp, bottom = 6.dp), color = Palette.Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ScanTitle(state: AppState, onCancel: () -> Unit, compact: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (state.enrollmentName != null) "Capture appearance" else "AI detection",
                fontSize = if (compact) 21.sp else 27.sp, fontWeight = FontWeight.Medium)
            Text(if (state.enrollmentName != null) "${state.enrollmentName} / ${state.enrollmentLabel}"
                else "Know the people. See the context.", Modifier.padding(top = 5.dp), color = Palette.Muted,
                fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (state.enrollmentName != null) TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
private fun ScanMetrics(state: AppState, fresh: Boolean, stacked: Boolean) {
    if (state.enrollmentName != null) {
        Text("${state.enrollmentCount} of 6 samples", Modifier.padding(vertical = 14.dp), color = Palette.Accent, fontSize = 14.sp)
        return
    }
    val values = buildList {
        if (state.display.countPeople) add("People visible" to if (fresh) "${state.frame.peopleCount}${if (state.frame.objectsAtCapacity) "+" else ""}" else "—")
        if (state.display.recognizeFaces && state.display.showRecognizedCount) add("Recognized faces" to
            if (fresh) state.frame.faces.count { it.match != null }.toString() else "—")
        if (state.display.identifyObjects) add("Objects" to if (fresh) state.frame.objects.size.toString() else "—")
        add("People saved" to state.profiles.size.toString())
    }
    if (stacked) Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        values.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Palette.Muted, fontSize = 12.sp)
                Text(value, color = Palette.Accent, fontSize = 23.sp, fontWeight = FontWeight.Medium)
            }
        }
    } else Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        values.forEach { (label, value) ->
            Column {
                Text(value, fontSize = 21.sp, fontWeight = FontWeight.Medium)
                Text(label, Modifier.padding(top = 3.dp), color = Palette.Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SceneSummary(state: AppState, fresh: Boolean) {
    if (state.enrollmentName != null) return
    if (!state.display.recognizeFaces && !state.display.needsObjectDetector) {
        Text("Choose a detector in On screen.", Modifier.padding(bottom = 10.dp), color = Palette.Warning, fontSize = 12.sp)
    }
    if (fresh && state.display.identifyObjects) {
        val summary = state.frame.objects.groupingBy { it.label }.eachCount().entries
            .joinToString(", ") { "${it.value} ${it.key.lowercase()}" }
        if (summary.isNotBlank()) Text(summary, Modifier.padding(bottom = 8.dp), color = Palette.Muted,
            fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ScanActions(state: AppState, paused: Boolean, permission: Boolean, onRegister: () -> Unit, onPause: () -> Unit) {
    if (state.enrollmentName != null) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onRegister, enabled = permission && state.enrollmentName == null,
            modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(11.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
            Glyph(Symbol.Add, Palette.Ink, Modifier.size(18.dp))
            Text("Register face", Modifier.padding(start = 6.dp), fontSize = 12.sp)
        }
        OutlinedButton(onClick = onPause, enabled = state.enrollmentName == null, shape = RoundedCornerShape(11.dp),
            modifier = Modifier.height(48.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
            Text(if (paused) "Resume" else "Pause", fontSize = 12.sp)
        }
    }
}
