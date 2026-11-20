package com.whoisthis.app

import kotlin.math.sqrt

data class AppearanceProfile(val id: String, val label: String, val templates: List<FloatArray>)

data class Person(
    val id: String,
    val name: String,
    val detail: String,
    val notes: String,
    val appearances: List<AppearanceProfile>,
    val gender: String = "",
    val ageGroup: String = "",
) {
    val templates get() = appearances.flatMap { it.templates }
    val description get() = listOf(detail, gender, ageGroup).filter { it.isNotBlank() }.joinToString(" / ")
}

data class FaceMatch(val person: Person, val similarity: Float)

object FaceMatcher {
    const val DIMENSIONS = 192
    const val DEFAULT_THRESHOLD = 0.72f
    const val MIN_MARGIN = 0.08f
    const val MAX_APPEARANCES = 20

    fun normalize(vector: FloatArray): FloatArray {
        require(vector.isNotEmpty() && vector.all { it.isFinite() }) { "Invalid face vector" }
        val norm = sqrt(vector.sumOf { it.toDouble() * it })
        require(norm > 1e-8) { "Empty face vector" }
        return FloatArray(vector.size) { (vector[it] / norm).toFloat() }
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size && a.isNotEmpty()) { "Incompatible face vectors" }
        val left = normalize(a)
        val right = normalize(b)
        return left.indices.sumOf { (left[it] * right[it]).toDouble() }.toFloat().coerceIn(-1f, 1f)
    }

    fun find(vector: FloatArray, people: List<Person>, threshold: Float): FaceMatch? {
        require(threshold in 0.6f..0.9f)
        val scores = people.mapNotNull { person ->
            val score = person.appearances.mapNotNull { appearance ->
                val samples = appearance.templates.filter { it.size == vector.size }
                    .map { cosine(vector, it) }.sortedDescending().take(2)
                samples.takeIf { it.isNotEmpty() }?.average()?.toFloat()
            }.maxOrNull()
            score?.let { FaceMatch(person, it) }
        }.sortedByDescending { it.similarity }
        val best = scores.firstOrNull() ?: return null
        if (best.similarity < threshold) return null
        if (scores.size > 1 && best.similarity - scores[1].similarity < MIN_MARGIN) return null
        return best
    }
}

/** A name is visible only after three consecutive, unambiguous observations. */
class MatchStabilizer {
    private data class Vote(val personId: String, val count: Int, val lastSeen: Long)
    private val votes = mutableMapOf<Int, Vote>()

    fun observe(trackId: Int?, match: FaceMatch?, now: Long): FaceMatch? {
        if (trackId == null) return null
        if (match == null) {
            votes.remove(trackId)
            return null
        }
        val previous = votes[trackId]
        val count = if (previous?.personId == match.person.id && now - previous.lastSeen <= 5_000) {
            previous.count + 1
        } else 1
        votes[trackId] = Vote(match.person.id, count, now)
        return match.takeIf { count >= 3 }
    }

    fun retain(activeIds: Set<Int>) { votes.keys.retainAll(activeIds) }
    fun clear() { votes.clear() }
}

/** Six templates: two straight on, two with a small turn in each direction. */
class EnrollmentSession(
    val name: String,
    val detail: String,
    val notes: String,
    val personId: String? = null,
    val appearanceLabel: String = "Normal",
    val gender: String = "",
    val ageGroup: String = "",
) {
    val templates = mutableListOf<FloatArray>()
    private var lastCapture = Long.MIN_VALUE
    private var trackId: Int? = null
    val complete get() = templates.size == 6
    val instruction: String get() = when (templates.size) {
        0, 1 -> "Look straight at the camera"
        2, 3 -> "Turn your head slightly to one side"
        4, 5 -> "Now turn slightly to the other side"
        else -> "Face captured. Saving profile…"
    }
    private var firstTurnSign = 0f

    fun accept(vector: FloatArray, yaw: Float, id: Int?, now: Long): Boolean {
        if (complete || id == null) return false
        if (trackId != null && id != trackId) return false
        if (templates.isNotEmpty() && now - lastCapture < 450) return false
        if (templates.isNotEmpty() && FaceMatcher.cosine(templates.first(), vector) < 0.55f) return false
        val poseReady = when (templates.size) {
            0, 1 -> kotlin.math.abs(yaw) <= 10
            2 -> kotlin.math.abs(yaw) in 12f..24f
            3 -> yaw * firstTurnSign in 12f..24f
            else -> -yaw * firstTurnSign in 12f..24f
        }
        if (!poseReady) return false
        if (templates.size == 2) firstTurnSign = if (yaw > 0) 1f else -1f
        trackId = id
        templates += FaceMatcher.normalize(vector)
        lastCapture = now
        return true
    }

    fun isSameTrack(id: Int?) = trackId == null || trackId == id
    fun restart() = EnrollmentSession(name, detail, notes, personId, appearanceLabel, gender, ageGroup)
}
