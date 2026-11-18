package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AiShortProject
import com.example.data.model.ClipJob
import com.example.data.model.GeneratedClip
import com.example.data.model.UgcAvatar
import com.example.data.model.YoutubeStudioProject
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class OpenShortsFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun unwrapFirestoreCatch(error: Throwable, path: String, uid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
        throw error
    }

    // --- Clip Jobs & Generated Clips ---

    fun observeClipJobs(userId: String): Flow<List<ClipJob>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user changed; refusing cross-user read." }
        val path = "users/$uid/clip_jobs"
        emitAll(
            db.collection("users").document(uid).collection("clip_jobs")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ClipJob::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error -> unwrapFirestoreCatch(error, path, uid) }
        )
    }

    fun observeClipsForJob(userId: String, jobId: String): Flow<List<GeneratedClip>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user changed; refusing cross-user read." }
        require(jobId.isNotBlank()) { "Job ID cannot be blank" }
        val path = "users/$uid/clip_jobs/$jobId/clips"
        emitAll(
            db.collection("users").document(uid)
                .collection("clip_jobs").document(jobId)
                .collection("clips")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        GeneratedClip::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.clipIndex }
                }
                .catch { error -> unwrapFirestoreCatch(error, path, uid) }
        )
    }

    suspend fun createClipJobWithClips(
        userId: String,
        job: ClipJob,
        clips: List<GeneratedClip>
    ): Result<String> {
        var docPath = "users/$userId/clip_jobs"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val jobRef = db.collection("users").document(uid).collection("clip_jobs").document()
            docPath = jobRef.path

            val cleanJob = job.copy(
                userId = uid,
                status = "completed",
                clipsCount = clips.size.toLong()
            )
            jobRef.set(cleanJob.toCreateMap()).await()

            clips.forEachIndexed { index, clip ->
                val clipRef = jobRef.collection("clips").document()
                val cleanClip = clip.copy(
                    userId = uid,
                    jobId = jobRef.id,
                    clipIndex = index.toLong()
                )
                clipRef.set(cleanClip.toCreateMap()).await()
            }
            Result.success(jobRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateClipSubtitles(
        userId: String,
        jobId: String,
        clipId: String,
        subtitlePosition: String,
        subtitleAnimation: String,
        subtitleFont: String,
        subtitleColorHex: String,
        subtitleHighlightHex: String,
        subtitleText: String
    ): Result<Unit> {
        val docPath = "users/$userId/clip_jobs/$jobId/clips/$clipId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            require(jobId.isNotBlank() && clipId.isNotBlank())
            db.document(docPath).update(
                mapOf(
                    "subtitlePosition" to if (subtitlePosition in listOf("top", "middle", "bottom")) subtitlePosition else "bottom",
                    "subtitleAnimation" to if (subtitleAnimation in listOf("pop", "word-highlight", "karaoke", "none")) subtitleAnimation else "word-highlight",
                    "subtitleFont" to subtitleFont.take(40),
                    "subtitleColorHex" to subtitleColorHex.take(12),
                    "subtitleHighlightHex" to subtitleHighlightHex.take(12),
                    "subtitleText" to subtitleText.take(3000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateClipHook(
        userId: String,
        jobId: String,
        clipId: String,
        viralHookText: String,
        hookPosition: String,
        hookSize: String,
        hookAnimation: String,
        hookDurationSec: Long
    ): Result<Unit> {
        val docPath = "users/$userId/clip_jobs/$jobId/clips/$clipId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            require(jobId.isNotBlank() && clipId.isNotBlank())
            db.document(docPath).update(
                mapOf(
                    "viralHookText" to viralHookText.take(160),
                    "hookPosition" to if (hookPosition in listOf("top", "center", "bottom")) hookPosition else "top",
                    "hookSize" to if (hookSize in listOf("S", "M", "L")) hookSize else "M",
                    "hookAnimation" to if (hookAnimation in listOf("spring", "fade", "slide-up", "none")) hookAnimation else "spring",
                    "hookDurationSec" to hookDurationSec.coerceIn(1L, 60L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateClipTranslation(
        userId: String,
        jobId: String,
        clipId: String,
        translatedLanguage: String,
        subtitleText: String,
        viralHookText: String,
        youtubeTitle: String,
        tiktokCaption: String
    ): Result<Unit> {
        val docPath = "users/$userId/clip_jobs/$jobId/clips/$clipId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            require(jobId.isNotBlank() && clipId.isNotBlank())
            db.document(docPath).update(
                mapOf(
                    "translatedLanguage" to translatedLanguage.take(30),
                    "subtitleText" to subtitleText.take(3000),
                    "viralHookText" to viralHookText.take(160),
                    "youtubeTitle" to youtubeTitle.ifBlank { "Translated Short" }.take(150),
                    "tiktokCaption" to tiktokCaption.take(1000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteClipJob(userId: String, jobId: String): Result<Unit> {
        val docPath = "users/$userId/clip_jobs/$jobId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user delete." }
            val clipsSnap = db.collection("users").document(uid)
                .collection("clip_jobs").document(jobId)
                .collection("clips").get().await()
            for (doc in clipsSnap.documents) {
                doc.reference.delete().await()
            }
            db.document(docPath).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- AI Shorts Projects ---

    fun observeAiShorts(userId: String): Flow<List<AiShortProject>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user changed; refusing cross-user read." }
        val path = "users/$uid/ai_shorts"
        emitAll(
            db.collection("users").document(uid).collection("ai_shorts")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AiShortProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error -> unwrapFirestoreCatch(error, path, uid) }
        )
    }

    suspend fun createAiShort(userId: String, project: AiShortProject): Result<String> {
        var docPath = "users/$userId/ai_shorts"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val docRef = db.collection("users").document(uid).collection("ai_shorts").document()
            docPath = docRef.path
            docRef.set(project.copy(userId = uid).toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAiShort(userId: String, shortId: String): Result<Unit> {
        val docPath = "users/$userId/ai_shorts/$shortId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.document(docPath).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- YouTube Studio Projects ---

    fun observeYoutubeProjects(userId: String): Flow<List<YoutubeStudioProject>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user changed; refusing cross-user read." }
        val path = "users/$uid/youtube_projects"
        emitAll(
            db.collection("users").document(uid).collection("youtube_projects")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        YoutubeStudioProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error -> unwrapFirestoreCatch(error, path, uid) }
        )
    }

    suspend fun saveYoutubeProject(userId: String, project: YoutubeStudioProject): Result<String> {
        var docPath = "users/$userId/youtube_projects"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val collection = db.collection("users").document(uid).collection("youtube_projects")
            if (project.id.isBlank()) {
                val docRef = collection.document()
                docPath = docRef.path
                docRef.set(project.copy(userId = uid).toCreateMap()).await()
                Result.success(docRef.id)
            } else {
                val docRef = collection.document(project.id)
                docPath = docRef.path
                val updatePayload = mapOf(
                    "selectedTitle" to project.selectedTitle.ifBlank { "Untitled Video" }.take(120),
                    "suggestedTitles" to project.suggestedTitles.take(15),
                    "description" to project.description?.take(5000),
                    "thumbnailPrompt" to project.thumbnailPrompt?.take(1000),
                    "thumbnailPath" to project.thumbnailPath?.take(500),
                    "publishedStatus" to if (project.publishedStatus in listOf("draft", "published")) project.publishedStatus else "draft",
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }.mapValues { it.value!! }
                docRef.update(updatePayload).await()
                Result.success(project.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteYoutubeProject(userId: String, projectId: String): Result<Unit> {
        val docPath = "users/$userId/youtube_projects/$projectId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.document(docPath).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- UGC Avatars ---

    fun observeAvatars(userId: String): Flow<List<UgcAvatar>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user changed; refusing cross-user read." }
        val path = "users/$uid/avatars"
        emitAll(
            db.collection("users").document(uid).collection("avatars")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UgcAvatar::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error -> unwrapFirestoreCatch(error, path, uid) }
        )
    }

    suspend fun createAvatar(userId: String, avatar: UgcAvatar): Result<String> {
        var docPath = "users/$userId/avatars"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val docRef = db.collection("users").document(uid).collection("avatars").document()
            docPath = docRef.path
            docRef.set(avatar.copy(userId = uid).toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAvatar(userId: String, avatarId: String): Result<Unit> {
        val docPath = "users/$userId/avatars/$avatarId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.document(docPath).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
