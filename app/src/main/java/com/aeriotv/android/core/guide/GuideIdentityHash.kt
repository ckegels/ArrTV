package com.aeriotv.android.core.guide

import com.aeriotv.android.core.data.M3UChannel
import java.security.MessageDigest

/**
 * Stable fingerprint of a loaded channel list's identity: the sorted set of
 * canonical ids plus each channel's declared guide key. The EPG cache is
 * stamped with it on a successful fetch; a different fingerprint at paint
 * time means the cache was built for a different channel list and must be
 * refetched (docs/guide-semantics.md, section 4). Order-independent, so
 * re-sorting or re-grouping the same channels never invalidates the cache.
 */
object GuideIdentityHash {
    fun of(channels: List<M3UChannel>): String {
        if (channels.isEmpty()) return ""
        val md = MessageDigest.getInstance("SHA-1")
        keys(channels)
            .sorted()
            .forEach { md.update(it.toByteArray(Charsets.UTF_8)); md.update(0) }
        val sb = StringBuilder(40)
        for (b in md.digest()) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    private fun keys(channels: List<M3UChannel>): List<String> =
        channels.map { it.guideChannelId().value + "|" + GuideMatchMaps.normalize(it.tvgID) }

    private const val SIGNATURE_SLOTS = 32

    /**
     * How alike two channel lists are, kept small enough for a preference: a
     * MinHash of the same keys [of] hashes. A few channels added or removed
     * (a plugin moving channels between groups) leaves nearly every slot
     * equal; a list keyed another way leaves almost none. Empty for none.
     */
    fun signature(channels: List<M3UChannel>): String {
        if (channels.isEmpty()) return ""
        val mins = LongArray(SIGNATURE_SLOTS) { Long.MAX_VALUE }
        for (key in keys(channels)) {
            var h = -0x340d631b7bdddcdbL // FNV-1a 64
            for (c in key) { h = (h xor c.code.toLong()) * 0x100000001b3L }
            for (i in 0 until SIGNATURE_SLOTS) {
                val v = mix(h xor (i + 1).toLong() * -0x61c8864680b583ebL)
                if (v < mins[i]) mins[i] = v
            }
        }
        return mins.joinToString(",") { java.lang.Long.toHexString(it) }
    }

    /** The share of two [signature]s' slots that agree, which estimates the
     *  share of channels the lists have in common; 0 when either is missing. */
    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val x = a.split(','); val y = b.split(',')
        if (x.size != y.size) return 0.0
        return x.indices.count { x[it] == y[it] }.toDouble() / x.size
    }

    private fun mix(z0: Long): Long { // splitmix64 finaliser
        var z = z0
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }
}
