// What the timer's surfaces say when a timer action does not go through: the notices of the
// timer's pages and the toast of the notifications, the widget and the tile.
plugins {
    alias(libs.plugins.tokkl.android.library)
}

android {
    namespace = "com.github.e0ipso.tokkl.core.timernotice"
}

dependencies {
    // A notice carries the timer action's result, and a failure reads as the catalog's problem.
    api(project(":core:timerstate"))
    implementation(project(":core:catalog"))
}
