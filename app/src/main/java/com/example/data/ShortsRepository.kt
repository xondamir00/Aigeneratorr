package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ShortsRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Firestore'ga murojaat qilishdan oldin Google hisobiga kiring.")
    }

    fun observeShorts(): Flow<List<HorrorShortProject>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/shorts"
        emitAll(
            db.collection("users").document(uid).collection("shorts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            HorrorShortProject::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeSchedule(): Flow<AutomationSchedule> = flow {
        val uid = requireUserId()
        val path = "users/$uid/settings/schedule"
        emitAll(
            db.collection("users").document(uid).collection("settings").document("schedule")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            AutomationSchedule::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: AutomationSchedule(userId = uid)
                    } else {
                        AutomationSchedule(userId = uid)
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getScheduleOnce(): Result<AutomationSchedule> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/settings/schedule"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("settings").document("schedule")
            docPath = ref.path
            val snap = ref.get().await()
            val schedule = if (snap.exists()) {
                snap.toObject(
                    AutomationSchedule::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: AutomationSchedule(userId = uid)
            } else {
                AutomationSchedule(userId = uid)
            }
            Result.success(schedule)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    suspend fun createShort(
        folkloreTheme: String,
        youtubeTitle: String,
        youtubeDescription: String,
        hashtags: List<String>,
        scenes: List<ShortScene>,
        status: ShortStatus = ShortStatus.GENERATING,
        localVideoPath: String? = null,
        localThumbPath: String? = null,
        durationSeconds: Long = 15L
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/shorts"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("shorts").document()
            docPath = docRef.path

            val safeTheme = folkloreTheme.trim().ifBlank { FolkloreCategory.FOREST_SPIRITS.id }.take(80)
            val safeTitle = youtubeTitle.trim().ifBlank { "Midnight Folklore #Shorts" }.take(100)
            val safeDesc = youtubeDescription.trim().ifBlank { "A 15-second mystical folklore horror short." }.take(1500)
            val safeTags = hashtags.map { it.trim() }.filter { it.isNotBlank() }.take(15)
            val safeScenes = scenes.take(6).map { it.toMap() }

            val payload = mutableMapOf<String, Any?>(
                "userId" to uid,
                "folkloreTheme" to safeTheme,
                "youtubeTitle" to safeTitle,
                "youtubeDescription" to safeDesc,
                "hashtags" to safeTags,
                "scenes" to safeScenes,
                "status" to status.name,
                "localVideoPath" to localVideoPath?.take(500),
                "localThumbPath" to localThumbPath?.take(500),
                "durationSeconds" to durationSeconds.coerceIn(5L, 60L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateShortSeo(
        shortId: String,
        youtubeTitle: String,
        youtubeDescription: String,
        hashtags: List<String>
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/shorts/$shortId"
        return try {
            val uid = requireUserId()
            require(shortId.isNotBlank()) { "Short ID bo'sh bo'lmasligi kerak." }
            val safeTitle = youtubeTitle.trim().take(100)
            val safeDesc = youtubeDescription.trim().take(1500)
            require(safeTitle.isNotBlank()) { "Sarlavha bo'sh bo'lmasligi kerak." }
            require(safeDesc.isNotBlank()) { "Tavsif bo'sh bo'lmasligi kerak." }
            val safeTags = hashtags.map { it.trim() }.filter { it.isNotBlank() }.take(15)

            val docRef = db.collection("users").document(uid).collection("shorts").document(shortId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "youtubeTitle" to safeTitle,
                    "youtubeDescription" to safeDesc,
                    "hashtags" to safeTags,
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

    suspend fun updateShortPipelineState(
        shortId: String,
        status: ShortStatus,
        localVideoPath: String? = null,
        localThumbPath: String? = null,
        scheduledPublishAtIso: String? = null,
        youtubeVideoId: String? = null,
        errorMessage: String? = null,
        durationSeconds: Long? = null
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/shorts/$shortId"
        return try {
            val uid = requireUserId()
            require(shortId.isNotBlank()) { "Short ID bo'sh bo'lmasligi kerak." }
            val docRef = db.collection("users").document(uid).collection("shorts").document(shortId)
            docPath = docRef.path

            val updates = mutableMapOf<String, Any>(
                "status" to status.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            localVideoPath?.let { updates["localVideoPath"] = it.take(500) }
            localThumbPath?.let { updates["localThumbPath"] = it.take(500) }
            scheduledPublishAtIso?.let { updates["scheduledPublishAtIso"] = it.take(64) }
            youtubeVideoId?.let { updates["youtubeVideoId"] = it.take(64) }
            if (errorMessage != null) {
                updates["errorMessage"] = errorMessage.take(500)
            }
            durationSeconds?.let { updates["durationSeconds"] = it.coerceIn(5L, 60L) }

            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteShort(shortId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/shorts/$shortId"
        return try {
            val uid = requireUserId()
            require(shortId.isNotBlank()) { "Short ID bo'sh bo'lmasligi kerak." }
            val docRef = db.collection("users").document(uid).collection("shorts").document(shortId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveSchedulePreferences(
        autoEnabled: Boolean,
        publishHour: Long,
        publishMinute: Long,
        defaultFolkloreCategory: String,
        autoUploadPrivacy: String = "private"
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/settings/schedule"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("settings").document("schedule")
            docPath = ref.path
            val safeHour = publishHour.coerceIn(0L, 23L)
            val safeMinute = publishMinute.coerceIn(0L, 59L)
            val safeCategory = defaultFolkloreCategory.trim().ifBlank { FolkloreCategory.FOREST_SPIRITS.id }.take(80)
            val safePrivacy = if (autoUploadPrivacy in listOf("private", "public", "unlisted")) {
                autoUploadPrivacy
            } else {
                "private"
            }

            val exists = ref.get().await().exists()
            if (exists) {
                ref.update(
                    mapOf(
                        "autoEnabled" to autoEnabled,
                        "publishHour" to safeHour,
                        "publishMinute" to safeMinute,
                        "defaultFolkloreCategory" to safeCategory,
                        "autoUploadPrivacy" to safePrivacy,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "autoEnabled" to autoEnabled,
                        "publishHour" to safeHour,
                        "publishMinute" to safeMinute,
                        "defaultFolkloreCategory" to safeCategory,
                        "autoUploadPrivacy" to safePrivacy,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveChannelAndRunStatus(
        connectedChannelTitle: String? = null,
        connectedChannelId: String? = null,
        lastRunStatus: String? = null,
        lastRunAtIso: String? = null
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/settings/schedule"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("settings").document("schedule")
            docPath = ref.path
            val exists = ref.get().await().exists()
            if (exists) {
                val updates = mutableMapOf<String, Any>(
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                connectedChannelTitle?.let { updates["connectedChannelTitle"] = it.take(120) }
                connectedChannelId?.let { updates["connectedChannelId"] = it.take(120) }
                lastRunStatus?.let { updates["lastRunStatus"] = it.take(300) }
                lastRunAtIso?.let { updates["lastRunAtIso"] = it.take(64) }
                ref.update(updates).await()
            } else {
                val payload = mutableMapOf<String, Any?>(
                    "userId" to uid,
                    "autoEnabled" to true,
                    "publishHour" to 19L,
                    "publishMinute" to 0L,
                    "defaultFolkloreCategory" to FolkloreCategory.FOREST_SPIRITS.id,
                    "autoUploadPrivacy" to "private",
                    "connectedChannelTitle" to connectedChannelTitle?.take(120),
                    "connectedChannelId" to connectedChannelId?.take(120),
                    "lastRunStatus" to lastRunStatus?.take(300),
                    "lastRunAtIso" to lastRunAtIso?.take(64),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                ref.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }
}
