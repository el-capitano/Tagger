package com.whoisthis.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import androidx.core.graphics.createBitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.hypot

data class FaceObservation(
    val trackId: Int?,
    val bounds: RectF,
    val match: FaceMatch?,
    val message: String,
)

@androidx.annotation.OptIn(markerClass = [androidx.camera.view.TransformExperimental::class])
data class ScanFrame(
    val faces: List<FaceObservation> = emptyList(),
    val transform: OutputTransform? = null,
    val processingMs: Long = 0,
    val completedAt: Long = SystemClock.elapsedRealtime(),
    val objects: List<ObjectObservation> = emptyList(),
    val scene: List<SceneLabel> = emptyList(),
    val peopleCount: Int = 0,
    val objectsAtCapacity: Boolean = false,
)

@androidx.annotation.OptIn(markerClass = [androidx.camera.view.TransformExperimental::class])
class FaceEngine(context: Context) : AutoCloseable {
    private val detector = FaceDetection.getClient(FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setMinFaceSize(0.08f)
        .enableTracking()
        .build())
    private val interpreter: Interpreter
    private val input = ByteBuffer.allocateDirect(112 * 112 * 3 * 4).order(ByteOrder.nativeOrder())
    private val pixels = IntArray(112 * 112)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val stability = MatchStabilizer()
    private val objects by lazy { ObjectEngine(context) }
    private var objectsInitialized = false
    private val transformFactory = ImageProxyTransformFactory().apply {
        isUsingCropRect = true
        isUsingRotationDegrees = true
    }

