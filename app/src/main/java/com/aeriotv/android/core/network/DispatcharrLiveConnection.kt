package com.aeriotv.android.core.network

import android.os.SystemClock
import android.util.Log
import com.aeriotv.android.core.data.db.entity.PlaylistEntity
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * One long-lived connection to Dispatcharr's `/ws/` socket, emitting the
 * parsed [DispatcharrLiveEvent]s the app acts on.
 *
 * Auth: the socket takes a JWT in `?token=` and checks it only during the
 * handshake (dispatcharr/jwt_ws_auth.py), so an open socket outlives the
 * 30-minute access token and only a RECONNECT needs a fresh one. The token
 * comes from the shared [DispatcharrTokenStore]: cached access, else the
 * refresh token, else a login from the playlist's saved credentials.
 * Dispatcharr rate-limits `/api/accounts/token/` (HTTP 429), so login is the
 * last resort and failures back off instead of retrying hot.
 *
 * Privacy: frames are never logged. Admin sockets also receive
 * `channel_stats`, whose payload carries upstream stream URLs with the
 * provider's credentials in them.
 */
@Singleton
class DispatcharrLiveConnection @Inject constructor(
    private val client: DispatcharrClient,
    private val tokenStore: DispatcharrTokenStore,
) {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // Long-lived socket: no read timeout. The ping below detects a dead
        // link (Wi-Fi drop, server restart) instead.
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val _events = MutableSharedFlow<DispatcharrLiveEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<DispatcharrLiveEvent> = _events.asSharedFlow()

    private sealed interface SessionEnd {
        data class Closed(val code: Int) : SessionEnd
        /** Handshake refused with an HTTP status (403 = token not accepted). */
        data class Refused(val httpCode: Int) : SessionEnd
        data class Failed(val reason: String) : SessionEnd
    }

    /**
     * Keep a socket open for [playlist] until the calling coroutine is
     * cancelled, reconnecting with backoff. [baseUrl] is re-resolved on every
     * attempt so a LAN/WAN switch is picked up on the next reconnect.
     */
    suspend fun run(playlist: PlaylistEntity, baseUrl: suspend () -> String) {
        var failures = 0
        while (currentCoroutineContext().isActive) {
            val base = baseUrl().trimEnd('/')
            val token = try {
                accessToken(playlist, base)
            } catch (c: CancellationException) {
                throw c
            } catch (e: DispatcharrError.InvalidCredentials) {
                // The saved password no longer works; hammering login would only
                // lock the account. The user fixes it in Edit Playlist.
                Log.w(TAG, "login rejected for ${playlist.id.take(8)}; retrying in ${INVALID_CREDENTIALS_RETRY_MS / 60_000} min")
                delay(INVALID_CREDENTIALS_RETRY_MS)
                continue
            } catch (t: Throwable) {
                val wait = backoffMs(failures++, rateLimited = t.isRateLimit())
                Log.w(TAG, "token unavailable (${t::class.simpleName}: ${t.message}); retry in ${wait / 1000}s")
                delay(wait)
                continue
            }
            val openedAt = SystemClock.elapsedRealtime()
            val end = session(base, token)
            val lived = SystemClock.elapsedRealtime() - openedAt
            if (lived >= STABLE_SESSION_MS) failures = 0
            if (end is SessionEnd.Refused && (end.httpCode == 401 || end.httpCode == 403)) {
                // Token not accepted: drop it so the next attempt refreshes or logs in.
                tokenStore.clear(playlist.id)
            }
            val wait = backoffMs(failures++, rateLimited = end is SessionEnd.Refused && end.httpCode == 429)
            Log.i(TAG, "socket ended ($end) after ${lived / 1000}s; reconnect in ${wait / 1000}s")
            delay(wait)
        }
    }

    /** Cached access token, else refresh, else login. Never logs the token. */
    private suspend fun accessToken(playlist: PlaylistEntity, base: String): String {
        if (!tokenStore.accessIsExpired(playlist.id, slackSeconds = TOKEN_SLACK_SECONDS)) {
            tokenStore.accessToken(playlist.id)?.let { return it }
        }
        tokenStore.refreshToken(playlist.id)?.let { refresh ->
            try {
                val access = client.refreshAccessToken(base, refresh)
                tokenStore.storeRefreshedAccess(playlist.id, access)
                return access
            } catch (c: CancellationException) {
                throw c
            } catch (e: DispatcharrError.RefreshExpired) {
                Log.i(TAG, "refresh token expired; logging in again")
            }
        }
        val user = playlist.username?.takeIf { it.isNotBlank() }
        val pass = playlist.password?.takeIf { it.isNotBlank() }
        if (user == null || pass == null) throw IllegalStateException("no saved username/password")
        val pair = client.login(base, user, pass)
        tokenStore.store(playlist.id, pair.access, pair.refresh)
        return pair.access
    }

    /** One socket, from handshake to close. Suspends until it ends. */
    private suspend fun session(base: String, token: String): SessionEnd =
        suspendCancellableCoroutine { cont ->
            val url = "$base/ws/?token=" + URLEncoder.encode(token, "UTF-8")
            val request = Request.Builder().url(url).header("User-Agent", "AerioTV-Android").build()
            val socket = http.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.i(TAG, "socket open")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val event = DispatcharrLiveEvent.parse(text) ?: return
                    Log.i(TAG, "event: $event")
                    if (!_events.tryEmit(event)) Log.w(TAG, "event dropped (buffer full): $event")
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (cont.isActive) cont.resume(SessionEnd.Closed(code))
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val end = if (response != null) SessionEnd.Refused(response.code)
                    else SessionEnd.Failed(t::class.simpleName ?: "error")
                    response?.close()
                    if (cont.isActive) cont.resume(end)
                }
            })
            cont.invokeOnCancellation {
                // Leaving the app or turning the setting off: close cleanly.
                socket.close(1000, null)
            }
        }

    private fun Throwable.isRateLimit(): Boolean = message?.contains("429") == true

    companion object {
        private const val TAG = "DispatcharrLive"

        /** A socket that stayed up this long resets the backoff. */
        private const val STABLE_SESSION_MS = 60_000L

        /** Refresh the access token when it expires within this window. */
        private const val TOKEN_SLACK_SECONDS = 120L

        private const val INVALID_CREDENTIALS_RETRY_MS = 30L * 60_000L

        /** 5 s, 10 s, 30 s, 1 min, 2 min, then every 5 min; at least 2 min after a 429. */
        internal fun backoffMs(failures: Int, rateLimited: Boolean): Long {
            val steps = longArrayOf(5_000, 10_000, 30_000, 60_000, 120_000, 300_000)
            val base = steps[failures.coerceIn(0, steps.lastIndex)]
            return if (rateLimited) maxOf(base, 120_000) else base
        }
    }
}
