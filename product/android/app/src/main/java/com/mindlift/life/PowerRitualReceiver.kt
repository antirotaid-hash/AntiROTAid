package com.mindlift.life

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mindlift.Features
import com.mindlift.MindLiftApplication

/**
 * The charging ritual (full flavor): plugging in at night is the closing
 * signal; pulling the charger inside the sleep window is the ritual broken on
 * purpose, and the engine escalates. The engine owns the window — this only
 * reports plug/unplug, so daytime cable handling stays pure noise.
 *
 * Registered in src/full/AndroidManifest.xml; ACTION_POWER_CONNECTED and
 * ACTION_POWER_DISCONNECTED are exempt from the implicit-broadcast limits.
 */
class PowerRitualReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Features.playSafe) return
        val connected = when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> true
            Intent.ACTION_POWER_DISCONNECTED -> false
            else -> return
        }
        val pending = goAsync()  // the engine call must outlive onReceive
        Thread {
            try {
                MindLiftApplication.from(
                    context.applicationContext as android.app.Application)
                    .engine.lifePowerEvent(connected)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}
