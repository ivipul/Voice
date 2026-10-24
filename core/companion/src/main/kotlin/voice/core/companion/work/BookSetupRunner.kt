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
import voice.core.companion.IdeaFeature
import voice.core.companion.IdeaStatus
import voice.core.companion.StepState
import voice.core.companion.LookSource
import voice.core.companion.extras.ExtraBuilder
import voice.core.companion.look.LookMaker
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
  Change,
  Cover,
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
  private val lookMaker: LookMaker,
) {

  /** Starts whatever part of the setup hasn't finished yet. Safe to call again; running jobs keep running. */
  suspend fun start(bookId: BookId) {
    val setup = stores.setup.get(bookId)
    // A new book starts in its cover's colors, so the player and the cover read as one surface.
    if (setup.research == StepState.NotStarted && stores.look.get(bookId).source == LookSource.Default) {
      val _ = runCatching { lookMaker.useCoverColors(bookId) }
    }
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

  /** Once the book is researched, draws a first cover in the background so the New cover option is ready. */
  suspend fun prepareCover(bookId: BookId) {
    if (stores.setup.get(bookId).research != StepState.Done || stores.look.get(bookId).coverHistory.isNotEmpty()) return
    enqueue(SetupJob.Cover, bookId)
  }

  fun retry(bookId: BookId, job: SetupJob) {
    enqueue(job, bookId, policy = ExistingWorkPolicy.REPLACE)
  }

  /** More suggestions, on top of the cards the listener already built or skipped. */
  fun moreIdeas(bookId: BookId) {
    enqueue(SetupJob.Ideas, bookId, policy = ExistingWorkPolicy.REPLACE)
  }

  /** Stops a cloud transcription, when a transcript file was added after all. */
  fun cancelTranscription(bookId: BookId) {
    val _ = WorkManager.getInstance(context).cancelUniqueWork(workName(SetupJob.Transcribe, bookId, cardId = null))
  }

  /** Stops every setup job of [bookId], running or waiting. */
  fun cancelAll(bookId: BookId) {
    val workManager = WorkManager.getInstance(context)
    val _ = workManager.cancelAllWorkByTag(bookTag(bookId))
    // Jobs queued before the book tag existed only have their names.
    SetupJob.entries.forEach { job -> val _ = workManager.cancelUniqueWork(workName(job, bookId, cardId = null)) }
  }

  suspend fun build(bookId: BookId, cardId: String, note: String? = null) {
    updateCard(bookId, cardId) { card ->
      card.copy(status = IdeaStatus.Building, error = null, listenerNote = note?.trim() ?: card.listenerNote)
    }
    enqueue(SetupJob.Build, bookId, cardId = cardId)
  }

  /**
   * Changes a built idea: [features] become its parts and [request] says what to change. A shelf Extra keeps its
   * page and gets edited; a snip picture style is drawn again with the request as the listener's note.
   */
  suspend fun change(bookId: BookId, cardId: String, features: List<IdeaFeature>, request: String) {
    val card = stores.ideas.get(bookId).cards.firstOrNull { it.id == cardId } ?: return
    if (card.point == ExtensionPoint.Shelf && card.extraId != null) {
      updateCard(bookId, cardId) { it.copy(status = IdeaStatus.Building, error = null, features = features, pendingChange = request) }
      enqueue(SetupJob.Change, bookId, cardId = cardId, policy = ExistingWorkPolicy.REPLACE)
    } else {
      updateCard(bookId, cardId) { it.copy(features = features) }
      build(bookId, cardId, note = request)
    }
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
        prepareCover(bookId)
      }
      SetupJob.Ideas -> ideas.suggest(bookId)
      SetupJob.Cover -> lookMaker.prepareCover(bookId)
      SetupJob.Scene -> sceneIndexer.index(bookId)
      SetupJob.Summaries -> summarizer.summarize(bookId)
      SetupJob.Materials -> {
        val studiedNew = materials.run(bookId)
        // A PDF added after the ideas were suggested gets ideas of its own.
        if (studiedNew && stores.setup.get(bookId).ideas == StepState.Done) ideas.suggest(bookId)
      }
      SetupJob.Build -> buildCard(bookId, checkNotNull(cardId) { "Build needs a card" })
      SetupJob.Change -> changeCard(bookId, checkNotNull(cardId) { "Change needs a card" })
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
          // The PDF chips come from the same kind of transcript scan, so a retry here also retries a failed one.
          if (stores.setup.get(bookId).materials == StepState.Failed) {
            enqueue(SetupJob.Materials, bookId, policy = ExistingWorkPolicy.REPLACE)
          }
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

  private suspend fun changeCard(bookId: BookId, cardId: String) {
    val card = stores.ideas.get(bookId).cards.firstOrNull { it.id == cardId } ?: return
    val extraId = card.extraId ?: return
    try {
      val error = stores.shelf.get(bookId).extras.firstOrNull { it.id == extraId }?.lastError
      builder.change(bookId, extraId, card.pendingChange, error)
      updateCard(bookId, cardId) { it.copy(status = IdeaStatus.Built, pendingChange = "") }
    } catch (e: Exception) {
      Logger.w(e, "Changing ${card.title} failed")
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
      .addTag(bookTag(bookId))
      .build()
    val _ = WorkManager.getInstance(context).enqueueUniqueWork(workName(job, bookId, cardId), policy, request)
  }

  private fun bookTag(bookId: BookId) = "earwig:book:${bookId.value}"

  private fun workName(job: SetupJob, bookId: BookId, cardId: String?) =
    listOfNotNull("earwig", job.name, bookId.value, cardId).joinToString(":")
}
