package com.aeriotv.android.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.aeriotv.android.core.data.EPGProgramme
import com.aeriotv.android.core.data.M3UChannel
import com.aeriotv.android.core.ui.ClockFormat
import com.aeriotv.android.core.ui.SkipIntervals
import com.aeriotv.android.core.ui.rememberClockMode
import com.aeriotv.android.core.ui.seasonEpisodeLabel
import com.aeriotv.android.ui.tv.tvFocusScale
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * What the TV "info bar" overlay style needs beyond the current channel and
 * programme (Settings > Player > Overlay Style). Null on the chrome = the
 * standard info card and control circles.
 */
@Immutable
class TvInfoBarModel(
    /** The channels after the current one in guide order (wrapping), for the
     *  card row on OK. */
    val upcoming: List<M3UChannel>,
    /** 1-based position of the current channel in the list it was tuned
     *  from, shown as its number (the guide's TiviMate numbering); null =
     *  the provider's channel number. */
    val position: Int? = null,
    val nextProgramme: EPGProgramme?,
    /** Current programme of any channel, for the channel cards. */
    val nowFor: (M3UChannel) -> EPGProgramme?,
    val onTuneChannel: (M3UChannel) -> Unit,
    val onOpenGuide: () -> Unit,
    val onOpenHistory: () -> Unit,
)

/**
 * The Live Rewind buffer the OK view's transport buttons move in; null while
 * no buffer rolls (the buttons beside play / pause are then dimmed).
 */
@Immutable
class InfoBarTransport(
    val tailWallMs: Long,
    val headWallMs: Long,
    /** Where playback is, on the wall clock (the head when live). */
    val positionWallMs: Long,
    val onSeekWall: (Long) -> Unit,
    val onGoLive: () -> Unit,
) {
    /** More than a few seconds behind the live edge. */
    val behind: Boolean get() = headWallMs - positionWallMs > 5_000
}

/**
 * A look-back replay shown in the info bar (the user, 2026-10-02: the replay had a black panel
 * and a box top left, unlike the live bar): the programme's times and progress follow the
 * playback position instead of the clock, the next-programme line says when it aired, and the
 * card row and LIVE stay away.
 */
@Immutable
class InfoBarReplay(
    /** "Look back · Thu, Oct 1", in place of the next programme. */
    val label: String,
    /** Where playback is, on the wall clock of the programme's airing. */
    val positionWallMs: Long,
)

/**
 * Info bar overlay (TV). Two states:
 *  - zapping / launch hint ([expanded] false): group name top left, clock top
 *    right, and along the bottom the channel logo with the programme title,
 *    time / progress / channel line, description and the next programme;
 *  - OK ([expanded] true): the same bar without the description, a timeline
 *    across the screen, under it the elapsed time, the transport buttons
 *    (start, back, play / pause, forward, live) and LIVE, and a focusable row
 *    of TV guide, History and the next channels' cards.
 * Holding OK opens the options menu (PlayerScreen's key handler); so does
 * Down from the card row ([onOpenOptions]).
 */
