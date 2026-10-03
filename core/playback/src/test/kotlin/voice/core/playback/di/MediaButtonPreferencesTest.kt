package voice.core.playback.di

import androidx.media3.common.Player
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
  fun `Ask and Snip take the left slots, Recap, rewind and forward the right group`() {
    assertEquals(
      expected = listOf(
        CommandButton.SLOT_BACK,
        CommandButton.SLOT_FORWARD,
        CommandButton.SLOT_OVERFLOW,
        CommandButton.SLOT_OVERFLOW,
        CommandButton.SLOT_OVERFLOW,
      ),
      actual = buttons.map { it.slots[0] },
    )
    assertEquals(
      expected = listOf(
        CustomCommand.COPILOT_ASK_ACTION,
        CustomCommand.COPILOT_SNIP_ACTION,
        CustomCommand.COPILOT_RECAP_ACTION,
        null,
        null,
      ),
      actual = buttons.map { it.sessionCommand?.customAction },
    )
  }

  @Test
  fun `rewind is 15 seconds and forward is 30 seconds, after Recap`() {
    assertEquals(CommandButton.ICON_SKIP_BACK_15, buttons[3].icon)
    assertEquals(Player.COMMAND_SEEK_BACK, buttons[3].playerCommand)
    assertEquals(CommandButton.ICON_SKIP_FORWARD_30, buttons[4].icon)
    assertEquals(Player.COMMAND_SEEK_FORWARD, buttons[4].playerCommand)
  }
}
