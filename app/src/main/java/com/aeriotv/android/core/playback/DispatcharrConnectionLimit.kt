package com.aeriotv.android.core.playback

import com.aeriotv.android.R
import com.aeriotv.android.core.locale.AppText
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Dispatcharr connection-limit refusals, recognized ONLY from the server's
 * exact signals and ONLY for Direct Connect (Dispatcharr API) playlists:
 *
 *  - HTTP 429 whose JSON `error` starts with "Stream limit exceeded": the
 *    user's own concurrent stream limit blocked this request (proxy
 *    live_proxy/views.py, terminate_on_limit_exceeded off or no candidate).
 *  - HTTP 503 whose JSON `error` starts with "All active M3U profiles have
 *    reached maximum connection limits": every provider profile is at its
 *    max_streams.
 *
 * Neither is transient from the client's side, so the app shows the notice
 * with a Retry button and does nothing else: no automatic retry, no failover
 * walk, no Reconnecting spinner. Any other status or body (503 "Channel is
 * stopping", a clean end of stream after a server-side terminate, ...) is
 * NOT a limit signal and keeps its existing handling. When the server ends
 * an existing client to make room it says nothing that client can read, so
 * that case is never guessed at.
 *
 * One more, from a Dispatch More server whose admin holds this login to one
 * arrTV device at a time (contract §5a): HTTP 409 with `device_replaced`
 * true and `by`, the device that took over. The server closed this device's
 * stream when that one started; the reconnect that follows is refused until
 * the viewer presses "Watch here", which takes the stream back
 * ([DispatchMore.takeOver]). Reconnecting on our own would take it from the
 * other TV, and that one from us, forever.
 */
@UnstableApi
object DispatcharrConnectionLimit {

    /** [STREAM_ENDED] is not a server refusal: the live stream ended cleanly
     *  again right after its one reconnect (see AerioExoPlayerHolder
     *  onLiveCleanEnd). It reuses the same card and Retry plumbing. */
    enum class Kind { USER_STREAM_LIMIT, PROVIDER_LIMIT, STREAM_ENDED, OTHER_DEVICE }

    data class Notice(
        val kind: Kind,
        val title: String,
        val message: String,
        /** [Kind.OTHER_DEVICE]: the id of the device that took over, so this one can
         *  offer to join it (watch together); null when the server did not say. */
        val byDevice: String? = null,
    )

    /** True while the active playlist is a Dispatcharr API (Direct Connect)
     *  source. Published by PlaylistRepository whenever the active playlist
     *  changes; XC / M3U playlists never show the notice. */
    @Volatile
    var directConnectActive: Boolean = false

    /** Shown only once a clean end has been VERIFIED as this account hitting
     *  its stream limit (see StreamEndVerifier); an unverified clean end keeps
     *  reconnecting on a backoff instead. Says nothing about limits, because
     *  what the user can do about it is press Retry. */
    val STREAM_ENDED: Notice
        get() = Notice(
            kind = Kind.STREAM_ENDED,
            title = AppText.get(R.string.player_limit_stream_ended_title),
            message = AppText.get(R.string.player_limit_stream_ended_message),
        )

    private const val USER_LIMIT_PREFIX = "Stream limit exceeded"
    private const val PROVIDER_LIMIT_PREFIX =
        "All active M3U profiles have reached maximum connection limits"

    /** The limit notice inside whatever Media3 handed us, or null. */
    fun parse(error: Throwable?): Notice? {
        val http = Dispatcharr503.findInvalidResponseCode(error) ?: return null
        return fromResponse(http.responseCode, String(http.responseBody, Charsets.UTF_8))
    }

    /** Same match for a raw OkHttp response (cast ingest, retained fill). */
    fun fromResponse(code: Int, body: String): Notice? {
        // Only Dispatch More sends this, on Xtream links as well as Direct Connect
        otherDevice(code, body)?.let { return it }
        if (!directConnectActive) return null
        val reason = Dispatcharr503.errorField(body) ?: return null
        return when {
            code == 429 && reason.startsWith(USER_LIMIT_PREFIX, ignoreCase = true) -> Notice(
                kind = Kind.USER_STREAM_LIMIT,
                title = AppText.get(R.string.player_limit_user_title),
                message = userLimitMessage(reason),
            )
            code == 503 && reason.startsWith(PROVIDER_LIMIT_PREFIX, ignoreCase = true) -> Notice(
                kind = Kind.PROVIDER_LIMIT,
                title = AppText.get(R.string.player_limit_provider_title),
                message = AppText.get(R.string.player_limit_provider_message),
            )
            else -> null
        }
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _replaced = kotlinx.coroutines.flow.MutableSharedFlow<Notice>(extraBufferCapacity = 1)

    /** The server said on its socket that another device of this login took over: the
     *  player stops at once with [otherDeviceNotice] instead of playing out its buffer
     *  first. The refusal of its next request says the same, for when the socket is closed. */
    val replaced: kotlinx.coroutines.flow.SharedFlow<Notice> = _replaced

    fun replacedNow(byName: String?, byDevice: String? = null) {
        _replaced.tryEmit(otherDeviceNotice(byName, byDevice))
    }

    /** The other-device notice, when this is Dispatch More's refusal of a
     *  replaced device, naming the device that took over. */
    private fun otherDevice(code: Int, body: String): Notice? {
        if (code != 409 || !body.contains("device_replaced")) return null
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        if ((root["device_replaced"] as? JsonPrimitive)?.booleanOrNull != true) return null
        return otherDeviceNotice(
            (root["by"] as? JsonPrimitive)?.contentOrNull,
            (root["by_device"] as? JsonPrimitive)?.contentOrNull,
        )
    }

    /** "Playing on Bedroom TV" (or on another device, when [byName] is blank). */
    fun otherDeviceNotice(byName: String?, byDevice: String? = null): Notice {
        val by = byName?.trim()?.takeIf { it.isNotEmpty() && it != "another device" }
        return Notice(
            kind = Kind.OTHER_DEVICE,
            title = if (by != null) {
                AppText.get(R.string.player_other_device_title, by)
            } else {
                AppText.get(R.string.player_other_device_title_unnamed)
            },
            message = AppText.get(R.string.player_other_device_message),
            byDevice = byDevice?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun userLimitMessage(reason: String): String {
        val count = Regex("""\((\d+)""").find(reason)?.groupValues?.get(1)?.toIntOrNull()
        return if (count == null) {
            AppText.get(R.string.player_limit_user_message_unknown)
        } else {
            AppText.plural(R.plurals.player_limit_user_message, count, count)
        }
    }

    /** Load-error policy wrapper: a limit refusal is never retried by Media3
     *  itself; everything else defers to [base]. */
    class LoadErrorPolicy(
        private val base: LoadErrorHandlingPolicy = DefaultLoadErrorHandlingPolicy(),
    ) : LoadErrorHandlingPolicy by base {
        override fun getRetryDelayMsFor(
            loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo,
        ): Long {
            if (parse(loadErrorInfo.exception) != null) return C.TIME_UNSET
            return base.getRetryDelayMsFor(loadErrorInfo)
        }
    }
}
