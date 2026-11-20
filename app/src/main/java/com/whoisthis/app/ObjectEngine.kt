package com.whoisthis.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ObjectResult(val objects: List<ObjectObservation>, val atCapacity: Boolean)

class ObjectEngine(context: Context) : AutoCloseable {
    private val labels = context.assets.open("object-labels.txt").bufferedReader().use { it.readLines() }
    private val interpreter: Interpreter
    private val input = ByteBuffer.allocateDirect(320 * 320 * 3).order(ByteOrder.nativeOrder())
    private val image = createBitmap(320, 320)
    private val canvas = Canvas(image)
    private val pixels = IntArray(320 * 320)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.75f).build())
    private val maxDetections: Int
    private val boxes: Array<Array<FloatArray>>
    private val classes: Array<FloatArray>
    private val scores: Array<FloatArray>
    private val count = FloatArray(1)

    init {
        val bytes = context.assets.open("efficientdet_lite0.tflite").use { it.readBytes() }
        val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); rewind() }
        interpreter = Interpreter(model, Interpreter.Options().setNumThreads(2))
        require(interpreter.getInputTensor(0).shape().contentEquals(intArrayOf(1, 320, 320, 3)) &&
            interpreter.getInputTensor(0).dataType() == DataType.UINT8 && interpreter.outputTensorCount == 4)
        maxDetections = interpreter.getOutputTensor(0).shape()[1]
        require(interpreter.getOutputTensor(0).shape().contentEquals(intArrayOf(1, maxDetections, 4)))
        require(labels.first() == "person" && maxDetections > 0)
        boxes = arrayOf(Array(maxDetections) { FloatArray(4) })
        classes = arrayOf(FloatArray(maxDetections))
        scores = arrayOf(FloatArray(maxDetections))
    }

    fun detect(bitmap: Bitmap): ObjectResult {
        val geometry = DetectionGeometry(bitmap.width, bitmap.height, 320)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(bitmap, null, RectF(geometry.paddingX, geometry.paddingY,
            320 - geometry.paddingX, 320 - geometry.paddingY), paint)
        image.getPixels(pixels, 0, 320, 0, 0, 320, 320)
        input.rewind()
        pixels.forEach { color -> input.put(Color.red(color).toByte()).put(Color.green(color).toByte()).put(Color.blue(color).toByte()) }
        input.rewind()
        interpreter.runForMultipleInputsOutputs(arrayOf(input), mapOf(0 to boxes, 1 to classes, 2 to scores, 3 to count))
        val number = count[0].toInt().coerceIn(0, maxDetections)
        val objects = (0 until number).mapNotNull { index ->
            val score = scores[0][index]
            val label = labels.getOrNull(classes[0][index].toInt())
            if (!score.isFinite() || score < 0.5f || label.isNullOrBlank() || label == "???") return@mapNotNull null
            val box = boxes[0][index]
            val bounds = geometry.map(box[0], box[1], box[2], box[3]) ?: return@mapNotNull null
            val displayLabel = when (label) {
                "person" -> "Person"
                "motorcycle" -> "Motorbike"
                else -> label.replaceFirstChar { it.titlecase(java.util.Locale.ROOT) }
            }
            ObjectObservation(displayLabel, score, bounds)
        }
        return ObjectResult(objects, number == maxDetections && scores[0][number - 1] >= 0.5f)
    }

    fun scene(bitmap: Bitmap): List<SceneLabel> = Tasks.await(labeler.process(InputImage.fromBitmap(bitmap, 0)))
        .filter { it.text in sceneLabels }.sortedByDescending { it.confidence }.take(2)
        .map { SceneLabel(it.text, it.confidence) }

    override fun close() {
        labeler.close()
        interpreter.close()
        image.recycle()
    }

    companion object {
        // Scene labels describe the view; they are not localized or counted as objects.
        private val sceneLabels = setOf("Building", "Skyscraper", "Roof", "Barn", "Castle", "Palace",
            "Room", "Kitchen", "Bedroom", "Road", "Skyline", "Forest", "Beach", "Mountain")
    }
}
