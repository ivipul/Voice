package voice.core.companion

import kotlinx.serialization.Serializable

/**
 * Who or what is in the scene, through the whole book: characters for fiction, topics for non-fiction. Built by
 * [SceneIndexer] from the transcript. All times are positions in the whole book, in milliseconds.
 */
@Serializable
data class SceneIndex(
  val kind: BookKind = BookKind.Fiction,
  val entries: List<SceneEntry> = emptyList(),
  val cues: List<SceneCue> = emptyList(),
)

@Serializable
data class SceneEntry(
  val id: String,
  val name: String,
  /** One line on who or what this is, as first introduced. */
  val summary: String = "",
  /** What the book has said about it so far, growing through the book; only notes already heard are shown. */
  val notes: List<SceneNote> = emptyList(),
  /** Topics: the shelf Extra this topic unlocks, if one was built for it. */
  val extraId: String? = null,
)

@Serializable
data class SceneNote(
  val fromMs: Long,
  val text: String,
)

@Serializable
data class SceneCue(
  val entry: String,
  val startMs: Long,
  val endMs: Long,
)

/** The entries whose cue range holds [positionMs], in the order the index lists them. */
fun SceneIndex.activeAt(positionMs: Long): List<SceneEntry> {
  val ids = cues.filter { positionMs in it.startMs..it.endMs }.mapTo(mutableSetOf()) { it.entry }
  return entries.filter { it.id in ids }
}

/** The first moment [entryId] is in the scene, or null if it never is. */
fun SceneIndex.firstCueMs(entryId: String): Long? = cues.filter { it.entry == entryId }.minOfOrNull { it.startMs }

/** What the listener knows about [entry] at [positionMs]: its summary plus every note already reached. */
fun SceneEntry.knownAt(positionMs: Long): List<String> = notes.filter { it.fromMs <= positionMs }.map { it.text }
