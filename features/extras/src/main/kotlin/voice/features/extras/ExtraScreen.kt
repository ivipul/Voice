package voice.features.extras

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import voice.core.common.rootGraphAs
import voice.core.data.BookId
import voice.core.ui.crawl.CrawlPalette
import voice.core.ui.crawl.CrawlTheme
import voice.core.ui.icons.VoiceIcons
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.core.strings.R as StringsR

@ContributesTo(AppScope::class)
interface ExtraGraph {
  val extraViewModelFactory: ExtraViewModel.Factory
}

@ContributesTo(AppScope::class)
interface ExtraProvider {

  @Provides
  @IntoSet
  fun extraNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.Extra> { key ->
    NavEntry(key) {
      ExtraScreen(bookId = key.bookId, extraId = key.extraId)
    }
  }
}

@Composable
private fun ExtraScreen(
  bookId: BookId,
  extraId: String,
) {
  val viewModel = retain(bookId.value, extraId) {
    rootGraphAs<ExtraGraph>().extraViewModelFactory.create(bookId, extraId)
  }
  val viewState = viewModel.viewState() ?: return
  BackHandler(onBack = viewModel::close)
  CrawlTheme(bookStyle = viewState.bookStyle) {
    val palette = viewState.bookStyle.palette
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(palette.background)
        .safeDrawingPadding(),
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        if (viewState.lastError != null || viewState.fixing || viewState.fixError != null) {
          FixBanner(viewState = viewState, palette = palette, onFix = viewModel::onFix, onDismiss = viewModel::onDismissError)
        }
        ExtraWebView(page = viewState.page, viewModel = viewModel, modifier = Modifier.weight(1F))
      }
      ExtraActions(
        palette = palette,
        onClose = viewModel::close,
        onFix = viewModel::onFix,
        onDelete = viewModel::onDelete,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(8.dp),
      )
    }
  }
}

@Composable
private fun ExtraActions(
  palette: CrawlPalette,
  onClose: () -> Unit,
  onFix: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var menuOpen by remember { mutableStateOf(false) }
  var confirmDelete by remember { mutableStateOf(false) }
  Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    Box {
      RoundIconButton(palette = palette, onClick = { menuOpen = true }) {
        Icon(imageVector = VoiceIcons.MoreVert, contentDescription = stringResource(StringsR.string.extra_menu))
      }
      DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
          text = { Text(text = stringResource(StringsR.string.extra_fix)) },
          onClick = {
            menuOpen = false
            onFix()
          },
        )
        DropdownMenuItem(
          text = { Text(text = stringResource(StringsR.string.extra_delete)) },
          onClick = {
            menuOpen = false
            confirmDelete = true
          },
        )
      }
    }
    RoundIconButton(palette = palette, onClick = onClose) {
      Icon(imageVector = VoiceIcons.Close, contentDescription = stringResource(StringsR.string.common_action_close))
    }
  }
  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text(text = stringResource(StringsR.string.extra_delete_title)) },
      text = { Text(text = stringResource(StringsR.string.extra_delete_body)) },
      confirmButton = {
        TextButton(
          onClick = {
            confirmDelete = false
            onDelete()
          },
        ) {
          Text(text = stringResource(StringsR.string.extra_delete))
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmDelete = false }) {
          Text(text = stringResource(StringsR.string.extra_keep))
        }
      },
    )
  }
}

@Composable
private fun RoundIconButton(
  palette: CrawlPalette,
  onClick: () -> Unit,
  content: @Composable () -> Unit,
) {
  IconButton(
    onClick = onClick,
    colors = IconButtonDefaults.iconButtonColors(containerColor = palette.background, contentColor = palette.content),
    modifier = Modifier
      .size(40.dp)
      .border(width = 2.dp, color = palette.content, shape = CircleShape),
    content = content,
  )
}

