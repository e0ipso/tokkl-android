import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The app version lives in gradle/version.properties, which the release step
// rewrites. versionName is MAJOR.MINOR.PATCH, and
//
//     versionCode = major * 1_000_000 + minor * 1_000 + patch
//
// with minor and patch below 1000, so every release gets a higher code than
// the one before it. 0.1.0 gets 1000.
//
// The file also stores the computed number, because F-Droid reads it at each
// v* tag and cannot evaluate a formula. Its metadata for the app needs
//
//     UpdateCheckMode: Tags
//     UpdateCheckData: gradle/version.properties|versionCode=(\d+)|.|versionName=(\S+)
//
// The build fails when the stored number breaks the formula.
val appVersion = Properties().apply {
    load(
        providers.fileContents(layout.settingsDirectory.file("gradle/version.properties"))
            .asText.get().reader(),
    )
}
val appVersionName: String = appVersion.getProperty("versionName")
    ?: throw GradleException("gradle/version.properties has no versionName.")
val appVersionCode: Int = run {
    val parts = Regex("""(0|[1-9][0-9]{0,3})\.(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,2})""")
        .matchEntire(appVersionName)
        ?.destructured?.toList()?.map { it.toLong() }
        ?: throw GradleException(
            "gradle/version.properties: versionName $appVersionName is not MAJOR.MINOR.PATCH " +
                "with MINOR and PATCH below 1000.",
        )
    val (major, minor, patch) = parts
    val code = major * 1_000_000 + minor * 1_000 + patch
    if (code !in 1..Int.MAX_VALUE) {
        throw GradleException("versionName $appVersionName gives $code, outside 1..${Int.MAX_VALUE}.")
    }
    code.toInt()
}
appVersion.getProperty("versionCode").let { stored ->
    if (stored != appVersionCode.toString()) {
        throw GradleException(
            "gradle/version.properties: versionCode is $stored, but versionName " +
                "$appVersionName needs $appVersionCode.",
        )
    }
}

// Release signing reads the keystore path, the passwords and the key alias
// from these environment variables and from nowhere else, so no tracked file
// holds any of them. CI fills them from Actions secrets; README.md, "Release
// builds", names the secrets. With none of them set, assembleRelease builds
// the unsigned APK. Setting only some of them fails the build.
val releaseSigningVariables = listOf(
    "TOKKL_RELEASE_KEYSTORE",
    "TOKKL_RELEASE_KEYSTORE_PASSWORD",
    "TOKKL_RELEASE_KEY_ALIAS",
    "TOKKL_RELEASE_KEY_PASSWORD",
)
val releaseSigning: Map<String, String>? = run {
    val values = releaseSigningVariables.associateWith { name ->
        providers.environmentVariable(name).orNull?.takeIf { it.isNotEmpty() }
    }
    val missing = values.filterValues { it == null }.keys
    when {
        missing.size == values.size -> null
        missing.isNotEmpty() -> throw GradleException(
            "Release signing needs all of ${releaseSigningVariables.joinToString()}, or none " +
                "of them for an unsigned APK. Not set: ${missing.joinToString()}.",
        )
        else -> values.mapValues { (_, value) -> checkNotNull(value) }
    }
}

android {
    namespace = "com.github.e0ipso.tokkl"
    // Compose UI 1.12 and androidx.core 1.19 need compileSdk 37.
    compileSdk = 37
    // Pinned so that F-Droid builds with the same build-tools as CI, which
    // installs this version. 36.0.0 is both the default and the minimum of AGP
    // 9.4.1, so check this pin whenever AGP changes.
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.github.e0ipso.tokkl"
        minSdk = 26
        // The API level of the test emulator.
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = file(releaseSigning.getValue("TOKKL_RELEASE_KEYSTORE"))
                storePassword = releaseSigning.getValue("TOKKL_RELEASE_KEYSTORE_PASSWORD")
                keyAlias = releaseSigning.getValue("TOKKL_RELEASE_KEY_ALIAS")
                keyPassword = releaseSigning.getValue("TOKKL_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // When signing, AGP adds the dependency list to the APK signing block,
    // encrypted with a key only Google can read. F-Droid fails the build of an
    // app whose signed APK carries it ("found extra signing block 'Dependency
    // metadata'").
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
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

    // Declares the empty activity that createComposeRule() starts, in debug builds only.
    debugImplementation(libs.androidx.compose.ui.test.manifest)

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
