package com.mindlift.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.mindlift.R
import com.mindlift.usage.AttentionMonitorService
import com.mindlift.usage.UsagePermissions

/**
 * Owner controls for the optional local attention monitor. Both permissions are
 * optional; the copy is explicit that manual mode works without them. Re-checks
 * permission state on resume (the owner grants these in system Settings).
 */
@Composable
fun MonitorControls(courseId: String) {
    val context = LocalContext.current
    var usageGranted by remember { mutableStateOf(UsagePermissions.hasUsageAccess(context)) }
    var overlayGranted by remember { mutableStateOf(UsagePermissions.hasOverlayAccess(context)) }
    var monitoring by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                usageGranted = UsagePermissions.hasUsageAccess(context)
                overlayGranted = UsagePermissions.hasOverlayAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    SectionCard(title = "Monitor de atentie (optional)") {
        Text(
            if (usageGranted) "Acces la utilizare acordat."
            else stringResource(R.string.usage_access_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!usageGranted) {
            OutlinedButton(
                onClick = { context.startActivity(UsagePermissions.usageAccessIntent()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.usage_access_title)) }
        }

        Text(
            if (overlayGranted) "Permisiune overlay acordata."
            else stringResource(R.string.overlay_access_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!overlayGranted) {
            OutlinedButton(
                onClick = { context.startActivity(UsagePermissions.overlayAccessIntent(context)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.overlay_access_title)) }
        }

        if (!monitoring) {
            Button(
                onClick = { AttentionMonitorService.start(context, courseId); monitoring = true },
                enabled = usageGranted,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Porneste monitorul local") }
        } else {
            OutlinedButton(
                onClick = { AttentionMonitorService.stop(context); monitoring = false },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Opreste monitorul") }
        }

        if (!usageGranted) {
            Text(stringResource(R.string.degraded_mode),
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}
