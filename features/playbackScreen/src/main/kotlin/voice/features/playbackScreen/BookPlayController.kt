package voice.features.playbackScreen

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.launch
import voice.core.common.rootGraphAs
import voice.core.data.BookId
import voice.core.ui.InventoryContent
import voice.core.ui.crawl.CrawlTheme
import voice.core.ui.crawl.crawlPaletteOf
import voice.features.playbackScreen.view.BookPlayView
import voice.features.playbackScreen.view.SystemAiChatSheet
import voice.features.playbackScreen.view.holocard.HoloCardDialog
import voice.features.sleepTimer.SleepTimerDialog
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.core.strings.R as StringsR

@Composable
fun BookPlayScreen(bookId: BookId) {
  val viewModel = retain(bookId.value) {
    rootGraphAs<BookPlayGraph>()
      .bookPlayViewModelFactory
      .create(bookId)
  }
  val snackbarHostState = remember { SnackbarHostState() }
  val dialogState = viewModel.dialogState.value
  val viewState = viewModel.viewState()
    ?: return
  val snipSavedMessage = stringResource(StringsR.string.copilot_snip_saved_snackbar)
  val stripUnlockedMessage = stringResource(StringsR.string.strip_unlocked_snackbar)
  val batteryOptimizationMessage = stringResource(StringsR.string.playback_battery_optimization_rationale)
  val batteryOptimizationAction = stringResource(StringsR.string.playback_battery_optimization_action)
  val microphonePermissionMessage = stringResource(StringsR.string.copilot_permission_microphone_required)
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val microphonePermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted ->
    if (granted) {
      viewModel.onAskClick()
    } else {
      coroutineScope.launch {
        snackbarHostState.showSnackbar(message = microphonePermissionMessage)
      }
    }
  }
  val onAskClick: () -> Unit = {
    val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED
    if (hasPermission) {
      viewModel.onAskClick()
    } else {
      microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
  }
  val onChatMicClick: () -> Unit = {
    // Turning the mic off never needs the permission; turning it on goes through the same check as Ask.
    if (viewModel.chatInput.value.isListening) viewModel.onChatMicClick() else onAskClick()
  }
  val lifecycleOwner = LocalLifecycleOwner.current
  LaunchedEffect(viewModel, lifecycleOwner) {
    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
      viewModel.collectSnipEvents()
    }
  }
  LaunchedEffect(viewModel) {
    viewModel.viewEffects.collect { viewEffect ->
      when (viewEffect) {
        BookPlayViewEffect.SnipSaved -> {
          Toast.makeText(context, snipSavedMessage, Toast.LENGTH_SHORT).show()
        }
        BookPlayViewEffect.StripUnlocked -> {
          Toast.makeText(context, stripUnlockedMessage, Toast.LENGTH_SHORT).show()
        }
        BookPlayViewEffect.RequestIgnoreBatteryOptimization -> {
          val result = snackbarHostState.showSnackbar(
            message = batteryOptimizationMessage,
            duration = SnackbarDuration.Long,
            actionLabel = batteryOptimizationAction,
          )
          if (result == SnackbarResult.ActionPerformed) {
            viewModel.onBatteryOptimizationRequested()
          }
        }
      }
    }
  }
  CrawlTheme(edition = viewState.edition) {
    // The chat sheet slides over the whole player, Inventory card included.
    Box(modifier = Modifier.fillMaxSize()) {
      BookPlayView(
        viewState,
        bookId = bookId,
        onPlayClick = viewModel::playPause,
        onFastForwardClick = viewModel::fastForward,
        onRewindClick = viewModel::rewind,
        onSkipToNext = viewModel::next,
        onSkipToPrevious = viewModel::previous,
        onSeek = viewModel::seekTo,
        onStripZoneTap = viewModel::onStripZoneTap,
        onStripFrameClick = viewModel::onStripFrameClick,
        onCloseClick = viewModel::onCloseClick,
        onFeedClick = viewModel::onFeedClick,
        onSleepTimerClick = viewModel::toggleSleepTimer,
        onSkipSilenceClick = viewModel::toggleSkipSilence,
        onVolumeBoostClick = viewModel::onVolumeGainIconClick,
        onCurrentChapterClick = viewModel::onCurrentChapterClick,
        onSmartRecapClick = viewModel::onCatchMeUpClick,
        onSpeedClick = viewModel::onPlaybackSpeedIconClick,
        onAskClick = onAskClick,
        onSnipClick = viewModel::onSnipClick,
        onXRayChipClick = viewModel::onXRayChipClick,
        inventoryContent = rootGraphAs<BookPlayGraph>().inventoryContent,
        useLandscapeLayout = LocalConfiguration.current.orientation == ORIENTATION_LANDSCAPE,
        snackbarHostState = snackbarHostState,
      )
      SystemAiChatSheet(
        visible = viewModel.feedVisible.value,
        palette = crawlPaletteOf(viewState.edition),
        messages = viewModel.copilotMessages(),
        isThinking = viewModel.isThinking.value,
        input = viewModel.chatInput.value,
        onInputChange = viewModel::onChatInputChange,
        onInputTap = viewModel::onChatInputTap,
        onSend = viewModel::onChatSend,
        onCancelAutoSend = viewModel::onChatAutoSendCancel,
        onMicClick = onChatMicClick,
        spokenMessage = viewModel.spokenMessage(),
        onMessagePlayClick = viewModel::onMessagePlayClick,
        onDismiss = viewModel::onFeedDismiss,
        onSeekToSnip = viewModel::onSnipTimestampClick,
        snipLocationLabel = viewModel.snipLocationLabel(),
      )
    }
    if (dialogState != null) {
      when (dialogState) {
        is BookPlayDialogViewState.SpeedDialog -> {
          SpeedSheet(
            speed = dialogState.speed,
            edition = viewState.edition,
            onSpeedChange = viewModel::onPlaybackSpeedChanged,
            onDismiss = viewModel::dismissDialog,
          )
        }
        is BookPlayDialogViewState.VolumeGainDialog -> {
          VolumeGainDialog(dialogState, viewModel)
        }
        is BookPlayDialogViewState.SelectChapterDialog -> {
          SelectChapterDialog(dialogState, viewModel)
        }
        is BookPlayDialogViewState.XRayCardDialog -> {
          HoloCardDialog(
            dialogState = dialogState,
            positionMs = viewModel.cardPositionMs(dialogState.openedAtMs),
            onDismiss = viewModel::dismissDialog,
          )
        }
        is BookPlayDialogViewState.SleepTimer -> {
          SleepTimerDialog(
            viewState = dialogState.viewState,
            onDismiss = viewModel::dismissDialog,
            onIncrementSleepTime = viewModel::incrementSleepTime,
            onDecrementSleepTime = viewModel::decrementSleepTime,
            onAcceptSleepTime = viewModel::onAcceptSleepTime,
            onAcceptSleepAtEndOfChapter = viewModel::onAcceptSleepAtEndOfChapter,
          )
        }
      }
    }
    viewModel.snipSheet.value?.let { snipSheet ->
      SnipBottomSheet(
        state = snipSheet,
        frame = snipSheet.frameState(viewModel.copilotMessages()),
        edition = viewState.edition,
        onDismiss = viewModel::onSnipSheetDismiss,
      )
    }
  }
}

@ContributesTo(AppScope::class)
interface BookPlayGraph {
  val bookPlayViewModelFactory: BookPlayViewModel.Factory
  val inventoryContent: InventoryContent
}

@ContributesTo(AppScope::class)
interface BookPlayProvider {

  @Provides
  @IntoSet
  fun bookPlayNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.Playback> { key ->
    NavEntry(key) {
      BookPlayScreen(bookId = key.bookId)
    }
  }
}
