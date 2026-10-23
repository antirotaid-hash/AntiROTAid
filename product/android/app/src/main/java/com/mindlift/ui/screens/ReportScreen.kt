package com.mindlift.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
fun ReportScreen(vm: CourseViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (state.report == null) vm.buildReport() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Raportul zilei") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Inapoi")
                }
            },
        )
    }) { pad ->
        val report = state.report
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (report == null) {
                Text("Pregatesc raportul…")
                return@Column
            }
            SectionCard(title = report.localDay) {
                Text("Pasi de studiu azi: ${report.reviewsToday}  •  de revazut: ${report.dueCount}",
                    style = MaterialTheme.typography.bodyMedium)
            }
            SectionCard(title = "Cum a fost ziua") {
                Text(report.narrative, style = MaterialTheme.typography.bodyLarge)
            }
            SectionCard(title = "Maine") {
                Text(report.tomorrowFocus, style = MaterialTheme.typography.bodyLarge)
            }
            Button(onClick = { vm.buildReport() }, modifier = Modifier.fillMaxWidth()) {
                Text("Actualizeaza")
            }
        }
    }
}
