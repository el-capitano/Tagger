package com.whoisthis.app

import org.junit.Assert.*
import org.junit.Test

class DetectionTest {
    @Test fun peopleCounterCombinesBodiesAndFacesWithoutDoubleCounting() {
        val firstBody = ObjectObservation("Person", 0.9f, DetectionBounds(0f, 0f, 100f, 300f))
        val secondBody = ObjectObservation("Person", 0.9f, DetectionBounds(120f, 0f, 220f, 300f))
        val firstFace = DetectionBounds(20f, 10f, 80f, 80f)
        val thirdFace = DetectionBounds(250f, 10f, 290f, 80f)
        val car = ObjectObservation("Car", 0.99f, DetectionBounds(230f, 0f, 400f, 300f))
        assertEquals(3, PeopleCounter.count(listOf(firstFace, thirdFace), listOf(firstBody, secondBody, car)))
        assertEquals(2, PeopleCounter.count(emptyList(), listOf(firstBody, secondBody, car)))
        assertEquals(1, PeopleCounter.count(listOf(firstFace), emptyList()))
        assertEquals(0, PeopleCounter.count(emptyList(), listOf(car)))
    }

    @Test fun twoFacesInsideOneBodyDetectionStillCountAsTwoPeople() {
        val body = ObjectObservation("Person", 0.8f, DetectionBounds(0f, 0f, 200f, 300f))
        assertEquals(2, PeopleCounter.count(listOf(DetectionBounds(10f, 10f, 60f, 80f),
            DetectionBounds(100f, 10f, 160f, 80f)), listOf(body)))
    }

    @Test fun overlappingPeopleDoNotInflateTheCountDependingOnFaceOrder() {
        val bodies = listOf(ObjectObservation("Person", 0.9f, DetectionBounds(0f, 0f, 100f, 300f)),
            ObjectObservation("Person", 0.9f, DetectionBounds(60f, 0f, 260f, 300f)))
        val faces = listOf(DetectionBounds(70f, 10f, 90f, 40f), DetectionBounds(10f, 10f, 30f, 40f))
        assertEquals(2, PeopleCounter.count(faces, bodies))
        assertEquals(2, PeopleCounter.count(faces.reversed(), bodies))
    }

    @Test fun letterboxMappingRestoresImageCoordinatesAndDiscardsPadding() {
        val landscape = DetectionGeometry(640, 320, 320)
        assertEquals(0.5f, landscape.scale, 0.001f)
        val full = landscape.map(0.25f, 0f, 0.75f, 1f)!!
        assertEquals(0f, full.left, 0.001f)
        assertEquals(640f, full.right, 0.001f)
        assertEquals(320f, full.bottom, 0.001f)
        assertNull(landscape.map(0f, 0f, 0.2f, 1f))
        val portrait = DetectionGeometry(320, 640, 320).map(0f, 0.25f, 1f, 0.75f)!!
        assertEquals(320f, portrait.right, 0.001f)
        assertEquals(640f, portrait.bottom, 0.001f)
        assertNull(landscape.map(Float.NaN, 0f, 1f, 1f))
        assertNull(landscape.map(0.8f, 0.9f, 0.2f, 0.1f))
    }

    @Test fun peopleCountingRunsIndependentlyFromFaceRecognitionAndObjectLabels() {
        val counterOnly = DisplayOptions(recognizeFaces = false, identifyObjects = false, countPeople = true)
        assertTrue(counterOnly.needsObjectDetector)
        assertFalse(counterOnly.recognizeFaces)
        assertFalse(counterOnly.copy(countPeople = false).needsObjectDetector)
    }
}
