@file:Suppress("ktlint:compose:compositionlocal-allowlist")

package voice.core.ui

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import voice.core.data.BookId

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

fun sharedCoverKey(bookId: BookId): String = "book-cover-${bookId.value}"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedCoverElementModifier(bookId: BookId): Modifier {
  val sharedTransitionScope = LocalSharedTransitionScope.current
    ?: return this
  return with(sharedTransitionScope) {
    sharedElement(
      sharedContentState = rememberSharedContentState(key = sharedCoverKey(bookId)),
      animatedVisibilityScope = LocalNavAnimatedContentScope.current,
    )
  }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.playButtonSharedBoundsModifier(): Modifier {
  val sharedTransitionScope = LocalSharedTransitionScope.current
    ?: return this
  return with(sharedTransitionScope) {
    sharedBounds(
      sharedContentState = rememberSharedContentState(key = "play-button"),
      animatedVisibilityScope = LocalNavAnimatedContentScope.current,
      enter = EnterTransition.None,
      exit = ExitTransition.None,
      // Above a library card growing into the player, which holds the player's play button.
      zIndexInOverlay = 2F,
    )
  }
}

/** How round a library card's corners are. The player it grows into has square ones. */
val BookCardCorner = 28.dp

private const val BOOK_CARD_DURATION_MS = 500
private val BookCardEasing = CubicBezierEasing(0.2F, 0F, 0F, 1F)
private val BookCardBoundsTransform = BoundsTransform { _, _ ->
  tween(durationMillis = BOOK_CARD_DURATION_MS, easing = BookCardEasing)
}

/**
 * A library card grows into its book's player and shrinks back into the card, as two shared bounds. This one goes on
 * the card's color, which only moves and never fades, so nothing behind shows through while [bookCardContentModifier]
 * crossfades what sits on it. [onCard] is true on the library card and false on the player.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.bookCardBackdropModifier(
  bookId: BookId,
  onCard: Boolean,
): Modifier {
  val sharedTransitionScope = LocalSharedTransitionScope.current
    ?: return this
  val animatedVisibilityScope = LocalNavAnimatedContentScope.current
  val clip = bookCardClip(animatedVisibilityScope.transition, onCard)
  return with(sharedTransitionScope) {
    sharedBounds(
      sharedContentState = rememberSharedContentState(key = "book-card-backdrop-${bookId.value}"),
      animatedVisibilityScope = animatedVisibilityScope,
      enter = EnterTransition.None,
      exit = ExitTransition.None,
      boundsTransform = BookCardBoundsTransform,
      resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
      clipInOverlayDuringTransition = OverlayClip(clip),
    )
  }
}

/** The card's content giving way to the player's, or back, on top of [bookCardBackdropModifier]. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.bookCardContentModifier(
  bookId: BookId,
  onCard: Boolean,
): Modifier {
  val sharedTransitionScope = LocalSharedTransitionScope.current
    ?: return this
  val animatedVisibilityScope = LocalNavAnimatedContentScope.current
  val clip = bookCardClip(animatedVisibilityScope.transition, onCard)
  return with(sharedTransitionScope) {
    sharedBounds(
      sharedContentState = rememberSharedContentState(key = "book-card-${bookId.value}"),
      animatedVisibilityScope = animatedVisibilityScope,
      enter = fadeIn(animationSpec = tween(durationMillis = 300, delayMillis = 150)),
      exit = fadeOut(animationSpec = tween(durationMillis = 150)),
      boundsTransform = BookCardBoundsTransform,
      resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter),
      zIndexInOverlay = 1F,
      clipInOverlayDuringTransition = OverlayClip(clip),
    )
  }
}

/** Round corners while this side is the card, square ones while it is the player. */
@Composable
private fun bookCardClip(
  transition: Transition<EnterExitState>,
  onCard: Boolean,
): Shape {
  val corner = transition.animateDp(
    transitionSpec = { tween(durationMillis = BOOK_CARD_DURATION_MS, easing = BookCardEasing) },
    label = "bookCardCorner",
  ) { state ->
    if ((state == EnterExitState.Visible) == onCard) BookCardCorner else 0.dp
  }
  return remember(corner) { AnimatedCornerShape(corner) }
}

/** Reads [corner] when the clip is drawn, so the corners animate without recomposing. */
private class AnimatedCornerShape(private val corner: State<Dp>) : Shape {

  override fun createOutline(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
  ): Outline = RoundedCornerShape(corner.value).createOutline(size, layoutDirection, density)
}
