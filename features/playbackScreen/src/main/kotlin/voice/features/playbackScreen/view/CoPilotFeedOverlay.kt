package voice.features.playbackScreen.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import voice.core.data.ChapterId
import voice.core.strings.R
import voice.core.ui.formatTime
import voice.core.ui.icons.VoiceIcons
import voice.core.copilot.CoPilotMessage

private val FRAME_BUBBLE_MAX_WIDTH = 280.dp

@Composable
internal fun CoPilotFeedOverlay(
  messages: List<CoPilotMessage>,
  isThinking: Boolean,
  onSend: (String) -> Unit,
  onDismiss: () -> Unit,
  onSeekToSnip: (ChapterId, Long) -> Unit,
  snipLocationLabel: (ChapterId, Long) -> String?,
) {
  var viewedFrame by remember { mutableStateOf<CoPilotMessage?>(null) }
  Dialog(
    onDismissRequest = onDismiss,
    // decorFitsSystemWindows = false hands IME sizing entirely to this content's own
    // imePadding() below, instead of the dialog's window also resizing itself and double-
    // counting the keyboard height. Setting it here (applied before the window is shown)
    // rather than reaching into the window after first composition avoids a race where
    // focusing the field before that later fix-up ran would briefly let the window resize
    // itself too, snapping the input bar to the top before settling into place.
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    Surface(modifier = Modifier.fillMaxSize()) {
      viewedFrame?.imagePath?.let { path ->
        FrameCardViewer(
          imagePath = path,
          contentDescription = viewedFrame?.text.orEmpty(),
          onDismiss = { viewedFrame = null },
        )
      }
      Scaffold(
        topBar = {
          TopAppBar(
            title = { Text(text = stringResource(id = R.string.copilot_feed_title)) },
            navigationIcon = {
              IconButton(onClick = onDismiss) {
                Icon(
                  imageVector = VoiceIcons.Close,
                  contentDescription = stringResource(id = R.string.copilot_feed_close),
                )
              }
            },
          )
        },
        bottomBar = {
          FeedInputBar(isThinking = isThinking, onSend = onSend)
        },
      ) { contentPadding ->
        if (messages.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(contentPadding)
              .padding(24.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = stringResource(id = R.string.copilot_feed_empty),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          val listState = rememberLazyListState()
          LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) {
              listState.animateScrollToItem(messages.lastIndex)
            }
          }
          LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
              .fillMaxSize()
              .padding(contentPadding),
          ) {
            items(messages, key = { it.id }) { message ->
              ChatBubble(
                message,
                onSeekToSnip = onSeekToSnip,
                snipLocationLabel = snipLocationLabel,
                onOpenFrame = { viewedFrame = message },
              )
            }
            if (isThinking) {
              item {
                ThinkingBubble()
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ChatBubble(
  message: CoPilotMessage,
  onSeekToSnip: (ChapterId, Long) -> Unit,
  snipLocationLabel: (ChapterId, Long) -> String?,
  onOpenFrame: () -> Unit,
) {
  val isUser = message.role == CoPilotMessage.Role.User
  val snipChapterId = message.snipChapterId
  val snipPositionInChapterMs = message.snipPositionInChapterMs
  val hasFrame = message.imagePath != null || message.isGeneratingImage
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
  ) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
      } else {
        MaterialTheme.colorScheme.surfaceVariant
      },
      modifier = Modifier.widthIn(max = if (hasFrame) FRAME_BUBBLE_MAX_WIDTH else 320.dp),
    ) {
      Column {
        if (hasFrame) {
          HoloImageLoader(
            imagePath = message.imagePath,
            contentDescription = message.text,
            modifier = if (message.imagePath != null) {
              Modifier.clickable(onClickLabel = stringResource(id = R.string.copilot_feed_frame_open), onClick = onOpenFrame)
            } else {
              Modifier
            },
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
          )
        }
        if (snipChapterId != null && snipPositionInChapterMs != null) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onSeekToSnip(snipChapterId, snipPositionInChapterMs) }
              .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = VoiceIcons.Timelapse,
              contentDescription = null,
              modifier = Modifier.size(14.dp),
              tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
              text = stringResource(
                id = R.string.copilot_feed_snip_annotation,
                snipLocationLabel(snipChapterId, snipPositionInChapterMs) ?: formatTime(snipPositionInChapterMs),
              ),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
        Text(
          text = message.text,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          style = MaterialTheme.typography.bodyLarge,
        )
      }
    }
  }
}

@Composable
private fun ThinkingBubble() {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.size(8.dp))
        Text(text = stringResource(id = R.string.copilot_feed_thinking))
      }
    }
  }
}

@Composable
private fun FeedInputBar(isThinking: Boolean, onSend: (String) -> Unit) {
  var text by remember { mutableStateOf("") }
  fun send() {
    val trimmed = text.trim()
    if (trimmed.isNotEmpty() && !isThinking) {
      onSend(trimmed)
      text = ""
    }
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .imePadding()
      .windowInsetsPadding(WindowInsets.navigationBars)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    OutlinedTextField(
      value = text,
      onValueChange = { text = it },
      modifier = Modifier
        .weight(1f),
      placeholder = { Text(text = stringResource(id = R.string.copilot_feed_input_placeholder)) },
      maxLines = 4,
      enabled = !isThinking,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
      keyboardActions = KeyboardActions(onSend = { send() }),
    )
    Spacer(modifier = Modifier.size(8.dp))
    IconButton(
      enabled = !isThinking,
      onClick = ::send,
    ) {
      Icon(
        imageVector = VoiceIcons.Send,
        contentDescription = stringResource(id = R.string.copilot_feed_input_send),
      )
    }
  }
}
