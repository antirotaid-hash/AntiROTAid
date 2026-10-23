// Root build file. Plugin versions are declared here with `apply false`
// and applied per-module. Keeps the local-first, single-module app simple.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.chaquopy) apply false
}
