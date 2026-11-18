package com.example.data.automation

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Instant
import java.util.concurrent.TimeUnit

class PublishAutomationScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    fun enqueuePublishJob(
        userId: String,
        jobId: String,
        scheduledIso: String?
    ) {
        val delayMs = computeDelayMillis(scheduledIso)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<PublishShortWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    PublishShortWorker.KEY_USER_ID to userId,
                    PublishShortWorker.KEY_JOB_ID to jobId
                )
            )
            .addTag("openshorts_publish")
            .build()

        workManager.enqueueUniqueWork(
            uniqueWorkName(jobId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelPublishJob(jobId: String) {
        workManager.cancelUniqueWork(uniqueWorkName(jobId))
    }

    private fun uniqueWorkName(jobId: String): String = "publish_short_$jobId"

    private fun computeDelayMillis(scheduledIso: String?): Long {
        if (scheduledIso.isNullOrBlank()) return 0L
        return try {
            val targetEpochMs = Instant.parse(scheduledIso.trim()).toEpochMilli()
            (targetEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        }
    }
}
