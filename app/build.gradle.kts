import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.github.e0ipso.tokkl"
    // Compose UI 1.12 and androidx.core 1.19 need compileSdk 37.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.github.e0ipso.tokkl"
        minSdk = 26
        // The API level of the test emulator.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // Puts the merged manifest's path on the unit test classpath, in
        // com/android/tools/test_config.properties, for BackupRulesTest.
        unitTests.isIncludeAndroidResources = true
        // Runs each instrumented test in a new app process, which
        // ApiTokenStoreRestartTest needs to restart the process between steps.
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver3)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestUtil(libs.androidx.test.orchestrator)
}

// The dependency graph of the release runtime classpath, for the check below.
val releaseDependencyGraph = objects.property(ResolvedComponentResult::class.java)
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        releaseDependencyGraph.set(
            variant.runtimeConfiguration.incoming.resolutionResult.rootComponent,
        )
    }
}

// Fails when the release runtime classpath holds a module from a group in
// gradle/denied-dependency-groups.txt, or from a group below one of them, and
// names each such module with the path that pulled it in. `check` runs it.
val checkDeniedDependencies = tasks.register("checkDeniedDependencies") {
    group = "verification"
    description = "Fails when a denied dependency group reaches the release runtime classpath."
    val denyList = layout.settingsDirectory.file("gradle/denied-dependency-groups.txt").asFile
    val graph = releaseDependencyGraph
    inputs.file(denyList)
    doLast {
        val reasons = linkedMapOf<String, String>()
        denyList.readLines().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val group = line.substringBefore('#').trim()
            val reason = line.substringAfter('#', "").trim()
            if (group.isEmpty() || reason.isEmpty() || group.any { it.isWhitespace() }) {
                throw GradleException(
                    "${denyList.path}:${index + 1}: write each entry as \"<group> # <reason>\".",
                )
            }
            reasons[group] = reason
        }

        // Breadth first, remembering which component first reached each one.
        val root = graph.get()
        val reachedFrom = mutableMapOf<ResolvedComponentResult, ResolvedComponentResult>()
        val queue = ArrayDeque(listOf(root))
        val seen = mutableSetOf(root)
        val offenders = linkedMapOf<String, MutableList<String>>()
        while (queue.isNotEmpty()) {
            val component = queue.removeFirst()
            val id = component.id
            if (id is ModuleComponentIdentifier) {
                val denied = reasons.keys.firstOrNull { id.group == it || id.group.startsWith("$it.") }
                if (denied != null) {
                    val path = generateSequence(reachedFrom[component]) { reachedFrom[it] }
                        .toList()
                        .asReversed()
                        .joinToString(" > ") { it.id.displayName }
                    offenders.getOrPut(denied) { mutableListOf() } +=
                        "    ${id.displayName}, via $path"
                }
            }
            component.dependencies
                .filterIsInstance<ResolvedDependencyResult>()
                .filter { !it.isConstraint }
                .forEach { dependency ->
                    if (seen.add(dependency.selected)) {
                        reachedFrom[dependency.selected] = component
                        queue.addLast(dependency.selected)
                    }
                }
        }
        if (offenders.isNotEmpty()) {
            val report = offenders.entries.joinToString("\n") { (denied, modules) ->
                "  $denied: ${reasons.getValue(denied)}\n" + modules.joinToString("\n")
            }
            throw GradleException(
                "The release runtime classpath holds modules from groups denied in " +
                    "gradle/denied-dependency-groups.txt:\n$report",
            )
        }
    }
}

tasks.named("check") {
    dependsOn(checkDeniedDependencies)
}