@Composable
private fun FixBanner(
  viewState: ExtraViewState,
  palette: CrawlPalette,
  onFix: () -> Unit,
  onDismiss: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 12.dp, end = 104.dp, top = 8.dp, bottom = 8.dp)
      .background(palette.highlight, RoundedCornerShape(14.dp))
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    val text = when {
      viewState.fixing -> stringResource(StringsR.string.extra_fixing)
      viewState.fixError != null -> stringResource(StringsR.string.extra_fix_failed, viewState.fixError)
      else -> stringResource(StringsR.string.extra_error)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      if (viewState.fixing) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = palette.onHighlight)
      }
      Text(text = text, style = MaterialTheme.typography.bodyMedium, color = palette.onHighlight)
    }
    if (!viewState.fixing) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onFix) { Text(text = stringResource(StringsR.string.extra_fix)) }
        TextButton(onClick = onDismiss) {
          Text(text = stringResource(StringsR.string.extra_error_dismiss), color = palette.onHighlight)
        }
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ExtraWebView(
  page: String,
  viewModel: ExtraViewModel,
  modifier: Modifier = Modifier,
) {
  val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
  DisposableEffect(Unit) {
    onDispose { scope.cancel() }
  }
  AndroidView(
    modifier = modifier.fillMaxWidth(),
    factory = { context -> sandboxedWebView(context, viewModel, scope) },
    update = { webView ->
      if (webView.tag != page) {
        webView.tag = page
        webView.loadDataWithBaseURL(null, page, "text/html", "utf-8", null)
      }
    },
    onRelease = { it.destroy() },
  )
}

/**
 * A WebView that can run the page's own script and nothing else: no network, no files, no navigation away, and only
 * the [ExtraBridge] to talk to the app.
 */
@SuppressLint("SetJavaScriptEnabled")
private fun sandboxedWebView(
  context: Context,
  viewModel: ExtraViewModel,
  scope: CoroutineScope,
): WebView = WebView(context).apply {
  setBackgroundColor(android.graphics.Color.TRANSPARENT)
  settings.apply {
    javaScriptEnabled = true
    allowFileAccess = false
    allowContentAccess = false
    blockNetworkLoads = true
    blockNetworkImage = true
    domStorageEnabled = false
    javaScriptCanOpenWindowsAutomatically = false
    setSupportMultipleWindows(false)
    setGeolocationEnabled(false)
  }
  webViewClient = object : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
      if (request.url.scheme == "data") null else WebResourceResponse("text/plain", "utf-8", null)
  }
  addJavascriptInterface(ExtraBridge(this, viewModel, scope), "EarwigNative")
}

/**
 * What the page's `earwig` object calls. These run on the WebView's bridge thread, so the quick reads block it
 * briefly, and `ask` answers later through `__earwigResolve` on the main thread.
 */
private class ExtraBridge(
  private val webView: WebView,
  private val viewModel: ExtraViewModel,
  private val scope: CoroutineScope,
) {

  @JavascriptInterface
  fun book(): String = runBlocking { viewModel.bookInfo() }

  @JavascriptInterface
  fun transcript(maxChars: Int): String = runBlocking { viewModel.transcript(maxChars) }

  @JavascriptInterface
  fun ask(
    id: Int,
    question: String,
  ) {
    scope.launch {
      val result = runCatching { viewModel.ask(question) }
      val text = result.getOrNull()?.let(::JsonPrimitive) ?: JsonNull
      val error = result.exceptionOrNull()?.let { JsonPrimitive(it.message ?: "Ask failed") } ?: JsonNull
      webView.evaluateJavascript("window.__earwigResolve($id, $text, $error);", null)
    }
  }

  @JavascriptInterface
  fun get(key: String): String? = runBlocking { viewModel.savedValue(key) }

  @JavascriptInterface
  fun set(
    key: String,
    value: String,
  ) {
    runBlocking { viewModel.saveValue(key, value) }
  }

  @JavascriptInterface
  fun seek(positionMs: Double) {
    scope.launch { viewModel.onSeek(positionMs.toLong()) }
  }

  @JavascriptInterface
  fun close() {
    scope.launch { viewModel.close() }
  }

  @JavascriptInterface
  fun reportError(message: String) {
    scope.launch { viewModel.onPageError(message) }
  }
}
