plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Imzo: KEYSTORE_PATH muhit o'zgaruvchisi berilsa, o'sha kalit ishlatiladi,
// aks holda debug kaliti bilan imzolanadi (o'z qurilmangizga o'rnatish uchun yetarli).
val hasReleaseKey = !System.getenv("KEYSTORE_PATH").isNullOrBlank()

android {
    namespace = "uz.vohid.download"
    compileSdk = 35

    defaultConfig {
        applicationId = "uz.vohid.download"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.0"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(System.getenv("KEYSTORE_PATH"))
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // yt-dlp/Jackson reflection ishlatadi, shuning uchun R8 o'chirilgan.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // Har bir arxitektura uchun alohida (kichik) APK + universal APK.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
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
        // youtubedl-android native kutubxonalarni (python, ffmpeg) diskka chiqarib ishlatadi.
        jniLibs.useLegacyPackaging = true
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("io.coil-kt:coil-compose:2.7.0")

    // yt-dlp + FFmpeg (Seal ilovasi ishlatadigan kutubxona)
    implementation("io.github.junkfood02.youtubedl-android:library:0.17.3")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.17.3")
}
