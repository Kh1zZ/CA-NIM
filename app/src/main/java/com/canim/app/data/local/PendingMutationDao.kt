package com.canim.app.data.local

import android.content.ContentValues
import android.database.Cursor
import android.util.Log
import com.canim.app.data.local.LocalDatabase.Companion.COL_MUT_ATTEMPTS
import com.canim.app.data.local.LocalDatabase.Companion.COL_MUT_CREATED_AT
import com.canim.app.data.local.LocalDatabase.Companion.COL_MUT_ID
import com.canim.app.data.local.LocalDatabase.Companion.COL_MUT_LOCAL_UPDATED_AT
import com.canim.app.data.local.LocalDatabase.Companion.COL_MUT_MAL_ID
import com.canim.app.data.local.LocalDatabase.Companion.COL_MUT_MEDIA_TYPE
import com.canim.app.data.local.LocalDatabase.Companion.COL_MUT_PAYLOAD_JSON
import com.canim.app.data.local.LocalDatabase.Companion.COL_MUT_STATUS
import com.canim.app.data.local.LocalDatabase.Companion.COL_MUT_TYPE
import com.canim.app.data.local.LocalDatabase.Companion.TABLE_PENDING_MUTATIONS
import com.canim.app.data.local.PendingMutation.Companion.STATUS_FAILED_PERMANENTLY
import com.canim.app.data.local.PendingMutation.Companion.STATUS_IN_FLIGHT
import com.canim.app.data.local.PendingMutation.Companion.STATUS_PENDING
import com.canim.app.data.local.PendingMutation.Companion.STATUS_SUCCEEDED
import com.canim.app.data.local.PendingMutation.Companion.TYPE_DELETE

/**
 * Data Access Object for [TABLE_PENDING_MUTATIONS].
 *
 * Coalescing rules enforced by [enqueueMutation]:
 * - Two UPDATE mutations for the same (malId, mediaType): only the newer one survives.
 * - DELETE + any previous mutation for the same (malId, mediaType): DELETE wins; prior is removed.
 * - UPDATE enqueued while DELETE is already PENDING: UPDATE is silently ignored.
 *
 * All operations are synchronous SQLite calls — callers dispatch to Dispatchers.IO.
 */
class PendingMutationDao(private val db: LocalDatabase) {

    /**
     * Enqueues a [PendingMutation] with coalescing logic:
     * - If a PENDING or IN_FLIGHT entry exists for the same (malId, mediaType):
     *   - If new mutation is DELETE: replace it (DELETE always wins).
     *   - If new mutation is UPDATE and existing is DELETE: ignore the new UPDATE (DELETE wins).
     *   - If both are UPDATE: replace existing with the newer one.
     */
    fun enqueueMutation(mutation: PendingMutation) {
        try {
            val wdb = db.writableDatabase
            wdb.beginTransaction()
            try {
                // Find existing active mutation for this (malId, mediaType)
                val existing = getActiveMutationForMalId(mutation.malId, mutation.mediaType)

                if (existing != null) {
                    when {
                        // DELETE always wins — remove existing and insert new DELETE
                        mutation.mutationType == TYPE_DELETE -> {
                            wdb.delete(
                                TABLE_PENDING_MUTATIONS,
                                "$COL_MUT_ID = ?",
                                arrayOf(existing.id.toString())
                            )
                            wdb.insert(TABLE_PENDING_MUTATIONS, null, mutation.toContentValues())
                        }
                        // Existing is DELETE, new is UPDATE — DELETE wins, ignore UPDATE
                        existing.mutationType == TYPE_DELETE -> {
                            // Do nothing — DELETE already in queue is authoritative
                        }
                        // Both are UPDATE — replace with newer (higher localUpdatedAt)
                        else -> {
                            wdb.delete(
                                TABLE_PENDING_MUTATIONS,
                                "$COL_MUT_ID = ?",
                                arrayOf(existing.id.toString())
                            )
                            wdb.insert(TABLE_PENDING_MUTATIONS, null, mutation.toContentValues())
                        }
                    }
                } else {
                    wdb.insert(TABLE_PENDING_MUTATIONS, null, mutation.toContentValues())
                }
                wdb.setTransactionSuccessful()
            } finally {
                wdb.endTransaction()
            }
        } catch (e: Exception) {
            Log.e("PendingMutationDao", "enqueueMutation failed for malId=${mutation.malId}: ${e.message}", e)
            throw e
        }
    }

