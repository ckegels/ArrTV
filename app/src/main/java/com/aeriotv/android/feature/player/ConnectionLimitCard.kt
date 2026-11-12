package com.aeriotv.android.feature.player

import androidx.compose.ui.res.stringResource
import com.aeriotv.android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aeriotv.android.core.playback.DispatcharrConnectionLimit

/**
 * Dispatcharr connection-limit notice (see [DispatcharrConnectionLimit]).
 * Same look as the player's Channel Unavailable / Catch-up Unavailable cards,
 * but deliberately static: the server refused on purpose, so there is no
 * countdown, no auto-retry and no Reconnecting line. Retry is one fresh
 * attempt. [compact] is the multiview tile size.
 */
@Composable
fun ConnectionLimitCard(
    notice: DispatcharrConnectionLimit.Notice,
    isTv: Boolean,
    onRetry: () -> Unit,
    compact: Boolean = false,
    /** Watch together: join the device that took over (another-device notice only). */
    onJoin: (() -> Unit)? = null,
    /** On a TV, which button the remote is on (0 = the first, 1 = Join): OK presses it. */
    choice: Int = 0,
) {
    val joinable = onJoin != null && notice.kind == DispatcharrConnectionLimit.Kind.OTHER_DEVICE &&
        notice.byDevice != null
    val retryFocus = remember { FocusRequester() }
    if (isTv && !compact) {
        LaunchedEffect(notice) {
            runCatching { retryFocus.requestFocus() }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (compact) 0.82f else 0.72f))
            .padding(if (compact) 12.dp else 32.dp),
        verticalArrangement = Arrangement.spacedBy(
            if (compact) 6.dp else 10.dp,
            Alignment.CenterVertically,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = notice.title,
            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            text = notice.message,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            maxLines = if (compact) 3 else Int.MAX_VALUE,
        )
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // On a TV the player handles the remote (Left / Right, OK): the chosen button
            // is drawn filled, the other outlined
            val filled = androidx.compose.material3.ButtonDefaults.buttonColors()
            val outlined = androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
            val colors = { index: Int -> if (!isTv || !joinable || choice == index) filled else outlined }
            Button(
                onClick = onRetry,
                modifier = Modifier.focusRequester(retryFocus),
                colors = colors(0),
            ) {
                // Another TV took this login's stream: pressing takes it back
                Text(
                    stringResource(
                        if (notice.kind == DispatcharrConnectionLimit.Kind.OTHER_DEVICE) {
                            R.string.player_watch_here
                        } else {
                            R.string.player_retry
                        },
                    ),
                )
            }
            if (joinable) {
                // Watch together: follow that device's channel from now on
                Button(onClick = { onJoin?.invoke() }, colors = colors(1)) {
                    Text(stringResource(R.string.player_join_other_device))
                }
            }
        }
        if (!compact &&
            notice.kind != DispatcharrConnectionLimit.Kind.STREAM_ENDED &&
            notice.kind != DispatcharrConnectionLimit.Kind.OTHER_DEVICE
        ) {
            Text(
                text = stringResource(R.string.player_close_another_stream),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
