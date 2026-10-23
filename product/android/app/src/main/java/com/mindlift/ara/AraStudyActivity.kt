package com.mindlift.ara

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindlift.MindLiftApplication
import org.json.JSONArray
import org.json.JSONObject

/**
 * AntiROTAid local study flow (lean engine, offline):
 *   home (instructions + courses sorted by deadline)
 *     -> course detail (info, plan, cards, delete)
 *       -> card feed
 *     -> settings (theme + font preview + privacy/info)
 * Demo lessons are bundled copyright-safe assets only. Nothing leaves the phone.
 */
class AraStudyActivity : ComponentActivity() {

    private sealed interface Route {
        data object Home : Route
        data object Settings : Route
        data object Exams : Route
        data object Courses : Route
        data object Info : Route
        data object Tutorial : Route
        data class Detail(val id: String) : Route
        data class Study(val id: String) : Route
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyImmersive()
        val engine = AraEngine(MindLiftApplication.from(application).engine)
        val store = AraStore(this)
        val planStore = AraPlanStore(this)
        setContent {
            var route by remember { mutableStateOf<Route>(Route.Home) }
            var font by remember { mutableStateOf(store.font()) }
            var theme by remember { mutableStateOf(store.theme()) }
            var lang by remember { mutableStateOf(store.lang()) }
            val coach = rememberCoachState()
            AraBrand.activeTheme = theme
            var refresh by remember { mutableStateOf(0) }
            var overlayOn by remember { mutableStateOf(AraModeService.isRunning) }
            // First install: hide demo courses so the library opens clean, and
            // show the one-time intro.
            remember {
                if (store.isFirstRun()) store.completeFirstRun(AraDemo.allDemoIds(this@AraStudyActivity))
                // Seed one fully worked "Model exam" the very first time, so a new
                // user immediately sees how an exam groups a material, gets a date,
                // and how the plan/pacing reads — instead of an empty Exams screen.
                seedModelExamIfNeeded(this@AraStudyActivity, store, planStore)
                0
            }
            // First run: 3 = language, 1 = consent/policy, 2 = tutorial, 0 = done.
            var firstRunStage by remember {
                mutableStateOf(
                    if (store.langOrNull() == null) 3
                    else if (store.onboardingSeen()) 0 else 1
                )
            }
            val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
            androidx.compose.runtime.DisposableEffect(lifecycle) {
                val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
                    if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME)
                        overlayOn = AraModeService.isRunning
                }
                lifecycle.addObserver(obs)
                onDispose { lifecycle.removeObserver(obs) }
            }
            // When importing a material straight into an exam, remember which one.
            var pendingExamId by remember { mutableStateOf<String?>(null) }
            val importer = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    val newId = importCourse(uri)
                    if (newId != null) {
                        // Count units + seed progress right after import (off-thread)
                        // so the course shows real "carduri / slide-uri" immediately
                        // AND the exam pacing has a unit count to divide by — instead
                        // of "—" until the course is opened once.
                        AraBg.launch {
                            precountCourse(newId, engine, store, planStore)
                            withContext(Dispatchers.Main) { refresh++ }
                        }
                        val exId = pendingExamId
                        if (exId != null) {
                            // Attach to the exam and inherit its date as the deadline.
                            planStore.addMaterial(exId, newId)
                            planStore.exam(exId)?.let { store.setDeadline(newId, it.dateMs) }
                            pendingExamId = null
                            refresh++
                        } else {
                            // Standalone import: just add it to the library. No date
                            // prompt — deadlines are set only when a material is put
                            // under an exam.
                            refresh++
                        }
                    }
                }
            }

            // The system/gesture back action otherwise finishes the Activity from
            // ANY screen (it has no idea about our single-route navigation), which
            // boots the user out of the app instead of going up one level like the
            // on-screen "←" does. Mirror exactly what each screen's own back arrow
            // already does. During first-run (language/consent/tutorial) there is
            // no sensible "up" target, so swallow back instead of letting it exit
            // mid-onboarding.
            androidx.activity.compose.BackHandler(enabled = firstRunStage != 0 || route != Route.Home) {
                if (firstRunStage == 0) {
                    route = when (val r = route) {
                        is Route.Study -> Route.Detail(r.id)
                        else -> Route.Home
                    }
                } // else: mid-onboarding — consume the press, no sensible "up" target.
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalTr provides trFor(lang)) {
            when (val r = route) {
                is Route.Home -> {
                    AraHome(
                        overlayOn = overlayOn,
                        coach = coach,
                        onToggleOverlay = {
                            overlayOn = if (overlayOn) { stopOverlayMode(); false } else startOverlayMode()
                        },
                        onSettings = { route = Route.Settings },
                        onExams = { route = Route.Exams },
                        onCourses = { route = Route.Courses },
                        onInfo = { route = Route.Info },
                        onQuickGuide = { route = Route.Tutorial },
                    )
                }

                is Route.Info -> AraInfoScreen(onBack = { route = Route.Home })

                is Route.Tutorial -> AraTutorial(onClose = { route = Route.Home })

                is Route.Courses -> {
                    val lessons = remember(refresh) {
                        AraDemo.purgeExpired(this, store, planStore)
                        // A plain library catalogue. Sorting is chosen on-screen;
                        // deadlines belong to exams, not to materials.
                        AraDemo.list(this, store.hiddenDemos())
                    }
                    val examNamesFor: (String) -> List<String> = remember(refresh) {
                        val byMat = HashMap<String, MutableList<String>>()
                        planStore.exams().forEach { e ->
                            e.materialIds.forEach { byMat.getOrPut(it) { ArrayList() }.add(e.name) }
                        }
                        val fn: (String) -> List<String> = { id -> byMat[id]?.toList() ?: emptyList() }
                        fn
                    }
                    // Backfill counts for courses imported before counting existed
                    // (or never opened), so they stop showing "—" and the pacing has
                    // their units. One-shot per missing course.
                    LaunchedEffect(lessons) {
                        val missing = lessons.filter {
                            planStore.unitCount(it.id) == 0 || store.cardsTotal(it.id) == 0
                        }
                        if (missing.isNotEmpty()) AraBg.launch {
                            missing.forEach { precountCourse(it.id, engine, store, planStore) }
                            withContext(Dispatchers.Main) { refresh++ }
                        }
                    }
                    AraCoursesScreen(
                        lessons = lessons,
                        store = store,
                        planStore = planStore,
                        refresh = refresh,
                        examNamesFor = examNamesFor,
                        onAddCourse = { importer.launch(AraDemo.supportedMimeTypes) },
                        onOpenDetail = { route = Route.Detail(it) },
                        onStudy = { route = Route.Study(it) },
                        onRestoreDemos = { store.restoreDemos(); refresh++ },
                        onBack = { route = Route.Home },
                    )
                }

                is Route.Exams -> {
                    val exams = remember(refresh) {
                        AraDemo.purgeExpired(this, store, planStore)
                        planStore.exams()
                    }
                    val allCourses = remember(refresh) { AraDemo.list(this, store.hiddenDemos()) }
                    val planJson = remember(refresh) {
                        val arr = org.json.JSONArray()
                        exams.forEach { e ->
                            // Coverage is now an EXAM-level knob. num_units = the exam's
                            // total logical units; repeats = how many full passes the
                            // owner asked for (COUNT mode), or 1 in NEDEFINIT/interval
                            // mode (there the gap is owner-pinned and we instead show how
                            // many passes still fit — computed live in the card).
                            val totalUnits = e.materialIds.sumOf { m -> planStore.unitCount(m).coerceAtLeast(1) }
                            val reps = if (e.coverageMode == AraExam.COVERAGE_INTERVAL) 1
                                       else e.coverageCount.coerceAtLeast(1)
                            arr.put(
                                org.json.JSONObject()
                                    .put("id", e.id).put("name", e.name)
                                    .put("exam_iso", isoOf(e.dateMs))
                                    .put("num_units", totalUnits).put("repeats", reps)
                            )
                        }
                        engine.studyPlan(arr, planStore.effectiveWeeklyMinutes(), planStore.studyMode())
                    }
                    AraExamsScreen(
                        exams = exams,
                        allCourses = allCourses,
                        weeklySet = planStore.weeklyPhoneMinutes() > 0,
                        dailyPhoneMin = planStore.effectiveWeeklyMinutes() / 7.0,
                        courseTitle = { id -> AraDemo.find(this, id)?.title ?: id },
                        planJson = planJson,
                        pickDate = { cb -> pickDeadline(cb) },
                        onSaveExam = { ex ->
                            planStore.saveExam(ex)
                            // Keep each material's deadline in sync with the exam date
                            // (so changing the date re-paces everything under it).
                            ex.materialIds.forEach { store.setDeadline(it, ex.dateMs) }
                            refresh++
                        },
                        onDeleteExam = { planStore.deleteExam(it); refresh++ },
                        onToggleMaterial = { ex, m ->
                            val e = planStore.exam(ex)
                            if (e != null) {
                                if (m in e.materialIds) planStore.removeMaterial(ex, m)
                                else { planStore.addMaterial(ex, m); store.setDeadline(m, e.dateMs) }
                                refresh++
                            }
                        },
                        onImportToExam = { pendingExamId = it; importer.launch(AraDemo.supportedMimeTypes) },
                        materialSettings = { id -> planStore.materialSettings(id) },
                        onMaterialSettings = { id, s -> planStore.setMaterialSettings(id, s); refresh++ },
                        loadMaterialOptions = { id ->
                            engine.materialOptions(AraDemo.sourceType(AraDemo.find(this, id)?.filename ?: ""))
                        },
                        onOpenSettings = { route = Route.Settings },
                        onBack = { route = Route.Home },
                    )
                }

                is Route.Settings -> AraSettings(
                    store = store,
                    planStore = planStore,
                    font = font,
                    theme = theme,
                    lang = lang,
                    onLang = { lang = it.also(store::setLang) },
                    onFont = { font = it.also(store::setFont) },
                    onTheme = { theme = it.also(store::setTheme).also { t -> AraBrand.activeTheme = t } },
                    onPurgeExpired = { AraDemo.purgeExpired(this, store, planStore); refresh++ },
                    onBack = { route = Route.Home },
                )

                is Route.Detail -> {
                    val lesson = remember(r.id, refresh) { AraDemo.find(this, r.id) }
                    if (lesson == null) { route = Route.Home }
                    else {
                        // Off the main thread — PDF extraction here froze the
                        // detail screen on open. Show an empty list until ready.
                        val loaded by produceState<AraEngine.Loaded?>(initialValue = null, r.id, refresh) {
                            // Detail only needs card text — load without slide-image
                            // base64 so a large deck can't OOM the count/list view.
                            value = withContext(Dispatchers.Default) {
                                AraDemo.load(this@AraStudyActivity, engine, r.id, includeMedia = false,
                                    settings = planStore.materialSettings(r.id))
                                    .also { planStore.setUnitCount(r.id, it.feed.optInt("page_count", 0)) }
                            }
                        }
                        AraCourseDetail(
                            lesson = lesson,
                            store = store,
                            engine = engine,
                            planStore = planStore,
                            refresh = refresh,
                            cards = loaded?.cards ?: emptyList(),
                            onStudy = { route = Route.Study(r.id) },
                            onDelete = {
                                AraDemo.remove(this, store, r.id)
                                planStore.clearMaterialSettings(r.id)
                                refresh++
                                route = Route.Home
                            },
                            onSettingsChanged = { refresh++ },
                            onBack = { route = Route.Home },
                        )
                    }
                }

                is Route.Study -> {
                    val id = r.id
                    val session = remember(id) { AraSession(id) }
                    // Build the feed off the main thread (PDF extraction is heavy);
                    // doing it inline in composition froze the screen on open.
                    val loaded by produceState<AraEngine.Loaded?>(initialValue = null, id, refresh) {
                        value = withContext(Dispatchers.Default) {
                            AraDemo.load(this@AraStudyActivity, engine, id,
                                settings = planStore.materialSettings(id))
                        }
                    }
                    val data = loaded
                    if (data == null) {
                        AraOverlayLoading(onExit = { route = Route.Detail(id) })
                    } else {
                        val exit: () -> Unit = {
                            AraBg.launch {
                                engine.persist(store, session, data)
                                store.recordSession(id, session.shownCount())
                            }
                            refresh++
                            route = Route.Detail(id)
                        }
                        var hiddenCards by remember(id) { mutableStateOf(store.hiddenCards(id)) }
                        val deck = remember(data, hiddenCards) { data.cards.filter { it.cardId !in hiddenCards } }
                        // Cache the current deck ids so passes/planner needn't re-extract.
                        LaunchedEffect(deck) { store.saveCardIds(id, deck.map { it.cardId }) }
                        // Robust resume: resolve by CardId+fingerprint against THIS deck.
                        val startAt = remember(deck) { store.resolveStartIndex(id, deck) }
                        AraCardFeed(
                            cards = deck,
                            font = font,
                            theme = theme,
                            subject = AraDemo.find(this@AraStudyActivity, id)?.title ?: "",
                            startIndex = startAt,
                            showBack = true,
                            onExit = exit,
                            onCycleFont = { font = font.next().also(store::setFont) },
                            onCycleTheme = { theme = theme.next().also(store::setTheme) },
                            onCardShown = { c, idx -> session.log("card_shown", c.cardId, outer = idx); store.markCardSeen(id, c.cardId); store.saveResumeAt(id, idx, deck) },
                            onCardHidden = { c -> session.log("card_hidden", c.cardId) },
                            onInnerScroll = { c, d -> session.log("inner_scroll", c.cardId, innerDepth = d) },
                            onUnderstood = { c, ok ->
                                session.log(if (ok) "mark_understood" else "mark_not_understood", c.cardId)
                            },
                            onStayHere = { _ -> exit() },
                            // Finished the whole deck: next open starts from card 1,
                            // not stuck on the last card.
                            onFinishedDeck = { store.resetResume(id) },
                            onDeleteCard = { c ->
                                val old = deck
                                val newDeck = old.filter { it.cardId != c.cardId }
                                store.hideCard(id, c.cardId)
                                store.onCardRemoved(id, c.cardId, old, newDeck)
                                hiddenCards = store.hiddenCards(id)
                                session.log("card_deleted", c.cardId)
                            },
                            initialScrollFor = { c -> store.cardScrollOffset(id, c.cardId) },
                            onScroll = { c, off, pct -> store.saveCardScroll(id, c.cardId, off, pct) },
                            onCardComplete = { c -> store.markCardCompleted(id, c.cardId) },
                        )
                    }
                }
            }

            when (firstRunStage) {
                3 -> AraLanguagePicker(
                    current = lang,
                    onPick = { lang = it; store.setLang(it) },
                    // Persist whatever is currently selected (the default included),
                    // so tapping Continue without first tapping a row still saves the
                    // choice — otherwise the picker reappears on every launch.
                    onContinue = { store.setLang(lang); firstRunStage = 4 },
                )
                4 -> AraDisclaimerGate(onContinue = { firstRunStage = 1 })
                1 -> AraConsent(onAccept = { firstRunStage = 2 })
                2 -> AraTutorial(onClose = { store.markOnboardingSeen(); firstRunStage = 0 })
            }
            } // CompositionLocalProvider(LocalTr)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    /** Hide the system navigation bar so the app owns the whole screen; it slides
     *  back in transiently on a swipe from the edge. The status bar stays. */
    private fun applyImmersive() {
        val ic = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        ic.isAppearanceLightStatusBars = false
        ic.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        ic.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /** Epoch ms -> ISO-8601 (UTC) for the engine's study planner. */
    private fun isoOf(ms: Long): String =
        if (ms <= 0) "" else java.time.Instant.ofEpochMilli(ms).toString()

    /** Native date -> time picker, returns the chosen instant (epoch ms). */
    private fun pickDeadline(onPicked: (Long) -> Unit) {
        val cal = java.util.Calendar.getInstance()
        android.app.DatePickerDialog(
            this,
            { _, y, m, d ->
                android.app.TimePickerDialog(
                    this,
                    { _, h, min ->
                        val out = java.util.Calendar.getInstance()
                        out.set(y, m, d, h, min, 0)
                        out.set(java.util.Calendar.MILLISECOND, 0)
                        onPicked(out.timeInMillis)
                    },
                    cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE),
                    true,
                ).show()
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH),
        ).apply {
            datePicker.minDate = System.currentTimeMillis() - 1000
        }.show()
    }

    private fun tr(): Tr = trFor(AraStore(this).lang())

    private fun startOverlayMode(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
            !android.provider.Settings.canDrawOverlays(this)) {
            android.widget.Toast.makeText(
                this, tr().overlayPermPrompt,
                android.widget.Toast.LENGTH_LONG,
            ).show()
            startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName"),
                )
            )
            return false
        }
        startForegroundService(android.content.Intent(this, AraModeService::class.java))
        android.widget.Toast.makeText(
            this, tr().overlayStarted,
            android.widget.Toast.LENGTH_LONG,
        ).show()
        return true
    }

    private fun stopOverlayMode() {
        AraModeService.stop(this)
        android.widget.Toast.makeText(
            this, tr().overlayStopped, android.widget.Toast.LENGTH_SHORT,
        ).show()
    }

    /** Import a local study file. Returns the new course id on success, else null. */
    /** Load a course once to cache its logical-unit count + seed progress, so the
     *  home shows real "carduri / slide-uri" and the exam pacing has units to
     *  divide by. Idempotent: does nothing once both are known. Call off the main
     *  thread (the PDF/PPTX extractor is heavy). */
    private fun precountCourse(id: String, engine: AraEngine, store: AraStore, planStore: AraPlanStore) {
        if (planStore.unitCount(id) > 0 && store.cardsTotal(id) > 0) return
        runCatching {
            val l = AraDemo.load(this, engine, id, includeMedia = false,
                settings = planStore.materialSettings(id))
            planStore.setUnitCount(id, l.feed.optInt("page_count", 0))
            if (store.cardsTotal(id) == 0) engine.persist(store, AraSession(id), l)
        }
    }

    private fun importCourse(uri: android.net.Uri): String? = runCatching {
        val name = AraDemo.displayName(this, uri)
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return null
        if (bytes.isEmpty()) return null
        val filename = AraDemo.saveCustomFile(this, name, bytes)
        android.widget.Toast.makeText(this, tr().toastCourseAdded, android.widget.Toast.LENGTH_SHORT).show()
        "custom_$filename"
    }.getOrElse {
        android.widget.Toast.makeText(this, tr().toastReadFail, android.widget.Toast.LENGTH_SHORT).show()
        null
    }
}

