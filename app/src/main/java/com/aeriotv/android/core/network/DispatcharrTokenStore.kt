package com.aeriotv.android.core.network

import android.util.Log
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Per-playlist live JWT cache. Holds the access + refresh pair plus a
 * best-effort expiry timestamp parsed from the access token's `exp` claim
 * so callers can pre-emptively refresh before the server returns 401.
 * The pair is also kept in the app's private storage (next to the playlist
 * row's saved username/password, which a login would use anyway), so a
 * fresh start of the app refreshes instead of logging in: Dispatcharr
 * rate-limits `/api/accounts/token/` by address, and every device behind one
 * VPN address -- plus a TV started a few times in a row -- shares that limit
 * (the Shield's live socket refused with HTTP 429, 2026-10-18, so a watch
 * together "you're in" never reached it).
 *
 * Mirrors iOS [DispatcharrTokenStore](Aerio/Networking/DispatcharrDirectConnect.swift)
 * lines 96-267. Differences from iOS:
 *  - Keyed by playlist UUID string (not `UUID` value type) since Android's
 *    PlaylistEntity stores it as String.
 *  - ConcurrentHashMap instead of NSLock — same thread-safety property,
 *    less verbose, fine for our access pattern (low contention since
 *    Dispatcharr calls run inside coroutines on Dispatchers.IO).
 *  - JWT exp parsing uses java.util.Base64.getUrlDecoder + kotlinx
 *    serialization instead of Foundation Base64 + JSONSerialization.
 */
@Singleton
class DispatcharrTokenStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) {
    private val saved: android.content.SharedPreferences by lazy {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
    }

    private data class TokenPair(
        val access: String,
        val refresh: String,
        val accessExpiresAt: Instant,
    )

    private val pairs = ConcurrentHashMap<String, TokenPair>()

    /** The pair in memory, or the one kept from before this start. */
    private fun pair(playlistId: String): TokenPair? = pairs[playlistId] ?: runCatching {
        val kept = saved.getString(playlistId, null) ?: return@runCatching null
        val (access, refresh) = kept.split("\n", limit = 2).takeIf { it.size == 2 } ?: return@runCatching null
        TokenPair(access, refresh, expiry(access) ?: Instant.EPOCH).also { pairs[playlistId] = it }
    }.getOrNull()

    private fun keep(playlistId: String, pair: TokenPair?) {
        runCatching {
            saved.edit().apply {
                if (pair == null) remove(playlistId) else putString(playlistId, pair.access + "\n" + pair.refresh)
            }.apply()
        }
    }

    /** Current access token for [playlistId], or null when never logged in. */
    fun accessToken(playlistId: String): String? = pair(playlistId)?.access

    /** Current refresh token for [playlistId], or null when never logged in. */
    fun refreshToken(playlistId: String): String? = pair(playlistId)?.refresh

    /**
     * True when the cached access token's `exp` claim is in the past or
     * within [slackSeconds] seconds of expiring. Defaults to 30 seconds of
     * slack so a request that lands right at the boundary doesn't race the
     * server's clock. Returns true when no token is cached, since the caller
     * should treat that as "needs login".
     */
    fun accessIsExpired(playlistId: String, slackSeconds: Long = 30): Boolean {
        val pair = pair(playlistId) ?: return true
        return pair.accessExpiresAt.isBefore(Instant.now().plusSeconds(slackSeconds))
    }

    /** Cache a freshly-issued JWT pair after a successful login. */
    fun store(playlistId: String, access: String, refresh: String) {
        val exp = expiry(access) ?: Instant.now().plusSeconds(1500)
        val pair = TokenPair(access = access, refresh = refresh, accessExpiresAt = exp)
        pairs[playlistId] = pair
        keep(playlistId, pair)
    }

    /**
     * Update only the access token after a refresh. The refresh token stays
     * the same — Dispatcharr does not rotate it on `/api/accounts/token/refresh/`,
     * only emits a new access.
     */
    fun storeRefreshedAccess(playlistId: String, access: String) {
        val existing = pair(playlistId) ?: return
        val exp = expiry(access) ?: Instant.now().plusSeconds(1500)
        val pair = existing.copy(access = access, accessExpiresAt = exp)
        pairs[playlistId] = pair
        keep(playlistId, pair)
    }

    /** Drop the cached pair for one playlist. Call on delete / explicit logout. */
    fun clear(playlistId: String) {
        pairs.remove(playlistId)
        keep(playlistId, null)
    }

    /** Drop every cached pair. Used by the iCloud-equivalent data-clear flow. */
    fun clearAll() {
        pairs.clear()
        runCatching { saved.edit().clear().apply() }
    }

    /**
     * Best-effort decode of a JWT's `exp` claim (Unix epoch seconds). Returns
     * null on any parse failure; callers fall back to a 25-minute heuristic
     * (5 min before the Dispatcharr 30-min default) so we still pre-emptively
     * refresh without trusting a malformed token.
     */
    private fun expiry(jwt: String): Instant? {
        val parts = jwt.split(".")
        if (parts.size < 2) return null
        val payload = parts[1]
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        return try {
            val bytes = Base64.getUrlDecoder().decode(padded)
            val obj = Json.parseToJsonElement(String(bytes, Charsets.UTF_8)) as? JsonObject
                ?: return null
            val exp = obj["exp"]?.jsonPrimitive?.longOrNull ?: return null
            Instant.ofEpochSecond(exp)
        } catch (t: Throwable) {
            Log.w(TAG, "JWT exp decode failed: ${t.message}")
            null
        }
    }

    private companion object {
        const val TAG = "DispatcharrTokenStore"
        const val PREFS = "dispatcharr_tokens"
    }
}
