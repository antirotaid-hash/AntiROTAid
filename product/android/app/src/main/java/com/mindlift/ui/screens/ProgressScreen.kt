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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mindlift.bridge.CategoryProgressCategory
import com.mindlift.bridge.CategoryProgressModel
import com.mindlift.data.EngineRepository
import com.mindlift.data.EngineResult
import kotlinx.coroutines.launch

private val ColorGreen  = Color(0xFF2E7D32)
private val ColorAmber  = Color(0xFFF9A825)
private val ColorOrange = Color(0xFFE65100)
private val ColorRed    = Color(0xFFC62828)

private fun statusColor(color: String) = when (color) {
    "green"  -> ColorGreen
    "amber"  -> ColorAmber
    "orange" -> ColorOrange
    else     -> ColorRed
}

private fun statusLabel(status: String) = when (status) {
    "secure"     -> "Sigur"
    "moving"     -> "In progres"
    "needs_work" -> "Trebuie lucrat"
    else         -> "Incepe"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(courseId: String, repo: EngineRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var model by remember { mutableStateOf<CategoryProgressModel?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(courseId) {
        scope.launch {
            when (val r = repo.categoryProgress(courseId)) {
                is EngineResult.Ok -> { model = r.value; loading = false }
                is EngineResult.Err -> { error = r.message; loading = false }
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Progres pe categorii") },
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            when {
                loading -> {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> Text("Eroare: $error", color = MaterialTheme.colorScheme.error)
                model != null -> {
                    val m = model!!
                    OverallReadinessCard(m.overallReadiness)
                    m.categories.forEach { cat -> CategoryCard(cat) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OverallReadinessCard(readiness: Double) {
    val pct = (readiness * 100).toInt()
    val color = when {
        pct >= 80 -> ColorGreen
        pct >= 55 -> ColorAmber
        pct >= 25 -> ColorOrange
        else      -> ColorRed
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$pct%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
            Column {
                Text("Pregatire generala", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    when {
                        pct >= 80 -> "Esti pregatit"
                        pct >= 55 -> "Merge bine, continua"
                        pct >= 25 -> "Mai e de lucru"
                        else      -> "Abia ai inceput"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(cat: CategoryProgressCategory) {
    val color = statusColor(cat.color)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(12.dp).clip(CircleShape).background(color)
                )
                Text(cat.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                Text(statusLabel(cat.status), style = MaterialTheme.typography.labelSmall, color = color)
            }

            ProgressRow(label = "Acoperire", value = cat.coveragePercent, color = color)
            ProgressRow(label = "Invatat", value = cat.learnedPercent, color = color)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatChip("${cat.cards} carduri")
                if (cat.weak > 0) StatChip("${cat.weak} fragile", highlight = true)
            }

            if (cat.concepts.isNotEmpty()) {
                Text(
                    cat.concepts.take(5).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProgressRow(label: String, value: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(70.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(
            progress = { (value / 100.0).coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.15f),
        )
        Text("${value.toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatChip(text: String, highlight: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (highlight) ColorOrange.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall,
            color = if (highlight) ColorOrange else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
