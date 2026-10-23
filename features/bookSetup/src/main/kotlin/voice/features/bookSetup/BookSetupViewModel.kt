package voice.features.bookSetup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.net.Uri
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import voice.core.companion.CompanionStores
import voice.core.companion.IdeaCard
import voice.core.companion.LookSource
import voice.core.companion.StepState
import voice.core.companion.look.LookMaker
import voice.core.companion.setup.IdeaGenerator
import voice.core.companion.setup.TranscriptImporter
import voice.core.companion.work.BookSetupRunner
import voice.core.companion.work.SetupJob
import voice.core.data.ApiKeys
import voice.core.data.BookId
import voice.core.data.toUri
import voice.core.data.repo.BookRepository
import voice.core.data.store.ApiKeysStore
import voice.core.logging.api.Logger
import voice.core.ui.crawl.toStyle
import voice.navigation.Destination
import voice.navigation.Navigator

/**
 * The guided setup of one book: transcription and research run in the background while the screen shows how far
 * they got, then Claude's idea cards wait for Build or Skip. The listener can pitch an idea of their own and pick
 * the book's colors here too.
 */
@AssistedInject
class BookSetupViewModel(
  private val repo: BookRepository,
  private val stores: CompanionStores,
  private val runner: BookSetupRunner,
  private val ideaGenerator: IdeaGenerator,
  private val lookMaker: LookMaker,
  private val transcriptImporter: TranscriptImporter,
  private val navigator: Navigator,
  @ApiKeysStore
  private val keysStore: DataStore<ApiKeys>,
  @Assisted
  private val bookId: BookId,
  @Assisted
  private val autoStart: Boolean,
) {

  private val scope = MainScope()
  private var ownIdea by mutableStateOf(OwnIdeaViewState())
  private var lookWorking by mutableStateOf(false)
  private var lookError by mutableStateOf<String?>(null)
  private var autoStarted = false

  @Composable
  fun viewState(): BookSetupViewState? {
    val bookState by remember(bookId) { repo.flow(bookId) }.collectAsState(initial = null)
    val setupState by remember(bookId) { stores.setup.flow(bookId) }.collectAsState(initial = null)
    val profile by remember(bookId) { stores.profile.flow(bookId) }.collectAsState(initial = null)
    val board by remember(bookId) { stores.ideas.flow(bookId) }.collectAsState(initial = null)
    val lookState by remember(bookId) { stores.look.flow(bookId) }.collectAsState(initial = null)
    val keysState by remember { keysStore.data }.collectAsState(initial = null)
    val book = bookState ?: return null
    val setup = setupState ?: return null
    val look = lookState ?: return null
    val keys = keysState ?: return null

    val missingKeys = buildList {
      if (keys.claude.isBlank()) add(MissingKey.Claude)
      if (keys.deepgram.isBlank() && setup.transcription.state != StepState.Done) add(MissingKey.Deepgram)
    }
    val notStarted = setup.transcription.state == StepState.NotStarted && setup.research == StepState.NotStarted
    LaunchedEffect(notStarted, missingKeys) {
      if (autoStart && !autoStarted && notStarted && missingKeys.isEmpty()) {
        autoStarted = true
        runner.start(bookId)
      }
    }
    val cards = board?.cards.orEmpty()
    return BookSetupViewState(
      title = book.content.name,
      author = book.content.author,
      cover = book.content.cover?.absolutePath,
      bookStyle = look.toStyle(),
      missingKeys = missingKeys,
      canDrawCovers = keys.ideogram.isNotBlank() && keys.claude.isNotBlank(),
      notStarted = notStarted,
      transcription = setup.transcription.toViewState(book.duration),
      research = StepViewState(setup.research, setup.researchError),
      profile = profile?.takeIf { setup.research == StepState.Done },
      ideas = StepViewState(setup.ideas, setup.ideasError),
      cards = cards.activeCards(),
      deck = cards.ideaDeck(),
      skippedCards = cards.skippedCards(),
      ownIdea = ownIdea,
      look = LookViewState(
        source = look.source,
        extraPrompt = look.extraCoverPrompt,
        displayFont = look.displayFont,
        working = lookWorking,
        error = lookError,
        hasCover = book.content.cover != null,
      ),
    )
  }

  fun onStart() {
    scope.launch { runner.start(bookId) }
  }

  fun onRetryTranscription() {
    runner.retry(bookId, SetupJob.Transcribe)
  }

  /** A transcript (`.json`, `.srt`) or PDFs added after the book: a transcript stops cloud transcription. */
  fun onFilesAdded(uris: List<Uri>) {
    if (uris.isEmpty()) return
    scope.launch {
      val book = repo.get(bookId) ?: return@launch
      val singleFile = book.chapters.singleOrNull()?.id?.toUri()
      val added = transcriptImporter.addTo(bookId, singleFile, uris)
      if (added.transcripts.isNotEmpty() && stores.setup.get(bookId).transcription.state == StepState.Done) {
        runner.cancelTranscription(bookId)
      }
      runner.start(bookId)
    }
  }

  fun onRetryResearch() {
    runner.retry(bookId, SetupJob.Research)
  }

  fun onMoreIdeas() {
    runner.moreIdeas(bookId)
  }

  fun onBuild(card: IdeaCard) {
    scope.launch { runner.build(bookId, card.id) }
  }

  fun onSkip(card: IdeaCard) {
    scope.launch { runner.skip(bookId, card.id) }
  }

  fun onToggleFeature(card: IdeaCard, index: Int) {
    scope.launch { runner.toggleFeature(bookId, card.id, index) }
  }

  fun onRestore(card: IdeaCard) {
    scope.launch { runner.restore(bookId, card.id) }
  }

  fun onOpen(card: IdeaCard) {
    val extraId = card.extraId
    if (extraId != null) {
      navigator.goTo(Destination.Extra(bookId, extraId))
    } else {
      navigator.goTo(Destination.Playback(bookId))
    }
  }

  fun onOwnIdeaChange(text: String) {
    ownIdea = ownIdea.copy(text = text, verdict = null, error = null)
  }

  fun onOwnIdeaSubmit() {
    val idea = ownIdea.text.trim()
    if (idea.isEmpty() || ownIdea.checking) return
    ownIdea = ownIdea.copy(checking = true, verdict = null, error = null)
    scope.launch {
      ownIdea = try {
        val verdict = ideaGenerator.check(bookId, idea)
        OwnIdeaViewState(text = if (verdict.feasible) "" else idea, verdict = verdict)
      } catch (e: Exception) {
        Logger.w(e, "Checking the idea failed")
        ownIdea.copy(checking = false, error = e.message ?: e.javaClass.simpleName)
      }
    }
  }

  /** Puts the suggested alternative in the idea field, so one tap on Check sends it. */
  fun onUseAlternative() {
    val alternative = ownIdea.verdict?.alternative?.takeIf { it.isNotBlank() } ?: return
    ownIdea = OwnIdeaViewState(text = alternative)
  }

  fun onLookSourceClick(source: LookSource) {
    runLookJob {
      when (source) {
        LookSource.Default -> lookMaker.useDefault(bookId)
        LookSource.Cover -> check(lookMaker.useCoverColors(bookId)) { "This book has no cover to take colors from" }
        LookSource.Generated -> lookMaker.generateCover(bookId, stores.look.get(bookId).extraCoverPrompt)
      }
    }
  }

  fun onGenerateCover(extraPrompt: String) {
    runLookJob { lookMaker.generateCover(bookId, extraPrompt.trim()) }
  }

  fun onDisplayFontClick(font: String) {
    scope.launch { stores.look.update(bookId) { it.copy(displayFont = font) } }
  }

  fun onOpenSettings() {
    navigator.goTo(Destination.CoPilotSettings)
  }

  fun onListen() {
    navigator.goTo(Destination.Playback(bookId))
  }

  fun onClose() {
    scope.launch { stores.setup.update(bookId) { it.copy(dismissed = true) } }
    navigator.goBack()
  }

  private fun runLookJob(job: suspend () -> Unit) {
    if (lookWorking) return
    lookWorking = true
    lookError = null
    scope.launch {
      try {
        job()
      } catch (e: Exception) {
        Logger.w(e, "Changing the look failed")
        lookError = e.message ?: e.javaClass.simpleName
      } finally {
        lookWorking = false
      }
    }
  }

  @AssistedFactory
  interface Factory {
    fun create(
      bookId: BookId,
      autoStart: Boolean,
    ): BookSetupViewModel
  }
}
