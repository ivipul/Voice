plugins {
  id("voice.library")
  alias(libs.plugins.metro)
  alias(libs.plugins.kotlin.serialization)
}

dependencies {
  api(projects.core.ai)
  api(projects.core.transcript)
  implementation(projects.core.data.api)
  implementation(projects.core.logging.api)
  implementation(libs.serialization.json)
  implementation(libs.coroutines.core)
  implementation(libs.datastore)
  implementation(libs.work.runtime)
  implementation(projects.core.strings)
  implementation(projects.core.common)
  implementation(projects.core.scanner)
  implementation(libs.androidxCore)

  testImplementation(libs.junit)
  testImplementation(libs.kotlin.testJunit)
  testImplementation(libs.coroutines.test)
}
