package com.mindlift.overlay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindlift.MindLiftApplication
import com.mindlift.data.EngineRepository
import com.mindlift.ui.theme.MindLiftTheme

/**
 * Full-screen gentle recovery overlay. Launched by the attention monitor when
 * pull is high. It asks the engine for an intervention decision (which enforces
 * the cooldown — a recent nudge is suppressed and the overlay simply closes),
 * then renders the no-shame payload with a microtask and a bounded break.
 */
class OverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val courseId = intent.getStringExtra(EXTRA_COURSE_ID).orEmpty()
        val repo = EngineRepository(MindLiftApplication.from(application).engine)

        setContent {
            MindLiftTheme {
                val vm: OverlayViewModel = viewModel(
                    factory = OverlayViewModel.factory(repo, courseId))
                OverlayScreen(vm = vm, onDismiss = {
                    finish()
                    moveTaskToBack(true)
                })
            }
        }
    }

    companion object {
        const val EXTRA_COURSE_ID = "course_id"
    }
}
