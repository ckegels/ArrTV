package com.aeriotv.android.core.network

import com.aeriotv.android.core.data.SourceType
import com.aeriotv.android.core.data.db.entity.PlaylistEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class DispatcharrLiveEligibilityTest {
    private fun playlist(type: SourceType?, user: String? = null, pass: String? = null) = PlaylistEntity(
        id = "p1", name = "Home", urlString = "http://srv:9191",
        sourceType = type?.name ?: "SomethingElse", username = user, password = pass,
    )

    @Test
    fun dispatcharrPasswordLoginIsAvailable() {
        assertEquals(
            DispatcharrLiveEligibility.AVAILABLE,
            DispatcharrLiveEligibility.of(playlist(SourceType.DispatcharrUserPass, "admin", "secret")),
        )
    }

    @Test
    fun apiKeyLoginNeedsAPassword() {
        assertEquals(
            DispatcharrLiveEligibility.NEEDS_PASSWORD_LOGIN,
            DispatcharrLiveEligibility.of(playlist(SourceType.DispatcharrApiKey)),
        )
    }

    @Test
    fun passwordModeWithBlankCredentialsNeedsAPassword() {
        assertEquals(
            DispatcharrLiveEligibility.NEEDS_PASSWORD_LOGIN,
            DispatcharrLiveEligibility.of(playlist(SourceType.DispatcharrUserPass, "admin", " ")),
        )
    }

    @Test
    fun apiKeyPlaylistWithSavedCredentialsIsAvailable() {
        // The socket only needs a JWT; how the key was obtained does not matter.
        assertEquals(
            DispatcharrLiveEligibility.AVAILABLE,
            DispatcharrLiveEligibility.of(playlist(SourceType.DispatcharrApiKey, "viewer", "secret")),
        )
    }

    @Test
    fun m3uAndXtreamAreNotDispatcharr() {
        assertEquals(
            DispatcharrLiveEligibility.NOT_DISPATCHARR,
            DispatcharrLiveEligibility.of(playlist(SourceType.M3uUrl)),
        )
        assertEquals(
            DispatcharrLiveEligibility.NOT_DISPATCHARR,
            DispatcharrLiveEligibility.of(playlist(SourceType.XtreamCodes, "user", "pass")),
        )
    }

    @Test
    fun unknownSourceTypeIsNotDispatcharr() {
        assertEquals(DispatcharrLiveEligibility.NOT_DISPATCHARR, DispatcharrLiveEligibility.of(playlist(null)))
    }

    @Test
    fun noPlaylist() {
        assertEquals(DispatcharrLiveEligibility.NO_PLAYLIST, DispatcharrLiveEligibility.of(null))
    }
}
