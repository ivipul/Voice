package voice.core.playback.di

import androidx.media3.session.CommandButton
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import voice.core.playback.session.CustomCommand
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class MediaButtonPreferencesTest {

  private val buttons = mediaButtonPreferences(ApplicationProvider.getApplicationContext())

  @Test
  fun `layout is Rewind, Ask, Snip, Forward`() {
    assertEquals(
      expected = listOf(
        CommandButton.SLOT_BACK,
        CommandButton.SLOT_FORWARD,
        CommandButton.SLOT_FORWARD_SECONDARY,
        CommandButton.SLOT_OVERFLOW,
      ),
      actual = buttons.map { it.slots[0] },
    )
    assertEquals(
      expected = listOf(
        null,
        CustomCommand.COPILOT_ASK_ACTION,
        CustomCommand.COPILOT_SNIP_ACTION,
        null,
      ),
      actual = buttons.map { it.sessionCommand?.customAction },
    )
  }

  @Test
  fun `Forward is the last button`() {
    assertEquals(
      expected = androidx.media3.common.Player.COMMAND_SEEK_FORWARD,
      actual = buttons.last().playerCommand,
    )
  }
}
