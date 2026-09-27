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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.ui.VoiceTheme
import voice.core.ui.icons.VoiceIcons
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.navigation.Navigator
import voice.core.strings.R as StringsR

/** Which trigger slot a headset button press maps to. Wiring these to real Bluetooth events is Phase 3. */
private enum class TriggerSlot(val labelRes: Int) {
  SingleNext(StringsR.string.copilot_settings_trigger_single_next),
  SinglePrevious(StringsR.string.copilot_settings_trigger_single_previous),
  DoubleNext(StringsR.string.copilot_settings_trigger_double_next),
  DoublePrevious(StringsR.string.copilot_settings_trigger_double_previous),
}

private enum class TriggerAction(val labelRes: Int) {
  DefaultRewind(StringsR.string.copilot_settings_action_default_rewind),
  DefaultForward(StringsR.string.copilot_settings_action_default_forward),
  OpenMic(StringsR.string.copilot_settings_action_open_mic),
  AutoIdentify(StringsR.string.copilot_settings_action_auto_identify),
  CatchMeUp(StringsR.string.copilot_settings_action_catch_me_up),
}

@Composable
fun CoPilotSettingsScreen(navigator: Navigator) {
  // Matches the hardcoded default in LibrarySessionCallback (core:playback). Changing the
  // selection here doesn't yet change what the headset buttons actually trigger - see that
  // file's dispatch logic and the description text below.
  var mapping by remember {
    mutableStateOf(
      mapOf(
        TriggerSlot.SingleNext to TriggerAction.OpenMic,
        TriggerSlot.SinglePrevious to TriggerAction.DefaultRewind,
        TriggerSlot.DoubleNext to TriggerAction.AutoIdentify,
        TriggerSlot.DoublePrevious to TriggerAction.CatchMeUp,
      ),
    )
  }
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(id = StringsR.string.copilot_settings_title)) },
        navigationIcon = {
          IconButton(onClick = navigator::goBack) {
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
          selected = mapping.getValue(slot),
          onSelect = { action -> mapping = mapping + (slot to action) },
        )
      }
    }
  }
}

@Composable
private fun TriggerRow(
  slot: TriggerSlot,
  selected: TriggerAction,
  onSelect: (TriggerAction) -> Unit,
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
          TriggerAction.entries.forEach { action ->
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
    CoPilotSettingsScreen(navigator = Navigator())
  }
}

@ContributesTo(AppScope::class)
interface CoPilotSettingsProvider {

  @Provides
  @IntoSet
  fun coPilotSettingsNavEntryProvider(navigator: Navigator): NavEntryProvider<*> =
    NavEntryProvider<Destination.CoPilotSettings> { key ->
      NavEntry(key) {
        CoPilotSettingsScreen(navigator = navigator)
      }
    }
}
