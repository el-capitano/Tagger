package com.whoisthis.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The Keystore key and encrypted no-backup file never leave this installation. */
class ProfileStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "profiles.enc"))
    private val alias = "whoisthis.profiles.v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            generateKey()
        }
    }

    fun load(): List<Person> {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return emptyList()
        val bytes = file.readFully()
        require(bytes.size > 29 && bytes[0] == 1.toByte()) { "Unsupported profile database" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        return ProfileCodec.decode(String(cipher.doFinal(bytes.copyOfRange(13, bytes.size)), Charsets.UTF_8))
    }

    fun save(people: List<Person>) {
        val plaintext = ProfileCodec.encode(people).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = byteArrayOf(1) + cipher.iv + cipher.doFinal(plaintext)
        val stream = file.startWrite()
        try {
            stream.write(encrypted)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}

internal object ProfileCodec {
    fun decode(json: String): List<Person> {
        val root = JSONObject(json)
        val version = root.getInt("version")
        require(version in 1..2) { "Unsupported profile version" }
        val entries = root.getJSONArray("people")
        val people = List(entries.length()) { index ->
            val value = entries.getJSONObject(index)
            val appearances = if (version == 1) {
                listOf(AppearanceProfile("normal", "Normal", readSamples(value.getJSONArray("templates"))))
            } else {
                val array = value.getJSONArray("appearances")
                List(array.length()) { profileIndex ->
                    val appearance = array.getJSONObject(profileIndex)
                    AppearanceProfile(appearance.getString("id"), appearance.getString("label"),
                        readSamples(appearance.getJSONArray("templates")))
                }
            }
            Person(value.getString("id"), value.getString("name"), value.getString("detail"),
                value.getString("notes"), appearances, value.optString("gender", ""), value.optString("ageGroup", ""))
                .also(::validate)
        }
        require(people.map { it.id }.distinct().size == people.size) { "Duplicate person IDs" }
        return people
    }

    fun encode(people: List<Person>): String {
        require(people.map { it.id }.distinct().size == people.size) { "Duplicate person IDs" }
        val entries = JSONArray()
        people.forEach { person ->
            validate(person)
            val appearances = JSONArray()
            person.appearances.forEach { appearance ->
                val samples = JSONArray()
                appearance.templates.forEach { vector ->
                    require(vector.size == FaceMatcher.DIMENSIONS) { "Incompatible face model" }
                    samples.put(JSONArray(FaceMatcher.normalize(vector).map { it.toDouble() }))
                }
                appearances.put(JSONObject().put("id", appearance.id).put("label", appearance.label).put("templates", samples))
            }
            entries.put(JSONObject().put("id", person.id).put("name", person.name).put("detail", person.detail)
                .put("notes", person.notes).put("gender", person.gender).put("ageGroup", person.ageGroup)
                .put("appearances", appearances))
        }
        return JSONObject().put("version", 2).put("people", entries).toString()
    }

    private fun readSamples(samples: JSONArray): List<FloatArray> {
        require(samples.length() in 1..12) { "Invalid profile samples" }
        return List(samples.length()) { index ->
            val sample = samples.getJSONArray(index)
            require(sample.length() == FaceMatcher.DIMENSIONS) { "Incompatible face model" }
            FaceMatcher.normalize(FloatArray(sample.length()) { sample.getDouble(it).toFloat() })
        }
    }

    private fun validate(person: Person) {
        require(person.id.isNotBlank() && person.name.isNotBlank() && person.name.length <= 60 &&
            person.detail.length <= 80 && person.notes.length <= 160 && person.gender.length <= 40 && person.ageGroup.length <= 20)
        require(person.appearances.size in 1..FaceMatcher.MAX_APPEARANCES) { "Invalid appearance count" }
        require(person.appearances.map { it.id }.distinct().size == person.appearances.size) { "Duplicate appearance IDs" }
        require(person.appearances.map { it.label.trim().lowercase(java.util.Locale.ROOT) }.distinct().size == person.appearances.size) {
            "Duplicate appearance labels"
        }
        person.appearances.forEach {
            require(it.id.isNotBlank() && it.label.isNotBlank() && it.label.length <= 40 && it.templates.size in 1..12)
        }
    }
}
