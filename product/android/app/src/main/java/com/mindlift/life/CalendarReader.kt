package com.mindlift.life

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Reads the next-48h calendar instances locally and shapes them for
 * `life_sync_meetings`. The engine keeps only this snapshot — no calendar id,
 * no attendees, no description ever leave the ContentResolver query.
 *
 * Full flavor only (READ_CALENDAR is declared in src/full). Without the
 * permission this returns null so callers can show the request flow.
 */
object CalendarReader {

    private const val WINDOW_HOURS = 48L
    private const val ALL_DAY_WINDOW_DAYS = 7L

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** Upcoming events as [{title, due_at ISO-UTC}], or null without permission. */
    fun upcomingMeetings(context: Context): JSONArray? {
        if (!hasPermission(context)) return null

        val now = System.currentTimeMillis()
        val end = now + WINDOW_HOURS * 3600_000L
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString()).appendPath(end.toString()).build()

        val out = JSONArray()
        context.contentResolver.query(
            uri,
            arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN),
            "${CalendarContract.Instances.ALL_DAY} = 0", null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            val iTitle = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val iBegin = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            while (cursor.moveToNext()) {
                val begin = cursor.getLong(iBegin)
                if (begin < now) continue   // already started: engine ignores it anyway
                out.put(JSONObject()
                    .put("title", cursor.getString(iTitle).orEmpty().ifBlank { "Meeting" })
                    .put("due_at", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                        Instant.ofEpochMilli(begin).atOffset(ZoneOffset.UTC))))
            }
        }
        return out
    }

    /** All-day items (birthdays, name days) in the next 7 days, as
     * [{title, due_at ISO-UTC noon}], or null without permission. */
    fun upcomingAllDayEvents(context: Context): JSONArray? {
        if (!hasPermission(context)) return null

        val now = System.currentTimeMillis()
        val end = now + ALL_DAY_WINDOW_DAYS * 24 * 3600_000L
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString()).appendPath(end.toString()).build()

        val out = JSONArray()
        context.contentResolver.query(
            uri,
            arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN),
            "${CalendarContract.Instances.ALL_DAY} = 1", null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            val iTitle = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val iBegin = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            while (cursor.moveToNext()) {
                val title = cursor.getString(iTitle).orEmpty()
                if (title.isBlank()) continue
                // Anchor the day at noon UTC so "today/tomorrow" reads naturally.
                val noon = Instant.ofEpochMilli(cursor.getLong(iBegin))
                    .atOffset(ZoneOffset.UTC).withHour(12).withMinute(0).withSecond(0)
                out.put(JSONObject().put("title", title)
                    .put("due_at", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(noon)))
            }
        }
        return out
    }
}
