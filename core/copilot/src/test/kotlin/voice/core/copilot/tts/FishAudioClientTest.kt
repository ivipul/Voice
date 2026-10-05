package voice.core.copilot.tts

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FishAudioClientTest {

  private val audio = byteArrayOf(0x49, 0x44, 0x33, 1, 2, 3)

  private fun client(vararg codes: Int, body: ByteArray = audio, calls: MutableList<Int> = mutableListOf()): OkHttpClient {
    var i = 0
    return OkHttpClient.Builder().addInterceptor(
      Interceptor { chain ->
        val code = codes[minOf(i++, codes.lastIndex)]
        calls += code
        Response.Builder()
          .request(chain.request())
          .protocol(Protocol.HTTP_1_1)
          .code(code)
          .message("m")
          .body((if (code in 200..299) body else "err".toByteArray()).toResponseBody("audio/mpeg".toMediaType()))
          .build()
      },
    ).build()
  }

  private val request = FishAudioClient.buildRequest("secret", "Hello crawler")

  @Test
  fun `request targets tts with bearer key, free model header, the voice id and mp3`() {
    assertEquals("https://api.fish.audio/v1/tts", request.url.toString())
    assertEquals("Bearer secret", request.header("Authorization"))
    assertEquals("s2.1-pro-free", request.header("model"))
    val json = okio.Buffer().also { request.body!!.writeTo(it) }.readUtf8()
    assertTrue(json.contains("\"text\":\"Hello crawler\""))
    assertTrue(json.contains("\"reference_id\":\"ad2dc11f1eaf4f6eb1ca4dd9d01ec6eb\""))
    assertTrue(json.contains("\"format\":\"mp3\""))
  }

  @Test
  fun `success returns the audio bytes`() {
    assertContentEquals(audio, FishAudioClient.fetchAudio(client(200), request, 0))
  }

  @Test
  fun `a 5xx is retried once then succeeds`() {
    val calls = mutableListOf<Int>()
    assertContentEquals(audio, FishAudioClient.fetchAudio(client(503, 200, calls = calls), request, 0))
    assertEquals(listOf(503, 200), calls)
  }

  @Test
  fun `a 429 that keeps failing throws after two attempts`() {
    val calls = mutableListOf<Int>()
    assertFailsWith<IllegalStateException> { FishAudioClient.fetchAudio(client(429, calls = calls), request, 0) }
    assertEquals(listOf(429, 429), calls)
  }

  @Test
  fun `out of credit is not retried and fails`() {
    val calls = mutableListOf<Int>()
    val error = assertFailsWith<IllegalStateException> { FishAudioClient.fetchAudio(client(402, calls = calls), request, 0) }
    assertEquals(listOf(402), calls)
    assertTrue(error.message!!.contains("402"))
  }

  @Test
  fun `an empty body is an error`() {
    assertFailsWith<IllegalStateException> { FishAudioClient.fetchAudio(client(200, body = ByteArray(0)), request, 0) }
  }
}
