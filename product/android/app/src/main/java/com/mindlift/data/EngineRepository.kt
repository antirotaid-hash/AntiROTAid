package com.mindlift.data

import com.mindlift.bridge.Course
import com.mindlift.bridge.CourseProgress
import com.mindlift.bridge.DailyReport
import com.mindlift.bridge.DateInputPreview
import com.mindlift.bridge.ImportPreview
import com.mindlift.bridge.toDateInputPreview
import com.mindlift.bridge.toImportPreview
import com.mindlift.bridge.DayPlan
import com.mindlift.bridge.DnaSummary
import com.mindlift.bridge.DueCard
import com.mindlift.bridge.EngineBridge
import com.mindlift.bridge.Envelope
import com.mindlift.bridge.InterventionResult
import com.mindlift.bridge.Inventory
import com.mindlift.bridge.ReviewOutcome
import com.mindlift.bridge.Settings
import com.mindlift.bridge.UsageResult
import com.mindlift.bridge.CategoryProgressModel
import com.mindlift.bridge.CourseHealthModel
import com.mindlift.bridge.toCategoryProgressModel
import com.mindlift.bridge.DeckQualityModel
import com.mindlift.bridge.LearningPulseSummary
import com.mindlift.bridge.OralExamPackModel
import com.mindlift.bridge.SyllabusModel
import com.mindlift.bridge.UserDashboardModel
import com.mindlift.bridge.toCourseHealthModel
import com.mindlift.bridge.toDeckQualityModel
import com.mindlift.bridge.toLearningPulseSummary
import com.mindlift.bridge.toOralExamPackModel
import com.mindlift.bridge.toSyllabusModel
import com.mindlift.bridge.toUserDashboardModel
import com.mindlift.bridge.toCourse
import com.mindlift.bridge.toCourseProgress
import com.mindlift.bridge.toDailyReport
import com.mindlift.bridge.toDayPlan
import com.mindlift.bridge.toDnaSummary
import com.mindlift.bridge.toDueCards
import com.mindlift.bridge.toInterventionResult
import com.mindlift.bridge.toInventory
import com.mindlift.bridge.toReviewOutcome
import com.mindlift.bridge.toSettings
import com.mindlift.bridge.toUsageResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Result type that carries either typed data or an owner-safe error message. */
sealed interface EngineResult<out T> {
    data class Ok<T>(val value: T) : EngineResult<T>
    data class Err(val message: String, val code: String) : EngineResult<Nothing>
}

private inline fun <T> Envelope.map(transform: (JSONObject) -> T): EngineResult<T> =
    when (this) {
        is Envelope.Success -> EngineResult.Ok(transform(data))
        is Envelope.Error -> EngineResult.Err(message, code)
    }

/**
 * Coroutine-friendly repository over [EngineBridge]. Every method hops to the IO
 * dispatcher because the bridge blocks on the Python interpreter. The UI layer
 * only ever sees [EngineResult] (typed data or a no-shame message).
 */
class EngineRepository(private val engine: EngineBridge) {

    suspend fun doctorOk(): Boolean = withContext(Dispatchers.IO) {
        engine.doctor().isOk
    }

    suspend fun listCourses(): EngineResult<List<Course>> =
        withContext(Dispatchers.IO) {
            engine.call("list_courses").map { data ->
                val arr = data.optJSONArray("courses")
                if (arr == null) emptyList()
                else (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.toCourse() }
            }
        }

    suspend fun createCourse(title: String, examDate: String?): EngineResult<Course> =
        withContext(Dispatchers.IO) { engine.createCourse(title, examDate).map { it.toCourse() } }

    suspend fun dateInputPreview(rawDate: String, locale: String = "ro"): EngineResult<DateInputPreview> =
        withContext(Dispatchers.IO) { engine.dateInputPreview(rawDate, locale).map { it.toDateInputPreview() } }

    suspend fun importPreviewContract(text: String, locale: String = "ro"): EngineResult<ImportPreview> =
        withContext(Dispatchers.IO) { engine.importPreviewContract(text, locale).map { it.toImportPreview() } }

    // --- v10 boost pack -----------------------------------------------------

    suspend fun studyStreak(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.studyStreak(courseId).map { it } }

    suspend fun leechReport(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.leechReport(courseId).map { it } }

