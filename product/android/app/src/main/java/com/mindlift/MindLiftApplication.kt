package com.mindlift

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.mindlift.bridge.EngineBridge

/**
 * App entry point. Boots the embedded Python runtime (Chaquopy) once and creates
 * a single [EngineBridge] backed by an on-device SQLite file.
 *
 * Product rule for v12: the Android app is offline-only. It does not read API
 * keys, does not load environment secret files, does not call a backend, and does not depend
 * on the owner after installation. All course data and generated cards live in
 * the app's private storage on the learner's device.
 */
class MindLiftApplication : Application() {

    lateinit var engine: EngineBridge
        private set

    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        val dbPath = getDatabasePath("mindlift.db").absolutePath
        // Always force the embedded engine into deterministic local mode. No
        // Environment secret files, external keys, account, backend, analytics endpoint, or owner
        // machine is part of the runtime contract.
        engine = EngineBridge(Python.getInstance(), dbPath, envFile = "", aiProvider = "local_rules")
    }

    companion object {
        fun from(app: Application): MindLiftApplication = app as MindLiftApplication
    }
}
