package voice.features.bookSetup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import voice.core.companion.ExtensionPoint
import voice.core.companion.IdeaCard
import voice.core.companion.IdeaStatus
import voice.core.companion.StepState
import voice.core.ui.crawl.CrawlPalette
import voice.core.ui.crawl.CrawlType
import voice.core.strings.R as StringsR

@Composable
internal fun IdeasSection(
  viewState: BookSetupViewState,
  palette: CrawlPalette,
  viewModel: BookSetupViewModel,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(text = stringResource(StringsR.string.setup_ideas_title), style = MaterialTheme.typography.titleLarge)
    Text(text = stringResource(StringsR.string.setup_ideas_body), style = MaterialTheme.typography.bodyMedium)
    when (viewState.ideas.state) {
      StepState.NotStarted -> if (viewState.research.state != StepState.Failed) {
        Working(stringResource(StringsR.string.setup_ideas_waiting))
      }
      StepState.Running -> Working(stringResource(StringsR.string.setup_ideas_running))
      StepState.Failed -> {
        ErrorText(stringResource(StringsR.string.setup_ideas_failed, viewState.ideas.error.orEmpty()))
        OutlinedButton(onClick = viewModel::onMoreIdeas) { Text(text = stringResource(StringsR.string.setup_retry)) }
      }
      StepState.Done -> Unit
    }
    viewState.cards.forEach { card ->
      IdeaCardView(
        card = card,
        palette = palette,
        transcriptReady = viewState.transcription.state == StepState.Done,
        onBuild = { viewModel.onBuild(card) },
        onSkip = { viewModel.onSkip(card) },
        onOpen = { viewModel.onOpen(card) },
      )
    }
    if (viewState.ideas.state == StepState.Done) {
      OutlinedButton(onClick = viewModel::onMoreIdeas, modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(StringsR.string.setup_ideas_more))
      }
    }
    if (viewState.skippedCards.isNotEmpty()) {
      SkippedCards(cards = viewState.skippedCards, onRestore = viewModel::onRestore)
    }
    OwnIdeaCard(
      state = viewState.ownIdea,
      palette = palette,
      enabled = viewState.research.state == StepState.Done,
      onChange = viewModel::onOwnIdeaChange,
      onSubmit = viewModel::onOwnIdeaSubmit,
      onUseAlternative = viewModel::onUseAlternative,
    )
  }
}

private val ExtensionPoint.labelRes: Int
  get() = when (this) {
    ExtensionPoint.SnipPicture -> StringsR.string.setup_point_snip_picture
    ExtensionPoint.Shelf -> StringsR.string.setup_point_shelf
    ExtensionPoint.SceneChips -> StringsR.string.setup_point_scene_chips
  }

@Composable
private fun IdeaCardView(
  card: IdeaCard,
  palette: CrawlPalette,
  transcriptReady: Boolean,
  onBuild: () -> Unit,
  onSkip: () -> Unit,
  onOpen: () -> Unit,
) {
  SetupCard(palette) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      SectionLabel(stringResource(card.point.labelRes), palette)
      if (card.kind.isNotBlank()) Tag(card.kind, palette)
      if (card.fromListener) Tag(stringResource(StringsR.string.setup_idea_yours), palette)
    }
    Text(text = card.title, style = CrawlType.button.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize))
    Text(text = card.description, style = MaterialTheme.typography.bodyMedium)
    if (card.why.isNotBlank() && !card.fromListener) {
      Text(text = card.why, style = MaterialTheme.typography.bodySmall, color = palette.content.copy(alpha = 0.75F))
    }
    when (card.status) {
      IdeaStatus.Suggested -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onBuild) { Text(text = stringResource(StringsR.string.setup_idea_build)) }
        TextButton(onClick = onSkip) { Text(text = stringResource(StringsR.string.setup_idea_skip)) }
      }
      IdeaStatus.Building -> Working(
        stringResource(
          if (card.point == ExtensionPoint.SceneChips && !transcriptReady) {
            StringsR.string.setup_idea_waiting_for_transcript
          } else {
            StringsR.string.setup_idea_building
          },
        ),
      )
      IdeaStatus.Built -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = stringResource(StringsR.string.setup_idea_built), style = CrawlType.label, color = palette.highlight)
        if (card.point == ExtensionPoint.Shelf) {
          Button(onClick = onOpen) { Text(text = stringResource(StringsR.string.setup_idea_open)) }
        }
      }
      IdeaStatus.Failed -> {
        ErrorText(stringResource(StringsR.string.setup_idea_failed, card.error.orEmpty()))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(onClick = onBuild) { Text(text = stringResource(StringsR.string.setup_retry)) }
          TextButton(onClick = onSkip) { Text(text = stringResource(StringsR.string.setup_idea_skip)) }
        }
      }
      IdeaStatus.Skipped -> Unit
    }
  }
}

@Composable
private fun SkippedCards(
  cards: List<IdeaCard>,
  onRestore: (IdeaCard) -> Unit,
) {
  var open by rememberSaveable { mutableStateOf(false) }
  TextButton(onClick = { open = !open }) {
    Text(text = stringResource(StringsR.string.setup_ideas_skipped, cards.size))
  }
  if (open) {
    cards.forEach { card ->
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text = card.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1F))
        TextButton(onClick = { onRestore(card) }) { Text(text = stringResource(StringsR.string.setup_idea_restore)) }
      }
    }
  }
}

@Composable
private fun OwnIdeaCard(
  state: OwnIdeaViewState,
  palette: CrawlPalette,
  enabled: Boolean,
  onChange: (String) -> Unit,
  onSubmit: () -> Unit,
  onUseAlternative: () -> Unit,
) {
  SetupCard(palette) {
    SectionLabel(stringResource(StringsR.string.setup_own_label), palette)
    Text(text = stringResource(StringsR.string.setup_own_body), style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(
      value = state.text,
      onValueChange = onChange,
      enabled = enabled && !state.checking,
      placeholder = { Text(text = stringResource(StringsR.string.setup_own_placeholder)) },
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
      keyboardActions = KeyboardActions(onSend = { onSubmit() }),
      modifier = Modifier.fillMaxWidth(),
    )
    if (state.checking) {
      Working(stringResource(StringsR.string.setup_own_checking))
    } else {
      Button(onClick = onSubmit, enabled = enabled && state.text.isNotBlank()) {
        Text(text = stringResource(StringsR.string.setup_own_check))
      }
    }
    state.error?.let { ErrorText(stringResource(StringsR.string.setup_own_failed, it)) }
    state.verdict?.let { verdict ->
      if (verdict.feasible) {
        Text(
          text = stringResource(StringsR.string.setup_own_added, verdict.title),
          style = MaterialTheme.typography.bodyMedium,
          color = palette.highlight,
        )
      } else {
        Text(text = verdict.reason, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start)
        if (verdict.alternative.isNotBlank()) {
          Text(
            text = stringResource(StringsR.string.setup_own_alternative, verdict.alternative),
            style = MaterialTheme.typography.bodySmall,
          )
          TextButton(onClick = onUseAlternative) { Text(text = stringResource(StringsR.string.setup_own_use_alternative)) }
        }
      }
    }
  }
}
