package com.mindlift.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindlift.ui.CourseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OralExamScreen(vm: CourseViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (state.oralPack == null) vm.refreshOralExam() }
    val pack = state.oralPack
    Scaffold(topBar = {
        TopAppBar(title = { Text("Oral exam") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (pack == null) { Text("Preparing oral exam questions…"); return@Column }
            SectionCard(title = "Oral pack") {
                Text("${pack.questionCount} questions • ≈${pack.estimatedMinutes.toInt()} min")
                pack.strategy.take(3).forEach { Text("• $it") }
            }
            Button(onClick = { vm.refreshOralExam() }, modifier = Modifier.fillMaxWidth()) { Text("Regenerate") }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pack.questions) { q ->
                    SectionCard(title = q.concept) {
                        Text(q.prompt)
                        Text("Difficulty: ${q.difficulty}")
                        Text("Expected: ${q.expectedPoints.take(3).joinToString("; ")}")
                        if (q.followups.isNotEmpty()) Text("Follow-up: ${q.followups.first()}")
                    }
                }
            }
        }
    }
}
