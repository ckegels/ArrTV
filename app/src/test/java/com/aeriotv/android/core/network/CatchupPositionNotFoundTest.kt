package com.aeriotv.android.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A 404 about the session keeps position reports going; only a server without the
 *  endpoint switches them off (the keep-alive for a long catch-up pause). */
class CatchupPositionNotFoundTest {
    @Test
    fun theServersOwnAnswersAreAboutTheSession() {
        assertTrue(isSessionLevelNotFound("""{"error":"No active playback for this session"}"""))
        assertTrue(isSessionLevelNotFound(""" {"error": "Session not found"} """))
    }

    @Test
    fun aPlainNotFoundPageMeansNoEndpoint() {
        assertFalse(isSessionLevelNotFound("<!doctype html><title>Not Found</title>"))
        assertFalse(isSessionLevelNotFound("""{"detail":"Not found."}"""))
        assertFalse(isSessionLevelNotFound(null))
        assertFalse(isSessionLevelNotFound(""))
    }
}
