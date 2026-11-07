// AGP 9 compiles Kotlin itself and brings Kotlin Gradle plugin 2.2.10. The
// Compose compiler plugin depends on the Kotlin Gradle plugin of its own
// version, so declaring it here raises Kotlin to the catalog's version.
// Every plugin a module uses is declared here, so all modules load the same
// plugin classes, and the convention plugins in build-logic apply them by ID.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // KSP extends the Kotlin Gradle plugin, so it loads beside it.
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.detekt) apply false
}
