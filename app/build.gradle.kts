plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Release identity is derived from annotated git tags shaped `vMAJOR.MINOR.PATCH`.
val gitDescribe: String = runCatching {
    providers.exec {
        commandLine("git", "describe", "--tags", "--match", "v*", "--dirty", "--always")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrDefault("")

val describedVersion = Regex("""^v(\d+)\.(\d+)\.(\d+)(?:-(\d+)-g[0-9a-f]+)?(-dirty)?$""")
    .matchEntire(gitDescribe)

val semVer = describedVersion?.let {
    Triple(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
}

// Both groups are empty only when HEAD is exactly a tag and the tree is clean.
val isTaggedRelease = describedVersion != null &&
    describedVersion.groupValues[4].isEmpty() &&
    describedVersion.groupValues[5].isEmpty()

// Play never accepts a re-used versionCode: bump this to re-upload the same tag.
val buildNumber = providers.gradleProperty("KNOTSSH_BUILD_NUMBER").orNull?.toInt() ?: 0

val appVersionCode = semVer?.let { (major, minor, patch) ->
    major * 1_000_000 + minor * 10_000 + patch * 100 + buildNumber
} ?: 1

val appVersionName = when {
    semVer == null -> "0.0.0-dev"
    isTaggedRelease -> "${semVer.first}.${semVer.second}.${semVer.third}"
    else -> gitDescribe.removePrefix("v")
}

val releaseVersionError = when {
    semVer == null ->
        "no v<MAJOR>.<MINOR>.<PATCH> tag is reachable from HEAD " +
            "(git describe: '${gitDescribe.ifEmpty { "unavailable" }}')"
    !isTaggedRelease -> "HEAD is not a clean release tag (git describe: '$gitDescribe')"
    else -> null
}

// Fails before any task runs, instead of after a full release build.
gradle.taskGraph.whenReady {
    val buildsReleaseArtifact = allTasks.any {
        it.name == "assembleRelease" || it.name == "bundleRelease"
    }
    if (buildsReleaseArtifact) {
        require(releaseVersionError == null) {
            "Refusing to build a release artifact: $releaseVersionError. " +
                "Tag the commit first, e.g. git tag -a v1.0.0 -m \"KnotSSH 1.0.0\"."
        }
    }
}

android {
    namespace = "com.knotssh"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.knotssh"
        minSdk = 31
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    /**
     * Release signing is driven by four Gradle properties. Keep them out of the repository:
     * put them in ~/.gradle/gradle.properties locally, or pass them as -P flags / environment
     * variables (ORG_GRADLE_PROJECT_KNOTSSH_STORE_FILE, ...) from CI.
     *
     *   KNOTSSH_STORE_FILE=/absolute/path/to/knotssh-release.jks
     *   KNOTSSH_STORE_PASSWORD=...
     *   KNOTSSH_KEY_ALIAS=knotssh
     *   KNOTSSH_KEY_PASSWORD=...
     *
     * When they are missing the release build still runs and produces an unsigned APK, so a
     * plain `assembleRelease` never silently falls back to the shared debug key.
     */
    val releaseKeystore = providers.gradleProperty("KNOTSSH_STORE_FILE").orNull?.let(::file)

    signingConfigs {
        if (releaseKeystore?.exists() == true) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = providers.gradleProperty("KNOTSSH_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("KNOTSSH_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("KNOTSSH_KEY_PASSWORD").get()
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Core Android
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.appcompat)
    implementation("com.google.android.material:material:1.12.0")
    implementation(libs.activity.compose)

    // Navigation
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Security
    implementation(libs.biometric)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // SSH
    implementation(libs.jsch)

    // Argon2id, used only to derive the backup encryption key from a password
    implementation(libs.argon2)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test.junit4)
}
