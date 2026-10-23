package com.mindlift.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mindlift.bridge.CourseHealthModel
import com.mindlift.bridge.CourseProgress
import com.mindlift.bridge.DailyReport
import com.mindlift.bridge.DayPlan
import com.mindlift.bridge.DeckQualityModel
import com.mindlift.bridge.DnaSummary
import com.mindlift.bridge.DueCard
import com.mindlift.bridge.ImportPreview
import com.mindlift.bridge.LearningPulseSummary
import com.mindlift.bridge.OralExamPackModel
import com.mindlift.bridge.SyllabusModel
import com.mindlift.bridge.UserDashboardModel
import com.mindlift.bridge.toDnaSummary
import com.mindlift.data.EngineRepository
import com.mindlift.data.EngineResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Per-course product state. This is no longer only import/deck/review: it wires
 * the larger Python engine into Android surfaces: dashboard, syllabus, deck
 * quality, oral exam mode, learning pulse, time logging, and daily report.
 */
class CourseViewModel(
    private val repo: EngineRepository,
    private val courseId: String,
) : ViewModel() {

    data class State(
        val busy: Boolean = false,
        val message: String? = null,
        val dna: DnaSummary? = null,
        val deckSize: Int = 0,
        val due: List<DueCard> = emptyList(),
        val currentIndex: Int = 0,
        val revealed: Boolean = false,
        val progress: CourseProgress? = null,
        val plan: DayPlan? = null,
        val report: DailyReport? = null,
        val pulse: LearningPulseSummary? = null,
        val syllabus: SyllabusModel? = null,
        val oralPack: OralExamPackModel? = null,
        val deckQuality: DeckQualityModel? = null,
        val health: CourseHealthModel? = null,
        val dashboard: UserDashboardModel? = null,
        val materialPreview: JSONObject? = null,
        val learnerProfile: JSONObject? = null,
        val lectureMap: JSONObject? = null,
        val evidenceMap: JSONObject? = null,
        val conceptLadder: JSONObject? = null,
        val smartDeck: JSONObject? = null,
        val examModePack: JSONObject? = null,
        val answerCoach: JSONObject? = null,
        val socraticHint: JSONObject? = null,
        val revisionPack: JSONObject? = null,
        val attentionForecast: JSONObject? = null,
        val autopilotV2: JSONObject? = null,
        val countdownPlan: JSONObject? = null,
        val kpis: JSONObject? = null,
        val productHub: JSONObject? = null,
        val studyPack: JSONObject? = null,
        val lectureMapV3: JSONObject? = null,
        val ocrGate: JSONObject? = null,
        val importWorkbench: JSONObject? = null,
        val importWorkbenchScreen: JSONObject? = null,
        val recoveryLoop: JSONObject? = null,
        val permissionReadiness: JSONObject? = null,
        val manualCompliance: JSONObject? = null,
        val releaseGate: JSONObject? = null,
        val examReadiness: JSONObject? = null,
        val knowledgeGaps: JSONObject? = null,
        val studyMomentum: JSONObject? = null,
        val calibration: JSONObject? = null,
        val interleavedSession: JSONObject? = null,
        val dailyBoard: JSONObject? = null,
        val distractionTriggers: JSONObject? = null,
        /** Set when a paste looks mostly like logistics; the user must confirm. */
        val importWarning: ImportPreview? = null,
        // v10 boost pack
        val streak: JSONObject? = null,
        val leech: JSONObject? = null,
        val digest: JSONObject? = null,
        val weakDrill: JSONObject? = null,
        val ankiExport: JSONObject? = null,
        val nudge: JSONObject? = null,
        val predictedGrade: JSONObject? = null,
        val achievements: JSONObject? = null,
        /** card_id -> "why this card now?" one-line reason. */
        val whyNow: Map<String, String> = emptyMap(),
    )

    /** Paste held back by the import preflight, awaiting an explicit confirm. */
    private var pendingImportText: String? = null

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val currentCard: DueCard? get() = _state.value.due.getOrNull(_state.value.currentIndex)

    // On open, load the product home AND the deck/progress so the command center
    // shows the real card count for an existing course (not just after an import).
    init { refreshProductHome(); refreshProgress(); refreshDue() }

    private fun <T> handle(r: EngineResult<T>, onOk: (T) -> Unit) = when (r) {
        is EngineResult.Ok -> { _state.update { it.copy(busy = false) }; onOk(r.value) }
        is EngineResult.Err -> _state.update { it.copy(busy = false, message = r.message) }
    }

    fun refreshProductHome() = viewModelScope.launch {
        _state.update { it.copy(busy = true, message = null) }
        handle(repo.userDashboard(courseId)) { dash -> _state.update { it.copy(dashboard = dash) } }
        when (val h = repo.courseHealth(courseId)) {
            is EngineResult.Ok -> _state.update { it.copy(health = h.value) }
            is EngineResult.Err -> Unit
        }
        when (val pulse = repo.learningPulse(courseId)) {
            is EngineResult.Ok -> _state.update { it.copy(pulse = pulse.value) }
            is EngineResult.Err -> Unit
        }
        when (val hub = repo.productHub(courseId)) {
            is EngineResult.Ok -> _state.update { it.copy(productHub = hub.value) }
            is EngineResult.Err -> Unit
        }
        when (val kpis = repo.courseKpis(courseId)) {
            is EngineResult.Ok -> _state.update { it.copy(kpis = kpis.value) }
            is EngineResult.Err -> Unit
        }
        loadBoost()
    }

    /** v10 boost pack: streak, leech, weekly digest — best-effort, never blocks. */
    fun loadBoost() = viewModelScope.launch {
        when (val s = repo.studyStreak(courseId)) {
            is EngineResult.Ok -> _state.update { it.copy(streak = s.value) }
            is EngineResult.Err -> Unit
        }
        when (val l = repo.leechReport(courseId)) {
            is EngineResult.Ok -> _state.update { it.copy(leech = l.value) }
            is EngineResult.Err -> Unit
        }
        when (val d = repo.weeklyDigest(courseId)) {
            is EngineResult.Ok -> _state.update { it.copy(digest = d.value) }
            is EngineResult.Err -> Unit
        }
        when (val n = repo.motivationNudge(courseId)) {
            is EngineResult.Ok -> _state.update { it.copy(nudge = n.value) }
            is EngineResult.Err -> Unit
        }
        when (val g = repo.predictedGrade(courseId)) {
            is EngineResult.Ok -> _state.update { it.copy(predictedGrade = g.value) }
            is EngineResult.Err -> Unit
        }
        when (val ach = repo.achievements(courseId)) {
            is EngineResult.Ok -> _state.update { it.copy(achievements = ach.value) }
            is EngineResult.Err -> Unit
        }
    }

    /** Persist extra card types (cloze/reverse/true-false) into the deck + SRS. */
    fun enrichDeck() = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        when (val r = repo.enrichDeck(courseId)) {
            is EngineResult.Ok -> {
                _state.update { it.copy(busy = false,
                    message = "Deck imbogatit: +${r.value.optInt("added")} carduri (cloze/inverse/adevarat-fals).") }
                refreshAfterDeckChange()
            }
            is EngineResult.Err -> _state.update { it.copy(busy = false, message = r.message) }
        }
    }

    fun loadWeakDrill() = viewModelScope.launch {
        when (val d = repo.weakSpotDrill(courseId)) {
            is EngineResult.Ok -> _state.update { it.copy(weakDrill = d.value) }
            is EngineResult.Err -> _state.update { it.copy(message = d.message) }
        }
    }

    fun exportAnki() = viewModelScope.launch {
        when (val a = repo.exportAnkiDeck(courseId)) {
            is EngineResult.Ok -> _state.update {
                it.copy(ankiExport = a.value,
                        message = "Export Anki gata: ${a.value.optInt("card_count")} carduri.")
            }
            is EngineResult.Err -> _state.update { it.copy(message = a.message) }
        }
    }

    fun setExamDate(examDate: String) = viewModelScope.launch {
        repo.setExamDate(courseId, examDate.ifBlank { null })
        _state.update { it.copy(message = if (examDate.isBlank()) "Data examen stearsa." else "Examen setat: $examDate") }
    }

    fun importMaterial(text: String) = viewModelScope.launch {
        if (text.isBlank()) return@launch
        _state.update { it.copy(busy = true, message = null, importWarning = null) }
        // Preflight: if the paste is mostly admin/logistics, hold back and warn
        // instead of turning a syllabus cover page into cards. A failed preflight
        // never blocks the import — we just proceed to build.
        when (val pre = repo.importPreviewContract(text)) {
            is EngineResult.Ok -> if (!pre.value.canGenerateCards) {
                pendingImportText = text
                _state.update { it.copy(busy = false, importWarning = pre.value) }
                return@launch
            }
            is EngineResult.Err -> Unit
        }
        applyImport(repo.importAndBuildDeck(courseId, "Material", text))
    }

    /** User chose "generate anyway" after the logistics warning. */
    fun confirmImport() = viewModelScope.launch {
        val text = pendingImportText ?: return@launch
        pendingImportText = null
        _state.update { it.copy(busy = true, message = null, importWarning = null) }
        applyImport(repo.importAndBuildDeck(courseId, "Material", text))
    }

    fun dismissImportWarning() {
        pendingImportText = null
        _state.update { it.copy(importWarning = null) }
    }

    /** Import an uploaded file (PPTX/PDF/DOCX/TXT...) as base64 and build the deck. */
    fun importFile(filename: String, base64Payload: String) = viewModelScope.launch {
        if (base64Payload.isBlank()) return@launch
        _state.update { it.copy(busy = true, message = null) }
        applyImport(repo.importAndBuildDeckFile(courseId, filename, filename, base64Payload))
    }

    private fun applyImport(imp: EngineResult<JSONObject>) = when (imp) {
        is EngineResult.Err -> _state.update { it.copy(busy = false, message = imp.message) }
        is EngineResult.Ok -> {
            // import_and_build_deck already built the DNA and the deck in one pass.
            // Do NOT rebuild the DNA here: a rebuild replaces the concept rows, and
            // the cards' ON DELETE CASCADE foreign key would wipe the freshly built
            // deck. Use the DNA summary returned by the import.
            val dna = imp.value.optJSONObject("dna")?.toDnaSummary()
            val deck = imp.value.optJSONObject("deck")
            _state.update {
                it.copy(busy = false, dna = dna ?: it.dna,
                        deckSize = deck?.optInt("cards") ?: it.deckSize)
            }
            refreshAfterDeckChange()
        }
    }

    private fun refreshAfterDeckChange() {
        refreshDue()
        refreshProgress()
        refreshSyllabus()
        refreshQuality()
        refreshPulse()
        refreshProductHome()
    }

    fun refreshDue() = viewModelScope.launch {
        handle(repo.nextDue(courseId, limit = 20)) { cards ->
            _state.update { it.copy(due = cards, currentIndex = 0, revealed = false) }
        }
        // "Why this card now?" reasons, mapped by card id (best-effort).
        when (val w = repo.dueQueueExplained(courseId, limit = 20)) {
            is EngineResult.Ok -> {
                val arr = w.value.optJSONArray("explained")
                val map = buildMap {
                    if (arr != null) for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        put(o.optString("card_id"), o.optString("primary_reason"))
                    }
                }
                _state.update { it.copy(whyNow = map) }
            }
            is EngineResult.Err -> Unit
        }
    }

    fun reveal() = _state.update { it.copy(revealed = true) }

    fun grade(grade: String) = viewModelScope.launch {
        val card = currentCard ?: return@launch
        _state.update { it.copy(busy = true) }
        handle(repo.review(card.cardId, grade)) {
            // Spaced repetition within the session: a card you didn't really know
            // (again/hard) is re-queued to the end so it comes back this sitting —
            // a card is shown several times until it sticks. good/easy graduate it.
            val repeat = grade == "again" || grade == "hard"
            _state.update { s ->
                val due = if (repeat) s.due + card else s.due
                s.copy(due = due, currentIndex = s.currentIndex + 1, revealed = false)
            }
            refreshProgress(); refreshPulse(); loadBoost()
        }
    }

    fun answerFreeText(answer: String) = viewModelScope.launch {
        val card = currentCard ?: return@launch
        _state.update { it.copy(busy = true) }
        handle(repo.answerReview(card.cardId, answer)) {
            _state.update { s -> s.copy(currentIndex = s.currentIndex + 1, revealed = false) }
            refreshProgress(); refreshPulse()
        }
    }

    fun refreshProgress() = viewModelScope.launch {
        // card_count from progress is the authoritative deck size for an opened
        // course, so keep the command-center "Carduri" stat in sync with it.
        handle(repo.progress(courseId)) { p -> _state.update { it.copy(progress = p, deckSize = p.cardCount) } }
    }

    fun refreshPulse() = viewModelScope.launch {
        handle(repo.learningPulse(courseId)) { p -> _state.update { it.copy(pulse = p) } }
    }

    fun refreshSyllabus() = viewModelScope.launch {
        handle(repo.buildSyllabus(courseId)) { s -> _state.update { it.copy(syllabus = s) } }
    }

    fun refreshOralExam() = viewModelScope.launch {
        handle(repo.oralExamPack(courseId, 18)) { p -> _state.update { it.copy(oralPack = p) } }
    }

    fun refreshQuality() = viewModelScope.launch {
        handle(repo.deckQualityV2(courseId)) { q -> _state.update { it.copy(deckQuality = q) } }
    }

    fun refreshMaterialPreview() = viewModelScope.launch {
        handle(repo.materialPreview(courseId)) { p -> _state.update { it.copy(materialPreview = p) } }
    }

    fun refreshLearnerProfile() = viewModelScope.launch {
        handle(repo.learnerProfile(courseId)) { p -> _state.update { it.copy(learnerProfile = p) } }
    }

    fun buildPlan(minutes: Double = 15.0) = viewModelScope.launch {
        _state.update { it.copy(busy = true, message = null) }
        handle(repo.buildPlan(courseId, minutes)) { p -> _state.update { it.copy(plan = p) } }
    }

    fun buildReport() = viewModelScope.launch {
        _state.update { it.copy(busy = true, message = null) }
        handle(repo.dailyReport(courseId)) { r -> _state.update { it.copy(report = r) } }
    }

    fun logStudyBlock(minutes: Double) = viewModelScope.launch {
        _state.update { it.copy(busy = true, message = null) }
        handle(repo.logStudyMinutes(courseId, minutes)) {
            _state.update { it.copy(message = "Study block logged: ${minutes.toInt()} min") }
            refreshPulse(); refreshProductHome()
        }
    }

    fun logLostTime(minutes: Double) = viewModelScope.launch {
        _state.update { it.copy(busy = true, message = null) }
        handle(repo.logUsageBlock(minutes, "high_pull", "owner.reported", courseId)) {
            _state.update { it.copy(message = "Lost time logged: ${minutes.toInt()} min. Pulse recalibrated.") }
            refreshPulse(); refreshProductHome()
        }
    }

    fun markKnown(concept: String) = viewModelScope.launch {
        if (concept.isBlank()) return@launch
        _state.update { it.copy(busy = true, message = null) }
        handle(repo.markConceptKnown(courseId, concept, 0.94)) { _ ->
            _state.update { it.copy(message = "$concept marked known. Pulse recalibrated.") }
            refreshPulse(); refreshProgress()
        }
    }


    fun refreshLectureMap() = viewModelScope.launch {
        handle(repo.lectureMap(courseId)) { p -> _state.update { it.copy(lectureMap = p) } }
    }

    fun refreshEvidenceMap() = viewModelScope.launch {
        handle(repo.evidenceMap(courseId)) { p -> _state.update { it.copy(evidenceMap = p) } }
    }

    fun refreshConceptLadder() = viewModelScope.launch {
        handle(repo.conceptLadder(courseId)) { p -> _state.update { it.copy(conceptLadder = p) } }
    }

    fun refreshSmartDeck() = viewModelScope.launch {
        handle(repo.compileSmartDeck(courseId)) { p -> _state.update { it.copy(smartDeck = p) } }
    }

    fun refreshExamModePack(mode: String = "oral") = viewModelScope.launch {
        handle(repo.examModePackV2(courseId, mode)) { p -> _state.update { it.copy(examModePack = p) } }
    }

    fun coachAnswer(question: String, answer: String) = viewModelScope.launch {
        if (question.isBlank() || answer.isBlank()) return@launch
        _state.update { it.copy(busy = true, message = null) }
        handle(repo.coachAnswer(courseId, question, answer)) { p -> _state.update { it.copy(answerCoach = p) } }
    }

    fun refreshSocraticHint(conceptName: String, attempt: String = "") = viewModelScope.launch {
        if (conceptName.isBlank()) return@launch
        handle(repo.socraticHint(courseId, conceptName, attempt)) { p -> _state.update { it.copy(socraticHint = p) } }
    }

    fun refreshRevisionPack() = viewModelScope.launch {
        handle(repo.revisionPack(courseId)) { p -> _state.update { it.copy(revisionPack = p) } }
    }

    fun refreshAttentionForecast() = viewModelScope.launch {
        handle(repo.attentionForecast(courseId)) { p -> _state.update { it.copy(attentionForecast = p) } }
    }

    fun refreshAutopilotV2() = viewModelScope.launch {
        handle(repo.autopilotV2(courseId)) { p -> _state.update { it.copy(autopilotV2 = p) } }
    }

    fun refreshCountdownPlan() = viewModelScope.launch {
        handle(repo.examCountdownPlan(courseId)) { p -> _state.update { it.copy(countdownPlan = p) } }
    }

    fun refreshStudyPack() = viewModelScope.launch {
        handle(repo.exportStudyPack(courseId)) { p -> _state.update { it.copy(studyPack = p) } }
    }


    fun refreshLectureMapV3() = viewModelScope.launch {
        handle(repo.lectureMapV3(courseId)) { p -> _state.update { it.copy(lectureMapV3 = p) } }
    }

    fun diagnoseOcr(filename: String) = viewModelScope.launch {
        if (filename.isBlank()) return@launch
        handle(repo.ocrGate(filename)) { p -> _state.update { it.copy(ocrGate = p) } }
    }

    fun refreshImportWorkbench(filename: String) = viewModelScope.launch {
        if (filename.isBlank()) return@launch
        val arr = org.json.JSONArray().put(org.json.JSONObject().put("filename", filename))
        handle(repo.importWorkbench(arr)) { p -> _state.update { it.copy(importWorkbench = p) } }
        handle(repo.importWorkbenchScreen(arr)) { p -> _state.update { it.copy(importWorkbenchScreen = p) } }
    }

    fun refreshRecoveryLoop() = viewModelScope.launch {
        handle(repo.recoveryLoop(courseId)) { p -> _state.update { it.copy(recoveryLoop = p) } }
    }

    fun refreshPermissionReadiness() = viewModelScope.launch {
        val status = org.json.JSONObject()
        handle(repo.permissionReadiness(status)) { p -> _state.update { it.copy(permissionReadiness = p) } }
    }

    fun refreshCompliance() = viewModelScope.launch {
        handle(repo.manualComplianceReport()) { p -> _state.update { it.copy(manualCompliance = p) } }
        handle(repo.releaseGateReport(false)) { p -> _state.update { it.copy(releaseGate = p) } }
    }

    // --- insights subsystem -------------------------------------------------

    fun refreshInsights() = viewModelScope.launch {
        _state.update { it.copy(busy = true, message = null) }
        handle(repo.examReadiness(courseId)) { p -> _state.update { it.copy(examReadiness = p) } }
        handle(repo.knowledgeGaps(courseId)) { p -> _state.update { it.copy(knowledgeGaps = p) } }
        handle(repo.studyMomentum()) { p -> _state.update { it.copy(studyMomentum = p) } }
        handle(repo.confidenceCalibration(courseId)) { p -> _state.update { it.copy(calibration = p) } }
        handle(repo.interleavedSession(courseId)) { p -> _state.update { it.copy(interleavedSession = p) } }
        handle(repo.dailyBoard()) { p -> _state.update { it.copy(dailyBoard = p) } }
        handle(repo.distractionTriggers()) { p -> _state.update { it.copy(distractionTriggers = p) } }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    companion object {
        fun factory(repo: EngineRepository, courseId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CourseViewModel(repo, courseId) as T
        }
    }
}
