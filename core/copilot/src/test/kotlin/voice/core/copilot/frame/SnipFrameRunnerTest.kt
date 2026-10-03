package voice.core.copilot.frame

import android.content.Context
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import voice.core.copilot.CoPilotMessage
import voice.core.copilot.CoPilotRepository
import voice.core.data.BookId
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SnipFrameRunnerTest {

  private val filesDir: File = createTempDirectory("frames").toFile()
  private val context = mockk<Context> { every { filesDir } returns this@SnipFrameRunnerTest.filesDir }
  private val frameGenerator = mockk<FrameGenerator>()
  private val ideogramClient = mockk<IdeogramClient>()
  private val framePack = mockk<FramePack>()
  private val repository = CoPilotRepository()
  private val runner = SnipFrameRunner(context, frameGenerator, ideogramClient, framePack, repository)

  private val bookId = BookId(Uuid.random().toString())
  private val request = mockk<FrameRequest>()

  private fun addGeneratingMessage() = repository.addMessage(
    bookId,
    CoPilotMessage("m1", CoPilotMessage.Role.CoPilot, "a snip", 0L, isVisualPriority = true, isGeneratingImage = true),
  )

  private fun saved() = repository.allMessagesByBook.value.getValue(bookId).single()

  @Test
  fun `a finished frame is saved and attached to the snip`() = runTest {
    addGeneratingMessage()
    coEvery { frameGenerator.generate(request) } returns byteArrayOf(9, 8, 7)

    runner.run(bookId, "m1", request)

    val message = saved()
    assertFalse(message.isGeneratingImage)
    assertContentEquals(byteArrayOf(9, 8, 7), File(checkNotNull(message.imagePath)).readBytes())
    assertEquals(File(filesDir, "copilot-frames"), File(checkNotNull(message.imagePath)).parentFile)
  }

  @Test
  fun `a failed frame leaves the snip as text and stops the spinner`() = runTest {
    addGeneratingMessage()
    coEvery { frameGenerator.generate(request) } throws IllegalStateException("Ideogram failed: 402")

    runner.run(bookId, "m1", request)

    val message = saved()
    assertFalse(message.isGeneratingImage)
    assertNull(message.imagePath)
    assertEquals("a snip", message.text)
  }

  @Test
  fun `drawing needs both the Ideogram key and the frame pack`() = runTest {
    every { ideogramClient.isConfigured } returns true
    every { framePack.isAvailable() } returns true
    assertTrue(runner.isAvailable())

    every { framePack.isAvailable() } returns false
    assertFalse(runner.isAvailable())

    every { ideogramClient.isConfigured } returns false
    every { framePack.isAvailable() } returns true
    assertFalse(runner.isAvailable())
  }
}
