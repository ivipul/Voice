package voice.features.playbackScreen.view

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The poster player's own icons, on the 24 unit grid of the Material symbols. */
internal object PosterIcons {

  /** A viewfinder: capture this moment. */
  val Snip: ImageVector = icon("Snip") {
    stroke("M4 9V5h4M16 5h4v4M20 15v4h-4M8 19H4v-4")
    fill("M12 9.8a2.2 2.2 0 1 1 0 4.4a2.2 2.2 0 1 1 0-4.4z")
  }

  /** A loot chest. */
  val Inventory: ImageVector = icon("Inventory") {
    stroke("M4 10h16v9a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1zM4 10a4 4 0 0 1 4-4h8a4 4 0 0 1 4 4M4 13h16M11 12h2v3h-2z", width = 1.9F)
  }
}

private fun icon(
  name: String,
  paths: ImageVector.Builder.() -> Unit,
): ImageVector = ImageVector.Builder(
  name = name,
  defaultWidth = 24.dp,
  defaultHeight = 24.dp,
  viewportWidth = 24F,
  viewportHeight = 24F,
)
  .apply(paths)
  .build()

private fun ImageVector.Builder.stroke(
  pathData: String,
  width: Float = 2F,
) {
  addPath(
    pathData = addPathNodes(pathData),
    stroke = SolidColor(Color.Black),
    strokeLineWidth = width,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
  )
}

private fun ImageVector.Builder.fill(pathData: String) {
  addPath(
    pathData = addPathNodes(pathData),
    fill = SolidColor(Color.Black),
  )
}
