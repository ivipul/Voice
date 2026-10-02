plugins {
  id("voice.library")
  alias(libs.plugins.metro)
  alias(libs.plugins.kotlin.serialization)
}

android {
  buildFeatures {
    buildConfig = true
  }

  // Set gemini.apiKey / openrouter.apiKey / ideogram.apiKey in ~/.gradle/gradle.properties
  // (outside the repo, never committed) to enable the co-pilot's Gemini and Jev router calls
  // and the Snip comic-frame image generation.
  defaultConfig {
    buildConfigField(
      type = "String",
      name = "GEMINI_API_KEY",
      value = "\"${providers.gradleProperty("gemini.apiKey").getOrElse("")}\"",
    )
    buildConfigField(
      type = "String",
      name = "OPENROUTER_API_KEY",
      value = "\"${providers.gradleProperty("openrouter.apiKey").getOrElse("")}\"",
    )
    buildConfigField(
      type = "String",
      name = "IDEOGRAM_API_KEY",
      value = "\"${providers.gradleProperty("ideogram.apiKey").getOrElse("")}\"",
    )
  }
}

dependencies {
  implementation(projects.core.common)
  implementation(projects.core.data.api)
  // api, not implementation: Metro needs TranscriptRepository's @Inject constructor visible
  // on :app's compile classpath to resolve RealCoPilotPipeline's dependency graph.
  api(projects.core.transcript)
  implementation(projects.core.logging.api)

  implementation(libs.serialization.json)
  implementation(libs.okhttp)

  testImplementation(libs.bundles.testing.jvm)
}
