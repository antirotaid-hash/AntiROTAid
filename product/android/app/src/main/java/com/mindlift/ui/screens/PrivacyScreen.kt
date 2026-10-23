package com.mindlift.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mindlift.R
import com.mindlift.bridge.Inventory
import com.mindlift.data.EngineRepository
import com.mindlift.data.EngineResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Owner data controls: see what's stored, export a user-owned local backup, or
 * wipe the local database. These make the offline-first promise tangible: no
 * account, no server, no external keys, no analytics, no sync. Network is OFF.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(repo: EngineRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var inventory by remember { mutableStateOf<Inventory?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }

    fun refresh() = scope.launch {
        when (val r = repo.inventory()) {
            is EngineResult.Ok -> inventory = r.value
            is EngineResult.Err -> status = r.message
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { refresh() }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = "Pregatesc backup-ul local…"
            val temp = File(context.cacheDir, "mindlift_backup_tmp.json")
            when (val exported = repo.export(temp.absolutePath)) {
                is EngineResult.Err -> status = exported.message
                is EngineResult.Ok -> {
                    val copied = withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                temp.inputStream().use { input -> input.copyTo(out) }
                            } ?: error("Nu am putut deschide destinatia aleasa.")
                        }
                    }
                    status = if (copied.isSuccess) {
                        "Backup salvat unde ai ales. AntiROTAid nu l-a trimis pe server."
                    } else {
                        "Nu am putut salva backup-ul: ${copied.exceptionOrNull()?.message ?: "eroare"}"
                    }
                    temp.delete()
                }
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.nav_privacy)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Inapoi")
                }
            },
        )
    }) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "Offline-first") {
                Text(
                    stringResource(R.string.privacy_no_network),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            SectionCard(title = "Control local") {
                Text(
                    "Aplicatia instalata ramane utilizabila fara cont, fara sincronizare si fara server AntiROTAid. Cursurile, cardurile, planurile si progresul sunt locale.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Open the public Privacy Policy in the external browser. ACTION_VIEW
            // hands off to the browser app, so this works even though AntiROTAid
            // itself has no INTERNET permission in the public build.
            OutlinedButton(
                onClick = {
                    val url = context.getString(R.string.privacy_policy_url)
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(url),
                            ),
                        )
                    }.onFailure { status = "Nu am putut deschide politica: $url" }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.privacy_policy_button)) }

            SectionCard(title = stringResource(R.string.privacy_inventory)) {
                val inv = inventory
                if (inv == null) {
                    Text("Citesc inventarul…")
                } else {
                    Text("${inv.totalRows} inregistrari locale  •  ${inv.sizeBytes / 1024} KB")
                    inv.perTable.entries.filter { it.value > 0 }.forEach {
                        Text("${it.key}: ${it.value}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            status?.let { SectionCard { Text(it) } }

            Button(
                onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                    createBackup.launch("mindlift_backup_$stamp.json")
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.privacy_export)) }

            OutlinedButton(
                onClick = { confirmWipe = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.privacy_delete_local)) }
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text(stringResource(R.string.privacy_delete_local)) },
            text = { Text("Sterge definitiv toate datele de pe acest telefon " +
                "(cursuri, carduri, progres). Nu se pot recupera.") },
            confirmButton = {
                Button(onClick = {
                    confirmWipe = false
                    scope.launch {
                        status = when (val r = repo.wipe()) {
                            is EngineResult.Ok -> { refresh(); "Datele au fost sterse. Reluam oricand." }
                            is EngineResult.Err -> r.message
                        }
                    }
                }) { Text("Sterge") }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("Anuleaza") }
            },
        )
    }
}
