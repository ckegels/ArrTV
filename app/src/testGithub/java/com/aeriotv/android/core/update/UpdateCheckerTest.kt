package com.aeriotv.android.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private fun newer(remote: String, local: String) = UpdateChecker.isNewer(remote, local)

    @Test
    fun officialReleasesCompareAsBefore() {
        assertTrue(newer("0.5.10", "0.5.9"))
        assertTrue(newer("0.6.0", "0.5.9"))
        assertFalse(newer("0.5.9", "0.5.9"))
        assertFalse(newer("0.5.8", "0.5.9"))
        // A bare release outranks the same triple with a suffix, not the reverse.
        assertTrue(newer("0.3.0", "0.3.0-beta1"))
        assertFalse(newer("0.3.0-beta1", "0.3.0"))
    }

    @Test
    fun forkBuildsCountUpWithinOneBaseVersion() {
        assertTrue(newer("0.5.9-arr.2", "0.5.9-arr.1"))
        assertTrue(newer("0.5.9-arr.10", "0.5.9-arr.9"))
        assertFalse(newer("0.5.9-arr.1", "0.5.9-arr.1"))
        assertFalse(newer("0.5.9-arr.1", "0.5.9-arr.2"))
        assertTrue(newer("0.3.0-beta2", "0.3.0-beta1"))
    }

    @Test
    fun aNewBaseVersionWinsOverAnyCounter() {
        assertTrue(newer("0.5.10-arr.1", "0.5.9-arr.7"))
        assertFalse(newer("0.5.9-arr.9", "0.5.10-arr.1"))
    }

    @Test
    fun differentSuffixLabelsAreNotComparable() {
        assertFalse(newer("0.5.9-arr.2", "0.5.9-beta1"))
        assertFalse(newer("0.5.9-rc", "0.5.9-beta"))
    }
}
