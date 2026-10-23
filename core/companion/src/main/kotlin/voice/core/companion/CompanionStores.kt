package voice.core.companion

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import voice.core.transcript.BookFiles

/** Everything Earwig keeps per book, each as its own JSON file in the book's folder. */
@SingleIn(AppScope::class)
@Inject
class CompanionStores(bookFiles: BookFiles) {

  val profile = BookJsonStore(bookFiles::bookDir, "profile.json", BookProfile.serializer()) { BookProfile() }
  val setup = BookJsonStore(bookFiles::bookDir, "setup.json", BookSetup.serializer()) { BookSetup() }
  val ideas = BookJsonStore(bookFiles::bookDir, "ideas.json", IdeaBoard.serializer()) { IdeaBoard() }
  val shelf = BookJsonStore(bookFiles::bookDir, "extras.json", ExtraShelf.serializer()) { ExtraShelf() }
  val look = BookJsonStore(bookFiles::bookDir, "look.json", BookLook.serializer()) { BookLook() }
  val scene = BookJsonStore(bookFiles::bookDir, "scene.json", SceneIndex.serializer()) { SceneIndex() }
  val materials = BookJsonStore(bookFiles::bookDir, "materials.json", BookMaterials.serializer()) { BookMaterials() }
  val summaries = BookJsonStore(bookFiles::bookDir, "summaries.json", ChapterSummaries.serializer()) { ChapterSummaries() }
}
