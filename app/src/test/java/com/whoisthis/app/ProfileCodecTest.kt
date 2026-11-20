package com.whoisthis.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProfileCodecTest {
    private fun vector() = FloatArray(192) { if (it == 0) 1f else 0f }
    private fun person() = Person("alex", "Alex", "Designer", "Note", listOf(
        AppearanceProfile("normal", "Normal", List(6) { vector() }),
        AppearanceProfile("smile", "Smiling", List(6) { vector() })), "Provided", "Adult")

    @Test fun migratesExistingProfilesWithoutLosingIdentityOrFaceTemplates() {
        val entry = JSONObject().put("id", "existing-id").put("name", "Alex").put("detail", "Friend")
            .put("notes", "Original notes").put("templates", JSONArray().put(JSONArray(vector().map { it.toDouble() })))
        val legacy = JSONObject().put("version", 1).put("people", JSONArray().put(entry)).toString()
        val migrated = ProfileCodec.decode(legacy).single()
        assertEquals("existing-id", migrated.id)
        assertEquals("Original notes", migrated.notes)
        assertEquals("Normal", migrated.appearances.single().label)
        assertEquals("", migrated.gender)
        assertEquals(1f, FaceMatcher.cosine(vector(), migrated.templates.single()), 0.001f)
        val reloaded = ProfileCodec.decode(ProfileCodec.encode(listOf(migrated))).single()
        assertEquals(migrated.id, reloaded.id)
        assertEquals(1, reloaded.templates.size)
    }

    @Test fun multipleAppearancesAndProvidedAttributesRoundTrip() {
        val encoded = ProfileCodec.encode(listOf(person()))
        assertEquals(2, JSONObject(encoded).getInt("version"))
        val restored = ProfileCodec.decode(encoded).single()
        assertEquals(listOf("Normal", "Smiling"), restored.appearances.map { it.label })
        assertEquals(12, restored.templates.size)
        assertEquals("Provided", restored.gender)
        assertEquals("Adult", restored.ageGroup)
    }

    @Test fun rejectsUnknownVersionsDuplicateAppearancesAndIncompatibleVectors() {
        val encoded = ProfileCodec.encode(listOf(person()))
        assertThrows(IllegalArgumentException::class.java) { ProfileCodec.decode(JSONObject(encoded).put("version", 99).toString()) }
        val duplicate = person().copy(appearances = person().appearances + AppearanceProfile("other", "smiling", listOf(vector())))
        assertThrows(IllegalArgumentException::class.java) { ProfileCodec.encode(listOf(duplicate)) }
        assertThrows(IllegalArgumentException::class.java) { ProfileCodec.encode(listOf(person().copy(appearances = emptyList()))) }
        val malformed = JSONObject(encoded)
        malformed.getJSONArray("people").getJSONObject(0).getJSONArray("appearances").getJSONObject(0)
            .put("templates", JSONArray().put(JSONArray(listOf(1.0, 0.0))))
        assertThrows(IllegalArgumentException::class.java) { ProfileCodec.decode(malformed.toString()) }
        val zero = person().copy(appearances = listOf(AppearanceProfile("bad", "Normal", listOf(FloatArray(192)))))
        assertThrows(IllegalArgumentException::class.java) { ProfileCodec.encode(listOf(zero)) }
    }
}
