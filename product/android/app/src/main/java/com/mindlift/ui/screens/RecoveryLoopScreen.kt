package com.mindlift.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindlift.ui.CourseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryLoopScreen(vm: CourseViewModel, onBack: () -> Unit, onStudy: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Recovery loop") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = vm::refreshRecoveryLoop, modifier = Modifier.fillMaxWidth()) { Text("Refresh recovery loop") }
            state.recoveryLoop?.let { loop ->
                SectionCard(title = "Status") {
                    Text("Mode: ${loop.optString("mode")} • urgency: ${loop.optString("urgency")}")
                    Text("Next screen: ${loop.optString("next_screen")} • check: ${loop.optInt("poll_after_minutes")} min")
                }
                val steps = loop.optJSONArray("steps")
                if (steps != null) {
                    for (i in 0 until steps.length()) {
                        val step = steps.optJSONObject(i)
                        SectionCard(title = step.optString("title", "Step")) {
                            Text("${step.optString("kind")} • ${step.optDouble("minutes")} min")
                            Text(step.optJSONObject("payload")?.toString(2) ?: "")
                        }
                    }
                }
                OutlinedButton(onClick = onStudy, modifier = Modifier.fillMaxWidth()) { Text("Go to study block") }
            } ?: SectionCard { Text("No recovery loop loaded yet.") }
        }
    }
}
