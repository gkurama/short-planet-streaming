package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.StudioClip
import com.example.data.model.StylePreferences
import com.example.data.model.TrendItem
import com.example.data.model.VideoSource
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

class StudioRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireSignedInUid(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Inicia sessão com o Google antes de aceder ao Firestore.")
        if (expectedUserId != null && uid != expectedUserId) {
            throw IllegalStateException("Sessão alterada; operação recusada.")
        }
        return uid
    }

    // --- Real-time Streams ---

    fun observeSources(userId: String): Flow<List<VideoSource>> = flow {
        require(userId.isNotBlank())
        val path = "users/$userId/sources"
        emitAll(
            db.collection("users").document(userId).collection("sources")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VideoSource::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, userId)
                }
        )
    }

    fun observeClips(userId: String): Flow<List<StudioClip>> = flow {
        require(userId.isNotBlank())
        val path = "users/$userId/clips"
        emitAll(
            db.collection("users").document(userId).collection("clips")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StudioClip::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, userId)
                }
        )
    }

    fun observeStylePreferences(userId: String): Flow<StylePreferences> = flow {
        require(userId.isNotBlank())
        val path = "users/$userId/preferences/style"
        emitAll(
            db.collection("users").document(userId).collection("preferences").document("style")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        StylePreferences::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: StylePreferences(userId = userId)
                }
                .catch { error ->
                    handleFlowError(error, OperationType.GET, path, userId)
                }
        )
    }

    fun observeTrends(userId: String): Flow<List<TrendItem>> = flow {
        require(userId.isNotBlank())
        val path = "users/$userId/trends"
        emitAll(
            db.collection("users").document(userId).collection("trends")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TrendItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.heatScore }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, userId)
                }
        )
    }

    private fun handleFlowError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        uid: String
    ): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    // --- Mutations ---

    suspend fun addSource(userId: String, source: VideoSource): Result<String> {
        var docPath = "users/$userId/sources"
        return try {
            val uid = requireSignedInUid(userId)
            require(source.youtubeUrl.trim().length in 5..500) { "URL do YouTube inválido." }
            require(source.title.trim().length in 1..200) { "O título deve ter entre 1 e 200 caracteres." }

            val docRef = db.collection("users").document(uid).collection("sources").document()
            docPath = docRef.path
            docRef.set(source.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateSourceAnalysis(
        userId: String,
        sourceId: String,
        status: String,
        aiSummary: String
    ): Result<Unit> {
        var docPath = "users/$userId/sources/$sourceId"
        return try {
            val uid = requireSignedInUid(userId)
            require(sourceId.isNotBlank())
            val safeStatus = if (status in listOf("IMPORTED", "ANALYZED", "ARCHIVED")) status else "ANALYZED"
            val docRef = db.collection("users").document(uid).collection("sources").document(sourceId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "status" to safeStatus,
                    "aiSummary" to aiSummary.trim().take(1500),
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

    suspend fun deleteSource(userId: String, sourceId: String): Result<Unit> {
        var docPath = "users/$userId/sources/$sourceId"
        return try {
            val uid = requireSignedInUid(userId)
            require(sourceId.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("sources").document(sourceId)
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

    suspend fun addClip(userId: String, clip: StudioClip): Result<String> {
        var docPath = "users/$userId/clips"
        return try {
            val uid = requireSignedInUid(userId)
            require(clip.title.trim().length in 1..140) { "Título do clip obrigatório." }
            val docRef = db.collection("users").document(uid).collection("clips").document()
            docPath = docRef.path
            docRef.set(clip.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateClipEditor(
        userId: String,
        clip: StudioClip
    ): Result<Unit> {
        var docPath = "users/$userId/clips/${clip.id}"
        return try {
            val uid = requireSignedInUid(userId)
            require(clip.id.isNotBlank()) { "Clip ID inválido." }
            require(clip.title.trim().length in 1..140) { "Título deve ter entre 1 e 140 caracteres." }
            val docRef = db.collection("users").document(uid).collection("clips").document(clip.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "title" to clip.title.trim().take(140),
                    "hookText" to clip.hookText.trim().take(260),
                    "startTime" to clip.startTime.trim().take(20),
                    "endTime" to clip.endTime.trim().take(20),
                    "durationSeconds" to clip.durationSeconds.coerceIn(5L, 300L),
                    "aspectRatio" to if (clip.aspectRatio in StudioClip.ALLOWED_ASPECT_RATIOS) clip.aspectRatio else "9:16",
                    "captionStyle" to if (clip.captionStyle in StudioClip.ALLOWED_CAPTION_STYLES) clip.captionStyle else "Dinâmico",
                    "framingMode" to if (clip.framingMode in StudioClip.ALLOWED_FRAMING_MODES) clip.framingMode else "Seguir pessoa",
                    "captionsText" to clip.captionsText.trim().take(2500),
                    "highlightKeywords" to clip.highlightKeywords.trim().take(240),
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

    suspend fun updateClipPublishing(
        userId: String,
        clipId: String,
        targetPlatform: String,
        publishStatus: String,
        publishNotes: String
    ): Result<Unit> {
        var docPath = "users/$userId/clips/$clipId"
        return try {
            val uid = requireSignedInUid(userId)
            require(clipId.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("clips").document(clipId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "targetPlatform" to if (targetPlatform in StudioClip.ALLOWED_PLATFORMS) targetPlatform else "BOTH",
                    "publishStatus" to if (publishStatus in StudioClip.ALLOWED_PUBLISH_STATUSES) publishStatus else "READY",
                    "publishNotes" to publishNotes.trim().take(600),
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

    suspend fun deleteClip(userId: String, clipId: String): Result<Unit> {
        var docPath = "users/$userId/clips/$clipId"
        return try {
            val uid = requireSignedInUid(userId)
            require(clipId.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("clips").document(clipId)
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

    suspend fun saveStylePreferences(userId: String, prefs: StylePreferences): Result<Unit> {
        var docPath = "users/$userId/preferences/style"
        return try {
            val uid = requireSignedInUid(userId)
            val docRef = db.collection("users").document(uid)
                .collection("preferences").document("style")
            docPath = docRef.path
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "editingPace" to if (prefs.editingPace in StylePreferences.ALLOWED_PACES) prefs.editingPace else "Dinâmico",
                        "captionPreset" to if (prefs.captionPreset in StylePreferences.ALLOWED_CAPTION_PRESETS) prefs.captionPreset else "Destacadas",
                        "framingPreset" to if (prefs.framingPreset in StudioClip.ALLOWED_FRAMING_MODES) prefs.framingPreset else "Seguir pessoa",
                        "preferredDuration" to if (prefs.preferredDuration in StylePreferences.ALLOWED_DURATIONS) prefs.preferredDuration else "30–60 segundos",
                        "brandTone" to prefs.brandTone.trim().take(160),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(prefs.toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addTrend(userId: String, trend: TrendItem): Result<String> {
        var docPath = "users/$userId/trends"
        return try {
            val uid = requireSignedInUid(userId)
            val docRef = db.collection("users").document(uid).collection("trends").document()
            docPath = docRef.path
            docRef.set(trend.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTrend(userId: String, trendId: String): Result<Unit> {
        var docPath = "users/$userId/trends/$trendId"
        return try {
            val uid = requireSignedInUid(userId)
            require(trendId.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("trends").document(trendId)
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
}
