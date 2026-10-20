package com.aeriotv.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeriotv.android.feature.dvr.DvrViewModel
import com.aeriotv.android.feature.settings.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Commercial breaks on a Dispatcharr recording: Comskip in "mark" mode leaves the recording
 * whole and Dispatch More (v239) puts the breaks on it as custom_properties.comskip.breaks,
 * [start, end] seconds. The player skips them, the way Plex, Jellyfin and Kodi do with
 * Comskip's marks, instead of the server cutting them out of the file -- a wrong guess then
 * costs nothing (the user, 2026-09-30).
 *
 * Settings -> DVR -> Commercial breaks: "auto" (default) skips a break as it starts and for a
 * few seconds offers to go back ("◀ Back"); "button" shows "Skip ad" while one plays; "off"
 * plays them. Kept out of VODPlayerScreen, which sits near ART's verifier limit: the screen
 * only draws [CommercialBreakSkipper] and hands it a key first ([onKey]).
 */
object CommercialBreaks {
    /** Whoever shows a break takes the remote's OK / Left for it; null when nothing is shown. */
    @Volatile
    private var keyHandler: ((Key) -> Boolean)? = null

    /** True when the press was for the break (skip, or go back). */
    fun onKey(key: Key): Boolean = keyHandler?.invoke(key) ?: false

    internal fun setKeyHandler(handler: ((Key) -> Boolean)?) {
        keyHandler = handler
    }

    /** The breaks in custom_properties.comskip.breaks, in milliseconds, in order. */
    fun parse(customProperties: JsonObject?): List<LongRange> {
        val comskip = customProperties?.get("comskip") as? JsonObject ?: return emptyList()
        val breaks = comskip["breaks"] as? JsonArray ?: return emptyList()
        return breaks.mapNotNull { pair ->
            val both = pair as? JsonArray ?: return@mapNotNull null
            val start = both.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val end = both.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            if (end <= start) null else (start * 1000).toLong()..(end * 1000).toLong()
        }.sortedBy { it.first }
    }

    /** The break playing at this position: from its start to a second and a half before its
     *  end, so a skip that lands a hair early is not taken for the same break again. */
    fun at(breaks: List<LongRange>, positionMs: Long): LongRange? =
        breaks.firstOrNull { positionMs >= it.first && positionMs < it.last - 1_500L }

    fun length(range: LongRange): String {
        val seconds = ((range.last - range.first) / 1_000L).coerceAtLeast(1L)
        return if (seconds >= 60) "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" else "${seconds}s"
    }
}

/** Only for a Dispatcharr recording (videoId "dvr-<id>"); draws nothing otherwise. */
@Composable
fun CommercialBreakSkipper(
    videoId: String?,
    positionOf: () -> Long,
    seekTo: (Long) -> Unit,
) {
    val recordingId = videoId?.removePrefix("dvr-")?.toIntOrNull()?.takeIf { videoId.startsWith("dvr-") } ?: return
    val dvrVm: DvrViewModel = hiltViewModel()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val mode by settingsVm.dvrCommercialBreaks.collectAsStateWithLifecycle(initialValue = "auto")
    var breaks by remember(recordingId) { mutableStateOf(emptyList<LongRange>()) }
    // The break playing now (button mode), and the one just skipped (auto mode's "Back")
    var current by remember { mutableStateOf<LongRange?>(null) }
    var skipped by remember { mutableStateOf<LongRange?>(null) }
    var skippedAt by remember { mutableStateOf(0L) }
    // A break the viewer went back into is theirs to watch: not skipped again
    val watched = remember(recordingId) { mutableSetOf<Long>() }
    val latestSeek by rememberUpdatedState(seekTo)
    val latestPosition by rememberUpdatedState(positionOf)

    LaunchedEffect(recordingId) {
        breaks = dvrVm.commercialBreaks(recordingId)
    }
    LaunchedEffect(breaks, mode) {
        if (breaks.isEmpty() || mode == "off") {
            current = null
            return@LaunchedEffect
        }
        while (true) {
            val here = CommercialBreaks.at(breaks, latestPosition())
            val justSkipped = here != null && here == skipped && System.currentTimeMillis() - skippedAt < 3_000L
            if (here != null && mode == "auto" && here.first !in watched && !justSkipped) {
                latestSeek(here.last)
                skipped = here
                skippedAt = System.currentTimeMillis()
                current = null
            } else if (!justSkipped) {
                current = if (mode == "button") here else null
            }
            if (skipped != null && System.currentTimeMillis() - skippedAt > 6_000L) skipped = null
            delay(400L)
        }
    }
    DisposableEffect(recordingId) {
        CommercialBreaks.setKeyHandler { key ->
            val back = skipped
            val now = current
            when {
                back != null && key == Key.DirectionLeft -> {
                    watched.add(back.first)
                    latestSeek(back.first)
                    skipped = null
                    true
                }
                now != null && (key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter) -> {
                    latestSeek(now.last)
                    current = null
                    true
                }
                else -> false
            }
        }
        onDispose { CommercialBreaks.setKeyHandler(null) }
    }

    val shownSkipped = skipped
    val shownCurrent = current
    if (shownSkipped == null && shownCurrent == null) return
    Box(modifier = Modifier.fillMaxSize().padding(end = 36.dp, bottom = 96.dp), contentAlignment = Alignment.BottomEnd) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable {
                    if (shownSkipped != null) {
                        watched.add(shownSkipped.first); seekTo(shownSkipped.first); skipped = null
                    } else if (shownCurrent != null) {
                        seekTo(shownCurrent.last); current = null
                    }
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            if (shownSkipped != null) {
                Text("Skipped an ad break (${CommercialBreaks.length(shownSkipped)})", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("◀ Back to watch it", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
            } else if (shownCurrent != null) {
                Text("Skip ad ›", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Press OK · ${CommercialBreaks.length(shownCurrent)}", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
            }
        }
    }
}
