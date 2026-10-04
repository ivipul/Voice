package voice.core.xray

import voice.core.documentfile.CachedDocumentFile

/** The folder of art that sits next to a `.cards.json`; it holds hundreds of images and never any manifests. */
internal fun CachedDocumentFile.isCardArtFolder(): Boolean = isDirectory && name?.endsWith(CARD_ART_SUFFIX) == true

private const val CARD_ART_SUFFIX = ".cards"

/**
 * Every file under this folder, one folder at a time, so the neighbours of a manifest are listed before any
 * subfolder is opened. Card art folders are listed but not opened: each folder opened is a slow query to the
 * storage provider, and the art folders hold most of a library's folders.
 */
internal fun CachedDocumentFile.breadthFirst(): Sequence<CachedDocumentFile> = sequence {
  val folders = ArrayDeque<CachedDocumentFile>()
  folders.add(this@breadthFirst)
  while (folders.isNotEmpty()) {
    for (child in folders.removeFirst().children) {
      yield(child)
      if (child.isDirectory && !child.isCardArtFolder()) folders.add(child)
    }
  }
}
