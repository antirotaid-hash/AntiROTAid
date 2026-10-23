package com.mindlift.ara

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Compose CompositionLocals for the AntiROTAid UI.
 *
 * Kept OUT of AraI18n.kt on purpose: the translation tables and the pure
 * `trFor` / `importErrorFor` helpers must stay Compose-free so they can be
 * exercised in plain JVM unit tests (localization completeness) without loading
 * the Compose runtime at class-initialization time.
 */
val LocalTr = staticCompositionLocalOf { trFor(Lang.EN) }
