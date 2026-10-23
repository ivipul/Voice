package voice.core.companion.work

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import voice.core.companion.CompanionStores
import voice.core.companion.ExtensionPoint
import voice.core.companion.IdeaCard
import voice.core.companion.IdeaStatus
import voice.core.companion.StepState
import voice.core.companion.extras.ExtraBuilder
import voice.core.companion.materials.MaterialStudy
import voice.core.companion.scene.SceneIndexer
import voice.core.companion.setup.BookResearcher
import voice.core.companion.setup.BookTranscriber
import voice.core.companion.setup.ChapterSummarizer
import voice.core.companion.setup.IdeaGenerator
import voice.core.data.BookId
import voice.core.logging.api.Logger
import androidx.work.Constraints as WorkConstraints

/** The background jobs of a book's setup; each runs in [CompanionWorker] so it outlives the screen that started it. */
enum class SetupJob {
  Transcribe,
  Research,
  Ideas,
  Build,
  Scene,
  Summaries,
  Materials,
}

/**
 * Runs a book's guided setup: transcription and research start together, idea cards follow the research, and each
 * card the listener builds becomes its own job. Everything reports into the book's setup state, which the setup
 * screen shows.
 */
@SingleIn(AppScope::class)
@Inject
class BookSetupRunner(
  private val context: Context,
  private val stores: CompanionStores,
  private val transcriber: BookTranscriber,
  private val researcher: BookResearcher,
  private val ideas: IdeaGenerator,
  private val builder: ExtraBuilder,
  private val sceneIndexer: SceneIndexer,
  private val summarizer: ChapterSummarizer,
  private val materials: MaterialStudy,
) {

  /** Starts whatever part of the setup hasn't finished yet. Safe to call again; running jobs keep running. */
  suspend fun start(bookId: BookId) {
    val setup = stores.setup.get(bookId)
    if (setup.transcription.state != StepState.Done) {
      enqueue(SetupJob.Transcribe, bookId)
    } else if (setup.summaries != StepState.Done) {
      enqueue(SetupJob.Summaries, bookId)
    }
    when {
      setup.research != StepState.Done -> enqueue(SetupJob.Research, bookId)
      setup.ideas != StepState.Done -> enqueue(SetupJob.Ideas, bookId)
    }
    if (setup.research == StepState.Done && setup.materials != StepState.Done && stores.materials.get(bookId).files.isNotEmpty()) {
      enqueue(SetupJob.Materials, bookId)
    }
  }

  fun retry(bookId: BookId, job: SetupJob) {
    enqueue(job, bookId, policy = ExistingWorkPolicy.REPLACE)
  }

  /** More suggestions, on top of the cards the listener already built or skipped. */
  fun moreIdeas(bookId: BookId) {
    enqueue(SetupJob.Ideas, bookId, policy = ExistingWorkPolicy.REPLACE)
  }

  suspend fun build(bookId: BookId, cardId: String) {
    updateCard(bookId, cardId) { it.copy(status = IdeaStatus.Building, error = null) }
    enqueue(SetupJob.Build, bookId, cardId = cardId)
  }

  suspend fun skip(bookId: BookId, cardId: String) {
    updateCard(bookId, cardId) { it.copy(status = IdeaStatus.Skipped) }
  }

  suspend fun toggleFeature(bookId: BookId, cardId: String, index: Int) {
    updateCard(bookId, cardId) { card ->
      card.copy(
        features = card.features.mapIndexed { i, feature ->
          if (i == index) feature.copy(included = !feature.included) else feature
        },
      )
    }
  }

  suspend fun restore(bookId: BookId, cardId: String) {
    updateCard(bookId, cardId) { it.copy(status = IdeaStatus.Suggested, error = null) }
  }

  /** What [CompanionWorker] runs. Throws to let the worker report a failure; the setup state already says why. */
  internal suspend fun run(job: SetupJob, bookId: BookId, cardId: String?) {
    when (job) {
      SetupJob.Transcribe -> {
        transcriber.transcribe(bookId)
        afterTranscription(bookId)
      }
      SetupJob.Research -> {
        researcher.research(bookId)
        // Ideas draw on the PDFs, so they are read first; a PDF that can't be read still leaves ideas to suggest.
        val _ = runCatching { materials.run(bookId) }
        ideas.suggest(bookId)
      }
      SetupJob.Ideas -> ideas.suggest(bookId)
      SetupJob.Scene -> sceneIndexer.index(bookId)
      SetupJob.Summaries -> summarizer.summarize(bookId)
      SetupJob.Materials -> materials.run(bookId)
      SetupJob.Build -> buildCard(bookId, checkNotNull(cardId) { "Build needs a card" })
    }
  }

  private suspend fun buildCard(bookId: BookId, cardId: String) {
    val card = stores.ideas.get(bookId).cards.firstOrNull { it.id == cardId } ?: return
    try {
      when (card.point) {
        ExtensionPoint.SnipPicture -> {
          builder.buildSnipStyle(bookId, card)
          // Only one snip style is used at a time, so an older one stops counting as built.
          stores.ideas.update(bookId) { board ->
            board.copy(
              cards = board.cards.map {
                if (it.point == ExtensionPoint.SnipPicture && it.id != cardId && it.status == IdeaStatus.Built) {
                  it.copy(status = IdeaStatus.Suggested)
                } else {
                  it
                }
              },
            )
          }
          updateCard(bookId, cardId) { it.copy(status = IdeaStatus.Built) }
        }
        ExtensionPoint.Shelf -> {
          val extra = builder.buildShelfExtra(bookId, card)
          updateCard(bookId, cardId) { it.copy(status = IdeaStatus.Built, extraId = extra.id) }
        }
        ExtensionPoint.SceneChips -> {
          // The scan needs the whole transcript; until then the card waits and transcription picks it up.
          if (stores.setup.get(bookId).transcription.state == StepState.Done) {
            sceneIndexer.index(bookId)
            updateCard(bookId, cardId) { it.copy(status = IdeaStatus.Built) }
          }
        }
      }
    } catch (e: Exception) {
      Logger.w(e, "Building ${card.title} failed")
      updateCard(bookId, cardId) { it.copy(status = IdeaStatus.Failed, error = e.message ?: e.javaClass.simpleName) }
      throw e
    }
  }

  private suspend fun afterTranscription(bookId: BookId) {
    enqueue(SetupJob.Summaries, bookId, policy = ExistingWorkPolicy.REPLACE)
    if (stores.materials.get(bookId).files.isNotEmpty()) enqueue(SetupJob.Materials, bookId, policy = ExistingWorkPolicy.REPLACE)
    stores.ideas.get(bookId).cards
      .filter { it.point == ExtensionPoint.SceneChips && it.status == IdeaStatus.Building }
      .forEach { enqueue(SetupJob.Build, bookId, cardId = it.id) }
  }

  private suspend fun updateCard(bookId: BookId, cardId: String, transform: (IdeaCard) -> IdeaCard) {
    stores.ideas.update(bookId) { board -> board.copy(cards = board.cards.map { if (it.id == cardId) transform(it) else it }) }
  }

  private fun enqueue(
    job: SetupJob,
    bookId: BookId,
    cardId: String? = null,
    policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
  ) {
    val input: Data = workDataOf(
      CompanionWorker.KEY_JOB to job.name,
      CompanionWorker.KEY_BOOK to bookId.value,
      CompanionWorker.KEY_CARD to cardId,
    )
    val request = OneTimeWorkRequestBuilder<CompanionWorker>()
      .setInputData(input)
      .setConstraints(WorkConstraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
      .addTag(CompanionWorker.TAG)
      .build()
    val name = listOfNotNull("earwig", job.name, bookId.value, cardId).joinToString(":")
    val _ = WorkManager.getInstance(context).enqueueUniqueWork(name, policy, request)
  }
}