@Composable
internal fun PlayerInfoBarOverlay(
    visible: Boolean,
    expanded: Boolean,
    channel: M3UChannel?,
    programme: EPGProgramme?,
    model: TvInfoBarModel,
    formatBadge: String?,
    sleepRemainingMillis: Long?,
    /** Live Rewind timeline (focusable) while a buffer rolls; null = a
     *  read-only programme progress line. */
    timeline: (@Composable () -> Unit)?,
    onInteraction: () -> Unit,
    /** Play / pause (OK view): Up from the card row reaches the button. */
    paused: Boolean = false,
    onTogglePause: () -> Unit = {},
    /** The transport buttons' buffer; null = only play / pause works. */
    transport: InfoBarTransport? = null,
    /** Down from the card row: the options menu. */
    onOpenOptions: () -> Unit = {},
    /** A look-back replay (see [InfoBarReplay]); null = live. */
    replay: InfoBarReplay? = null,
) {
    // Read when the card row wants the focus, not when it was drawn
    val visibleNow by androidx.compose.runtime.rememberUpdatedState(visible)
    AnimatedVisibility(
        visible = visible && channel != null,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val ch = channel ?: return@AnimatedVisibility
        // A synthesized placeholder (a channel without guide data) is not a
        // programme: show the channel alone rather than its made-up times.
        val programme = programme?.takeUnless { it.isPlaceholder }
        Box(modifier = Modifier.fillMaxSize()) {
            // Scrims: a light one behind the top labels, a deeper one rising
            // from the bottom behind the bar (taller when the cards show).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(if (expanded) 0.75f else 0.55f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Black.copy(alpha = 0.6f),
                            1f to Color.Black.copy(alpha = 0.9f),
                        ),
                    ),
            )

            Text(
                text = ch.groupTitle,
                style = MaterialTheme.typography.titleMedium.merge(LABEL_SHADOW),
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.align(Alignment.TopStart).padding(start = EDGE, top = 24.dp),
            )
            ClockLabel(modifier = Modifier.align(Alignment.TopEnd).padding(end = EDGE, top = 20.dp))

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(bottom = if (expanded) 8.dp else 28.dp),
            ) {
                InfoBarHeader(
                    channel = ch,
                    number = model.position?.toString() ?: ch.channelNumber,
                    programme = programme,
                    nextProgramme = if (replay != null) null else model.nextProgramme,
                    formatBadge = formatBadge,
                    sleepRemainingMillis = sleepRemainingMillis,
                    showDescription = !expanded,
                    atMs = replay?.positionWallMs,
                    replayLabel = replay?.label,
                )
                if (expanded) {
                    // Up from ANY card lands on play / pause (not on whatever
                    // is nearest above it: from History or a channel card that
                    // was the timeline), and Up again on the timeline to seek.
                    val playPauseFocus = remember { FocusRequester() }
                    val timelineFocus = remember { FocusRequester() }
                    Spacer(Modifier.height(14.dp))
                    // The timeline across the screen, and under it the
                    // transport row (Up from the cards lands on play / pause,
                    // Up again on the timeline to seek).
                    Box(modifier = Modifier.fillMaxWidth().focusRequester(timelineFocus).focusGroup()) {
                        if (timeline != null) {
                            timeline()
                        } else {
                            ProgrammeLine(programme, modifier = Modifier.padding(horizontal = EDGE))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TransportRow(
                        programme = programme,
                        paused = paused,
                        transport = transport,
                        onTogglePause = { onInteraction(); onTogglePause() },
                        onInteraction = onInteraction,
                        playPauseModifier = Modifier.focusRequester(playPauseFocus),
                        up = if (timeline != null) timelineFocus else null,
                        replay = replay != null,
                    )
                    // A replay has no card row to take the focus: it goes to play / pause, or
                    // the remote's keys reached the player behind the bar (nothing focused)
                    if (replay != null) {
                        LaunchedEffect(Unit) {
                            repeat(10) {
                                if (runCatching { playPauseFocus.requestFocus() }.isSuccess) return@LaunchedEffect
                                delay(50)
                            }
                        }
                    }
                    // A replay is the lesser bar: no guide / history / channel cards
                    if (replay == null) {
                        Spacer(Modifier.height(12.dp))
                        InfoBarCardRow(
                            model = model,
                            onInteraction = onInteraction,
                            up = playPauseFocus,
                            onDown = onOpenOptions,
                            active = { visibleNow },
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.CenterHorizontally).size(28.dp),
                        )
                    } else {
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoBarHeader(
    channel: M3UChannel,
    number: String?,
    programme: EPGProgramme?,
    nextProgramme: EPGProgramme?,
    formatBadge: String?,
    sleepRemainingMillis: Long?,
    showDescription: Boolean,
    /** A replay's playback position (wall clock of the airing); null = now. */
    atMs: Long? = null,
    /** A replay's "Look back · day" line, in place of the next programme. */
    replayLabel: String? = null,
) {
    val clockMode = rememberClockMode()
    val timeFormat = remember(clockMode) { ClockFormat.short(clockMode) }
    // Re-read "now" every 30 s so the progress and remaining minutes move.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = EDGE),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(width = 150.dp, height = 96.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (channel.tvgLogo.isNotBlank()) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(channel.tvgLogo).size(Size.ORIGINAL).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            val title = programme?.let { p ->
                listOfNotNull(p.title, p.seasonEpisodeLabel()).joinToString(" ")
            } ?: channel.name
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.merge(LABEL_SHADOW),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (programme != null) {
                    Text(
                        text = timeRange(programme, timeFormat),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                    Spacer(Modifier.width(14.dp))
                    MiniProgress(fraction = progressOf(programme, atMs ?: now))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = remainingLabel(programme, atMs ?: now),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                    Spacer(Modifier.width(22.dp))
                }
                number?.takeIf { it.isNotBlank() }?.let { num ->
                    Text(
                        text = num,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                formatBadge?.split(" · ")?.filter { it.isNotBlank() }?.forEach { part ->
                    Spacer(Modifier.width(10.dp))
                    InfoBarChip(part.uppercase(Locale.getDefault()))
                }
                sleepRemainingMillis?.let { remaining ->
                    Spacer(Modifier.width(10.dp))
                    InfoBarChip("SLEEP ${(remaining / 60_000L).coerceAtLeast(0L)} MIN")
                }
            }
            val description = programme?.description?.takeIf { it.isNotBlank() }
            if (showDescription && description != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (replayLabel != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = replayLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                )
            }
            if (nextProgramme != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeRange(nextProgramme, timeFormat),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = listOfNotNull(nextProgramme.title, nextProgramme.seasonEpisodeLabel())
                            .joinToString(" "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Card row on OK: TV guide, History, then the next channels. Focus lands on
 *  TV guide, the first card (as in TiviMate). */
@Composable
private fun InfoBarCardRow(
    model: TvInfoBarModel,
    onInteraction: () -> Unit,
    up: FocusRequester,
    onDown: () -> Unit,
    /** Still meant to be on screen: a row fading out (the options menu took
     *  its place) must not take the focus from what replaced it. */
    active: () -> Boolean,
) {
    val upToPlayPause = Modifier.focusProperties { this.up = up }
    val guideFocus = remember { FocusRequester() }
    val stillActive by androidx.compose.runtime.rememberUpdatedState(active)
    LaunchedEffect(Unit) {
        delay(100)
        if (stillActive()) runCatching { guideFocus.requestFocus() }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = EDGE),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        // Nothing is below the cards: Down there brings up the options menu
        modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) {
                onInteraction()
                onDown()
                true
            } else {
                false
            }
        },
    ) {
        item(key = "guide") {
            InfoBarCard(onClick = model.onOpenGuide, onInteraction = onInteraction, modifier = Modifier.focusRequester(guideFocus).then(upToPlayPause)) {
                ActionCardContent(Icons.Filled.ViewList, "TV guide")
            }
        }
        item(key = "history") {
            InfoBarCard(onClick = model.onOpenHistory, onInteraction = onInteraction, modifier = upToPlayPause) {
                ActionCardContent(Icons.Filled.History, "History")
            }
        }
        itemsIndexed(model.upcoming, key = { _, c -> c.id }) { _, c ->
            InfoBarCard(
                onClick = { model.onTuneChannel(c) },
                onInteraction = onInteraction,
                modifier = upToPlayPause,
            ) {
                ChannelCardContent(c, model.nowFor(c))
            }
        }
    }
}

@Composable
private fun InfoBarCard(
    onClick: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(CARD_CORNER)
    Box(
        modifier = modifier
            .size(width = 150.dp, height = 96.dp)
            .tvFocusScale(focused, focusedScale = 1.06f)
            .clip(shape)
            .background(if (focused) Color.White.copy(alpha = 0.28f) else Color(0xFF2A2F38).copy(alpha = 0.85f))
            .border(2.dp, if (focused) Color.White else Color.Transparent, shape)
            .onFocusChanged { if (it.isFocused) onInteraction() }
            .clickable(interactionSource = interaction, indication = null) {
                onInteraction()
                onClick()
            }
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ActionCardContent(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(6.dp))
        Text(text = label, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

@Composable
private fun ChannelCardContent(channel: M3UChannel, now: EPGProgramme?) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (channel.tvgLogo.isNotBlank()) {
                    AsyncImage(
                        model = channel.tvgLogo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = now?.title ?: channel.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (now != null) {
            // Programme progress along the card's bottom edge.
            val fraction = progressOf(now, System.currentTimeMillis())
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(fraction)
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.85f)),
            )
        }
    }
}

/** Round play / pause control: quiet at rest, white platter with a dark glyph when focused. */
/**
 * Under the timeline: the programme's elapsed and whole length on the left,
 * the transport buttons centered, LIVE on the right. Start, back, forward and
 * live move in the Live Rewind buffer ([transport]); without one they are
 * dimmed and skipped by the focus, and only play / pause is there.
 */
@Composable
private fun TransportRow(
    programme: EPGProgramme?,
    paused: Boolean,
    transport: InfoBarTransport?,
    onTogglePause: () -> Unit,
    onInteraction: () -> Unit,
    playPauseModifier: Modifier,
    up: FocusRequester?,
    /** A look-back replay: no Live button and no LIVE badge. */
    replay: Boolean = false,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val position = transport?.positionWallMs ?: now
    val upTo = Modifier.focusProperties { if (up != null) this.up = up }
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = EDGE)) {
        programme?.let { p ->
            val length = (p.endMillis - p.startMillis).coerceAtLeast(0L)
            val elapsed = (position - p.startMillis).coerceIn(0L, length)
            Text(
                text = "${clockOf(elapsed)} / ${clockOf(length)}",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val back = SkipIntervals.backMs
            val forward = SkipIntervals.forwardMs
            TransportButton(
                icon = Icons.Filled.SkipPrevious,
                label = "Start",
                enabled = transport != null,
                onInteraction = onInteraction,
                modifier = upTo,
            ) {
                transport?.let { t -> t.onSeekWall(maxOf(t.tailWallMs, programme?.startMillis ?: t.tailWallMs)) }
            }
            TransportButton(
                icon = Icons.Filled.FastRewind,
                label = "Back",
                enabled = transport != null,
                onInteraction = onInteraction,
                modifier = upTo,
            ) {
                transport?.let { t -> t.onSeekWall((t.positionWallMs - back).coerceAtLeast(t.tailWallMs)) }
            }
            PlayPauseButton(
                paused = paused,
                onClick = onTogglePause,
                onInteraction = onInteraction,
                modifier = playPauseModifier.then(upTo),
            )
            TransportButton(
                icon = Icons.Filled.FastForward,
                label = "Forward",
                enabled = transport?.behind == true,
                onInteraction = onInteraction,
                modifier = upTo,
            ) {
                transport?.let { t -> t.onSeekWall((t.positionWallMs + forward).coerceAtMost(t.headWallMs)) }
            }
            if (!replay) {
                TransportButton(
                    icon = Icons.Filled.SkipNext,
                    label = "Live",
                    enabled = transport?.behind == true,
                    onInteraction = onInteraction,
                    modifier = upTo,
                ) {
                    transport?.onGoLive?.invoke()
                }
            }
        }
        if (!replay) {
            LiveBadge(
                live = transport?.behind != true,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .focusProperties { canFocus = enabled }
            .size(44.dp)
            .tvFocusScale(focused, focusedScale = 1.08f)
            .clip(CircleShape)
            .background(if (focused) Color.White else Color.Transparent)
            .onFocusChanged { if (it.isFocused) onInteraction() }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                onInteraction()
                onClick()
            }
            .focusable(enabled = enabled, interactionSource = interaction),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = when {
                focused -> Color.Black
                enabled -> Color.White
                else -> Color.White.copy(alpha = 0.35f)
            },
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun PlayPauseButton(
    paused: Boolean,
    onClick: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // The big one in the middle: white whether focused or not, a ring when focused
    Box(
        modifier = modifier
            .size(60.dp)
            .tvFocusScale(focused, focusedScale = 1.08f)
            .clip(CircleShape)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.85f))
            .border(3.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
            .onFocusChanged { if (it.isFocused) onInteraction() }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
            contentDescription = if (paused) "Play" else "Pause",
            tint = Color.Black,
            modifier = Modifier.size(34.dp),
        )
    }
}

/** LIVE on the right: filled at the live edge, an outline while behind it. */
@Composable
private fun LiveBadge(live: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Text(
        text = "LIVE",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = if (live) Color.Black else Color.White.copy(alpha = 0.8f),
        modifier = modifier
            .clip(shape)
            .background(if (live) Color.White else Color.Transparent)
            .border(2.dp, Color.White.copy(alpha = if (live) 1f else 0.6f), shape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** 1:10:00 or 23:25. */
private fun clockOf(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.getDefault(), "%d:%02d", m, sec)
}

/** Read-only programme progress across the screen, with a dot at "now". */
@Composable
private fun ProgrammeLine(programme: EPGProgramme?, modifier: Modifier = Modifier) {
    val fraction = programme?.let { progressOf(it, System.currentTimeMillis()) } ?: 0f
    Box(modifier = modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
        Box(
            Modifier.fillMaxWidth(fraction).height(3.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        if (programme != null) {
            Row(Modifier.fillMaxWidth(fraction), horizontalArrangement = Arrangement.End) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}

@Composable
private fun MiniProgress(fraction: Float) {
    Box(
        modifier = Modifier.width(64.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)),
    ) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Color.White))
    }
}

@Composable
private fun InfoBarChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.22f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** "Sat, Sep 26, 10:09 AM" in the device locale, following the 12/24 h setting. */
@Composable
private fun ClockLabel(modifier: Modifier = Modifier) {
    val clockMode = rememberClockMode()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L * (60 - (System.currentTimeMillis() / 1000L % 60)))
            now = System.currentTimeMillis()
        }
    }
    val dateFormat = remember {
        java.text.SimpleDateFormat(
            android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEMMMd"),
            Locale.getDefault(),
        )
    }
    val timeFormat = remember(clockMode) { ClockFormat.short(clockMode) }
    Text(
        text = "${dateFormat.format(Date(now))}, ${timeFormat.format(Date(now))}",
        style = MaterialTheme.typography.titleLarge.merge(LABEL_SHADOW),
        color = Color.White,
        modifier = modifier,
    )
}

private fun timeRange(p: EPGProgramme, format: java.text.DateFormat): String =
    "${format.format(Date(p.startMillis))} – ${format.format(Date(p.endMillis))}"

private fun progressOf(p: EPGProgramme, now: Long): Float {
    val total = (p.endMillis - p.startMillis).coerceAtLeast(1L)
    return ((now - p.startMillis).toFloat() / total).coerceIn(0f, 1f)
}

private fun remainingLabel(p: EPGProgramme, now: Long): String {
    val minutes = ((p.endMillis - now).coerceAtLeast(0L) + 59_999L) / 60_000L
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}

private val EDGE = 40.dp
private val CARD_CORNER = 8.dp
private val LABEL_SHADOW = TextStyle(
    shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 2f), blurRadius = 6f),
)
