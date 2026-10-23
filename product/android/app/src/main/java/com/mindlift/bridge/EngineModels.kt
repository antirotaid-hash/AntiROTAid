package com.mindlift.bridge

import org.json.JSONArray
import org.json.JSONObject

/**
 * Kotlin data classes mirroring the engine's `data` payloads, plus parsers from
 * [JSONObject]. These are the shapes documented in the bridge contract; keeping
 * them in one place makes the UI independent of raw JSON.
 */

data class Course(val id: String, val title: String, val examDate: String?)

fun JSONObject.toCourse() = Course(
    id = optString("course_id"),
    title = optString("title"),
    examDate = if (isNull("exam_date")) null else optString("exam_date"),
)

data class DnaSummary(
    val courseId: String,
    val version: Int,
    val conceptCount: Int,
    val topicCount: Int,
    val edgeCount: Int,
    val topics: List<String>,
    val bridgeConcepts: List<String>,
)

fun JSONObject.toDnaSummary() = DnaSummary(
    courseId = optString("course_id"),
    version = optInt("version"),
    conceptCount = optInt("concept_count"),
    topicCount = optInt("topic_count"),
    edgeCount = optInt("edge_count"),
    topics = optJSONArray("topics").toStringList(),
    bridgeConcepts = optJSONArray("bridge_concepts").toStringList(),
)

data class DueCard(val cardId: String, val kind: String, val prompt: String,
                   val answer: String = "")

fun JSONObject.toDueCards(): List<DueCard> =
    optJSONArray("due").toObjectList().map {
        DueCard(it.optString("card_id"), it.optString("kind"), it.optString("prompt"),
            it.optString("answer"))
    }

data class ReviewOutcome(
    val cardId: String,
    val grade: String,
    val intervalDays: Double,
    val repetitions: Int,
    val band: String,
    val score: Double,
)

fun JSONObject.toReviewOutcome() = ReviewOutcome(
    cardId = optString("card_id"),
    grade = optString("grade"),
    intervalDays = optDouble("interval_days"),
    repetitions = optInt("repetitions"),
    band = optString("band"),
    score = optDouble("score"),
)

data class CourseProgress(
    val conceptCount: Int,
    val cardCount: Int,
    val dueCount: Int,
    val studiedRatio: Double,
    val avgScore: Double,
    val bandCounts: Map<String, Int>,
)

fun JSONObject.toCourseProgress(): CourseProgress {
    val bands = mutableMapOf<String, Int>()
    optJSONObject("band_counts")?.let { bc ->
        bc.keys().forEach { k -> bands[k] = bc.optInt(k) }
    }
    return CourseProgress(
        conceptCount = optInt("concept_count"),
        cardCount = optInt("card_count"),
        dueCount = optInt("due_count"),
        studiedRatio = optDouble("studied_ratio"),
        avgScore = optDouble("avg_score"),
        bandCounts = bands,
    )
}

data class PlanStep(val concept: String, val kind: String, val prompt: String,
                    val estMinutes: Double, val reason: String)

data class DayPlan(
    val mode: String,
    val message: String,
    val totalMinutes: Double,
    val steps: List<PlanStep>,
)

fun JSONObject.toDayPlan(): DayPlan {
    val strategy = optJSONObject("strategy") ?: JSONObject()
    val next = optJSONObject("next_steps") ?: JSONObject()
    val steps = next.optJSONArray("steps").toObjectList().map {
        PlanStep(
            concept = it.optString("concept"),
            kind = it.optString("kind"),
            prompt = it.optString("prompt"),
            estMinutes = it.optDouble("est_minutes"),
            reason = it.optString("reason"),
        )
    }
    return DayPlan(
        mode = strategy.optString("mode"),
        message = strategy.optString("message"),
        totalMinutes = next.optDouble("total_minutes"),
        steps = steps,
    )
}

data class RiskSummary(val score: Double, val level: String, val reasons: List<String>)

data class UsageResult(
    val sessions: Int,
    val pullSessions: Int,
    val risk: RiskSummary,
    val features: Map<String, Double>,
)

fun JSONObject.toUsageResult(): UsageResult {
    val riskObj = optJSONObject("risk") ?: JSONObject()
    val feats = mutableMapOf<String, Double>()
    optJSONObject("features")?.let { f -> f.keys().forEach { feats[it] = f.optDouble(it) } }
    return UsageResult(
        sessions = optInt("sessions"),
        pullSessions = optInt("pull_sessions"),
        risk = RiskSummary(
            score = riskObj.optDouble("score"),
            level = riskObj.optString("level"),
            reasons = riskObj.optJSONArray("reasons").toStringList(),
        ),
        features = feats,
    )
}

data class OverlayChoice(val id: String, val label: String, val kind: String)

