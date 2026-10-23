package com.mindlift.ara

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mindlift.MindLiftApplication
import com.mindlift.overlay.OverlayWindowService
import com.mindlift.usage.UsagePermissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * AntiROTAid Mode — the manual, opt-in overlay study service.
 *
 * Unlike the owner-only attention monitor, this service does NOT read usage
 * stats or use an accessibility service. It is started explicitly by the user
 * ("Start AntiROTAid Mode") and, while running, shows one short study card over
 * the current screen on a fixed interval. Each card is fully dismissible (Exit),
 * and a persistent notification offers a one-tap Stop. Nothing leaves the device.
 */
class AraModeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        isRunning = true
        startForeground(NOTIF_ID, buildNotification())
        val courseId = intent?.getStringExtra(EXTRA_COURSE_ID).orEmpty()
        val intervalMin = intent?.getIntExtra(EXTRA_INTERVAL_MIN, DEFAULT_INTERVAL_MIN)
            ?: DEFAULT_INTERVAL_MIN
        scope.launch { cardLoop(courseId, intervalMin) }
        return START_STICKY
    }

    private suspend fun cardLoop(courseId: String, intervalMin: Int) {
        // "Show now": a first card a few seconds after the user enables the mode,
        // so the mechanic is immediately visible. Never stack: OverlayWindowService
        // no-ops if a card is already on screen.
        delay(4_000L)
        while (scope.isActive) {
            showCard(courseId)
            // EXAM-PACED: the gap between cards = the engine's study-plan interval
            // (phone-time-left-until-exam ÷ units×repeats), recomputed each time so
            // it tightens as the exam nears. When the exam is close / material is
            // large, the interval drops below a minute — we keep it in SECONDS
            // (down to MIN_GAP_MS) so the owner gets spammed near-continuously, as
            // intended. Falls back to the fixed interval only with no exam to pace.
            val ms = if (com.mindlift.Features.araMode) examPacedDelayMs()
                     else intervalMin.coerceIn(1, 60) * 60_000L
            delay(ms)
        }
    }

    /** Delay (ms) until the next card = the smallest (most urgent) per-exam gap.
     *  Sub-minute gaps are honored down to MIN_GAP_MS so an exam-tomorrow spams every
     *  few seconds; fixed default when there's nothing to pace.
     *
     *  Per EXAM, coverage is one knob:
     *   - NEDEFINIT (interval): the owner pins the gap between cards directly.
     *   - COUNT (de câte ori): ADAPTIVE — gap = phone-time-left ÷ REMAINING (not-yet-
     *     completed) appearances = total (units × count) minus what's already done.
     *     Race through the first cards → remaining drops → gap widens (you're ahead);
     *     dismiss cards and burn time → remaining stays high while time shrinks → gap
     *     tightens. Recomputed every loop and on every (re)start, so rejecting help
     *     now means a more aggressive pace the moment the mode is turned back on. */
    private fun examPacedDelayMs(): Long = try {
        val planStore = AraPlanStore(this)
        val store = AraStore(this)
        val dailyPhoneMin = planStore.effectiveWeeklyMinutes() / 7.0
        val now = System.currentTimeMillis()
        var bestMs = 0L
        planStore.exams().filter { it.materialIds.isNotEmpty() }.forEach { e ->
            val gapMin: Double = if (e.coverageMode == AraExam.COVERAGE_INTERVAL) {
                // Owner-pinned cadence (minutes of active phone use between cards).
                e.coverageIntervalMin.toDouble().takeIf { it >= 1.0 } ?: 0.0
            } else {
                val daysLeft = if (e.dateMs <= 0) 0.0 else (e.dateMs - now) / 86_400_000.0
                val phoneMinLeft = dailyPhoneMin * daysLeft.coerceAtLeast(0.0)
                val remaining = e.materialIds.sumOf { m -> remainingAppearances(store, planStore, m, e.coverageCount) }
                if (phoneMinLeft <= 0.0 || remaining <= 0) 0.0
                else (phoneMinLeft / remaining).coerceAtLeast(1.0)
            }
            if (gapMin > 0.0) {
                val ms = (gapMin * 60_000L).toLong().coerceIn(MIN_GAP_MS, MAX_GAP_MS)
                if (bestMs == 0L || ms < bestMs) bestMs = ms
            }
        }
        if (bestMs > 0L) bestMs else DEFAULT_INTERVAL_MIN * 60_000L
    } catch (_: Exception) {
        DEFAULT_INTERVAL_MIN * 60_000L
    }

    /** Not-yet-finished appearances for one material under a COUNT-mode exam: total
     *  (units × count) minus the completions already logged (each card capped at
     *  `count`, so over-studying one card can't make the material look done). Falls
     *  back to the full total before any card list is known. */
    private fun remainingAppearances(store: AraStore, planStore: AraPlanStore, materialId: String, count: Int): Int {
        val reps = count.coerceAtLeast(1)
        val cardIds = store.cardIdsList(materialId)
        val units = if (cardIds.isNotEmpty()) cardIds.size else planStore.unitCount(materialId).coerceAtLeast(1)
        val total = units * reps
        val done = cardIds.sumOf { minOf(reps, store.cardTimesCompleted(materialId, it)) }
        return (total - done).coerceAtLeast(0)
    }

    private fun showCard(courseId: String) {
        if (!UsagePermissions.hasOverlayAccess(this)) return
        // AntiROTAid (ara): the overlay loads its own lesson card feed from the
        // lean engine — no course/SRS, no decideIntervention. Just show it.
        if (com.mindlift.Features.araMode) {
            startService(Intent(this, OverlayWindowService::class.java))
            return
        }
        if (courseId.isBlank()) return
        val app = MindLiftApplication.from(application)
        // Prefetch the card on this background thread so the overlay can render
        // immediately without competing for the engine lock at display time.
        val data = try {
            app.engine.decideIntervention(courseId, "high").dataOrNull()
        } catch (_: Exception) {
            null
        } ?: return
        if (data.optBoolean("suppressed", false) || data.isNull("overlay")) return
        startService(
            Intent(this, OverlayWindowService::class.java)
                .putExtra(OverlayWindowService.EXTRA_COURSE_ID, courseId)
                .putExtra(OverlayWindowService.EXTRA_RISK_LEVEL, "high")
                .putExtra(OverlayWindowService.EXTRA_INTERVENTION_JSON, data.toString())
        )
    }

    private fun buildNotification(): android.app.Notification {
        ensureChannel()
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, AraModeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val tr = trFor(AraStore(this).lang())
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(tr.notifTitle)
            .setContentText(tr.notifText)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID, "AntiROTAid Mode",
                        NotificationManager.IMPORTANCE_LOW,
                    )
                )
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Reflects whether AntiROTAid Mode is currently active, so the in-app
         *  control shows Stop (not Start) after the user navigates away and back. */
        @Volatile
        var isRunning: Boolean = false
            private set

        const val EXTRA_COURSE_ID = "course_id"
        const val EXTRA_INTERVAL_MIN = "interval_min"
        const val ACTION_STOP = "app.antirotaid.STOP_MODE"
        const val DEFAULT_INTERVAL_MIN = 5
        // Exam-paced gap bounds: as little as 6s apart (near-continuous spam when
        // an exam is imminent) up to 3h (distant exam, little to cover).
        const val MIN_GAP_MS = 6_000L
        const val MAX_GAP_MS = 180L * 60_000L
        private const val CHANNEL_ID = "ara_mode"
        private const val NOTIF_ID = 5301

        fun start(context: Context, courseId: String?, intervalMin: Int = DEFAULT_INTERVAL_MIN) {
            val intent = Intent(context, AraModeService::class.java)
                .putExtra(EXTRA_COURSE_ID, courseId)
                .putExtra(EXTRA_INTERVAL_MIN, intervalMin)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, AraModeService::class.java).setAction(ACTION_STOP)
            )
        }

        /** Called when a course is deleted (Phase 4). The overlay re-resolves which
         *  course to surface on every appearance from the (now reduced) library /
         *  exams, so it can never re-pick a deleted id. If nothing valid remains,
         *  stop the mode outright so it never keeps a stale courseId. */
        fun onCourseDeleted(context: Context, deletedId: String) {
            if (!isRunning) return
            val store = AraStore(context)
            val remaining = AraDemo.list(context, store.hiddenDemos())
            if (remaining.isEmpty()) stop(context)
        }
    }
}
