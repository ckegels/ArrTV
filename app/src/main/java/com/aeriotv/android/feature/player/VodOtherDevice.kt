package com.aeriotv.android.feature.player

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.aeriotv.android.core.network.DispatchMore
import com.aeriotv.android.core.playback.DispatcharrConnectionLimit
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A login held to one device at a time (Dispatch More, contract §5a) in the on-demand player:
 * another device of it started something, and the server closed this movie, episode or look
 * back. Shown instead of the error card, with Watch here and nothing retried by itself: an
 * automatic retry would take the stream back from that device at every attempt.
 *
 * Kept out of VODPlayerScreen, which is at the JVM verifier's register limit: the notice lives
 * here, and the screen only sets it (the refusal of a request) and reads it (its key handler).
 */
object VodOtherDevice {
    val notice = MutableStateFlow<DispatcharrConnectionLimit.Notice?>(null)
}

@Composable
fun VodOtherDeviceOverlay(player: Player?, isTv: Boolean, onWatchHere: () -> Unit) {
    val currentPlayer by rememberUpdatedState(player)
    val currentWatchHere by rememberUpdatedState(onWatchHere)
    // The server's socket message: stop at once, rather than when the refusal of the next
    // request arrives (after the buffer has played out, which looked like nothing stopped)
    LaunchedEffect(Unit) {
        DispatcharrConnectionLimit.replaced.collect { notice ->
            currentPlayer?.let { p ->
                p.playWhenReady = false
                p.stop()
            }
            Log.w("VODPlayer", "[LIMIT] ${notice.kind} (socket) \"${notice.title}\"; waiting for Watch here")
            VodOtherDevice.notice.value = notice
        }
    }
    // A notice belongs to this screen: the next movie starts without it
    DisposableEffect(Unit) { onDispose { VodOtherDevice.notice.value = null } }
    val notice by VodOtherDevice.notice.collectAsStateWithLifecycle()
    notice?.let {
        ConnectionLimitCard(
            notice = it,
            isTv = isTv,
            onRetry = {
                DispatchMore.takeOver()
                VodOtherDevice.notice.value = null
                currentWatchHere()
            },
        )
    }
}
