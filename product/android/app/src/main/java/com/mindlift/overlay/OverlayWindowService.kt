package com.mindlift.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.mindlift.MindLiftApplication
import com.mindlift.data.EngineRepository
import com.mindlift.ui.theme.MindLiftTheme

/**
 * Draws the study-card recovery overlay as a TYPE_APPLICATION_OVERLAY window so
 * it floats over the currently-active app (YouTube Shorts, etc.) without switching
 * the task or bringing MindLift to the foreground.
 *
 * Requires SYSTEM_ALERT_WINDOW (already granted by the owner via Settings →
 * Special app access → Display over other apps).
 */
class OverlayWindowService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val _vmStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = _vmStore

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val courseId = intent?.getStringExtra(EXTRA_COURSE_ID).orEmpty()
        val riskLevel = intent?.getStringExtra(EXTRA_RISK_LEVEL) ?: "high"
        val interventionJson = intent?.getStringExtra(EXTRA_INTERVENTION_JSON)
        val scrolledMinutes = intent?.getDoubleExtra(EXTRA_SCROLLED_MINUTES, 0.0) ?: 0.0
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        if (overlayView == null) showOverlay(courseId, riskLevel, interventionJson, scrolledMinutes)
        return START_NOT_STICKY
    }

    private fun showOverlay(courseId: String, riskLevel: String = "high",
                            interventionJson: String? = null,
                            scrolledMinutes: Double = 0.0) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayWindowService)
            setViewTreeSavedStateRegistryOwner(this@OverlayWindowService)
            setViewTreeViewModelStoreOwner(this@OverlayWindowService)
            setContent {
                MindLiftTheme {
                    if (com.mindlift.Features.araMode) {
                        // AntiROTAid: the overlay IS the lesson card feed (over
                        // other apps). Exit closes this card; stopping the whole
                        // mode lives only on the home on/off button + notification.
                        com.mindlift.ara.AraOverlayContent(
                            onExit = { stopSelf() },
                        )
                    } else {
                        val repo = EngineRepository(MindLiftApplication.from(application).engine)
                        val vm: OverlayViewModel = viewModel(
                            factory = OverlayViewModel.factory(repo, courseId, riskLevel,
                                interventionJson, scrolledMinutes)
                        )
                        OverlayScreen(vm = vm, onDismiss = { stopSelf() })
                    }
                }
            }
        }
        overlayView = view
        wm.addView(view, params)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        _vmStore.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_COURSE_ID = "course_id"
        const val EXTRA_RISK_LEVEL = "risk_level"
        const val EXTRA_INTERVENTION_JSON = "intervention_json"
        const val EXTRA_SCROLLED_MINUTES = "scrolled_minutes"
    }
}
