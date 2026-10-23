package com.mindlift.ui

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Validates a user-entered exam date (+ optional time of day) and turns it into
 * the ISO string the engine stores. Rules the owner asked for:
 *   - an exam can have a time of day (e.g. 14:30), not just a date;
 *   - a date/time that has already passed is refused with a clear message.
 *
 * Kept UI-side so the user gets instant feedback; the engine repeats the
 * past-date guard as a safety net.
 */
sealed interface ExamDateResult {
    /** No date typed — clears / leaves the exam date unset (allowed). */
    data object Empty : ExamDateResult
    /** Valid, in the future. [iso] is what to store ("2026-06-15" or "2026-06-15T14:30"). */
    data class Ok(val iso: String) : ExamDateResult
    /** Rejected — [reason] is shown to the user. */
    data class Invalid(val reason: String) : ExamDateResult
}

object ExamDateTime {

    private fun parseTime(raw: String): LocalTime? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        // Accept "14:30" and "9:30"; padding a single-digit hour.
        val padded = if (t.length == 4 && t[1] == ':') "0$t" else t
        return try { LocalTime.parse(padded) } catch (_: Exception) { null }
    }

    fun parse(date: String, time: String, now: LocalDateTime = LocalDateTime.now()): ExamDateResult {
        val d = date.trim()
        val t = time.trim()
        if (d.isEmpty()) return ExamDateResult.Empty

        val localDate = try { LocalDate.parse(d) } catch (_: Exception) {
            return ExamDateResult.Invalid("Format dată invalid. Folosește AAAA-LL-ZZ (ex: 2026-06-15).")
        }
        val localTime = if (t.isEmpty()) null else parseTime(t)
            ?: return ExamDateResult.Invalid("Format oră invalid. Folosește HH:MM (ex: 14:30).")

        // Past check: with a time, compare to the exact moment; without one, the
        // whole exam day still counts as valid until it ends.
        val moment = localTime?.let { localDate.atTime(it) } ?: localDate.atTime(LocalTime.MAX)
        if (moment.isBefore(now)) {
            return ExamDateResult.Invalid("Data a trecut — nu se poate. Alege o dată din viitor.")
        }

        val iso = if (localTime != null) "${localDate}T${localTime}" else localDate.toString()
        return ExamDateResult.Ok(iso)
    }
}
