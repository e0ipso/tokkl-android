// Times in the account's zone, durations and their text, with the web app's rounding.
plugins {
    alias(libs.plugins.tokkl.android.library)
}

android {
    namespace = "com.github.e0ipso.tokkl.core.time"
}

dependencies {
    testImplementation(libs.junit)
}
