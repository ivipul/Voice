package voice.features.bookSetup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavEntry
import coil.compose.AsyncImage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.common.rootGraphAs
import voice.core.companion.BookKind
import voice.core.companion.BookProfile
import voice.core.companion.StepState
import voice.core.data.BookId
import voice.core.ui.ResetBookDialog
import voice.core.ui.crawl.BookStyle
import voice.core.ui.crawl.CrawlPalette
import voice.core.ui.crawl.CrawlTheme
import voice.core.ui.crawl.CrawlType
import voice.core.ui.crawl.DefaultPalette
import voice.core.ui.crawl.hardShadow
import voice.core.ui.icons.VoiceIcons
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import java.io.File
import java.util.Locale
import voice.core.strings.R as StringsR

@ContributesTo(AppScope::class)
interface BookSetupGraph {
  val bookSetupViewModelFactory: BookSetupViewModel.Factory
}

@ContributesTo(AppScope::class)
interface BookSetupProvider {

  @Provides
  @IntoSet
  fun bookSetupNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.BookSetup> { key ->
    NavEntry(key) {
      BookSetupScreen(bookId = key.bookId, autoStart = key.autoStart)
    }
  }
}

@Composable
private fun BookSetupScreen(
  bookId: BookId,
  autoStart: Boolean,
) {
  val viewModel = retain(bookId.value) {
    rootGraphAs<BookSetupGraph>().bookSetupViewModelFactory.create(bookId, autoStart)
  }
  val viewState = viewModel.viewState()
  if (viewState == null) {
    AddingBook(onClose = viewModel::onClose)
    return
  }
  CrawlTheme(bookStyle = viewState.bookStyle) {
    BookSetupScreen(viewState = viewState, viewModel = viewModel)
  }
}

/** Shown while a freshly picked file is still being scanned into the library. */
@Composable
private fun AddingBook(onClose: () -> Unit) {
  CrawlTheme(bookStyle = BookStyle()) {
    val palette = DefaultPalette
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(palette.background)
        .safeDrawingPadding(),
    ) {
      IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart)) {
        Icon(imageVector = VoiceIcons.Close, contentDescription = stringResource(StringsR.string.common_action_close), tint = palette.content)
      }
      Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        CircularProgressIndicator(color = palette.highlight)
        Text(text = stringResource(StringsR.string.setup_adding), color = palette.content, style = MaterialTheme.typography.bodyLarge)
      }
    }
  }
}

internal val CardShape = RoundedCornerShape(20.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookSetupScreen(
  viewState: BookSetupViewState,
  viewModel: BookSetupViewModel,
) {
  val palette = viewState.bookStyle.palette
  Scaffold(
    containerColor = palette.background,
    contentColor = palette.content,
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(StringsR.string.setup_title)) },
        navigationIcon = {
          IconButton(onClick = viewModel::onClose) {
            Icon(imageVector = VoiceIcons.Close, contentDescription = stringResource(StringsR.string.common_action_close))
          }
        },
        actions = {
          if (!viewState.notStarted) {
            TextButton(onClick = viewModel::onResetClick) {
              Text(text = stringResource(StringsR.string.book_reset_setup), color = palette.content)
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = palette.background,
          titleContentColor = palette.content,
          navigationIconContentColor = palette.content,
        ),
      )
    },
  ) { contentPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp)
        .navigationBarsPadding(),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      BookHeader(viewState)
      if (viewState.missingKeys.isNotEmpty()) {
        MissingKeysCard(missing = viewState.missingKeys, palette = palette, onOpenSettings = viewModel::onOpenSettings)
      }
      if (viewState.notStarted) {
        StartCard(
          transcription = viewState.transcription,
          enabled = viewState.missingKeys.isEmpty(),
          palette = palette,
          onStart = viewModel::onStart,
        )
      } else {
        TranscriptionCard(
          transcription = viewState.transcription,
          palette = palette,
          onRetry = viewModel::onRetryTranscription,
          onFilesAdded = viewModel::onFilesAdded,
        )
        ResearchCard(viewState.research, viewState.profile, palette, onRetry = viewModel::onRetryResearch)
        IdeasSection(
          viewState = viewState,
          palette = palette,
          viewModel = viewModel,
        )
      }
      OwnIdeaCard(
        state = viewState.ownIdea,
        palette = palette,
        enabled = MissingKey.Claude !in viewState.missingKeys,
        onChange = viewModel::onOwnIdeaChange,
        onSubmit = viewModel::onOwnIdeaSubmit,
        onUseAlternative = viewModel::onUseAlternative,
      )
      LookSection(
        look = viewState.look,
        canDrawCovers = viewState.canDrawCovers,
        palette = palette,
        onSourceClick = viewModel::onLookSourceClick,
        onGenerate = viewModel::onGenerateCover,
        onCoverClick = viewModel::onGeneratedCoverClick,
        onFontClick = viewModel::onDisplayFontClick,
      )
      Button(
        onClick = viewModel::onListen,
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 8.dp),
      ) {
        Text(text = stringResource(StringsR.string.setup_listen), style = CrawlType.button)
      }
      Spacer(modifier = Modifier.size(16.dp))
    }
  }
  viewState.reset?.let { reset ->
    ResetBookDialog(
      removeFromLibrary = false,
      keepFiles = reset.keepFiles,
      onDismiss = viewModel::onResetDismiss,
      onConfirm = viewModel::onResetConfirm,
      onKeepFilesChange = viewModel::onResetKeepFilesChange,
    )
  }
  viewState.edit?.let { edit ->
    EditIdeaDialog(
      state = edit,
      onToggleFeature = viewModel::onEditToggleFeature,
      onRequestChange = viewModel::onEditRequestChange,
      onDismiss = viewModel::onEditDismiss,
      onSend = viewModel::onEditSend,
    )
  }
}

