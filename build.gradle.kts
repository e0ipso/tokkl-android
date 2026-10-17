// AGP 9 compiles Kotlin itself and brings Kotlin Gradle plugin 2.2.10. The
// Compose compiler plugin depends on the Kotlin Gradle plugin of its own
// version, so declaring it here raises Kotlin to the catalog's version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
