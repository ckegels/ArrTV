package com.aeriotv.android.core.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * The stream's own clock for watching together in step (WatchTogether): the PTS of the first
 * audio or video packet of a live connection. Media3's TS extractor counts the player's
 * position from that same first timestamp, so first PTS + position is the broadcast moment on
 * screen, and two devices fed the same bytes by one channel's buffer compare exactly.
 *
 * Reads along, changes nothing: the bytes pass through as they came, and after the first
 * timestamp (or [GIVE_UP_BYTES] without one) the probe stops looking.
 */
@UnstableApi
class LivePtsProbe(private val upstream: DataSource, private val onFirstPts: (Long) -> Unit) : DataSource {

    private val finder = FirstPtsFinder()

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        finder.reset()
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val n = upstream.read(buffer, offset, length)
        if (n > 0 && !finder.done) finder.feed(buffer, offset, n)?.let(onFirstPts)
        return n
    }

    override fun getUri() = upstream.uri

    override fun getResponseHeaders() = upstream.responseHeaders

    override fun close() = upstream.close()

    class Factory(private val upstream: DataSource.Factory, private val onFirstPts: (Long) -> Unit) : DataSource.Factory {
        override fun createDataSource(): DataSource = LivePtsProbe(upstream.createDataSource(), onFirstPts)
    }

    companion object {
        const val GIVE_UP_BYTES = 1024 * 1024
    }
}

/**
 * Finds the first PES timestamp (audio, video or private stream) in a TS byte stream.
 *
 * Each byte is looked at once: only the unfinished packet at the end of a read is kept for the
 * next (at most a few hundred bytes). It used to keep everything read so far and scan it all
 * again at every read, which on a slow TV box -- on the thread that loads the stream -- could
 * cost what it was meant to save.
 */
internal class FirstPtsFinder {
    private val carry = ByteArray(PACKET * 3)
    private var carryLen = 0
    private var aligned = false
    private var seen = 0

    /** Found, or given up. */
    var done = false
        private set

    fun reset() {
        carryLen = 0; aligned = false; seen = 0; done = false
    }

    /** The first PTS (90 kHz) once these bytes complete it, else null. */
    fun feed(b: ByteArray, off: Int, len: Int): Long? {
        if (done) return null
        seen += len
        // The kept bytes and as much of the new ones as can be packets
        val data = if (carryLen == 0) b else ByteArray(carryLen + len).also {
            System.arraycopy(carry, 0, it, 0, carryLen)
            System.arraycopy(b, off, it, carryLen, len)
        }
        var p = if (carryLen == 0) off else 0
        val end = if (carryLen == 0) off + len else carryLen + len
        carryLen = 0
        if (!aligned) {
            // The first packet boundary: three sync bytes 188 apart
            while (p + PACKET * 2 < end &&
                !(data[p] == SYNC && data[p + PACKET] == SYNC && data[p + PACKET * 2] == SYNC)
            ) p++
            if (p + PACKET * 2 >= end) {
                keep(data, p, end)
                return giveUpIfLong()
            }
            aligned = true
        }
        while (p + PACKET <= end) {
            if (data[p] != SYNC) {
                // Lost the packet boundary: find it again from here
                aligned = false
                keep(data, p, end)
                return giveUpIfLong()
            }
            pts(data, p)?.let { done = true; return it }
            p += PACKET
        }
        keep(data, p, end)
        return giveUpIfLong()
    }

    private fun keep(data: ByteArray, from: Int, end: Int) {
        val n = minOf(end - from, carry.size)
        System.arraycopy(data, end - n, carry, 0, n)
        carryLen = n
    }

    private fun giveUpIfLong(): Long? {
        if (seen > LivePtsProbe.GIVE_UP_BYTES) done = true
        return null
    }

