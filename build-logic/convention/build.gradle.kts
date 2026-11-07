plugins {
    `kotlin-dsl`
}

// The plugins compile against AGP only. The main build puts AGP and the Kotlin plugins on its
// classpath through the root build.gradle.kts, so every module runs the same plugin classes.
dependencies {
    compileOnly(libs.android.gradlePlugin)
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
    }
}