data class OverlayPayload(
    val surface: String,
    val level: String,
    val title: String,
    val message: String,
    val reasons: List<String>,
    val aggressive: Boolean,
    val microtaskConcept: String?,
    val microtaskTheory: String?,
    val microtaskPrompt: String?,
    val microtaskAnswer: String?,
    val microtaskCardId: String?,
    val microtaskMode: String,        // "teach" = read-only theory; "test" = quiz
    val studyPhase: String,           // teach | test | done
    val studyCoverage: Double,        // materie parcursa (0..1)
    val studyUnderstanding: Double,   // inteles de mine (0..1)
    val breakSeconds: Int,
    val breakId: String?,
    val choices: List<OverlayChoice>,
    val cadenceGapSec: Int,
)

data class InterventionResult(
    val decision: String,
    val reason: String,
    val suppressed: Boolean,
    val breakId: String?,
    val overlay: OverlayPayload?,
)

fun JSONObject.toInterventionResult(): InterventionResult {
    val ov = optJSONObject("overlay")
    val overlay = ov?.let {
        val mt = it.optJSONObject("microtask")
        val study = it.optJSONObject("study")
        OverlayPayload(
            surface = it.optString("surface"),
            level = it.optString("level"),
            title = it.optString("title"),
            message = it.optString("message"),
            reasons = it.optJSONArray("reasons").toStringList(),
            aggressive = it.optBoolean("aggressive", false),
            microtaskConcept = mt?.optString("concept"),
            microtaskTheory = mt?.optString("theory"),
            microtaskPrompt = mt?.optString("prompt"),
            microtaskAnswer = mt?.optString("answer"),
            microtaskCardId = mt?.optString("card_id"),
            microtaskMode = mt?.optString("mode")?.ifEmpty { "test" } ?: "test",
            studyPhase = study?.optString("phase")?.ifEmpty { "test" } ?: "test",
            studyCoverage = study?.optDouble("coverage", 0.0) ?: 0.0,
            studyUnderstanding = study?.optDouble("understanding", 0.0) ?: 0.0,
            breakSeconds = it.optJSONObject("break_offer")?.optInt("duration_sec") ?: 300,
            breakId = it.optString("break_id").ifEmpty { null },
            choices = it.optJSONArray("choices").toObjectList().map { c ->
                OverlayChoice(c.optString("id"), c.optString("label"), c.optString("kind"))
            },
            cadenceGapSec = it.optJSONObject("cadence")?.optInt("gap_seconds") ?: 60,
        )
    }
    return InterventionResult(
        decision = optString("decision"),
        reason = optString("reason"),
        suppressed = optBoolean("suppressed"),
        breakId = if (isNull("break_id")) null else optString("break_id"),
        overlay = overlay,
    )
}

data class DailyReport(
    val localDay: String,
    val narrative: String,
    val tomorrowFocus: String,
    val reviewsToday: Int,
    val dueCount: Int,
)

fun JSONObject.toDailyReport(): DailyReport {
    val metrics = optJSONObject("metrics") ?: JSONObject()
    val study = metrics.optJSONObject("study") ?: JSONObject()
    return DailyReport(
        localDay = optString("local_day"),
        narrative = optString("narrative"),
        tomorrowFocus = optString("tomorrow_focus"),
        reviewsToday = study.optInt("reviews_today"),
        dueCount = study.optInt("due"),
    )
}

data class Inventory(val totalRows: Int, val sizeBytes: Long, val perTable: Map<String, Int>)

fun JSONObject.toInventory(): Inventory {
    val per = mutableMapOf<String, Int>()
    optJSONObject("per_table")?.let { p -> p.keys().forEach { per[it] = p.optInt(it) } }
    return Inventory(optInt("total_rows"), optLong("size_bytes"), per)
}

data class Settings(
    val locale: String,
    val networkEnabled: Boolean,
    val quietHoursStart: Int,
    val quietHoursEnd: Int,
    val interventionCadenceMin: Int,
    val dailyTimeBudgetMin: Int,
)

fun JSONObject.toSettings() = Settings(
    locale = optString("locale", "ro"),
    networkEnabled = optBoolean("network_enabled", false),
    quietHoursStart = optInt("quiet_hours_start", 23),
    quietHoursEnd = optInt("quiet_hours_end", 7),
    interventionCadenceMin = optInt("intervention_cadence_min", 45),
    dailyTimeBudgetMin = optInt("daily_time_budget_min", 15),
)

// --- small JSON helpers -----------------------------------------------------

data class CategoryProgressCategory(
    val sectionId: String,
    val title: String,
    val coveragePercent: Double,
    val reviewedPercent: Double,
    val learnedPercent: Double,
    val readiness: Double,
    val color: String,
    val status: String,
    val notificationLine: String,
    val concepts: List<String>,
    val cards: Int,
    val weak: Int,
)

