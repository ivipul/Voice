plugins {
  id("voice.library")
  alias(libs.plugins.metro)
  alias(libs.plugins.kotlin.serialization)
}

android {
  androidResources {
    enable = true
  }

  buildFeatures {
    buildConfig = true
  }

  // Bluetooth voice round-trip spike only (Phase 4 groundwork): set gemini.apiKey in
  // ~/.gradle/gradle.properties (outside the repo, never committed) to test the
  // mic -> Gemini -> TTS pipeline on-device.
  defaultConfig {
    buildConfigField(
      type = "String",
      name = "GEMINI_API_KEY",
      value = "\"${providers.gradleProperty("gemini.apiKey").getOrElse("")}\"",
    )
  }
}

dependencies {
  implementation(projects.core.common)
  implementation(projects.core.strings)
  implementation(projects.core.featureflag)
  implementation(projects.core.sleeptimer.api)
  implementation(projects.core.data.api)
  implementation(projects.core.analytics.api)

  implementation(libs.androidxCore)
  implementation(libs.coil)
  implementation(libs.coroutines.guava)
  implementation(libs.serialization.json)
  implementation(libs.okhttp)

  implementation(libs.media3.exoplayer)
  implementation(libs.media3.session)

  testImplementation(libs.bundles.testing.jvm)
  testImplementation(libs.media3.testUtils.core)
  testImplementation(libs.media3.testUtils.robolectric)
}
