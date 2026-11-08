// The fields of a time entry that the timer, the entry form, the Pomodoro page and the widget
// edit: the description with its suggestions, the project, task and tag picker, billable, and the
// split of an entry's time range.
plugins {
    alias(libs.plugins.tokkl.android.library.compose)
}

android {
    namespace = "com.github.e0ipso.tokkl.core.entryfields"
}

dependencies {
    // The fields and the picker's options are the cache's projects, tasks, tags and entries.
    api(project(":core:cache"))
    api(project(":core:ui"))

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)

    testImplementation(libs.junit)
}
