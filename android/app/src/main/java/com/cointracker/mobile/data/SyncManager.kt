package com.cointracker.mobile.data

import kotlin.math.abs

/**
 * Compares a local cache snapshot against what Firestore returned.
 * Returns a [SyncResult] that drives what the ViewModel does next.
 *
 * Decision table:
 * ┌──────────────────┬───────────────┬───────────────────────────────────────────────┐
 * │ Cache            │ Firestore     │ Result                                        │
 * ├──────────────────┼───────────────┼───────────────────────────────────────────────┤
 * │ null             │ OK            │ DbOnly  → use DB, create cache                │
 * │ null             │ failed        │ BothEmpty                                     │
 * │ exists           │ failed        │ UseCache → offline mode                       │
 * │ exists           │ empty/wiped   │ RestoreFromCache → push cache to DB silently  │
 * │ exists           │ same data     │ Synced → normal                               │
 * │ exists           │ DB is newer   │ DbNewer → update cache from DB                │
 * │ exists           │ cache newer   │ CacheNewer → push cache to DB (offline edits) │
 * │ exists           │ big mismatch  │ ConflictDetected → show dialog                │
 * └──────────────────┴───────────────┴───────────────────────────────────────────────┘
 */
object SyncManager {

    // DB is considered "wiped" if it has fewer transactions than cache by this margin
    private const val WIPE_TX_THRESHOLD = 3

    // Balance difference that triggers a conflict dialog (coins)
    private const val CONFLICT_BALANCE_THRESHOLD = 500

    // Transaction count difference that also triggers conflict
    private const val CONFLICT_TX_THRESHOLD = 5

    fun compare(
        cached    : CachedProfile?,
        dbSuccess : Boolean,
        dbEnv     : ProfileEnvelope?
    ): SyncResult {
        // ── No data anywhere ──────────────────────────────────────────────────
        if (cached == null && !dbSuccess) return SyncResult.BothEmpty
        if (cached == null && dbEnv == null) return SyncResult.BothEmpty

        // ── Only cache available (offline) ────────────────────────────────────
        if (!dbSuccess) {
            return if (cached != null) SyncResult.UseCache(cached)
            else SyncResult.BothEmpty
        }

        // ── DB succeeded ──────────────────────────────────────────────────────
        checkNotNull(dbEnv)

        // No local cache yet — first install or cache was cleared
        if (cached == null) return SyncResult.DbOnly(dbEnv)

        val dbTxCount    = dbEnv.transactions.size
        val cacheTxCount = cached.transactionCount

        // DB appears wiped (0 or very few txns) but cache has significant data
        if (dbTxCount == 0 && cacheTxCount >= WIPE_TX_THRESHOLD) {
            return SyncResult.RestoreFromCache(cached)
        }

        // Both have data — compare
        val balanceDiff = abs(cached.balance - dbEnv.balance)
        val txDiff      = abs(cacheTxCount - dbTxCount)

        // Significant mismatch on both balance AND txn count → conflict
        if (balanceDiff >= CONFLICT_BALANCE_THRESHOLD && txDiff >= CONFLICT_TX_THRESHOLD) {
            // If cache clearly has more data → cache is newer (offline edits)
            if (cacheTxCount > dbTxCount + CONFLICT_TX_THRESHOLD) {
                return SyncResult.CacheNewer(cached)
            }
            // Otherwise prompt user
            return SyncResult.ConflictDetected(cached, dbEnv)
        }

        // Cache has more transactions (user edited offline, DB didn't sync)
        if (cacheTxCount > dbTxCount + CONFLICT_TX_THRESHOLD && balanceDiff > 100) {
            return SyncResult.CacheNewer(cached)
        }

        // DB is newer or they're in sync — use DB
        return SyncResult.Synced(dbEnv)
    }
}

// ── Result types ──────────────────────────────────────────────────────────────

sealed class SyncResult {
    /** DB and cache match — use DB, refresh cache. */
    data class Synced(val env: ProfileEnvelope) : SyncResult()

    /** No local cache — first run. Use DB, create cache. */
    data class DbOnly(val env: ProfileEnvelope) : SyncResult()

    /** DB failed to load — work offline from cache. */
    data class UseCache(val cached: CachedProfile) : SyncResult()

    /** DB has 0/few txns but cache is rich — DB was wiped. Auto-restore silently. */
    data class RestoreFromCache(val cached: CachedProfile) : SyncResult()

    /** Cache has more txns than DB — offline edits. Push cache to DB silently. */
    data class CacheNewer(val cached: CachedProfile) : SyncResult()

    /** DB is clearly newer than cache (shouldn't happen normally). Use DB. */
    data class DbNewer(val env: ProfileEnvelope) : SyncResult()

    /** Both have significant data that disagrees — ask user. */
    data class ConflictDetected(val cached: CachedProfile, val db: ProfileEnvelope) : SyncResult()

    /** No data anywhere. */
    object BothEmpty : SyncResult()
}
