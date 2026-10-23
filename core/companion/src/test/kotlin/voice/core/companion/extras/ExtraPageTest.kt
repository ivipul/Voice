package voice.core.companion.extras

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtraPageTest {

  private val page = "<!doctype html><html><head><title>x</title></head><body><p>Hi</p></body></html>"

  @Test
  fun thePageIsTakenOutOfAFencedReply() {
    assertEquals(page, validated("Here you go:\n```html\n$page\n```\nEnjoy!"))
  }

  @Test
  fun pagesThatLoadFromTheInternetAreRejected() {
    val external = page.replace("<title>x</title>", "<script src=\"https://cdn.example.com/lib.js\"></script>")
    assertFailsWith<IllegalStateException> { validated(external) }
  }

  @Test
  fun aPageNamesItsTopicInAMetaTag() {
    assertEquals("compound-interest", topicOf("<head><meta name=\"earwig-topic\" content=\"compound-interest\"></head>"))
    assertNull(topicOf("<head><meta name='earwig-topic' content='none'></head>"))
    assertNull(topicOf(page))
  }

  @Test
  fun theSandboxThemeAndBridgeGoFirstInTheHead() {
    val theme = ExtraTheme(
      background = 0xFFEDE4CA.toInt(),
      card = 0xFFFFFFFF.toInt(),
      ink = 0xFF17171C.toInt(),
      muted = 0xFF6B6B6B.toInt(),
      accent = 0xFFD62828.toInt(),
      accentInk = 0xFFFFF4EE.toInt(),
      displayFont = "sans-serif-condensed",
      bodyFont = "sans-serif",
    )

    val wrapped = wrapExtraPage(page, theme)

    assertTrue(wrapped.startsWith("<!doctype html><html><head><meta http-equiv=\"Content-Security-Policy\""))
    assertTrue("--bg:#EDE4CA" in wrapped)
    assertTrue("window.earwig" in wrapped)
    assertTrue(wrapped.indexOf("window.earwig") < wrapped.indexOf("<title>x</title>"))
  }

  @Test
  fun aBareFragmentGetsAWholeDocument() {
    assertTrue(wrapExtraPage("<p>Hi</p>", themeOf()).endsWith("<body><p>Hi</p></body></html>"))
  }

  private fun themeOf() = ExtraTheme(0, 0, 0, 0, 0, 0, "serif", "serif")
}
