import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes GITHUB_RUN_NUMBER so every build gets a higher version and installs as an update.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.nishanth.instamapper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nishanth.instamapper"
        minSdk = 26 // Android 8.0+, covers almost every phone in use
        targetSdk = 35
        versionCode = buildNumber + 1
        versionName = "1.1.$buildNumber"
        buildConfigField("String", "RELEASES_URL", "\"https://github.com/nishanthks373-git/Insta_mapper/releases/latest\"")
        buildConfigField("String", "LATEST_API", "\"https://api.github.com/repos/nishanthks373-git/Insta_mapper/releases/tags/latest\"")
    }

    signingConfigs {
        // A private key stored in GitHub Secrets is used when available (see README).
        // Otherwise the key committed in the repo is used, so builds always install as updates.
        create("shared") {
            val secretStore = System.getenv("SIGNING_STORE_FILE")
            if (!secretStore.isNullOrBlank() && file(secretStore).exists()) {
                storeFile = file(secretStore)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            } else {
                storeFile = file("instamapper.keystore")
                storePassword = "instamapper"
                keyAlias = "instamapper"
                keyPassword = "instamapper"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("shared")
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
