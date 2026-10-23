package com.mindlift

/**
 * Single switch for the public, privacy-safe distribution.
 *
 * [PLAY_SAFE] is set per Gradle product flavor (see app/build.gradle.kts):
 *   - play flavor -> true  : reduced public build. No attention monitor, no
 *                            recovery overlay, no network AI. Cards + PDF/pages
 *                            study only. Everything stays on the device.
 *   - full flavor -> false : owner/internal build with local monitor / overlay.
 *                            Still no cloud AI/API-key in v12.
 *
 * Code paths that touch a removed permission or service must check
 * [Features.playSafe]. No build flavor should require network or external keys.
 */
object Features {
    /** True in the reduced public (Play) build. */
    val playSafe: Boolean = BuildConfig.PLAY_SAFE

    /**
     * True only in the AntiROTAid (`ara`) flavor. Routes the app into the lean
     * lesson/card-feed product and enables the manual, fully-dismissible
     * overlay study mode. Set per Gradle flavor via BuildConfig.ARA_MODE.
     */
    val araMode: Boolean = BuildConfig.ARA_MODE

    /** User-facing product name: "AntiROTAid" in the ara flavor, else "MindLift". */
    val brandName: String = if (BuildConfig.ARA_MODE) "AntiROTAid" else "MindLift"
}
