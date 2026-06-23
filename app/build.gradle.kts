plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace   = "com.financeos.hub"
    compileSdk  = 34

    // CI injects FOS_BUILD_NUMBER (the GitHub Actions run number) so every release gets a
    // monotonically increasing versionCode and a unique versionName the in-app updater can
    // compare against the latest GitHub Release tag. Local builds fall back to 1 / "0.1.0".
    val buildNumber  = (System.getenv("FOS_BUILD_NUMBER") ?: "0").toIntOrNull() ?: 0
    val baseVersion  = "0.1.0"
    val resolvedName = if (buildNumber > 0) "$baseVersion.$buildNumber" else baseVersion

    defaultConfig {
        applicationId   = "com.financeos.hub"
        minSdk          = 26
        targetSdk       = 34
        versionCode     = if (buildNumber > 0) buildNumber else 1
        versionName     = resolvedName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Repository the in-app updater queries for new releases (GitHub Releases API).
        buildConfigField("String", "GITHUB_REPO", "\"hoxitoo/financeos-hub\"")

        ksp {
            arg("room.incremental", "true")
        }
    }

    signingConfigs {
        // A fixed, repo-committed debug keystore (password "android"). Every CI-built and
        // locally-built debug APK is therefore signed with the SAME key, so the in-app
        // updater can install a newer build over an older one without a signature mismatch.
        // This is a debug key only — it is intentionally not secret and never used for release.
        create("shared") {
            storeFile     = file("debug.keystore")
            storePassword = "android"
            keyAlias      = "androiddebugkey"
            keyPassword   = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable        = true
            signingConfig       = signingConfigs.getByName("shared")
            // Note: no applicationIdSuffix — the distributed debug build keeps the real
            // applicationId so in-app updates replace the same package the user installed.
            versionNameSuffix   = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    // Kotlin 1.9.x configures Compose via the compiler extension (the standalone
    // org.jetbrains.kotlin.plugin.compose only exists from Kotlin 2.0+).
    // Compose Compiler 1.5.14 is the version matched to Kotlin 1.9.24.
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        noCompress += "tflite"
    }

    lint {
        // Lint still runs and writes its HTML report (uploaded as a CI artifact),
        // but does not fail the build. The codebase is in active development;
        // remaining lint findings are tracked via the report, not as a hard gate.
        abortOnError = false
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.kotlinx.coroutines.android)

    // Compose BOM — manages all compose artifact versions
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Lifecycle + ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt DI
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // WorkManager + Hilt integration
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)

    // AppCompat (required for AppCompatActivity used by BiometricPrompt)
    implementation(libs.androidx.appcompat)

    // Biometric
    implementation(libs.biometric)

    // PDF text extraction — Apache PDFBox Android port (SAF-compatible, no permissions needed)
    implementation(libs.pdfbox.android)

    // TFLite — ML inference for merchant classification and spending prediction.
    // Only the core runtime (org.tensorflow.lite.Interpreter) is used; the support and
    // metadata artifacts are versioned independently (never published at 2.14.0) and are
    // not referenced anywhere, so they are intentionally not included.
    implementation(libs.tflite.core)

    // Debug
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
