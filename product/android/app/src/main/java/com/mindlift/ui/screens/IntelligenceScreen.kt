package com.mindlift.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindlift.ui.CourseViewModel
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntelligenceScreen(vm: CourseViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf("Hub") }
    LaunchedEffect(Unit) {
        vm.refreshAutopilotV2()
        vm.refreshLectureMap()
        vm.refreshConceptLadder()
        vm.refreshSmartDeck()
        vm.refreshEvidenceMap()
        vm.refreshCountdownPlan()
        vm.refreshRevisionPack()
        vm.refreshStudyPack()
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("AntiROTAid Intelligence") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Inapoi") } },
        )
    }) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "Suprafete noi") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("Hub", "Ladder", "Deck", "Evidence").forEach { label ->
                        OutlinedButton(onClick = { mode = label }) { Text(label) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("Countdown", "Revision", "Export", "Forecast").forEach { label ->
                        OutlinedButton(onClick = { mode = label }) { Text(label) }
                    }
                }
                Button(onClick = {
                    vm.refreshAutopilotV2(); vm.refreshSmartDeck(); vm.refreshEvidenceMap(); vm.refreshConceptLadder()
                }) { Text("Recalculeaza") }
            }

            when (mode) {
                "Hub" -> ProductPayloadCard("Autopilot v2", state.autopilotV2)
                "Ladder" -> ProductPayloadCard("Concept ladder", state.conceptLadder)
                "Deck" -> ProductPayloadCard("Smart deck compiler", state.smartDeck)
                "Evidence" -> ProductPayloadCard("Evidence map", state.evidenceMap)
                "Countdown" -> ProductPayloadCard("Exam countdown plan", state.countdownPlan)
                "Revision" -> ProductPayloadCard("Revision pack", state.revisionPack)
                "Export" -> ProductPayloadCard("Study pack export", state.studyPack)
                "Forecast" -> ProductPayloadCard("Attention forecast", state.attentionForecast)
            }
        }
    }
}

@Composable
fun ProductPayloadCard(title: String, payload: JSONObject?) {
    SectionCard(title = title) {
        if (payload == null) {
            Text("Se incarca sau nu exista inca date suficiente.")
        } else {
            Text(payload.toString(2), fontFamily = FontFamily.Monospace)
        }
    }
}
