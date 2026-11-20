package com.whoisthis.app

import org.junit.Assert.*
import org.junit.Test

class FaceMatcherTest {
    private val a = floatArrayOf(1f, 0f, 0f)
    private val b = floatArrayOf(0f, 1f, 0f)
    private fun person(id: String, vararg templates: FloatArray) = Person(id, id, "", "", listOf(AppearanceProfile("normal", "Normal", templates.toList())))

    @Test fun appearancesMatchOneIdentityWithoutCompetingAgainstEachOther() {
        val person = person("alex", a, a).copy(appearances = listOf(
            AppearanceProfile("normal", "Normal", listOf(a, a)),
            AppearanceProfile("smile", "Smiling", listOf(b, b)),
            AppearanceProfile("angry", "Angry", listOf(b, b))))
        assertEquals("alex", FaceMatcher.find(a, listOf(person), 0.72f)?.person?.id)
        assertEquals("alex", FaceMatcher.find(b, listOf(person), 0.72f)?.person?.id)
        assertEquals(6, person.templates.size)
    }

    @Test fun restartingCaptureRetainsPersonAndAppearanceButClearsSamples() {
        val session = EnrollmentSession("Alex", "Designer", "Note", "person-id", "Smiling", "Provided", "Adult")
        session.accept(a, 0f, 3, 1_000)
        val restarted = session.restart()
        assertEquals("person-id", restarted.personId)
        assertEquals("Smiling", restarted.appearanceLabel)
        assertEquals("Provided", restarted.gender)
        assertEquals("Adult", restarted.ageGroup)
        assertTrue(restarted.templates.isEmpty())
    }

    @Test fun normalizesAndMatchesWithoutTreatingScoreAsProbability() {
        val result = FaceMatcher.find(floatArrayOf(4f, 0f, 0f), listOf(person("a", a, a), person("b", b)), 0.72f)
        assertEquals("a", result?.person?.id)
        assertEquals(1f, result!!.similarity, 0.0001f)
        assertNull(FaceMatcher.find(floatArrayOf(0f, 0f, 1f), listOf(person("a", a)), 0.72f))
        assertNull(FaceMatcher.find(a, emptyList(), 0.72f))
    }

    @Test fun rejectsAmbiguousIdentitiesAndSingleOutlierSamples() {
        assertNull(FaceMatcher.find(a, listOf(person("a", a), person("duplicate", a)), 0.72f))
        assertNull(FaceMatcher.find(a, listOf(person("outlier", a, b)), 0.72f))
    }

    @Test fun thresholdActuallyControlsAcceptance() {
        val near = floatArrayOf(0.8f, 0.6f, 0f)
        assertNotNull(FaceMatcher.find(near, listOf(person("a", a)), 0.72f))
        assertNull(FaceMatcher.find(near, listOf(person("a", a)), 0.85f))
    }

    @Test fun rejectsMalformedVectorsAndThresholds() {
        for (invalid in listOf(floatArrayOf(), floatArrayOf(0f, 0f), floatArrayOf(Float.NaN), floatArrayOf(Float.POSITIVE_INFINITY))) {
            assertThrows(IllegalArgumentException::class.java) { FaceMatcher.normalize(invalid) }
        }
        assertThrows(IllegalArgumentException::class.java) { FaceMatcher.cosine(a, floatArrayOf(1f)) }
        assertThrows(IllegalArgumentException::class.java) { FaceMatcher.find(a, emptyList(), 0.99f) }
    }

    @Test fun identityRequiresThreeConsecutiveFramesAndDropsImmediatelyOnRejection() {
        val gate = MatchStabilizer()
        val match = FaceMatch(person("a", a), 0.9f)
        assertNull(gate.observe(1, match, 100))
        assertNull(gate.observe(1, match, 200))
        assertNotNull(gate.observe(1, match, 300))
        assertNull(gate.observe(1, null, 400))
        assertNull(gate.observe(1, match, 500))
        assertNull(gate.observe(null, match, 600))
    }

    @Test fun tracksCannotShareVotesAndOldVotesExpire() {
        val gate = MatchStabilizer()
        val first = FaceMatch(person("a", a), 0.9f)
        val second = FaceMatch(person("b", b), 0.9f)
        gate.observe(1, first, 100)
        gate.observe(1, first, 200)
        assertNull(gate.observe(2, first, 300))
        assertNull(gate.observe(1, first, 6_000))
        assertNull(gate.observe(1, second, 6_100))
        gate.retain(emptySet())
        assertNull(gate.observe(1, second, 6_200))
    }

    @Test fun enrollmentRequiresSixSpacedSamplesAcrossThreePosesFromOneTrack() {
        val session = EnrollmentSession("Alex", "Designer", "")
        assertFalse(session.accept(a, 20f, 1, 0))
        assertTrue(session.accept(a, 0f, 1, 100))
        assertFalse(session.accept(a, 0f, 1, 200))
        assertTrue(session.accept(a, 0f, 1, 600))
        assertFalse(session.accept(a, 15f, 2, 1_100))
        assertFalse(session.accept(b, 15f, 1, 1_100))
        assertTrue(session.accept(a, 15f, 1, 1_100))
        assertFalse(session.accept(a, -15f, 1, 1_600))
        assertTrue(session.accept(a, 18f, 1, 1_600))
        assertFalse(session.accept(a, 16f, 1, 2_100))
        assertTrue(session.accept(a, -15f, 1, 2_100))
        assertTrue(session.accept(a, -18f, 1, 2_600))
        assertTrue(session.complete)
        assertFalse(session.accept(a, -18f, 1, 3_100))
    }
}
