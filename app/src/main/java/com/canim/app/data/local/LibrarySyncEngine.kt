package com.canim.app.data.local

import android.util.Log
import com.canim.app.data.model.MalTracking
import com.canim.app.data.model.MediaType
import com.canim.app.data.repository.MalAuthManager
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * Drains the [PendingMutationDao] sync queue when the device has network connectivity.
 *
 * ## Responsibilities
 * - Scheduling: decides **when** to attempt a sync (network availability check, attempts limit).
 * - Delegation: actual HTTP calls go through [MalAuthManager], which routes through the
 *   Phase 1 [com.canim.app.data.remote.MalApiPolicyWrapper] (concurrency limiting, 429 cooldown).
 *   This engine does NOT implement its own HTTP retry/backoff — that is Phase 1's concern.
 *
 * ## Not Responsible For
 * - HTTP retry policy (Phase 1 — [com.canim.app.data.remote.RequestPolicy])
 * - Concurrency limiting per host (Phase 1 — semaphore in [com.canim.app.data.remote.RequestPolicy])
 * - Token refresh (Phase 1 — [MalAuthManager.executeWithTokenRefresh])
 *
 * ## Queue Retry Scheduling
 * Each mutation tracks [PendingMutation.attempts]. If a sync attempt fails:
 * - `attempts < [maxAttempts]`: requeue (mark back to PENDING, increment counter).
 * - `attempts >= [maxAttempts]`: mark FAILED_PERMANENTLY (not drained again until force-refresh).
 * There is no sleep/backoff in this engine — the queue is only drained on the next
 * network-available trigger, not on an internal timer.
 *
 * ## Logout + IN_FLIGHT Safety
 * [onLogout] cancels the drain job and clears all local DB data.
 * If an HTTP request was already sent (IN_FLIGHT) when logout is called:
 * - The coroutine is cancelled — Phase 1 result is discarded.
 * - IN_FLIGHT records are NOT marked as SUCCEEDED locally.
 * - All DB tables are cleared unconditionally after job cancellation.
 * - The caller must NOT assume that HTTP cancellation is guaranteed at the OkHttp level.
 *
 * @param pendingMutationDao DAO for the pending queue.
 * @param libraryDao DAO for the library entries (updated on successful sync).
 * @param malAuthManager Source of truth for sending mutations to MAL.
 * @param networkChecker Abstraction for network availability (mockable in tests).
 * @param appScope Application-scoped [CoroutineScope] — outlives any single ViewModel.
 * @param maxAttempts Maximum drain attempts per mutation before FAILED_PERMANENTLY.
 * @param pollIntervalMs How frequently to poll for pending mutations when network is available.
 */
