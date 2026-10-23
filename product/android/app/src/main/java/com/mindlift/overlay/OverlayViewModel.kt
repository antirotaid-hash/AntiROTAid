package com.mindlift.overlay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mindlift.bridge.OverlayPayload
import com.mindlift.data.EngineRepository
import com.mindlift.data.EngineResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.mindlift.bridge.toInterventionResult

/**
 * Drives the overlay: decide the intervention, then run the bounded break loop
 * (start -> countdown -> advance to resolve). All durations come from the engine
 * so the break can never become open-ended.
 */
class OverlayViewModel(
    private val repo: EngineRepository,
    private val courseId: String,
    private val riskLevel: String = "high",
    private val prefetchedJson: String? = null,
    private val scrolledMinutes: Double = 0.0,
) : ViewModel() {

    enum class Phase { LOADING, PROMPT, BREAK, DONE, DISMISSED }
    enum class Step { THEORY, TEST }   // read the theory first, then get tested

    /** A life-deadline interruption (bill/meeting/bedtime) instead of a card. */
    data class LifeMsg(val headline: String, val body: String, val severity: String)

    data class State(
        val phase: Phase = Phase.LOADING,
        val overlay: OverlayPayload? = null,
        val lifeMsg: LifeMsg? = null,    // when set, PROMPT shows this, not a card
        val taxRemaining: Int = 1,       // cards left to pay before freedom
        val frictionLevel: Int = 0,      // bypass escalation -> darker backdrop
        val step: Step = Step.THEORY,
        val teachOnly: Boolean = false,  // teach phase: read theory, no test
        val revealed: Boolean = false,   // test answer shown -> grade buttons
        val grading: Boolean = false,    // a grade is being recorded
        val breakRemainingSec: Int = 0,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var breakId: String? = null

    // Scroll tax: how many cards buy the next stretch of scrolling. Set from
    // the engine arbiter (urgency + escape surcharge); paying it sets you free.
    private var taxRemaining: Int = 1

    // True once the tax was paid in full: leaving then is EARNED, so closing
    // must not be timestamped as a dismissal (which would make the very next
    // scroll look like a bypass and escalate someone who did the work).
    private var paidInFull: Boolean = false

    init {
        viewModelScope.launch {
            // Life deadlines (bill/meeting/bedtime) compete with the card for the
            // overlay slot; the engine arbiter ranks them on the same urgency dial.
            // The overlay only fires under heavy pull, so phone_pressure is high.
            val life = withTimeoutOrNull(4_000L) {
                repo.lifeOverlayDecision(phonePressure = 0.9,
                    scrolledMinutesToday = scrolledMinutes)
            }
            if (life is EngineResult.Ok) {
                taxRemaining = life.value.optInt("scroll_tax_cards", 1)
                val friction = life.value.optJSONObject("discipline")
                    ?.optJSONObject("escalation")?.optInt("level", 0) ?: 0
                _state.update { it.copy(taxRemaining = taxRemaining,
                    frictionLevel = friction) }
            }
            if (life is EngineResult.Ok && life.value.optString("show") == "life") {
                val msg = life.value.optJSONObject("message")
                if (msg != null) {
                    _state.update {
                        it.copy(phase = Phase.PROMPT, lifeMsg = LifeMsg(
                            headline = msg.optString("headline"),
                            body = msg.optString("body"),
                            severity = msg.optString("severity", "notice")))
                    }
                    return@launch
                }
            }
            // Fast path: monitor pre-fetched the decision — show card immediately.
            if (prefetchedJson != null) {
                val res = try {
                    org.json.JSONObject(prefetchedJson).toInterventionResult()
                } catch (_: Exception) { null }
                if (res != null && !res.suppressed && res.overlay != null) {
                    breakId = res.breakId
                    val teach = res.overlay.microtaskMode == "teach"
                    _state.update {
                        it.copy(phase = Phase.PROMPT, overlay = res.overlay,
                            step = if (teach) Step.THEORY else Step.TEST,
                            teachOnly = teach)
                    }
                    return@launch
                }
            }
            // Fallback: fetch from engine (e.g., overlay triggered from within the app).
            val r = withTimeoutOrNull(8_000L) { repo.decideIntervention(courseId, riskLevel) }
            if (r == null) {
                _state.update { it.copy(phase = Phase.DISMISSED) }
                return@launch
            }
            when (r) {
                is EngineResult.Err -> _state.update { it.copy(phase = Phase.DISMISSED, message = r.message) }
                is EngineResult.Ok -> {
                    val res = r.value
                    if (res.suppressed || res.overlay == null) {
                        _state.update { it.copy(phase = Phase.DISMISSED) }
                    } else {
                        breakId = res.breakId
                        val teach = res.overlay.microtaskMode == "teach"
                        _state.update {
                            it.copy(phase = Phase.PROMPT, overlay = res.overlay,
                                step = if (teach) Step.THEORY else Step.TEST,
                                teachOnly = teach)
                        }
                    }
                }
            }
        }
    }

    fun onChoice(id: String) {
        val aggressive = _state.value.overlay?.aggressive == true
        when (id) {
            "take_break" -> if (!aggressive) startBreak()
            "start_step", "make_easier" -> _state.update { it.copy(revealed = true) }
            "delay" -> if (!aggressive) _state.update { it.copy(phase = Phase.DISMISSED) }
        }
    }

    /** Life message read — the obligation stays; only the overlay closes.
     * The cadence engine refires soon while the deadline keeps pressing. */
    fun acknowledgeLife() = _state.update { it.copy(phase = Phase.DONE) }

    /** Move from the theory card to the test card. */
    fun continueToTest() = _state.update { it.copy(step = Step.TEST) }

    /** Teach phase: theory read, nothing to grade — just move to the next card.
     * Coverage is recorded engine-side when the card is presented. */
    fun acknowledgeTeach() {
        if (_state.value.grading) return
        viewModelScope.launch { loadNext() }
    }

    /** Self-graded recall of the inline card: records the review, then chains the next card. */
    fun gradeCard(grade: String) {
        val cardId = _state.value.overlay?.microtaskCardId
        if (cardId.isNullOrBlank() || _state.value.grading) {
            _state.update { it.copy(phase = Phase.DONE) }
            return
        }
        _state.update { it.copy(grading = true) }
        viewModelScope.launch {
            repo.review(cardId, grade)
            // Redemption: a card you actually knew buys one escalation level
            // back — the way out of the punishment is through learning.
            if (grade == "good") {
                try { repo.lifeRecordOverlayEvent("card_paid") } catch (_: Exception) { }
            }
            loadNext()
        }
    }

    private suspend fun loadNext() {
        // One card paid. Tax settled -> the scroll is earned; overlay closes.
        taxRemaining -= 1
        if (taxRemaining <= 0) {
            paidInFull = true
            _state.update { it.copy(phase = Phase.DONE, taxRemaining = 0) }
            return
        }
        _state.update { it.copy(phase = Phase.LOADING, grading = false,
            taxRemaining = taxRemaining) }
        val r = withTimeoutOrNull(8_000L) { repo.decideIntervention(courseId, riskLevel) }
        if (r == null) { _state.update { it.copy(phase = Phase.DONE) }; return }
        when (r) {
            is EngineResult.Err -> _state.update { it.copy(phase = Phase.DONE) }
            is EngineResult.Ok -> {
                val res = r.value
                if (res.suppressed || res.overlay == null) {
                    _state.update { it.copy(phase = Phase.DONE) }
                } else {
                    breakId = res.breakId
                    val teach = res.overlay.microtaskMode == "teach"
                    _state.update {
                        it.copy(
                            phase = Phase.PROMPT,
                            overlay = res.overlay,
                            step = if (teach) Step.THEORY else Step.TEST,
                            teachOnly = teach,
                            revealed = false,
                            grading = false,
                        )
                    }
                }
            }
        }
    }

    private fun startBreak() {
        val bId = breakId ?: run { _state.update { it.copy(phase = Phase.DONE) }; return }
        val seconds = _state.value.overlay?.breakSeconds ?: 300
        viewModelScope.launch {
            repo.startBreak(bId)
            _state.update { it.copy(phase = Phase.BREAK, breakRemainingSec = seconds) }
            var remaining = seconds
            while (remaining > 0 && _state.value.phase == Phase.BREAK) {
                delay(1000)
                remaining -= 1
                _state.update { it.copy(breakRemainingSec = remaining) }
            }
            if (_state.value.phase == Phase.BREAK) {
                repo.advanceBreak(bId)
                _state.update { it.copy(phase = Phase.DONE) }
            }
        }
    }

    fun endBreakEarly() {
        val bId = breakId ?: return
        viewModelScope.launch {
            repo.advanceBreak(bId)
            _state.update { it.copy(phase = Phase.DONE) }
        }
    }

    /** The overlay is gone (any reason). The engine timestamps the dismissal so
     * an immediate dive back into the feed counts as a bypass attempt. Fired
     * from onCleared on a detached scope because viewModelScope is dead here. */
    @kotlinx.coroutines.DelicateCoroutinesApi
    override fun onCleared() {
        if (!paidInFull) {
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try { repo.lifeRecordOverlayEvent("dismissed") } catch (_: Exception) { }
            }
        }
        super.onCleared()
    }

    companion object {
        fun factory(repo: EngineRepository, courseId: String, riskLevel: String = "high",
                    prefetchedJson: String? = null, scrolledMinutes: Double = 0.0) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    OverlayViewModel(repo, courseId, riskLevel, prefetchedJson,
                        scrolledMinutes) as T
            }
    }
}
