plugins {
  id("voice.library")
  alias(libs.plugins.metro)
  alias(libs.plugins.kotlin.serialization)
}

dependencies {
  implementation(projects.core.documentfile)
  implementation(projects.core.data.api)
  implementation(projects.core.logging.api)
  implementation(libs.serialization.json)
  implementation(libs.coroutines.core)

  testImplementation(libs.junit)
  testImplementation(libs.kotlin.testJunit)
  testImplementation(libs.coroutines.test)
  testImplementation(libs.mockk)
}
