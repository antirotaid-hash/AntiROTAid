package com.mindlift.ara

import org.json.JSONArray

/**
 * Central manifest of EVERY persisted key tied to one course/material, plus the
 * pure exam-reference cleanup (Android Update 1, Phase 4).
 *
 * Deleting a course previously left orphaned state behind (hidden cards, per-card
 * progress, cached deck ids, the overlay unit cursor, material settings, exam
 * references). Centralizing the key list here — and unit-testing it — guarantees
 * a delete wipes all of it and that we never forget a key when new state is added.
 *
 * A course/material id is the same id the rest of the app uses:
 * "custom_<file>" for imported files, "demo_<name>" for bundled demos.
 */
object AraCourseCleanup {

    /** SharedPreferences key prefixes that are namespaced per course id. */
    private val PER_COURSE_PREFIXES = listOf(
        "prog_",         // progress json (cards_seen/total…)
        "deadline_",     // exam deadline
        "passes_",       // completed passes counter
        "pace_",         // learned pace EMA
        "checked_",      // legacy checked flag
        "resumeidx_",    // legacy integer resume index
        "resume_",       // new resume model (idx + cardId + fingerprint)
        "hiddencards_",  // per-material hidden-card set
        "cardprog_",     // per-card seen/known/repeat/scroll state
        "cardids_",      // cached ordered deck id list (deck fingerprint source)
        "unit_idx_",     // overlay per-course unit rotation cursor
        "matset_",       // AraPlanStore per-material settings
        "units_",        // AraPlanStore cached logical-unit count
    )

    /** All keys that must be removed from SharedPreferences when [courseId] is deleted. */
    fun keysFor(courseId: String): List<String> =
        PER_COURSE_PREFIXES.map { it + courseId }

    /** Prefix every generated feed-cache file for a stored source file name shares
     *  (see AraDemo.cacheKey). Sanitized the same way the cache key is. */
    fun feedCachePrefix(storedName: String): String =
        "${storedName}_".replace(Regex("[^A-Za-z0-9._]+"), "_")

    /** True when a cache file belongs to [storedName] and must be purged on delete. */
    fun matchesFeedCache(fileName: String, storedName: String): Boolean =
        fileName.startsWith(feedCachePrefix(storedName))

    /**
     * Remove every reference to [courseId] from a persisted exams JSON array
     * (each exam's "material_ids"). Pure + defensive: malformed input yields an
     * empty array string rather than throwing. Returns the new array as a string.
     */
    fun stripMaterialFromExams(examsJson: String?, courseId: String): String {
        val arr = runCatching { JSONArray(examsJson ?: "[]") }.getOrDefault(JSONArray())
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val exam = arr.optJSONObject(i) ?: continue
            val mats = exam.optJSONArray("material_ids") ?: JSONArray()
            val kept = JSONArray()
            for (j in 0 until mats.length()) {
                val id = mats.optString(j)
                if (id != courseId) kept.put(id)
            }
            exam.put("material_ids", kept)
            out.put(exam)
        }
        return out.toString()
    }
}
