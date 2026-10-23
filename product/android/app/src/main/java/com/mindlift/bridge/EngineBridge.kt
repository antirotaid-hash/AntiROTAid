package com.mindlift.bridge

import com.chaquo.python.PyObject
import com.chaquo.python.Python
import org.json.JSONObject

/**
 * Thin Kotlin wrapper over the Python `mindlift.android_bridge.Bridge`.
 *
 * Each call sends a method name + a JSON args object and receives a JSON
 * envelope string, which is parsed into [Envelope]. The Python engine owns all
 * business logic; this class only marshals the call and never reimplements it.
 *
 * Calls block on the Python interpreter, so invoke from a background dispatcher
 * (the repository layer does this) — never the main thread.
 */
class EngineBridge(
    python: Python,
    dbPath: String,
    envFile: String? = null,
    aiProvider: String? = null,
) {

    private val bridge: PyObject =
        python.getModule("mindlift.android_bridge").callAttr(
            "make_bridge",
            dbPath,
            envFile ?: "",
            aiProvider ?: "",
        )

    @Synchronized
    fun call(method: String, args: JSONObject = JSONObject()): Envelope {
        val raw = bridge.callAttr("call", method, args.toString()).toString()
        return Envelope.parse(raw)
    }

    /** Startup smoke check — proves the embedded engine is healthy on-device. */
    @Synchronized
    fun doctor(): Envelope = Envelope.parse(bridge.callAttr("doctor").toString())

    @Synchronized
    fun close() {
        bridge.callAttr("close")
    }

    // --- typed convenience wrappers (still return Envelope) -----------------

    fun createCourse(title: String, examDate: String?) = call(
        "create_course",
        JSONObject().put("title", title).put("exam_date", examDate)
    )

    fun importMaterial(courseId: String, title: String, text: String, type: String = "pasted") =
        call(
            "import_material",
            JSONObject().put("course_id", courseId).put("title", title)
                .put("text", text).put("source_type", type)
        )

    fun importFile(courseId: String, title: String, filename: String, base64Payload: String) =
        call(
            "import_file",
            JSONObject().put("course_id", courseId).put("title", title)
                .put("filename", filename).put("payload", base64Payload)
                .put("encoding", "base64")
        )

    fun importAndBuildDeck(courseId: String, title: String, text: String, type: String = "pasted") =
        call(
            "import_and_build_deck",
            JSONObject().put("course_id", courseId).put("title", title)
                .put("text", text).put("source_type", type)
        )

    fun importAndBuildDeckFile(courseId: String, title: String, filename: String, base64Payload: String) =
        call(
            "import_and_build_deck",
            JSONObject().put("course_id", courseId).put("title", title)
                .put("filename", filename).put("payload", base64Payload).put("encoding", "base64")
        )

    fun buildDna(courseId: String) =
        call("build_dna", JSONObject().put("course_id", courseId))

    fun buildDeck(courseId: String) =
        call("build_deck", JSONObject().put("course_id", courseId))

    fun nextDue(courseId: String, limit: Int = 10) =
        call("next_due", JSONObject().put("course_id", courseId).put("limit", limit))

    fun review(cardId: String, grade: String) =
        call("review", JSONObject().put("card_id", cardId).put("grade", grade))

    fun answerReview(cardId: String, answer: String) =
        call("answer_review", JSONObject().put("card_id", cardId).put("answer", answer))

    fun progress(courseId: String) =
        call("progress", JSONObject().put("course_id", courseId))

    fun learningState(courseId: String) =
        call("learning_state", JSONObject().put("course_id", courseId))

    fun markConceptKnown(courseId: String, conceptName: String, score: Double = 0.92) =
        call(
            "mark_concept_known",
            JSONObject().put("course_id", courseId)
                .put("concept_name", conceptName).put("score", score)
        )

    fun buildPlan(courseId: String, minutes: Double = 15.0) =
        call("build_plan", JSONObject().put("course_id", courseId).put("minutes", minutes))

    fun buildAdaptivePlan(courseId: String, baseDailyStudyMinutes: Double = 45.0) =
        call(
            "build_adaptive_plan",
            JSONObject().put("course_id", courseId)
                .put("base_daily_study_minutes", baseDailyStudyMinutes)
        )

    fun timeStatus(courseId: String?, baseDailyStudyMinutes: Double = 45.0) =
        call(
            "time_status",
            JSONObject().put("course_id", courseId)
                .put("base_daily_study_minutes", baseDailyStudyMinutes)
        )

    fun learningPulse(courseId: String, baseDailyStudyMinutes: Double = 45.0) =
        call(
            "learning_pulse",
            JSONObject().put("course_id", courseId)
                .put("base_daily_study_minutes", baseDailyStudyMinutes)
        )

    fun logStudyMinutes(courseId: String, minutes: Double) =
        call("log_study_minutes", JSONObject().put("course_id", courseId).put("minutes", minutes))

    fun logUsageBlock(minutes: Double, category: String, packageName: String, courseId: String?) =
        call(
            "log_usage_block",
            JSONObject().put("minutes", minutes).put("category", category)
                .put("package", packageName).put("course_id", courseId)
        )

    fun assessUsage(scenario: String, courseId: String?, studyMinutes: Double) = call(
        "assess_usage",
        JSONObject().put("scenario", scenario).put("course_id", courseId)
            .put("study_minutes", studyMinutes)
    )

    /** Assess attention risk from real device sessions (UsageStatsManager). */
    fun assessUsageSessions(sessions: org.json.JSONArray, courseId: String?,
                            studyMinutes: Double) = call(
        "assess_usage_sessions",
        JSONObject().put("sessions", sessions).put("course_id", courseId)
            .put("study_minutes", studyMinutes)
    )

    fun decideIntervention(courseId: String, riskLevel: String = "high") =
        call("decide_intervention", JSONObject().put("course_id", courseId).put("risk_level", riskLevel))

    fun startBreak(breakId: String) =
        call("start_break", JSONObject().put("break_id", breakId))

    fun advanceBreak(breakId: String) =
        call("advance_break", JSONObject().put("break_id", breakId))

    fun dailyReport(courseId: String) =
        call("daily_report", JSONObject().put("course_id", courseId))

    fun inventory() = call("inventory")
    fun export(destination: String) = call("export", JSONObject().put("destination", destination))
    fun wipe() = call("wipe")
    fun getSettings() = call("get_settings")
    fun updateSettings(changes: JSONObject) = call("update_settings", changes)

    fun courseHome(courseId: String) =
        call("course_home", JSONObject().put("course_id", courseId))

    fun materialPreview(courseId: String) =
        call("material_preview", JSONObject().put("course_id", courseId))

    fun deckQualityReport(courseId: String) =
        call("deck_quality_report", JSONObject().put("course_id", courseId))

    fun buildSyllabus(courseId: String) =
        call("build_syllabus", JSONObject().put("course_id", courseId))

    fun oralExamPack(courseId: String, maxQuestions: Int = 24) =
        call("oral_exam_pack", JSONObject().put("course_id", courseId).put("max_questions", maxQuestions))

    fun deckQualityV2(courseId: String) =
        call("deck_quality_v2", JSONObject().put("course_id", courseId))

    fun learnerProfile(courseId: String) =
        call("learner_profile", JSONObject().put("course_id", courseId))

    fun courseHealth(courseId: String) =
        call("course_health", JSONObject().put("course_id", courseId))

    fun categoryProgress(courseId: String) =
        call("category_progress", JSONObject().put("course_id", courseId))

    fun deleteCourse(courseId: String) =
        call("delete_course", JSONObject().put("course_id", courseId))

    fun setExamDate(courseId: String, examDate: String?) =
        call("set_exam_date", JSONObject().put("course_id", courseId).put("exam_date", examDate))

    // --- v9 localized setup / human date / import preflight ------------------

    /** Normalizes a human-typed date (e.g. "15 iunie 2026") and validates it. */
    fun dateInputPreview(rawDate: String, locale: String = "ro") =
        call("date_input_preview", JSONObject().put("raw_date", rawDate).put("locale", locale))

    /** Preflights pasted material so noisy admin/logistics pages don't become cards. */
    fun importPreviewContract(text: String, locale: String = "ro") =
        call("import_preview_contract", JSONObject().put("text", text).put("locale", locale))

    /** Localized strings + supported-locale metadata for the UI. */
    fun localizationPack(locale: String = "ro") =
        call("localization_pack", JSONObject().put("locale", locale))

    /** One-shot localized contract for the first course-setup screen. */
    fun courseSetupContract(locale: String = "ro") =
        call("course_setup_contract", JSONObject().put("locale", locale))

    // --- v10 boost pack -----------------------------------------------------

    /** Cards the learner keeps failing — flagged with severity + a fix suggestion. */
    fun leechReport(courseId: String, minAttempts: Int = 3, lapseThreshold: Double = 0.4) =
        call("leech_report", JSONObject().put("course_id", courseId)
            .put("min_attempts", minAttempts).put("lapse_threshold", lapseThreshold))

    /** Every due card with a one-line "why this card now?" reason. */
    fun dueQueueExplained(courseId: String, limit: Int = 10) =
        call("due_queue_explained", JSONObject().put("course_id", courseId).put("limit", limit))

    /** A focused session built only from the lowest-mastery concepts. */
    fun weakSpotDrill(courseId: String, maxCards: Int = 12) =
        call("weak_spot_drill", JSONObject().put("course_id", courseId).put("max_cards", maxCards))

    /** Consecutive study days + an exam-adaptive daily goal. */
    fun studyStreak(courseId: String) =
        call("study_streak", JSONObject().put("course_id", courseId))

    /** Short, honest weekly recap: volume, accuracy, strong vs slipping. */
    fun weeklyDigest(courseId: String, windowDays: Int = 7) =
        call("weekly_digest", JSONObject().put("course_id", courseId).put("window_days", windowDays))

    /** Export the deck as Anki-importable TSV (or CSV). */
    fun exportAnkiDeck(courseId: String, fmt: String = "tsv") =
        call("export_anki_deck", JSONObject().put("course_id", courseId).put("fmt", fmt))

    /** Decide the next-card delay + intervention intensity from live scroll. */
    fun scrollCadence(scrollVelocity: Double, minutesOnApp: Double,
                      examHorizonDays: Double? = null, packageName: String? = null) =
        call("scroll_cadence", JSONObject().put("scroll_velocity", scrollVelocity)
            .put("minutes_on_app", minutesOnApp)
            .put("exam_horizon_days", examHorizonDays).put("package", packageName))

    // --- v10 boost pack, wave 2 ---------------------------------------------

    fun reviewForecast(courseId: String, days: Int = 14) =
        call("review_forecast", JSONObject().put("course_id", courseId).put("days", days))

    fun retentionForecast(courseId: String, horizonDays: Int = 7) =
        call("retention_forecast", JSONObject().put("course_id", courseId).put("horizon_days", horizonDays))

    fun mockExam(courseId: String, questionCount: Int = 15, minutes: Int = 30) =
        call("mock_exam", JSONObject().put("course_id", courseId)
            .put("question_count", questionCount).put("minutes", minutes))

    fun mcqQuiz(courseId: String, count: Int = 10) =
        call("mcq_quiz", JSONObject().put("course_id", courseId).put("count", count))

    fun examDayChecklist(courseId: String) =
        call("exam_day_checklist", JSONObject().put("course_id", courseId))

    fun prerequisitePath(courseId: String) =
        call("prerequisite_path", JSONObject().put("course_id", courseId))

    fun duplicateCards(courseId: String, threshold: Double = 0.8) =
        call("duplicate_cards", JSONObject().put("course_id", courseId).put("threshold", threshold))

    fun cardHealth(courseId: String) =
        call("card_health", JSONObject().put("course_id", courseId))

    fun todayAgenda() = call("today_agenda")

    fun sessionPlanner(minutes: Int = 50, blockMinutes: Int = 20, breakMinutes: Int = 5) =
        call("session_planner", JSONObject().put("minutes", minutes)
            .put("block_minutes", blockMinutes).put("break_minutes", breakMinutes))

    fun searchCourse(courseId: String, query: String, limit: Int = 20) =
        call("search_course", JSONObject().put("course_id", courseId)
            .put("query", query).put("limit", limit))

    fun opportunityCost(lostMinutes: Double, minutesPerCard: Double = 1.5) =
        call("opportunity_cost", JSONObject().put("lost_minutes", lostMinutes)
            .put("minutes_per_card", minutesPerCard))

    fun distractionBudget(sessions: org.json.JSONArray, budgets: JSONObject? = null) =
        call("distraction_budget", JSONObject().put("sessions", sessions).put("budgets", budgets))

    fun clozeCards(courseId: String, maxCards: Int = 20) =
        call("cloze_cards", JSONObject().put("course_id", courseId).put("max_cards", maxCards))

    fun motivationNudge(courseId: String) =
        call("motivation_nudge", JSONObject().put("course_id", courseId))

    fun userDashboard(courseId: String) =
        call("user_dashboard", JSONObject().put("course_id", courseId))


    fun uploadDiagnosis(filename: String, base64Payload: String? = null, ownerHint: String? = null) =
        call("upload_diagnosis", JSONObject().put("filename", filename)
            .put("payload", base64Payload).put("owner_hint", ownerHint))

    fun lectureMap(courseId: String) =
        call("lecture_map", JSONObject().put("course_id", courseId))

    fun evidenceMap(courseId: String, maxItems: Int = 120) =
        call("evidence_map", JSONObject().put("course_id", courseId).put("max_items", maxItems))

    fun conceptLadder(courseId: String) =
        call("concept_ladder", JSONObject().put("course_id", courseId))

    fun compileSmartDeck(courseId: String, targetCards: Int = 72, mode: String = "exam") =
        call("compile_smart_deck", JSONObject().put("course_id", courseId)
            .put("target_cards", targetCards).put("mode", mode))

    fun examModePack(courseId: String, mode: String = "oral", targetGrade: Double = 8.5, maxTasks: Int = 24) =
        call("exam_mode_pack", JSONObject().put("course_id", courseId)
            .put("mode", mode).put("target_grade", targetGrade).put("max_tasks", maxTasks))

    fun coachAnswer(courseId: String, question: String, answer: String, mode: String = "oral") =
        call("coach_answer", JSONObject().put("course_id", courseId)
            .put("question", question).put("answer", answer).put("mode", mode))

    fun socraticHint(courseId: String, conceptName: String, attempt: String = "") =
        call("socratic_hint", JSONObject().put("course_id", courseId)
            .put("concept_name", conceptName).put("attempt", attempt))

    fun revisionPack(courseId: String, minutes: Int = 30, style: String = "exam") =
        call("revision_pack", JSONObject().put("course_id", courseId)
            .put("minutes", minutes).put("style", style))

    fun attentionForecast(courseId: String?, windowDays: Int = 30) =
        call("attention_forecast", JSONObject().put("course_id", courseId)
            .put("window_days", windowDays))

    fun autopilotV2(courseId: String, baseDailyMinutes: Double = 45.0) =
        call("autopilot_v2", JSONObject().put("course_id", courseId)
            .put("base_daily_minutes", baseDailyMinutes))

    fun examCountdownPlan(courseId: String, days: Int = 14, minutesPerDay: Int = 45, targetGrade: Double = 8.5) =
        call("exam_countdown_plan", JSONObject().put("course_id", courseId)
            .put("days", days).put("minutes_per_day", minutesPerDay).put("target_grade", targetGrade))

    fun courseKpis(courseId: String) =
        call("course_kpis", JSONObject().put("course_id", courseId))

    fun productHub(courseId: String) =
        call("product_hub", JSONObject().put("course_id", courseId))

    fun exportStudyPack(courseId: String, format: String = "markdown") =
        call("export_study_pack", JSONObject().put("course_id", courseId).put("format", format))

    fun lectureMapV3(courseId: String, targetCards: Int = 72) =
        call("lecture_map_v3", JSONObject().put("course_id", courseId).put("target_cards", targetCards))

    fun ocrGate(filename: String, base64Payload: String? = null, mime: String? = null) =
        call("ocr_gate", JSONObject().put("filename", filename).put("payload", base64Payload).put("mime", mime))

    fun importWorkbench(items: org.json.JSONArray) =
        call("import_workbench", JSONObject().put("items", items))

    fun importWorkbenchScreen(items: org.json.JSONArray) =
        call("import_workbench_screen", JSONObject().put("items", items))

    fun recoveryLoop(courseId: String, baseDailyMinutes: Double = 45.0) =
        call("recovery_loop", JSONObject().put("course_id", courseId).put("base_daily_minutes", baseDailyMinutes))

    fun permissionReadiness(status: JSONObject) =
        call("permission_readiness", JSONObject().put("status", status))

    fun manualComplianceReport() = call("manual_compliance_report")

    fun releaseGateReport(buyerClean: Boolean = false) =
        call("release_gate_report", JSONObject().put("buyer_clean", buyerClean))

    // --- life modes: war on doomscrolling beyond exams (full flavor) ---------

    fun lifeStatus() = call("life_status")

    fun lifeSetMode(mode: String) =
        call("life_set_mode", JSONObject().put("mode", mode))

    fun lifeSetSleepWindow(bedtime: String, wake: String, tzOffsetMinutes: Int) =
        call("life_set_sleep_window", JSONObject().put("bedtime", bedtime)
            .put("wake", wake).put("tz_offset_minutes", tzOffsetMinutes))

    fun lifeAddBill(title: String, dueAt: String, amount: String = "",
                    recurring: String = "") =
        call("life_add_bill", JSONObject().put("title", title)
            .put("due_at", dueAt).put("amount", amount).put("recurring", recurring))

    fun lifePayBill(billId: String) =
        call("life_pay_bill", JSONObject().put("bill_id", billId))

    fun lifeRemoveBill(billId: String) =
        call("life_remove_bill", JSONObject().put("bill_id", billId))

    fun lifeSyncMeetings(meetings: org.json.JSONArray) =
        call("life_sync_meetings", JSONObject().put("meetings", meetings))

    /** All-day people-days (birthdays etc.), next ~7 days. */
    fun lifeSyncEvents(events: org.json.JSONArray) =
        call("life_sync_events", JSONObject().put("events", events))

    /** The interruption arbiter: card vs life message vs quiet, + the gap. */
    fun lifeOverlayDecision(phonePressure: Double, examProximity: Double = 0.0,
                            examCount: Int = 0, scrolledMinutesToday: Double = 0.0) =
        call("life_overlay_decision", JSONObject().put("phone_pressure", phonePressure)
            .put("exam_proximity", examProximity).put("exam_count", examCount)
            .put("scrolled_minutes_today", scrolledMinutesToday))

    /** Tonight's self-declared scroll budget; expires at midnight. */
    fun lifeSetContract(minutes: Int) =
        call("life_set_contract", JSONObject().put("minutes", minutes))

    fun lifeContractStatus(scrolledMinutes: Double) =
        call("life_contract_status", JSONObject().put("scrolled_minutes", scrolledMinutes))

    /** Overlay lifecycle: "dismissed" | "quick_return" -> escalation state. */
    fun lifeRecordOverlayEvent(event: String) =
        call("life_record_overlay_event", JSONObject().put("event", event))

    /** Scroll minutes -> sleep debt; the engine keeps only in-window ones. */
    fun lifeRecordNightUsage(minutes: Double) =
        call("life_record_night_usage", JSONObject().put("minutes", minutes))

    fun lifeSleepDebt() = call("life_sleep_debt")

    /** One call per high-pull poll: binge ledger + (in-window) sleep debt. */
    fun lifeRecordScroll(minutes: Double) =
        call("life_record_scroll", JSONObject().put("minutes", minutes))

    /** The bill for a finished scroll stretch: minutes -> cards lost. */
    fun lifeBingeReceipt(minutes: Double) =
        call("life_binge_receipt", JSONObject().put("minutes", minutes))

    /** Dated truce; pass null to cancel. */
    fun lifeSetVacation(until: String?) =
        call("life_set_vacation",
            JSONObject().put("until", until ?: JSONObject.NULL))

    /** Heads-up just before one of the learner's own risky hours, max 1/h/day. */
    fun lifePreemptiveNudge() = call("life_preemptive_nudge")

    /** Honest weekly standing (grade + lines). Display-only, never auto-sent. */
    fun lifeWarReport() = call("life_war_report")

    /** Exam-eve mobilization until `until`; null stands down. */
    fun lifeSetPanic(until: String?) =
        call("life_set_panic", JSONObject().put("until", until ?: JSONObject.NULL))

    /** Charger plugged/unplugged; night unplug breaks the ritual. */
    fun lifePowerEvent(connected: Boolean) =
        call("life_power_event", JSONObject().put("connected", connected))

    /** The declared daily study hour; start=null disables. */
    fun lifeSetFocusWindow(start: String?, end: String?, tzOffsetMinutes: Int) =
        call("life_set_focus_window", JSONObject()
            .put("start", start ?: JSONObject.NULL)
            .put("end", end ?: JSONObject.NULL)
            .put("tz_offset_minutes", tzOffsetMinutes))

    /** Multi-exam war: which course owns the next interruption. */
    fun lifePickCourse() = call("life_pick_course")

    /** Scroll-minute balance + open-loan standing. */
    fun lifeWallet() = call("life_wallet")

    /** Borrow scroll minutes now, repay in known cards by ~21:00. */
    fun lifeTakeLoan(minutes: Int) =
        call("life_take_loan", JSONObject().put("minutes", minutes))

    /** After the exam: predicted vs reality, stored for calibration. */
    fun examPostmortem(courseId: String, actualGrade: Double) =
        call("exam_postmortem", JSONObject().put("course_id", courseId)
            .put("actual_grade", actualGrade))

    /** ONE bridge call per monitor poll: bypass + ledger + decision/nudge. */
    fun lifeMonitorTick(onHighPull: Boolean, deltaMinutes: Double = 0.0,
                        scrolledMinutesToday: Double = 0.0) =
        call("life_monitor_tick", JSONObject().put("on_high_pull", onHighPull)
            .put("delta_minutes", deltaMinutes)
            .put("scrolled_minutes_today", scrolledMinutesToday))

    // --- insights subsystem -------------------------------------------------

    fun examReadiness(courseId: String, dailyMinutes: Double = 45.0) =
        call("exam_readiness", JSONObject().put("course_id", courseId)
            .put("daily_minutes", dailyMinutes))

    fun knowledgeGaps(courseId: String, maxGaps: Int = 12) =
        call("knowledge_gaps", JSONObject().put("course_id", courseId)
            .put("max_gaps", maxGaps))

    fun studyMomentum(windowDays: Int = 14, targetDailyMinutes: Double = 30.0) =
        call("study_momentum", JSONObject().put("window_days", windowDays)
            .put("target_daily_minutes", targetDailyMinutes))

    fun confidenceCalibration(courseId: String? = null) =
        call("confidence_calibration", JSONObject().put("course_id", courseId))

    fun interleavedSession(courseId: String, targetMinutes: Double = 20.0) =
        call("interleaved_session", JSONObject().put("course_id", courseId)
            .put("target_minutes", targetMinutes))

    fun dailyBoard(totalMinutes: Double = 45.0) =
        call("daily_board", JSONObject().put("total_minutes", totalMinutes))

    fun distractionTriggers(windowDays: Int = 30) =
        call("distraction_triggers", JSONObject().put("window_days", windowDays))



    // --- v9/v12 source-10 local hardening ----------------------------------

    fun runtimeGuardV9(provider: String = "auto", mode: String = "personal_local") =
        call("runtime_guard_v9", JSONObject().put("provider", provider).put("mode", mode))

    fun promptRegistryV9() = call("prompt_registry_v9")

    fun aiPipelineV9(courseId: String, provider: String = "auto", targetCards: Int = 72, mode: String = "personal_local") =
        call("ai_pipeline_v9", JSONObject().put("course_id", courseId)
            .put("provider", provider).put("target_cards", targetCards).put("mode", mode))

    fun courseBlueprintV9(courseId: String, targetCards: Int = 72) =
        call("course_blueprint_v9", JSONObject().put("course_id", courseId).put("target_cards", targetCards))

    fun finalExamControllerV9(courseId: String, hoursLeft: Double? = null, targetGrade: Double = 8.5) =
        call("final_exam_controller_v9", JSONObject().put("course_id", courseId)
            .put("hours_left", hoursLeft).put("target_grade", targetGrade))

    fun productConsoleV9(courseId: String? = null) =
        call("product_console_v9", JSONObject().put("course_id", courseId))

    fun ownerStartPacketV9() = call("owner_start_packet_v9")

    fun claudeHandoffV9() = call("claude_handoff_v9")

    fun source10GateV9() = call("source10_gate_v9")

}