    suspend fun dueQueueExplained(courseId: String, limit: Int = 10): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.dueQueueExplained(courseId, limit).map { it } }

    suspend fun weakSpotDrill(courseId: String, maxCards: Int = 12): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.weakSpotDrill(courseId, maxCards).map { it } }

    suspend fun weeklyDigest(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.weeklyDigest(courseId).map { it } }

    suspend fun exportAnkiDeck(courseId: String, fmt: String = "tsv"): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.exportAnkiDeck(courseId, fmt).map { it } }

    suspend fun motivationNudge(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.motivationNudge(courseId).map { it } }

    suspend fun reviewForecast(courseId: String, days: Int = 14): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.reviewForecast(courseId, days).map { it } }

    suspend fun mockExam(courseId: String, questionCount: Int = 15, minutes: Int = 30): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.mockExam(courseId, questionCount, minutes).map { it } }

    suspend fun cardHealth(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.cardHealth(courseId).map { it } }

    suspend fun todayAgenda(): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.todayAgenda().map { it } }

    suspend fun predictedGrade(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.call("predicted_grade", JSONObject().put("course_id", courseId)).map { it } }

    suspend fun achievements(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.call("achievements", JSONObject().put("course_id", courseId)).map { it } }

    suspend fun enrichDeck(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.call("enrich_deck", JSONObject().put("course_id", courseId)).map { it } }

    suspend fun importMaterial(courseId: String, title: String, text: String):
        EngineResult<JSONObject> = withContext(Dispatchers.IO) {
        engine.importMaterial(courseId, title, text).map { it }
    }

    suspend fun importFile(courseId: String, title: String, filename: String, base64Payload: String):
        EngineResult<JSONObject> = withContext(Dispatchers.IO) {
        engine.importFile(courseId, title, filename, base64Payload).map { it }
    }

    suspend fun importAndBuildDeck(courseId: String, title: String, text: String):
        EngineResult<JSONObject> = withContext(Dispatchers.IO) {
        engine.importAndBuildDeck(courseId, title, text).map { it }
    }

    suspend fun importAndBuildDeckFile(courseId: String, title: String, filename: String, base64Payload: String):
        EngineResult<JSONObject> = withContext(Dispatchers.IO) {
        engine.importAndBuildDeckFile(courseId, title, filename, base64Payload).map { it }
    }

    suspend fun buildDna(courseId: String): EngineResult<DnaSummary> =
        withContext(Dispatchers.IO) { engine.buildDna(courseId).map { it.toDnaSummary() } }

    suspend fun buildDeck(courseId: String): EngineResult<Int> =
        withContext(Dispatchers.IO) { engine.buildDeck(courseId).map { it.optInt("cards") } }

    suspend fun nextDue(courseId: String, limit: Int = 10): EngineResult<List<DueCard>> =
        withContext(Dispatchers.IO) { engine.nextDue(courseId, limit).map { it.toDueCards() } }

    suspend fun review(cardId: String, grade: String): EngineResult<ReviewOutcome> =
        withContext(Dispatchers.IO) { engine.review(cardId, grade).map { it.toReviewOutcome() } }


    suspend fun answerReview(cardId: String, answer: String): EngineResult<ReviewOutcome> =
        withContext(Dispatchers.IO) { engine.answerReview(cardId, answer).map { it.toReviewOutcome() } }

    suspend fun markConceptKnown(courseId: String, conceptName: String, score: Double = 0.92): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.markConceptKnown(courseId, conceptName, score).map { it } }

    suspend fun progress(courseId: String): EngineResult<CourseProgress> =
        withContext(Dispatchers.IO) { engine.progress(courseId).map { it.toCourseProgress() } }

    suspend fun buildPlan(courseId: String, minutes: Double): EngineResult<DayPlan> =
        withContext(Dispatchers.IO) { engine.buildPlan(courseId, minutes).map { it.toDayPlan() } }

    suspend fun buildAdaptivePlan(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.buildAdaptivePlan(courseId).map { it } }

    suspend fun timeStatus(courseId: String?): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.timeStatus(courseId).map { it } }

    suspend fun assessUsage(scenario: String, courseId: String?, studyMinutes: Double):
        EngineResult<UsageResult> = withContext(Dispatchers.IO) {
        engine.assessUsage(scenario, courseId, studyMinutes).map { it.toUsageResult() }
    }

    suspend fun assessUsageSessions(sessions: org.json.JSONArray, courseId: String?,
                                    studyMinutes: Double): EngineResult<UsageResult> =
        withContext(Dispatchers.IO) {
            engine.assessUsageSessions(sessions, courseId, studyMinutes)
                .map { it.toUsageResult() }
        }

    suspend fun decideIntervention(courseId: String, riskLevel: String = "high"): EngineResult<InterventionResult> =
        withContext(Dispatchers.IO) {
            engine.decideIntervention(courseId, riskLevel).map { it.toInterventionResult() }
        }

    suspend fun startBreak(breakId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.startBreak(breakId).map { it } }

    suspend fun advanceBreak(breakId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.advanceBreak(breakId).map { it } }

    suspend fun dailyReport(courseId: String): EngineResult<DailyReport> =
        withContext(Dispatchers.IO) { engine.dailyReport(courseId).map { it.toDailyReport() } }


    suspend fun learningPulse(courseId: String): EngineResult<LearningPulseSummary> =
        withContext(Dispatchers.IO) { engine.learningPulse(courseId).map { it.toLearningPulseSummary() } }

    suspend fun logStudyMinutes(courseId: String, minutes: Double): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.logStudyMinutes(courseId, minutes).map { it } }

    suspend fun logUsageBlock(minutes: Double, category: String, packageName: String, courseId: String?):
        EngineResult<JSONObject> = withContext(Dispatchers.IO) {
        engine.logUsageBlock(minutes, category, packageName, courseId).map { it }
    }

    suspend fun courseHome(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.courseHome(courseId).map { it } }

    suspend fun materialPreview(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.materialPreview(courseId).map { it } }

    suspend fun deckQualityReport(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.deckQualityReport(courseId).map { it } }

    suspend fun buildSyllabus(courseId: String): EngineResult<SyllabusModel> =
        withContext(Dispatchers.IO) { engine.buildSyllabus(courseId).map { it.toSyllabusModel() } }

    suspend fun oralExamPack(courseId: String, maxQuestions: Int = 18): EngineResult<OralExamPackModel> =
        withContext(Dispatchers.IO) { engine.oralExamPack(courseId, maxQuestions).map { it.toOralExamPackModel() } }

    suspend fun deckQualityV2(courseId: String): EngineResult<DeckQualityModel> =
        withContext(Dispatchers.IO) { engine.deckQualityV2(courseId).map { it.toDeckQualityModel() } }

    suspend fun learnerProfile(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.learnerProfile(courseId).map { it } }

    suspend fun courseHealth(courseId: String): EngineResult<CourseHealthModel> =
        withContext(Dispatchers.IO) { engine.courseHealth(courseId).map { it.toCourseHealthModel() } }

    suspend fun categoryProgress(courseId: String): EngineResult<CategoryProgressModel> =
        withContext(Dispatchers.IO) { engine.categoryProgress(courseId).map { it.toCategoryProgressModel() } }

    suspend fun deleteCourse(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.deleteCourse(courseId).map { it } }

    suspend fun setExamDate(courseId: String, examDate: String?): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.setExamDate(courseId, examDate).map { it } }

    suspend fun userDashboard(courseId: String): EngineResult<UserDashboardModel> =
        withContext(Dispatchers.IO) { engine.userDashboard(courseId).map { it.toUserDashboardModel() } }


    suspend fun uploadDiagnosis(filename: String, base64Payload: String? = null, ownerHint: String? = null): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.uploadDiagnosis(filename, base64Payload, ownerHint).map { it } }

    suspend fun lectureMap(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.lectureMap(courseId).map { it } }

    suspend fun evidenceMap(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.evidenceMap(courseId).map { it } }

    suspend fun conceptLadder(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.conceptLadder(courseId).map { it } }

    suspend fun compileSmartDeck(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.compileSmartDeck(courseId).map { it } }

    suspend fun examModePackV2(courseId: String, mode: String = "oral"): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.examModePack(courseId, mode).map { it } }

    suspend fun coachAnswer(courseId: String, question: String, answer: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.coachAnswer(courseId, question, answer).map { it } }

    suspend fun socraticHint(courseId: String, conceptName: String, attempt: String = ""): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.socraticHint(courseId, conceptName, attempt).map { it } }

    suspend fun revisionPack(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.revisionPack(courseId).map { it } }

    suspend fun attentionForecast(courseId: String?): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.attentionForecast(courseId).map { it } }

    suspend fun autopilotV2(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.autopilotV2(courseId).map { it } }

    suspend fun examCountdownPlan(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.examCountdownPlan(courseId).map { it } }

    suspend fun courseKpis(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.courseKpis(courseId).map { it } }

    suspend fun productHub(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.productHub(courseId).map { it } }

    suspend fun exportStudyPack(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.exportStudyPack(courseId).map { it } }

    suspend fun inventory(): EngineResult<Inventory> =
        withContext(Dispatchers.IO) { engine.inventory().map { it.toInventory() } }

    suspend fun export(destination: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.export(destination).map { it } }

    suspend fun wipe(): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.wipe().map { it } }

    suspend fun settings(): EngineResult<Settings> =
        withContext(Dispatchers.IO) { engine.getSettings().map { it.toSettings() } }

    suspend fun updateSettings(changes: JSONObject): EngineResult<Settings> =
        withContext(Dispatchers.IO) { engine.updateSettings(changes).map { it.toSettings() } }
    suspend fun lectureMapV3(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.lectureMapV3(courseId).map { it } }

    suspend fun ocrGate(filename: String, base64Payload: String? = null, mime: String? = null): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.ocrGate(filename, base64Payload, mime).map { it } }

    suspend fun importWorkbench(items: org.json.JSONArray): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.importWorkbench(items).map { it } }

    suspend fun importWorkbenchScreen(items: org.json.JSONArray): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.importWorkbenchScreen(items).map { it } }

    suspend fun recoveryLoop(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.recoveryLoop(courseId).map { it } }

    suspend fun permissionReadiness(status: JSONObject): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.permissionReadiness(status).map { it } }

    suspend fun manualComplianceReport(): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.manualComplianceReport().map { it } }

    suspend fun releaseGateReport(buyerClean: Boolean = false): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.releaseGateReport(buyerClean).map { it } }

    // --- life modes: war on doomscrolling beyond exams (full flavor) ---------

    suspend fun lifeStatus(): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.lifeStatus().map { it } }

    suspend fun lifeSetMode(mode: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.lifeSetMode(mode).map { it } }

    suspend fun lifeSetSleepWindow(bedtime: String, wake: String, tzOffsetMinutes: Int):
        EngineResult<JSONObject> = withContext(Dispatchers.IO) {
        engine.lifeSetSleepWindow(bedtime, wake, tzOffsetMinutes).map { it }
    }

    suspend fun lifeAddBill(title: String, dueAt: String, amount: String = "",
                            recurring: String = ""): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) {
            engine.lifeAddBill(title, dueAt, amount, recurring).map { it }
        }

    suspend fun lifePayBill(billId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.lifePayBill(billId).map { it } }

    suspend fun lifeRemoveBill(billId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.lifeRemoveBill(billId).map { it } }

    suspend fun lifeSyncMeetings(meetings: org.json.JSONArray): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.lifeSyncMeetings(meetings).map { it } }

    suspend fun lifeSyncEvents(events: org.json.JSONArray): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.lifeSyncEvents(events).map { it } }

    suspend fun lifeOverlayDecision(phonePressure: Double, examProximity: Double = 0.0,
                                    examCount: Int = 0, scrolledMinutesToday: Double = 0.0):
        EngineResult<JSONObject> = withContext(Dispatchers.IO) {
        engine.lifeOverlayDecision(phonePressure, examProximity, examCount,
            scrolledMinutesToday).map { it }
    }

    suspend fun lifeSetContract(minutes: Int): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.lifeSetContract(minutes).map { it } }

    suspend fun lifeContractStatus(scrolledMinutes: Double): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.lifeContractStatus(scrolledMinutes).map { it } }

    suspend fun lifeRecordOverlayEvent(event: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.lifeRecordOverlayEvent(event).map { it } }

    suspend fun lifeSleepDebt(): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.lifeSleepDebt().map { it } }

    suspend fun lifeSetVacation(until: String?): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.lifeSetVacation(until).map { it } }

    suspend fun lifeWarReport(): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.lifeWarReport().map { it } }

    suspend fun lifeWallet(): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.lifeWallet().map { it } }

    suspend fun lifeTakeLoan(minutes: Int): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.lifeTakeLoan(minutes).map { it } }

    suspend fun lifeSetPanic(until: String?): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.lifeSetPanic(until).map { it } }

    suspend fun lifeSetFocusWindow(start: String?, end: String?, tzOffsetMinutes: Int):
        EngineResult<JSONObject> = withContext(Dispatchers.IO) {
        engine.lifeSetFocusWindow(start, end, tzOffsetMinutes).map { it }
    }

    // --- insights subsystem -------------------------------------------------

    suspend fun examReadiness(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.examReadiness(courseId).map { it } }

    suspend fun knowledgeGaps(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.knowledgeGaps(courseId).map { it } }

    suspend fun studyMomentum(): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.studyMomentum().map { it } }

    suspend fun confidenceCalibration(courseId: String?): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.confidenceCalibration(courseId).map { it } }

    suspend fun interleavedSession(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.interleavedSession(courseId).map { it } }

    suspend fun dailyBoard(): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.dailyBoard().map { it } }

    suspend fun distractionTriggers(): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.distractionTriggers().map { it } }



    // --- v9/v12 source-10 local hardening ----------------------------------

    suspend fun runtimeGuardV9(): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.runtimeGuardV9().map { it } }

    suspend fun promptRegistryV9(): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.promptRegistryV9().map { it } }

    suspend fun aiPipelineV9(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.aiPipelineV9(courseId).map { it } }

    suspend fun courseBlueprintV9(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.courseBlueprintV9(courseId).map { it } }

    suspend fun finalExamControllerV9(courseId: String): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.finalExamControllerV9(courseId).map { it } }

    suspend fun productConsoleV9(courseId: String?): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.productConsoleV9(courseId).map { it } }

    suspend fun ownerStartPacketV9(): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.ownerStartPacketV9().map { it } }

    suspend fun claudeHandoffV9(): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.claudeHandoffV9().map { it } }

    suspend fun source10GateV9(): EngineResult<JSONObject> =
        withContext(Dispatchers.IO) { engine.source10GateV9().map { it } }

}
