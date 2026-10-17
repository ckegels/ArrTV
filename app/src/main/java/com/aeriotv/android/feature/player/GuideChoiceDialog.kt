package com.aeriotv.android.feature.player

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aeriotv.android.core.network.DispatchMoreGuides
import com.aeriotv.android.core.network.DispatcharrClient
import java.text.DateFormat
import java.util.Date

/** What the list is about: the stream being watched, and its channel's name.
 *  [report] is the problem report "Could not find the guide" sends, read at the
 *  press like the report menu's; null where the server takes no reports. */
internal data class GuideChoiceRequest(
    val streamUrl: String,
    val channelName: String,
    val report: ProblemReportRequest? = null,
)

/**
 * "Wrong guide? Choose another" (Dispatch More, contract 7a): what the
 * channel's guide says now, then the guides it could be on, each with the
 * programme on it now in large type -- that is what gets compared with the
 * picture. One press on a row chooses it and closes the list; the change is
 * made in the background and a short notice says how it went. Back closes
 * without changing anything.
 *
 * The server sends the list a page at a time, best first and less sure the
 * further down (v217). "Load more" at the bottom asks for the next page for
 * as long as the server says there is one; the focus moves to its first row.
 * While the server says it is still reading guides nobody had read, the
 * latest page is asked for again every [READING_AGAIN_MS], at most
 * [READING_ASKS] times, without moving the focus.
 */