    /**
     * Returns the current active (PENDING or IN_FLIGHT) mutation for a given (malId, mediaType),
     * or null if no active mutation exists.
     */
    fun getActiveMutationForMalId(malId: Int, mediaType: String): PendingMutation? {
        return try {
            val cursor = db.readableDatabase.query(
                TABLE_PENDING_MUTATIONS,
                null,
                "$COL_MUT_MAL_ID = ? AND $COL_MUT_MEDIA_TYPE = ? AND $COL_MUT_STATUS IN (?, ?)",
                arrayOf(malId.toString(), mediaType, STATUS_PENDING, STATUS_IN_FLIGHT),
                null, null,
                "$COL_MUT_CREATED_AT ASC",
                "1"
            )
            cursor.use { if (it.moveToFirst()) it.toPendingMutation() else null }
        } catch (e: Exception) {
            Log.e("PendingMutationDao", "getActiveMutation failed for malId=$malId: ${e.message}", e)
            null
        }
    }

    /**
     * Returns the set of malIds that have PENDING or IN_FLIGHT mutations for the given [mediaType].
     * Used during force-refresh reconciliation to protect entries with unresolved mutations.
     */
    fun getActiveMalIds(mediaType: String): Set<Int> {
        return try {
            val cursor = db.readableDatabase.query(
                TABLE_PENDING_MUTATIONS,
                arrayOf(COL_MUT_MAL_ID),
                "$COL_MUT_MEDIA_TYPE = ? AND $COL_MUT_STATUS IN (?, ?)",
                arrayOf(mediaType, STATUS_PENDING, STATUS_IN_FLIGHT),
                COL_MUT_MAL_ID, null, null
            )
            cursor.use {
                val ids = mutableSetOf<Int>()
                while (it.moveToNext()) ids.add(it.getInt(0))
                ids
            }
        } catch (e: Exception) {
            Log.e("PendingMutationDao", "getActiveMalIds failed for type=$mediaType: ${e.message}", e)
            emptySet()
        }
    }

    /**
     * Returns all PENDING mutations ordered FIFO by [COL_MUT_CREATED_AT].
     * Excludes IN_FLIGHT, SUCCEEDED, and FAILED_PERMANENTLY entries.
     */
    fun getPendingMutations(): List<PendingMutation> {
        return try {
            val cursor = db.readableDatabase.query(
                TABLE_PENDING_MUTATIONS,
                null,
                "$COL_MUT_STATUS = ?",
                arrayOf(STATUS_PENDING),
                null, null,
                "$COL_MUT_CREATED_AT ASC"
            )
            cursor.use { it.toPendingMutationList() }
        } catch (e: Exception) {
            Log.e("PendingMutationDao", "getPendingMutations failed: ${e.message}", e)
            emptyList()
        }
    }

    /** Transitions a mutation from PENDING → IN_FLIGHT. */
    fun markInFlight(id: Long) = updateStatus(id, STATUS_IN_FLIGHT)

    /** Transitions a mutation to SUCCEEDED (terminal state). */
    fun markSucceeded(id: Long) = updateStatus(id, STATUS_SUCCEEDED)

    /**
     * Transitions a mutation from IN_FLIGHT → PENDING and increments [COL_MUT_ATTEMPTS].
     * Called when a sync attempt fails transiently (network error, non-auth failure).
     */
    fun incrementAttemptsAndRequeue(id: Long) {
        try {
            // Use raw SQL to safely increment attempts atomically
            db.writableDatabase.execSQL(
                "UPDATE $TABLE_PENDING_MUTATIONS SET $COL_MUT_ATTEMPTS = $COL_MUT_ATTEMPTS + 1, $COL_MUT_STATUS = ? WHERE $COL_MUT_ID = ?",
                arrayOf(STATUS_PENDING, id)
            )
        } catch (e: Exception) {
            Log.e("PendingMutationDao", "incrementAttemptsAndRequeue failed for id=$id: ${e.message}", e)
        }
    }

