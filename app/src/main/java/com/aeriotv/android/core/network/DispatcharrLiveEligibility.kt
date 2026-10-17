package com.aeriotv.android.core.network

import com.aeriotv.android.core.data.SourceType
import com.aeriotv.android.core.data.db.entity.PlaylistEntity

/**
 * Whether a playlist can follow Dispatcharr's live change notifications.
 *
 * Dispatcharr's `/ws/` socket authenticates with a JWT in `?token=` only
 * (dispatcharr/jwt_ws_auth.py). An API key is never checked there, and a JWT
 * can only be obtained from `/api/accounts/token/` with a username and
 * password, so an API-key-only playlist cannot connect. Any account level
 * works: the socket filters only connection telemetry (channel/vod/timeshift
 * stats) to admins, and the playlist and EPG refresh events reach everyone.
 */
enum class DispatcharrLiveEligibility {
    AVAILABLE,

    /** Dispatcharr, but no saved username + password (API key login). */
    NEEDS_PASSWORD_LOGIN,

    /** M3U or Xtream Codes: the server has no change notifications. */
    NOT_DISPATCHARR,

    /** No active playlist. */
    NO_PLAYLIST;

    companion object {
        fun of(playlist: PlaylistEntity?): DispatcharrLiveEligibility {
            if (playlist == null) return NO_PLAYLIST
            val type = SourceType.entries.firstOrNull { it.name == playlist.sourceType }
            val isDispatcharr = type == SourceType.DispatcharrUserPass || type == SourceType.DispatcharrApiKey
            if (!isDispatcharr) return NOT_DISPATCHARR
            val hasPassword = !playlist.username.isNullOrBlank() && !playlist.password.isNullOrBlank()
            return if (hasPassword) AVAILABLE else NEEDS_PASSWORD_LOGIN
        }
    }
}