    /** The PTS of the PES this packet starts, if it is audio, video or a private stream. */
    private fun pts(d: ByteArray, p: Int): Long? {
        val unitStart = d[p + 1].toInt() and 0x40 != 0
        val adaptation = (d[p + 3].toInt() shr 4) and 0x3
        var payload = p + 4
        if (adaptation == 2 || adaptation == 3) payload += 1 + (d[p + 4].toInt() and 0xFF)
        if (!unitStart || !(adaptation == 1 || adaptation == 3) || payload + 14 > p + PACKET) return null
        if (d[payload] != 0.toByte() || d[payload + 1] != 0.toByte() || d[payload + 2] != 1.toByte()) return null
        val streamId = d[payload + 3].toInt() and 0xFF
        val media = streamId in 0xC0..0xEF || streamId == 0xBD
        val hasPts = d[payload + 7].toInt() and 0x80 != 0
        return if (media && hasPts) readTs(d, payload + 9) else null
    }

    companion object {
        const val PACKET = 188
        const val SYNC = 0x47.toByte()

        fun readTs(b: ByteArray, o: Int): Long =
            ((b[o].toLong() shr 1) and 0x07 shl 30) or
                ((b[o + 1].toLong() and 0xFF) shl 22) or
                (((b[o + 2].toLong() and 0xFF) shr 1) shl 15) or
                ((b[o + 3].toLong() and 0xFF) shl 7) or
                ((b[o + 4].toLong() and 0xFF) shr 1)
    }
}

/** Where two devices showing one channel are, from their stream timestamps. */
object InStep {
    private const val WRAP = 1L shl 33

    /**
     * How far ahead (ms, negative when behind) this device is of the other: [myPts] shown at
     * [myAtMs], [theirPts] at [theirAtMs] (both wall-clock ms), the other projected to this
     * device's moment at normal speed. PTS wraps every 2^33 ticks (26.5 h).
     */
    fun aheadMs(myPts: Long, myAtMs: Long, theirPts: Long, theirAtMs: Long): Long {
        val theirsNow = theirPts + (myAtMs - theirAtMs) * 90
        var d = Math.floorMod(myPts - theirsNow, WRAP)
        if (d > WRAP / 2) d -= WRAP
        return d / 90
    }

    /** The position on screen as a PTS: the first timestamp plus the player's position. */
    fun shown(firstPts: Long, positionMs: Long): Long = Math.floorMod(firstPts + positionMs * 90, WRAP)
}

/**
 * Whether a device behind the one it watches together with should catch up by tuning afresh.
 *
 * A fresh connection only takes away what a stall, a pause or a rewind added. What a device
 * itself adds -- a slower start, a deeper buffer before it plays -- stays: on a Pixel 9 against
 * a Shield (2026-10-17) that was about 5 s, every fresh tune landed 5 s behind again, and with
 * a 1.5 s slack the phone reconnected every 30 s, which looked like endless buffering. So:
 * only more than [SLACK_MS] behind (the user: 5 s apart is fine), at most once per [MIN_GAP_MS],
 * and when one catch-up did not take away at least [IMPROVE_MS], not again on this channel.
 */
class InStepCatchUp {
    private var channel: String? = null
    private var lastTuneAtMs = Long.MIN_VALUE / 2
    private var behindBeforeTune: Long? = null
    private var givenUp = false

    /** True: tune afresh now. [behindMs] is how far behind the other this device shows. */
    fun decide(channelId: String?, behindMs: Long, nowMs: Long): Boolean {
        if (channelId != channel) {
            channel = channelId
            lastTuneAtMs = Long.MIN_VALUE / 2
            behindBeforeTune = null
            givenUp = false
        }
        if (givenUp) return false
        behindBeforeTune?.let { before ->
            behindBeforeTune = null
            // The first look after a catch-up: no better, so it is this device's own delay
            if (behindMs > SLACK_MS && before - behindMs < IMPROVE_MS) {
                givenUp = true
                return false
            }
        }
        if (behindMs <= SLACK_MS || behindMs > MAX_MS) return false
        if (nowMs - lastTuneAtMs < MIN_GAP_MS) return false
        lastTuneAtMs = nowMs
        behindBeforeTune = behindMs
        return true
    }

    companion object {
        const val SLACK_MS = 6_000L
        const val IMPROVE_MS = 3_000L
        const val MIN_GAP_MS = 120_000L
        /** Further than this is not the same moment of one broadcast (a wrap, a restart). */
        const val MAX_MS = 15 * 60_000L
    }
}
