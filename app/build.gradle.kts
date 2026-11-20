import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun loadExternalSigningProperties(): Properties {
    val props = Properties()
    val candidates = listOf(
        file("/home/manuelblasaditsch/.tapwing-signing/keystore.properties"),
        file("${System.getProperty("user.home")}/.tapwing-signing/keystore.properties")
    )
    val existing = candidates.firstOrNull { it.isFile }
    if (existing != null) {
        existing.inputStream().use { props.load(it) }
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
        versionCode = 5
        versionName = "1.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.datastore:datastore-preferences:1.1.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("com.google.android.material:material:1.12.0")

    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("org.json:json:20240303")
}
