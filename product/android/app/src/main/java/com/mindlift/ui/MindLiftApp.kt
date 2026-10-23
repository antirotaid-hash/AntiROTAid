package com.mindlift.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mindlift.data.EngineRepository
import com.mindlift.ui.screens.CourseScreen
import com.mindlift.ui.screens.CoursesScreen
import com.mindlift.ui.screens.PlanScreen
import com.mindlift.ui.screens.PrivacyScreen
import com.mindlift.ui.screens.ReportScreen
import com.mindlift.ui.screens.StudyScreen
import com.mindlift.ui.screens.SyllabusScreen
import com.mindlift.ui.screens.QualityScreen
import com.mindlift.ui.screens.OralExamScreen
import com.mindlift.ui.screens.AutopilotScreen
import com.mindlift.ui.screens.IntelligenceScreen
import com.mindlift.ui.screens.ComplianceScreen
import com.mindlift.ui.screens.ImportWorkbenchScreen
import com.mindlift.ui.screens.RecoveryLoopScreen
import com.mindlift.ui.screens.InsightsScreen
import com.mindlift.ui.screens.HowToUseScreen
import com.mindlift.ui.screens.LaunchChecklistScreen
import com.mindlift.ui.screens.ProductTourScreen
import com.mindlift.ui.screens.ProgressScreen

object Routes {
    const val COURSES = "courses"
    const val COURSE = "course/{id}/{title}"
    const val STUDY = "study/{id}"
    const val PLAN = "plan/{id}"
    const val REPORT = "report/{id}"
    const val AUTOPILOT = "autopilot/{id}"
    const val SYLLABUS = "syllabus/{id}"
    const val ORAL = "oral/{id}"
    const val QUALITY = "quality/{id}"
    const val INTELLIGENCE = "intelligence/{id}"
    const val RECOVERY = "recovery/{id}"
    const val IMPORT_WORKBENCH = "import_workbench/{id}"
    const val COMPLIANCE = "compliance/{id}"
    const val INSIGHTS = "insights/{id}"
    const val PRIVACY = "privacy"
    const val LIFE = "life"
    const val HOW_TO_USE = "how_to_use"
    const val PRODUCT_TOUR = "product_tour"
    const val LAUNCH_CHECKLIST = "launch_checklist"
    const val PROGRESS = "progress/{id}"

    fun course(id: String, title: String) =
        "course/$id/${android.net.Uri.encode(title)}"
    fun study(id: String) = "study/$id"
    fun plan(id: String) = "plan/$id"
    fun report(id: String) = "report/$id"
    fun autopilot(id: String) = "autopilot/$id"
    fun syllabus(id: String) = "syllabus/$id"
    fun oral(id: String) = "oral/$id"
    fun quality(id: String) = "quality/$id"
    fun intelligence(id: String) = "intelligence/$id"
    fun recovery(id: String) = "recovery/$id"
    fun importWorkbench(id: String) = "import_workbench/$id"
    fun compliance(id: String) = "compliance/$id"
    fun insights(id: String) = "insights/$id"
    fun progress(id: String) = "progress/$id"
}

/** Root navigation. The course list owns [MainViewModel]; per-course screens
 *  each build their own [CourseViewModel] keyed by course id. */