class LibrarySyncEngine(
    private val pendingMutationDao: PendingMutationDao,
    private val libraryDao: LibraryDao,
    private val malAuthManager: MalAuthManager,
    private val networkChecker: NetworkAvailabilityChecker,
    private val appScope: CoroutineScope,
    val maxAttempts: Int = 3,
    private val pollIntervalMs: Long = 30_000L
) {
    private val TAG = "LibrarySyncEngine"
    private val gson = Gson()

    /** Mutex guards concurrent drain attempts (e.g. immediate trigger + poll trigger). */
    private val drainMutex = Mutex()

    /** Active background polling job. */
    @Volatile private var pollingJob: Job? = null

    /**
     * Guard flag set to true on logout. Prevents new enqueue after logout
     * and skips any in-progress drain.
     */
    @Volatile var isLoggedOut: Boolean = false
        private set

    /**
     * Starts the background polling loop. Safe to call multiple times — idempotent.
     * On app start, also resets any stale IN_FLIGHT entries back to PENDING
     * (to recover from process death mid-sync).
     */
    fun start() {
        if (pollingJob?.isActive == true) return
        // Recover from process death: any IN_FLIGHT from previous session → PENDING
        try {
            pendingMutationDao.resetInFlightToPending()
        } catch (e: Exception) {
            Log.w(TAG, "resetInFlightToPending failed on start: ${e.message}")
        }
        pollingJob = appScope.launch {
            while (isActive && !isLoggedOut) {
                if (networkChecker.isNetworkAvailable()) {
                    drainQueue()
                }
                delay(pollIntervalMs)
            }
        }
        Log.d(TAG, "SyncEngine polling started")
    }

    /**
     * Stops the polling loop and clears all local library + pending mutation data.
     * Called on user logout.
     *
     * Important: cancelling the coroutine does NOT guarantee that an already-sent
     * HTTP request was cancelled at the OkHttp level. The mutation result, if any,
     * will be silently discarded — no false SUCCEEDED state is created locally
     * because the DB is cleared entirely after job cancellation.
     */
    fun onLogout() {
        isLoggedOut = true
        pollingJob?.cancel()
        pollingJob = null
        try {
            pendingMutationDao.clearAll()
            libraryDao.clearAll()
        } catch (e: Exception) {
            Log.e(TAG, "onLogout DB clear failed: ${e.message}", e)
        }
        Log.d(TAG, "SyncEngine stopped on logout, DB cleared")
    }

    /**
     * Resets the engine for a new login session (called after successful MAL login).
     * Clears the logout guard and re-starts polling.
     */
    fun onLogin() {
        isLoggedOut = false
        start()
    }

    /**
     * Attempts to immediately send the pending mutation for a specific (malId, mediaType).
     * Called after a local write when network is known to be available.
     *
     * Returns true if the mutation was sent and confirmed, false otherwise.
     * Does not throw — failure is handled by leaving the mutation in PENDING state.
     */
    suspend fun trySendImmediate(malId: Int, mediaType: String): Boolean {
        if (isLoggedOut || !networkChecker.isNetworkAvailable()) return false
        return drainMutex.withLock {
            val mutation = pendingMutationDao.getPendingMutations()
                .firstOrNull { it.malId == malId && it.mediaType == mediaType }
                ?: return@withLock true  // Nothing pending to send — already in-flight or clean
            sendMutation(mutation)
        }
    }

    /**
     * Explicitly resets FAILED_PERMANENTLY mutations back to PENDING (resetting attempts to 0)
     * and triggers a drain if network is available.
     *
     * Rules:
     * - Never called automatically by the polling loop.
     * - Only invoked by explicit user actions (force-refresh or manual retry).
     * - Does NOT affect IN_FLIGHT mutations.
     *
     * @return the number of mutations reset to PENDING.
     */
    suspend fun retryFailedPermanently(mediaType: String? = null): Int {
        if (isLoggedOut) return 0
        val count = pendingMutationDao.resetFailedPermanentlyToPending(mediaType)
        Log.d(TAG, "retryFailedPermanently: reset $count mutations to PENDING")
        if (count > 0 && networkChecker.isNetworkAvailable()) {
            drainQueue()
        }
        return count
    }

    /**
     * Drains all PENDING mutations from the queue in FIFO order.
     * Protected by [drainMutex] to prevent concurrent drains.
     *
     * Exits early if [isLoggedOut] is set or network is lost mid-drain.
     */
    suspend fun drainQueue() {
        if (isLoggedOut) return
        drainMutex.withLock {
            val pending = try {
                pendingMutationDao.getPendingMutations()
            } catch (e: Exception) {
                Log.w(TAG, "drainQueue: failed to fetch pending list: ${e.message}")
                return@withLock
            }
            if (pending.isEmpty()) return@withLock

            Log.d(TAG, "drainQueue: processing ${pending.size} pending mutations")
            for (mutation in pending) {
                if (isLoggedOut || !networkChecker.isNetworkAvailable()) {
                    Log.d(TAG, "drainQueue: stopping early — logged out or network lost")
                    break
                }
                sendMutation(mutation)
            }
            // Compact the table after a successful drain pass
            try { pendingMutationDao.clearSucceeded() } catch (_: Exception) {}
        }
    }

    /**
     * Sends a single [PendingMutation] to MAL via [MalAuthManager].
     * Updates mutation status based on the result.
     *
     * Returns true if the send was confirmed successful.
     */
    private suspend fun sendMutation(mutation: PendingMutation): Boolean {
        val mutId = mutation.id
        val malId = mutation.malId
        val mediaType = mutation.mediaType
        val type = if (mediaType == "ANIME") MediaType.ANIME else MediaType.MANGA

        pendingMutationDao.markInFlight(mutId)

        return try {
            val result: Result<Unit> = when (mutation.mutationType) {
                PendingMutation.TYPE_UPDATE -> {
                    val tracking = parseTracking(mutation.payloadJson)
                    if (tracking == null) {
                        Log.w(TAG, "sendMutation: invalid payload for id=$mutId, marking failed permanently")
                        pendingMutationDao.markFailedPermanently(mutId)
                        return false
                    }
                    if (type == MediaType.ANIME) {
                        malAuthManager.updateAnimeTracking(malId, tracking)
                    } else {
                        malAuthManager.updateMangaTracking(malId, tracking)
                    }
                }
                PendingMutation.TYPE_DELETE -> {
                    if (type == MediaType.ANIME) {
                        malAuthManager.deleteAnimeTracking(malId)
                    } else {
                        malAuthManager.deleteMangaTracking(malId)
                    }
                }
                else -> {
                    Log.w(TAG, "sendMutation: unknown mutationType=${mutation.mutationType}, skipping id=$mutId")
                    pendingMutationDao.markFailedPermanently(mutId)
                    return false
                }
            }

            if (result.isSuccess) {
                pendingMutationDao.markSucceeded(mutId)
                libraryDao.markSynced(malId, mediaType)
                Log.d(TAG, "sendMutation: SUCCESS id=$mutId malId=$malId type=${mutation.mutationType}")
                true
            } else {
                handleSendFailure(mutation, result.exceptionOrNull())
                false
            }
        } catch (e: CancellationException) {
            // Coroutine was cancelled (e.g. logout) — requeue so it's not lost
            // but only if we're NOT logging out (logout will clear everything anyway)
            if (!isLoggedOut) {
                pendingMutationDao.incrementAttemptsAndRequeue(mutId)
            }
            throw e  // Always re-throw CancellationException
        } catch (e: Exception) {
            handleSendFailure(mutation, e)
            false
        }
    }

    /**
     * Handles a failed send attempt:
     * - If [mutation.attempts] + 1 < [maxAttempts]: requeue (PENDING) with incremented counter.
     * - Otherwise: mark FAILED_PERMANENTLY.
     */
    private fun handleSendFailure(mutation: PendingMutation, cause: Throwable?) {
        val newAttempts = mutation.attempts + 1
        if (newAttempts < maxAttempts) {
            pendingMutationDao.incrementAttemptsAndRequeue(mutation.id)
            Log.w(TAG, "sendMutation: FAILURE id=${mutation.id} malId=${mutation.malId} attempts=$newAttempts/${maxAttempts}: ${cause?.message}")
        } else {
            pendingMutationDao.markFailedPermanently(mutation.id)
            Log.e(TAG, "sendMutation: FAILED_PERMANENTLY id=${mutation.id} malId=${mutation.malId} after $newAttempts attempts: ${cause?.message}")
        }
    }

    private fun parseTracking(json: String): MalTracking? {
        return try {
            if (json.isBlank()) null
            else gson.fromJson(json, MalTracking::class.java)
        } catch (_: Exception) {
            null
        }
    }
}
