import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.chaquopy)
}

// Release signing secrets are NEVER hardcoded in this committed build script.
// They are read from a gitignored keystore.properties (preferred) or, failing
// that, environment variables. See RELEASE_SIGNING.md. If neither is present,
// the release signingConfigs are left unconfigured so the build script still
// loads (a `*Release` build will then fail explicitly at signing time, which is
// the correct, secret-free behavior).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}
fun signingSecret(key: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(key)

android {
    namespace = "com.mindlift"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mindlift"
        minSdk = 26          // UsageStatsManager + overlay APIs available
        targetSdk = 36
        versionCode = 33
        versionName = "0.6.0-offline"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Public product target: real phones, small bundle. Keep only arm64.
            // Add x86_64 temporarily only for emulator debugging, not Play release.
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        // Dedicated AntiROTAid upload key (CN=AntiROTAid). Used by the `ara`
        // flavor so the public app is signed under its own brand, not MindLift.
        // Secrets read from keystore.properties / env (ANTIROTAID_*).
        create("ara") {
            val store = signingSecret("ANTIROTAID_KEYSTORE_PATH")
            if (store != null) {
                storeFile = file(store)
                storePassword = signingSecret("ANTIROTAID_KEYSTORE_PASSWORD")
                keyAlias = signingSecret("ANTIROTAID_KEY_ALIAS")
                keyPassword = signingSecret("ANTIROTAID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8/resource shrinking is DISABLED: it broke the AntiROTAid (ara)
            // release build (file import + slide-image cards silently failed while
            // the debug build worked). The ara APK is a personal/sideload build, so
            // a slightly larger APK is the right trade for guaranteed-correct
            // behavior. Re-enable only with verified Chaquopy/Compose keep rules and
            // a full on-device pass (import a PPTX, confirm image cards render).
            isMinifyEnabled = false
            isShrinkResources = false
            // Signing is assigned on the `ara` product flavor below.
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Public repository: only the `ara` distribution (the app published as
    // AntiROTAid) is kept. The project began as an internal prototype with two
    // more flavors (`full`, `play`); those and their engine were left out of
    // this repository.
    flavorDimensions += "distribution"
    productFlavors {
        // AntiROTAid (ARA): the public, Play-safe study UI PLUS a manual,
        // fully-dismissible overlay study mode. No usage stats, no accessibility,
        // no network — overlay is opt-in via SYSTEM_ALERT_WINDOW only. This is the
        // brand the owner ships to Google Play as `app.antirotaid`.
        create("ara") {
            dimension = "distribution"
            applicationId = "app.antirotaid"
            // Google Play release of AntiROTAid (app.antirotaid).
            //   vc1 / 1.0.0 = first closed-testing alpha upload.
            //   vc2 / 1.0.1 = Android Update 1 (reliability & bug-fix): robust resume
            //                 after deck changes, real file validation, complete course
            //                 deletion, safer imports. Same applicationId + upload key.
            //   vc3 / 1.0.2 = localized-demo baseline (never uploaded; ChatGPT handoff).
            //   vc4 / 1.0.3 = per-language demo lessons (RO/EN/DE/FR/ES) loaded at
            //                 runtime + Privacy / Terms / Rate links in Settings.
            //   vc5 / 1.0.4 = release-cleanup: old "MindLift" brand string in the
            //                 usage-access copy rebranded to AntiROTAid; dead
            //                 CoursePackV8Screen ("Generate premium course pack")
            //                 removed; stray "neural_networks.pdf" filename dropped
            //                 from the tutorial mock card. No feature/behavior change.
            //   vc6 / 1.0.5 = dex-string cleanup: the full-flavor Compose screens
            //                 (Course/Courses/HowToUse/Intelligence/Privacy/Tour) and
            //                 the hourly-progress notification title still carried the
            //                 old "MindLift" brand in user-visible string literals
            //                 (compiled into classes*.dex even though the ara UI never
            //                 shows them). All rebranded to AntiROTAid. Only remaining
            //                 "MindLift" tokens are code identifiers (MindLiftApplication/
            //                 Theme.MindLift/com.mindlift) + the Features.brandName
            //                 else-branch that resolves to "AntiROTAid" in ara. No
            //                 feature/behavior change.
            //   vc7 / 1.0.6 = Android 16 / API 36 update: compileSdk + targetSdk
            //                 raised 35 -> 36. No feature, UI, permission, content
            //                 or engine change; the audit found no API 36 behavior
            //                 break to fix (already edge-to-edge, already fully
            //                 resizable, already 16 KB-aligned native libs).
            //   vc8 / 1.0.7 = card fix: a TXT/notes slide with real lines under its
            //                 title is kept as text instead of "Visual slide — no
            //                 extractable text". No UI or permission change.
            // versionName is set explicitly here — no "-ara" suffix — so the store
            // shows a clean "1.0.7". Bumped above vc7 to avoid any Play Console clash.
            versionCode = 8
            versionName = "1.0.7"
            // Sign release builds of this flavor with the AntiROTAid upload key
            // (overrides the buildType-level release signingConfig).
            signingConfig = signingConfigs.getByName("ara")
            // PLAY_SAFE hides usage-monitor / life-modes / network entry points,
            // giving the clean public UI. ARA_MODE adds back only the manual
            // AntiROTAid overlay control.
            buildConfigField("boolean", "PLAY_SAFE", "true")
            buildConfigField("boolean", "ARA_MODE", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true   // exposes BuildConfig.PLAY_SAFE to gate code per flavor
    }
}

chaquopy {
    defaultConfig {
        version = "3.11"
        // The engine is pure stdlib. The one third-party need — PDF text
        // extraction for font-subset PDFs without a ToUnicode CMap — is met by
        // pypdf, which is pure-Python and VENDORED into engine_ara/pypdf (see
        // _extract_pdf_resilient). Vendoring keeps the offline build self-contained
        // (no pip/buildPython/network needed, survives clean-checkout audits), so
        // no pip block is required here.

        // Ship the engine as .py SOURCE, never .pyc. Chaquopy compiles to .pyc
        // only when it finds a local buildPython, so without this the contents of
        // the shipped bundle depend on whether the build machine happens to have
        // Python installed. The published 1.0.5/vc6 bundle shipped .py; pinning
        // this keeps every future build byte-comparable against that baseline.
        pyc { src = false }
    }
    sourceSets {
        // Per-flavor Python engine:
        //   ara -> the LEAN AntiROTAid product engine (engine_ara): lesson
        //          cards (lesson_chunk/slide_image), local behavior & progress only.
        // ara points at the MIRROR (engine_ara_runtime), not engine_ara directly,
        // so the release bundle never ships tests/, .pytest_cache/, __pycache__/ or
        // stale *.pyc. See syncEngineAra below.
        getByName("ara") { srcDir("../../engine_ara_runtime") }
    }
}

// The ara flavor's engine (engine_ara) is hand-maintained, but the working copy
// accumulates tests/, .pytest_cache/, __pycache__/ and *.pyc that must NOT ship
// in a Play release. Mirror it into a
// clean engine_ara_runtime (Sync also deletes stale files), and point the ara
// Python source set at that mirror instead of engine_ara.
val syncEngineAra by tasks.registering(Sync::class) {
    from("../../engine_ara") {
        exclude("**/__pycache__/**", "**/*.pyc", "**/*.pyo",
                "**/.pytest_cache/**", "tests/**", "**/tests/**")
    }
    into("../../engine_ara_runtime")
}

tasks.named("preBuild") { dependsOn(syncEngineAra) }
// Chaquopy's merge*PythonSources tasks read the engine dirs directly, so they must
// see the mirror tasks as explicit dependencies (Gradle 8 validation).
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("PythonSources") }
    .configureEach { dependsOn(syncEngineAra) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.navigation.compose)
    // NOTE: androidx.work (WorkManager) was removed. It was never used in any
    // flavor (no enqueue/Worker anywhere) yet it injected WAKE_LOCK,
    // ACCESS_NETWORK_STATE and RECEIVE_BOOT_COMPLETED into the merged manifest —
    // which contradicts the AntiROTAid "offline, nothing leaves the device"
    // Play story and Data Safety form. Dropping it yields a clean permission set.

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    // Android ships a non-functional org.json stub for JVM unit tests; the real
    // implementation lets the pure resume/cleanup logic (which persists small JSON
    // blobs) be unit-tested off-device.
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}


