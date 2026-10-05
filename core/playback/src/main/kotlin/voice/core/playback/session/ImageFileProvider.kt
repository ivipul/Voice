package voice.core.playback.session

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import voice.core.ui.crawl.CrawlEdition
import voice.core.ui.crawl.crawlEditionOf
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Inject
@SingleIn(AppScope::class)
class ImageFileProvider(private val application: Application) {

  private val uris = ConcurrentHashMap<File, Uri>()

  private val editionUris = ConcurrentHashMap<CrawlEdition, Uri>()

  internal fun uri(file: File): Uri = uris.computeIfAbsent(file) { grantedUri(file) }

  /** The Crawl's own cover for the book if it is a series book, else the book's embedded cover. */
  internal fun artwork(
    bookName: String,
    embeddedCover: File?,
  ): Uri? {
    val edition = crawlEditionOf(bookName)
    return if (edition != null) {
      editionUris.computeIfAbsent(edition) { grantedUri(editionCoverFile(it)) }
    } else {
      embeddedCover?.let(::uri)
    }
  }

  // Android Auto and the system UI load artwork through content uris, so the cover resource has to be a file.
  private fun editionCoverFile(edition: CrawlEdition): File {
    val file = File(application.filesDir, "crawl_covers/book${edition.number}.webp")
    file.parentFile?.mkdirs()
    application.resources.openRawResource(edition.cover).use { input ->
      file.outputStream().use { output -> input.copyTo(output) }
    }
    return file
  }

  private fun grantedUri(file: File): Uri {
    return FileProvider
      .getUriForFile(
        application,
        application.packageName + ".coverprovider",
        file,
      )
      .also { uri ->
        // These are necessary to grant the cover uri file permissions.
        // systemui is related to this one:
        // https://github.com/PaulWoitaschek/Voice/issues/1860
        //
        // The others are related to watch and car
        // https://github.com/android/uamp/blob/2136c37bcef54da1ee350fd642fc61a744e86654/common/src/main/res/xml/allowed_media_browser_callers.xml
        listOf(
          "com.android.systemui",
          "com.google.android.autosimulator",
          "com.google.android.carassistant",
          "com.google.android.googlequicksearchbox",
          "com.google.android.projection.gearhead",
          "com.google.android.wearable.app",
        ).forEach { grantedPackage ->
          application.grantUriPermission(
            grantedPackage,
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
        }
      }
  }
}
