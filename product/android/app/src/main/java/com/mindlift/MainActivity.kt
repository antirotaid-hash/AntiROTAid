package com.mindlift

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mindlift.data.EngineRepository
import com.mindlift.ui.MindLiftApp
import com.mindlift.ui.theme.MindLiftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // AntiROTAid (ara) ships the lean lesson/card-feed product, not the
        // MindLift course UI — route straight into it at launch.
        if (com.mindlift.Features.araMode) {
            startActivity(android.content.Intent(this, com.mindlift.ara.AraStudyActivity::class.java))
            finish()
            return
        }
        enableEdgeToEdge()
        val repo = EngineRepository(MindLiftApplication.from(application).engine)
        setContent {
            MindLiftTheme {
                MindLiftApp(repo)
            }
        }
    }
}
