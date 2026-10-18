package com.aeriotv.android.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeriotv.android.R
import com.aeriotv.android.core.network.DispatcharrClient
import com.aeriotv.android.core.network.WatchTogether

/*
 * Watch together, the screens' side (Dispatch More v260, other people in the house from v266):
 * the pop-ups another person's device brings up -- asked to let it in, asked whether to follow
 * its channel change -- and the Watch together list behind the Join button and the player's
 * menu. All of them app-wide, over whatever screen is up.
 */

/** How long a pop-up waits before its default answer: Allow, Follow (the user, 2026-10-17). */
internal const val ANSWER_SECONDS = 10

/** "Pixel 9 · Yves", or whichever of the two there is. */
internal fun deviceOfPerson(name: String, user: String): String =
    listOf(user, name).filter { it.isNotBlank() }.joinToString(" · ")

/**
 * Another person's request to join this device: Allow / Decline, Allow when unanswered. (A
 * channel change to follow is the small card at the top right instead, WatchTogetherFollowCard.)
 */
@Composable
fun WatchTogetherPrompts() {
    val join by WatchTogether.joinRequest.collectAsStateWithLifecycle()
    join?.let { request ->
        Countdown(request) { WatchTogether.answer(allow = true) }.let { left ->
            val focus = remember { FocusRequester() }
            AlertDialog(
                containerColor = solidDialog(),
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurface,
                onDismissRequest = { WatchTogether.answer(allow = false) },
                title = { Text(stringResource(R.string.watch_together_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.watch_together_request_message,
                            deviceOfPerson(request.name, request.user),
                            request.channelName.ifBlank { stringResource(R.string.watch_together_unnamed_device) },
                        ),
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { WatchTogether.answer(allow = true) },
                        modifier = Modifier.focusRequester(focus),
                    ) { Text(stringResource(R.string.watch_together_allow, left)) }
                },
                dismissButton = {
                    TextButton(onClick = { WatchTogether.answer(allow = false) }) {
                        Text(stringResource(R.string.watch_together_decline))
                    }
                },
            )
            FocusOn(focus, request)
        }
    }
}

/** The pop-ups come up over a playing picture: a solid box, never the see-through one. */
@Composable
private fun solidDialog() = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 1f)

/** Seconds left on this request's pop-up; [onEnd] when they run out. */
@Composable
private fun Countdown(key: Any, onEnd: () -> Unit): Int {
    var left by remember(key) { mutableIntStateOf(ANSWER_SECONDS) }
    LaunchedEffect(key) {
        while (left > 0) {
            kotlinx.coroutines.delay(1_000)
            left -= 1
        }
        onEnd()
    }
    return left
}

/** A TV: the remote starts on the default button. */
@Composable
private fun FocusOn(focus: FocusRequester, key: Any) {
    LaunchedEffect(key) {
        repeat(5) {
            if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect
            kotlinx.coroutines.delay(50)
        }
    }
}

/**
 * The Watch together list: who this device is linked with (Disconnect for another person's
 * device when this one leads, Stop watching together), and every device it may join now.
 */
@Composable
fun WatchTogetherSheet() {
    val open by WatchTogether.sheetOpen.collectAsStateWithLifecycle()
    if (!open) return
    val status by WatchTogether.status.collectAsStateWithLifecycle()
    val linked by WatchTogether.linked.collectAsStateWithLifecycle()
    val close = { WatchTogether.sheetOpen.value = false }
    val firstFocus = remember { FocusRequester() }
    AlertDialog(
        containerColor = solidDialog(),
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        onDismissRequest = close,
        title = { Text(stringResource(R.string.watch_together_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val state = status
                if (linked && state != null && state.together.isNotEmpty()) {
                    Text(stringResource(R.string.watch_together_with), style = MaterialTheme.typography.labelLarge)
                    state.together.forEach { member ->
                        MemberRow(member, canDisconnect = state.leads && member.user.isNotBlank() && !member.leads)
                    }
                    OutlinedButton(
                        onClick = { close(); WatchTogether.leave() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.player_option_stop_watch_together)) }
                }
                val offers = state?.offers.orEmpty()
                Text(stringResource(R.string.watch_together_can_join), style = MaterialTheme.typography.labelLarge)
                if (offers.isEmpty()) {
                    Text(stringResource(R.string.watch_together_nobody), style = MaterialTheme.typography.bodyMedium)
                }
                offers.forEachIndexed { i, offer ->
                    OfferRow(offer, if (i == 0) Modifier.focusRequester(firstFocus) else Modifier) {
                        close()
                        WatchTogether.join(offer.device)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.watch_together_close)) } },
    )
    if (!status?.offers.isNullOrEmpty()) FocusOn(firstFocus, open)
}

