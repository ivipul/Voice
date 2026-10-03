package voice.core.copilot.frame

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import voice.core.copilot.CoPilotRepository
import voice.core.data.BookId
import voice.core.logging.api.Logger
import java.io.File

/**
 * Draws a snip's comic frame in the background and attaches it to the Feed message once it
 * exists. A frame takes a minute or more (a plan, a plate and one edit per character), so the
 * snip itself is saved to the Feed straight away and the image arrives later, even if the
 * listener leaves the screen. Any failure just leaves the snip as text.
 */
@SingleIn(AppScope::class)
@Inject
class SnipFrameRunner(
  private val context: Context,
  private val frameGenerator: FrameGenerator,
  private val ideogramClient: IdeogramClient,
  private val framePack: FramePack,
  private val coPilotRepository: CoPilotRepository,
) {

  internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  /** True when there is an Ideogram key and a frame pack with style images to draw from. */
  suspend fun isAvailable(): Boolean =
    ideogramClient.isConfigured && withContext(Dispatchers.IO) { framePack.isAvailable() }

  internal fun start(bookId: BookId, messageId: String, request: FrameRequest) {
    scope.launch { run(bookId, messageId, request) }
  }

  internal suspend fun run(bookId: BookId, messageId: String, request: FrameRequest) {
    val path = try {
      val bytes = frameGenerator.generate(request)
      withContext(Dispatchers.IO) { save(messageId, bytes) }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Logger.w(e, "Snip frame generation failed")
      null
    }
    coPilotRepository.updateMessage(bookId, messageId) { it.copy(imagePath = path, isGeneratingImage = false) }
  }

  private fun save(messageId: String, bytes: ByteArray): String {
    val dir = File(context.filesDir, FRAME_DIR).apply { mkdirs() }
    val file = File(dir, "$messageId.png")
    file.writeBytes(bytes)
    return file.absolutePath
  }

  private companion object {
    const val FRAME_DIR = "copilot-frames"
  }
}