@Composable
fun MindLiftApp(repo: EngineRepository) {
    val nav = rememberNavController()
    val mainVm: MainViewModel = viewModel(factory = MainViewModel.factory(repo))

    NavHost(navController = nav, startDestination = Routes.COURSES) {
        composable(Routes.COURSES) {
            val state by mainVm.state.collectAsStateWithLifecycle()
            CoursesScreen(
                state = state,
                onCreate = { title, exam -> mainVm.createCourse(title, exam) { c ->
                    nav.navigate(Routes.course(c.id, c.title))
                } },
                onExamDateChanged = mainVm::previewExamDate,
                onOpen = { c -> nav.navigate(Routes.course(c.id, c.title)) },
                onDelete = { id -> mainVm.deleteCourse(id) },
                onPrivacy = { nav.navigate(Routes.PRIVACY) },
                onHowToUse = { nav.navigate(Routes.HOW_TO_USE) },
                onProductTour = { nav.navigate(Routes.PRODUCT_TOUR) },
                onLaunchChecklist = { nav.navigate(Routes.LAUNCH_CHECKLIST) },
                onDismissMessage = mainVm::clearMessage,
                onLife = if (com.mindlift.Features.playSafe) null
                         else ({ nav.navigate(Routes.LIFE) }),
            )
        }
        composable(Routes.COURSE) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val title = entry.arguments?.getString("title").orEmpty()
            val vm: CourseViewModel = viewModel(
                key = "course-$id", factory = CourseViewModel.factory(repo, id))
            CourseScreen(
                courseId = id, title = title, vm = vm,
                onStudy = { nav.navigate(Routes.study(id)) },
                onPlan = { nav.navigate(Routes.plan(id)) },
                onReport = { nav.navigate(Routes.report(id)) },
                onAutopilot = { nav.navigate(Routes.autopilot(id)) },
                onSyllabus = { nav.navigate(Routes.syllabus(id)) },
                onOral = { nav.navigate(Routes.oral(id)) },
                onQuality = { nav.navigate(Routes.quality(id)) },
                onIntelligence = { nav.navigate(Routes.intelligence(id)) },
                onRecovery = { nav.navigate(Routes.recovery(id)) },
                onImportWorkbench = { nav.navigate(Routes.importWorkbench(id)) },
                onCompliance = { nav.navigate(Routes.compliance(id)) },
                onInsights = { nav.navigate(Routes.insights(id)) },
                onProgress = { nav.navigate(Routes.progress(id)) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.STUDY) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: CourseViewModel = viewModel(
                key = "course-$id", factory = CourseViewModel.factory(repo, id))
            StudyScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.PLAN) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: CourseViewModel = viewModel(
                key = "course-$id", factory = CourseViewModel.factory(repo, id))
            PlanScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.REPORT) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: CourseViewModel = viewModel(
                key = "course-$id", factory = CourseViewModel.factory(repo, id))
            ReportScreen(vm = vm, onBack = { nav.popBackStack() })
        }

        composable(Routes.AUTOPILOT) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: CourseViewModel = viewModel(key = "course-$id", factory = CourseViewModel.factory(repo, id))
            AutopilotScreen(vm = vm, onBack = { nav.popBackStack() }, onStudy = { nav.navigate(Routes.study(id)) })
        }
        composable(Routes.SYLLABUS) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: CourseViewModel = viewModel(key = "course-$id", factory = CourseViewModel.factory(repo, id))
            SyllabusScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.ORAL) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: CourseViewModel = viewModel(key = "course-$id", factory = CourseViewModel.factory(repo, id))
            OralExamScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.QUALITY) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: CourseViewModel = viewModel(key = "course-$id", factory = CourseViewModel.factory(repo, id))
            QualityScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.INTELLIGENCE) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: CourseViewModel = viewModel(key = "course-$id", factory = CourseViewModel.factory(repo, id))
            IntelligenceScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.RECOVERY) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: CourseViewModel = viewModel(key = "course-$id", factory = CourseViewModel.factory(repo, id))
            RecoveryLoopScreen(vm = vm, onBack = { nav.popBackStack() }, onStudy = { nav.navigate(Routes.study(id)) })
        }
        composable(Routes.IMPORT_WORKBENCH) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: CourseViewModel = viewModel(key = "course-$id", factory = CourseViewModel.factory(repo, id))
            ImportWorkbenchScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.COMPLIANCE) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: CourseViewModel = viewModel(key = "course-$id", factory = CourseViewModel.factory(repo, id))
            ComplianceScreen(vm = vm, onBack = { nav.popBackStack() })
        }

        composable(Routes.INSIGHTS) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: CourseViewModel = viewModel(key = "course-$id", factory = CourseViewModel.factory(repo, id))
            InsightsScreen(vm = vm, onBack = { nav.popBackStack() })
        }

        composable(Routes.PRIVACY) {
            PrivacyScreen(repo = repo, onBack = { nav.popBackStack() })
        }
        composable(Routes.LIFE) {
            com.mindlift.ui.screens.LifeScreen(repo = repo, onBack = { nav.popBackStack() })
        }
        composable(Routes.HOW_TO_USE) {
            HowToUseScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.PRODUCT_TOUR) {
            ProductTourScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.LAUNCH_CHECKLIST) {
            LaunchChecklistScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.PROGRESS) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ProgressScreen(courseId = id, repo = repo, onBack = { nav.popBackStack() })
        }
    }
}
