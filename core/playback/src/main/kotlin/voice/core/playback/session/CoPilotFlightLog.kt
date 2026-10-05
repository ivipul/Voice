package voice.core.playback.session

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * A small on-device record of what each spoken co-pilot flow did (`files/copilot-flight.log`),
 * because logcat only keeps a couple of minutes on a phone and a flow that fails away from a
 * computer otherwise leaves no trace. Read it with
 * `adb shell run-as <package> cat files/copilot-flight.log`.
 */
internal class CoPilotFlightLog(context: Context) {

  private val file = File(context.filesDir, "copilot-flight.log")
  private val executor = Executors.newSingleThreadExecutor { Thread(it, "copilot-flight-log").apply { isDaemon = true } }
  private val format = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

  fun log(line: String) {
    val stamped = "${format.format(Date())} $line\n"
    executor.execute {
      try {
        if (file.length() > MAX_BYTES) file.writeText(file.readText().takeLast(MAX_BYTES / 2).substringAfter('\n'))
        file.appendText(stamped)
      } catch (_: Exception) {
        // The log must never get in the way of the flow.
      }
    }
  }

  private companion object {
    const val MAX_BYTES = 128 * 1024
  }
}
