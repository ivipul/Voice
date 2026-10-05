package voice.core.playback.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import voice.core.copilot.tts.FishAudioClient
import voice.core.copilot.tts.SpeechChunker
import voice.core.logging.api.Logger
import java.io.File
import kotlin.coroutines.resume
import androidx.media3.common.AudioAttributes as MediaAudioAttributes

/** The message being read aloud. It is not [audible] yet while its first words are still being prepared. */
data class SpokenMessage(
  val id: String,
  val audible: Boolean,
)

/**
 * Reads one written co-pilot message aloud on request, in the System AI's voice, falling back to the phone's own
 * voice when that is not available. It holds transient audio focus while it speaks, so a playing book pauses and
 * then carries on, and it stops as soon as anything else takes the focus. Call it from the main thread.
 */
@SingleIn(AppScope::class)
@Inject
class MessageSpeaker(
  private val context: Context,
  private val fishAudioClient: FishAudioClient,
) {

  private val scope = MainScope()
  private val audioManager = context.getSystemService(AudioManager::class.java)
  private var current: Utterance? = null
  private var focusRequest: AudioFocusRequest? = null

  val speaking: StateFlow<SpokenMessage?>
    field = MutableStateFlow<SpokenMessage?>(null)

  /** Starts reading [text] aloud as the message [id], replacing whatever was being read. */
  fun speak(id: String, text: String) {
    stop()
    val utterance = Utterance(id)
    current = utterance
    speaking.value = SpokenMessage(id, audible = false)
    requestAudioFocus()
    utterance.job = scope.launch {
      try {
        val chunks = SpeechChunker.split(text)
        val played = if (fishAudioClient.isConfigured) utterance.playWithFish(chunks) else 0
        val rest = chunks.drop(played).joinToString(" ")
        if (rest.isNotBlank()) utterance.playWithPhoneVoice(rest)
      } finally {
        utterance.release()
        if (current === utterance) {
          current = null
          speaking.value = null
          abandonAudioFocus()
        }
      }
    }
  }

  fun stop() {
    val utterance = current ?: return
    current = null
    speaking.value = null
    utterance.job?.cancel()
    utterance.release()
    abandonAudioFocus()
  }

  private fun onAudible(utterance: Utterance) {
    scope.launch {
      if (current === utterance) speaking.value = SpokenMessage(utterance.id, audible = true)
    }
  }

  /** Plays the chunks in order as their audio arrives and returns how many of them were spoken in full. */
  private suspend fun Utterance.playWithFish(chunks: List<String>): Int = coroutineScope {
    val fetches = chunks.map { chunk -> async(Dispatchers.IO) { fetch(chunk) } }
    val exo = ExoPlayer.Builder(context)
      .setAudioAttributes(
        MediaAudioAttributes.Builder()
          .setUsage(C.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
          .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
          .build(),
        // The speaker already holds audio focus for the whole message.
        false,
      )
      .build()
    player = exo
    val ended = CompletableDeferred<Boolean>()
    var allQueued = false
    exo.addListener(
      object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
          if (isPlaying) onAudible(this@playWithFish)
        }

        override fun onPlaybackStateChanged(state: Int) {
          if (state == Player.STATE_ENDED && allQueued) ended.complete(true)
        }

        override fun onPlayerError(error: PlaybackException) {
          Logger.w(error, "MessageSpeaker: playback failed")
          ended.complete(false)
        }
      },
    )
    var queued = 0
    for (fetch in fetches) {
      val file = fetch.await() ?: break
      files += file
      exo.addMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
      if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(exo.mediaItemCount - 1, 0)
      if (exo.playbackState == Player.STATE_IDLE) exo.prepare()
      exo.playWhenReady = true
      queued++
    }
    // Only when a chunk failed is anything still running here; the phone's voice takes over from that chunk.
    fetches.forEach { it.cancel() }
    if (queued == 0) return@coroutineScope 0
    allQueued = true
    if (exo.playbackState == Player.STATE_ENDED) ended.complete(true)
    if (ended.await()) queued else exo.currentMediaItemIndex
  }

  private suspend fun fetch(chunk: String): File? {
    val file = File.createTempFile("message-tts", ".wav", context.cacheDir)
    return try {
      fishAudioClient.synthesizeTo(chunk, file)
      file
    } catch (e: CancellationException) {
      file.delete()
      throw e
    } catch (e: Throwable) {
      file.delete()
      Logger.w(e, "MessageSpeaker: Fish chunk failed")
      null
    }
  }

  private suspend fun Utterance.playWithPhoneVoice(text: String) = suspendCancellableCoroutine { cont ->
    tts = TextToSpeech(context) { status ->
      val engine = tts
      if (status != TextToSpeech.SUCCESS || engine == null) {
        if (cont.isActive) cont.resume(Unit)
        return@TextToSpeech
      }
      engine.setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
          .build(),
      )
      engine.setOnUtteranceProgressListener(
        object : UtteranceProgressListener() {
          override fun onStart(utteranceId: String?) {
            onAudible(this@playWithPhoneVoice)
          }

          override fun onDone(utteranceId: String?) {
            if (cont.isActive) cont.resume(Unit)
          }

          @Deprecated("Deprecated in Java")
          override fun onError(utteranceId: String?) {
            if (cont.isActive) cont.resume(Unit)
          }
        },
      )
      engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "message")
    }
  }

  private fun requestAudioFocus() {
    val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
          .build(),
      )
      .setOnAudioFocusChangeListener { change ->
        if (change < 0) scope.launch { stop() }
      }
      .build()
    focusRequest = request
    audioManager.requestAudioFocus(request)
  }

  private fun abandonAudioFocus() {
    focusRequest?.let(audioManager::abandonAudioFocusRequest)
    focusRequest = null
  }

  /** One message being read: everything it holds is let go together, whether it finished or was stopped. */
  private class Utterance(val id: String) {
    var job: Job? = null
    var player: ExoPlayer? = null
    var tts: TextToSpeech? = null
    val files = mutableListOf<File>()

    fun release() {
      player?.release()
      player = null
      tts?.stop()
      tts?.shutdown()
      tts = null
      files.forEach { it.delete() }
      files.clear()
    }
  }
}
