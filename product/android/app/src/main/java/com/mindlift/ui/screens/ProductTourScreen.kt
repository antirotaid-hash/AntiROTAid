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
fun ProductTourScreen(onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Tur AntiROTAid") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Inapoi") } }
        )
    }) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeroPanel(
                eyebrow = com.mindlift.Features.brandName,
                title = "Aplicatie de studiu local-first",
                subtitle = "Nu este un chatbot si nu trimite materia pe server. Transforma materialul tau in carduri, plan si progres direct pe telefon."
            )
            SectionCard(title = "Ce primeste utilizatorul") {
                StepItem("1", "Carduri din materialul propriu", "Intrebari de definitie, rol, examen si completare, toate ancorate in textul importat.")
                StepItem("2", "Plan scurt de studiu", "Pasi mici pentru ziua curenta, in functie de cardurile pregatite si data examenului.")
                StepItem("3", "Progres vizibil", "Categorii, concepte acoperite si carduri care trebuie revazute.")
                StepItem("4", "Control local", "Backup JSON si stergere locala din ecranul de confidentialitate.")
            }
            SectionCard(title = "Limite asumate") {
                Text("AntiROTAid nu inventeaza teorie din nimic. Calitatea cardurilor depinde de calitatea materialului introdus.")
                Text("Prima versiune publica este offline-only: fara cont, fara sincronizare si fara servicii online.")
            }
        }
    }
}
