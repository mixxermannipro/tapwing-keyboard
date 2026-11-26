import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
}

fun loadExternalSigningProperties(): Properties {
    val props = Properties()
    val config = file("${System.getProperty("user.home")}/.tapwing-signing/keystore.properties")
    if (config.isFile) {
        config.inputStream().use { props.load(it) }
    }
    return props
}

val externalSigningProps = loadExternalSigningProperties()
val envKeystorePath = System.getenv("TAPWING_KEYSTORE_PATH")
    ?: externalSigningProps.getProperty("storeFile")
val envStorePassword = System.getenv("TAPWING_STORE_PASSWORD")
    ?: externalSigningProps.getProperty("storePassword")
val envKeyAlias = System.getenv("TAPWING_KEY_ALIAS")
    ?: externalSigningProps.getProperty("keyAlias")
val envKeyPassword = System.getenv("TAPWING_KEY_PASSWORD")
    ?: externalSigningProps.getProperty("keyPassword")

val hasReleaseSigning = !envKeystorePath.isNullOrBlank() &&
    file(envKeystorePath).isFile &&
    !envStorePassword.isNullOrBlank() &&
    !envKeyAlias.isNullOrBlank() &&
    !envKeyPassword.isNullOrBlank()

android {
    namespace = "com.tapwing.keyboard"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.tapwing.keyboard"
        minSdk = 29
        targetSdk = 36
        versionCode = 17
        versionName = "2.9.0"

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
            "ExportedActivity",
            "ExportedReceiver",
            "ExportedContentProvider",
            "WorldReadableFiles",
            "WorldWriteableFiles",
            "InsecureBaseConfiguration",
            "CleartextTrafficPermitted"
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
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