@Composable
internal fun GuideChoiceDialog(
    request: GuideChoiceRequest,
    guides: DispatchMoreGuides,
    onDismiss: () -> Unit,
    /** "Could not find the guide": sends the report; null hides the button. */
    onCouldNotFind: (() -> Unit)? = null,
) {
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }
    var current by remember { mutableStateOf<DispatcharrClient.GuideOption?>(null) }
    var pages by remember { mutableStateOf(listOf<List<DispatcharrClient.GuideOption>>()) }
    var reading by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var pagesWanted by remember { mutableIntStateOf(1) }
    var focusedPage by remember { mutableIntStateOf(-1) }
    val pageStart = remember { FocusRequester() }
    val loadMoreButton = remember { FocusRequester() }
    val closeButton = remember { FocusRequester() }

    LaunchedEffect(request, pagesWanted) {
        val page = pagesWanted - 1
        val before = pages.take(page).flatten().map { it.epgId }
        loadingMore = page > 0
        fun apply(answer: DispatcharrClient.GuideAnswer) {
            val listed = (answer as? DispatcharrClient.GuideAnswer.Listed)?.choices
            if (listed == null) {
                if (page == 0 && !loaded) failed = (answer as? DispatcharrClient.GuideAnswer.Refused)?.message
                    ?: "Unexpected answer"
                return
            }
            if (page == 0) current = listed.current
            val seen = before.toSet()
            pages = pages.take(page) + listOf(listed.guides.filter { it.epgId !in seen })
            reading = listed.reading
            more = listed.more
        }
        apply(guides.list(request.streamUrl, before))
        loaded = true
        loadingMore = false
        var asks = 0
        while (reading && asks < READING_ASKS) {
            kotlinx.coroutines.delay(READING_AGAIN_MS)
            asks++
            apply(guides.list(request.streamUrl, before))
        }
        reading = false
    }

    // At most 90 % of the screen, the list in the middle giving way: a TV is 540 dp tall,
    // and the list's 420 dp with everything around it came to ~700, so the dialog, centred,
    // lost its title and the channel's name off the top and its buttons off the bottom
    val maxHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.9f).dp
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(min = 420.dp, max = 640.dp).heightIn(max = maxHeight),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Wrong guide? Choose another", style = MaterialTheme.typography.titleLarge)
                // Which channel this is about: the list is compared with its picture
                if (request.channelName.isNotBlank()) {
                    Text(
                        request.channelName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                when {
                    failed != null -> Text(failed.orEmpty())
                    !loaded -> {
                        Text("Looking for the guides ${request.channelName} could be on…")
                        CircularProgressIndicator(Modifier.padding(8.dp))
                    }
                    else -> GuideList(
                        current = current,
                        pages = pages,
                        reading = reading,
                        more = more,
                        loadingMore = loadingMore,
                        pageStart = pageStart,
                        loadMoreButton = loadMoreButton,
                        onLoadMore = { if (!loadingMore) pagesWanted = pages.size + 1 },
                    ) { option ->
                        guides.chooseInBackground(request.streamUrl, option)
                        onDismiss()
                    }
                }
                // At the very bottom: none of these is it. Sends the same report as
                // the report menu's "Wrong or missing guide", and closes.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onCouldNotFind != null) {
                        TextButton(onClick = { onCouldNotFind(); onDismiss() }) {
                            Text("Could not find the guide")
                        }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.focusRequester(closeButton)) {
                        Text("Close")
                    }
                }
            }
        }
    }

    // D-pad focus: the first row of each page as it arrives (the first guide
    // at the start, the first new one after "Load more"); Load more or Close
    // when a page brought none. A page that grows while it is read leaves it.
    val lastPageHasRows = pages.lastOrNull()?.isNotEmpty() == true
    LaunchedEffect(loaded, failed, pages.size, lastPageHasRows, loadingMore) {
        if (!loaded && failed == null) return@LaunchedEffect
        if (loadingMore) return@LaunchedEffect
        val page = pages.size - 1
        if (page <= focusedPage) return@LaunchedEffect
        // The dialog's window composes a moment after this effect starts
        kotlinx.coroutines.delay(100)
        runCatching {
            when {
                // Only a page with rows counts as focused: an empty one that
                // fills while it is read still gets its first row focused
                lastPageHasRows -> pageStart.requestFocus().also { focusedPage = page }
                more -> loadMoreButton.requestFocus()
                else -> closeButton.requestFocus()
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.GuideList(
    current: DispatcharrClient.GuideOption?,
    pages: List<List<DispatcharrClient.GuideOption>>,
    reading: Boolean,
    more: Boolean,
    loadingMore: Boolean,
    pageStart: FocusRequester,
    loadMoreButton: FocusRequester,
    onLoadMore: () -> Unit,
    onChoose: (DispatcharrClient.GuideOption) -> Unit,
) {
    Text(
        when {
            current == null -> "Guide now: none"
            current.now == null -> "Guide now: ${current.name} — no information"
            else -> "Guide now: ${current.name} — ${current.now.title} (${span(current.now)})"
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val all = pages.flatten()
    val firstOfLastPage = pages.lastOrNull()?.firstOrNull()
    if (all.isEmpty() && !reading && !more) {
        Text("No other guide has anything on for this channel right now.")
    }
    if (all.isNotEmpty() || more || loadingMore) {
        LazyColumn(
            // Takes what the title, the channel and the buttons leave, and scrolls
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(all, key = { it.epgId }) { option ->
                GuideRow(
                    option = option,
                    modifier = if (option == firstOfLastPage) Modifier.focusRequester(pageStart) else Modifier,
                    onClick = { onChoose(option) },
                )
            }
            if (loadingMore) {
                item(key = "loading-more") {
                    Text("Loading more…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (more) {
                item(key = "load-more") {
                    TextButton(onClick = onLoadMore, modifier = Modifier.focusRequester(loadMoreButton)) {
                        Text("Load more")
                    }
                }
            }
        }
    }
    if (reading) {
        Text("Looking for more guides…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GuideRow(option: DispatcharrClient.GuideOption, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            // What is on now, large: this is what is compared with the picture
            Text(
                option.now?.title.orEmpty(),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    option.name.ifBlank { null },
                    option.source.ifBlank { null },
                    option.now?.endMs?.let { "until ${time(it)}" },
                ).joinToString(" · "),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            option.next?.let { next ->
                Text(
                    "Next: ${next.title}" + (next.startMs?.let { " (${time(it)})" } ?: ""),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun span(programme: DispatcharrClient.GuideProgramme): String =
    listOfNotNull(programme.startMs?.let(::time), programme.endMs?.let(::time)).joinToString("–")

private fun time(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

/** The server was still reading guides: ask again after this long, at most this often. */
private const val READING_AGAIN_MS = 5_000L
private const val READING_ASKS = 12
