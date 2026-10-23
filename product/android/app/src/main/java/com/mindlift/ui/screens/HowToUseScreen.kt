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
fun HowToUseScreen(onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Cum folosesti AntiROTAid") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Inapoi")
                }
            },
        )
    }) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeroPanel(
                eyebrow = "Start rapid",
                title = "Din notite in carduri de studiu",
                subtitle = "${com.mindlift.Features.brandName} functioneaza offline: creezi cursul, adaugi materialul si studiezi cardurile generate local. Nu ai cont, server sau chei externe."
            )
            SectionCard(title = "Fluxul recomandat") {
                StepItem("1", "Creeaza cursul", "Pune titlul si, daca o stii, data examenului.")
                StepItem("2", "Adauga material", "Lipeste lectia sau importa un fisier local. Pentru poze scanate, extrage textul cu o aplicatie OCR si lipeste-l aici.")
                StepItem("3", "Construieste deck-ul", "Motorul local separa continutul de logistica si construieste carduri de recapitulare.")
                StepItem("4", "Studiaza", "Raspunzi la carduri si alegi cum a mers: Inca, Greu, Bine sau Usor.")
                StepItem("5", "Urmareste progresul", "Vezi conceptele acoperite, cardurile de revazut si planul zilei.")
            }
            SectionCard(title = "Ce material merge bine") {
                Text("Cel mai bun rezultat apare cand textul contine definitii, explicatii, cauze, exemple sau pasi de rezolvare.")
                Text("Daca lipesti doar deadline-uri, nume, note finale sau mesaje administrative, AntiROTAid te avertizeaza inainte de generare.")
            }
            SectionCard(title = "Independenta") {
                Text("Datele raman pe telefon. Aplicatia instalata poate crea cursuri, genera carduri si urmari progresul fara conexiune la internet.")
            }
        }
    }
}
