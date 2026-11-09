plugins {
    `kotlin-dsl`
}

// The plugins compile against AGP, the Kotlin Gradle plugin, detekt and Animal Sniffer only. The
// main build puts those on its classpath through the root build.gradle.kts, so every module runs
// the same plugin classes.
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.animalsniffer.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "tokkl.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "tokkl.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "tokkl.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "tokkl.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidScreenFeature") {
            id = "tokkl.android.feature.screen"
            implementationClass = "AndroidScreenFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = "tokkl.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
