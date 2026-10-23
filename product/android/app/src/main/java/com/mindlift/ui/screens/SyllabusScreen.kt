package com.mindlift.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
fun SyllabusScreen(vm: CourseViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (state.syllabus == null) vm.refreshSyllabus() }
    val syllabus = state.syllabus
    Scaffold(topBar = {
        TopAppBar(title = { Text("Harta cursului") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Inapoi") }
        })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (syllabus == null) { Text("Construiesc harta cursului…"); return@Column }
            SectionCard(title = syllabus.title) {
                Text("${syllabus.studyUnits} unitati de studiu • ${syllabus.logisticsUnits} fragmente administrative • ≈${syllabus.estimatedTotalMinutes.toInt()} min")
                syllabus.notes.take(2).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(syllabus.units) { unit ->
                    SectionCard(title = unit.title) {
                        Text("${unit.label} • prioritate ${(unit.priority * 100).toInt()}% • ≈${unit.estimatedMinutes} min")
                        if (unit.concepts.isNotEmpty()) Text(unit.concepts.take(8).joinToString(" • "))
                        Text(unit.reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
