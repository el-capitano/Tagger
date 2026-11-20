package com.whoisthis.app

data class DisplayOptions(
    val recognizeFaces: Boolean = true,
    val identifyObjects: Boolean = true,
    val countPeople: Boolean = false,
    val showBoxes: Boolean = true,
    val showLabels: Boolean = true,
    val showDetails: Boolean = true,
    val showScores: Boolean = false,
    val showRecognizedCount: Boolean = true,
) {
    val needsObjectDetector get() = identifyObjects || countPeople
}

data class DetectionBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)
data class ObjectObservation(val label: String, val score: Float, val bounds: DetectionBounds)
data class SceneLabel(val label: String, val score: Float)

object PeopleCounter {
    fun count(faces: List<DetectionBounds>, objects: List<ObjectObservation>): Int {
        val bodies = objects.filter { it.label == "Person" }.map { it.bounds }
        val assignedFace = IntArray(bodies.size) { -1 }
        fun match(faceIndex: Int, visited: BooleanArray): Boolean {
            val face = faces[faceIndex]
            val centerX = (face.left + face.right) / 2
            val centerY = (face.top + face.bottom) / 2
            for (index in bodies.indices) {
                val body = bodies[index]
                if (!visited[index] && centerX in body.left..body.right && centerY in body.top..body.bottom) {
                    visited[index] = true
                    if (assignedFace[index] == -1 || match(assignedFace[index], visited)) {
                        assignedFace[index] = faceIndex
                        return true
                    }
                }
            }
            return false
        }
        // Reassign overlapping matches so processing order cannot inflate the count.
        val matched = faces.indices.count { match(it, BooleanArray(bodies.size)) }
        return bodies.size + faces.size - matched
    }
}

/** Reverses aspect-preserving model padding without stretching object boxes. */
class DetectionGeometry(val width: Int, val height: Int, val side: Int) {
    init { require(width > 0 && height > 0 && side > 0) }
    val scale = minOf(side.toFloat() / width, side.toFloat() / height)
    val paddingX = (side - width * scale) / 2
    val paddingY = (side - height * scale) / 2

    fun map(top: Float, left: Float, bottom: Float, right: Float): DetectionBounds? {
        if (!listOf(top, left, bottom, right).all { it.isFinite() } || top >= bottom || left >= right) return null
        val x1 = ((left * side - paddingX) / scale).coerceIn(0f, width.toFloat())
        val y1 = ((top * side - paddingY) / scale).coerceIn(0f, height.toFloat())
        val x2 = ((right * side - paddingX) / scale).coerceIn(0f, width.toFloat())
        val y2 = ((bottom * side - paddingY) / scale).coerceIn(0f, height.toFloat())
        return if (x2 > x1 && y2 > y1) DetectionBounds(x1, y1, x2, y2) else null
    }
}
