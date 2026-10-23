package com.mindlift.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
fun PlanScreen(vm: CourseViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (state.plan == null) vm.buildPlan() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Planul de azi") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Inapoi")
                }
            },
        )
    }) { pad ->
        val plan = state.plan
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (plan == null) {
                Text("Pregatesc planul de azi…")
                return@Column
            }
            SectionCard(title = "Ritm de studiu") {
                Text(plan.message)
                Text("≈ ${plan.totalMinutes} min, in pasi mici",
                    style = MaterialTheme.typography.bodyMedium)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(plan.steps) { i, step ->
                    SectionCard {
                        Text("${i + 1}. ${step.prompt}", style = MaterialTheme.typography.bodyLarge)
                        Text("${step.reason}  •  ≈${step.estMinutes} min",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Button(onClick = { vm.buildPlan() }, modifier = Modifier.fillMaxWidth()) {
                Text("Reconstruieste planul")
            }
        }
    }
}
