package voice.core.copilot.frame

import okhttp3.MultipartBody
import kotlin.test.Test
import kotlin.test.assertEquals

class IdeogramClientTest {

  private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0, 0, 0, 0)
  private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0)

  private fun MultipartBody.fields(): List<String> = parts.map { part ->
    val disposition = part.headers!!["Content-Disposition"].orEmpty()
    Regex("name=\"([^\"]+)\"").find(disposition)!!.groupValues[1]
  }

  private fun MultipartBody.fileNames(): List<String> = parts.mapNotNull { part ->
    Regex("filename=\"([^\"]+)\"").find(part.headers!!["Content-Disposition"].orEmpty())?.groupValues?.get(1)
  }

  @Test
  fun `plate call sends the prompt, 736x1312, medium quality and every style image`() {
    val body = IdeogramClient.plateBody("a prompt", listOf(png, jpeg, png))

    assertEquals(listOf("prompt", "size", "quality", "images", "images", "images"), body.fields())
    assertEquals(listOf("style-0.png", "style-1.jpg", "style-2.png"), body.fileNames())
    val buffer = okio.Buffer().also { body.writeTo(it) }.readUtf8()
    assertEquals(true, buffer.contains("736x1312"))
    assertEquals(true, buffer.contains("medium"))
  }

  @Test
  fun `precise edit sends image, mask and each reference image`() {
    val body = IdeogramClient.editBody("add a man", image = png, mask = png, references = listOf(png, png))

    assertEquals(listOf("prompt", "quality", "image", "mask", "reference_images", "reference_images"), body.fields())
  }

  @Test
  fun `the output url is read from the first data entry`() {
    val url = IdeogramClient.parseImageUrl("""{"created":"x","data":[{"prompt":"p","url":"https://ideogram.ai/api/images/ephemeral/abc.png","seed":1}]}""")

    assertEquals("https://ideogram.ai/api/images/ephemeral/abc.png", url)
  }
}
