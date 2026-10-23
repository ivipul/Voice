package voice.features.settings.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.launch
import voice.core.data.ApiKeys
import voice.core.data.CoPilotButtonMapping
import voice.core.data.CoPilotTriggerAction
import voice.core.data.store.CoPilotButtonMappingStore
import voice.core.data.store.ApiKeysStore
import voice.core.ui.VoiceTheme
import voice.core.ui.crawl.CrawlLibraryTheme
import voice.core.ui.icons.VoiceIcons
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.navigation.Navigator
import voice.core.strings.R as StringsR

/** Which headset button gesture a slot represents. */
private enum class TriggerSlot(val labelRes: Int) {
  SingleNext(StringsR.string.copilot_settings_trigger_single_next),
  SinglePrevious(StringsR.string.copilot_settings_trigger_single_previous),
  DoubleNext(StringsR.string.copilot_settings_trigger_double_next),
  TripleNext(StringsR.string.copilot_settings_trigger_triple_next),
  DoublePrevious(StringsR.string.copilot_settings_trigger_double_previous),
}

/** A service whose API key the listener types in here. */
private enum class KeyService(val titleRes: Int, val hintRes: Int) {
  Claude(StringsR.string.copilot_settings_keys_claude, StringsR.string.copilot_settings_keys_claude_hint),
  Deepgram(StringsR.string.copilot_settings_keys_deepgram, StringsR.string.copilot_settings_keys_deepgram_hint),
  Ideogram(StringsR.string.copilot_settings_keys_ideogram, StringsR.string.copilot_settings_keys_ideogram_hint),
  ;

  fun read(keys: ApiKeys): String = when (this) {
    Claude -> keys.claude
    Deepgram -> keys.deepgram
    Ideogram -> keys.ideogram
  }

  fun write(keys: ApiKeys, key: String): ApiKeys = when (this) {
    Claude -> keys.copy(claude = key)
    Deepgram -> keys.copy(deepgram = key)
    Ideogram -> keys.copy(ideogram = key)
  }
}

private val CoPilotTriggerAction.labelRes: Int
  get() = when (this) {
    CoPilotTriggerAction.DefaultRewind -> StringsR.string.copilot_settings_action_default_rewind
    CoPilotTriggerAction.DefaultForward -> StringsR.string.copilot_settings_action_default_forward
    CoPilotTriggerAction.OpenMic -> StringsR.string.copilot_settings_action_open_mic
    CoPilotTriggerAction.AutoIdentify -> StringsR.string.copilot_settings_action_auto_identify
    CoPilotTriggerAction.CatchMeUp -> StringsR.string.copilot_settings_action_catch_me_up
    CoPilotTriggerAction.Snip -> StringsR.string.copilot_settings_action_snip
  }

/**
 * Single NEXT/PREVIOUS only offer the fixed-seek direction that matches their physical
 * gesture (rewind for PREVIOUS, forward for NEXT) - the other direction wouldn't make sense
 * mapped to that button. Double- and triple-tap slots don't offer either: a fixed skip is a
 * single-press action, not something worth reserving a multi-tap for.
 */
private fun TriggerSlot.availableActions(): List<CoPilotTriggerAction> = when (this) {
  TriggerSlot.SingleNext -> CoPilotTriggerAction.entries - CoPilotTriggerAction.DefaultRewind
  TriggerSlot.SinglePrevious -> CoPilotTriggerAction.entries - CoPilotTriggerAction.DefaultForward
  TriggerSlot.DoubleNext,
  TriggerSlot.TripleNext,
  TriggerSlot.DoublePrevious,
  -> CoPilotTriggerAction.entries - CoPilotTriggerAction.DefaultRewind - CoPilotTriggerAction.DefaultForward
}

private fun CoPilotButtonMapping.get(slot: TriggerSlot): CoPilotTriggerAction = when (slot) {
  TriggerSlot.SingleNext -> singleNext
  TriggerSlot.SinglePrevious -> singlePrevious
  TriggerSlot.DoubleNext -> doubleNext
  TriggerSlot.TripleNext -> tripleNext
  TriggerSlot.DoublePrevious -> doublePrevious
}

private fun CoPilotButtonMapping.with(slot: TriggerSlot, action: CoPilotTriggerAction): CoPilotButtonMapping =
  when (slot) {
    TriggerSlot.SingleNext -> copy(singleNext = action)
    TriggerSlot.SinglePrevious -> copy(singlePrevious = action)
    TriggerSlot.DoubleNext -> copy(doubleNext = action)
    TriggerSlot.TripleNext -> copy(tripleNext = action)
    TriggerSlot.DoublePrevious -> copy(doublePrevious = action)
  }

@Composable
fun CoPilotSettingsScreen(
  navigator: Navigator,
  mappingStore: DataStore<CoPilotButtonMapping>,
  keysStore: DataStore<ApiKeys>,
) {
  val mapping by remember { mappingStore.data }.collectAsState(initial = CoPilotButtonMapping())
  val keys by remember { keysStore.data }.collectAsState(initial = ApiKeys())
  val coroutineScope = rememberCoroutineScope()
  CoPilotSettingsScreen(
    mapping = mapping,
    keys = keys,
    onClose = navigator::goBack,
    onSelect = { slot, action ->
      coroutineScope.launch {
        mappingStore.updateData { it.with(slot, action) }
      }
    },
    onKeyChange = { service, key ->
      coroutineScope.launch {
        keysStore.updateData { service.write(it, key.trim()) }
      }
    },
  )
}

