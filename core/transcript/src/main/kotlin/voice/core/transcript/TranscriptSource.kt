package voice.core.transcript

import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.nameWithoutExtension

/**
 * Finds the transcript JSON file for [audioFile] among this folder's children, matching on
 * basename (e.g. `Book 1.m4b` -> `Book 1.json`).
 */
fun CachedDocumentFile.transcriptFor(audioFile: CachedDocumentFile): CachedDocumentFile? {
  val targetName = audioFile.nameWithoutExtension()
  return children.firstOrNull {
    it.isFile && it.name?.endsWith(".json", ignoreCase = true) == true && it.nameWithoutExtension() == targetName
  }
}
