package com.aeriotv.android.core.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Look-back priority (Dispatch More v248, its fork/lookback-priority.md): the only provider with
 * a programme's archive was busy with another viewer's live channel, and the server is moving
 * that viewer to another stream of theirs. While it does, the steps it reports are shown over
 * whatever screen started the look back (CatchupRoomOverlay), one line each, the last one live.
 */
object CatchupRoom {
    data class Progress(val lines: List<String>, val done: Boolean = false)

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress

    fun show(lines: List<String>) {
        _progress.value = Progress(lines)
    }

    fun clear() {
        _progress.value = null
    }

    /** This TV's channel was moved to another stream for someone's look back (the server's
     *  socket message `lookback_moved`); shown only when it is the channel playing here. */
    data class Moved(val channelUuid: String, val text: String, val atMs: Long = System.currentTimeMillis())

    private val _moved = MutableStateFlow<Moved?>(null)
    val moved: StateFlow<Moved?> = _moved

    fun moved(channelUuid: String, text: String) {
        _moved.value = Moved(channelUuid, text)
    }
}
