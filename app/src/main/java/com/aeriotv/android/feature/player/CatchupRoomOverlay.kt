package com.aeriotv.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeriotv.android.core.playback.AerioExoPlayerHolder
import com.aeriotv.android.core.playback.CatchupRoom

/**
 * Look-back priority (Dispatch More v248): while the server moves another viewer off the
 * archive's provider, its steps one under the other -- the earlier ones dimmed, the latest with
 * a spinner -- over whatever screen started the look back. And on the moved viewer's TV, a toast
 * when its own channel was the one moved.
 */
@Composable
fun CatchupRoomOverlay(holder: AerioExoPlayerHolder) {
    val progress by CatchupRoom.progress.collectAsStateWithLifecycle()
    val moved by CatchupRoom.moved.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(moved) {
        val notice = moved ?: return@LaunchedEffect
        if (System.currentTimeMillis() - notice.atMs > 10_000) return@LaunchedEffect
        if (holder.currentPlayUrl?.contains(notice.channelUuid) == true) {
            android.widget.Toast.makeText(context, notice.text, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    val lines = progress?.lines?.filter { it.isNotBlank() }.orEmpty()
    if (lines.isEmpty()) return
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(16.dp))
                .padding(horizontal = 28.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Starting look back", style = MaterialTheme.typography.titleMedium, color = Color.White)
            lines.forEachIndexed { i, line ->
                val last = i == lines.lastIndex
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (last) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("✓", color = Color.White.copy(alpha = 0.6f))
                    }
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (last) Color.White else Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}
