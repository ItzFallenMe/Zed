plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

// Never hard-code a real key. Provide it through the environment or a Gradle
// property at build time, or enter it in the app's Settings screen.
val geminiApiKey = (project.findProperty("GEMINI_API_KEY") as String?)
    ?: System.getenv("GEMINI_API_KEY")
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

android {
  namespace = "com.zed.assistant"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.zed.assistant"
    minSdk = 26
    targetSdk = 36
    versionCode = 2
    versionName = "1.1.0"

    buildConfigField(
      "String",
      "GEMINI_API_KEY",
      "\"${geminiApiKey.replace("\\", "\\\\").replace("\"", "\\\"")}\""
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

  testImplementation(libs.junit)

  debugImplementation(libs.androidx.compose.ui.tooling)
}
