package com.mindlift.usage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mindlift.MindLiftApplication
import com.mindlift.R
import com.mindlift.bridge.Envelope
import com.mindlift.bridge.toUsageResult
import com.mindlift.overlay.OverlayWindowService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Local, on-device attention monitor. Periodically:
 *   1. collects recent usage sessions (UsageStatsManager),
 *   2. asks the engine to assess attention risk from them,
 *   3. on a HIGH/SEVERE assessment, asks the engine for an intervention and
 *      surfaces it (overlay if permitted, else a gentle notification).
 *
 * Everything stays local. The owner starts/stops this from the app; it is never
 * required — manual mode works without it.
 */
class AttentionMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildOngoingNotification())
        val courseId = intent?.getStringExtra(EXTRA_COURSE_ID)
        scope.launch { monitorLoop(courseId) }
        return START_STICKY
    }

    private fun currentForegroundCategory(): String {
        val usm = getSystemService(USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
            ?: return "unknown"
        val end = System.currentTimeMillis()
        // 10-min window so we catch the foreground app even if the poll fires late
        val events = usm.queryEvents(end - 10 * 60_000, end)
        val ev = android.app.usage.UsageEvents.Event()
        var lastPkg = ""
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND)
                lastPkg = ev.packageName ?: ""
        }
        return if (lastPkg.isNotEmpty()) AppCategories.categoryFor(lastPkg) else "unknown"
    }

    private suspend fun monitorLoop(courseId: String?) {
        val app = MindLiftApplication.from(application)
        val collector = UsageCollector(this)
        var lastHourlyNotifMs = 0L
        var lastAdaptiveMin = 1  // first non-high-pull poll after 1 min; adapts thereafter
        var lastHighPullMs = 0L  // for ledger/sleep-debt minutes between polls
        var stretchStartMs = 0L  // current uninterrupted high-pull stretch
        // The 24h UsageStatsManager sweep is too heavy for every 20-60s poll;
        // contract minutes move slowly, so refresh the sum at most every 5 min.
        var scrolledMinCache = 0.0
        var scrolledMinCacheAt = 0L
        while (scope.isActive) {
            val onHighPull = currentForegroundCategory().let { it == "video" || it == "social" }

            // A heavy stretch just ended: hand over the bill (cost, not blame).
            if (!onHighPull && stretchStartMs > 0L) {
                val stretchMin = (System.currentTimeMillis() - stretchStartMs) / 60_000.0
                stretchStartMs = 0L
                if (stretchMin >= 15.0) fireBingeReceipt(app, stretchMin)
            }

            // On high-pull, surface the intervention immediately and skip heavy
            // autopilotV2 can be heavier, so keep the GIL free for the
            // overlay's decideIntervention call.
            if (onHighPull && courseId != null) {
                val now = System.currentTimeMillis()
                // Minutes since the previous high-pull poll feed the personal
                // binge ledger; the engine routes in-window ones to sleep debt.
                val deltaMin = if (lastHighPullMs > 0L && now - lastHighPullMs < 10 * 60_000L)
                    (now - lastHighPullMs) / 60_000.0 else 0.0
                lastHighPullMs = now
                if (stretchStartMs == 0L) stretchStartMs = now

                // High-pull minutes in the last 24h drive the evening contract.
                if (now - scrolledMinCacheAt > 5 * 60_000L) {
                    scrolledMinCache = try { highPullMinutes(collector) } catch (_: Exception) { 0.0 }
                    scrolledMinCacheAt = now
                }
                val scrolledMin = scrolledMinCache

                // ONE engine call: bypass timing, ledger, milestone, decision.
                // Every bridge crossing blocks on the Python GIL — bundle it.
                var gapSec = 30
                try {
                    val tick = app.engine.lifeMonitorTick(true, deltaMin, scrolledMin)
                        .dataOrNull()
                    tick?.optJSONObject("milestone")?.let { m ->
                        fireSimpleNotif(m.optString("headline"),
                            m.optString("body"), NOTIF_ID + 5)
                    }
                    gapSec = tick?.optJSONObject("decision")
                        ?.optInt("gap_seconds", 30) ?: 30
                } catch (_: Exception) { }

                // Multi-exam war: the engine picks which course interrupts —
                // the nearest fire wins, passed exams keep a trickle.
                val warCourse = try {
                    app.engine.lifePickCourse().dataOrNull()
                        ?.optJSONObject("picked")?.optString("course_id")
                        ?.takeIf { it.isNotBlank() }
                } catch (_: Exception) { null } ?: courseId

                surfaceIntervention(warCourse, "high", scrolledMin)
                delay(gapSec.coerceIn(20, 600) * 1000L)
                continue
            }

            var level: String? = null
            val sessions = collector.collectSessions()
            // Single pass: compute once, reuse everywhere in this cycle.
            val pulse = courseId?.let { app.engine.learningPulse(it).dataOrNull() }
            if (sessions.length() > 0) {
                val usageEnv = app.engine.assessUsageSessions(sessions, courseId, 0.0)
                val usage = usageEnv.dataOrNull()?.toUsageResult()
                level = usage?.risk?.level
                val status = pulse?.optJSONObject("time_status") ?: app.engine.timeStatus(courseId).dataOrNull()
                updateOngoingNotification(status, pulse, null)
            } else {
                updateOngoingNotification(app.engine.timeStatus(courseId).dataOrNull(), pulse, null)
            }

            if (courseId != null && (level == "medium" || level == "high" || level == "severe")) {
                surfaceIntervention(courseId, level ?: "high")
            }

            // Calm waters: one tick checks whether one of the owner's learned
            // weak hours is about to start — win the battle before it begins.
            try {
                val tick = app.engine.lifeMonitorTick(false).dataOrNull()
                tick?.optJSONObject("nudge")?.let { nudge ->
                    fireSimpleNotif(nudge.optString("headline"),
                        nudge.optString("body"), NOTIF_ID + 4)
                }
                // Sunday evening: the week's war report shows up unasked.
                tick?.optJSONObject("checkin")?.let { rep ->
                    val firstLine = rep.optJSONArray("lines")?.optString(0).orEmpty()
                    fireSimpleNotif(rep.optString("headline"), firstLine, NOTIF_ID + 6)
                }
            } catch (_: Exception) { }

            // Adaptive poll interval from pulse only (no autopilotV2 in hot path).
            val adaptive = pulse?.optInt("poll_after_minutes", lastAdaptiveMin) ?: lastAdaptiveMin
            lastAdaptiveMin = adaptive.coerceIn(2, 10)
            val pollDelaySec = if (level == "medium" || level == "high" || level == "severe") 30
                               else lastAdaptiveMin * 60

            val now = System.currentTimeMillis()
            if (courseId != null && now - lastHourlyNotifMs >= 60 * 60 * 1000L) {
                fireHourlyProgressNotif(app, courseId)
                lastHourlyNotifMs = now
            }

            delay(pollDelaySec * 1000L)
        }
    }

    private fun fireSimpleNotif(title: String, text: String, id: Int) {
        if (title.isBlank()) return
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(id, n)
    }

    /** "23 min de scroll = 15 carduri pierdute." Arithmetic, never a lecture. */
    private fun fireBingeReceipt(app: MindLiftApplication, minutes: Double) {
        try {
            val data = app.engine.lifeBingeReceipt(minutes).dataOrNull() ?: return
            if (data.optInt("cards_lost") <= 0) return
            fireSimpleNotif(data.optString("headline"),
                data.optString("body"), NOTIF_ID + 3)
        } catch (_: Exception) { }
    }

    private fun fireHourlyProgressNotif(app: MindLiftApplication, courseId: String) {
        try {
            val result = app.engine.categoryProgress(courseId)
            val data = (result as? Envelope.Success)?.data ?: return
            val cats = data.optJSONArray("categories") ?: return
            val overall = (data.optDouble("overall_readiness", 0.0) * 100).toInt()
            // Find the weakest category that needs work
            var worstLine = "Continua sa inveti!"
            for (i in 0 until cats.length()) {
                val cat = cats.optJSONObject(i) ?: continue
                if (cat.optString("status") == "not_started" || cat.optString("status") == "needs_work") {
                    worstLine = cat.optString("notification_line", worstLine)
                    break
                }
            }
            val n = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentTitle("${com.mindlift.Features.brandName} · Progres $overall%")
                .setContentText(worstLine)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID + 2, n)
        } catch (_: Exception) { }
    }


    private fun updateOngoingNotification(status: JSONObject?, pulse: JSONObject? = null, autopilot: JSONObject? = null) {
        val remaining = status?.optDouble("remaining_study_minutes_today", 0.0) ?: 0.0
        val block = status?.optDouble("recommended_next_block_minutes", 0.0) ?: 0.0
        val action = autopilot?.optJSONObject("decision") ?: pulse?.optJSONObject("next_action")
        val mode = action?.optString("mode") ?: status?.optString("insertion_mode", "monitoring") ?: "monitoring"
        val title = action?.optString("title") ?: "următorul bloc"
        val text = if (remaining <= 0.0) {
            "Timpul de studiu de azi este acoperit; ${title.lowercase()}."
        } else {
            "Rămân ${remaining.toInt()} min azi; ${title.lowercase()}: ${block.toInt()} min ($mode)."
        }
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, n)
    }

    /** Total video/social minutes across the collector's 24h session window. */
    private fun highPullMinutes(collector: UsageCollector): Double {
        val sessions = collector.collectSessions()
        var sec = 0L
        for (i in 0 until sessions.length()) {
            val s = sessions.optJSONObject(i) ?: continue
            val cat = s.optString("category")
            if (cat == "video" || cat == "social") sec += s.optInt("duration_sec").toLong()
        }
        return sec / 60.0
    }

    private fun surfaceIntervention(courseId: String, riskLevel: String = "high",
                                    scrolledMinutes: Double = 0.0) {
        if (UsagePermissions.hasOverlayAccess(this)) {
            // Pre-fetch the intervention decision here (monitor thread) so the
            // overlay service can display the card immediately without competing
            // for the engine lock at display time.
            val app = MindLiftApplication.from(application)
            val result = try { app.engine.decideIntervention(courseId, riskLevel) } catch (_: Exception) { null }
            val data = result?.dataOrNull() ?: return  // suppressed or error → skip
            if (data.optBoolean("suppressed", false) || data.isNull("overlay")) return
            startService(
                Intent(this, OverlayWindowService::class.java)
                    .putExtra(OverlayWindowService.EXTRA_COURSE_ID, courseId)
                    .putExtra(OverlayWindowService.EXTRA_RISK_LEVEL, riskLevel)
                    .putExtra(OverlayWindowService.EXTRA_INTERVENTION_JSON, data.toString())
                    .putExtra(OverlayWindowService.EXTRA_SCROLLED_MINUTES, scrolledMinutes)
            )
        } else {
            val n = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Un moment pentru atentie")
                .setContentText("Un pas mic acum te readuce in ritm.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIF_ID + 1, n)
        }
    }

    private fun buildOngoingNotification() = run {
        ensureChannel()
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Monitor de atentie activ, local.")
            .setOngoing(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID, "Atentie",
                        NotificationManager.IMPORTANCE_LOW,
                    )
                )
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_COURSE_ID = "course_id"
        private const val CHANNEL_ID = "mindlift_attention"
        private const val NOTIF_ID = 4201
        fun start(context: Context, courseId: String?) {
            val intent = Intent(context, AttentionMonitorService::class.java)
                .putExtra(EXTRA_COURSE_ID, courseId)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AttentionMonitorService::class.java))
        }
    }
}