    /** Transitions a mutation to FAILED_PERMANENTLY (terminal state — no more auto-drain). */
    fun markFailedPermanently(id: Long) = updateStatus(id, STATUS_FAILED_PERMANENTLY)

    /** Deletes all mutations with status SUCCEEDED. Call periodically to compact the table. */
    fun clearSucceeded() {
        try {
            db.writableDatabase.delete(
                TABLE_PENDING_MUTATIONS,
                "$COL_MUT_STATUS = ?",
                arrayOf(STATUS_SUCCEEDED)
            )
        } catch (e: Exception) {
            Log.e("PendingMutationDao", "clearSucceeded failed: ${e.message}", e)
        }
    }

    /**
     * Resets all IN_FLIGHT mutations back to PENDING.
     * Called on app restart to recover mutations that were IN_FLIGHT when the process died.
     */
    fun resetInFlightToPending() {
        try {
            db.writableDatabase.execSQL(
                "UPDATE $TABLE_PENDING_MUTATIONS SET $COL_MUT_STATUS = ? WHERE $COL_MUT_STATUS = ?",
                arrayOf(STATUS_PENDING, STATUS_IN_FLIGHT)
            )
        } catch (e: Exception) {
            Log.e("PendingMutationDao", "resetInFlightToPending failed: ${e.message}", e)
        }
    }

    /** Deletes all rows from [TABLE_PENDING_MUTATIONS]. Used on logout. */
    fun clearAll() {
        try {
            db.writableDatabase.delete(TABLE_PENDING_MUTATIONS, null, null)
        } catch (e: Exception) {
            Log.e("PendingMutationDao", "clearAll failed: ${e.message}", e)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun updateStatus(id: Long, newStatus: String) {
        try {
            val cv = ContentValues().apply { put(COL_MUT_STATUS, newStatus) }
            db.writableDatabase.update(
                TABLE_PENDING_MUTATIONS,
                cv,
                "$COL_MUT_ID = ?",
                arrayOf(id.toString())
            )
        } catch (e: Exception) {
            Log.e("PendingMutationDao", "updateStatus failed for id=$id newStatus=$newStatus: ${e.message}", e)
        }
    }

    private fun PendingMutation.toContentValues(): ContentValues = ContentValues().apply {
        // id is AUTOINCREMENT — do not include it on insert (id == 0L means new row)
        put(COL_MUT_MAL_ID, malId)
        put(COL_MUT_MEDIA_TYPE, mediaType)
        put(COL_MUT_TYPE, mutationType)
        put(COL_MUT_PAYLOAD_JSON, payloadJson)
        put(COL_MUT_LOCAL_UPDATED_AT, localUpdatedAt)
        put(COL_MUT_CREATED_AT, createdAt)
        put(COL_MUT_ATTEMPTS, attempts)
        put(COL_MUT_STATUS, status)
    }

    private fun Cursor.toPendingMutationList(): List<PendingMutation> {
        val list = mutableListOf<PendingMutation>()
        while (moveToNext()) list.add(toPendingMutation())
        return list
    }

    private fun Cursor.toPendingMutation(): PendingMutation = PendingMutation(
        id = getLong(getColumnIndexOrThrow(COL_MUT_ID)),
        malId = getInt(getColumnIndexOrThrow(COL_MUT_MAL_ID)),
        mediaType = getString(getColumnIndexOrThrow(COL_MUT_MEDIA_TYPE)),
        mutationType = getString(getColumnIndexOrThrow(COL_MUT_TYPE)),
        payloadJson = getString(getColumnIndexOrThrow(COL_MUT_PAYLOAD_JSON)) ?: "",
        localUpdatedAt = getLong(getColumnIndexOrThrow(COL_MUT_LOCAL_UPDATED_AT)),
        createdAt = getLong(getColumnIndexOrThrow(COL_MUT_CREATED_AT)),
        attempts = getInt(getColumnIndexOrThrow(COL_MUT_ATTEMPTS)),
        status = getString(getColumnIndexOrThrow(COL_MUT_STATUS)) ?: STATUS_PENDING
    )
}
