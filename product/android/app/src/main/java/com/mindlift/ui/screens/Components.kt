package com.mindlift.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val HeroStart = Color(0xFFEAF1FF)
private val HeroEnd = Color(0xFFFFF8EA)
private val SoftBlue = Color(0xFFE7EFFF)
private val SoftGreen = Color(0xFFEAF7F1)
private val SoftAmber = Color(0xFFFFF3D7)
private val InkMuted = Color(0xFF667085)

/** A titled surface card used across screens to keep the calm, sectioned look. */
@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

/** Large visual header for home/course screens. */
@Composable
fun HeroPanel(
    eyebrow: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(HeroStart, HeroEnd)))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
            Text(title, style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = InkMuted)
            content?.invoke()
        }
    }
}

@Composable
fun StatPill(label: String, value: String, tone: String = "blue") {
    val bg = when (tone) {
        "green" -> SoftGreen
        "amber" -> SoftAmber
        else -> SoftBlue
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = bg,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = InkMuted)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun StatRow(vararg stats: Pair<String, String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stats.take(3).forEachIndexed { index, pair ->
            Box(modifier = Modifier.weight(1f)) {
                StatPill(pair.first, pair.second, when (index) { 1 -> "green"; 2 -> "amber"; else -> "blue" })
            }
        }
    }
}

@Composable
fun PrimaryAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) { Text(text, modifier = Modifier.padding(vertical = 4.dp), fontWeight = FontWeight.Bold) }
}

@Composable
fun SecondaryAction(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) { Text(text, modifier = Modifier.padding(vertical = 3.dp), fontWeight = FontWeight.SemiBold) }
}

@Composable
fun StepItem(number: String, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(number, color = Color.White, fontWeight = FontWeight.ExtraBold) }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(body, color = InkMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Maps a mastery band to a short, neutral Romanian label. No-shame wording. */
fun bandLabel(band: String): String = when (band) {
    "unseen" -> "neinceput"
    "shaky" -> "se aseaza"
    "learning" -> "in lucru"
    "solid" -> "solid"
    "secure" -> "stabil"
    else -> band
}

/** Maps a risk level to a calm, non-alarming label. */
fun riskLabel(level: String): String = when (level) {
    "low" -> "atentie stabila"
    "medium" -> "atractie moderata"
    "high" -> "atractie ridicata"
    "severe" -> "atractie foarte ridicata"
    else -> level
}
