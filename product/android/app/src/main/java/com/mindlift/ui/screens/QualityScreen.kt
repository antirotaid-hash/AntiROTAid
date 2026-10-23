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
fun QualityScreen(vm: CourseViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (state.deckQuality == null) vm.refreshQuality() }
    val quality = state.deckQuality
    Scaffold(topBar = {
        TopAppBar(title = { Text("Calitatea deck-ului") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Inapoi") }
        })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (quality == null) { Text("Verific deck-ul…"); return@Column }
            SectionCard(title = "Deck verificat") {
                Text("${quality.keptCards}/${quality.totalCards} carduri pastrate • ${quality.rejectedCards} respinse")
                Text("Scor mediu: ${quality.scoreAverage} • limita recomandata: ${quality.recommendedCardCap}")
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quality.issues.entries.toList()) { issue ->
                    SectionCard { Text("${issue.key}: ${issue.value}") }
                }
            }
        }
    }
}
