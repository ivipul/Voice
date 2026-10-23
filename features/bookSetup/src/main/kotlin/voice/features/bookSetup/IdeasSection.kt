package voice.features.bookSetup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import voice.core.companion.ExtensionPoint
import voice.core.companion.IdeaCard
import voice.core.companion.IdeaFeature
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
    IdeaDeck(
      deck = viewState.deck,
      palette = palette,
      onBuild = { card, note -> viewModel.onBuild(card, note) },
      onSkip = viewModel::onSkip,
      onToggleFeature = viewModel::onToggleFeature,
    )
    if (viewState.deck.current == null && viewState.ideas.state == StepState.Done) {
      Text(text = stringResource(StringsR.string.setup_ideas_all_decided), style = MaterialTheme.typography.bodyMedium)
      OutlinedButton(onClick = viewModel::onMoreIdeas, modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(StringsR.string.setup_ideas_more))
      }
    }
    val chosen = viewState.cards.filter { it.status != IdeaStatus.Suggested }
    if (chosen.isNotEmpty()) {
      Text(
        text = stringResource(StringsR.string.setup_ideas_decided).uppercase(),
        style = CrawlType.label,
        color = palette.content.copy(alpha = 0.6F),
      )
      chosen.forEach { card ->
        ChosenIdeaRow(
          card = card,
          palette = palette,
          transcriptReady = viewState.transcription.state == StepState.Done,
          onRetry = { viewModel.onBuild(card) },
          onSkip = { viewModel.onSkip(card) },
          onOpen = { viewModel.onOpen(card) },
        )
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

/** One card on top of a small stack: a glance at the idea, its parts to untick, and Skip or Build. */
@Composable
private fun IdeaDeck(
  deck: IdeaDeckViewState,
  palette: CrawlPalette,
  onBuild: (IdeaCard, String) -> Unit,
  onSkip: (IdeaCard) -> Unit,
  onToggleFeature: (IdeaCard, Int) -> Unit,
) {
  val current = deck.current ?: return
  Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp)) {
    if (deck.total - deck.position > 2) StackedCard(palette, rotation = 3.5F)
    if (deck.total - deck.position > 1) StackedCard(palette, rotation = -2.5F)
    AnimatedContent(
      targetState = current,
      contentKey = { it.id },
      transitionSpec = {
        (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
      },
      label = "ideaDeck",
    ) { card ->
      IdeaDeckCard(
        card = card,
        number = deck.position + 1,
        total = deck.total,
        palette = palette,
        onBuild = { note -> onBuild(card, note) },
        onSkip = { onSkip(card) },
        onToggleFeature = { onToggleFeature(card, it) },
      )
    }
  }
}

@Composable
private fun BoxScope.StackedCard(
  palette: CrawlPalette,
  rotation: Float,
) {
  Box(
    modifier = Modifier
      .matchParentSize()
      .graphicsLayer { rotationZ = rotation }
      .background(palette.background, CardShape)
      .border(width = 2.dp, color = palette.content.copy(alpha = 0.35F), shape = CardShape),
  )
}

@Composable
private fun IdeaDeckCard(
  card: IdeaCard,
  number: Int,
  total: Int,
  palette: CrawlPalette,
  onBuild: (note: String) -> Unit,
  onSkip: () -> Unit,
  onToggleFeature: (Int) -> Unit,
) {
  var note by rememberSaveable(card.id) { mutableStateOf(card.listenerNote) }
  SetupCard(palette) {
    DeckProgress(done = number, total = total, palette = palette)
    Spacer(modifier = Modifier.height(4.dp))
    val label = if (card.fromListener) {
      stringResource(StringsR.string.setup_idea_yours)
    } else {
      stringResource(StringsR.string.setup_idea_number, number, total)
    }
    val source = if (card.materialSectionId != null) " · ${stringResource(StringsR.string.setup_idea_from_pdf).uppercase()}" else ""
    Text(
      text = "${label.uppercase()} · ${stringResource(card.point.labelRes).uppercase()}$source",
      style = CrawlType.label,
      color = palette.content.copy(alpha = 0.6F),
    )
    Text(
      text = card.title,
      style = CrawlType.button.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
    )
    if (card.description.isNotBlank()) {
      Text(
        text = card.description,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
    if (card.features.isNotEmpty()) {
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = stringResource(StringsR.string.setup_idea_includes).uppercase(),
        style = CrawlType.label,
        color = palette.content.copy(alpha = 0.6F),
      )
      card.features.forEachIndexed { index, feature ->
        FeatureRow(feature = feature, palette = palette, onToggle = { onToggleFeature(index) })
      }
    }
    OutlinedTextField(
      value = note,
      onValueChange = { note = it },
      placeholder = { Text(text = stringResource(StringsR.string.setup_idea_note_placeholder)) },
      textStyle = MaterialTheme.typography.bodyMedium,
      maxLines = 4,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TextButton(onClick = onSkip) {
        Text(text = stringResource(StringsR.string.setup_idea_skip), color = palette.content.copy(alpha = 0.7F))
      }
      Button(
        onClick = { onBuild(note) },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = palette.content, contentColor = palette.background),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
      ) {
        Text(text = stringResource(StringsR.string.setup_idea_build))
      }
    }
  }
}

@Composable
private fun DeckProgress(
  done: Int,
  total: Int,
  palette: CrawlPalette,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    repeat(total) { index ->
      Box(
        modifier = Modifier
          .weight(1F)
          .height(4.dp)
          .background(
            color = if (index < done) palette.highlight else palette.content.copy(alpha = 0.12F),
            shape = CircleShape,
          ),
      )
    }
  }
}