data class CategoryProgressModel(
    val courseId: String,
    val overallReadiness: Double,
    val categories: List<CategoryProgressCategory>,
)

fun JSONObject.toCategoryProgressModel(): CategoryProgressModel {
    val cats = optJSONArray("categories").toObjectList().map {
        CategoryProgressCategory(
            sectionId = it.optString("section_id"),
            title = it.optString("title"),
            coveragePercent = it.optDouble("coverage_percent"),
            reviewedPercent = it.optDouble("reviewed_percent"),
            learnedPercent = it.optDouble("learned_percent"),
            readiness = it.optDouble("readiness"),
            color = it.optString("color"),
            status = it.optString("status"),
            notificationLine = it.optString("notification_line"),
            concepts = it.optJSONArray("concepts").toStringList(),
            cards = it.optInt("cards"),
            weak = it.optInt("weak"),
        )
    }
    return CategoryProgressModel(
        courseId = optString("course_id"),
        overallReadiness = optDouble("overall_readiness"),
        categories = cats,
    )
}

// --- small JSON helpers -----------------------------------------------------

fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { optString(it) }
}

fun JSONArray?.toObjectList(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it) }
}

data class LearningPulseSummary(
    val remainingMinutes: Double,
    val studiedMinutes: Double,
    val recommendedBlockMinutes: Double,
    val pollAfterMinutes: Int,
    val mode: String,
    val reason: String,
    val readinessScore: Double,
    val weakConcepts: List<String>,
    val masteredConcepts: List<String>,
)

fun JSONObject.toLearningPulseSummary(): LearningPulseSummary {
    val time = optJSONObject("time_status") ?: optJSONObject("time") ?: JSONObject()
    val action = optJSONObject("next_action") ?: optJSONObject("recommendation") ?: JSONObject()
    val state = optJSONObject("learning_state") ?: optJSONObject("state") ?: JSONObject()
    fun names(key: String): List<String> = state.optJSONArray(key).toObjectList().map {
        it.optString("name").ifEmpty { it.optString("concept") }
    }.filter { it.isNotBlank() }
    return LearningPulseSummary(
        remainingMinutes = time.optDouble("remaining_study_minutes_today", time.optDouble("remaining_minutes_today", 0.0)),
        studiedMinutes = time.optDouble("studied_minutes_today", 0.0),
        recommendedBlockMinutes = action.optDouble("minutes", time.optDouble("recommended_next_block_minutes", 10.0)),
        pollAfterMinutes = optInt("poll_after_minutes", action.optInt("poll_after_minutes", 5)),
        mode = action.optString("mode", time.optString("insertion_mode", "steady")),
        reason = action.optString("reason", time.optString("reason", "Keep the next block small and concrete.")),
        readinessScore = state.optDouble("readiness_score", optDouble("readiness_score", 0.0)),
        weakConcepts = names("weak_concepts").ifEmpty { names("weak") },
        masteredConcepts = names("mastered_concepts").ifEmpty { names("mastered") },
    )
}

data class SyllabusUnitModel(
    val id: String,
    val title: String,
    val label: String,
    val estimatedMinutes: Double,
    val priority: Double,
    val reason: String,
    val concepts: List<String>,
)

data class SyllabusModel(
    val title: String,
    val studyUnits: Int,
    val logisticsUnits: Int,
    val estimatedTotalMinutes: Double,
    val units: List<SyllabusUnitModel>,
    val notes: List<String>,
)

fun JSONObject.toSyllabusModel(): SyllabusModel = SyllabusModel(
    title = optString("title"),
    studyUnits = optInt("study_units"),
    logisticsUnits = optInt("logistics_units"),
    estimatedTotalMinutes = optDouble("estimated_total_minutes"),
    units = optJSONArray("units").toObjectList().map {
        SyllabusUnitModel(
            id = it.optString("id"),
            title = it.optString("title"),
            label = it.optString("label"),
            estimatedMinutes = it.optDouble("estimated_minutes"),
            priority = it.optDouble("priority"),
            reason = it.optString("reason"),
            concepts = it.optJSONArray("concept_names").toStringList(),
        )
    },
    notes = optJSONArray("coverage_notes").toStringList(),
)

data class OralQuestionModel(
    val id: String,
    val prompt: String,
    val concept: String,
    val difficulty: String,
    val expectedPoints: List<String>,
    val followups: List<String>,
)

data class OralExamPackModel(
    val questionCount: Int,
    val estimatedMinutes: Double,
    val questions: List<OralQuestionModel>,
    val strategy: List<String>,
)

