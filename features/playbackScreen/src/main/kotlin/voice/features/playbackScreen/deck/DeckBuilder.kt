package voice.features.playbackScreen.deck

import voice.core.xray.XRayManifest
import voice.core.xray.card.PlayerCardData
import voice.core.xray.card.PlayerCardFields

/** One book's side of The Deck: how far the listener is in it and what its X-Ray and card files say. */
internal data class DeckBook(
  val key: String,
  val number: Int,
  val positionMs: Long,
  val manifest: XRayManifest?,
  val cards: Collection<PlayerCardData>,
)

sealed interface DeckSlot {
  val id: String

  /** A crawler the listener has met: [data] is the card of the latest book they reached, shown at [positionMs]. */
  data class Unlocked(
    override val id: String,
    val name: String,
    val description: String,
    val portrait: String?,
    val data: PlayerCardData,
    val bookKey: String,
    val positionMs: Long,
    val imageUris: Map<String, String> = emptyMap(),
  ) : DeckSlot

  /** A crawler still ahead: nothing but the book where they first show up. */
  data class Locked(
    override val id: String,
    val firstBook: Int,
  ) : DeckSlot
}

/**
 * Every crawler with a card in any of [books], once, in the order they first show up. A crawler is met in a book
 * when the listener's position there has reached the first moment they are on screen (their first X-Ray cue, and
 * their first look when the card has one), so a card never shows a look from further on than the listener has heard.
 * A met crawler shows the card of the latest book they are met in; one met nowhere is a [DeckSlot.Locked] slot.
 */
internal fun buildDeck(books: List<DeckBook>): List<DeckSlot> {
  val ordered = books.sortedBy(DeckBook::number)
  val ids = ordered.flatMap { book -> book.cards.map(PlayerCardData::id) }.distinct()
  return ids
    .map { id -> id to ordered.filter { book -> book.cards.any { it.id == id } } }
    .sortedWith(
      compareBy(
        { (_, appearsIn) -> appearsIn.first().number },
        { (id, appearsIn) -> appearsIn.first().firstSeenMs(id) },
      ),
    )
    .map { (id, appearsIn) ->
      val metIn = appearsIn.filter { it.hasMet(id) }
      val latest = metIn.lastOrNull()
      if (latest == null) {
        DeckSlot.Locked(id = id, firstBook = appearsIn.first().number)
      } else {
        val entity = metIn.reversed().firstNotNullOfOrNull { book -> book.manifest?.entities?.firstOrNull { it.id == id } }
        DeckSlot.Unlocked(
          id = id,
          name = entity?.title ?: id.replace('_', ' ').replace('-', ' ').replaceFirstChar(Char::uppercase),
          description = entity?.description.orEmpty(),
          portrait = entity?.image,
          data = latest.cards.first { it.id == id },
          bookKey = latest.key,
          positionMs = latest.positionMs,
        )
      }
    }
}

private fun DeckBook.hasMet(id: String): Boolean = positionMs > 0 && positionMs >= firstSeenMs(id)

private fun DeckBook.firstSeenMs(id: String): Long {
  val cue = manifest?.timeline?.filter { it.entity == id }?.minOfOrNull { it.startMs } ?: 0L
  val look = cards.firstOrNull { it.id == id }
    ?.entries
    ?.filter { it.field == PlayerCardFields.LOOK && it.image != null }
    ?.minOfOrNull { it.ms }
    ?: 0L
  return maxOf(cue, look)
}
