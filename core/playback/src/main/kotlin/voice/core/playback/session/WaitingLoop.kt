package voice.core.playback.session

import android.content.Context
import android.net.Uri
import android.os.Handler
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import voice.core.playback.R

/** The platform side of the waiting loop; every call happens on the main thread. */
internal interface WaitingLoopPlayback {
  fun start()
  fun fadeOutAndRelease()
  fun releaseNow()
}

/**
 * The state of the "System AI is thinking" loop: started when a spoken flow begins waiting for
 * its answer, stopped with a short fade when the first spoken audio starts, or at once on any
 * failure, finish or interrupt. Starting twice or stopping when idle does nothing, so callers
 * don't have to track whether it is playing.
 */
internal class WaitingLoop(private val playback: WaitingLoopPlayback) {

  private var running = false

  val isRunning: Boolean get() = running

  fun start() {
    if (running) return
    running = true
    playback.start()
  }

  fun stopWithFade() {
    if (!running) return
    running = false
    playback.fadeOutAndRelease()
  }

  fun stopNow() {
    if (!running) return
    running = false
    playback.releaseNow()
  }
}

/** Loops the bundled clip at low volume under the engine's audio focus, as assistant audio. */
internal class ExoWaitingLoopPlayback(
  private val context: Context,
  private val handler: Handler,
) : WaitingLoopPlayback {

  private var player: ExoPlayer? = null
  private var fade: Runnable? = null

  override fun start() {
    releaseNow()
    val exo = ExoPlayer.Builder(context)
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(C.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
          .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
          .build(),
        // The engine already holds audio focus for the whole round trip.
        false,
      )
      .build()
    exo.repeatMode = Player.REPEAT_MODE_ONE
    exo.volume = 0f
    exo.setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/${R.raw.copilot_waiting_loop}")))
    exo.prepare()
    exo.playWhenReady = true
    player = exo
    ramp(from = 0f, to = LOOP_VOLUME, durationMs = FADE_IN_MS) {}
  }

  override fun fadeOutAndRelease() {
    val exo = player ?: return
    ramp(from = exo.volume, to = 0f, durationMs = FADE_OUT_MS) { releaseNow() }
  }

  override fun releaseNow() {
    fade?.let(handler::removeCallbacks)
    fade = null
    player?.release()
    player = null
  }

  private fun ramp(from: Float, to: Float, durationMs: Long, onEnd: () -> Unit) {
    fade?.let(handler::removeCallbacks)
    val steps = (durationMs / STEP_MS).toInt().coerceAtLeast(1)
    var step = 0
    val runnable = object : Runnable {
      override fun run() {
        val exo = player ?: return
        step++
        exo.volume = from + (to - from) * step / steps
        if (step < steps) handler.postDelayed(this, STEP_MS) else {
          fade = null
          onEnd()
        }
      }
    }
    fade = runnable
    handler.post(runnable)
  }

  private companion object {
    const val LOOP_VOLUME = 0.35f
    const val FADE_IN_MS = 150L
    const val FADE_OUT_MS = 200L
    const val STEP_MS = 20L
  }
}
