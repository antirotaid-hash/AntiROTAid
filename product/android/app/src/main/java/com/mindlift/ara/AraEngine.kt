package com.mindlift.ara

import android.util.Base64
import com.mindlift.bridge.EngineBridge
import org.json.JSONObject

/**
 * Thin wrapper over the lean AntiROTAid Python bridge (engine_ara).
 * All calls are offline; results stay on the device. This wrapper exposes only
 * the study product surface: text/file -> lesson_chunk / slide_image cards.
 */
class AraEngine(private val bridge: EngineBridge) {

    data class Loaded(val feed: JSONObject, val cards: List<AraCard>, val errorMessage: String? = null)

    /** Build the lesson card feed from source text. */
    fun load(lessonId: String, text: String, tr: Tr = trFor(Lang.EN)): Loaded {
        val stype = when {
            text.contains("Slide 1:", ignoreCase = true) -> "slides"
            text.contains("Page 1:", ignoreCase = true) -> "pdf"
            else -> "pasted"
        }
        val env = bridge.call(
            "lesson_feed",
            JSONObject().put("text", text).put("source_type", stype),
        )
        val feed = env.dataOrNull()
        return if (feed != null) Loaded(feed, parseAraFeed(feed))
        else errorLoaded(tr, env.messageOrNull() ?: tr.errGenerate)
    }

    /** Build cards directly from an imported local file. No network, no external AI.
     *  Pass includeMedia=false for the detail/count screen: it only needs the card
     *  text, and decoding every slide image into base64 can OOM on large decks. */
    fun loadFile(filename: String, bytes: ByteArray? = null, localPath: String? = null,
                 includeMedia: Boolean = true,
                 settings: AraMaterialSettings = AraMaterialSettings(),
                 tr: Tr = trFor(Lang.EN)): Loaded {
        // Phase 7 (import memory): when the file is already in app storage, let the
        // Python engine read it straight from disk instead of base64-encoding the
        // whole document in Java (that round-trip allocated tens of MB per import
        // and could OOM on large decks). Only fall back to a base64 payload when no
        // on-device path is available.
        val useDisk = !localPath.isNullOrBlank()
        val payload = if (useDisk || bytes == null) "" else Base64.encodeToString(bytes, Base64.NO_WRAP)
        val env = bridge.call(
            "lesson_feed_file",
            JSONObject()
                .put("filename", filename)
                .put("payload", payload)
                .put("encoding", "base64")
                .put("include_media", includeMedia)
                .put("local_path", localPath ?: "")
                .put("card_size", settings.cardSize)
                .put("appearance", settings.appearance),
        )
        val feed = env.dataOrNull()
        return if (feed != null) Loaded(feed, parseAraFeed(feed))
        else errorLoaded(tr, importErrorMessage(env, tr))
    }

    /** Lightweight load for the detail/count screen: Python reads the file from
     *  its on-device path, so Java never base64-encodes the whole deck. No media. */
    fun loadFilePath(filename: String, localPath: String,
                     settings: AraMaterialSettings = AraMaterialSettings(),
                     tr: Tr = trFor(Lang.EN)): Loaded {
        val env = bridge.call(
            "lesson_feed_file",
            JSONObject()
                .put("filename", filename)
                .put("payload", "")
                .put("encoding", "base64")
                .put("include_media", false)
                .put("local_path", localPath)
                .put("card_size", settings.cardSize)
                .put("appearance", settings.appearance),
        )
        val feed = env.dataOrNull()
        return if (feed != null) Loaded(feed, parseAraFeed(feed))
        else errorLoaded(tr, importErrorMessage(env, tr))
    }

    /** Type-aware card-appearance options offered for a material (what the ⚙ sheet shows). */
    fun materialOptions(sourceType: String): JSONObject =
        bridge.call("material_options", JSONObject().put("source_type", sourceType))
            .dataOrNull() ?: JSONObject()

    /** Schedule several exams against the weekly phone-time budget. */
    fun studyPlan(exams: org.json.JSONArray, weeklyPhoneMinutes: Int, mode: String): JSONObject =
        bridge.call(
            "study_plan",
            JSONObject().put("exams", exams)
                .put("weekly_phone_minutes", weeklyPhoneMinutes)
                .put("mode", mode),
        ).dataOrNull() ?: JSONObject()

    private fun errorLoaded(tr: Tr, message: String): Loaded {
        val card = AraCard(
            cardId = "import_error",
            cardType = "lesson_chunk",
            sourceLabel = "Import",
            title = tr.errTitle,
            body = message,
        )
        val feed = JSONObject()
            .put("pages", org.json.JSONArray())
            .put("page_count", 0)
            .put("card_count", 1)
        return Loaded(feed, listOf(card), message)
    }

    /** Update local progress from this session's events, then persist it. */
    fun persist(store: AraStore, session: AraSession, loaded: Loaded) {
        if (loaded.errorMessage != null) return
        val existing = store.progressJson(session.lessonId)
        val base = if (existing.has("cards_total")) existing else
            bridge.call(
                "init_progress",
                JSONObject().put("lesson_id", session.lessonId)
                    .put("title", session.lessonId).put("feed", loaded.feed),
            ).dataOrNull() ?: JSONObject()
        val updated = bridge.call(
            "update_progress",
            JSONObject().put("progress", base).put("feed", loaded.feed)
                .put("events", session.events),
        ).dataOrNull() ?: base
        store.saveProgress(session.lessonId, updated)
    }
}
