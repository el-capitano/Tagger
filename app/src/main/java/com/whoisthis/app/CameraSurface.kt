package com.whoisthis.app

import android.graphics.RectF
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.CoordinateTransform
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CameraSurface(
    model: RecognitionViewModel,
    state: AppState,
    enabled: Boolean,
    front: Boolean,
    onSwitchCamera: () -> Unit,
    onDisplay: () -> Unit,
    onFullscreen: () -> Unit,
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    } }
    var dimensions by remember { mutableStateOf(IntSize.Zero) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var streaming by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var clock by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }

    LaunchedEffect(enabled) {
        while (enabled) {
            clock = android.os.SystemClock.elapsedRealtime()
            delay(250)
        }
    }
    DisposableEffect(previewView, lifecycle) {
        val observer = Observer<PreviewView.StreamState> { streaming = it == PreviewView.StreamState.STREAMING }
        previewView.previewStreamState.observe(lifecycle, observer)
        onDispose { previewView.previewStreamState.removeObserver(observer) }
    }
    DisposableEffect(enabled, front, dimensions, lifecycle, retry) {
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var boundPreview: Preview? = null
        var boundAnalysis: ImageAnalysis? = null
        cameraError = null
        model.resetTracking()
        if (enabled && dimensions.width > 0 && dimensions.height > 0) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (!disposed) {
                    try {
                        val cameraProvider = future.get()
                        provider = cameraProvider
                        val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                        check(cameraProvider.hasCamera(selector)) { "This camera is unavailable. Try the other camera." }
                        val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                        val preview = Preview.Builder().setTargetRotation(rotation).build().apply {
                            setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setTargetRotation(rotation)
                            .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                                ResolutionStrategy(android.util.Size(1280, 720),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build().apply { setAnalyzer(model.cameraExecutor, model) }
                        boundPreview = preview
                        boundAnalysis = analysis
                        val viewport = checkNotNull(previewView.viewPort) { "Camera view is not ready. Try again." }
                        val group = UseCaseGroup.Builder().setViewPort(viewport)
                            .addUseCase(preview).addUseCase(analysis).build()
                        cameraProvider.bindToLifecycle(lifecycle, selector, group)
                    } catch (error: Exception) {
                        cameraError = error.message ?: "Could not open the camera. Close other camera apps and retry."
                    }
                }
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose {
            disposed = true
            boundAnalysis?.clearAnalyzer()
            val useCases = listOfNotNull(boundPreview, boundAnalysis)
            if (useCases.isNotEmpty()) provider?.unbind(*useCases.toTypedArray())
        }
    }

    Box(modifier.clip(RoundedCornerShape(22.dp)).background(Palette.Camera).onSizeChanged { dimensions = it }) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        val fresh = enabled && streaming && clock - state.frame.completedAt < 1_500
        if (fresh && cameraError == null) FaceOverlay(state.frame, previewView, dimensions, state.display, state.enrollmentName != null)
        if (enabled && streaming && fresh && state.frame.faces.isEmpty() && state.frame.objects.isEmpty() && state.enrollmentName == null) {
            Text(if (!state.display.recognizeFaces && !state.display.needsObjectDetector) "Choose a detector in On screen"
                else "Point the camera at people or objects", modifier = Modifier.align(Alignment.Center)
                .padding(20.dp).background(Palette.Hud, RoundedCornerShape(8.dp)).padding(12.dp),
                color = Palette.Text, fontSize = 13.sp)
        }
        if (fresh && state.enrollmentName == null) {
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if (state.display.countPeople) CounterBadge("People visible", "${state.frame.peopleCount}${if (state.frame.objectsAtCapacity) "+" else ""}")
                else Spacer(Modifier.width(1.dp))
                if (state.display.recognizeFaces && state.display.showRecognizedCount) {
                    CounterBadge("Recognized faces", state.frame.faces.count { it.match != null }.toString())
                }
            }
            if (state.display.identifyObjects && state.display.showLabels && state.frame.scene.isNotEmpty()) {
                Text("Scene: ${state.frame.scene.joinToString(", ") { it.label }}",
                    Modifier.align(Alignment.BottomStart).padding(start = 16.dp,
                        bottom = if (state.display.countPeople || state.display.showRecognizedCount) 88.dp else 16.dp)
                        .background(Palette.Hud, RoundedCornerShape(8.dp)).padding(8.dp), color = Palette.Muted, fontSize = 11.sp)
            }
        }
        if (state.enrollmentName != null && enabled && streaming) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Palette.Hud).padding(18.dp)) {
                Text(state.frame.faces.firstOrNull()?.message ?: state.instruction,
                    color = Palette.Accent, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { state.enrollmentCount / 6f }, modifier = Modifier.fillMaxWidth(),
                    color = Palette.Accent, trackColor = Palette.Border)
                Text("${state.enrollmentCount} of 6 samples", Modifier.padding(top = 8.dp),
                    fontSize = 12.sp, color = Palette.Muted)
            }
        }
        if (!enabled || cameraError != null) {
            Column(Modifier.fillMaxSize().background(Palette.Background).padding(28.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Glyph(if (cameraError == null) Symbol.Pause else Symbol.Camera, Palette.Muted, Modifier.size(36.dp))
                Spacer(Modifier.height(16.dp))
                Text(cameraError ?: "Recognition paused", color = Palette.Text,
                    style = MaterialTheme.typography.bodyLarge)
                if (cameraError != null) TextButton(onClick = { retry++ }) { Text("Retry camera") }
                else Text("Resume when you’re ready", Modifier.padding(top = 8.dp), color = Palette.Muted, fontSize = 13.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Palette.Hud, shape = RoundedCornerShape(7.dp)) {
                Text(when {
                    !enabled -> "Camera paused"
                    cameraError != null -> "Camera unavailable"
                    streaming -> if (front) "Live front camera" else "Live rear camera"
                    else -> "Opening camera…"
                }, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = Palette.Accent, fontSize = 12.sp)
            }
            Surface(color = Palette.Hud, shape = RoundedCornerShape(7.dp)) {
                TextButton(onClick = onSwitchCamera, modifier = Modifier.heightIn(min = 48.dp)
                    .semantics { contentDescription = if (front) "Switch to rear camera" else "Switch to front camera" },
                    contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Glyph(Symbol.Flip, Palette.Text, Modifier.size(18.dp))
                    Text(if (front) "Use rear" else "Use front", Modifier.padding(start = 6.dp),
                        color = Palette.Text, fontSize = 12.sp)
                }
            }
        }
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 76.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Surface(color = Palette.Hud, shape = RoundedCornerShape(7.dp)) {
                TextButton(onClick = onDisplay, modifier = Modifier.heightIn(min = 48.dp)) {
                    Glyph(Symbol.Controls, Palette.Text, Modifier.size(16.dp))
                    Text("On screen", Modifier.padding(start = 6.dp), color = Palette.Text, fontSize = 11.sp)
                }
            }
            Surface(color = Palette.Hud, shape = RoundedCornerShape(7.dp)) {
                TextButton(onClick = onFullscreen, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (fullscreen) "Exit fullscreen" else "Fullscreen", color = Palette.Text, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun CounterBadge(label: String, value: String) {
    Column(Modifier.background(Palette.Hud, RoundedCornerShape(9.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(value, color = Palette.Accent, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        Text(label, color = Palette.Muted, fontSize = 10.sp)
    }
}

@Composable
@androidx.annotation.OptIn(markerClass = [androidx.camera.view.TransformExperimental::class])
private fun FaceOverlay(frame: ScanFrame, preview: PreviewView, dimensions: IntSize, display: DisplayOptions, enrolling: Boolean) {
    val density = LocalDensity.current
    val mapped = remember(frame, dimensions, display.recognizeFaces, enrolling) {
        val source = frame.transform
        val target = preview.outputTransform
        if (source == null || target == null) emptyList() else {
            val transform = CoordinateTransform(source, target)
            if (display.recognizeFaces || enrolling) frame.faces.map { face -> face to RectF(face.bounds).apply { transform.mapRect(this) } }
            else emptyList()
        }
    }
    val mappedObjects = remember(frame, dimensions, display.identifyObjects) {
        val source = frame.transform
        val target = preview.outputTransform
        if (!display.identifyObjects || source == null || target == null) emptyList() else {
            val transform = CoordinateTransform(source, target)
            frame.objects.map { item -> item to RectF(item.bounds.left, item.bounds.top, item.bounds.right, item.bounds.bottom)
                .apply { transform.mapRect(this) } }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        if (!display.showBoxes) return@Canvas
        mappedObjects.forEach { (_, rect) ->
            drawRect(Palette.Object, Offset(rect.left, rect.top), Size(rect.width(), rect.height()), style = Stroke(1.5.dp.toPx()))
        }
        mapped.forEach { (face, rect) ->
            val color = if (face.match != null) Palette.Accent else Palette.Warning
            val line = 2.dp.toPx()
            val corner = 19.dp.toPx().coerceAtMost(rect.width() / 3)
            drawRect(color.copy(alpha = 0.35f), Offset(rect.left, rect.top), Size(rect.width(), rect.height()), style = Stroke(1.dp.toPx()))
            listOf(Offset(rect.left, rect.top) to Offset(1f, 1f),
                Offset(rect.right, rect.top) to Offset(-1f, 1f),
                Offset(rect.left, rect.bottom) to Offset(1f, -1f),
                Offset(rect.right, rect.bottom) to Offset(-1f, -1f)).forEach { (origin, direction) ->
                drawLine(color, origin, origin + Offset(corner * direction.x, 0f), line)
                drawLine(color, origin, origin + Offset(0f, corner * direction.y), line)
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (!display.showLabels) return@Box
        mappedObjects.forEach { (item, rect) ->
            val x = rect.left.coerceIn(0f, (dimensions.width - with(density) { 140.dp.toPx() }).coerceAtLeast(0f))
            val y = rect.top.coerceIn(0f, (dimensions.height - with(density) { 26.dp.toPx() }).coerceAtLeast(0f))
            Text(if (display.showScores) String.format(Locale.US, "%s %.2f", item.label, item.score) else item.label,
                Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.widthIn(max = 140.dp)
                    .background(Palette.Hud, RoundedCornerShape(4.dp)).padding(5.dp),
                color = Palette.Object, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        mapped.forEach { (face, rect) ->
            val person = face.match?.person
            val cardWidth = with(density) { 188.dp.toPx() }.coerceAtMost(dimensions.width.toFloat())
            val cardHeight = with(density) { (if (person == null) 66 else 148).dp.toPx() }
            val gap = with(density) { 8.dp.toPx() }
            val x = rect.left.coerceIn(0f, (dimensions.width - cardWidth).coerceAtLeast(0f))
            val y = (if (rect.bottom + cardHeight + gap < dimensions.height) rect.bottom + gap
                else rect.top - cardHeight - gap).coerceIn(0f, (dimensions.height - cardHeight).coerceAtLeast(0f))
            Column(Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.width(with(density) { cardWidth.toDp() })
                .clip(RoundedCornerShape(8.dp)).background(Palette.Hud).padding(10.dp)
                .semantics(mergeDescendants = true) { contentDescription = person?.let {
                    if (display.showDetails) "Recognized ${it.name}, ${it.description}, ${it.notes}" else "Recognized ${it.name}"
                } ?: face.message }) {
                Text(person?.name ?: "Unknown face", color = if (person != null) Palette.Accent else Palette.Warning,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (person != null) {
                    if (display.showDetails && person.detail.isNotBlank()) Text(person.detail, color = Palette.Text, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val provided = listOf(person.gender, person.ageGroup).filter { it.isNotBlank() }.joinToString(" / ")
                    if (display.showDetails && provided.isNotBlank()) Text("$provided (provided)", color = Palette.Muted, fontSize = 11.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (display.showDetails && person.notes.isNotBlank()) Text(person.notes, color = Palette.Muted, fontSize = 11.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (display.showScores) Text(String.format(Locale.US, "Similarity %.2f", face.match.similarity), Modifier.padding(top = 4.dp),
                        color = Palette.Muted, fontSize = 10.sp, fontFamily = Palette.Telemetry)
                } else Text(face.message, color = Palette.Muted, fontSize = 12.sp)
            }
        }
    }
}
