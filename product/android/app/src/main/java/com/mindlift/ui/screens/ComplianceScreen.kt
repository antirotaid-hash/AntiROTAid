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
fun ComplianceScreen(vm: CourseViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Compliance") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = vm::refreshCompliance, modifier = Modifier.fillMaxWidth()) { Text("Refresh compliance") }
            state.manualCompliance?.let { comp ->
                SectionCard(title = "Manual compliance") {
                    Text(comp.optString("owner_summary"))
                    Text(comp.optJSONObject("counts")?.toString(2) ?: "")
                    Text("Release candidate: ${comp.optBoolean("release_candidate")}")
                }
            }
            state.releaseGate?.let { gate -> SectionCard(title = "Release gate") { Text(gate.toString(2)) } }
        }
    }
}
