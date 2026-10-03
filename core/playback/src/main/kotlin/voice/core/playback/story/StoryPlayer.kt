package voice.core.playback.story

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import voice.core.data.Chapter
import voice.core.data.toUri
import voice.core.playback.player.OnlyAudioRenderersFactory

/**
 * Plays the audio of a Strip story on its own ExoPlayer, separate from the book's player: it has no media
 * session or notification, and it never touches the book's saved position. Create it when the story opens and
 * call [stop] when it closes. Positions are in book time (the sum of the earlier files' durations plus the
 * position in the file), the same clock the Strips' frame timestamps use.
 */
@Inject
class StoryPlayer(private val context: Context) {

  data class State(
    val bookMs: Long,
    val isPlaying: Boolean,
  )

  private var player: ExoPlayer? = null
  private var chapters: List<Chapter> = emptyList()

  /** The story audio's position and play state, null while no story is playing. */
  val state: Flow<State?> = flow {
    while (true) {
      emit(snapshot())
      delay(POSITION_POLL_MS)
    }
  }

  fun start(
    chapters: List<Chapter>,
    bookMs: Long,
  ) {
    stop()
    val (index, positionInFile) = chapters.fileLocationOf(bookMs) ?: return
    this.chapters = chapters
    player = newPlayer().apply {
      setMediaItems(chapters.map { MediaItem.fromUri(it.id.toUri()) }, index, positionInFile)
      prepare()
      playWhenReady = true
    }
  }

  fun seekTo(bookMs: Long) {
    val (index, positionInFile) = chapters.fileLocationOf(bookMs) ?: return
    player?.seekTo(index, positionInFile)
  }

  fun togglePlaying() {
    player?.let { it.playWhenReady = !it.playWhenReady }
  }

  fun stop() {
    player?.release()
    player = null
  }

  private fun newPlayer(): ExoPlayer {
    val mediaSourceFactory = DefaultMediaSourceFactory(
      DefaultDataSource.Factory(context),
      DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true),
    )
    return ExoPlayer.Builder(context, OnlyAudioRenderersFactory(context), mediaSourceFactory)
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
          .setUsage(C.USAGE_MEDIA)
          .build(),
        true,
      )
      .setHandleAudioBecomingNoisy(true)
      .setWakeMode(C.WAKE_MODE_LOCAL)
      .build()
  }

  private fun snapshot(): State? {
    val player = player ?: return null
    return State(
      bookMs = chapters.bookMsOf(player.currentMediaItemIndex, player.currentPosition.coerceAtLeast(0L)),
      isPlaying = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
    )
  }

  private companion object {
    const val POSITION_POLL_MS = 200L
  }
}