@Composable
private fun FeatureRow(
  feature: IdeaFeature,
  palette: CrawlPalette,
  onToggle: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .toggleable(value = feature.included, role = Role.Checkbox, onValueChange = { onToggle() })
      .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CheckDot(checked = feature.included, palette = palette)
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = feature.label,
      style = MaterialTheme.typography.bodyLarge,
      color = if (feature.included) palette.content else palette.content.copy(alpha = 0.55F),
    )
  }
}

@Composable
private fun CheckDot(
  checked: Boolean,
  palette: CrawlPalette,
) {
  val modifier = Modifier.size(24.dp).clip(CircleShape)
  if (checked) {
    Canvas(modifier = modifier.background(palette.highlight)) {
      val check = Path().apply {
        moveTo(size.width * 0.28F, size.height * 0.52F)
        lineTo(size.width * 0.44F, size.height * 0.67F)
        lineTo(size.width * 0.73F, size.height * 0.36F)
      }
      drawPath(check, color = palette.onHighlight, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
  } else {
    Box(modifier = modifier.border(width = 1.5.dp, color = palette.content.copy(alpha = 0.35F), shape = CircleShape))
  }
}

/** An idea the listener already chose: a short row with how its build is going. */
@Composable
private fun ChosenIdeaRow(
  card: IdeaCard,
  palette: CrawlPalette,
  transcriptReady: Boolean,
  onRetry: () -> Unit,
  onSkip: () -> Unit,
  onOpen: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
      Text(
        text = card.title,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1F),
      )
      when (card.status) {
        IdeaStatus.Building -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        IdeaStatus.Built -> if (card.point == ExtensionPoint.Shelf) {
          TextButton(onClick = onOpen) { Text(text = stringResource(StringsR.string.setup_idea_open)) }
        } else {
          Text(text = stringResource(StringsR.string.setup_idea_built), style = CrawlType.label, color = palette.highlight)
        }
        IdeaStatus.Failed -> {
          TextButton(onClick = onRetry) { Text(text = stringResource(StringsR.string.setup_retry)) }
          TextButton(onClick = onSkip) { Text(text = stringResource(StringsR.string.setup_idea_skip)) }
        }
        IdeaStatus.Suggested, IdeaStatus.Skipped -> Unit
      }
    }
    when (card.status) {
      IdeaStatus.Building -> Text(
        text = stringResource(
          if (card.point == ExtensionPoint.SceneChips && !transcriptReady) {
            StringsR.string.setup_idea_waiting_for_transcript
          } else {
            StringsR.string.setup_idea_building
          },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = palette.content.copy(alpha = 0.7F),
      )
      IdeaStatus.Failed -> ErrorText(stringResource(StringsR.string.setup_idea_failed, card.error.orEmpty()))
      else -> Unit
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