@Composable
private fun CoPilotSettingsScreen(
  mapping: CoPilotButtonMapping,
  keys: ApiKeys,
  onClose: () -> Unit,
  onSelect: (TriggerSlot, CoPilotTriggerAction) -> Unit,
  onKeyChange: (KeyService, String) -> Unit,
) {
  var editing by remember { mutableStateOf<KeyService?>(null) }
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(id = StringsR.string.copilot_settings_title)) },
        navigationIcon = {
          IconButton(onClick = onClose) {
            Icon(
              imageVector = VoiceIcons.Close,
              contentDescription = stringResource(id = StringsR.string.common_action_close),
            )
          }
        },
      )
    },
  ) { contentPadding ->
    Column(
      modifier = Modifier
        .padding(contentPadding)
        .verticalScroll(rememberScrollState()),
    ) {
      SectionHeader(StringsR.string.copilot_settings_keys_header)
      Text(
        text = stringResource(id = StringsR.string.copilot_settings_keys_description),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
      )
      KeyService.entries.forEach { service ->
        KeyRow(service = service, key = service.read(keys), onClick = { editing = service })
      }
      SectionHeader(StringsR.string.copilot_settings_buttons_header)
      TriggerSlot.entries.forEach { slot ->
        TriggerRow(
          slot = slot,
          selected = mapping.get(slot),
          onSelect = { action -> onSelect(slot, action) },
        )
      }
    }
  }
  editing?.let { service ->
    KeyDialog(
      service = service,
      current = service.read(keys),
      onSave = { key ->
        onKeyChange(service, key)
        editing = null
      },
      onDismiss = { editing = null },
    )
  }
}

@Composable
private fun SectionHeader(text: Int) {
  Text(
    text = stringResource(id = text),
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
  )
}

@Composable
private fun KeyRow(
  service: KeyService,
  key: String,
  onClick: () -> Unit,
) {
  ListItem(
    modifier = Modifier.clickable(onClick = onClick),
    overlineContent = { Text(text = stringResource(id = service.hintRes)) },
    supportingContent = {
      Text(
        text = if (key.isBlank()) {
          stringResource(id = StringsR.string.copilot_settings_keys_not_set)
        } else {
          stringResource(id = StringsR.string.copilot_settings_keys_set, key.takeLast(4))
        },
        color = if (key.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
  ) {
    Text(text = stringResource(id = service.titleRes))
  }
}

@Composable
private fun KeyDialog(
  service: KeyService,
  current: String,
  onSave: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var text by remember { mutableStateOf(current) }
  var visible by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(id = service.titleRes)) },
    text = {
      Column {
        Text(text = stringResource(id = service.hintRes), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
          value = text,
          onValueChange = { text = it },
          label = { Text(text = stringResource(id = StringsR.string.copilot_settings_keys_dialog_label)) },
          singleLine = true,
          visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
          modifier = Modifier.padding(top = 12.dp),
        )
        TextButton(onClick = { visible = !visible }) {
          Text(text = stringResource(id = StringsR.string.copilot_settings_keys_dialog_show))
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onSave(text) }) {
        Text(text = stringResource(id = StringsR.string.copilot_settings_keys_dialog_save))
      }
    },
    dismissButton = {
      if (current.isNotBlank()) {
        TextButton(onClick = { onSave("") }) {
          Text(text = stringResource(id = StringsR.string.copilot_settings_keys_dialog_clear))
        }
      }
    },
  )
}

@Composable
private fun TriggerRow(
  slot: TriggerSlot,
  selected: CoPilotTriggerAction,
  onSelect: (CoPilotTriggerAction) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  ListItem(
    supportingContent = { Text(text = stringResource(id = selected.labelRes)) },
    trailingContent = {
      Box {
        IconButton(onClick = { expanded = true }) {
          Icon(imageVector = VoiceIcons.ExpandMore, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
          slot.availableActions().forEach { action ->
            DropdownMenuItem(
              text = { Text(text = stringResource(id = action.labelRes)) },
              onClick = {
                onSelect(action)
                expanded = false
              },
            )
          }
        }
      }
    },
  ) {
    Text(text = stringResource(id = slot.labelRes))
  }
}

@Composable
@Preview
private fun CoPilotSettingsPreview() {
  VoiceTheme {
    CoPilotSettingsScreen(
      mapping = CoPilotButtonMapping(),
      keys = ApiKeys(claude = "sk-ant-example-1234"),
      onClose = {},
      onSelect = { _, _ -> },
      onKeyChange = { _, _ -> },
    )
  }
}

@ContributesTo(AppScope::class)
interface CoPilotSettingsProvider {

  @Provides
  @IntoSet
  fun coPilotSettingsNavEntryProvider(
    navigator: Navigator,
    @CoPilotButtonMappingStore mappingStore: DataStore<CoPilotButtonMapping>,
    @ApiKeysStore keysStore: DataStore<ApiKeys>,
  ): NavEntryProvider<*> =
    NavEntryProvider<Destination.CoPilotSettings> { key ->
      NavEntry(key) {
        CrawlLibraryTheme {
          CoPilotSettingsScreen(navigator = navigator, mappingStore = mappingStore, keysStore = keysStore)
        }
      }
    }
}
