package com.mindlift.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindlift.ui.CourseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutopilotScreen(vm: CourseViewModel, onBack: () -> Unit, onStudy: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshPulse(); vm.refreshLearnerProfile() }
    val pulse = state.pulse
    Scaffold(topBar = {
        TopAppBar(title = { Text("Autopilot") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(title = "Now") {
                if (pulse == null) {
                    Text("Calculating the next block…")
                } else {
                    Text("Remaining today: ${pulse.remainingMinutes.toInt()} min", style = MaterialTheme.typography.titleLarge)
                    Text("Studied: ${pulse.studiedMinutes.toInt()} min • next block: ${pulse.recommendedBlockMinutes.toInt()} min")
                    Text("Mode: ${pulse.mode} • next check in ${pulse.pollAfterMinutes} min")
                    Text(pulse.reason, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (pulse != null) {
                SectionCard(title = "Weak focus") {
                    Text(pulse.weakConcepts.take(6).ifEmpty { listOf("No urgent weak concepts yet") }.joinToString(" • "))
                }
                SectionCard(title = "Already stable") {
                    Text(pulse.masteredConcepts.take(6).ifEmpty { listOf("Nothing marked stable yet") }.joinToString(" • "))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onStudy, modifier = Modifier.weight(1f)) { Text("Start block") }
                OutlinedButton(onClick = { vm.refreshPulse() }, modifier = Modifier.weight(1f)) { Text("Refresh") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { vm.logStudyBlock(12.0) }, modifier = Modifier.weight(1f)) { Text("+12 study") }
                OutlinedButton(onClick = { vm.logLostTime(20.0) }, modifier = Modifier.weight(1f)) { Text("+20 lost") }
            }
        }
    }
}
