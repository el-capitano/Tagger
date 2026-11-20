package com.whoisthis.app

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class ObjectDetectionTest {
    @Test fun bundledObjectModelRunsAndReturnsValidPersonCoordinates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bytes = context.assets.open("efficientdet_lite0.tflite").use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("2e04c53bfeac0ac2a30c057c7e2a777594ce39baaac35a92f74fb1e8c4fc4e0b", hash)
        val bitmap = instrumentation.context.assets.open("face-fixture.jpg").use { BitmapFactory.decodeStream(it) }
        ObjectEngine(context).use { engine ->
            val result = engine.detect(bitmap)
            assertTrue("The portrait should be detected as a person", result.objects.any { it.label == "Person" })
            result.objects.forEach {
                assertTrue(it.score in 0.5f..1f)
                assertTrue(it.bounds.left >= 0 && it.bounds.top >= 0)
                assertTrue(it.bounds.right <= bitmap.width && it.bounds.bottom <= bitmap.height)
                assertTrue(it.bounds.left < it.bounds.right && it.bounds.top < it.bounds.bottom)
            }
            assertTrue(engine.scene(bitmap).all { it.label !in setOf("Baby", "Grandparent", "Dude", "Bride", "Groom") })
        }
        bitmap.recycle()
    }
}