@Composable
private fun OfferRow(offer: DispatcharrClient.LinkOffer, modifier: Modifier, onJoin: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                deviceOfPerson(
                    offer.name.takeIf { it.isNotBlank() && it != "another device" }
                        ?: stringResource(R.string.watch_together_unnamed_device),
                    offer.user,
                ),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                offer.channelName.ifBlank { offer.channel.take(8) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Button(onClick = onJoin, modifier = modifier) {
            Text(stringResource(if (offer.user.isNotBlank()) R.string.watch_together_ask else R.string.player_join_other_device))
        }
    }
}

@Composable
private fun MemberRow(member: DispatcharrClient.LinkMember, canDisconnect: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                deviceOfPerson(member.name, member.user),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (member.leads && member.user.isNotBlank()) {
                Text(
                    stringResource(R.string.watch_together_leads),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (canDisconnect) {
            OutlinedButton(onClick = { WatchTogether.disconnect(member.device) }) {
                Text(stringResource(R.string.watch_together_disconnect))
            }
        }
    }
}

/** Whether there is anything for the Join button: someone to join, or someone joined. */
@Composable
fun watchTogetherAvailable(): Boolean {
    val status by WatchTogether.status.collectAsStateWithLifecycle()
    val linked by WatchTogether.linked.collectAsStateWithLifecycle()
    return linked || !status?.offers.isNullOrEmpty()
}

/** The phone header's Join circle, only while there is something to join or someone joined. */
@Composable
fun WatchTogetherAction() {
    if (!watchTogetherAvailable()) return
    val linked by WatchTogether.linked.collectAsStateWithLifecycle()
    val highlight by WatchTogether.highlight.collectAsStateWithLifecycle()
    com.aeriotv.android.feature.livetv.LiveTvPhoneCircle(
        icon = Icons.Outlined.Groups,
        contentDescription = stringResource(R.string.watch_together_title),
        onClick = { WatchTogether.openSheet() },
        active = linked || highlight,
    )
}

/** The TV pill row's Join circle, the same only-when-there-is-something rule. */
@Composable
fun TvWatchTogetherCircle(modifier: Modifier = Modifier) {
    if (!watchTogetherAvailable()) return
    val linked by WatchTogether.linked.collectAsStateWithLifecycle()
    val highlight by WatchTogether.highlight.collectAsStateWithLifecycle()
    com.aeriotv.android.ui.tv.TvActionCircle(
        icon = Icons.Outlined.Groups,
        contentDescription = stringResource(R.string.watch_together_title),
        onClick = { WatchTogether.openSheet() },
        modifier = modifier,
        selected = linked || highlight,
        accentGlyph = true,
    )
}


/** A channel change is only shown once the other has stayed on it this long: zapping through
 *  four channels was four pop-ups (the user, 2026-10-18), now it is one card, for the last. */
internal const val SETTLE_SECONDS = 5

/**
 * The device this one watches together with changed channel: a small card at the top right,
 * over whatever plays -- "SHIELD switched to SAT.1 · following in 10 s" -- once that channel
 * has been kept [SETTLE_SECONDS], one card at a time (a newer change replaces it). Ten seconds
 * unanswered is Follow; "Don't follow" stands out: on a TV, OK or Back is Don't follow
 * (WatchTogether.cardKey), on a phone it is the highlighted button beside "Follow now".
 */
@Composable
fun WatchTogetherFollowCard() {
    val request by WatchTogether.followRequest.collectAsStateWithLifecycle()
    var shown by remember { androidx.compose.runtime.mutableStateOf<WatchTogether.FollowRequest?>(null) }
    var left by remember { mutableIntStateOf(ANSWER_SECONDS) }
    LaunchedEffect(request) {
        shown = null
        WatchTogether.followShown.value = false
        val current = request ?: return@LaunchedEffect
        kotlinx.coroutines.delay(SETTLE_SECONDS * 1_000L)
        left = ANSWER_SECONDS
        shown = current
        WatchTogether.followShown.value = true
        while (left > 0) {
            kotlinx.coroutines.delay(1_000)
            left -= 1
        }
        WatchTogether.followShown.value = false
        if (WatchTogether.followRequest.value == current) WatchTogether.follow()
    }
    val current = shown ?: return
    val tv = com.aeriotv.android.ui.settings.rememberIsTvDevice()
    val who = deviceOfPerson(current.name, current.user)
    androidx.compose.ui.window.Popup(
        alignment = Alignment.TopEnd,
        offset = androidx.compose.ui.unit.IntOffset(-24, 24),
        properties = androidx.compose.ui.window.PopupProperties(focusable = false),
    ) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            color = solidDialog(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.widthIn(max = 360.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.Icon(Icons.Outlined.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            stringResource(
                                R.string.watch_together_follow_message,
                                who,
                                current.channelName.ifBlank { current.channel.take(8) },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(R.string.watch_together_following_in, left),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (tv) {
                    Text(
                        stringResource(R.string.watch_together_follow_tv_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { WatchTogether.stay() }) { Text(stringResource(R.string.watch_together_dont_follow)) }
                        TextButton(onClick = { WatchTogether.follow() }) { Text(stringResource(R.string.watch_together_follow_now)) }
                    }
                }
            }
        }
    }
}

/** How long the start card stays, and the buttons stand out after it. */
internal const val HINT_SECONDS = 10
internal const val HIGHLIGHT_SECONDS = 30

/**
 * At the start of the app: whom this device could watch together with, as a small card in the
 * top right corner over whatever is playing -- a TV almost always opens a stream at once, so
 * a dialog on the guide was never seen (the user, 2026-10-18). Ten seconds, never in the way:
 * it takes no focus; on a TV, OK opens the Watch together list and Back puts it away
 * (WatchTogether.hintKey); on a phone, a tap opens it. The Watch together buttons stand out
 * meanwhile.
 */
@Composable
fun WatchTogetherHint() {
    val offer by WatchTogether.hint.collectAsStateWithLifecycle()
    LaunchedEffect(offer) {
        val shown = offer ?: return@LaunchedEffect
        kotlinx.coroutines.delay(HINT_SECONDS * 1_000L)
        if (WatchTogether.hint.value == shown) WatchTogether.hint.value = null
        kotlinx.coroutines.delay((HIGHLIGHT_SECONDS - HINT_SECONDS) * 1_000L)
        WatchTogether.highlight.value = false
    }
    val current = offer ?: return
    val tv = com.aeriotv.android.ui.settings.rememberIsTvDevice()
    androidx.compose.ui.window.Popup(
        alignment = Alignment.TopEnd,
        offset = androidx.compose.ui.unit.IntOffset(-24, 24),
        properties = androidx.compose.ui.window.PopupProperties(focusable = false),
    ) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            color = solidDialog(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            onClick = { WatchTogether.openSheet() },
            modifier = Modifier.widthIn(max = 340.dp),
        ) {
            Row(
                modifier = androidx.compose.ui.Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                androidx.compose.material3.Icon(
                    Icons.Outlined.Groups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        stringResource(
                            R.string.watch_together_hint,
                            deviceOfPerson(
                                current.name.takeIf { it.isNotBlank() && it != "another device" }
                                    ?: stringResource(R.string.watch_together_unnamed_device),
                                current.user,
                            ),
                            current.channelName.ifBlank { current.channel.take(8) },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(if (tv) R.string.watch_together_hint_tv else R.string.watch_together_hint_phone),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
