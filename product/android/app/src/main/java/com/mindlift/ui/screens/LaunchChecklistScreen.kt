package com.mindlift.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaunchChecklistScreen(onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Checklist lansare") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Inapoi") } }
        )
    }) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeroPanel(
                eyebrow = "Inainte de Google Play",
                title = "Verificare produs public",
                subtitle = "Aceasta lista confirma ca varianta publica ramane offline-only si ca flow-ul de studiu este clar pentru un utilizator nou."
            )
            SectionCard(title = "Produs") {
                StepItem("1", "Curs nou", "Creeaza un curs fara cont si fara conexiune la internet.")
                StepItem("2", "Import", "Lipeste un text real de curs, nu doar logistica.")
                StepItem("3", "Carduri", "Construieste deck-ul si verifica daca intrebarile sunt clare.")
                StepItem("4", "Studiu", "Parcurge minimum 5 carduri si noteaza cum a mers.")
                StepItem("5", "Progres", "Verifica harta cursului, progresul si planul zilei.")
                StepItem("6", "Confidentialitate", "Testeaza backup local si stergere date locale.")
            }
            SectionCard(title = "Release") {
                StepItem("A", "Manifest", "playRelease nu declara INTERNET, usage stats, overlay, foreground service sau notificari.")
                StepItem("B", "Bundle", "Livreaza app-play-release.aab, nu APK debug.")
                StepItem("C", "Politica", "Privacy Policy este hostata public si identica in app si Play Console.")
            }
        }
    }
}
