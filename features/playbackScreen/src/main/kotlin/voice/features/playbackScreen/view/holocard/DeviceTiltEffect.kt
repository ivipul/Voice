package voice.features.playbackScreen.view.holocard

import android.content.Context
import android.content.ContextWrapper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Drives [tilt] from the phone's orientation while this is in the composition and the app is resumed, so the card
 * turns as you turn the phone. The sensor is only registered while the card is on screen. Does nothing when
 * [enabled] is false or the phone has no rotation sensor.
 */
@Composable
internal fun DeviceTiltEffect(
  tilt: HoloTiltState,
  enabled: Boolean,
) {
  if (!enabled) return
  val context = LocalContext.current
  val view = LocalView.current
  DisposableEffect(tilt) {
    val manager = context.getSystemService(SensorManager::class.java)
    val sensor = manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
      ?: manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    if (manager == null || sensor == null) return@DisposableEffect onDispose { }

    var tracker = DeviceTiltTracker()
    var lastNanos = 0L
    val matrix = FloatArray(9)
    val listener = object : SensorEventListener {
      override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        val delta = if (lastNanos == 0L) 0f else (event.timestamp - lastNanos) / 1_000_000_000f
        lastNanos = event.timestamp
        val rotation = view.display?.rotation ?: Surface.ROTATION_0
        val target = tracker.update(upX = matrix[6], upY = matrix[7], displayRotation = rotation, deltaSeconds = delta)
        tilt.onDeviceTilt(rotationX = target.rotationX, rotationY = target.rotationY)
      }

      override fun onAccuracyChanged(
        sensor: Sensor,
        accuracy: Int,
      ) = Unit
    }

    fun register() {
      tracker = DeviceTiltTracker()
      lastNanos = 0L
      manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    val lifecycle = context.findLifecycleOwner()?.lifecycle
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> register()
        Lifecycle.Event.ON_PAUSE -> manager.unregisterListener(listener)
        else -> Unit
      }
    }
    if (lifecycle == null) {
      register()
    } else {
      lifecycle.addObserver(observer)
    }
    onDispose {
      manager.unregisterListener(listener)
      lifecycle?.removeObserver(observer)
    }
  }
}

private fun Context.findLifecycleOwner(): LifecycleOwner? {
  var current: Context? = this
  while (current != null) {
    if (current is LifecycleOwner) return current
    current = (current as? ContextWrapper)?.baseContext
  }
  return null
}