internal fun AraFont.next(): AraFont =
    AraFont.entries[(ordinal + 1) % AraFont.entries.size]

/** Process-lifetime scope for fire-and-forget engine writes (progress persist).
 *  The engine bridge call is @Synchronized and can block for seconds; running it
 *  on the UI thread from a card's Exit caused an ANR ("Input dispatching timed
 *  out"). This scope also outlives a torn-down overlay/screen so the write isn't
 *  cancelled mid-flight. */
internal val AraBg = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * The AntiROTAid card feed as shown OVER other apps (TYPE_APPLICATION_OVERLAY).
 */
@Composable
fun AraOverlayContent(onExit: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val app = ctx.applicationContext as android.app.Application
    val engine = remember { AraEngine(com.mindlift.MindLiftApplication.from(app).engine) }
    val store = remember { AraStore(ctx) }
    val planStore = remember { AraPlanStore(ctx) }
    val lessons = remember { AraDemo.list(ctx, store.hiddenDemos()).sortedWith(AraDemo.byDeadline(store)) }
    if (lessons.isEmpty()) { onExit(); return }
    // EXAM-DRIVEN: the overlay surfaces material from the user's exams, weighted by
    // each material's repeats and ordered by the study mode. Only if there is no
    // exam with materials do we fall back to walking the deadline-sorted library.
    // Within the chosen material the overlay walks one logical unit per appearance.
    val lesson = remember {
        val examPool = planStore.overlayMaterialOrder().filter { AraDemo.find(ctx, it) != null }
        val pool = examPool.ifEmpty { lessons.map { it.id } }
        AraDemo.find(ctx, pool[store.nextOverlayIndex(pool.size)]) ?: lessons.first()
    }
    val session = remember(lesson.id) { AraSession(lesson.id) }
    // Build the card feed OFF the main thread. A PDF course runs the Python
    // extractor (PDF -> slide images), which takes seconds; doing it inline in
    // composition froze the overlay window over the other app. Show a light
    // placeholder until the cards are ready.
    val loaded by produceState<AraEngine.Loaded?>(initialValue = null, lesson.id) {
        value = withContext(Dispatchers.Default) {
            AraDemo.load(ctx, engine, lesson.id, settings = planStore.materialSettings(lesson.id))
        }
    }
    var font by remember { mutableStateOf(store.font()) }
    var theme by remember { mutableStateOf(store.theme()) }
    AraBrand.activeTheme = theme
    val overlayTr = trFor(store.lang())
    // Picked once per overlay appearance (this composable is freshly mounted each
    // time the service shows a card), so the line rotates every interruption.
    val nag = remember { overlayTr.nagMessages.randomOrNull() }
    androidx.compose.runtime.CompositionLocalProvider(LocalTr provides overlayTr) {
    Box(Modifier.fillMaxSize()) {
        val data = loaded
        if (data == null) {
            AraOverlayLoading(onExit = onExit)
        } else {
        // The WHOLE course is available over the other app, not just one slice —
        // if the user feels like scrolling more, they can keep going through the
        // entire deck. It opens on the card they last left off at (resume), and a
        // progress bar shows how far through the course they are.
        var hiddenCards by remember(lesson.id) { mutableStateOf(store.hiddenCards(lesson.id)) }
        val deck = remember(data, hiddenCards) { data.cards.filter { it.cardId !in hiddenCards } }
        if (deck.isEmpty()) { onExit(); return@CompositionLocalProvider }
        LaunchedEffect(deck) { store.saveCardIds(lesson.id, deck.map { it.cardId }) }
        val startAt = remember(deck) { store.resolveStartIndex(lesson.id, deck) }
        AraCardFeed(
            cards = deck,
            font = font,
            theme = theme,
            subject = lesson.title,
            startIndex = startAt,
            onExit = { AraBg.launch { engine.persist(store, session, data) }; onExit() },
            onFinishedDeck = { store.resetResume(lesson.id) },
            onCycleFont = { font = font.next().also(store::setFont) },
            onCycleTheme = { theme = theme.next().also(store::setTheme) },
            onCardShown = { c, idx ->
                session.log("card_shown", c.cardId, outer = idx)
                store.markCardSeen(lesson.id, c.cardId)
                store.saveResumeAt(lesson.id, idx, deck)
            },
            onCardHidden = { c -> session.log("card_hidden", c.cardId) },
            onInnerScroll = { c, d -> session.log("inner_scroll", c.cardId, innerDepth = d) },
            onUnderstood = { c, ok ->
                session.log(if (ok) "mark_understood" else "mark_not_understood", c.cardId)
            },
            onStayHere = { c ->
                store.saveResumeAt(lesson.id, deck.indexOfFirst { it.cardId == c.cardId }.coerceAtLeast(0), deck)
            },
            onCardComplete = { c -> store.markCardCompleted(lesson.id, c.cardId) },
            onDeleteCard = { c ->
                val old = deck
                val newDeck = old.filter { it.cardId != c.cardId }
                store.hideCard(lesson.id, c.cardId)
                store.onCardRemoved(lesson.id, c.cardId, old, newDeck)
                hiddenCards = store.hiddenCards(lesson.id)
                session.log("card_deleted", c.cardId)
            },
            nagMessage = nag,
        )
        // Holographic "materialize": a scan line sweeps top->bottom once as the
        // overlay appears over the other app. Purely decorative; doesn't take taps.
        val mat = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(Unit) {
            mat.animateTo(1f, androidx.compose.animation.core.tween(900,
                easing = androidx.compose.animation.core.FastOutSlowInEasing))
        }
        if (mat.value < 1f) {
            val m = mat.value
            Canvas(Modifier.fillMaxSize()) {
                val y = m * size.height
                drawRect(color = AraBrand.green.copy(alpha = 0.06f * (1f - m)))
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, AraBrand.green.copy(alpha = 0.22f * (1f - m)), Color.Transparent),
                        startY = y - 70f, endY = y + 70f,
                    ),
                )
                drawLine(AraBrand.greenDeep.copy(alpha = 0.9f * (1f - m)),
                    Offset(0f, y), Offset(size.width, y), strokeWidth = 3f)
            }
        }
        }
    }
    } // CompositionLocalProvider(LocalTr)
}

/** Lightweight placeholder shown over the other app while the card feed is being
 *  built off the main thread. Tapping it (Exit) dismisses the overlay so the user
 *  is never trapped if preparation is slow. */
@Composable
private fun AraOverlayLoading(onExit: () -> Unit) {
    val tr = LocalTr.current
    AraGlassScrim {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AraLogoMark()
                Text(tr.cardPreparing, color = AraBrand.title,
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .border(androidx.compose.foundation.BorderStroke(1.dp, AraBrand.glassEdgeGreen), RoundedCornerShape(14.dp))
                        .clickable { onExit() }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                ) { Text(tr.close, color = AraBrand.greenDeep, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// Home
// ----------------------------------------------------------------------------

@Composable
private fun AraHome(
    overlayOn: Boolean,
    coach: CoachState,
    onToggleOverlay: () -> Unit,
    onSettings: () -> Unit,
    onExams: () -> Unit,
    onCourses: () -> Unit,
    onInfo: () -> Unit,
    onQuickGuide: () -> Unit,
) {
    val tr = LocalTr.current
    var guideChooser by remember { mutableStateOf(false) }
    // The live walkthrough steps, in the chosen language. Each points at a real
    // element on this screen via its coachAnchor id.
    val steps = remember(tr) {
        listOf(
            CoachStep("settings", tr.coachSettings),
            CoachStep("guide", tr.coachGuide),
            CoachStep("info", tr.coachInfo),
            CoachStep("overlay", tr.coachOverlay),
            CoachStep("exams", tr.coachExams),
            CoachStep("courses", tr.coachCourses),
        )
    }
    CoachHost(coach) {
      AraGlassScrim {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AraLogoMark()
                    Spacer(Modifier.width(11.dp))
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = AraBrand.title)) { append("AntiROTA") }
                            withStyle(SpanStyle(color = AraBrand.green, fontWeight = FontWeight.Bold)) { append("id") }
                        },
                        fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
                    )
                }
            }
            // Top menu: clear text actions instead of icon glyphs.
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TopMenuButton(tr.menuSettings, Modifier.weight(1f).coachAnchor(coach, "settings"), onSettings)
                    TopMenuButton(tr.menuGuide, Modifier.weight(1f).coachAnchor(coach, "guide")) { guideChooser = true }
                    TopMenuButton(tr.menuInfo, Modifier.weight(1f).coachAnchor(coach, "info"), onInfo)
                }
            }
            item {
                Text(tr.homeTagline,
                    color = AraBrand.muted, fontSize = 15.sp)
            }

            // What the app does — plain heading + text, NOT a tappable-looking card.
            item { HeroCard() }

            item { OverlayControl(overlayOn = overlayOn, onToggle = onToggleOverlay, modifier = Modifier.coachAnchor(coach, "overlay")) }

            // The two big sections: exams (with deadlines + pacing) and the plain
            // course library (a catalogue, no deadlines).
            item {
                HomeSectionCard(
                    icon = { ExamIcon() },
                    title = tr.examsTitle,
                    subtitle = tr.examsSub,
                    onClick = onExams,
                    modifier = Modifier.coachAnchor(coach, "exams"),
                )
            }
            item {
                HomeSectionCard(
                    icon = { LibraryIcon() },
                    title = tr.coursesTitle,
                    subtitle = tr.coursesSub,
                    onClick = onCourses,
                    modifier = Modifier.coachAnchor(coach, "courses"),
                )
            }

            item { Spacer(Modifier.height(4.dp)) }
            // Offline / study-only / on-device assurances — plain text at the
            // bottom, deliberately NOT styled as buttons.
            item { AssuranceStrip() }
            item {
                Text(tr.homeFooter,
                    color = AraBrand.muted, fontSize = 12.sp)
            }
        }
      }
    }
    if (guideChooser) {
        AraGuideChooser(
            onQuick = { guideChooser = false; onQuickGuide() },
            onLive = { guideChooser = false; coach.start(steps) },
            onDismiss = { guideChooser = false },
        )
    }
}

/** Asked when the user taps "Guide": pick the slide-based quick guide or the
 *  live, point-at-each-button walkthrough. */
@Composable
private fun AraGuideChooser(onQuick: () -> Unit, onLive: () -> Unit, onDismiss: () -> Unit) {
    val tr = LocalTr.current
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    AraGlassScrim {
      Box(
          Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
      ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(tr.guideAskTitle, color = AraBrand.title, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(tr.guideAskLead, color = AraBrand.muted, fontSize = 14.sp)
            GuideChoiceCard("▤", tr.guideQuick, tr.guideQuickSub, onQuick)
            GuideChoiceCard("◉", tr.guideLive, tr.guideLiveSub, onLive)
            Spacer(Modifier.height(2.dp))
            TopMenuButton(tr.guideBack, Modifier.fillMaxWidth()) { onDismiss() }
        }
      }
    }
}