@Composable
private fun BookHeader(viewState: BookSetupViewState) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    if (viewState.cover != null) {
      AsyncImage(
        model = File(viewState.cover),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .size(88.dp)
          .clip(RoundedCornerShape(12.dp)),
      )
      Spacer(modifier = Modifier.width(16.dp))
    }
    Column {
      val type = viewState.bookStyle.chapterType
      Text(
        text = if (type.uppercase) viewState.title.uppercase() else viewState.title,
        style = TextStyle(
          fontFamily = type.fontFamily,
          fontWeight = type.fontWeight,
          fontSize = minOf(type.fontSize.value, 30F).sp,
          lineHeight = minOf(type.fontSize.value, 30F).sp * 1.05,
        ),
      )
      viewState.author?.let {
        Text(text = it, style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}

/** A light card in the book's colors with the hard shadow the player uses. */
@Composable
internal fun SetupCard(
  palette: CrawlPalette,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .hardShadow(color = palette.shadow, shape = CardShape, x = 4.dp, y = 4.dp)
      .clip(CardShape)
      .background(palette.background)
      .border(width = 2.dp, color = palette.content, shape = CardShape)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    content = content,
  )
}

@Composable
internal fun SectionLabel(
  text: String,
  palette: CrawlPalette,
) {
  Text(
    text = text.uppercase(),
    style = CrawlType.label,
    color = palette.onHighlight,
    modifier = Modifier
      .background(color = palette.highlight, shape = CircleShape)
      .padding(horizontal = 10.dp, vertical = 5.dp),
  )
}

@Composable
internal fun ErrorText(text: String) {
  Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
internal fun Working(text: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
    Spacer(modifier = Modifier.width(10.dp))
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun MissingKeysCard(
  missing: List<MissingKey>,
  palette: CrawlPalette,
  onOpenSettings: () -> Unit,
) {
  SetupCard(palette) {
    SectionLabel(stringResource(StringsR.string.setup_keys_label), palette)
    missing.forEach { key ->
      Text(
        text = stringResource(
          when (key) {
            MissingKey.Claude -> StringsR.string.setup_keys_claude
            MissingKey.Deepgram -> StringsR.string.setup_keys_deepgram
          },
        ),
        style = MaterialTheme.typography.bodyMedium,
      )
    }
    Button(onClick = onOpenSettings) {
      Text(text = stringResource(StringsR.string.setup_keys_open_settings))
    }
  }
}

@Composable
private fun StartCard(
  transcription: TranscriptionViewState,
  enabled: Boolean,
  palette: CrawlPalette,
  onStart: () -> Unit,
) {
  SetupCard(palette) {
    SectionLabel(stringResource(StringsR.string.setup_start_label), palette)
    Text(text = stringResource(StringsR.string.setup_start_body), style = MaterialTheme.typography.bodyMedium)
    if (transcription.state != StepState.Done) {
      Text(
        text = stringResource(
          StringsR.string.setup_start_cost,
          formatHours(transcription.durationMinutes),
          formatUsd(transcription.estimatedCostUsd),
        ),
        style = MaterialTheme.typography.bodySmall,
      )
    }
    Button(onClick = onStart, enabled = enabled) {
      Text(text = stringResource(StringsR.string.setup_start_button))
    }
  }
}

@Composable
private fun TranscriptionCard(
  transcription: TranscriptionViewState,
  palette: CrawlPalette,
  onRetry: () -> Unit,
  onFilesAdded: (List<Uri>) -> Unit,
) {
  val addFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), onFilesAdded)
  SetupCard(palette) {
    SectionLabel(stringResource(StringsR.string.setup_transcript_label), palette)
    when (transcription.state) {
      StepState.NotStarted -> Working(stringResource(StringsR.string.setup_transcript_waiting))
      StepState.Running -> {
        LinearProgressIndicator(
          progress = { transcription.fraction },
          color = palette.highlight,
          trackColor = palette.shadow,
          modifier = Modifier.fillMaxWidth(),
        )
        Text(
          text = if (transcription.partsTotal == 0) {
            stringResource(StringsR.string.setup_transcript_preparing)
          } else {
            stringResource(StringsR.string.setup_transcript_progress, transcription.partsDone, transcription.partsTotal)
          },
          style = MaterialTheme.typography.bodyMedium,
        )
        Text(text = stringResource(StringsR.string.setup_transcript_partial_hint), style = MaterialTheme.typography.bodySmall)
      }
      StepState.Done -> Text(text = stringResource(StringsR.string.setup_transcript_done), style = MaterialTheme.typography.bodyMedium)
      StepState.Failed -> {
        ErrorText(stringResource(StringsR.string.setup_transcript_failed, transcription.error.orEmpty()))
        Text(text = stringResource(StringsR.string.setup_transcript_resume_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onRetry) { Text(text = stringResource(StringsR.string.setup_retry)) }
      }
    }
    TextButton(onClick = { addFiles.launch(ADDED_FILE_TYPES) }) {
      Text(text = stringResource(StringsR.string.setup_add_files))
    }
  }
}

/** Transcripts (`.json`, `.srt`) and PDFs that came with the book. */
private val ADDED_FILE_TYPES = arrayOf("application/json", "application/pdf", "application/x-subrip", "text/*", "application/octet-stream")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResearchCard(
  research: StepViewState,
  profile: BookProfile?,
  palette: CrawlPalette,
  onRetry: () -> Unit,
) {
  SetupCard(palette) {
    SectionLabel(stringResource(StringsR.string.setup_research_label), palette)
    when (research.state) {
      StepState.NotStarted, StepState.Running -> Working(stringResource(StringsR.string.setup_research_running))
      StepState.Failed -> {
        ErrorText(stringResource(StringsR.string.setup_research_failed, research.error.orEmpty()))
        OutlinedButton(onClick = onRetry) { Text(text = stringResource(StringsR.string.setup_retry)) }
      }
      StepState.Done -> if (profile != null) {
        Text(
          text = listOf(
            stringResource(if (profile.kind == BookKind.Fiction) StringsR.string.setup_research_fiction else StringsR.string.setup_research_nonfiction),
            profile.genre,
          ).filter { it.isNotBlank() }.joinToString(" · "),
          style = MaterialTheme.typography.titleSmall,
        )
        if (profile.summary.isNotBlank()) {
          Text(text = profile.summary, style = MaterialTheme.typography.bodyMedium)
        }
        val tags = if (profile.kind == BookKind.Fiction) profile.themes else profile.keyTopics.ifEmpty { profile.themes }
        if (tags.isNotEmpty()) {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tags.take(8).forEach { Tag(it, palette) }
          }
        }
      }
    }
  }
}

@Composable
internal fun Tag(
  text: String,
  palette: CrawlPalette,
) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelMedium,
    color = palette.content,
    modifier = Modifier
      .border(width = 1.dp, color = palette.content.copy(alpha = 0.5F), shape = CircleShape)
      .padding(horizontal = 10.dp, vertical = 4.dp),
  )
}

internal fun formatHours(minutes: Int): String = if (minutes < 90) {
  "$minutes min"
} else {
  String.format(Locale.US, "%.1f h", minutes / 60.0)
}

internal fun formatUsd(amount: Double): String = String.format(Locale.US, "$%.2f", amount)