fun JSONObject.toOralExamPackModel(): OralExamPackModel = OralExamPackModel(
    questionCount = optInt("question_count"),
    estimatedMinutes = optDouble("estimated_minutes"),
    questions = optJSONArray("questions").toObjectList().map {
        OralQuestionModel(
            id = it.optString("id"),
            prompt = it.optString("prompt"),
            concept = it.optString("concept"),
            difficulty = it.optString("difficulty"),
            expectedPoints = it.optJSONArray("expected_points").toStringList(),
            followups = it.optJSONArray("followups").toStringList(),
        )
    },
    strategy = optJSONArray("strategy").toStringList(),
)

data class DeckQualityModel(
    val totalCards: Int,
    val keptCards: Int,
    val rejectedCards: Int,
    val scoreAverage: Double,
    val issues: Map<String, Int>,
    val recommendedCardCap: Int,
)

fun JSONObject.toDeckQualityModel(): DeckQualityModel {
    val issues = mutableMapOf<String, Int>()
    optJSONObject("issues_by_code")?.let { obj -> obj.keys().forEach { issues[it] = obj.optInt(it) } }
    return DeckQualityModel(
        totalCards = optInt("total_cards"),
        keptCards = optInt("kept_cards"),
        rejectedCards = optInt("rejected_cards"),
        scoreAverage = optDouble("score_average"),
        issues = issues,
        recommendedCardCap = optInt("recommended_card_cap"),
    )
}

data class CourseHealthModel(
    val title: String,
    val readinessScore: Double,
    val healthBand: String,
    val nextActions: List<String>,
    val warnings: List<String>,
)

fun JSONObject.toCourseHealthModel(): CourseHealthModel = CourseHealthModel(
    title = optString("title"),
    readinessScore = optDouble("readiness_score", optDouble("score", 0.0)),
    healthBand = optString("health_band", optString("band", "unknown")),
    nextActions = optJSONArray("next_actions").toStringList(),
    warnings = optJSONArray("warnings").toStringList(),
)

data class UserDashboardModel(
    val title: String,
    val nextActionTitle: String,
    val sourceCount: Int,
    val passageCount: Int,
    val cardCount: Int,
)

fun JSONObject.toUserDashboardModel(): UserDashboardModel {
    val flow = optJSONObject("flow") ?: JSONObject()
    val next = flow.optJSONObject("next_action") ?: JSONObject()
    val counts = optJSONObject("counts") ?: JSONObject()
    return UserDashboardModel(
        title = optString("title"),
        nextActionTitle = next.optString("title", "Use daily pulse"),
        sourceCount = counts.optInt("sources"),
        passageCount = counts.optInt("passages"),
        cardCount = counts.optInt("cards"),
    )
}

// --- v9 human date input ----------------------------------------------------

/** A localized date shortcut chip (e.g. "Tomorrow" -> 2026-06-03). */
data class DateShortcut(val key: String, val label: String, val value: String)

/**
 * Result of `date_input_preview`: the engine normalizes a human-typed date and
 * reports whether it can be saved, plus a relative label and shortcut chips.
 */
data class DateInputPreview(
    val rawValue: String,
    val normalizedValue: String?,
    val displayValue: String,
    val relativeLabel: String,
    val isValid: Boolean,
    val isPast: Boolean,
    val canSave: Boolean,
    val error: String?,
    val placeholder: String,
    val shortcuts: List<DateShortcut>,
)

fun JSONObject.toDateInputPreview(): DateInputPreview = DateInputPreview(
    rawValue = optString("raw_value"),
    normalizedValue = if (isNull("normalized_value")) null else optString("normalized_value").ifEmpty { null },
    displayValue = optString("display_value"),
    relativeLabel = optString("relative_label"),
    isValid = optBoolean("is_valid"),
    isPast = optBoolean("is_past"),
    canSave = optBoolean("can_save"),
    error = if (isNull("error")) null else optString("error").ifEmpty { null },
    placeholder = optString("placeholder"),
    shortcuts = optJSONArray("shortcuts").toObjectList().map {
        DateShortcut(it.optString("key"), it.optString("label"), it.optString("value"))
    },
)

// --- v9 import preflight -----------------------------------------------------

/**
 * Result of `import_preview_contract`: labels pasted material as study/review/
 * context/logistics and says whether it is worth turning into cards yet.
 */
data class ImportPreview(
    val detectedLocale: String,
    val label: String,
    val canGenerateCards: Boolean,
    val warning: String?,
    val reason: String,
    val words: Int,
)

fun JSONObject.toImportPreview(): ImportPreview = ImportPreview(
    detectedLocale = optString("detected_locale"),
    label = optString("label"),
    canGenerateCards = optBoolean("can_generate_cards"),
    warning = if (isNull("warning")) null else optString("warning").ifEmpty { null },
    reason = optString("reason"),
    words = optInt("words"),
)
