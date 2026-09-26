plugins {
  id("voice.library")
  alias(libs.plugins.kotlin.serialization)
}

dependencies {
  implementation(projects.core.documentfile)
  implementation(libs.serialization.json)

  testImplementation(libs.junit)
  testImplementation(libs.kotlin.testJunit)
}
