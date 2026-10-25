plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.zed.assistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.zed.assistant"
        minSdk = 26
        targetSdk = 36
        versionCode = System.getenv("ZED_VERSION_CODE")?.toIntOrNull() ?: 3
        versionName = System.getenv("ZED_VERSION_NAME") ?: "1.1.1"
        resourceConfigurations += listOf("en", "hi")
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            val ks = file("../keystore/zed-release.jks")
            if (ks.exists()) {
                storeFile = ks
                // CI provides these as secrets; blank secrets must not override the demo key.
                storePassword = System.getenv("ZED_STORE_PASSWORD")?.takeIf { it.isNotBlank() }
                    ?: "zedassistant"
                keyAlias = System.getenv("ZED_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "zed"
                keyPassword = System.getenv("ZED_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
                    ?: "zedassistant"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            // Real phones are ARM; shipping x86 keeps the APK ~50 MB larger for no benefit.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
            if (file("../keystore/zed-release.jks").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*"
        )
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-service:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
    implementation("com.google.mlkit:translate:17.0.3")

    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")

    testImplementation("junit:junit:4.13.2")
}
