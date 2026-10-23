package voice.core.companion.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import voice.core.ai.MissingApiKeyException
import voice.core.common.rootGraphAs
import voice.core.data.BookId
import voice.core.logging.api.Logger
import voice.core.strings.R as StringsR

@ContributesTo(AppScope::class)
interface CompanionWorkerGraph {
  val bookSetupRunner: BookSetupRunner
}

/**
 * Runs one [SetupJob] in the foreground, so a transcription of many hours keeps going with the screen off. The work
 * itself lives in [BookSetupRunner]; this only adapts it to WorkManager.
 */
class CompanionWorker(
  context: Context,
  params: WorkerParameters,
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val job = inputData.getString(KEY_JOB)?.let { name -> SetupJob.entries.firstOrNull { it.name == name } } ?: return Result.failure()
    val bookId = inputData.getString(KEY_BOOK)?.let(::BookId) ?: return Result.failure()
    runCatching { setForeground(getForegroundInfo()) }
      .onFailure { Logger.w(it, "Could not run setup in the foreground") }
    return try {
      rootGraphAs<CompanionWorkerGraph>().bookSetupRunner.run(job, bookId, inputData.getString(KEY_CARD))
      Result.success()
    } catch (e: MissingApiKeyException) {
      Result.failure()
    } catch (e: Exception) {
      Logger.w(e, "Setup job $job failed")
      Result.failure()
    }
  }

  override suspend fun getForegroundInfo(): ForegroundInfo {
    val manager = applicationContext.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL) == null) {
      manager.createNotificationChannel(
        NotificationChannel(CHANNEL, applicationContext.getString(StringsR.string.setup_notification_channel), NotificationManager.IMPORTANCE_LOW),
      )
    }
    val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle(applicationContext.getString(StringsR.string.setup_notification_title))
      .setOngoing(true)
      .setSilent(true)
      .build()
    return if (Build.VERSION.SDK_INT >= 29) {
      ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
      ForegroundInfo(NOTIFICATION_ID, notification)
    }
  }

  companion object {
    const val KEY_JOB = "job"
    const val KEY_BOOK = "book"
    const val KEY_CARD = "card"
    const val TAG = "earwig-setup"
    private const val CHANNEL = "earwig-setup"
    private const val NOTIFICATION_ID = 7_301
  }
}
