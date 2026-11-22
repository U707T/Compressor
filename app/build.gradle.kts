import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.aboutlibraries.android)
}

// Kotlin compilation is provided by AGP's built-in Kotlin support (default since AGP 9.0);
// the separate org.jetbrains.kotlin.android plugin is no longer applied. The Compose
// compiler plugin is still required until AGP bundles it.

// Release signing is supplied by CI (or a local keystore.properties that is
// gitignored). Without it the release build stays unsigned instead of failing,
// so `assembleDebug` keeps working on a clean checkout.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(propertyKey: String, envKey: String): String? =
    keystoreProperties.getProperty(propertyKey)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envKey)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "KEY_PASSWORD")
val hasReleaseKeystore = releaseStoreFile != null &&
    file(releaseStoreFile).exists() &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

android {
    namespace = "compress.joshattic.us"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "compress.joshattic.us"
        minSdk = 24
        targetSdk = 37
        versionCode = 30
        versionName = "1.7.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // Built-in Kotlin derives the Kotlin jvmTarget from compileOptions, so the
    // deprecated kotlinOptions block is no longer needed.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    dependenciesInfo {
        includeInBundle = false
        includeInApk = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.aboutlibraries.core)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.ui)
    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:5.24.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.4.0")
    testImplementation("org.robolectric:robolectric:4.18.1")
    testImplementation("androidx.test:core:1.5.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}