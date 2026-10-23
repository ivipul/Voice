package voice.core.ui.crawl

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import voice.core.data.ThemeMode
import voice.core.ui.LocalThemeColorScheme
import voice.core.ui.VoiceTheme

/**
 * Colors everything inside in [bookStyle]'s palette and turns the system bar icons to match it.
 * Without a style the app theme is kept.
 */
@Composable
fun CrawlTheme(
  bookStyle: BookStyle?,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (bookStyle != null) {
    remember(bookStyle.palette) { bookStyle.palette.colorScheme() }
  } else {
    MaterialTheme.colorScheme
  }
  if (bookStyle != null) {
    SystemBarIcons(dark = bookStyle.palette.darkSystemBarIcons)
  }
  MaterialTheme(colorScheme = colorScheme, content = content)
}

/** The library and settings: the app's own light colors whatever the phone's dark mode, with dark system bar icons. */
@Composable
fun CrawlLibraryTheme(content: @Composable () -> Unit) {
  SystemBarIcons(dark = true)
  VoiceTheme(themeMode = ThemeMode.Light, themeColorScheme = LocalThemeColorScheme.current, content = content)
}

/** [bookStyle]'s palette, or one taken from the app theme without a style. */
@Composable
fun crawlPaletteOf(bookStyle: BookStyle?): CrawlPalette {
  if (bookStyle != null) return bookStyle.palette
  val colors = MaterialTheme.colorScheme
  return CrawlPalette(
    background = colors.surface,
    content = colors.onSurface,
    highlight = colors.primary,
    onHighlight = colors.onPrimary,
    accent = colors.tertiary,
    onAccent = colors.onTertiary,
    shadow = colors.primaryContainer,
  )
}

/** A comic-style shadow: [shape] in solid [color], offset by [x] and [y] and not blurred. */
fun Modifier.hardShadow(
  color: Color,
  shape: Shape,
  x: Dp,
  y: Dp,
): Modifier = drawBehind {
  val outline = shape.createOutline(size, layoutDirection, this)
  translate(left = x.toPx(), top = y.toPx()) {
    drawOutline(outline = outline, color = color)
  }
}

@Composable
private fun SystemBarIcons(dark: Boolean) {
  val view = LocalView.current
  if (view.isInEditMode) return
  DisposableEffect(view, dark) {
    val window = view.context.findActivity()?.window
    val controller = window?.let { WindowCompat.getInsetsController(it, view) }
    controller?.let { SystemBarIconsOverride.apply(it, dark) }
    onDispose {
      controller?.let(SystemBarIconsOverride::release)
    }
  }
}

/**
 * Book screens hand over to each other (player to Inventory and back), and the screen that opens is composed before
 * the one it replaces is disposed, so the app's own icon colors come back only once the last book screen is gone.
 */
private object SystemBarIconsOverride {

  private var holders = 0
  private var savedLightStatusBars = false
  private var savedLightNavigationBars = false

  fun apply(
    controller: WindowInsetsControllerCompat,
    dark: Boolean,
  ) {
    if (holders == 0) {
      savedLightStatusBars = controller.isAppearanceLightStatusBars
      savedLightNavigationBars = controller.isAppearanceLightNavigationBars
    }
    holders++
    controller.isAppearanceLightStatusBars = dark
    controller.isAppearanceLightNavigationBars = dark
  }

  fun release(controller: WindowInsetsControllerCompat) {
    holders = (holders - 1).coerceAtLeast(0)
    if (holders == 0) {
      controller.isAppearanceLightStatusBars = savedLightStatusBars
      controller.isAppearanceLightNavigationBars = savedLightNavigationBars
    }
  }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
