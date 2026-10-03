package voice.features.playbackScreen.view.holocard

/**
 * X-Ray entity images are bundled into the app as assets (see `:core:xray`'s `assets/xray/`),
 * referenced by a path relative to that folder. A full `http(s)` URL is still accepted as a
 * fallback for an entity whose image hasn't been bundled yet.
 */
internal fun String.toImageModel(): String = if (startsWith("http://") || startsWith("https://")) {
  this
} else {
  "file:///android_asset/xray/$this"
}
