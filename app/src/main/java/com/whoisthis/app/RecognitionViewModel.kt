package com.whoisthis.app

import android.app.Application
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.Executors

data class AppState(
    val loading: Boolean = true,
    val profiles: List<Person> = emptyList(),
    val frame: ScanFrame = ScanFrame(),
    val enrollmentName: String? = null,
    val enrollmentCount: Int = 0,
    val enrollmentLabel: String = "",
    val instruction: String = "",
    val threshold: Float = FaceMatcher.DEFAULT_THRESHOLD,
    val error: String? = null,
    val fatal: Boolean = false,
    val notice: String? = null,
    val display: DisplayOptions = DisplayOptions(),
)

class RecognitionViewModel(application: Application) : AndroidViewModel(application), ImageAnalysis.Analyzer {
    val cameraExecutor = Executors.newSingleThreadExecutor()
    private val mutableState = MutableStateFlow(AppState())
    val state = mutableState.asStateFlow()
    private val store = ProfileStore(application)
    private var engine: FaceEngine? = null
    private var enrollment: EnrollmentSession? = null
    private var lastFrame = 0L
    private var databaseReady = false
    private val preferences = application.getSharedPreferences("recognition", 0)

    init {
        cameraExecutor.execute {
            try {
                val profiles = store.load()
                databaseReady = true
                val threshold = preferences.getFloat("threshold", FaceMatcher.DEFAULT_THRESHOLD)
                    .takeIf { it.isFinite() && it in 0.6f..0.9f } ?: FaceMatcher.DEFAULT_THRESHOLD
                engine = FaceEngine(application)
                val display = DisplayOptions(
                    recognizeFaces = preferences.getBoolean("faces", true), identifyObjects = preferences.getBoolean("objects", true),
                    countPeople = preferences.getBoolean("peopleCount", false), showBoxes = preferences.getBoolean("boxes", true),
                    showLabels = preferences.getBoolean("labels", true), showDetails = preferences.getBoolean("details", true),
                    showScores = preferences.getBoolean("scores", false), showRecognizedCount = preferences.getBoolean("recognizedCount", true))
                mutableState.update { it.copy(loading = false, profiles = profiles, threshold = threshold, display = display) }
            } catch (error: Exception) {
                mutableState.update { it.copy(loading = false, fatal = true,
                    error = if (!databaseReady) "Cannot unlock saved profiles. Your data has not been overwritten. Clear app storage in Android settings to reset it."
                        else "Could not load the face model. Reinstall the app or report this error: ${error.javaClass.simpleName}") }
            }
        }
    }

    override fun analyze(image: ImageProxy) {
        try {
            val currentEngine = engine ?: return
            val now = SystemClock.elapsedRealtime()
            if (now - lastFrame < 120 || mutableState.value.fatal) return
            lastFrame = now
            val snapshot = mutableState.value
            val session = enrollment
            val frame = currentEngine.analyze(image, snapshot.profiles, snapshot.threshold, session, snapshot.display)
            if (mutableState.value.display != snapshot.display) return
            mutableState.update { it.copy(frame = frame, enrollmentCount = session?.templates?.size ?: 0,
                instruction = session?.instruction.orEmpty()) }
            if (session?.complete == true) {
                try {
                    val appearance = AppearanceProfile(UUID.randomUUID().toString(), session.appearanceLabel, session.templates.toList())
                    val existing = session.personId?.let { id -> snapshot.profiles.find { it.id == id } }
                    require(session.personId == null || existing != null) { "The selected person no longer exists" }
                    if (existing != null) {
                        require(existing.appearances.size < FaceMatcher.MAX_APPEARANCES)
                        require(existing.appearances.none { it.label.equals(appearance.label, ignoreCase = true) })
                        require(appearance.templates.any { vector -> existing.templates.any { FaceMatcher.cosine(vector, it) >= 0.5f } }) {
                            "The face differs from the selected person. Check the person and capture again."
                        }
                    }
                    val person = existing?.copy(appearances = existing.appearances + appearance)
                        ?: Person(UUID.randomUUID().toString(), session.name, session.detail, session.notes,
                            listOf(appearance), session.gender, session.ageGroup)
                    val profiles = if (existing == null) snapshot.profiles + person
                        else snapshot.profiles.map { if (it.id == person.id) person else it }
                    // Publish changes only after the encrypted file has committed successfully.
                    store.save(profiles)
                    mutableState.update { it.copy(profiles = profiles, notice = "${person.name}: ${appearance.label} saved") }
                } catch (error: Exception) {
                    mutableState.update { it.copy(error = error.message ?: "Could not save the profile. Please try again.") }
                }
                endEnrollment()
            }
        } catch (error: Exception) {
            engine?.reset()
            mutableState.update { it.copy(frame = ScanFrame(), error = "Camera analysis failed. Pause and resume, or switch cameras to retry.") }
        } finally { image.close() }
    }

