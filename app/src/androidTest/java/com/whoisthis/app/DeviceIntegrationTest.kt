package com.whoisthis.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class DeviceIntegrationTest {
    @Test fun realDetectorAlignsAndRecognizesMultipleFacesWithoutAManualFaceLimit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val portrait = instrumentation.context.assets.open("face-fixture.jpg").use {
            android.graphics.BitmapFactory.decodeStream(it)
        }
        FaceEngine(instrumentation.targetContext).use { engine ->
            val session = EnrollmentSession("Fixture profile", "Test", "")
            val first = engine.analyzeImage(portrait, emptyList(), 0.72f, session)
            assertEquals(1, first.size)
            assertTrue("Fixture must pass registration quality checks: ${first.first().message}", session.templates.isNotEmpty())
            val person = Person("fixture", "Fixture profile", "Test", "", listOf(AppearanceProfile("normal", "Normal", session.templates.toList())))
            engine.reset()
            var recognized = emptyList<FaceObservation>()
            repeat(3) { recognized = engine.analyzeImage(portrait, listOf(person), 0.72f) }
            assertEquals("fixture", recognized.single().match?.person?.id)
            val group = android.graphics.Bitmap.createBitmap(portrait.width * 2, portrait.height,
                android.graphics.Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(group).apply {
                drawBitmap(portrait, 0f, 0f, null)
                drawBitmap(portrait, portrait.width.toFloat(), 0f, null)
            }
            engine.reset()
            var attempts = 0
            do {
                recognized = engine.analyzeImage(group, listOf(person), 0.72f)
                attempts++
            } while ((recognized.size != 2 || recognized.any { it.match == null }) && attempts < 8)
            assertEquals(2, recognized.size)
            assertTrue("Tracker should settle on both faces: ${recognized.map { it.trackId to it.message }}",
                recognized.all { it.match?.person?.id == "fixture" })
            val unknown = engine.analyzeImage(group, emptyList(), 0.72f)
            assertTrue(unknown.all { it.match == null })
            group.recycle()
        }
        portrait.recycle()
    }

    @Test fun bundledModelHasExpectedHashTensorsAndFiniteEmbeddings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = context.assets.open("mobilefacenet.tflite").use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(model).joinToString("") { "%02x".format(it) }
        assertEquals("be4bc7cfc53f7bc336d0f28b1ab92535f618c913a422b683210750f6b5354854", hash)
        val buffer = ByteBuffer.allocateDirect(model.size).order(ByteOrder.nativeOrder()).apply { put(model); rewind() }
        Interpreter(buffer).use { interpreter ->
            assertArrayEquals(intArrayOf(1, 112, 112, 3), interpreter.getInputTensor(0).shape())
            assertArrayEquals(intArrayOf(1, 192), interpreter.getOutputTensor(0).shape())
            val input = ByteBuffer.allocateDirect(112 * 112 * 3 * 4).order(ByteOrder.nativeOrder())
            repeat(112 * 112 * 3) { input.putFloat((it % 255 - 127.5f) / 127.5f) }
            input.rewind()
            val output = arrayOf(FloatArray(192))
            interpreter.run(input, output)
            val vector = FaceMatcher.normalize(output[0])
            assertTrue(vector.all { it.isFinite() })
            assertEquals(1f, FaceMatcher.cosine(vector, vector), 0.0001f)
        }
        // Also checks the bundled ML Kit client and production interpreter initialize together.
        FaceEngine(context).use { }
    }

    @Test fun profilesRoundTripEncryptedAndDeletionPersists() {
        // Keep the fixture in an isolated cache directory, away from registered profiles.
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = java.io.File(target.cacheDir, "profile-store-integration").apply { mkdirs() }
        val context = object : android.content.ContextWrapper(target) {
            override fun getNoBackupFilesDir() = directory
        }
        val store = ProfileStore(context)
        val person = Person("test-person", "Test Person", "Tester", "Private test note",
            listOf(AppearanceProfile("normal", "Normal", List(6) { FloatArray(192) { index -> if (index == 0) 1f else 0f } })))
        store.save(listOf(person))
        val encrypted = java.io.File(context.noBackupFilesDir, "profiles.enc").readBytes()
        assertFalse(String(encrypted, Charsets.ISO_8859_1).contains("Private test note"))
        val restored = store.load().single()
        assertEquals(person.name, restored.name)
        assertEquals(person.notes, restored.notes)
        assertEquals(6, restored.templates.size)
        assertEquals(1f, FaceMatcher.cosine(person.templates[0], restored.templates[0]), 0.0001f)
        store.save(emptyList())
        assertTrue(store.load().isEmpty())
    }
}
