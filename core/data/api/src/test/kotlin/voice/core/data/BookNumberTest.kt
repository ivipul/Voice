package voice.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookNumberTest {

  @Test
  fun readsLeadingNumber() {
    assertEquals(2, leadingBookNumber("2. Carl's Doomsday Scenario"))
    assertEquals(10, leadingBookNumber("10 - Some Book"))
    assertNull(leadingBookNumber("Dungeon Crawler Carl"))
    assertNull(leadingBookNumber("1984"))
  }

  @Test
  fun stripsLeadingNumber() {
    assertEquals("Carl's Doomsday Scenario", withoutBookNumber("2. Carl's Doomsday Scenario"))
    assertEquals("Dungeon Crawler Carl", withoutBookNumber("Dungeon Crawler Carl"))
  }

  @Test
  fun addsNumberOnlyOnce() {
    assertEquals("3. The Dungeon Anarchist's Cookbook", numberedBookName("The Dungeon Anarchist's Cookbook", 3))
    assertEquals("3. The Dungeon Anarchist's Cookbook", numberedBookName("3. The Dungeon Anarchist's Cookbook", 3))
    assertEquals("Standalone", numberedBookName("Standalone", null))
  }

  @Test
  fun prefersPartTagOverFileName() {
    assertEquals(4, resolveBookNumber(partTag = "4", fileName = "1. File"))
    assertEquals(4, resolveBookNumber(partTag = "4 of 8", fileName = null))
    assertEquals(1, resolveBookNumber(partTag = null, fileName = "1. Dungeon Crawler Carl.m4b"))
    assertNull(resolveBookNumber(partTag = null, fileName = "Dungeon Crawler Carl.m4b"))
  }
}