@Composable
private fun GuideChoiceCard(glyph: String, title: String, sub: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .araGlass(RoundedCornerShape(20.dp), glow = AraBrand.glow, glowRadius = 14.dp)
            .clickable { onClick() }.padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(AraBrand.greenSoft)
                    .border(androidx.compose.foundation.BorderStroke(1.dp, AraBrand.glassEdgeGreen), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(glyph, color = AraBrand.greenDeep, fontSize = 22.sp, fontWeight = FontWeight.Black) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = AraBrand.title, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text(sub, color = AraBrand.muted, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Text("›", color = AraBrand.greenDeep, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ----------------------------------------------------------------------------
// Privacy policy (shared by the first-run consent gate and the Info screen)
// ----------------------------------------------------------------------------

@Composable
private fun PolicyBody() {
    val tr = LocalTr.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PolicyPoint(tr.pol1T, tr.pol1B)
        PolicyPoint(tr.pol2T, tr.pol2B)
        PolicyPoint(tr.pol3T, tr.pol3B)
        PolicyPoint(tr.pol4T, tr.pol4B)
        PolicyPoint(tr.pol5T, tr.pol5B)
    }
}

@Composable
private fun PolicyPoint(title: String, body: String) {
    Box(Modifier.fillMaxWidth().araGlass(RoundedCornerShape(16.dp))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = AraBrand.greenDeep, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(body, color = AraBrand.body, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

/** Honesty disclaimer: the app is a study aid, not a phone-addiction cure, and
 *  real learning still needs class/notes/school activities. Shown before consent
 *  on first run and again on the Info screen — distinct amber styling (not the
 *  green privacy bullets) so it reads as a warning, not another feature blurb. */
@Composable
private fun DisclaimerWarning() {
    val tr = LocalTr.current
    val amber = Color(0xFFFFB020)
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Color(0x1FFFB020))
            .border(androidx.compose.foundation.BorderStroke(1.dp, amber), RoundedCornerShape(16.dp)),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Text("⚠", color = amber, fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr.warnTitle, color = amber, fontSize = 14.sp, fontWeight = FontWeight.Black)
                Text(tr.warnBody, color = AraBrand.body, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

/** First-run gate shown on its OWN full screen, right after the language pick and
 *  BEFORE privacy/consent. Deliberately large and unavoidable: we state plainly
 *  that the app is not a cure and not a replacement for school, so it can never be
 *  read as hidden fine print. */
@Composable
private fun AraDisclaimerGate(onContinue: () -> Unit) {
    val tr = LocalTr.current
    val amber = Color(0xFFFFB020)
    AraGlassScrim {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(22.dp))
                    .background(Color(0x1FFFB020))
                    .border(androidx.compose.foundation.BorderStroke(1.5.dp, amber), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("⚠", color = amber, fontSize = 38.sp) }
            Text(tr.warnTitle, color = amber, fontSize = 32.sp, fontWeight = FontWeight.Black)
            Text(tr.warnBody, color = AraBrand.title, fontSize = 17.sp, lineHeight = 25.sp)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
                    .background(AraBrand.araBrush).clickable { onContinue() },
                contentAlignment = Alignment.Center,
            ) {
                Text(tr.langPickContinue, color = Color(0xFF16110A),
                    fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** First-run step 0: pick the interface language. Shown before consent so every
 *  later screen is already in the user's language. Language-agnostic by design
 *  (native names + flags), so it reads even before a choice is made. */
@Composable
private fun AraLanguagePicker(current: Lang, onPick: (Lang) -> Unit, onContinue: () -> Unit) {
    val tr = LocalTr.current
    AraGlassScrim {
      Box(Modifier.fillMaxSize().clickable(
          interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
          indication = null) {}) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AraLogoMark()
                Spacer(Modifier.width(8.dp))
                Text("AntiROTAid", color = AraBrand.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text(tr.langPickTitle, color = AraBrand.title, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text(tr.langPickLead, color = AraBrand.muted, fontSize = 14.sp, lineHeight = 19.sp)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Lang.entries.forEach { l ->
                    val selected = l == current
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if (selected) AraBrand.greenSoft else Color(0x12FFFFFF))
                            .border(androidx.compose.foundation.BorderStroke(
                                if (selected) 1.5.dp else 1.dp,
                                if (selected) AraBrand.green else AraBrand.glassEdgeGreen),
                                RoundedCornerShape(16.dp))
                            .clickable { onPick(l) }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                                .background(AraBrand.greenSoft)
                                .border(androidx.compose.foundation.BorderStroke(1.dp, AraBrand.glassEdgeGreen), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text(l.badge, color = AraBrand.greenDeep, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp) }
                        Spacer(Modifier.width(14.dp))
                        Text(l.display, color = AraBrand.title, fontSize = 17.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        if (selected) Text("✓", color = AraBrand.greenDeep, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
                    .background(AraBrand.araBrush).clickable { onContinue() },
                contentAlignment = Alignment.Center,
            ) {
                Text(tr.langPickContinue, color = Color(0xFF16110A), fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
      }
    }
}

/** First-run gate: shows the privacy policy and requires an explicit "am înțeles"
 *  before the app can be used. */
@Composable
private fun AraConsent(onAccept: () -> Unit) {
    val tr = LocalTr.current
    var agreed by remember { mutableStateOf(false) }
    AraGlassScrim {
      Box(Modifier.fillMaxSize().clickable(
          interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
          indication = null) {}) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AraLogoMark()
                Spacer(Modifier.width(8.dp))
                Text(tr.privacyBrandSub, color = AraBrand.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Text(tr.privacyTitle, color = AraBrand.title, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text(tr.privacyLead,
                color = AraBrand.muted, fontSize = 14.sp)
            PolicyBody()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { agreed = !agreed }.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(26.dp).clip(RoundedCornerShape(7.dp))
                        .background(if (agreed) AraBrand.green else AraBrand.greenSoft)
                        .border(androidx.compose.foundation.BorderStroke(1.dp, AraBrand.glassEdgeGreen), RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) { if (agreed) Text("✓", color = Color(0xFF16110A), fontWeight = FontWeight.Black) }
                Spacer(Modifier.width(12.dp))
                Text(tr.consentAgree, color = AraBrand.body, fontSize = 14.sp)
            }
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (agreed) AraBrand.araBrush else androidx.compose.ui.graphics.SolidColor(AraBrand.greenSoft))
                    .clickable(enabled = agreed) { onAccept() },
                contentAlignment = Alignment.Center,
            ) {
                Text(tr.consentContinue, color = if (agreed) Color(0xFF16110A) else AraBrand.muted,
                    fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
            // Safe gap so the button isn't flush with the bottom gesture area.
            Spacer(Modifier.height(36.dp))
        }
      }
    }
}

/** Reachable later from Home → Info. Same policy as the consent gate. */
@Composable
private fun AraInfoScreen(onBack: () -> Unit) {
    val tr = LocalTr.current
    AraGlassScrim {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { TopBar(title = tr.infoTitle, onBack = onBack) }
            item {
                Text(tr.infoLead,
                    color = AraBrand.body, fontSize = 14.sp, lineHeight = 20.sp)
            }
            item { DisclaimerWarning() }
            item { PolicyBody() }
            item { Text(tr.infoFooter,
                color = AraBrand.muted, fontSize = 12.sp) }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ----------------------------------------------------------------------------
// Tutorial (image-led: each step shows a real-looking mock of the app)
// ----------------------------------------------------------------------------

@Composable
private fun AraTutorial(onClose: () -> Unit) {
    val tr = LocalTr.current
    var step by remember { mutableStateOf(0) }
    val pages = 6
    AraGlassScrim {
      Box(Modifier.fillMaxSize().clickable(
          interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
          indication = null) {}) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tr.guideTitle, color = AraBrand.title, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Text(tr.guideSkip, color = AraBrand.muted, fontSize = 13.sp,
                    modifier = Modifier.clickable { onClose() })
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    when (step) {
                        0 -> TutorialWelcome(tr)
                        1 -> { MockStudyCard(); TutorialText(tr.tut0T, tr.tut0B) }
                        2 -> { MockExamCard(); TutorialText(tr.tut1T, tr.tut1B) }
                        3 -> { MockLibraryCard(); TutorialText(tr.tut2T, tr.tut2B) }
                        4 -> { MockOverlayCard(); TutorialText(tr.tut3T, tr.tut3B) }
                        else -> { MockActivationCard(); TutorialText(tr.tut4T, tr.tut4B) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(pages) { i ->
                    Box(Modifier.padding(4.dp).size(if (i == step) 10.dp else 7.dp).clip(CircleShape)
                        .background(if (i == step) AraBrand.green else AraBrand.greenSoft))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step > 0) TopMenuButton(tr.guideBack, Modifier.weight(1f)) { step-- }
                Box(
                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)).background(AraBrand.araBrush)
                        .clickable { if (step < pages - 1) step++ else onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(when { step == 0 -> tr.tutStart; step < pages - 1 -> tr.guideNext; else -> tr.guideDone },
                        color = Color(0xFF16110A), fontWeight = FontWeight.Black)
                }
            }
            // Safe gap above the bottom gesture area.
            Spacer(Modifier.height(24.dp))
        }
      }
    }
}

@Composable
private fun TutorialWelcome(tr: Tr) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AraLogoMark()
        }
        Text(tr.tutWelcomeT, color = AraBrand.title, fontSize = 22.sp, fontWeight = FontWeight.Black,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(tr.tutWelcomeB, color = AraBrand.body, fontSize = 14.sp, lineHeight = 20.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun TutorialText(title: String, body: String) {
    Text(title, color = AraBrand.title, fontSize = 19.sp, fontWeight = FontWeight.Black)
    Text(body, color = AraBrand.body, fontSize = 14.sp, lineHeight = 20.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

// --- tutorial mocks (sample content authored here, not the user's files) ------

@Composable private fun MockSectionHeader(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(15.dp).clip(RoundedCornerShape(2.dp)).background(AraBrand.araBrush))
        Spacer(Modifier.width(8.dp))
        Text(text, color = AraBrand.greenDeep, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}
@Composable private fun MockBullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(6.dp).clip(CircleShape).background(AraBrand.green))
        Spacer(Modifier.width(10.dp))
        Text(text, color = AraBrand.body, fontSize = 13.sp, lineHeight = 18.sp)
    }
}
@Composable private fun MockStat(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = AraBrand.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = AraBrand.title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun MockStudyCard() {
    val tr = LocalTr.current
    Box(Modifier.fillMaxWidth().araGlass(RoundedCornerShape(24.dp), glow = AraBrand.glowStrong)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.clip(CircleShape).background(AraBrand.greenSoft).padding(horizontal = 11.dp, vertical = 5.dp)) {
                Text(tr.mTagAlgebra, color = AraBrand.greenDeep, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            }
            Text(tr.mEqTitle, color = AraBrand.title, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Box(Modifier.width(46.dp).height(4.dp).clip(CircleShape).background(AraBrand.araBrush))
            MockSectionHeader(tr.mIdea)
            MockBullet(tr.mEqIdea1)
            MockBullet(tr.mEqIdea2)
            MockSectionHeader(tr.mExample)
            MockBullet("x + 5 = 12  →  x = 7")
        }
    }
}

@Composable private fun MockExamCard() {
    val tr = LocalTr.current
    Box(Modifier.fillMaxWidth().araGlass(RoundedCornerShape(20.dp))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr.mExamTitle, color = AraBrand.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            MockStat(tr.labelData, tr.mDateVal)
            MockStat(tr.labelMaterials, "2")
            MockStat(tr.labelPlan, tr.mPlanVal)
            Spacer(Modifier.height(2.dp))
            MockBullet(tr.mCourse1)
            MockBullet(tr.mCourse2)
        }
    }
}

@Composable private fun MockLibraryCard() {
    val tr = LocalTr.current
    Box(Modifier.fillMaxWidth().araGlass(RoundedCornerShape(22.dp))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr.tagMaterial, color = AraBrand.muted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            Text(tr.mNeural, color = AraBrand.title, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MockMetric("24", tr.unitCards); MockMetric("12", tr.unitSlides); MockMetric("40%", tr.learned)
            }
        }
    }
}
@Composable private fun MockMetric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = AraBrand.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = AraBrand.muted, fontSize = 11.sp)
    }
}

@Composable private fun MockOverlayCard() {
    // A faux "other app" backdrop with a study card floating on top.
    Box(
        Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF101010)),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
                Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(Color(0x22FFFFFF)))
            }
        }
        Box(Modifier.align(Alignment.Center).padding(horizontal = 10.dp)) {
            Box(Modifier.fillMaxWidth().araGlass(RoundedCornerShape(18.dp), glow = AraBrand.glowStrong)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.clip(CircleShape).background(AraBrand.greenSoft).padding(horizontal = 9.dp, vertical = 3.dp)) {
                        Text(LocalTr.current.mTagAlgebra, color = AraBrand.greenDeep, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(LocalTr.current.mBalanceTitle, color = AraBrand.title, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    MockBullet(LocalTr.current.mBalanceBody)
                }
            }
        }
    }
}

@Composable private fun MockActivationCard() {
    // Shows the home "Start AntiROTAid Mode" button so the guide can point at the
    // exact control the user must press, plus a hint that it toggles off again.
    val tr = LocalTr.current
    Box(Modifier.fillMaxWidth().araGlass(RoundedCornerShape(22.dp), glow = AraBrand.glowStrong)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
                    .background(AraBrand.araBrush),
                contentAlignment = Alignment.Center,
            ) {
                Text(tr.overlayOffTitle, color = Color(0xFF16110A),
                    fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp).clip(RoundedCornerShape(7.dp)).background(AraBrand.greenSoft)
                    .border(androidx.compose.foundation.BorderStroke(1.dp, AraBrand.glassEdgeGreen), RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center) { Text("✓", color = AraBrand.greenDeep, fontSize = 12.sp, fontWeight = FontWeight.Black) }
                Spacer(Modifier.width(10.dp))
                Text(tr.overlayOffSub, color = AraBrand.body, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

/** Minimal stroked line icons matching the design mockups. Drawn with Canvas so
 *  no icon-font dependency is needed and they stay crisp at any size. */
@Composable
private fun ChipIcon(kind: String, tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        val s = size.minDimension
        val w = s * 0.085f
        val st = androidx.compose.ui.graphics.drawscope.Stroke(
            width = w,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
        when (kind) {
            "offline" -> {
                val bx = s * 0.5f; val by = s * 0.70f
                listOf(0.40f, 0.27f, 0.14f).forEach { r ->
                    val rr = r * s
                    drawArc(tint, 200f, 140f, false,
                        topLeft = Offset(bx - rr, by - rr),
                        size = androidx.compose.ui.geometry.Size(rr * 2, rr * 2),
                        style = st)
                }
                drawCircle(tint, radius = s * 0.05f, center = Offset(bx, by))
                drawLine(tint, Offset(s * 0.16f, s * 0.14f), Offset(s * 0.84f, s * 0.86f),
                    strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
            "study" -> {
                val cx = s * 0.5f
                drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx, s * 0.30f)
                    cubicTo(s * 0.36f, s * 0.21f, s * 0.23f, s * 0.23f, s * 0.16f, s * 0.27f)
                    lineTo(s * 0.16f, s * 0.73f)
                    cubicTo(s * 0.23f, s * 0.69f, s * 0.36f, s * 0.68f, cx, s * 0.76f)
                }, tint, style = st)
                drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx, s * 0.30f)
                    cubicTo(s * 0.64f, s * 0.21f, s * 0.77f, s * 0.23f, s * 0.84f, s * 0.27f)
                    lineTo(s * 0.84f, s * 0.73f)
                    cubicTo(s * 0.77f, s * 0.69f, s * 0.64f, s * 0.68f, cx, s * 0.76f)
                }, tint, style = st)
            }
            "local" -> {
                drawRoundRect(tint,
                    topLeft = Offset(s * 0.32f, s * 0.13f),
                    size = androidx.compose.ui.geometry.Size(s * 0.36f, s * 0.74f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.08f, s * 0.08f),
                    style = st)
                drawLine(tint, Offset(s * 0.44f, s * 0.75f), Offset(s * 0.56f, s * 0.75f),
                    strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
    }
}

/** Faceted angular "ARA" wordmark drawn with Canvas paths (sharp miter joins +
 *  metallic gold gradient), matching the geometric logo in the design mockups.
 *  No SVG asset needed; stays crisp at any size. */
@Composable
internal fun AraLogoMark(modifier: Modifier = Modifier) {
    // The actual ARA wordmark from the design (gold mark on transparent bg).
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(id = com.mindlift.R.drawable.ara_wordmark),
        contentDescription = "ARA",
        modifier = modifier.height(30.dp),
        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
    )
}

@Composable
private fun HeroCard() {
    // Plain heading + body — no glass box, no icon tile, nothing that reads as a
    // tappable button. Just an explanation of what the app does.
    val tr = LocalTr.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr.heroTitle,
            color = AraBrand.title, fontSize = 22.sp, fontWeight = FontWeight.Black, lineHeight = 28.sp)
        Text(tr.heroBody,
            color = AraBrand.body, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

/** Flat offline / study-only / on-device assurances. Inline icon+text, no box or
 *  border — these are statements, not buttons. */
@Composable
private fun AssuranceStrip() {
    val tr = LocalTr.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AssuranceItem("offline", tr.chipOfflineT)
        AssuranceItem("study", tr.chipStudyT)
        AssuranceItem("local", tr.chipLocalT)
    }
}

@Composable
private fun AssuranceItem(kind: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ChipIcon(kind, AraBrand.greenDeep)
        Text(label, color = AraBrand.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** A light band that sweeps across text (the "ARA" wordmark) for a holographic
 *  shine. Pure animation over a fixed gradient — safe and cheap. */
@Composable
private fun rememberAraShine(): Brush {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "araShine")
    val p by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(2800, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "p",
    )
    val span = 320f
    val x = -span + p * (span * 2f)
    return Brush.linearGradient(
        listOf(AraBrand.teal, AraBrand.greenDeep, Color.White, AraBrand.green, AraBrand.teal),
        start = Offset(x, 0f),
        end = Offset(x + span, 0f),
    )
}

/** A small deterministic node-graph drawn with Canvas — the product's signature
 *  "cards are a graph" motif. Same seed -> same shape (stable per course), but it
 *  is ALIVE: nodes breathe with a soft halo and light pulses travel the edges like
 *  data moving through a neural net. */
@Composable
private fun Constellation(seed: Int, modifier: Modifier = Modifier, progress: Float = -1f) {
    val node = AraBrand.green
    val edge = AraBrand.glassEdgeGreen
    val spark = AraBrand.greenDeep
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "constellation")
    val phase by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(3800, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "phase",
    )
    // Animate the "lit" frontier smoothly toward the real progress so the graph
    // visibly grows as you learn.
    val litFrac by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (progress < 0f) 1f else progress.coerceIn(0f, 1f),
        animationSpec = androidx.compose.animation.core.tween(900),
        label = "lit",
    )
    Canvas(modifier) {
        val rnd = java.util.Random(seed.toLong())
        val n = 6
        val pts = ArrayList<Offset>()
        repeat(n) {
            pts.add(Offset(
                (0.12f + rnd.nextFloat() * 0.76f) * size.width,
                (0.12f + rnd.nextFloat() * 0.76f) * size.height,
            ))
        }
        // Gamified: nodes "light up" as the learner progresses. progress<0 = pure
        // decoration (all lit).
        val litCount = if (progress < 0f) n else Math.round(litFrac * n)
        fun lit(i: Int) = i < litCount
        val edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 0 to 3, 2 to 5)
        edges.forEach { (a, b) ->
            val on = lit(a) && lit(b)
            drawLine(if (on) edge else edge.copy(alpha = 0.25f), pts[a], pts[b], strokeWidth = 2f)
        }
        // Light pulses flow only along "learned" edges — the active part of the net.
        edges.forEachIndexed { idx, pair ->
            if (!(lit(pair.first) && lit(pair.second))) return@forEachIndexed
            val a = pts[pair.first]; val b = pts[pair.second]
            val tt = (phase + idx * 0.137f) % 1f
            val p = Offset(a.x + (b.x - a.x) * tt, a.y + (b.y - a.y) * tt)
            drawCircle(spark.copy(alpha = 0.20f), radius = 6f, center = p)
            drawCircle(spark, radius = 2.4f, center = p)
        }
        pts.forEachIndexed { i, p ->
            val baseR = if (i == 0) 5.5f else 3.5f
            if (lit(i)) {
                val breath = 0.5f + 0.5f * kotlin.math.sin((2.0 * Math.PI * (phase + i / n.toFloat())).toFloat())
                val r = baseR * (0.82f + 0.30f * breath)
                drawCircle(node.copy(alpha = 0.16f), radius = r * 2.6f, center = p)
                drawCircle(node, radius = r, center = p)
            } else {
                drawCircle(node.copy(alpha = 0.30f), radius = baseR * 0.8f, center = p)
            }
        }
    }
}

/** Exam-countdown ring: a glowing arc that fills as the exam approaches and
 *  breathes red when it's urgent. */
@Composable
private fun CountdownRing(fill: Float, urgent: Boolean, modifier: Modifier = Modifier) {
    val ringColor = if (urgent) AraBrand.danger else AraBrand.greenDeep
    val track = AraBrand.glassEdgeGreen.copy(alpha = 0.22f)
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "ring")
    val pulse by t.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "ringPulse",
    )
    val alpha = if (urgent) pulse else 0.92f
    Canvas(modifier) {
        val sw = 5.dp.toPx()
        val inset = sw / 2f
        val topLeft = Offset(inset, inset)
        val arcSize = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw)
        drawArc(track, -90f, 360f, false, topLeft = topLeft, size = arcSize,
            style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
        drawArc(ringColor.copy(alpha = alpha), -90f, 360f * fill.coerceIn(0f, 1f), false,
            topLeft = topLeft, size = arcSize,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

@Composable
private fun EmptyCoursesCard(onRestoreDemos: () -> Unit) {
    val tr = LocalTr.current
    Box(Modifier.fillMaxWidth().araGlass(RoundedCornerShape(20.dp))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Constellation(seed = 3, modifier = Modifier.size(72.dp))
            Text(tr.coursesEmpty1, color = AraBrand.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(tr.coursesEmpty2,
                color = AraBrand.body, fontSize = 13.sp, lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            OutlinedButton(
                onClick = onRestoreDemos,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AraBrand.glassEdgeGreen),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AraBrand.greenDeep),
            ) { Text(tr.restoreDemos, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// Hand-drawn line icons (no emoji) for the two home sections.
@Composable
private fun ExamIcon(modifier: Modifier = Modifier.size(30.dp)) {
    val c = AraBrand.greenDeep
    Canvas(modifier) {
        val w = size.width; val h = size.height; val st = w * 0.075f
        val cap = androidx.compose.ui.graphics.StrokeCap.Round
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(st, cap = cap,
            join = androidx.compose.ui.graphics.StrokeJoin.Round)
        drawRoundRect(c, topLeft = Offset(w * 0.13f, h * 0.20f),
            size = androidx.compose.ui.geometry.Size(w * 0.74f, h * 0.67f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.10f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(st))
        drawLine(c, Offset(w * 0.13f, h * 0.39f), Offset(w * 0.87f, h * 0.39f), st)
        drawLine(c, Offset(w * 0.35f, h * 0.10f), Offset(w * 0.35f, h * 0.26f), st, cap = cap)
        drawLine(c, Offset(w * 0.65f, h * 0.10f), Offset(w * 0.65f, h * 0.26f), st, cap = cap)
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.33f, h * 0.62f); lineTo(w * 0.45f, h * 0.74f); lineTo(w * 0.70f, h * 0.50f)
        }
        drawPath(p, c, style = stroke)
    }
}

@Composable
private fun LibraryIcon(modifier: Modifier = Modifier.size(30.dp)) {
    val c = AraBrand.greenDeep
    Canvas(modifier) {
        val w = size.width; val h = size.height; val st = w * 0.075f
        val bh = h * 0.165f
        listOf(h * 0.18f, h * 0.42f, h * 0.66f).forEach { ty ->
            drawRoundRect(c, topLeft = Offset(w * 0.16f, ty),
                size = androidx.compose.ui.geometry.Size(w * 0.68f, bh),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(st))
        }
    }
}

@Composable
private fun TopMenuButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(42.dp).clip(RoundedCornerShape(12.dp)).background(AraBrand.greenSoft)
            .border(androidx.compose.foundation.BorderStroke(1.dp, AraBrand.glassEdgeGreen), RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = AraBrand.greenDeep, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

/** A big home entry tile (Examenele mele / Cursurile mele). */
@Composable
private fun HomeSectionCard(icon: @Composable () -> Unit, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth()
            .araGlass(RoundedCornerShape(22.dp), glow = AraBrand.glow, glowRadius = 18.dp)
            .clickable { onClick() }
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(AraBrand.greenSoft)
                    .border(androidx.compose.foundation.BorderStroke(1.dp, AraBrand.glassEdgeGreen), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = AraBrand.title, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = AraBrand.muted, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            // A clear "Open" affordance instead of a bare arrow — these tiles are
            // buttons, so they should read like one.
            val tr = LocalTr.current
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(AraBrand.greenSoft)
                    .border(androidx.compose.foundation.BorderStroke(1.dp, AraBrand.glassEdgeGreen), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(tr.open, color = AraBrand.greenDeep, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** The course library: every imported/demo material as a catalogue. No deadlines
 *  here — those live on exams. */
@Composable
private fun AraCoursesScreen(
    lessons: List<AraDemo.Lesson>,
    store: AraStore,
    planStore: AraPlanStore,
    refresh: Int,
    examNamesFor: (String) -> List<String>,
    onAddCourse: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onStudy: (String) -> Unit,
    onRestoreDemos: () -> Unit,
    onBack: () -> Unit,
) {
    val tr = LocalTr.current
    val coach = rememberCoachState()
    val steps = remember(tr) {
        listOf(CoachStep("import", tr.cCoursesImport), CoachStep("card", tr.cCoursesCard))
    }
    var sort by remember { mutableStateOf("name") }
    // Nearest (soonest upcoming, else latest past) exam date per course — for the
    // "by exam" sort. Courses in no exam sort last.
    val nearestExam = remember(refresh) {
        val m = HashMap<String, Long>()
        planStore.exams().forEach { e ->
            e.materialIds.forEach { id ->
                val cur = m[id]
                if (cur == null || e.dateMs < cur) m[id] = e.dateMs
            }
        }
        m
    }
    val sorted = remember(lessons, sort, refresh) {
        when (sort) {
            "recent" -> lessons.sortedByDescending { it.addedMs }
            "progress" -> lessons.sortedByDescending { store.progress(it.id) }
            "exam" -> lessons.sortedBy { nearestExam[it.id] ?: Long.MAX_VALUE }
            else -> lessons.sortedBy { it.title.lowercase() }
        }
    }
    CoachHost(coach) {
    AraGlassScrim {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { TopBar(title = tr.coursesTitle, onBack = onBack, onHelp = { coach.start(steps) }) }
            item {
                Box(
                    Modifier.fillMaxWidth().height(50.dp).coachAnchor(coach, "import")
                        .clip(RoundedCornerShape(14.dp)).background(AraBrand.araBrush)
                        .clickable { onAddCourse() },
                    contentAlignment = Alignment.Center,
                ) { Text(tr.importCourse, color = Color(0xFF16110A), fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            }
            item {
                Text(String.format(tr.libraryCountFmt, lessons.size),
                    color = AraBrand.muted, fontSize = 12.sp)
            }
            item {
                SortBar(current = sort, onPick = { sort = it }, options = listOf(
                    "name" to tr.sortName, "recent" to tr.sortRecent,
                    "progress" to tr.sortProgress, "exam" to tr.sortExam,
                ))
            }
            if (sorted.isEmpty()) {
                item { EmptyCoursesCard(onRestoreDemos = onRestoreDemos) }
            } else {
                itemsIndexed(sorted, key = { _, l -> l.id }) { i, lesson ->
                    Box(if (i == 0) Modifier.coachAnchor(coach, "card") else Modifier) {
                        HomeCourseCard(lesson = lesson, store = store, planStore = planStore,
                            refresh = refresh, onStudy = onStudy, onDetail = onOpenDetail, library = true,
                            examNames = examNamesFor(lesson.id))
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
    }
}

/** A horizontal row of sort chips with a leading "Sort" label. */
@Composable
private fun SortBar(current: String, onPick: (String) -> Unit, options: List<Pair<String, String>>) {
    val tr = LocalTr.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tr.sortBy, color = AraBrand.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        options.forEach { (key, label) ->
            AraChip(label, current == key) { onPick(key) }
        }
    }
}

@Composable
private fun OverlayControl(overlayOn: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val tr = LocalTr.current
    // Slow breathing glow on the primary action — the signature "alive/futuristic"
    // accent. One animated element keeps it tasteful and cheap.
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "ctaPulse")
    val glowA by pulse.animateFloat(
        initialValue = 0.40f, targetValue = 0.95f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(2200, easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "ctaGlow",
    )
    val ctaGlow = (if (overlayOn) AraBrand.danger else AraBrand.green).copy(alpha = glowA * 0.7f)
    val ghostShape = RoundedCornerShape(22.dp)
    Box(
        modifier.fillMaxWidth().height(78.dp)
            .shadow(28.dp, ghostShape, spotColor = ctaGlow, ambientColor = ctaGlow)
            .clip(ghostShape)
            .background(
                if (overlayOn) Brush.horizontalGradient(listOf(Color(0xFFEF5350), Color(0xFFD64545)))
                else AraBrand.araBrush
            )
            .border(androidx.compose.foundation.BorderStroke(1.dp, AraBrand.glassEdgeGreen), ghostShape)
            .clickable { onToggle() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(if (overlayOn) tr.overlayOnTitle else tr.overlayOffTitle,
                color = if (overlayOn) Color.White else Color(0xFF16110A),
                fontWeight = FontWeight.Black, fontSize = 16.sp)
            Text(if (overlayOn) tr.overlayOnSub else tr.overlayOffSub,
                color = if (overlayOn) Color.White.copy(alpha = 0.86f) else Color(0xFF372400),
                fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun FileTypeChip(filename: String) {
    val ext = filename.substringAfterLast('.', "").uppercase().ifBlank { "TXT" }
    val label = when (ext) {
        "PPTX", "PPT" -> "PPTX"
        "DOCX", "DOC" -> "DOCX"
        "PDF" -> "PDF"
        "PNG", "JPG", "JPEG", "WEBP", "GIF", "BMP" -> "IMG"
        "MD", "MARKDOWN" -> "MD"
        else -> "TXT"
    }
    Box(Modifier.clip(RoundedCornerShape(5.dp)).background(AraBrand.greenSoft)
        .padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(label, color = AraBrand.greenDeep, fontSize = 9.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
    }
}

@Composable
private fun HomeCourseCard(
    lesson: AraDemo.Lesson,
    store: AraStore,
    planStore: AraPlanStore,
    refresh: Int,
    onStudy: (String) -> Unit,
    onDetail: (String) -> Unit,
    // In the library catalogue we hide everything deadline-related (tags, ring,
    // deadline metric) — deadlines belong to exams, not to materials.
    library: Boolean = false,
    // Names of the exams this course is part of (library only). Empty => shown as
    // "not assigned to any exam".
    examNames: List<String> = emptyList(),
) {
    val tr = LocalTr.current
    // Keyed on `refresh` so a data change re-reads deadline/progress from the store.
    val plan = remember(lesson.id, refresh) { AraPlan.of(store, lesson.id) }
    Box(
        Modifier.fillMaxWidth()
            .araGlass(RoundedCornerShape(26.dp), glow = AraBrand.glowStrong, glowRadius = 24.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val (tag, tagColor) = when {
                    library -> tr.tagMaterial to AraBrand.muted
                    plan.urgent -> tr.tagUrgent to AraBrand.danger
                    plan.deadlineMs > 0 -> tr.tagImportant to AraBrand.greenDeep
                    else -> tr.tagMaterial to AraBrand.muted
                }
                Text(
                    tag,
                    color = tagColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.7.sp,
                )
                if (lesson.id.startsWith("demo_")) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(7.dp)).background(AraBrand.greenSoft)
                            .border(androidx.compose.foundation.BorderStroke(1.dp, AraBrand.glassEdgeGreen), RoundedCornerShape(7.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text("DEMO", color = AraBrand.greenDeep, fontSize = 9.sp,
                            fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .border(androidx.compose.foundation.BorderStroke(1.dp, AraBrand.glassEdgeGreen), RoundedCornerShape(12.dp))
                        .clickable { onDetail(lesson.id) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(tr.courseDetails + "  ›", color = AraBrand.greenDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(108.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(Color(0x1FFFFFFF), Color(0x08000000))))
                        .border(androidx.compose.foundation.BorderStroke(1.dp, AraBrand.glassEdgeGreen), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    // Exam-countdown ring: fills as the exam nears, pulses red when urgent.
                    if (!library && plan.deadlineMs > 0) {
                        CountdownRing(
                            fill = (1f - (plan.daysLeft / 14f)).coerceIn(0.06f, 1f),
                            urgent = plan.urgent,
                            modifier = Modifier.fillMaxSize().padding(6.dp),
                        )
                    }
                    Constellation(
                        seed = lesson.id.hashCode(),
                        progress = plan.fraction,
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                    )
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        lesson.title,
                        color = AraBrand.title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 26.sp,
                    )
                    Text(
                        lesson.filename.ifBlank { tr.localMaterial },
                        color = AraBrand.muted,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RealFileTypePill(lesson.filename)
                    }
                }
            }

            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x10FFFFFF))
                    .border(androidx.compose.foundation.BorderStroke(1.dp, AraBrand.glassEdge), RoundedCornerShape(18.dp))
                    .padding(vertical = 12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    CourseMetric("▤", if (plan.total > 0) "${plan.total}" else "—", tr.unitCards)
                    // Real logical-unit count once known (cached at import/open);
                    // fall back to the rough estimate only before it's counted.
                    val units = remember(lesson.id, refresh) { planStore.unitCount(lesson.id) }
                    CourseMetric("▥", if (units > 0) "$units" else cardsHint(lesson.filename, plan.total), tr.unitSlides)
                    // Deadline only outside the library (it belongs to exams).
                    if (!library) {
                        val dlShort = when {
                            plan.deadlineMs <= 0 -> "—"
                            plan.daysLeft <= 1 -> tr.todayTomorrow
                            else -> String.format(tr.daysShortFmt, plan.daysLeft)
                        }
                        CourseMetric("◷", dlShort, "deadline")
                    } else {
                        val pct = (plan.fraction * 100).toInt()
                        CourseMetric("◐", "$pct%", tr.learned)
                    }
                }
            }

            if (library) {
                // Which exams this material belongs to — or that it's unassigned.
                if (examNames.isEmpty()) {
                    Text(tr.notInAnyExam, color = AraBrand.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                } else {
                    Text("${tr.inExamsLabel} ${examNames.joinToString(", ")}",
                        color = AraBrand.greenDeep, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (library) {
                val passes = remember(lesson.id, refresh) { store.materialPassesCached(lesson.id) }
                val resume = remember(lesson.id, refresh) { store.materialHasResume(lesson.id) }
                if (passes > 0 || resume) {
                    Text(
                        String.format(tr.passesFmt, passes) + if (resume) "  ·  ↩ ${tr.resumeHint}" else "",
                        color = AraBrand.greenDeep, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { plan.fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = AraBrand.green,
                trackColor = Color(0x18FFFFFF),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val primaryShape = RoundedCornerShape(18.dp)
                Box(
                    Modifier.weight(1.15f).height(58.dp)
                        .shadow(18.dp, primaryShape, spotColor = AraBrand.glowStrong, ambientColor = AraBrand.glowStrong)
                        .clip(primaryShape)
                        .background(AraBrand.araBrush)
                        .clickable { onStudy(lesson.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tr.openCards, color = Color(0xFF16110A), fontWeight = FontWeight.Black, fontSize = 15.sp)
                        Text("scroll study feed", color = Color(0xFF372400), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                OutlinedButton(
                    onClick = { onDetail(lesson.id) },
                    modifier = Modifier.weight(0.85f).height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AraBrand.glassEdgeGreen),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AraBrand.body),
                ) {
                    Text(tr.details, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** One colored pill reflecting the course's ACTUAL file type. */
@Composable
private fun RealFileTypePill(filename: String) {
    val ext = filename.substringAfterLast('.', "").lowercase()
    val (label, tint) = when (ext) {
        "pptx", "ppt" -> "PPTX" to Color(0xFFFF5533)
        "pdf" -> "PDF" to Color(0xFFFF3B30)
        "docx", "doc" -> "DOCX" to Color(0xFF4C8DFF)
        "png", "jpg", "jpeg", "webp", "gif", "bmp", "tiff", "tif" -> "IMG" to Color(0xFF22C55E)
        "md", "markdown" -> "MD" to Color(0xFF63B3FF)
        else -> "TXT" to Color(0xFF63B3FF)
    }
    FileTypePill(label, tint)
}

@Composable
private fun FileTypePill(label: String, tint: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.12f))
            .border(androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.55f)), RoundedCornerShape(10.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CourseMetric(icon: String, value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, color = AraBrand.body, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(7.dp))
        Column {
            Text(value, color = AraBrand.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, color = AraBrand.muted, fontSize = 11.sp, maxLines = 1)
        }
    }
}

private fun cardsHint(filename: String, total: Int): String {
    val ext = filename.substringAfterLast('.', "").lowercase()
    return when {
        ext.contains("ppt") && total > 0 -> maxOf(1, total / 3).toString()
        ext.contains("pdf") && total > 0 -> maxOf(1, total / 2).toString()
        total > 0 -> maxOf(1, total / 4).toString()
        else -> "—"
    }
}



// ----------------------------------------------------------------------------
// Course detail
// ----------------------------------------------------------------------------

@Composable
private fun AraCourseDetail(
    lesson: AraDemo.Lesson,
    store: AraStore,
    engine: AraEngine,
    planStore: AraPlanStore,
    refresh: Int,
    cards: List<AraCard>,
    onStudy: () -> Unit,
    onDelete: () -> Unit,
    onSettingsChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val tr = LocalTr.current
    var confirmDelete by remember { mutableStateOf(false) }
    val coach = rememberCoachState()
    val steps = remember(tr) {
        listOf(
            CoachStep("dProgress", tr.cDetailProgress),
            CoachStep("dStudy", tr.cDetailStudy),
            CoachStep("dSettings", tr.cDetailSettings),
        )
    }
    // Keyed on `refresh` so a newly set deadline shows without leaving the screen.
    val plan = remember(lesson.id, refresh) { AraPlan.of(store, lesson.id) }
    // Cache this deck's card ids so the library list can show passes cheaply.
    LaunchedEffect(cards) { if (cards.isNotEmpty()) store.saveCardIds(lesson.id, cards.map { it.cardId }) }
    CoachHost(coach) {
    AraGlassScrim {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { TopBar(title = tr.courseDetails, onBack = onBack, onHelp = { coach.start(steps) }) }

            item {
                Box(Modifier.fillMaxWidth().coachAnchor(coach, "dProgress").araGlass(RoundedCornerShape(20.dp))) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(lesson.title, color = AraBrand.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        // No deadline / per-day pacing here — those belong to exams.
                        // The library detail is just the material + study progress.
                        StatRow(tr.cardsLabel, (if (cards.isNotEmpty()) cards.size else plan.total).let { if (it > 0) "$it" else "—" })
                        StatRow(tr.detailProgress, "${(plan.fraction * 100).toInt()}%")
                        // Full passes: per-card definition from the spec — how many
                        // times the WHOLE deck was completed (every card >= once).
                        val cardIds = remember(cards) { cards.map { it.cardId } }
                        val passes = if (cardIds.isNotEmpty()) store.materialPasses(lesson.id, cardIds) else store.passes(lesson.id)
                        StatRow(tr.detailFullPasses, "${passes}×")
                        StatRow(tr.learnedPace, if (plan.learnedPace > 0) String.format(tr.learnedPaceFmt, plan.learnedPace) else tr.learning)
                        if (store.materialHasResume(lesson.id)) {
                            Text("↩  ${tr.resumeHint}", color = AraBrand.green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        LinearProgressIndicator(
                            progress = { plan.fraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = AraBrand.green, trackColor = Color(0x14000000),
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                        )
                    }
                }
            }

            item {
                val sShape = RoundedCornerShape(16.dp)
                Box(
                    Modifier.fillMaxWidth().height(54.dp).coachAnchor(coach, "dStudy")
                        .shadow(14.dp, sShape, spotColor = AraBrand.glow, ambientColor = AraBrand.glow)
                        .clip(sShape).background(AraBrand.araBrush)
                        .clickable { onStudy() },
                    contentAlignment = Alignment.Center,
                ) { Text(tr.openCards, color = Color(0xFF16110A), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }

            item {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AraBrand.danger),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AraBrand.danger),
                ) { Text(if (confirmDelete) tr.confirmAgain else tr.deleteCourse) }
            }
            if (confirmDelete) {
                item {
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Text(tr.confirmDeletePermanent, color = AraBrand.danger, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                val sourceType = remember(lesson.id) { AraDemo.sourceType(lesson.filename) }
                val options by produceState(initialValue = org.json.JSONObject(), lesson.id) {
                    value = withContext(Dispatchers.Default) { engine.materialOptions(sourceType) }
                }
                val current = remember(lesson.id, refresh) { planStore.materialSettings(lesson.id) }
                Box(Modifier.coachAnchor(coach, "dSettings")) {
                    MaterialSettingsCard(
                        options = options,
                        current = current,
                        onChange = { updated ->
                            planStore.setMaterialSettings(lesson.id, updated)
                            onSettingsChanged()
                        },
                    )
                }
            }

            item {
                Text(String.format(tr.courseCardsFmt, cards.size), color = AraBrand.title,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            items(cards, key = { it.cardId }) { c ->
                DetailCardRow(c)
            }
        }
    }
    }
}

@Composable
private fun DetailCardRow(card: AraCard) {
    val tr = LocalTr.current
    Box(Modifier.fillMaxWidth().araGlass(RoundedCornerShape(16.dp))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(araLocalizeSourceLabel(tr, card.sourceLabel).ifBlank { card.title.ifBlank { tr.cardFallback } }.uppercase(),
                color = AraBrand.greenDeep, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
            if (card.title.isNotBlank()) {
                Text(card.title, color = AraBrand.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            // Drop the title if the body repeats it as its first line, so the
            // preview doesn't show the heading twice.
            val previewBody = bodyWithoutLeadingTitle(card.body, card.title)
            val preview = if (card.isImage) tr.visualSlideTag
                else previewBody.take(160).let { if (previewBody.length > 160) "$it…" else it }
            if (preview.isNotBlank()) {
                Text(preview, color = AraBrand.body, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

/** Localize the engine's material-option group label by its stable key (the
 *  engine ships Romanian labels; we map by key so it follows the app language). */
private fun araOptionLabel(tr: Tr, key: String, fallback: String): String = when (key) {
    "appearance" -> tr.optAppearance
    "card_size" -> tr.optCardSize
    else -> fallback
}

/** Localize the engine's unit noun (slide / poză / paragraf). */
private fun araUnitWord(tr: Tr, raw: String): String = when (raw.trim().lowercase()) {
    "slide" -> tr.unitSlideW
    "poză", "poza", "image", "imagine" -> tr.unitImageW
    "paragraf", "paragraph" -> tr.unitParagraphW
    else -> raw
}

private fun araChoiceLabel(tr: Tr, key: String): String = when (key) {
    "pdf_as_is" -> tr.chPdfPages
    "pdf_paragraphs" -> tr.chPdfParagraphs
    "pptx_as_is" -> tr.chPptxAsIs
    "docx_paragraphs" -> tr.chDocxParagraphs
    "image_as_is" -> tr.chImageAsIs
    else -> key
}

/** One-line explanation of a split mode (shown under the chips / as info). */
private fun araChoiceDesc(tr: Tr, key: String): String = when (key) {
    "pdf_as_is" -> tr.descPdfPages
    "pdf_paragraphs" -> tr.descPdfParagraphs
    "pptx_as_is" -> tr.descPptxAsIs
    "docx_paragraphs" -> tr.descDocxParagraphs
    "image_as_is" -> tr.descImageAsIs
    else -> ""
}

/** Text-field colors that stay readable on the dark glass (the default M3 input
 *  text was dark-on-dark and effectively invisible). */
@Composable
private fun araFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedTextColor = AraBrand.title,
    unfocusedTextColor = AraBrand.title,
    cursorColor = AraBrand.green,
    focusedBorderColor = AraBrand.green,
    unfocusedBorderColor = AraBrand.glassEdgeGreen,
    focusedLabelColor = AraBrand.greenDeep,
    unfocusedLabelColor = AraBrand.muted,
)

@Composable
private fun MaterialSettingsCard(
    options: org.json.JSONObject,
    current: AraMaterialSettings,
    onChange: (AraMaterialSettings) -> Unit,
) {
    val tr = LocalTr.current
    val opts = options.optJSONArray("options") ?: return
    Box(Modifier.fillMaxWidth().araGlass(RoundedCornerShape(20.dp))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(tr.howCardsAppear, color = AraBrand.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(tr.coachModesBody, color = AraBrand.muted, fontSize = 12.sp, lineHeight = 16.sp)

            for (i in 0 until opts.length()) {
                val o = opts.optJSONObject(i) ?: continue
                val key = o.optString("key")
                val label = araOptionLabel(tr, key, o.optString("label", key))
                val choices = o.optJSONArray("choices") ?: continue
                val default = o.optString("default", if (choices.length() > 0) choices.optString(0) else "")
                // The current value, falling back to the file type's default when
                // nothing is stored yet (appearance defaults to "").
                val selected = (if (key == "appearance") current.appearance else current.cardSize)
                    .ifBlank { default }
                // Only PDF has a real choice (2 modes). For a single fixed mode we
                // show what it is, but no picker — there is nothing to choose.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(label, color = AraBrand.body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    if (choices.length() >= 2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (j in 0 until choices.length()) {
                                val value = choices.optString(j)
                                AraChip(araChoiceLabel(tr, value), selected == value) {
                                    onChange(if (key == "appearance") current.copy(appearance = value)
                                             else current.copy(cardSize = value))
                                }
                            }
                        }
                    }
                    araChoiceDesc(tr, selected).takeIf { it.isNotBlank() }?.let {
                        Text(it, color = AraBrand.muted, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }
            }
            // No "repetiții" here anymore: how many times to cover the material is an
            // EXAM-level knob (set on the exam), so this card only governs how the
            // file is split into cards. See ExamCoverageControl.
        }
    }
}

@Composable
private fun AraChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AraBrand.green else AraBrand.greenSoft)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Color(0xFF16110A) else AraBrand.body,
            fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AraBrand.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = AraBrand.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ----------------------------------------------------------------------------
// Exams (group materials under an exam date + show the study plan)
// ----------------------------------------------------------------------------

@Composable
private fun AraExamsScreen(
    exams: List<AraExam>,
    allCourses: List<AraDemo.Lesson>,
    weeklySet: Boolean,
    dailyPhoneMin: Double,
    courseTitle: (String) -> String,
    planJson: org.json.JSONObject,
    pickDate: ((Long) -> Unit) -> Unit,
    onSaveExam: (AraExam) -> Unit,
    onDeleteExam: (String) -> Unit,
    onToggleMaterial: (String, String) -> Unit,
    onImportToExam: (String) -> Unit,
    materialSettings: (String) -> AraMaterialSettings,
    onMaterialSettings: (String, AraMaterialSettings) -> Unit,
    loadMaterialOptions: (String) -> org.json.JSONObject,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    var nameDialog by remember { mutableStateOf(false) }
    var pickFor by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf("date") }
    val sortedExams = remember(exams, sort) {
        if (sort == "name") exams.sortedBy { it.name.lowercase() }
        else exams.sortedBy { if (it.dateMs <= 0) Long.MAX_VALUE else it.dateMs }
    }
    val planByExam = remember(planJson) {
        val m = HashMap<String, org.json.JSONObject>()
        planJson.optJSONArray("exams")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { m[it.optString("id")] = it }
        }
        m
    }
    val tr = LocalTr.current
    val coach = rememberCoachState()
    val steps = remember(tr) {
        listOf(CoachStep("new", tr.cExamsNew), CoachStep("card", tr.cExamsCard))
    }
    val fmt = remember(tr) { java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale(tr.localeCode)) }

    CoachHost(coach) {
    AraGlassScrim {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { TopBar(title = tr.examsTitle, onBack = onBack, onHelp = { coach.start(steps) }) }

            if (!weeklySet) {
                item {
                    Box(Modifier.fillMaxWidth().araGlass(RoundedCornerShape(14.dp))) {
                        Text(tr.examsEstimate,
                            color = AraBrand.muted, fontSize = 12.sp, modifier = Modifier.padding(14.dp))
                    }
                }
            }

            item {
                Box(
                    Modifier.fillMaxWidth().height(50.dp).coachAnchor(coach, "new")
                        .clip(RoundedCornerShape(14.dp)).background(AraBrand.araBrush)
                        .clickable { nameDialog = true },
                    contentAlignment = Alignment.Center,
                ) { Text("＋  " + tr.examNew, color = Color(0xFF16110A), fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            }

            if (exams.size > 1) {
                item {
                    SortBar(current = sort, onPick = { sort = it }, options = listOf(
                        "date" to tr.sortDate, "name" to tr.sortName,
                    ))
                }
            }

            if (sortedExams.isEmpty()) {
                item {
                    Text(tr.examsEmpty,
                        color = AraBrand.muted, fontSize = 13.sp)
                }
            } else {
                itemsIndexed(sortedExams, key = { _, e -> e.id }) { i, e ->
                  Box(if (i == 0) Modifier.coachAnchor(coach, "card") else Modifier) {
                    ExamCard(
                        exam = e,
                        plan = planByExam[e.id],
                        dateText = if (e.dateMs > 0) fmt.format(java.util.Date(e.dateMs)) else tr.noDate,
                        dailyPhoneMin = dailyPhoneMin,
                        courseTitle = courseTitle,
                        onAddExisting = { pickFor = e.id },
                        onImport = { onImportToExam(e.id) },
                        onRemoveMaterial = { m -> onToggleMaterial(e.id, m) },
                        onDelete = { onDeleteExam(e.id) },
                        onPickDate = { pickDate { ms -> onSaveExam(e.copy(dateMs = ms)) } },
                        onCoverage = { onSaveExam(it) },
                        materialSettings = materialSettings,
                        onMaterialSettings = onMaterialSettings,
                        loadMaterialOptions = loadMaterialOptions,
                        onOpenSettings = onOpenSettings,
                    )
                  }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
    }

    if (nameDialog) {
        var text by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { nameDialog = false },
            containerColor = AraBrand.bgBottom,
            titleContentColor = AraBrand.title,
            textContentColor = AraBrand.body,
            title = { Text(tr.examNew, color = AraBrand.title) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text(tr.examName) }, singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = AraBrand.title, fontWeight = FontWeight.Bold),
                    colors = araFieldColors(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val nm = text.trim().ifBlank { tr.examDefault }
                    nameDialog = false
                    pickDate { ms ->
                        onSaveExam(AraExam(id = "exam_" + System.currentTimeMillis().toString(36),
                            name = nm, dateMs = ms))
                    }
                }) { Text(tr.pickDateArrow) }
            },
            dismissButton = { TextButton(onClick = { nameDialog = false }) { Text(tr.cancel) } },
        )
    }

    val pf = pickFor
    if (pf != null) {
        val e = exams.firstOrNull { it.id == pf }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pickFor = null },
            // Without an explicit dark container the dialog uses the M3 default
            // (light) surface, making the app's light course names invisible.
            containerColor = AraBrand.bgBottom,
            titleContentColor = AraBrand.title,
            textContentColor = AraBrand.body,
            title = { Text(tr.addMaterials, color = AraBrand.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (allCourses.isEmpty()) Text(tr.noMaterialsImported, color = AraBrand.muted, fontSize = 13.sp)
                    allCourses.forEach { c ->
                        val inExam = e != null && c.id in e.materialIds
                        Row(
                            Modifier.fillMaxWidth().clickable { onToggleMaterial(pf, c.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (inExam) "☑" else "☐", fontSize = 18.sp, color = AraBrand.green)
                            Spacer(Modifier.width(8.dp))
                            Text(c.title, color = AraBrand.body, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickFor = null }) { Text(tr.coachDone) } },
        )
    }
}

@Composable
private fun ExamCard(
    exam: AraExam,
    plan: org.json.JSONObject?,
    dateText: String,
    dailyPhoneMin: Double,
    courseTitle: (String) -> String,
    onAddExisting: () -> Unit,
    onImport: () -> Unit,
    onRemoveMaterial: (String) -> Unit,
    onDelete: () -> Unit,
    onPickDate: () -> Unit,
    onCoverage: (AraExam) -> Unit,
    materialSettings: (String) -> AraMaterialSettings,
    onMaterialSettings: (String, AraMaterialSettings) -> Unit,
    loadMaterialOptions: (String) -> org.json.JSONObject,
    onOpenSettings: () -> Unit,
) {
    val tr = LocalTr.current
    var confirmDelete by remember { mutableStateOf(false) }
    var settingsFor by remember { mutableStateOf<String?>(null) }
    val totalUnits = plan?.optInt("num_units", 0) ?: 0
    Box(Modifier.fillMaxWidth().araGlass(RoundedCornerShape(20.dp))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(exam.name, color = AraBrand.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            // Date is changeable: tap the row to reopen the date picker.
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPickDate() }
                .padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr.labelData, color = AraBrand.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(dateText, color = AraBrand.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text("✎ ${tr.editBtn}", color = AraBrand.greenDeep, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            StatRow(tr.labelMaterials, "${exam.materialIds.size}")

            // Plan summary from the engine scheduler.
            if (plan != null) {
                val units = plan.optInt("num_units", 0)
                if (units <= 0) {
                    Text(tr.openMaterialsHint,
                        color = AraBrand.muted, fontSize = 11.sp)
                } else {
                    val interval = plan.optDouble("interval_minutes", 0.0)
                    val perDay = plan.optDouble("appearances_per_day", 0.0)
                    val covered = plan.optBoolean("fully_covered", true)
                    StatRow(tr.labelPlan, String.format(tr.planFmt, interval.toInt(), perDay.toInt()))
                    if (exam.coverageMode == AraExam.COVERAGE_COUNT && !covered) {
                        Text(tr.timeWarning,
                            color = AraBrand.danger, fontSize = 11.sp)
                        // Tell them exactly where to fix it, and take them there.
                        Text(tr.openPlanSettings,
                            color = AraBrand.greenDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onOpenSettings() }.padding(top = 2.dp))
                    }
                }
            }

            // Exam-level coverage knob (replaces per-material repeats) + LIVE readout
            // of how many full passes still fit at the chosen pace.
            ExamCoverageControl(
                exam = exam,
                totalUnits = totalUnits,
                dailyPhoneMin = dailyPhoneMin,
                onCoverage = onCoverage,
            )

            if (exam.materialIds.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    exam.materialIds.forEach { m ->
                        val s = materialSettings(m)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("• " + courseTitle(m), color = AraBrand.body, fontSize = 13.sp)
                                if (s.appearance.isNotBlank()) {
                                    Text(araChoiceLabel(tr, s.appearance),
                                        color = AraBrand.muted, fontSize = 11.sp)
                                }
                            }
                            Text("⚙", color = AraBrand.greenDeep, fontSize = 16.sp,
                                modifier = Modifier
                                    .clickable { settingsFor = if (settingsFor == m) null else m }
                                    .padding(horizontal = 8.dp))
                            Text("✕", color = AraBrand.danger, fontSize = 14.sp,
                                modifier = Modifier.clickable { onRemoveMaterial(m) }.padding(start = 4.dp))
                        }
                        if (settingsFor == m) {
                            val options by produceState(initialValue = org.json.JSONObject(), m) {
                                value = withContext(Dispatchers.Default) { loadMaterialOptions(m) }
                            }
                            MaterialSettingsCard(
                                options = options,
                                current = s,
                                onChange = { onMaterialSettings(m, it) },
                            )
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AraChip(tr.importShort, false) { onImport() }
                AraChip(tr.fromCourses, false) { onAddExisting() }
            }
            OutlinedButton(
                onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AraBrand.danger),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AraBrand.danger),
            ) { Text(if (confirmDelete) tr.confirmAgain else tr.deleteExam) }
        }
    }
}

/** Exam-level coverage: choose how many full times to cover the whole exam (COUNT),
 *  or pin the gap between cards (NEDEFINIT/interval) and let the card show, live,
 *  how many full passes still fit. One knob for the whole exam. */
@Composable
private fun ExamCoverageControl(
    exam: AraExam,
    totalUnits: Int,
    dailyPhoneMin: Double,
    onCoverage: (AraExam) -> Unit,
) {
    val tr = LocalTr.current
    val isInterval = exam.coverageMode == AraExam.COVERAGE_INTERVAL
    val presets = listOf(1, 2, 3, 5)
    var menuOpen by remember { mutableStateOf(false) }
    // Show the free-number box when count isn't one of the quick presets (incl. a
    // saved custom value), or when the owner explicitly chose "Alt număr…".
    var customCount by remember(exam.id) {
        mutableStateOf(!isInterval && exam.coverageCount !in presets)
    }
    // Bumped whenever "Alt număr…" is chosen, to force the number box open for typing.
    var editNonce by remember(exam.id) { mutableStateOf(0) }

    val closedLabel = when {
        isInterval && exam.coverageIntervalMin > 0 -> tr.coverageIntervalClosed(exam.coverageIntervalMin)
        isInterval -> tr.coverageIntervalUnset
        exam.coverageCount == 1 -> tr.coverageOnce
        else -> tr.coverageNTimes(exam.coverageCount)
    }

    Box(Modifier.fillMaxWidth().araGlass(RoundedCornerShape(16.dp))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr.coverageTitle, color = AraBrand.title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(tr.coverageSub, color = AraBrand.muted, fontSize = 11.sp, lineHeight = 15.sp)

            // The dropdown: tap to open a clear list of choices.
            Box {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(AraBrand.greenSoft)
                        .border(1.dp, AraBrand.glassEdgeGreen, RoundedCornerShape(12.dp))
                        .clickable { menuOpen = true }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(closedLabel, color = AraBrand.title, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("▾", color = AraBrand.greenDeep, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.background(AraBrand.bgBottom),
                ) {
                    presets.forEach { n ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(if (n == 1) tr.coverageOnce else tr.coverageNTimes(n), color = AraBrand.body) },
                            onClick = {
                                menuOpen = false; customCount = false
                                onCoverage(exam.copy(coverageMode = AraExam.COVERAGE_COUNT, coverageCount = n))
                            },
                        )
                    }
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(tr.coverageOtherNumber, color = AraBrand.body) },
                        onClick = {
                            menuOpen = false; customCount = true; editNonce++
                            if (exam.coverageMode != AraExam.COVERAGE_COUNT) {
                                onCoverage(exam.copy(coverageMode = AraExam.COVERAGE_COUNT))
                            }
                        },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(tr.coverageMenuInterval, color = AraBrand.body) },
                        onClick = {
                            menuOpen = false; customCount = false
                            onCoverage(exam.copy(coverageMode = AraExam.COVERAGE_INTERVAL, coverageIntervalMin = 0))
                        },
                    )
                }
            }

            if (isInterval) {
                LockableNumberField(
                    value = exam.coverageIntervalMin,
                    label = tr.coverageMinLabel,
                    suffix = " min",
                    range = 1..1440,
                    onCommit = { onCoverage(exam.copy(coverageIntervalMin = it)) },
                )
                Text(tr.coverageMinHint, color = AraBrand.muted, fontSize = 11.sp, lineHeight = 15.sp)
            } else if (customCount) {
                LockableNumberField(
                    value = exam.coverageCount,
                    label = tr.coverageCountLabel,
                    suffix = "×",
                    range = 1..99,
                    editKey = editNonce,
                    onCommit = { onCoverage(exam.copy(coverageMode = AraExam.COVERAGE_COUNT, coverageCount = it)) },
                )
            }
            ExamCoverageLive(exam = exam, totalUnits = totalUnits, dailyPhoneMin = dailyPhoneMin)
        }
    }
}

/** A numeric input that commits ONLY when the owner taps "Setează"; afterwards it
 *  locks into a read-only chip (with a small "✎ schimbă" to reopen), so a set
 *  number never looks editable and can't be nudged by accident. Used for every
 *  numeric setting — coverage count, NEDEFINIT minutes, weekly hours. */
@Composable
private fun LockableNumberField(
    value: Int,
    label: String,
    suffix: String,
    range: IntRange,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    // When this key changes (and is > 0), force the field open for typing — used by
    // the "Alt număr…" menu item so the box is immediately editable.
    editKey: Int = 0,
) {
    val tr = LocalTr.current
    // Open for input only when nothing is set yet; a real value shows locked.
    var editing by remember(value) { mutableStateOf(value <= 0) }
    LaunchedEffect(editKey) { if (editKey > 0) editing = true }
    if (editing) {
        var txt by remember(value) { mutableStateOf(if (value > 0) value.toString() else "") }
        val n = txt.toIntOrNull()
        val ok = n != null && n in range
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.OutlinedTextField(
                value = txt,
                onValueChange = { v -> txt = v.filter { it.isDigit() }.take(4) },
                singleLine = true,
                label = { Text(label) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = AraBrand.title, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                colors = araFieldColors(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                modifier = Modifier.width(130.dp),
            )
            Box(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .then(if (ok) Modifier.background(AraBrand.araBrush) else Modifier.background(AraBrand.greenSoft))
                    .then(if (ok) Modifier.clickable { onCommit(n!!); editing = false } else Modifier)
                    .padding(horizontal = 18.dp, vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) { Text(tr.setBtn, color = if (ok) Color(0xFF16110A) else AraBrand.muted, fontWeight = FontWeight.Bold) }
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(AraBrand.greenSoft)
                    .border(1.dp, AraBrand.glassEdgeGreen, RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 13.dp),
            ) { Text("$value$suffix", color = AraBrand.title, fontWeight = FontWeight.Black, fontSize = 16.sp) }
            Text("✎ ${tr.editBtn}", color = AraBrand.greenDeep, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { editing = true })
        }
    }
}

/** The live, ticking line: at the current pace, how many full passes through the
 *  whole exam still fit before the date — or that a whole pass no longer fits. */
@Composable
private fun ExamCoverageLive(exam: AraExam, totalUnits: Int, dailyPhoneMin: Double) {
    val tr = LocalTr.current
    if (totalUnits <= 0 || exam.dateMs <= 0) return
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(exam.id) {
        while (true) { nowMs = System.currentTimeMillis(); kotlinx.coroutines.delay(30_000L) }
    }
    val daysLeft = ((exam.dateMs - nowMs) / 86_400_000.0).coerceAtLeast(0.0)
    val phoneMinLeft = dailyPhoneMin * daysLeft
    if (exam.coverageMode == AraExam.COVERAGE_INTERVAL) {
        val gap = exam.coverageIntervalMin
        if (gap < 1) {
            Text(tr.coverageSetMinutes, color = AraBrand.muted, fontSize = 12.sp)
            return
        }
        val passes = (phoneMinLeft / gap) / totalUnits
        if (passes >= 1.0) {
            Text(String.format(tr.coveragePassesLive, passes.toInt()),
                color = AraBrand.green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
        } else {
            Text(tr.coverageNoTimeWhole,
                color = AraBrand.danger, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
        }
    } else {
        Text(String.format(tr.coverageCountLive, exam.coverageCount),
            color = AraBrand.body, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
    }
}

// ----------------------------------------------------------------------------
// Settings
// ----------------------------------------------------------------------------

@Composable
private fun AraSettings(
    store: AraStore,
    planStore: AraPlanStore,
    font: AraFont,
    theme: AraThemeStyle,
    lang: Lang,
    onLang: (Lang) -> Unit,
    onFont: (AraFont) -> Unit,
    onTheme: (AraThemeStyle) -> Unit,
    onPurgeExpired: () -> Unit,
    onBack: () -> Unit,
) {
    val tr = LocalTr.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val coach = rememberCoachState()
    val steps = remember(tr) {
        listOf(CoachStep("lang", tr.cSetLang), CoachStep("plan", tr.cSetPlan), CoachStep("look", tr.cSetLook))
    }
    // Open a web URL in the external browser. ACTION_VIEW hands off to the
    // browser app, so this works even though AntiROTAid has no INTERNET
    // permission itself. On failure (no handler) we Toast a localized message
    // instead of crashing.
    fun openUrl(url: String) {
        val ok = runCatching {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        if (!ok) android.widget.Toast.makeText(context, tr.linkOpenError, android.widget.Toast.LENGTH_SHORT).show()
    }
    // Rate: prefer the Play Store app (market://), fall back to the https listing.
    fun openRate() {
        val market = "market://details?id=app.antirotaid"
        val https = "https://play.google.com/store/apps/details?id=app.antirotaid"
        val ok = runCatching {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(market))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        if (!ok) openUrl(https)
    }
    CoachHost(coach) {
    AraGlassScrim {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { TopBar(title = tr.settingsTitle, onBack = onBack, onHelp = { coach.start(steps) }) }

            // Language: changeable anytime; switches the whole UI live.
            item { SectionLabel(tr.settingsLanguage) }
            item {
                Box(Modifier.fillMaxWidth().coachAnchor(coach, "lang").araGlass(RoundedCornerShape(18.dp))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr.settingsLanguageSub, color = AraBrand.muted, fontSize = 11.sp)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Lang.entries.forEach { l ->
                                AraChip("${l.badge}  ${l.display}", l == lang) { onLang(l) }
                            }
                        }
                    }
                }
            }

            // Study plan: weekly phone time (Variant B — owner states it; no usage
            // permission) + how to juggle several exams.
            item { SectionLabel(tr.planLabel) }
            item {
                var weeklyMin by remember { mutableStateOf(planStore.weeklyPhoneMinutes()) }
                var mode by remember { mutableStateOf(planStore.studyMode()) }
                Box(Modifier.fillMaxWidth().coachAnchor(coach, "plan").araGlass(RoundedCornerShape(18.dp))) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(tr.weeklyPhoneTime,
                            color = AraBrand.body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(tr.weeklyPhoneSub,
                            color = AraBrand.muted, fontSize = 11.sp)
                        // Type the exact number of hours, then Setează to lock it in;
                        // the chips below are just quick shortcuts.
                        LockableNumberField(
                            value = weeklyMin / 60,
                            label = tr.hoursPerWeek,
                            suffix = "h",
                            range = 1..168,
                            onCommit = { h -> weeklyMin = h * 60; planStore.setWeeklyPhoneMinutes(weeklyMin) },
                        )
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5, 10, 15, 20, 25, 30, 40).forEach { h ->
                                val sel = weeklyMin == h * 60
                                AraChip("${h}h", sel) {
                                    weeklyMin = h * 60; planStore.setWeeklyPhoneMinutes(weeklyMin)
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(tr.moreExams, color = AraBrand.body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AraChip(tr.combine, mode == AraPlanStore.MODE_COMBINE) {
                                mode = AraPlanStore.MODE_COMBINE; planStore.setStudyMode(mode)
                            }
                            AraChip(tr.oneAtTime, mode == AraPlanStore.MODE_SEQUENTIAL) {
                                mode = AraPlanStore.MODE_SEQUENTIAL; planStore.setStudyMode(mode)
                            }
                        }
                        Text(
                            if (mode == AraPlanStore.MODE_COMBINE) tr.combineDesc
                            else tr.oneAtTimeDesc,
                            color = AraBrand.muted, fontSize = 11.sp,
                        )
                    }
                }
            }

            // Auto-delete after deadline (opt-in). Turning it ON also purges
            // everything already past — with a warning first.
            item { SectionLabel(tr.autoDeleteSection) }
            item {
                var on by remember { mutableStateOf(planStore.autoDeleteEnabled()) }
                var confirm by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth().araGlass(RoundedCornerShape(18.dp))) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(tr.autoDeleteTitle, color = AraBrand.body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(tr.autoDeleteSub, color = AraBrand.muted, fontSize = 11.sp, lineHeight = 15.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AraChip(tr.onWord, on) {
                                // Enabling: warn, then purge what's already past.
                                if (!on) confirm = true
                            }
                            AraChip(tr.offWord, !on) {
                                if (on) { on = false; planStore.setAutoDeleteEnabled(false) }
                            }
                        }
                    }
                }
                if (confirm) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { confirm = false },
                        containerColor = AraBrand.bgBottom,
                        titleContentColor = AraBrand.title,
                        textContentColor = AraBrand.body,
                        title = { Text(tr.autoDeleteTitle, color = AraBrand.title) },
                        text = { Text(tr.autoDeleteDialogBody, color = AraBrand.body) },
                        confirmButton = {
                            TextButton(onClick = {
                                confirm = false
                                on = true
                                planStore.setAutoDeleteEnabled(true)
                                onPurgeExpired()
                            }) { Text(tr.autoDeleteDeleteNow, color = AraBrand.danger, fontWeight = FontWeight.Bold) }
                        },
                        dismissButton = { TextButton(onClick = { confirm = false }) { Text(tr.cancel) } },
                    )
                }
            }

            // Font.
            item { SectionLabel(tr.textSize) }
            item {
                Row(Modifier.fillMaxWidth().coachAnchor(coach, "look"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AraFont.entries.forEach { f ->
                        val sel = f == font
                        Box(
                            Modifier.weight(1f).height(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (sel) AraBrand.greenSoft else Color(0x14FFFFFF))
                                .then(if (sel) Modifier.shadow(0.dp) else Modifier)
                                .clickable { onFont(f) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Aa", color = if (sel) AraBrand.greenDeep else AraBrand.body,
                                    fontSize = (10 + f.ordinal * 4).sp, fontWeight = FontWeight.Bold)
                                val flabel = when (f) {
                                    AraFont.SMALL -> tr.fontSmall; AraFont.MEDIUM -> tr.fontMedium
                                    AraFont.LARGE -> tr.fontLarge; AraFont.XLARGE -> tr.fontXLarge
                                }
                                Text(flabel, color = if (sel) AraBrand.green else AraBrand.muted, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
            // Live preview of a card with the chosen font + theme.
            item { SectionLabel(tr.cardPreview) }
            item { MiniCardPreview(font = font, theme = theme) }

            // Theme.
            item { SectionLabel(tr.appTheme) }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AraThemeStyle.entries.forEach { t ->
                        ThemeTile(
                            theme = t,
                            selected = t == theme,
                            onClick = { onTheme(t) },
                        )
                    }
                }
            }

            // Privacy / info.
            item { SectionLabel(tr.privacyInfoTitle) }
            item {
                Box(Modifier.fillMaxWidth().araGlass(RoundedCornerShape(18.dp))) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        InfoLine("◆", tr.infoFilesT, tr.infoFilesB)
                        InfoLine("▣", tr.infoStorageT, tr.infoStorageB)
                        InfoLine("◍", tr.infoNoNetT, tr.infoNoNetB)
                        InfoLine("▤", tr.infoStudyT, tr.infoStudyB)
                        Text(tr.appVersionLocal, color = AraBrand.muted, fontSize = 11.sp)
                    }
                }
            }

            // Legal & store links: Privacy Policy + Terms of Service (opened in the
            // external browser) and a Rate button that opens the Play listing. No
            // rating prompt, no incentive — a plain, persistent link.
            item { SectionLabel(tr.legalSection) }
            item {
                Box(Modifier.fillMaxWidth().araGlass(RoundedCornerShape(18.dp))) {
                    Column(Modifier.padding(6.dp)) {
                        LinkRow("◆", tr.privacyPolicyLabel) {
                            openUrl("https://antirotaid.netlify.app/privacy-policy/")
                        }
                        LinkRow("§", tr.termsLabel) {
                            openUrl("https://antirotaid.netlify.app/terms/")
                        }
                        LinkRow("★", tr.rateLabel) { openRate() }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
    }
}


@Composable
private fun ThemeTile(theme: AraThemeStyle, selected: Boolean, onClick: () -> Unit) {
    val accent = AraBrand.swatchFor(theme)
    Box(
        Modifier.width(132.dp).height(158.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0x1FFFFFFF), Color(0x08000000))))
            .border(androidx.compose.foundation.BorderStroke(if (selected) 1.6.dp else 1.dp, if (selected) accent else AraBrand.glassEdge), RoundedCornerShape(22.dp))
            .clickable { onClick() }
            .padding(12.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.24f), Color(0x10000000))))
                .border(androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.35f)), RoundedCornerShape(14.dp))) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.width(42.dp).height(5.dp).clip(CircleShape).background(accent))
                    Box(Modifier.width(72.dp).height(5.dp).clip(CircleShape).background(accent.copy(alpha = 0.65f)))
                    Box(Modifier.width(50.dp).height(5.dp).clip(CircleShape).background(AraBrand.muted.copy(alpha = 0.45f)))
                }
            }
            Text(theme.label, color = AraBrand.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 15.sp)
            Spacer(Modifier.weight(1f))
            val tr = LocalTr.current
            if (selected) {
                Text(tr.selected, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            } else {
                Text(tr.tap, color = AraBrand.muted, fontSize = 10.sp)
            }
        }
    }
}


@Composable
private fun InfoLine(icon: String, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(icon, fontSize = 18.sp, color = AraBrand.greenDeep, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = AraBrand.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = AraBrand.body, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

/** A tappable Settings row that opens an external link (Privacy / Terms / Rate). */
@Composable
private fun LinkRow(icon: String, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 16.sp, color = AraBrand.greenDeep, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(12.dp))
        Text(label, color = AraBrand.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f))
        Text("↗", fontSize = 14.sp, color = AraBrand.muted)
    }
}

@Composable
private fun MiniCardPreview(font: AraFont, theme: AraThemeStyle) {
    val tr = LocalTr.current
    AraBrand.activeTheme = theme
    Box(Modifier.fillMaxWidth().araGlass(RoundedCornerShape(22.dp), glow = AraBrand.glowStrong, glowRadius = 22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.clip(CircleShape).background(AraBrand.greenSoft)
                .padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(tr.previewTag, color = AraBrand.greenDeep,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
            }
            Text(tr.previewTitle,
                fontSize = font.title.sp, fontWeight = FontWeight.Bold, color = AraBrand.title,
                lineHeight = (font.title * 1.18).sp)
            Box(Modifier.width(52.dp).height(4.dp).clip(CircleShape).background(AraBrand.araBrush))
            Text(tr.previewBody,
                fontSize = font.body.sp, color = AraBrand.body, lineHeight = (font.body * 1.48).sp)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = AraBrand.title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit, onHelp: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(AraBrand.greenSoft)
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) { Text("←", color = AraBrand.greenDeep, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Text(title, color = AraBrand.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (onHelp != null) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(AraBrand.greenSoft)
                    .border(androidx.compose.foundation.BorderStroke(1.dp, AraBrand.glassEdgeGreen), CircleShape)
                    .clickable { onHelp() },
                contentAlignment = Alignment.Center,
            ) { Text("?", color = AraBrand.greenDeep, fontSize = 18.sp, fontWeight = FontWeight.Black) }
        }
    }
}

// ----------------------------------------------------------------------------
// Plan / learning algorithm
// ----------------------------------------------------------------------------

/** Spreads a course's remaining cards across the days left until its deadline,
 *  and blends in the pace actually observed from the user's sessions. Pure local
 *  arithmetic — no network, no accounts. */
data class AraPlan(
    val total: Int,
    val seen: Int,
    val fraction: Float,
    val daysLeft: Int,
    val perDay: Int,
    val learnedPace: Int,
    val deadlineMs: Long,
    val urgent: Boolean,
) {
    fun deadlineText(tr: Tr): String =
        if (deadlineMs > 0) AraPlan.formatDeadline(deadlineMs, tr.localeCode) else tr.noDatePressChange

    companion object {
        private const val DAY_MS = 86_400_000L
        private const val DEFAULT_DAYS = 14

        fun of(store: AraStore, id: String): AraPlan {
            val j = store.progressJson(id)
            val total = j.optInt("cards_total", 0)
            val seen = j.optInt("cards_seen", 0)
            val frac = if (total > 0) seen.toFloat() / total else 0f
            val deadline = store.deadline(id)
            val now = System.currentTimeMillis()
            val daysLeft = if (deadline > 0)
                Math.max(1, Math.ceil((deadline - now).toDouble() / DAY_MS).toInt())
            else DEFAULT_DAYS
            val remaining = Math.max(0, total - seen)
            val needed = if (total > 0) Math.ceil(remaining.toDouble() / daysLeft).toInt() else 0
            val learned = store.learnedPace(id)
            // Blend: never recommend below what the deadline requires, but if the
            // user reliably does more per session, lift the target toward that.
            val perDay = if (total <= 0) 0 else Math.max(needed, Math.min(learned, total)).coerceAtLeast(1)
            val urgent = deadline in 1..(now + 2 * DAY_MS)
            return AraPlan(total, seen, frac, daysLeft, perDay, learned, deadline, urgent)
        }

        fun formatDeadline(ms: Long, localeCode: String = "ro"): String {
            val fmt = java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale(localeCode))
            return fmt.format(java.util.Date(ms))
        }
    }
}

/** Seed one worked-example exam on first run: a real demo material grouped under
 *  an exam with a date two weeks out and 3× repeats, so the planner produces a
 *  visible interval/pace right away. Runs at most once ever (flag-gated) and never
 *  overwrites a user's own exams. */
private fun seedModelExamIfNeeded(ctx: Context, store: AraStore, planStore: AraPlanStore) {
    if (store.modelExamSeeded()) return
    store.markModelExamSeeded()
    // Pick the first available demo material as the example's content.
    val matId = listOf("demo_neural_networks", "demo_photosynthesis", "demo_algebra")
        .firstOrNull { AraDemo.find(ctx, it) != null }
        ?: AraDemo.list(ctx).firstOrNull()?.id
        ?: return
    val tr = trFor(store.lang())
    val date = System.currentTimeMillis() + 14L * 24 * 60 * 60 * 1000
    val exam = AraExam(
        planStore.newExamId(), tr.examModelName, date, listOf(matId),
        coverageMode = AraExam.COVERAGE_COUNT, coverageCount = 3,
    )
    planStore.saveExam(exam)
    store.setDeadline(matId, date)
}

// ----------------------------------------------------------------------------
// Data: demo + imported courses
// ----------------------------------------------------------------------------

object AraDemo {
    data class Lesson(val id: String, val title: String, val custom: Boolean, val filename: String = "", val addedMs: Long = 0L)

    val supportedMimeTypes = arrayOf(
        "text/plain", "text/markdown", "text/*",
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/json", "image/*",
    )

    private val supportedExtensions = setOf(
        "txt", "text", "md", "markdown", "csv", "tsv", "json", "html", "htm",
        "docx", "pptx", "pdf", "png", "jpg", "jpeg", "webp", "bmp", "gif", "tiff", "tif",
    )

    private fun coursesDir(ctx: Context) =
        java.io.File(ctx.filesDir, "ara_courses").apply { mkdirs() }

    fun list(ctx: Context): List<Lesson> = list(ctx, emptySet())

    fun list(ctx: Context, hiddenDemos: Set<String>): List<Lesson> {
        // Demo titles are localized by stable id (never derived from the English
        // asset filename), so switching the app language switches the demo card
        // heading too — not just its body (see AraDemo.read).
        val tr = trFor(AraStore(ctx).lang())
        val demos = runCatching { ctx.assets.list("demo_lessons")?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.endsWith(".txt") }
            .sorted()
            .map { f ->
                val id = f.removeSuffix(".txt")
                Lesson(
                    id = id,
                    title = tr.demoTitle(id),
                    custom = false, filename = f,
                )
            }
            .filter { it.id !in hiddenDemos }
        val custom = coursesDir(ctx).listFiles { f ->
            f.isFile && f.extension.lowercase() in supportedExtensions
        }?.sortedByDescending { it.lastModified() }
            ?.map { f ->
                Lesson(
                    id = "custom_" + f.name,
                    title = f.nameWithoutExtension.replace('_', ' ').replaceFirstChar { it.uppercase() },
                    custom = true, filename = f.name, addedMs = f.lastModified(),
                )
            }.orEmpty()
        return custom + demos
    }

    /** Every bundled demo lesson id (used to hide them all on a clean first run). */
    fun allDemoIds(ctx: Context): List<String> =
        runCatching { ctx.assets.list("demo_lessons")?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.endsWith(".txt") }
            .map { it.removeSuffix(".txt") }

    fun find(ctx: Context, id: String): Lesson? = list(ctx).firstOrNull { it.id == id }

    /** Engine source_type for a material, from its filename extension. */
    fun sourceType(filename: String): String =
        when (filename.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "pdf"
            "pptx", "ppt" -> "pptx"
            "docx", "doc" -> "docx"
            "png", "jpg", "jpeg", "webp", "bmp", "gif", "tiff", "tif" -> "image"
            else -> "text"
        }

    /** Sort by deadline ascending; courses without a deadline go last. */
    fun byDeadline(store: AraStore): Comparator<Lesson> =
        Comparator { a, b ->
            val da = store.deadline(a.id).let { if (it <= 0) Long.MAX_VALUE else it }
            val db = store.deadline(b.id).let { if (it <= 0) Long.MAX_VALUE else it }
            da.compareTo(db)
        }

    /**
     * Auto-delete after deadline (opt-in via Settings). When ON:
     *  - an exam whose date has passed is deleted;
     *  - a course is deleted only once EVERY exam it belonged to has passed — a
     *    course shared by two exams survives until both are gone;
     *  - courses never attached to any exam are left untouched (a permanent
     *    library), since deadlines live on exams, not on materials.
     * No-op when the setting is OFF, so the owner deletes things manually.
     */
    fun purgeExpired(ctx: Context, store: AraStore, planStore: AraPlanStore) {
        if (!planStore.autoDeleteEnabled()) return
        val now = System.currentTimeMillis()
        val exams = planStore.exams()
        // Capture each material's exam dates BEFORE we delete any passed exam.
        val membership = HashMap<String, MutableList<Long>>()
        exams.forEach { e -> e.materialIds.forEach { membership.getOrPut(it) { ArrayList() }.add(e.dateMs) } }
        // 1) Drop exams whose date has passed.
        exams.filter { it.dateMs in 1 until now }.forEach { planStore.deleteExam(it.id) }
        // 2) Drop courses whose every exam has passed (had >=1, none in the future).
        membership.forEach { (id, dates) ->
            if (dates.isNotEmpty() && dates.all { it in 1 until now }) {
                remove(ctx, store, id)
                planStore.clearMaterialSettings(id)
            }
        }
    }

    /** Manual delete: imported courses lose their file; demo courses get hidden
     *  (their text lives in assets), so every course can be cleared. Removes ALL
     *  associated state (Phase 4): the imported file copy, the generated feed
     *  cache, every per-course preference, exam references, and it never lets the
     *  overlay keep using a deleted course. */
    fun remove(ctx: Context, store: AraStore, id: String) {
        // 1) Imported local file copy (compute name first so we can purge its cache).
        val storedName = if (id.startsWith("custom_")) id.removePrefix("custom_") else null
        // 2) Generated feed/deck cache for this course (keyed by stored file name).
        if (storedName != null) purgeFeedCache(ctx, storedName)
        // 3) Delete the file / hide the demo.
        if (storedName != null) {
            java.io.File(coursesDir(ctx), storedName).delete()
        } else {
            store.hideDemo(id)
        }
        // 4) Exam.MaterialIds references + per-material settings/unit count.
        val planStore = AraPlanStore(ctx)
        planStore.stripMaterialEverywhere(id)
        // 5) Every per-course preference (resume, hidden cards, per-card progress,
        //    cached deck ids, overlay unit cursor, settings, unit count).
        store.removeCourseData(id)
        // 6) Overlay safety: if AntiROTAid Mode is on, make sure it never keeps a
        //    stale courseId — stop it when nothing is left, else it re-resolves.
        AraModeService.onCourseDeleted(ctx, id)
    }

    /** Delete every cached feed JSON built for a given stored file name. Cache file
     *  names begin with the sanitized file name (see [cacheKey]); once the source
     *  file is gone we purge by that prefix so no stale generated deck remains. */
    private fun purgeFeedCache(ctx: Context, storedName: String) {
        runCatching {
            feedCacheDir(ctx).listFiles()?.forEach { f ->
                if (AraCourseCleanup.matchesFeedCache(f.name, storedName)) f.delete()
            }
        }
    }

    private fun feedCacheDir(ctx: Context) =
        java.io.File(ctx.cacheDir, "ara_feed_cache").apply { mkdirs() }

    /** Cache key for a built feed: file identity + the settings that change the deck. */
    private fun cacheKey(file: java.io.File, includeMedia: Boolean, s: AraMaterialSettings): String =
        "${file.name}_${file.length()}_${file.lastModified()}_${includeMedia}_${s.cardSize}_${s.appearance}"
            .replace(Regex("[^A-Za-z0-9._]+"), "_")

    fun load(ctx: Context, engine: AraEngine, id: String, includeMedia: Boolean = true,
             settings: AraMaterialSettings = AraMaterialSettings()): AraEngine.Loaded {
        val tr = trFor(AraStore(ctx).lang())
        return if (id.startsWith("custom_")) {
            val file = java.io.File(coursesDir(ctx), id.removePrefix("custom_"))
            // Re-running the Python PDF/PPTX extractor on every open (and every
            // overlay appearance) cost seconds. Cache the built feed JSON keyed by
            // file identity + deck settings; on a hit we skip extraction entirely.
            val cache = java.io.File(feedCacheDir(ctx), cacheKey(file, includeMedia, settings) + ".json")
            val cached = if (cache.exists()) runCatching {
                val feed = JSONObject(cache.readText())
                AraEngine.Loaded(feed, parseAraFeed(feed))
            }.getOrNull() else null
            if (cached != null) return cached
            // Detail/count view: let Python read the file from disk so we never
            // hold the whole deck as bytes + base64 in Java (OOM on big decks).
            val loaded = if (!includeMedia) engine.loadFilePath(file.name, file.absolutePath, settings, tr)
            // Phase 7: pass only the on-device path; the Python engine reads the file
            // from disk so Java never base64-encodes the whole deck (no OOM, no dup copy).
            else engine.loadFile(file.name, localPath = file.absolutePath, includeMedia = true, settings = settings, tr = tr)
            if (loaded.errorMessage == null) runCatching { cache.writeText(loaded.feed.toString()) }
            loaded
        } else {
            engine.load(id, read(ctx, id), tr)
        }
    }

    fun read(ctx: Context, id: String): String {
        // Localized demo content: prefer the current UI language's copy
        // (assets/<locale>/<id>.txt), falling back to the canonical Romanian
        // demo_lessons/ set when a translation is missing. Switching the app
        // language therefore switches the demo lesson text, not just the UI.
        val locale = AraStore(ctx).lang().code
        val localized = runCatching {
            ctx.assets.open("$locale/$id.txt").bufferedReader().use { it.readText() }
        }.getOrNull()
        if (!localized.isNullOrBlank()) return localized
        return runCatching {
            ctx.assets.open("demo_lessons/$id.txt").bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    fun displayName(ctx: Context, uri: android.net.Uri): String {
        val name = runCatching {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) c.getString(i) else null
            }
        }.getOrNull() ?: "curs.txt"
        return name.ifBlank { "curs.txt" }
    }

    /** Save imported material exactly as selected. Returns the stored filename. */
    fun saveCustomFile(ctx: Context, displayName: String, bytes: ByteArray): String {
        val raw = displayName.replace(Regex("[\\r\\n/\\\\]+"), "_").trim().ifBlank { "curs.txt" }
        val base = raw.substringBeforeLast('.', raw).lowercase()
            .replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "curs" }
        val ext = raw.substringAfterLast('.', "txt").lowercase()
            .replace(Regex("[^a-z0-9]+"), "").ifBlank { "txt" }
        var file = java.io.File(coursesDir(ctx), "$base.$ext")
        var n = 2
        while (file.exists()) { file = java.io.File(coursesDir(ctx), "${base}_$n.$ext"); n++ }
        // Atomic write (Phase 9): stage to a temp file, then rename into place, so a
        // failed/half-written import never leaves a partial course file on disk.
        val tmp = java.io.File(file.parentFile, "${file.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
        return file.name
    }
}

/** Local-only event log for one study session (sent to the behavior engine). */
class AraSession(val lessonId: String) {
    val events = JSONArray()
    private val shown = HashSet<String>()
    fun log(type: String, cardId: String, outer: Int = -1, innerDepth: Float = -1f) {
        if (type == "card_shown") shown.add(cardId)
        val e = JSONObject()
            .put("lesson_id", lessonId).put("card_id", cardId)
            .put("event_type", type).put("timestamp_ms", System.currentTimeMillis())
        if (outer >= 0) e.put("outer_index", outer)
        if (innerDepth >= 0f) e.put("inner_scroll_depth", innerDepth.toDouble())
        events.put(e)
    }
    /** Distinct cards actually shown this session — feeds the learned pace. */
    fun shownCount(): Int = shown.size
}

/** Local persistence of per-lesson progress, deadline, passes, learned pace and
 *  the user's theme/font choice. No cloud. */
class AraStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("antirotaid", Context.MODE_PRIVATE)

    fun font(): AraFont = runCatching { AraFont.valueOf(sp.getString("font", "MEDIUM")!!) }
        .getOrDefault(AraFont.MEDIUM)
    fun setFont(f: AraFont) = sp.edit().putString("font", f.name).apply()

    fun theme(): AraThemeStyle = runCatching {
        AraThemeStyle.valueOf(sp.getString("theme", "DARK_GOLD")!!)
    }.getOrDefault(AraThemeStyle.DARK_GOLD)
    fun setTheme(t: AraThemeStyle) = sp.edit().putString("theme", t.name).apply()

    // App interface language (picked on first launch, changeable in Settings).
    // null until the user has chosen — used to gate the first-run picker.
    fun langOrNull(): Lang? = sp.getString("lang", null)?.let { Lang.from(it) }
    fun lang(): Lang = langOrNull() ?: Lang.EN
    fun setLang(l: Lang) = sp.edit().putString("lang", l.code).apply()

    fun saveProgress(lessonId: String, json: JSONObject) =
        sp.edit().putString("prog_$lessonId", json.toString()).apply()
    fun progressJson(lessonId: String): JSONObject =
        runCatching { JSONObject(sp.getString("prog_$lessonId", "{}")!!) }.getOrDefault(JSONObject())
    fun progress(lessonId: String): Float {
        val j = progressJson(lessonId)
        val total = j.optInt("cards_total", 0)
        val seen = j.optInt("cards_seen", 0)
        return if (total > 0) seen.toFloat() / total else 0f
    }
    fun cardsTotal(lessonId: String): Int = progressJson(lessonId).optInt("cards_total", 0)

    // Deadline (epoch ms; 0 = none).
    fun deadline(lessonId: String): Long = sp.getLong("deadline_$lessonId", 0L)
    fun setDeadline(lessonId: String, ms: Long) = sp.edit().putLong("deadline_$lessonId", ms).apply()

    // Resume position: the card index the user last left off at, per material, so
    // the next time the deck opens (in-app OR over another app) it starts there
    // instead of card 1. Reset to 0 when a full pass completes.
    fun resumeCardIndex(materialId: String): Int = sp.getInt("resumeidx_$materialId", 0)
    fun setResumeCardIndex(materialId: String, idx: Int) {
        if (idx >= 0) sp.edit().putInt("resumeidx_$materialId", idx).apply()
    }

    // Complete passes through a course.
    fun passes(lessonId: String): Int = sp.getInt("passes_$lessonId", 0)

    // Learned pace: EMA of distinct cards shown per session.
    fun learnedPace(lessonId: String): Int = sp.getInt("pace_$lessonId", 0)

    /** Update learning + pass counters at the end of a study session. */
    fun recordSession(lessonId: String, cardsShown: Int) {
        if (cardsShown > 0) {
            val old = sp.getInt("pace_$lessonId", 0)
            val blended = if (old <= 0) cardsShown else Math.round(old * 0.6f + cardsShown * 0.4f)
            sp.edit().putInt("pace_$lessonId", blended).apply()
        }
        // A "pass" = the user has seen at least as many cards as the deck holds.
        val total = cardsTotal(lessonId)
        val seen = progressJson(lessonId).optInt("cards_seen", 0)
        if (total > 0 && seen >= total) {
            sp.edit().putInt("passes_$lessonId", passes(lessonId) + 1).apply()
            // Reset seen so the next pass tracks fresh progress.
            val j = progressJson(lessonId).put("cards_seen", 0)
            saveProgress(lessonId, j)
            // A finished pass starts the next one from the top.
            resetResume(lessonId)
        }
    }

    fun nextOverlayIndex(count: Int): Int {
        if (count <= 0) return 0
        val i = sp.getInt("overlay_idx", 0) % count
        sp.edit().putInt("overlay_idx", (i + 1) % count).apply()
        return i
    }

    /** Which logical unit to surface next for a course, advancing each appearance
     *  so the overlay walks unit-by-unit instead of dumping the whole deck. */
    fun nextUnitIndex(courseId: String, count: Int): Int {
        if (count <= 0) return 0
        val key = "unit_idx_$courseId"
        val i = sp.getInt(key, 0) % count
        sp.edit().putInt(key, (i + 1) % count).apply()
        return i
    }

    // Demo lessons live in assets and can't be file-deleted, so "deleting" one
    // hides it. This lets the user clear EVERY course, demos included.
    fun hiddenDemos(): Set<String> = sp.getStringSet("hidden_demos", emptySet()) ?: emptySet()
    fun hideDemo(lessonId: String) {
        sp.edit().putStringSet("hidden_demos", hiddenDemos() + lessonId).apply()
    }
    fun restoreDemos() {
        sp.edit().remove("hidden_demos").apply()
    }

    // Per-material card hiding: the owner can drop an individual card (a meme,
    // a book cover, an off-topic figure) straight from the study feed. Hidden
    // ids are filtered out every time the deck is rebuilt, so it stays gone.
    fun hiddenCards(materialId: String): Set<String> =
        sp.getStringSet("hiddencards_$materialId", emptySet()) ?: emptySet()
    fun hideCard(materialId: String, cardId: String) {
        sp.edit().putStringSet("hiddencards_$materialId", hiddenCards(materialId) + cardId).apply()
    }
    fun restoreCards(materialId: String) {
        sp.edit().remove("hiddencards_$materialId").apply()
    }

    // Per-card progress (spec §5): times_seen, times_completed, last_scroll px + %.
    // Stored as one JSON per material: { cardId: {s, c, off, pct} }.
    private fun cardProg(materialId: String): JSONObject =
        runCatching { JSONObject(sp.getString("cardprog_$materialId", "{}")!!) }.getOrDefault(JSONObject())
    private fun saveCardProg(materialId: String, obj: JSONObject) =
        sp.edit().putString("cardprog_$materialId", obj.toString()).apply()
    private fun cardEntry(materialId: String, cardId: String): JSONObject {
        val all = cardProg(materialId)
        return all.optJSONObject(cardId) ?: JSONObject()
    }
    fun cardTimesSeen(materialId: String, cardId: String): Int = cardEntry(materialId, cardId).optInt("s", 0)
    fun cardTimesCompleted(materialId: String, cardId: String): Int = cardEntry(materialId, cardId).optInt("c", 0)
    fun cardScrollOffset(materialId: String, cardId: String): Int = cardEntry(materialId, cardId).optInt("off", 0)
    fun markCardSeen(materialId: String, cardId: String) {
        val all = cardProg(materialId); val e = all.optJSONObject(cardId) ?: JSONObject()
        e.put("s", e.optInt("s", 0) + 1); all.put(cardId, e); saveCardProg(materialId, all)
    }
    fun markCardCompleted(materialId: String, cardId: String) {
        val all = cardProg(materialId); val e = all.optJSONObject(cardId) ?: JSONObject()
        e.put("c", e.optInt("c", 0) + 1); all.put(cardId, e); saveCardProg(materialId, all)
    }
    fun saveCardScroll(materialId: String, cardId: String, offset: Int, percent: Int) {
        val all = cardProg(materialId); val e = all.optJSONObject(cardId) ?: JSONObject()
        e.put("off", offset); e.put("pct", percent); all.put(cardId, e); saveCardProg(materialId, all)
    }
    /** Full passes = how many times the WHOLE material was gone through (min
     *  completions across its cards). 0 until every card has been completed once. */
    fun materialPasses(materialId: String, cardIds: List<String>): Int {
        if (cardIds.isEmpty()) return 0
        val all = cardProg(materialId)
        return cardIds.minOf { all.optJSONObject(it)?.optInt("c", 0) ?: 0 }
    }
    fun materialHasResume(materialId: String): Boolean =
        cardProg(materialId).let { all -> all.keys().asSequence().any { (all.optJSONObject(it)?.optInt("pct", 0) ?: 0) in 1..84 } }
    // Remember a material's card-id list (saved when its deck is loaded) so the
    // library can show passes without re-running the extractor for every card.
    fun saveCardIds(materialId: String, ids: List<String>) =
        sp.edit().putString("cardids_$materialId", org.json.JSONArray(ids).toString()).apply()
    fun cardIdsList(materialId: String): List<String> =
        runCatching {
            val a = org.json.JSONArray(sp.getString("cardids_$materialId", "[]")!!)
            (0 until a.length()).map { a.optString(it) }
        }.getOrDefault(emptyList())
    fun materialPassesCached(materialId: String): Int = materialPasses(materialId, cardIdsList(materialId))

    // First launch after install: start the library CLEAN (no demo courses
    // cluttering it) and show the one-time intro. Demos stay one tap away via
    // "Readu cursurile demo".
    fun isFirstRun(): Boolean = !sp.getBoolean("initialized_v1", false)
    fun completeFirstRun(allDemoIds: Collection<String>) {
        // Keep the demo courses VISIBLE on first run so a new user has real
        // examples to open in the library (they can delete them anytime).
        sp.edit()
            .putBoolean("initialized_v1", true)
            .apply()
    }
    fun onboardingSeen(): Boolean = sp.getBoolean("onboarding_seen_v1", false)
    fun markOnboardingSeen() = sp.edit().putBoolean("onboarding_seen_v1", true).apply()

    // Worked-example exam: seeded exactly once (first run), never re-added after the
    // user deletes it, so the flag is independent of whether any exam exists now.
    fun modelExamSeeded(): Boolean = sp.getBoolean("model_exam_seeded_v1", false)
    fun markModelExamSeeded() = sp.edit().putBoolean("model_exam_seeded_v1", true).apply()

    /** Erase EVERY key tied to a course (manual + auto delete). The full key list
     *  is centralized in [AraCourseCleanup] so no per-course state is ever orphaned
     *  (resume, hidden cards, per-card progress, cached deck ids, overlay unit
     *  cursor, material settings, unit count). */
    fun removeCourseData(lessonId: String) {
        val e = sp.edit()
        AraCourseCleanup.keysFor(lessonId).forEach { e.remove(it) }
        e.apply()
    }

    // --- Robust resume model (Phase 3 & 5) -----------------------------------
    // Persists idx + CardId + deck fingerprint (see [AraResume]) instead of a bare
    // index, so resume survives deck regeneration/reorder/insert/delete. Written to
    // a single SharedPreferences key, so each write is atomic (Phase 9).

    fun resumeModel(materialId: String): AraResume? =
        AraResume.fromStorage(sp.getString("resume_$materialId", null))

    private fun writeResume(materialId: String, r: AraResume) =
        sp.edit().putString("resume_$materialId", r.toStorage()).apply()

    /** Resolve the safe start index for the CURRENT deck, migrating any legacy
     *  integer resume the first time. Never opens an unrelated card. */
    fun resolveStartIndex(materialId: String, cards: List<AraCard>): Int {
        val ids = cards.map { it.cardId }
        val fp = AraResume.fingerprintCards(cards)
        var saved = resumeModel(materialId)
        if (saved == null) {
            val legacy = sp.getInt("resumeidx_$materialId", -1)
            if (legacy >= 0 && ids.isNotEmpty()) {
                saved = AraResume.migrateLegacy(legacy, ids, fp)
                writeResume(materialId, saved)
                sp.edit().remove("resumeidx_$materialId").apply()
            }
        }
        return AraResume.resolveIndex(saved, ids, fp)
    }

    /** Persist the user's current position within the current deck. */
    fun saveResumeAt(materialId: String, index: Int, cards: List<AraCard>) {
        val ids = cards.map { it.cardId }
        if (ids.isEmpty()) return
        writeResume(materialId, AraResume.capture(index, ids, AraResume.fingerprintCards(cards)))
    }

    /** Reset resume to the top of the deck (full-deck completion → start at card 1). */
    fun resetResume(materialId: String) {
        writeResume(materialId, AraResume.EMPTY)
        sp.edit().remove("resumeidx_$materialId").apply()
    }

    /** Phase 5: when a single card is removed, drop its per-card progress and remap
     *  the resume position to the nearest valid neighbor (clamp at the end). */
    fun onCardRemoved(materialId: String, removedCardId: String,
                      oldCards: List<AraCard>, newCards: List<AraCard>) {
        // Remove the deleted card's learning/progress state.
        val all = cardProg(materialId)
        all.remove(removedCardId)
        saveCardProg(materialId, all)
        // Remap resume by CardId / nearest neighbor.
        val r = AraResume.remapAfterRemoval(
            resumeModel(materialId), removedCardId,
            oldCards.map { it.cardId }, newCards.map { it.cardId },
            AraResume.fingerprintCards(newCards),
        )
        writeResume(materialId, r)
        // Keep the cached deck id list in sync with the new deck.
        saveCardIds(materialId, newCards.map { it.cardId })
    }
}
