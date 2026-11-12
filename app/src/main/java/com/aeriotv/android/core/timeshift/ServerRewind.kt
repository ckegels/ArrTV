package com.aeriotv.android.core.timeshift

import android.util.Log
import com.aeriotv.android.core.network.DispatchMore
import com.aeriotv.android.core.network.DispatcharrClient
import com.aeriotv.android.core.network.RewindWindow
import com.aeriotv.android.core.playback.CatchupUrlBuilder
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Server rewind (Dispatch More v248, its fork/pause-resume.md §4.3): the server keeps the
 * recording of the channel being watched, so pausing and rewinding need nothing on this device
 * -- whose own disk ended long pauses (a Shield with 1 GB free).
 *
 * While a live channel plays on a server that offers it, this says so every [KEEP_ALIVE_MS]
 * (and at once on a pause, a rewind or going live), with where playback is when it is behind
 * live, so the server keeps everything from there on. [window] is what can be rewound into;
 * TimeshiftController shows it as the rewind window, and the player plays
 * [RewindWindow.playlist] (HLS) from the wall time asked for.
 */
@Singleton
class ServerRewind @Inject constructor(
    private val client: DispatcharrClient,
    private val repository: Provider<com.aeriotv.android.core.data.repository.PlaylistRepository>,
) {
    companion object {
        private const val TAG = "ServerRewind"
        const val KEEP_ALIVE_MS = 20_000L
        private const val MARKER = "/proxy/ts/stream/"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    /** The channel being kept, or null. */
    @Volatile var channelUuid: String? = null
        private set
    @Volatile private var base: String? = null
    @Volatile private var path: String = DispatchMore.DEFAULT_REWIND_PATH

    /** Where playback is while behind live (paused or rewound), wall clock; null at live. */
    @Volatile private var behindAtMs: Long? = null

    /** Asked at each keep-alive while behind live, so the server keeps from the playhead. */
    @Volatile var positionProvider: (() -> Long?)? = null

    private val _window = MutableStateFlow<RewindWindow?>(null)
    val window: StateFlow<RewindWindow?> = _window
    /** When [window] was answered, to move its head on between answers. */
    @Volatile var windowAtMs: Long = 0L
        private set

    /** Whether the server behind [streamUrl] offers server rewind (its capabilities said so). */
    fun offeredFor(streamUrl: String?): Boolean {
        val url = streamUrl ?: return false
        if (!url.contains(MARKER)) return false
        return DispatchMore.serverFor(url)?.rewind == true
    }

    /** Start keeping [streamUrl]'s channel; ends any other. */
    fun start(streamUrl: String) {
        val uuid = streamUrl.substringAfter(MARKER, "").substringBefore('?').substringBefore('/')
        if (uuid.isBlank()) return
        stop()
        channelUuid = uuid
        base = CatchupUrlBuilder.dispatcharrBaseFromStreamUrl(streamUrl)
        path = DispatchMore.serverFor(streamUrl)?.rewindPath ?: DispatchMore.DEFAULT_REWIND_PATH
        behindAtMs = null
        _window.value = null
        loop = scope.launch {
            while (true) {
                tell()
                delay(KEEP_ALIVE_MS)
            }
        }
    }

    /** Behind live from [wallMs] (a pause, a rewind), or back at live (null). */
    fun setBehind(wallMs: Long?) {
        behindAtMs = wallMs
        if (channelUuid != null) scope.launch { tell() }
    }

    /** Leave the channel: the server stops recording once nobody watches. */
    fun stop() {
        loop?.cancel()
        loop = null
        val uuid = channelUuid ?: return
        val b = base
        val p = path
        channelUuid = null
        _window.value = null
        scope.launch {
            val key = apiKey() ?: return@launch
            val viewer = DispatchMore.deviceId ?: "tv"
            if (b != null) client.rewindLeave(b, key, p, uuid, viewer)
        }
    }

    /** The playlist's full address, or null while nothing is recorded. */
    fun playlistUrl(): String? {
        val w = _window.value ?: return null
        val b = base ?: return null
        if (!w.recording || w.playlist.isBlank()) return null
        return b.trimEnd('/') + w.playlist
    }

    private suspend fun apiKey(): String? =
        runCatching { repository.get().activePlaylist()?.apiKey?.takeIf { it.isNotBlank() } }.getOrNull()

    private suspend fun tell() {
        val uuid = channelUuid ?: return
        val b = base ?: return
        val key = apiKey() ?: return
        val viewer = DispatchMore.deviceId ?: "tv"
        // The player is read on the main thread
        val at = behindAtMs?.let { paused ->
            positionProvider?.let { ask -> runCatching { withContext(Dispatchers.Main) { ask() } }.getOrNull() } ?: paused
        }
        val answer = client.rewindWatch(b, key, path, uuid, viewer, at)
        if (answer != null && channelUuid == uuid) {
            _window.value = answer
            windowAtMs = System.currentTimeMillis()
            if (!answer.enabled) Log.i(TAG, "server rewind off on the server; the device's own buffer is used")
        }
    }
}
