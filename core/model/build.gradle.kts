// What the screens show and change: time entries, projects, clients, tasks, tags and timer
// templates, with typed ids. Plain Kotlin, apart from the ids' serializers, which let a route or a
// page's own server shape carry an id.
plugins {
    alias(libs.plugins.tokkl.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // The ids are @Serializable, so routes and server shapes can hold them.
    api(libs.kotlinx.serialization.core)
}
