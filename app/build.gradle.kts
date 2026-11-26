import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
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
        versionCode = 16
        versionName = "2.8.0"

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
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "/META-INF/proguard/**",
                "/META-INF/maven/**",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
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
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    debugImplementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.datastore:datastore-preferences:1.1.3")
    implementation("androidx.autofill:autofill:1.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // Play Services dynamically delivered ML Kit OCR (~260 KB vs ~13.5 MB bundled native library)
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.llamatik:library-android:1.10.1")
    implementation("com.k2fsa.sherpa:sherpa-onnx-static-link-onnxruntime:2.0.3@aar")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.mlkit:genai-proofreading:1.0.0-beta1")
    implementation("com.google.mlkit:genai-rewriting:1.0.0-beta1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("org.json:json:20240303")
}
