package com.aeriotv.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aeriotv.android.core.network.LiveCaption
import com.aeriotv.android.core.network.LiveCaptions
import com.aeriotv.android.core.playback.AerioExoPlayerHolder
import kotlinx.coroutines.delay

/**
 * Generated captions (Dispatch More v247, fork/subtitles.md §4): captions the server's caption
 * worker makes from the sound of the channel being watched, for channels without subtitles of
 * their own. Picked in the Subtitles menu like any track ([GENERATED_CAPTIONS_ID]).
 *
 * While picked, the server is asked about once a second for the lines made since the last
 * one; each carries the stream time (PTS) it belongs to, and a line is shown while the frame
 * on screen has that time ([AerioExoPlayerHolder.streamTimeNowSeconds]) -- so the text stays
 * with the picture whatever the delays. Leaving the channel, turning captions off or closing
 * the player tells the server it is done, and its job stops.
 */

/** The Subtitles menu's id for generated captions (track ids are 0 and up). */
const val GENERATED_CAPTIONS_ID = -1001

/**
 * The Subtitles menu's generated captions in a language: one entry per
 * [com.aeriotv.android.core.playback.CaptionLanguage] choice, so the language is picked where
 * the captions are (it was only in Settings -> Player, and only once Generated Captions was
 * on). The plain [GENERATED_CAPTIONS_ID] is the first choice, as spoken.
 */
fun generatedCaptionsId(languageIndex: Int): Int =
    if (languageIndex <= 0) GENERATED_CAPTIONS_ID else GENERATED_CAPTIONS_ID - languageIndex

/** The [com.aeriotv.android.core.playback.CaptionLanguage.CHOICES] index of a menu id, or -1. */
fun generatedCaptionsLanguageIndex(id: Int): Int {
    val index = GENERATED_CAPTIONS_ID - id
    return if (index in com.aeriotv.android.core.playback.CaptionLanguage.CHOICES.indices) index else -1
}

/** The Dispatcharr channel UUID in a live proxy URL (/proxy/ts/stream/<uuid>), or null. */
fun generatedCaptionsChannelUuid(streamUrl: String?): String? {
    val url = streamUrl ?: return null
    val marker = "/proxy/ts/stream/"
    val at = url.indexOf(marker).takeIf { it >= 0 } ?: return null
    return url.substring(at + marker.length).substringBefore('?').substringBefore('/').takeIf { it.isNotBlank() }
}

/**
 * The text to show at stream time [now]: the latest line that has begun, while its time holds
 * it (it stays a moment past its end, so a short one can be read). One line at a time: a line
 * that has begun replaces the one before at once -- shown together, the previous one lingering
 * over the new one made a mess (the user, 2026-10-01). Without a stream time (the extractor's
 * offset not known yet) the newest line, unsynced.
 */
fun captionLineAt(cues: List<LiveCaption>, now: Double?): String {
    if (cues.isEmpty()) return ""
    if (now == null) return cues.last().text
    val current = cues.lastOrNull { now >= it.start - 0.2 } ?: return ""
    return if (now <= current.end + 1.0) current.text else ""
}

/** What to say instead of captions, or "" while captions come. */
fun captionStatusLine(answer: LiveCaptions?): String = when (answer?.state) {
    null, "listening" -> ""
    "starting" -> "Captions: starting…"
    "loading model" -> "Captions: loading the speech model…"
    "busy" -> answer.reason.ifBlank { "Captions: the server is captioning as many channels as it can." }
    "not playing" -> ""
    "ended" -> "Captions: the channel stopped."
    "unsupported" -> "Captions: this server cannot make captions (Dispatch More v247 or later)."
    "off" -> answer.reason.ifBlank { "Captions are switched off on the server." }
    else -> "Captions: " + (answer.error.ifBlank { answer.reason }.ifBlank { answer.state })
}

@Composable
fun GeneratedCaptionsOverlay(
    exoHolder: AerioExoPlayerHolder,
    streamUrl: String?,
    active: Boolean,
    onPoll: suspend (streamUrl: String, channelUuid: String, since: Long) -> LiveCaptions?,
    onStop: (streamUrl: String, channelUuid: String) -> Unit,
) {
    val uuid = if (active) generatedCaptionsChannelUuid(streamUrl) else null
    val poll by rememberUpdatedState(onPoll)
    val stop by rememberUpdatedState(onStop)
    var shown by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(uuid, streamUrl) {
        shown = ""
        status = ""
        val channelUuid = uuid ?: return@LaunchedEffect
        val url = streamUrl ?: return@LaunchedEffect
        var since = 0L
        var cues = emptyList<LiveCaption>()
        try {
            status = "Captions: starting…"
            while (true) {
                val answer = poll(url, channelUuid, since)
                if (answer != null) {
                    if (answer.cues.isNotEmpty()) {
                        since = answer.cues.maxOf { it.seq }
                        cues = (cues + answer.cues).takeLast(60)
                    }
                    status = captionStatusLine(answer)
                }
                // Four looks at the picture's time per question to the server
                repeat(4) {
                    shown = captionLineAt(cues, exoHolder.streamTimeNowSeconds())
                    delay(250)
                }
            }
        } finally {
            stop(url, channelUuid)
        }
    }

    val text = shown.ifBlank { status }
    if (uuid != null && text.isNotBlank()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, bottom = 56.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = text,
                color = if (shown.isNotBlank()) Color.White else Color(0xFFBDBDBD),
                fontSize = if (shown.isNotBlank()) 26.sp else 16.sp,
                lineHeight = 32.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color(0xB3000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}