    fun register(name: String, detail: String, notes: String, appearanceLabel: String = "Normal",
        personId: String? = null, gender: String = "", ageGroup: String = "") {
        require(name.isNotBlank() && name.length <= 60 && detail.length <= 80 && notes.length <= 160 &&
            appearanceLabel.isNotBlank() && appearanceLabel.length <= 40 && gender.length <= 40 && ageGroup.length <= 20)
        cameraExecutor.execute {
            if (!databaseReady || engine == null) return@execute
            if (personId != null) {
                val person = mutableState.value.profiles.find { it.id == personId }
                if (person == null || person.appearances.size >= FaceMatcher.MAX_APPEARANCES ||
                    person.appearances.any { it.label.equals(appearanceLabel.trim(), true) }) {
                    mutableState.update { it.copy(error = "Choose a new appearance label. A person can have up to 20 appearances.") }
                    return@execute
                }
            }
            enrollment = EnrollmentSession(name.trim(), detail.trim(), notes.trim(), personId, appearanceLabel.trim(), gender.trim(), ageGroup.trim())
            engine?.reset()
            mutableState.update { it.copy(enrollmentName = name.trim(), enrollmentCount = 0,
                enrollmentLabel = appearanceLabel.trim(), instruction = enrollment!!.instruction, error = null, frame = ScanFrame()) }
        }
    }

    fun cancelEnrollment() { cameraExecutor.execute { endEnrollment() } }

    private fun endEnrollment() {
        enrollment = null
        engine?.reset()
        mutableState.update { it.copy(enrollmentName = null, enrollmentCount = 0, enrollmentLabel = "", instruction = "", frame = ScanFrame()) }
    }

    fun resetTracking() {
        cameraExecutor.execute {
            engine?.reset()
            lastFrame = 0
            enrollment = enrollment?.restart()
            mutableState.update { it.copy(frame = ScanFrame(), enrollmentCount = 0,
                instruction = enrollment?.instruction.orEmpty()) }
        }
    }

    fun delete(person: Person) {
        cameraExecutor.execute {
            val profiles = mutableState.value.profiles.filterNot { it.id == person.id }
            try {
                store.save(profiles)
                engine?.reset()
                mutableState.update { it.copy(profiles = profiles, frame = ScanFrame(), notice = "Profile deleted") }
            } catch (error: Exception) {
                mutableState.update { it.copy(error = "Could not delete the profile. Please try again.") }
            }
        }
    }

    fun setThreshold(value: Float) {
        require(value.isFinite() && value in 0.6f..0.9f)
        cameraExecutor.execute {
            preferences.edit { putFloat("threshold", value) }
            engine?.reset()
            mutableState.update { it.copy(threshold = value, frame = ScanFrame()) }
        }
    }

    fun updatePerson(person: Person) {
        cameraExecutor.execute {
            val current = mutableState.value.profiles
            val previous = current.find { it.id == person.id } ?: return@execute
            val updated = previous.copy(name = person.name.trim(), detail = person.detail.trim(), notes = person.notes.trim(),
                gender = person.gender.trim(), ageGroup = person.ageGroup.trim())
            savePeople(current.map { if (it.id == updated.id) updated else it }, "Details updated")
        }
    }

    fun deleteAppearance(personId: String, appearanceId: String) {
        cameraExecutor.execute {
            val current = mutableState.value.profiles
            val person = current.find { it.id == personId } ?: return@execute
            if (person.appearances.size <= 1) return@execute
            val updated = person.copy(appearances = person.appearances.filterNot { it.id == appearanceId })
            savePeople(current.map { if (it.id == personId) updated else it }, "Appearance deleted")
        }
    }

    private fun savePeople(people: List<Person>, notice: String) {
        try {
            store.save(people)
            engine?.reset()
            mutableState.update { it.copy(profiles = people, frame = ScanFrame(), notice = notice) }
        } catch (error: Exception) {
            mutableState.update { it.copy(error = "Could not save changes. Your saved profiles have not been replaced.") }
        }
    }

    fun setDisplay(value: DisplayOptions) {
        val previous = mutableState.value.display
        val modeChanged = previous.recognizeFaces != value.recognizeFaces || previous.identifyObjects != value.identifyObjects ||
            previous.countPeople != value.countPeople
        mutableState.update { it.copy(display = value, frame = if (modeChanged) ScanFrame() else it.frame) }
        cameraExecutor.execute {
            preferences.edit {
                putBoolean("faces", value.recognizeFaces); putBoolean("objects", value.identifyObjects)
                putBoolean("peopleCount", value.countPeople); putBoolean("boxes", value.showBoxes)
                putBoolean("labels", value.showLabels); putBoolean("details", value.showDetails)
                putBoolean("scores", value.showScores); putBoolean("recognizedCount", value.showRecognizedCount)
            }
            if (previous.recognizeFaces != value.recognizeFaces) engine?.reset()
        }
    }

    fun clearNotice() { mutableState.update { it.copy(notice = null) } }
    fun clearError() { mutableState.update { if (it.fatal) it else it.copy(error = null) } }

    override fun onCleared() {
        cameraExecutor.execute { engine?.close() }
        cameraExecutor.shutdown()
    }
}
