package com.mindlift.ara

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Live "coach marks" walkthrough: when started, the real screen behind blurs and
 * dims, a glowing ring frames the button being explained, and a callout bubble
 * describes what it does — stepped Back / Next / Done. The user sees the actual
 * UI being pointed at, not a separate mock, so nothing has to be guessed.
 *
 * Usage:
 *   val coach = rememberCoachState()
 *   CoachHost(coach) {  // wraps the screen content
 *       ...
 *       Button(Modifier.coachAnchor(coach, "settings")) { ... }
 *   }
 *   // to launch: coach.start(listOf(CoachStep("settings", tr.coachSettings), ...))
 */
class CoachStep(val targetId: String, val text: String)

class CoachState {
    /** Live screen-space bounds of every anchored element, keyed by id. */
    val bounds = mutableStateMapOf<String, Rect>()
    var steps by mutableStateOf<List<CoachStep>>(emptyList())
        private set
    var index by mutableStateOf(0)
        private set
    var active by mutableStateOf(false)
        private set

    fun start(steps: List<CoachStep>) {
        if (steps.isEmpty()) return
        this.steps = steps
        index = 0
        active = true
    }

    val current: CoachStep? get() = steps.getOrNull(index)
    val isLast: Boolean get() = index >= steps.lastIndex

    fun next() { if (isLast) stop() else index++ }
    fun back() { if (index > 0) index-- }
    fun stop() { active = false }
}

@Composable
fun rememberCoachState(): CoachState = remember { CoachState() }

/** Report this element's screen-space bounds to the walkthrough. */
fun Modifier.coachAnchor(state: CoachState, id: String): Modifier =
    this.onGloballyPositioned { state.bounds[id] = it.boundsInRoot() }

@Composable
fun CoachHost(state: CoachState, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
        if (state.active) CoachOverlay(state)
    }
}

@Composable
private fun CoachOverlay(state: CoachState) {
    val tr = LocalTr.current
    val step = state.current ?: return
    val rect = state.bounds[step.targetId]
    val density = LocalDensity.current
    val padPx = with(density) { 6.dp.toPx() }
    val radiusPx = with(density) { 16.dp.toPx() }

    // Swallow taps so nothing behind reacts during the walkthrough.
    Box(
        Modifier.fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { state.next() },
    ) {
        // Dim EVERYTHING except a sharp rounded "spotlight" hole over the element
        // being explained — so the described control stays crisp and readable
        // (not blurred), while the rest of the screen is darkened.
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(Color(0xE6090806))
            if (rect != null) {
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(rect.left - padPx, rect.top - padPx),
                    size = Size(rect.width + padPx * 2, rect.height + padPx * 2),
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    blendMode = BlendMode.Clear,
                )
            }
        }

        // Glowing ring around the spotlight (no fill — the element shows through).
        if (rect != null) {
            val pad = 6.dp
            with(density) {
                val x = rect.left.toDp() - pad
                val y = rect.top.toDp() - pad
                val w = rect.width.toDp() + pad * 2
                val h = rect.height.toDp() + pad * 2
                Box(
                    Modifier.offset(x, y).size(w, h)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, AraBrand.green, RoundedCornerShape(16.dp)),
                )
            }
        }

        // Callout bubble: below the target if it sits in the top half, else above.
        CoachCallout(
            rect = rect,
            density = density,
            text = step.text,
            stepLabel = "${state.index + 1}/${state.steps.size}",
            isLast = state.isLast,
            canBack = state.index > 0,
            onBack = { state.back() },
            onNext = { state.next() },
            nextLabel = if (state.isLast) tr.coachDone else tr.coachNext,
            backLabel = tr.coachBack,
            skipLabel = tr.guideSkip,
            onSkip = { state.stop() },
        )
    }
}

@Composable
private fun CoachCallout(
    rect: Rect?,
    density: androidx.compose.ui.unit.Density,
    text: String,
    stepLabel: String,
    isLast: Boolean,
    canBack: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextLabel: String,
    backLabel: String,
    skipLabel: String,
    onSkip: () -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxH = with(density) { constraints.maxHeight.toDp() }
        // Decide vertical placement: under the target, unless it's low on screen.
        val targetBottomDp = rect?.let { with(density) { it.bottom.toDp() } } ?: (maxH / 2)
        val targetTopDp = rect?.let { with(density) { it.top.toDp() } } ?: (maxH / 2)
        val below = targetBottomDp < maxH * 0.55f
        val yOffset = if (below) targetBottomDp + 14.dp else (targetTopDp - 14.dp - 200.dp)
        val safeY = yOffset.coerceIn(40.dp, maxH - 230.dp)

        Box(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).offset(y = safeY)
                .clip(RoundedCornerShape(20.dp))
                .background(AraBrand.bgBottom)
                .border(1.dp, AraBrand.glassEdgeGreen, RoundedCornerShape(20.dp)),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stepLabel, color = AraBrand.green, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    Text(skipLabel, color = AraBrand.muted, fontSize = 12.sp,
                        modifier = Modifier.clickable { onSkip() })
                }
                Text(text, color = AraBrand.body, fontSize = 15.sp, lineHeight = 21.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (canBack) {
                        Box(
                            Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(13.dp))
                                .background(Color(0x1AFFFFFF))
                                .border(1.dp, AraBrand.glassEdgeGreen, RoundedCornerShape(13.dp))
                                .clickable { onBack() },
                            contentAlignment = Alignment.Center,
                        ) { Text(backLabel, color = AraBrand.body, fontWeight = FontWeight.Bold) }
                    }
                    Box(
                        Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(13.dp))
                            .background(AraBrand.araBrush)
                            .clickable { onNext() },
                        contentAlignment = Alignment.Center,
                    ) { Text(nextLabel, color = Color(0xFF16110A), fontWeight = FontWeight.Black) }
                }
            }
        }
    }
}
