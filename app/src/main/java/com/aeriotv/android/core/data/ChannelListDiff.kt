package com.aeriotv.android.core.data

/**
 * What changed between two channel lists, judged only on the fields the UI
 * and the guide actually use. Lets a refresh that returned the same lineup
 * (the common case for a scheduled or server-triggered refresh) skip the
 * state update and guide rebuild entirely.
 *
 * Deliberately not `M3UChannel.equals`: a list restored from the channel
 * snapshot and one freshly parsed from the network can differ in fields the
 * snapshot does not round-trip (e.g. [M3UChannel.rawAttributes]) while being
 * the same lineup on screen.
 */
data class ChannelListDiff(
    val added: Int,
    val removed: Int,
    /** Present in both lists with a different name, number, group, logo, url, guide key or catch-up. */
    val changed: Int,
    /** Same channels, different order. */
    val reordered: Boolean,
) {
    val hasChanges: Boolean get() = added > 0 || removed > 0 || changed > 0 || reordered

    override fun toString(): String =
        "added=$added removed=$removed changed=$changed reordered=$reordered"

    companion object {
        fun between(old: List<M3UChannel>, new: List<M3UChannel>): ChannelListDiff {
            val oldById = old.associateBy { it.id }
            val newIds = new.mapTo(HashSet(new.size)) { it.id }
            var added = 0
            var changed = 0
            for (ch in new) {
                val before = oldById[ch.id]
                if (before == null) added++ else if (signature(before) != signature(ch)) changed++
            }
            val removed = old.count { it.id !in newIds }
            val reordered = added == 0 && removed == 0 && old.map { it.id } != new.map { it.id }
            return ChannelListDiff(added, removed, changed, reordered)
        }

        /** The channels of [new] that are not in [old], or are with a different signature. */
        fun addedOrChanged(old: List<M3UChannel>, new: List<M3UChannel>): List<M3UChannel> {
            val oldById = old.associateBy { it.id }
            return new.filter { ch -> oldById[ch.id]?.let { signature(it) != signature(ch) } ?: true }
        }

        private fun signature(c: M3UChannel): List<Any?> = listOf(
            c.name, c.url, c.groupTitle, c.tvgID, c.tvgLogo, c.channelNumber,
            c.catchupDays, c.dispatcharrChannelId, c.drmLicenseType, c.drmLicenseKey,
        )
    }
}
