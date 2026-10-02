package voice.core.copilot.frame

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import voice.core.copilot.GeminiClient
import voice.core.data.Book
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ChapterId
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class FrameGeneratorTest {

  private val styleImages = listOf(File("style-1.png"), File("style-2.png"), File("style-3.png"))
  private val carlLook2 = FrameLook(2, 6_436_624, "A man in a cloak", File("c2-standing.png"), File("c2-action.png"))
  private val carl = FrameCharacter(
    "Carl",
    "carl",
    listOf(FrameLook(1, 93_800, "A man in boxers", File("c1-standing.png"), File("c1-action.png")), carlLook2),
  )
  private val framePack = mockk<FramePack> {
    every { load(any()) } returns FramePackData("Dungeon Crawler Carl", styleImages, listOf(carl))
  }
  private val framePlanner = mockk<FramePlanner>()
  private val ideogramClient = mockk<IdeogramClient>()
  private val geminiClient = mockk<GeminiClient>()
  private val generator = FrameGenerator(framePack, framePlanner, ideogramClient, geminiClient)

  private val plate = byteArrayOf(1)
  private val withCarl = byteArrayOf(2)
  private val withGoblin = byteArrayOf(3)

  private val prompts = mutableListOf<String>()
  private val masks = mutableListOf<ByteArray>()
  private val references = mutableListOf<List<File>>()

  private fun stubIdeogram() {
    coEvery { ideogramClient.generatePlate(any(), any()) } returns plate
    var calls = 0
    coEvery { ideogramClient.preciseEdit(any(), any(), any(), any()) } answers {
      prompts += firstArg<String>()
      masks += thirdArg<ByteArray>()
      references += arg<List<File>>(3)
      if (calls++ == 0) withCarl else withGoblin
    }
  }

  private fun request(positionMs: Long = 7_000_000) = FrameRequest(
    book = fakeBook(),
    highlight = "a highlight",
    transcript = "a transcript",
    positionMs = positionMs,
  )

  @Test
  fun `draws the plate then adds each character with its own masked edit`() = runTest {
    coEvery { framePlanner.plan(any(), any(), any()) } returns framePlan(
      plannedCharacter(character = "Carl"),
      plannedCharacter(noun = "goblin", box = listOf(0.62, 0.3, 0.95, 0.9), prominence = 60).copy(description = "a green-skinned goblin"),
    )
    coEvery { geminiClient.askJson(any(), any(), any()) } throws IllegalStateException("no vision in this test")
    stubIdeogram()

    val result = generator.generate(request())

    assertContentEquals(withGoblin, result)
    assertEquals(2, prompts.size)
    assertTrue(prompts[0].startsWith("Add the man shown in the reference images"))
    assertTrue(prompts[1].startsWith("Add a green-skinned goblin"))
  }

  @Test
  fun `a reference character gets the standing and action images of the look at the snip timestamp`() = runTest {
    coEvery { framePlanner.plan(any(), any(), any()) } returns framePlan(
      plannedCharacter(character = "Carl"),
      plannedCharacter(noun = "goblin"),
    )
    coEvery { geminiClient.askJson(any(), any(), any()) } throws IllegalStateException("no vision")
    stubIdeogram()

    assertContentEquals(withGoblin, generator.generate(request(positionMs = 7_000_000)))

    assertEquals(listOf(File("c2-standing.png"), File("c2-action.png")), references[0])
    assertEquals(emptyList(), references[1])
    assertTrue(prompts[0].contains("This outfit and look: A man in a cloak."))
  }

  @Test
  fun `an earlier snip uses an earlier look`() = runTest {
    coEvery { framePlanner.plan(any(), any(), any()) } returns framePlan(plannedCharacter(character = "Carl"))
    coEvery { geminiClient.askJson(any(), any(), any()) } throws IllegalStateException("no vision")
    stubIdeogram()

    assertContentEquals(withCarl, generator.generate(request(positionMs = 600_000)))

    assertEquals(listOf(File("c1-standing.png"), File("c1-action.png")), references.single())
  }

  @Test
  fun `names never reach a prompt, only the planner is told them`() = runTest {
    val names = mutableListOf<List<String>>()
    coEvery { framePlanner.plan(any(), any(), capture(names)) } returns framePlan(plannedCharacter(character = "Carl"))
    coEvery { geminiClient.askJson(any(), any(), any()) } throws IllegalStateException("no vision")
    stubIdeogram()

    assertContentEquals(withCarl, generator.generate(request()))

    assertEquals(listOf("Carl"), names.single())
    assertFalse(prompts.any { it.contains("Carl") })
  }

  @Test
  fun `a look note that carries a name is left out of the prompt`() = runTest {
    every { framePack.load(any()) } returns FramePackData(
      "Dungeon Crawler Carl",
      styleImages,
      listOf(carl.copy(looks = listOf(carlLook2.copy(description = "Carl in a hooded cloak")))),
    )
    coEvery { framePlanner.plan(any(), any(), any()) } returns framePlan(plannedCharacter(character = "Carl"))
    coEvery { geminiClient.askJson(any(), any(), any()) } throws IllegalStateException("no vision")
    stubIdeogram()

    assertContentEquals(withCarl, generator.generate(request()))

    assertFalse(prompts.single().contains("hooded cloak"))
    assertFalse(prompts.single().contains("Carl"))
    assertEquals(carlLook2.references, references.single())
  }

  @Test
  fun `the vision box replaces the planned box when it is a sane size`() = runTest {
    coEvery { framePlanner.plan(any(), any(), any()) } returns framePlan(plannedCharacter(character = "Carl"))
    coEvery { geminiClient.askJson(any(), any(), any()) } returns """{"box": [0.4, 0.15, 0.9, 0.9]}"""
    stubIdeogram()

    assertContentEquals(withCarl, generator.generate(request()))

    val mask = ImageIO.read(ByteArrayInputStream(masks.single()))
    assertEquals(0, mask.raster.getSample((FRAME_WIDTH * 0.65).toInt(), (FRAME_HEIGHT * 0.5).toInt(), 0))
    assertEquals(255, mask.raster.getSample((FRAME_WIDTH * 0.2).toInt(), (FRAME_HEIGHT * 0.5).toInt(), 0))
  }

  @Test
  fun `a whole-frame vision answer is ignored and the planned box is used`() = runTest {
    coEvery { framePlanner.plan(any(), any(), any()) } returns framePlan(plannedCharacter(character = "Carl", box = listOf(0.1, 0.2, 0.6, 0.95)))
    coEvery { geminiClient.askJson(any(), any(), any()) } returns """{"box": [0, 0, 1, 1]}"""
    stubIdeogram()

    assertContentEquals(withCarl, generator.generate(request()))

    val mask = ImageIO.read(ByteArrayInputStream(masks.single()))
    assertEquals(255, mask.raster.getSample((FRAME_WIDTH * 0.8).toInt(), (FRAME_HEIGHT * 0.5).toInt(), 0))
    assertEquals(0, mask.raster.getSample((FRAME_WIDTH * 0.3).toInt(), (FRAME_HEIGHT * 0.5).toInt(), 0))
  }

  @Test
  fun `a frame with no characters is just the plate`() = runTest {
    coEvery { framePlanner.plan(any(), any(), any()) } returns framePlan()
    stubIdeogram()

    assertContentEquals(plate, generator.generate(request()))
    assertTrue(prompts.isEmpty())
  }

  @Test
  fun `a failing Ideogram call fails the whole frame`() = runTest {
    coEvery { framePlanner.plan(any(), any(), any()) } returns framePlan()
    coEvery { ideogramClient.generatePlate(any(), any()) } throws IllegalStateException("402")

    val result = runCatching { generator.generate(request()) }

    assertTrue(result.isFailure)
  }

  @Test
  fun `box parsing accepts fenced JSON and rejects anything else`() {
    assertEquals(listOf(0.1, 0.2, 0.3, 0.4), FrameGenerator.parseBox("```json\n{\"box\": [0.1, 0.2, 0.3, 0.4]}\n```"))
    assertNull(FrameGenerator.parseBox("""{"box": [0.1, 0.2]}"""))
    assertNull(FrameGenerator.parseBox("""{"box": ["a", "b", "c", "d"]}"""))
    assertNull(FrameGenerator.parseBox("nope"))
  }
}

private fun fakeBook(): Book {
  val chapterId = ChapterId(Uuid.random().toString())
  val chapter = Chapter(
    id = chapterId,
    name = "Chapter 1",
    duration = 60 * 60 * 1000L,
    fileLastModified = Instant.EPOCH,
    fileSize = 0,
    markData = emptyList(),
  )
  return Book(
    content = BookContent(
      id = BookId(Uuid.random().toString()),
      playbackSpeed = 1f,
      skipSilence = false,
      isActive = true,
      lastPlayedAt = Instant.EPOCH,
      author = null,
      name = "Dungeon Crawler Carl",
      addedAt = Instant.EPOCH,
      chapters = listOf(chapterId),
      currentChapter = chapterId,
      positionInChapter = 5 * 60 * 1000L,
      cover = null,
      gain = 0f,
      genre = null,
      narrator = null,
      series = null,
      part = null,
    ),
    chapters = listOf(chapter),
  )
}
