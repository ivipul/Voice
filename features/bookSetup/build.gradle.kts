plugins {
  id("voice.library")
  id("voice.compose")
  alias(libs.plugins.metro)
}

dependencies {
  implementation(projects.core.common)
  implementation(projects.core.companion)
  implementation(projects.core.strings)
  implementation(projects.core.ui)
  implementation(projects.navigation)
  implementation(projects.core.data.api)
  implementation(projects.core.logging.api)
  implementation(libs.datastore)
  implementation(libs.coil)

  testImplementation(libs.molecule)
  testImplementation(libs.turbine)
}