    init {
        val bytes = context.assets.open("mobilefacenet.tflite").use { it.readBytes() }
        val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
            put(bytes)
            rewind()
        }
        interpreter = Interpreter(model, Interpreter.Options().setNumThreads(2))
        require(interpreter.getInputTensor(0).shape().contentEquals(intArrayOf(1, 112, 112, 3)) &&
            interpreter.getInputTensor(0).dataType() == DataType.FLOAT32 &&
            interpreter.getOutputTensor(0).shape().contentEquals(intArrayOf(1, FaceMatcher.DIMENSIONS))) {
            "The bundled face model has incompatible tensors"
        }
    }

    fun reset() { stability.clear() }

    // CameraX runs this entire pipeline on one worker; only the latest frame is queued.
    fun analyze(
        proxy: ImageProxy,
        people: List<Person>,
        threshold: Float,
        enrollment: EnrollmentSession?,
        options: DisplayOptions = DisplayOptions(),
    ): ScanFrame {
        val started = SystemClock.elapsedRealtime()
        val transform = transformFactory.getOutputTransform(proxy)
        val raw = proxy.toBitmap()
        val crop = proxy.cropRect
        val cropped = Bitmap.createBitmap(raw, crop.left, crop.top, crop.width(), crop.height())
        val upright = Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height,
            Matrix().apply { postRotate(proxy.imageInfo.rotationDegrees.toFloat()) }, true)
        try {
            val faces = if (options.recognizeFaces || options.countPeople || enrollment != null) {
                analyzeImage(upright, people, threshold, enrollment, options.recognizeFaces)
            } else emptyList()
            val objectResult = if (options.needsObjectDetector && enrollment == null) {
                objectsInitialized = true
                objects.detect(upright)
            } else ObjectResult(emptyList(), false)
            val scene = if (options.identifyObjects && enrollment == null) objects.scene(upright) else emptyList()
            val faceBounds = faces.map { DetectionBounds(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom) }
            return ScanFrame(faces, transform, SystemClock.elapsedRealtime() - started,
                objects = objectResult.objects, scene = scene,
                peopleCount = PeopleCounter.count(faceBounds, objectResult.objects), objectsAtCapacity = objectResult.atCapacity)
        } finally {
            upright.recycle()
            if (cropped !== upright) cropped.recycle()
            if (raw !== cropped && raw !== upright) raw.recycle()
        }
    }

    internal fun analyzeImage(
        upright: Bitmap,
        people: List<Person>,
        threshold: Float,
        enrollment: EnrollmentSession? = null,
        recognize: Boolean = true,
    ): List<FaceObservation> {
            val detected = Tasks.await(detector.process(InputImage.fromBitmap(upright, 0)))
            val now = SystemClock.elapsedRealtime()
            stability.retain(detected.mapNotNull { it.trackingId }.toSet())
            val faces = detected.map { face ->
                if (!recognize && enrollment == null) {
                    stability.observe(face.trackingId, null, now)
                    return@map FaceObservation(face.trackingId, RectF(face.boundingBox), null, "Face detected")
                }
                var quality = qualityIssue(face, upright, enrollment != null)
                if (enrollment != null && detected.size != 1) quality = "Only one person in frame"
                var match: FaceMatch? = null
                var message = quality ?: "Not registered"
                if (quality == null) {
                    val aligned = align(upright, face)
                    try {
                        quality = imageQualityIssue(aligned)
                        if (quality == null) {
                            val vector = embedding(aligned)
                            if (enrollment != null) {
                                val sameTrack = enrollment.isSameTrack(face.trackingId)
                                enrollment.accept(vector, face.headEulerAngleY, face.trackingId, now)
                                message = if (sameTrack) enrollment.instruction else "Face changed. Restart registration"
                            } else {
                                val candidate = FaceMatcher.find(vector, people, threshold)
                                match = stability.observe(face.trackingId, candidate, now)
                                message = when {
                                    match != null -> "Matched"
                                    candidate != null -> "Verifying…"
                                    people.isEmpty() -> "No profiles yet"
                                    else -> "Not registered"
                                }
                            }
                        } else message = quality
                    } finally { aligned.recycle() }
                }
                if (quality != null || enrollment != null) stability.observe(face.trackingId, null, now)
                FaceObservation(face.trackingId, RectF(face.boundingBox), match, message)
            }
            return faces
    }

    private fun qualityIssue(face: Face, image: Bitmap, registering: Boolean): String? {
        val box = face.boundingBox
        val minimum = if (registering) 140 else 100
        if (box.width() < minimum || box.height() < minimum) return "Move closer"
        if (box.left < 2 || box.top < 2 || box.right > image.width - 2 || box.bottom > image.height - 2) {
            return "Keep your whole face in view"
        }
        if (abs(face.headEulerAngleY) > 28 || abs(face.headEulerAngleX) > 20 || abs(face.headEulerAngleZ) > 25) {
            return "Face the camera"
        }
        val left = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return "Keep both eyes visible"
        val right = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return "Keep both eyes visible"
        if (hypot(left.x - right.x, left.y - right.y) < 24) return "Move closer"
        return null
    }

    private fun align(image: Bitmap, face: Face): Bitmap {
        val eyes = listOf(face.getLandmark(FaceLandmark.LEFT_EYE)!!.position,
            face.getLandmark(FaceLandmark.RIGHT_EYE)!!.position).sortedBy { it.x }
        val matrix = Matrix()
        // Match the model source's 2.5× eye-distance crop, with eyes at 35% height.
        check(matrix.setPolyToPoly(floatArrayOf(eyes[0].x, eyes[0].y, eyes[1].x, eyes[1].y), 0,
            floatArrayOf(33.6f, 39.2f, 78.4f, 39.2f), 0, 2))
        return createBitmap(112, 112).also {
            Canvas(it).drawBitmap(image, matrix, paint)
        }
    }

    private fun imageQualityIssue(bitmap: Bitmap): String? {
        bitmap.getPixels(pixels, 0, 112, 0, 0, 112, 112)
        fun luminance(index: Int): Float {
            val color = pixels[index]
            return Color.red(color) * 0.299f + Color.green(color) * 0.587f + Color.blue(color) * 0.114f
        }
        var brightness = 0.0
        var laplacian = 0.0
        var square = 0.0
        var count = 0
        for (y in 12 until 100) for (x in 12 until 100) {
            val index = y * 112 + x
            val center = luminance(index)
            val value = 4 * center - luminance(index - 1) - luminance(index + 1) -
                luminance(index - 112) - luminance(index + 112)
            brightness += center
            laplacian += value
            square += value * value
            count++
        }
        if (brightness / count < 45) return "Find more light"
        if (brightness / count > 220) return "Avoid bright backlighting"
        // shortcut: heuristic quality cutoffs; calibrate against target cameras before deployment.
        if (square / count - (laplacian / count) * (laplacian / count) < 40) return "Hold still for a sharper image"
        return null
    }

    private fun embedding(bitmap: Bitmap): FloatArray {
        bitmap.getPixels(pixels, 0, 112, 0, 0, 112, 112)
        input.rewind()
        pixels.forEach { pixel ->
            input.putFloat((Color.red(pixel) - 127.5f) / 127.5f)
            input.putFloat((Color.green(pixel) - 127.5f) / 127.5f)
            input.putFloat((Color.blue(pixel) - 127.5f) / 127.5f)
        }
        input.rewind()
        val output = arrayOf(FloatArray(FaceMatcher.DIMENSIONS))
        interpreter.run(input, output)
        return FaceMatcher.normalize(output[0])
    }

    override fun close() {
        detector.close()
        interpreter.close()
        if (objectsInitialized) objects.close()
    }
}
