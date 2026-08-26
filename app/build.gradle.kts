import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "dz.winrah.trainradar"
  compileSdk = 36

  fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

  val configuredApiBaseUrl = providers.gradleProperty("WINRAH_API_BASE_URL").orElse(
    System.getenv("WINRAH_API_BASE_URL") ?: "https://train-api-uep7.onrender.com/"
  ).get().trim().let { if (it.endsWith("/")) it else "$it/" }
  val configuredApiEnvironment = providers.gradleProperty("WINRAH_API_ENVIRONMENT").orElse(
    System.getenv("WINRAH_API_ENVIRONMENT") ?: "production"
  ).get().trim().lowercase()
  val configuredApiWritesEnabled = providers.gradleProperty("WINRAH_API_WRITES_ENABLED").orElse(
    System.getenv("WINRAH_API_WRITES_ENABLED") ?: "false"
  ).get().toBooleanStrictOrNull() ?: false
  require(configuredApiBaseUrl.isNotBlank() && !configuredApiBaseUrl.contains("\n")) {
    "WINRAH_API_BASE_URL must be a non-empty single-line URL"
  }
  require(configuredApiEnvironment in setOf("local", "staging", "production")) {
    "WINRAH_API_ENVIRONMENT must be local, staging, or production"
  }

  val versionCodeFromEnv = System.getenv("VERSION_CODE")?.toIntOrNull()
  val versionNameFromEnv = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }
  val releaseKeystorePath = System.getenv("KEYSTORE_PATH")
    ?.takeIf { it.isNotBlank() }
    ?: "${rootDir}/my-upload-key.jks"
  val releaseKeystoreFile = file(releaseKeystorePath)
  val releaseStorePassword = System.getenv("STORE_PASSWORD")
  val releaseKeyAlias = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "upload"
  val releaseKeyPassword = System.getenv("KEY_PASSWORD")

  defaultConfig {
    applicationId = "dz.winrah.trainradar"
    minSdk = 24
    targetSdk = 36
    versionCode = versionCodeFromEnv ?: 1
    versionName = versionNameFromEnv ?: "1.0"
    buildConfigField("String", "GEMINI_API_KEY", "\"AIzaSyPlaceholder\"")
    buildConfigField("String", "WINRAH_API_BASE_URL", buildConfigString(configuredApiBaseUrl))
    buildConfigField("String", "WINRAH_API_ENVIRONMENT", buildConfigString(configuredApiEnvironment))
    buildConfigField("boolean", "WINRAH_API_WRITES_ENABLED", configuredApiWritesEnabled.toString())
    manifestPlaceholders["usesCleartextTraffic"] = configuredApiEnvironment == "local"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      storeFile = releaseKeystoreFile
      storePassword = releaseStorePassword
      keyAlias = releaseKeyAlias
      keyPassword = releaseKeyPassword
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
