package com.mindlift.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindlift.ui.CourseViewModel
import kotlin.math.roundToInt

private val ColorMastered = Color(0xFF4CAF50)
private val ColorGood = Color(0xFF2196F3)
private val ColorHard = Color(0xFFFFC107)
private val ColorNew = Color(0xFF9E9E9E)
private val ColorWeak = Color(0xFFE53935)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(vm: CourseViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        vm.refreshProgress()
        vm.refreshPulse()
    }

    val health = state.health
    val progress = state.progress
    val pulse = state.pulse
    val dna = state.dna

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Progres") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Inapoi")
                }
            },
        )
    }) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // --- Readiness score card ---
            val readiness = health?.readinessScore ?: pulse?.readinessScore ?: 0.0
            val band = health?.healthBand ?: "—"
            val bandColor = when {
                readiness >= 0.75 -> ColorMastered
                readiness >= 0.5 -> ColorGood
                readiness >= 0.25 -> ColorHard
                else -> ColorWeak
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = bandColor.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(bandColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${(readiness * 100).roundToInt()}%",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Pregatire examen", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(band.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = bandColor)
                        health?.nextActions?.firstOrNull()?.let { action ->
                            Text(action, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // --- Progress bars ---
            if (progress != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Parcurs curs", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        ProgressRow(
                            label = "Material studiat",
                            value = progress.studiedRatio.coerceIn(0.0, 1.0).toFloat(),
                            color = ColorGood,
                        )
                        ProgressRow(
                            label = "Scor mediu carduri",
                            value = progress.avgScore.coerceIn(0.0, 1.0).toFloat(),
                            color = ColorMastered,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            StatChip("${progress.cardCount} carduri total", ColorGood)
                            StatChip("${progress.dueCount} de revazut", ColorHard)
                        }
                    }
                }

                // --- Band distribution ---
                val bands = progress.bandCounts
                if (bands.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("Carduri per nivel", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                BandChip("Stapanit", bands["mastered"] ?: 0, ColorMastered,
                                    Modifier.weight(1f))
                                BandChip("Bine", bands["good"] ?: 0, ColorGood, Modifier.weight(1f))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                BandChip("Invatat", bands["hard"] ?: 0, ColorHard, Modifier.weight(1f))
                                BandChip("Nou", bands["new"] ?: (bands["again"] ?: 0), ColorNew,
                                    Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // --- Today's session ---
            if (pulse != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Azi", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StatChip("${pulse.studiedMinutes.roundToInt()} min studiat", ColorGood)
                            StatChip("${pulse.remainingMinutes.roundToInt()} min ramas", ColorHard)
                        }
                        Text(pulse.reason, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // --- Weak concepts ---
                if (pulse.weakConcepts.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ColorWeak.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("De consolidat", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold, color = ColorWeak)
                            pulse.weakConcepts.take(6).forEach { concept ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(6.dp).clip(CircleShape)
                                            .background(ColorWeak),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(concept, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                // --- Mastered concepts ---
                if (pulse.masteredConcepts.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ColorMastered.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("Stapanit", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold, color = ColorMastered)
                            pulse.masteredConcepts.take(6).forEach { concept ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(6.dp).clip(CircleShape)
                                            .background(ColorMastered),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(concept, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }

            // --- Topics from DNA ---
            dna?.topics?.takeIf { it.isNotEmpty() }?.let { topics ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Teme din curs (${topics.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        topics.take(10).forEachIndexed { i, topic ->
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${i + 1}.", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(20.dp))
                                Text(topic, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (topics.size > 10) {
                            Text("+ ${topics.size - 10} alte teme",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (progress == null && pulse == null) {
                Text("Incarca un curs si studiaza cateva carduri pentru a vedea progresul.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ProgressRow(label: String, value: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${(value * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = color)
        }
        LinearProgressIndicator(
            progress = { value },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
        )
    }
}

@Composable
private fun BandChip(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("$count", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
