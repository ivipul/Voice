plugins {
  id("voice.library")
  alias(libs.plugins.metro)
  alias(libs.plugins.kotlin.serialization)
}

dependencies {
  implementation(projects.core.common)
  implementation(projects.core.data.api)
  // api, not implementation: Metro needs the @Inject constructors of these modules visible on :app's compile
  // classpath to resolve RealCoPilotPipeline's dependency graph.
  api(projects.core.transcript)
  api(projects.core.ai)
  api(projects.core.companion)
  implementation(projects.core.logging.api)

  implementation(libs.serialization.json)
  implementation(libs.okhttp)

  testImplementation(libs.bundles.testing.jvm)
}
