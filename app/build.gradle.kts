plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

// Never hard-code a real key. Provide it through the environment or a Gradle
// property at build time, or enter it in the app's Settings screen.
val geminiApiKey = (project.findProperty("GEMINI_API_KEY") as String?)
    ?: System.getenv("GEMINI_API_KEY")
    ?: ""

// Optional certificate pin for creator master control. Leave blank for debug
// builds; set it to the release certificate's SHA-256 for a pinned build.
val signingCertSha256 = (project.findProperty("ZED_SIGNING_CERT_SHA256") as String?)
    ?: System.getenv("ZED_SIGNING_CERT_SHA256")
    ?: ""

// Release signing is opt-in and never committed. Maintainers provide these
// through the local shell, Gradle user properties, or CI secrets.
val releaseStorePath = (project.findProperty("ZED_RELEASE_STORE_FILE") as String?)
    ?: System.getenv("ZED_RELEASE_STORE_FILE")
val releaseStorePassword = (project.findProperty("ZED_RELEASE_STORE_PASSWORD") as String?)
    ?: System.getenv("ZED_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = (project.findProperty("ZED_RELEASE_KEY_ALIAS") as String?)
    ?: System.getenv("ZED_RELEASE_KEY_ALIAS")
val releaseKeyPassword = (project.findProperty("ZED_RELEASE_KEY_PASSWORD") as String?)
    ?: System.getenv("ZED_RELEASE_KEY_PASSWORD")

// Release CI opts into per-ABI APKs so ARMv8 phones can download a smaller
// native payload than the universal build. Local and debug builds stay universal.
val abiSplitsEnabled = (project.findProperty("zedAbiSplits") as String?) == "true"

android {
  namespace = "com.zed.assistant"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.zed.assistant"
    minSdk = 26
    targetSdk = 36
    versionCode = 21
    versionName = "2.1.0"

    buildConfigField(
      "String",
      "GEMINI_API_KEY",
      "\"${geminiApiKey.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    )
    buildConfigField(
      "String",
      "EXPECTED_SIGNATURE_SHA256",
      "\"${signingCertSha256.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    )
  }

  signingConfigs {
    if (releaseStorePath != null && releaseStorePassword != null &&
      releaseKeyAlias != null && releaseKeyPassword != null
    ) {
      create("release") {
        storeFile = file(releaseStorePath)
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
  }

  buildTypes {
    release {
      signingConfig = signingConfigs.findByName("release")
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

  splits {
    abi {
      isEnable = abiSplitsEnabled
      reset()
      include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
      isUniversalApk = true
    }
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.okhttp)
  implementation(libs.kotlinx.coroutines.android)

  // Fully offline wake-word and owner-voice verification. The model assets are
  // unpacked into app-private storage on first use.
  implementation("com.alphacephei:vosk-android:0.3.75@aar")
  implementation("net.java.dev.jna:jna:5.13.0@aar")

  testImplementation(libs.junit)

  debugImplementation(libs.androidx.compose.ui.tooling)
}
