package voice.features.settings.views

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.launch
import voice.core.data.CoPilotButtonMapping
import voice.core.data.CoPilotTriggerAction
import voice.core.data.CoPilotVoiceSettings
import voice.core.data.store.CoPilotButtonMappingStore
import voice.core.data.store.CoPilotVoiceSettingsStore
import voice.core.ui.VoiceTheme
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
  DoublePrevious(StringsR.string.copilot_settings_trigger_double_previous),
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
 * mapped to that button. Double-tap slots don't offer either: a fixed skip is a single-press
 * action, not something worth reserving a double-tap for.
 */
private fun TriggerSlot.availableActions(): List<CoPilotTriggerAction> = when (this) {
  TriggerSlot.SingleNext -> CoPilotTriggerAction.entries - CoPilotTriggerAction.DefaultRewind
  TriggerSlot.SinglePrevious -> CoPilotTriggerAction.entries - CoPilotTriggerAction.DefaultForward
  TriggerSlot.DoubleNext,
  TriggerSlot.DoublePrevious,
  -> CoPilotTriggerAction.entries - CoPilotTriggerAction.DefaultRewind - CoPilotTriggerAction.DefaultForward
}

private fun CoPilotButtonMapping.get(slot: TriggerSlot): CoPilotTriggerAction = when (slot) {
  TriggerSlot.SingleNext -> singleNext
  TriggerSlot.SinglePrevious -> singlePrevious
  TriggerSlot.DoubleNext -> doubleNext
  TriggerSlot.DoublePrevious -> doublePrevious
}

private fun CoPilotButtonMapping.with(slot: TriggerSlot, action: CoPilotTriggerAction): CoPilotButtonMapping =
  when (slot) {
    TriggerSlot.SingleNext -> copy(singleNext = action)
    TriggerSlot.SinglePrevious -> copy(singlePrevious = action)
    TriggerSlot.DoubleNext -> copy(doubleNext = action)
    TriggerSlot.DoublePrevious -> copy(doublePrevious = action)
  }

@Composable
fun CoPilotSettingsScreen(
  navigator: Navigator,
  mappingStore: DataStore<CoPilotButtonMapping>,
  voiceStore: DataStore<CoPilotVoiceSettings>,
) {
  val mapping by remember { mappingStore.data }.collectAsState(initial = CoPilotButtonMapping())
  val voice by remember { voiceStore.data }.collectAsState(initial = CoPilotVoiceSettings())
  val coroutineScope = rememberCoroutineScope()
  CoPilotSettingsScreen(
    mapping = mapping,
    voice = voice,
    onClose = navigator::goBack,
    onSelect = { slot, action ->
      coroutineScope.launch {
        mappingStore.updateData { it.with(slot, action) }
      }
    },
    onUseSystemAiVoiceChange = { enabled ->
      coroutineScope.launch {
        voiceStore.updateData { it.copy(useSystemAiVoice = enabled) }
      }
    },
    onKeepItCleanChange = { enabled ->
      coroutineScope.launch {
        voiceStore.updateData { it.copy(keepItClean = enabled) }
      }
    },
  )
}

@Composable
private fun CoPilotSettingsScreen(
  mapping: CoPilotButtonMapping,
  voice: CoPilotVoiceSettings,
  onClose: () -> Unit,
  onSelect: (TriggerSlot, CoPilotTriggerAction) -> Unit,
  onUseSystemAiVoiceChange: (Boolean) -> Unit,
  onKeepItCleanChange: (Boolean) -> Unit,
) {
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
    Column(modifier = Modifier.padding(contentPadding)) {
      Text(
        text = stringResource(id = StringsR.string.copilot_settings_description),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
      )
      TriggerSlot.entries.forEach { slot ->
        TriggerRow(
          slot = slot,
          selected = mapping.get(slot),
          onSelect = { action -> onSelect(slot, action) },
        )
      }
      VoiceSwitchRow(
        title = StringsR.string.copilot_settings_voice_system_ai_title,
        summary = StringsR.string.copilot_settings_voice_system_ai_summary,
        checked = voice.useSystemAiVoice,
        onCheckedChange = onUseSystemAiVoiceChange,
      )
      if (voice.useSystemAiVoice) {
        VoiceSwitchRow(
          title = StringsR.string.copilot_settings_voice_keep_clean_title,
          summary = StringsR.string.copilot_settings_voice_keep_clean_summary,
          checked = voice.keepItClean,
          onCheckedChange = onKeepItCleanChange,
        )
      }
    }
  }
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
private fun VoiceSwitchRow(
  title: Int,
  summary: Int,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  ListItem(
    supportingContent = { Text(text = stringResource(id = summary)) },
    trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
  ) {
    Text(text = stringResource(id = title))
  }
}

@Composable
@Preview
private fun CoPilotSettingsPreview() {
  VoiceTheme {
    CoPilotSettingsScreen(
      mapping = CoPilotButtonMapping(),
      voice = CoPilotVoiceSettings(),
      onClose = {},
      onSelect = { _, _ -> },
      onUseSystemAiVoiceChange = {},
      onKeepItCleanChange = {},
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
    @CoPilotVoiceSettingsStore voiceStore: DataStore<CoPilotVoiceSettings>,
  ): NavEntryProvider<*> =
    NavEntryProvider<Destination.CoPilotSettings> { key ->
      NavEntry(key) {
        CoPilotSettingsScreen(navigator = navigator, mappingStore = mappingStore, voiceStore = voiceStore)
      }
    }
}
