package com.mindlift.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset

/**
 * Reads foreground/background app transitions from [UsageStatsManager] over a
 * window and folds them into sessions, in the exact JSON shape the engine's
 * `assess_usage_sessions` expects. Mirrors the Python `sessionize` logic: pair a
 * foreground with its next background; drop unclosed foregrounds.
 *
 * Returns an empty array if usage access isn't granted — the caller then stays
 * in manual mode rather than guessing.
 */
class UsageCollector(private val context: Context) {

    /** Build a sessions JSON array for the last [windowMillis] of activity. */
    fun collectSessions(windowMillis: Long = 24L * 60 * 60 * 1000): JSONArray {
        val sessions = JSONArray()
        if (!UsagePermissions.hasUsageAccess(context)) return sessions

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return sessions
        val end = System.currentTimeMillis()
        val start = end - windowMillis
        val events = usm.queryEvents(start, end)

        val openSince = HashMap<String, Long>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND ->
                    openSince[pkg] = event.timeStamp
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val from = openSince.remove(pkg) ?: continue
                    val durSec = ((event.timeStamp - from) / 1000L).toInt()
                    if (durSec <= 0) continue
                    sessions.put(sessionJson(pkg, from, event.timeStamp, durSec))
                }
            }
        }
        // Count the app the owner is using RIGHT NOW: any foreground that never
        // closed is an ongoing session up to "end", so current scrolling registers
        // immediately instead of only after switching away.
        for ((pkg, from) in openSince) {
            val durSec = ((end - from) / 1000L).toInt()
            if (durSec > 0) sessions.put(sessionJson(pkg, from, end, durSec))
        }
        return sessions
    }

    private fun sessionJson(pkg: String, startMs: Long, endMs: Long, durSec: Int): JSONObject =
        JSONObject()
            .put("package", pkg)
            .put("category", AppCategories.categoryFor(pkg))
            .put("started_at", iso(startMs))
            .put("ended_at", iso(endMs))
            .put("duration_sec", durSec)

    private fun iso(ms: Long): String =
        Instant.ofEpochMilli(ms).atOffset(ZoneOffset.UTC).toString()
}
