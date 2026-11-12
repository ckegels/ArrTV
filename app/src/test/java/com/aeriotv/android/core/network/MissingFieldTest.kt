package com.aeriotv.android.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A login answer missing a field names it, through Ktor's wrapping (missingFieldName). */
class MissingFieldTest {
    @Test
    fun theFieldIsFoundInTheCauseChain() {
        val inner = IllegalArgumentException("Field 'api_key' is required for type with serial name 'User', but it was missing")
        val wrapped = RuntimeException("Illegal input", inner)
        assertEquals("api_key", missingFieldName(wrapped))
        assertEquals(" (missing 'api_key')", missingFieldSuffix(wrapped))
    }

    @Test
    fun anErrorThatIsNotAMissingFieldSaysNothing() {
        assertNull(missingFieldName(RuntimeException("Unexpected JSON token at offset 0")))
        assertEquals("", missingFieldSuffix(RuntimeException("timeout")))
    }
}
