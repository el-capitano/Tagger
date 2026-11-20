package com.whoisthis.app

import android.annotation.SuppressLint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.CoordinateTransform
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CameraSwitchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun switchesBothCamerasDuringRegistrationAndMapsMirroredPreviewCoordinates() {
        val context = instrumentation.targetContext
        val provider = ProcessCameraProvider.getInstance(context).get(15, TimeUnit.SECONDS)
        assumeTrue("This test requires both cameras", provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) &&
            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
        instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.CAMERA")
            .let { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() } }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: RecognitionViewModel
            scenario.onActivity { model = ViewModelProvider(it)[RecognitionViewModel::class.java] }
            awaitCamera(model, "Live rear camera", 0)
            verifyOverlayDirection(scenario, model, mirrored = false)

            model.register("Camera switch test", "", "")
            await("registration to start") { model.state.value.enrollmentName == "Camera switch test" }
            val frontStart = SystemClock.elapsedRealtime()
            clickCameraButton("Switch to front camera")
            awaitCamera(model, "Live front camera", frontStart)
            assertEquals("Camera switch test", model.state.value.enrollmentName)
            assertEquals(0, model.state.value.enrollmentCount)
            verifyOverlayDirection(scenario, model, mirrored = true)

            val rearStart = SystemClock.elapsedRealtime()
            clickCameraButton("Switch to rear camera")
            awaitCamera(model, "Live rear camera", rearStart)
            assertEquals("Camera switch test", model.state.value.enrollmentName)
            verifyOverlayDirection(scenario, model, mirrored = false)
            model.cancelEnrollment()
        }
    }

    private fun awaitCamera(model: RecognitionViewModel, label: String, after: Long) {
        await(label + " and a newly analyzed frame") {
            val frame = model.state.value.frame
            frame.processingMs > 0 && frame.completedAt > after &&
                instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(label)
                    ?.any { it.text?.toString() == label } == true
        }
    }

    private fun clickCameraButton(description: String) {
        var clicked = false
        await(description) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val pending = ArrayDeque<AccessibilityNodeInfo>()
            if (root != null) pending.add(root)
            while (pending.isNotEmpty() && !clicked) {
                val node = pending.removeFirst()
                if (node.contentDescription?.toString() == description && node.isClickable) {
                    clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } else repeat(node.childCount) { node.getChild(it)?.let(pending::addLast) }
            }
            clicked
        }
    }

    @androidx.annotation.OptIn(markerClass = [androidx.camera.view.TransformExperimental::class])
    @SuppressLint("RestrictedApi") // Inspect the pinned CameraX matrices only to verify real preview mirroring.
    private fun verifyOverlayDirection(
        scenario: ActivityScenario<MainActivity>,
        model: RecognitionViewModel,
        mirrored: Boolean,
    ) {
        scenario.onActivity { activity ->
            val preview = checkNotNull(findPreview(activity.window.decorView))
            val source = checkNotNull(model.state.value.frame.transform)
            val target = checkNotNull(preview.outputTransform)
            val imageBounds = RectF(-1f, -1f, 1f, 1f).apply { source.matrix.mapRect(this) }
            val point = floatArrayOf(imageBounds.left + imageBounds.width() * 0.25f, imageBounds.centerY())
            CoordinateTransform(source, target).mapPoints(point)
            assertEquals("Face boxes must follow the active camera's mirroring",
                preview.width * if (mirrored) 0.75f else 0.25f, point[0], 3f)
            assertEquals(preview.height * 0.5f, point[1], 3f)
        }
    }

    private fun findPreview(view: View): PreviewView? {
        if (view is PreviewView) return view
        if (view is ViewGroup) repeat(view.childCount) { findPreview(view.getChildAt(it))?.let { return it } }
        return null
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        fail("Timed out waiting for $description")
    }
}
