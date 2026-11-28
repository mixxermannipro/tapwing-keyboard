import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
    alias(libs.plugins.play.publisher)
}

val versionProps = Properties().apply {
    val versionFile = rootProject.file("version.properties")
    if (versionFile.isFile) {
        versionFile.inputStream().use { load(it) }
    }
}
val appVersionCode = versionProps.getProperty("versionCode")?.trim()?.toIntOrNull() ?: 17
val appVersionName = versionProps.getProperty("versionName")?.trim()?.takeIf { it.isNotEmpty() } ?: "2.9.0"

val envKeystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
val envKeyAlias = System.getenv("SIGNING_KEY_ALIAS")
val envStorePassword = System.getenv("SIGNING_STORE_PASSWORD")
val envKeyPassword = System.getenv("SIGNING_KEY_PASSWORD")

val hasReleaseSigning = !envKeystorePath.isNullOrBlank() &&
    file(envKeystorePath).isFile &&
    !envKeyAlias.isNullOrBlank() &&
    !envStorePassword.isNullOrBlank() &&
    !envKeyPassword.isNullOrBlank()

val playServiceAccountPath = System.getenv("PLAY_SERVICE_ACCOUNT_JSON_PATH")
val hasPlayServiceAccount = !playServiceAccountPath.isNullOrBlank() &&
    file(playServiceAccountPath).isFile

android {
    namespace = "com.tapwing.keyboard"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.tapwing.keyboard"
        minSdk = 29
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    androidResources {
        // Keep only Tapwing's 9 supported keyboard/UI locales and strip 75+ unused AndroidX/GMS locales
        localeFilters += listOf("en", "de", "es", "fr", "it", "nl", "pl", "pt", "tr")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(envKeystorePath!!)
                storePassword = envStorePassword
                keyAlias = envKeyAlias
                keyPassword = envKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
                abiFilters += listOf("arm64-v8a")
            }
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/*.version",
                "/META-INF/proguard/**",
                "/META-INF/maven/**",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
            merges += setOf(
                "META-INF/NOTICE*",
                "META-INF/LICENSE*"
            )
        }
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        baseline = file("lint-baseline.xml")
        error += setOf(
            "HardcodedDebugMode",
            "SetJavaScriptEnabled",
            "AllowBackup",
            "TrustAllX509TrustManager",
            "BadHostnameVerifier",
            "ExportedService",
            "ExportedReceiver",
            "ExportedContentProvider",
            "WorldReadableFiles",
            "WorldWriteableFiles",
            "InsecureBaseConfiguration",
            "NetworkSecurityConfig"
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

play {
    enabled.set(hasPlayServiceAccount)
    if (hasPlayServiceAccount) {
        serviceAccountCredentials.set(file(playServiceAccountPath!!))
    }
    track.set(System.getenv("PLAY_TRACK")?.takeIf { it.isNotBlank() } ?: "internal")
    releaseStatus.set(ReleaseStatus.COMPLETED)
    defaultToAppBundles.set(true)
}

kover {
    reports {
        verify {
            rule {
                minBound(29)
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.savedstate.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.autofill)
    implementation(libs.kotlinx.coroutines.android)

    // Play Services dynamically delivered ML Kit OCR (~260 KB vs ~13.5 MB bundled native library)
    implementation(libs.play.services.mlkit.text.recognition)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.llamatik.android)
    implementation(libs.sherpa.onnx) {
        artifact {
            type = "aar"
        }
    }
    implementation(libs.commons.compress)
    implementation(libs.okhttp)
    implementation(libs.mlkit.genai.proofreading)
    implementation(libs.mlkit.genai.rewriting)
    implementation(libs.kotlinx.coroutines.guava)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
