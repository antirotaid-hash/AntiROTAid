package com.mindlift.ara

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exam -> Materials study model (additive layer).
 *
 * The existing app keeps a flat list of "courses" (one file each) with a
 * deadline — see [AraDemo.Lesson] / [AraStore]. This file adds, WITHOUT touching
 * any of that, the owner's exam-centric model on top:
 *
 *   Exam (one date) -> several Materials (each an imported file) -> per-material
 *   card settings the owner ticks in a ⚙ sheet.
 *
 * Everything persists in the same "antirotaid" SharedPreferences. A material is
 * referenced by the very same id the rest of the app already uses
 * ("custom_<file>" / "demo_<name>"), so progress, deadlines and study all keep
 * working unchanged; the exam layer is pure grouping + settings on top.
 */

/** Per-material import setting. `appearance` carries the engine SPLIT MODE
 *  (pdf_as_is | pdf_paragraphs | pptx_as_is | docx_paragraphs | image_as_is).
 *  Empty = let the engine pick the file type's default. Only PDF is user-chosen;
 *  every other type has a single fixed mode. There is no granularity. */
data class AraMaterialSettings(
    val appearance: String = "",              // split mode; "" = engine default per type
    val cardSize: String = "compact",         // kept for wire-compat; not user-facing
    val repeats: Int = 3,                     // how many times to cover it
) {
    fun toJson(): JSONObject = JSONObject()
        .put("appearance", appearance)
        .put("card_size", cardSize)
        .put("repeats", repeats)

    companion object {
        // Split modes (the only one the user picks between is the PDF pair).
        const val PDF_AS_IS = "pdf_as_is"
        const val PDF_PARAGRAPHS = "pdf_paragraphs"
        const val PPTX_AS_IS = "pptx_as_is"
        const val DOCX_PARAGRAPHS = "docx_paragraphs"
        const val IMAGE_AS_IS = "image_as_is"

        fun from(j: JSONObject?): AraMaterialSettings {
            if (j == null) return AraMaterialSettings()
            return AraMaterialSettings(
                appearance = j.optString("appearance", ""),
                cardSize = j.optString("card_size", "compact"),
                repeats = j.optInt("repeats", 3),
            )
        }
    }
}

/** One exam: a name, a date, and the materials studied for it.
 *
 * COVERAGE (one knob for the WHOLE exam, replacing the old per-material repeats):
 *  - [COVERAGE_COUNT]: the owner says how many full times the exam's whole pile of
 *    materials should be covered before the date; the engine derives the card
 *    interval to hit that.
 *  - [COVERAGE_INTERVAL] ("NEDEFINIT"): the owner pins the gap between cards (in
 *    active-phone minutes) and we instead show, live, how many full passes still
 *    fit in the time left — or that there's no longer time for a whole pass. */
data class AraExam(
    val id: String,
    val name: String,
    val dateMs: Long,
    val materialIds: List<String> = emptyList(),
    val coverageMode: String = COVERAGE_COUNT,
    val coverageCount: Int = 1,
    val coverageIntervalMin: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("date_ms", dateMs)
        .put("material_ids", JSONArray(materialIds))
        .put("coverage_mode", coverageMode)
        .put("coverage_count", coverageCount)
        .put("coverage_interval_min", coverageIntervalMin)

    companion object {
        const val COVERAGE_COUNT = "count"
        const val COVERAGE_INTERVAL = "interval"

        fun from(j: JSONObject): AraExam {
            val arr = j.optJSONArray("material_ids") ?: JSONArray()
            val ids = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) ids.add(arr.optString(i))
            return AraExam(
                id = j.optString("id"),
                name = j.optString("name"),
                dateMs = j.optLong("date_ms", 0L),
                materialIds = ids,
                coverageMode = j.optString("coverage_mode", COVERAGE_COUNT),
                coverageCount = j.optInt("coverage_count", 1).coerceAtLeast(1),
                coverageIntervalMin = j.optInt("coverage_interval_min", 0).coerceAtLeast(0),
            )
        }
    }
}

/**
 * Persistence for exams, per-material settings and the pacing inputs. Shares the
 * "antirotaid" prefs with [AraStore] but owns its own keys, so it never collides
 * with existing state.
 */
class AraPlanStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("antirotaid", Context.MODE_PRIVATE)

    // --- exams ----------------------------------------------------------------
    fun exams(): List<AraExam> {
        val raw = sp.getString(KEY_EXAMS, "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { runCatching { AraExam.from(it) }.getOrNull() }
        }.sortedBy { if (it.dateMs <= 0) Long.MAX_VALUE else it.dateMs }
    }

    fun exam(id: String): AraExam? = exams().firstOrNull { it.id == id }

    fun saveExam(exam: AraExam) {
        val others = exams().filter { it.id != exam.id }
        val arr = JSONArray()
        (others + exam).forEach { arr.put(it.toJson()) }
        sp.edit().putString(KEY_EXAMS, arr.toString()).apply()
    }

    fun deleteExam(id: String) {
        val arr = JSONArray()
        exams().filter { it.id != id }.forEach { arr.put(it.toJson()) }
        sp.edit().putString(KEY_EXAMS, arr.toString()).apply()
    }

    fun addMaterial(examId: String, materialId: String) {
        val e = exam(examId) ?: return
        if (materialId in e.materialIds) return
        saveExam(e.copy(materialIds = e.materialIds + materialId))
    }

    fun removeMaterial(examId: String, materialId: String) {
        val e = exam(examId) ?: return
        saveExam(e.copy(materialIds = e.materialIds - materialId))
    }

    /** Remove a material id from EVERY exam (used on course deletion, Phase 4).
     *  Operates on the raw stored array via the pure [AraCourseCleanup] transform,
     *  so a single course delete can never leave a dangling Exam.MaterialIds ref. */
    fun stripMaterialEverywhere(materialId: String) {
        val raw = sp.getString(KEY_EXAMS, "[]")
        val cleaned = AraCourseCleanup.stripMaterialFromExams(raw, materialId)
        sp.edit().putString(KEY_EXAMS, cleaned).apply()
    }

    /** Total logical units across an exam's materials (caller supplies per-material
     *  unit counts, since counting needs the engine). */
    fun newExamId(): String = "exam_" + System.currentTimeMillis().toString(36)

    /**
     * Ordered material ids the overlay should surface, driven by EXAMS (not by the
     * raw course list). Empty when no exam has materials, so the caller can fall
     * back to the plain library.
     *
     * - "Un examen odată" (sequential): only the nearest upcoming exam's materials.
     * - "Îmbinat" (combine): every exam's materials, exams in date (EDF) order.
     *
     * Each material is weighted by its `repeats`: a material set to 3× appears
     * three times as often as a 1× one. We interleave by rounds (round r includes
     * every material whose repeats > r) so it never dumps one material in a block.
     */
    fun overlayMaterialOrder(): List<String> {
        val withMats = exams().filter { it.materialIds.isNotEmpty() }
        if (withMats.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val chosen = if (studyMode() == MODE_SEQUENTIAL) {
            listOf(withMats.firstOrNull { it.dateMs <= 0 || it.dateMs >= now } ?: withMats.first())
        } else withMats
        // Unique material ids, keeping first-seen (date) order. Coverage is now an
        // exam-level knob (not per material), so every material is interleaved with
        // equal weight here; how many times the whole set is covered is handled by
        // the pacing interval, not by repeating one material more than another.
        val ids = LinkedHashSet<String>().apply { chosen.forEach { addAll(it.materialIds) } }.toList()
        return ids
    }

    // --- per-material settings ------------------------------------------------
    fun materialSettings(materialId: String): AraMaterialSettings {
        val raw = sp.getString(matKey(materialId), null) ?: return AraMaterialSettings()
        return AraMaterialSettings.from(runCatching { JSONObject(raw) }.getOrNull())
    }

    fun setMaterialSettings(materialId: String, s: AraMaterialSettings) =
        sp.edit().putString(matKey(materialId), s.toJson().toString()).apply()

    fun clearMaterialSettings(materialId: String) =
        sp.edit().remove(matKey(materialId)).apply()

    // --- global pacing inputs -------------------------------------------------
    /** Owner-stated average phone minutes per week (Variant B — no usage stats).
     *  0 = not set yet. */
    fun weeklyPhoneMinutes(): Int = sp.getInt(KEY_WEEKLY_MIN, 0)
    fun setWeeklyPhoneMinutes(min: Int) =
        sp.edit().putInt(KEY_WEEKLY_MIN, min.coerceAtLeast(0)).apply()

    /** What the planner actually uses: the owner's value, or a sane 10h/week
     *  default so the very first plan is feasible instead of showing "~0 min". */
    fun effectiveWeeklyMinutes(): Int = weeklyPhoneMinutes().takeIf { it > 0 } ?: DEFAULT_WEEKLY_MIN

    /** "combine" (interleave by EDF) or "sequential" (one exam at a time). */
    fun studyMode(): String = sp.getString(KEY_MODE, MODE_COMBINE) ?: MODE_COMBINE
    fun setStudyMode(mode: String) =
        sp.edit().putString(KEY_MODE, if (mode == MODE_SEQUENTIAL) MODE_SEQUENTIAL else MODE_COMBINE).apply()

    /** Auto-delete exams/courses once their deadline passes. OFF by default — the
     *  owner opts in; otherwise nothing is ever removed without a manual delete. */
    fun autoDeleteEnabled(): Boolean = sp.getBoolean(KEY_AUTO_DELETE, false)
    fun setAutoDeleteEnabled(on: Boolean) = sp.edit().putBoolean(KEY_AUTO_DELETE, on).apply()

    /** Exams a given material currently belongs to (by id). */
    fun examsForMaterial(materialId: String): List<AraExam> =
        exams().filter { materialId in it.materialIds }

    // --- cached logical-unit count per material (so the planner needn't reload
    //     every deck just to count its slides/paragraphs). Saved when a material
    //     is opened. 0 = unknown yet. ----------------------------------------
    fun unitCount(materialId: String): Int = sp.getInt(unitKey(materialId), 0)
    fun setUnitCount(materialId: String, n: Int) {
        if (n > 0) sp.edit().putInt(unitKey(materialId), n).apply()
    }

    companion object {
        const val MODE_COMBINE = "combine"
        const val MODE_SEQUENTIAL = "sequential"
        const val DEFAULT_WEEKLY_MIN = 600  // 10h/week assumed until the owner sets it

        private const val KEY_EXAMS = "exams_v1"
        private const val KEY_WEEKLY_MIN = "weekly_phone_minutes"
        private const val KEY_MODE = "study_mode"
        private const val KEY_AUTO_DELETE = "auto_delete_expired"
        private fun matKey(materialId: String) = "matset_$materialId"
        private fun unitKey(materialId: String) = "units_$materialId"
    }
}
