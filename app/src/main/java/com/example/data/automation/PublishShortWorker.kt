package com.example.data.automation

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.network.ExternalIntegrationsService
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.OpenShortsFirestoreRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val TAG = "PublishShortWorker"

class PublishShortWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID).orEmpty()
        val jobId = inputData.getString(KEY_JOB_ID).orEmpty()
        if (userId.isBlank() || jobId.isBlank()) {
            return Result.failure()
        }

        val currentUid = Firebase.auth.currentUser?.uid
        if (currentUid != userId) {
            Log.w(TAG, "Skipping worker execution: signed-in user mismatch.")
            return Result.failure()
        }

        val db = FirebaseFirestore.getInstance(
            applicationContext.getString(R.string.firestore_database_id)
        )
        val firestoreRepo = OpenShortsFirestoreRepository(db)
        val settingsStore = SettingsDataStore(applicationContext)
        val integrationsService = ExternalIntegrationsService()

        return try {
            val jobResult = firestoreRepo.getPublishJob(userId, jobId)
            val job = jobResult.getOrNull() ?: return Result.failure()

            firestoreRepo.updatePublishJobStatus(
                userId = userId,
                jobId = jobId,
                status = "uploading",
                resultMessage = "Uploading short video with title & captions..."
            )

            val settings = settingsStore.settingsFlow.first()
            val publishResult = integrationsService.publishShort(
                context = applicationContext,
                settings = settings,
                job = job
            )

            publishResult.fold(
                onSuccess = { summary ->
                    firestoreRepo.updatePublishJobStatus(
                        userId = userId,
                        jobId = jobId,
                        status = "published",
                        resultMessage = summary
                    )
                    Result.success()
                },
                onFailure = { error ->
                    val message = error.localizedMessage ?: "Publishing failed."
                    firestoreRepo.updatePublishJobStatus(
                        userId = userId,
                        jobId = jobId,
                        status = "failed",
                        resultMessage = message
                    )
                    Result.failure()
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Publish worker crashed for job $jobId", e)
            firestoreRepo.updatePublishJobStatus(
                userId = userId,
                jobId = jobId,
                status = "failed",
                resultMessage = e.localizedMessage ?: "Unexpected publish error."
            )
            Result.failure()
        }
    }

    companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_JOB_ID = "job_id"
    }
}
