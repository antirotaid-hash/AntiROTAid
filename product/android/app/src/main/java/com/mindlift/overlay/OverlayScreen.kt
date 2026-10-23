package com.mindlift.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The recovery overlay UI. Calm, centered, no-shame. Choices preserve agency:
 * start a tiny step, make it easier, take a bounded break, or defer. The break
 * shows a live countdown and always ends — never open-ended.
 */
@Composable
fun OverlayScreen(vm: OverlayViewModel, onDismiss: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.phase) {
        if (state.phase == OverlayViewModel.Phase.DISMISSED ||
            state.phase == OverlayViewModel.Phase.DONE
        ) onDismiss()
    }

    // Progressive friction: every escape attempt darkens the backdrop — the
    // feed literally fades as the escalation level climbs (0xCC -> 0xF8).
    val backdropAlpha = (0xCC + state.frictionLevel * 0x0F).coerceAtMost(0xF8)
    Surface(color = Color((backdropAlpha shl 24) or 0x101412),
            modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            when (state.phase) {
                OverlayViewModel.Phase.BREAK -> BreakCard(
                    remaining = state.breakRemainingSec,
                    onEnd = vm::endBreakEarly,
                )
                OverlayViewModel.Phase.PROMPT -> state.lifeMsg?.let { msg ->
                    LifeCard(msg = msg, onAcknowledge = vm::acknowledgeLife)
                } ?: state.overlay?.let { ov ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.taxRemaining > 1) {
                        Text("Taxa de scroll: inca ${state.taxRemaining} carduri pana la libertate",
                            color = Color(0xFFFFCC80),
                            style = MaterialTheme.typography.labelLarge)
                    }
                    StudyCard(
                        step = state.step,
                        teachOnly = state.teachOnly,
                        concept = ov.microtaskConcept,
                        theory = ov.microtaskTheory,
                        front = ov.microtaskPrompt,
                        answer = ov.microtaskAnswer,
                        revealed = state.revealed,
                        grading = state.grading,
                        coverage = ov.studyCoverage,
                        understanding = ov.studyUnderstanding,
                        onContinue = vm::continueToTest,
                        onAcknowledge = vm::acknowledgeTeach,
                        onReveal = { vm.onChoice("start_step") },
                        onGrade = vm::gradeCard,
                    )
                    }
                }
                else -> Text("…", color = Color.White)
            }
        }
    }
}

@Composable
private fun StudyCard(
    step: OverlayViewModel.Step,
    teachOnly: Boolean,
    concept: String?,
    theory: String?,
    front: String?,
    answer: String?,
    revealed: Boolean,
    grading: Boolean,
    coverage: Double,
    understanding: Double,
    onContinue: () -> Unit,
    onAcknowledge: () -> Unit,
    onReveal: () -> Unit,
    onGrade: (String) -> Unit,
) {
    val isTheory = step == OverlayViewModel.Step.THEORY
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Section label: which kind of card this is.
            Text(if (isTheory) "TEORIE" else "TEST",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary)
            // Dual progress: how much of the material has been presented (parcurs)
            // and how much has actually stuck on tests (inteles). Use the primary
            // colour so it stays legible on the light card (outline is near-white).
            Text("Parcurs ${(coverage * 100).toInt()}%   ·   Înțeles ${(understanding * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            concept?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (isTheory) {
                        Text(theory.orEmpty().ifBlank { "—" },
                            style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Text(front.orEmpty().ifBlank { "—" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium)
                        if (revealed) {
                            HorizontalDivider()
                            Text(answer.orEmpty().ifBlank { "—" },
                                style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            when {
                isTheory -> {
                    // Teach phase: read-only, just advance. Otherwise: go to the test.
                    Button(onClick = if (teachOnly) onAcknowledge else onContinue,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (teachOnly) "Am inteles, mai departe" else "Testeaza-ma")
                    }
                }
                !revealed -> {
                    Button(onClick = onReveal, modifier = Modifier.fillMaxWidth()) {
                        Text("Arata raspunsul")
                    }
                }
                else -> {
                    Text("Cat de bine a mers?", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { onGrade("again") }, enabled = !grading,
                            modifier = Modifier.weight(1f)) { Text("Nu stiam") }
                        OutlinedButton(onClick = { onGrade("hard") }, enabled = !grading,
                            modifier = Modifier.weight(1f)) { Text("Aproape") }
                        Button(onClick = { onGrade("good") }, enabled = !grading,
                            modifier = Modifier.weight(1f)) { Text("Am stiut") }
                    }
                }
            }
        }
    }
}

/** A life-deadline interruption: bill, meeting or bedtime instead of a card.
 * Same no-shame tone; severity only changes the accent colour. */
@Composable
private fun LifeCard(msg: OverlayViewModel.LifeMsg, onAcknowledge: () -> Unit) {
    val accent = when (msg.severity) {
        "critical" -> Color(0xFFB71C1C)
        "urgent" -> Color(0xFFE65100)
        else -> MaterialTheme.colorScheme.secondary
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("VIATA REALA", style = MaterialTheme.typography.labelMedium, color = accent)
            Text(msg.headline, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold)
            if (msg.body.isNotBlank()) {
                Text(msg.body, style = MaterialTheme.typography.bodyLarge)
            }
            Button(onClick = onAcknowledge, modifier = Modifier.fillMaxWidth()) {
                Text("Am inteles")
            }
        }
    }
}

@Composable
private fun BreakCard(remaining: Int, onEnd: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Pauza scurta", style = MaterialTheme.typography.titleLarge)
            val mm = remaining / 60
            val ss = remaining % 60
            Text(String.format(java.util.Locale.US, "%02d:%02d", mm, ss),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Respira putin. La final, revii la un pas clar.",
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onEnd, modifier = Modifier.fillMaxWidth()) {
                Text("Sunt gata, revin acum")
            }
        }
    }
}
