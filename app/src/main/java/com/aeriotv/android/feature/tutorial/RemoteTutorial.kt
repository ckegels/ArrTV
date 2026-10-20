package com.aeriotv.android.feature.tutorial

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeriotv.android.R
import com.aeriotv.android.core.remote.GuideRemoteAction
import com.aeriotv.android.core.remote.PlayerRemoteAction
import com.aeriotv.android.core.remote.RemoteControlMap
import com.aeriotv.android.core.remote.RemoteSlot
import com.aeriotv.android.feature.settings.SettingsViewModel
import com.aeriotv.android.feature.settings.labelRes
import com.aeriotv.android.ui.scale.subtext
import kotlinx.coroutines.delay

/** The buttons the drawn remote has. */
enum class TutorialKey { UP, DOWN, LEFT, RIGHT, OK, BACK, HOME, PLAY_PAUSE, CH_UP, CH_DOWN }

/** One line of a page: which key(s), how they are pressed, what happens. */
data class TutorialStep(
    val keys: Set<TutorialKey>,
    val text: String,
    val hold: Boolean = false,
    val twice: Boolean = false,
)

data class TutorialPage(val title: String, val intro: String, val steps: List<TutorialStep>)

private val slotKey = mapOf(
    RemoteSlot.OK_SHORT to TutorialKey.OK, RemoteSlot.OK_LONG to TutorialKey.OK,
    RemoteSlot.UP_SHORT to TutorialKey.UP, RemoteSlot.UP_LONG to TutorialKey.UP,
    RemoteSlot.DOWN_SHORT to TutorialKey.DOWN, RemoteSlot.DOWN_LONG to TutorialKey.DOWN,
    RemoteSlot.LEFT_SHORT to TutorialKey.LEFT, RemoteSlot.LEFT_LONG to TutorialKey.LEFT,
    RemoteSlot.RIGHT_SHORT to TutorialKey.RIGHT, RemoteSlot.RIGHT_LONG to TutorialKey.RIGHT,
    RemoteSlot.PLAY_PAUSE to TutorialKey.PLAY_PAUSE,
    RemoteSlot.CHANNEL_UP to TutorialKey.CH_UP, RemoteSlot.CHANNEL_DOWN to TutorialKey.CH_DOWN,
)
private val holdSlots = setOf(RemoteSlot.OK_LONG, RemoteSlot.UP_LONG, RemoteSlot.DOWN_LONG, RemoteSlot.LEFT_LONG, RemoteSlot.RIGHT_LONG)

/**
 * The pages, built from the user's own button map (Settings › Remote Control),
 * so a remapped button is explained as it now works. Buttons that do nothing,
 * or only move focus, are left out of the guide and watching pages.
 */
@Composable
private fun tutorialPages(map: RemoteControlMap, sidebarGroups: Boolean): List<TutorialPage> {
    val guideSteps = buildList {
        add(TutorialStep(setOf(TutorialKey.UP, TutorialKey.DOWN), stringResource(R.string.tutorial_guide_updown)))
        val left = map.guideAction(RemoteSlot.LEFT_SHORT, sidebarGroups)
        if (left == GuideRemoteAction.NAVIGATE) {
            add(TutorialStep(setOf(TutorialKey.LEFT, TutorialKey.RIGHT), stringResource(R.string.tutorial_guide_leftright_time)))
        }
        for (slot in listOf(RemoteSlot.OK_SHORT, RemoteSlot.OK_LONG, RemoteSlot.LEFT_SHORT, RemoteSlot.LEFT_LONG, RemoteSlot.RIGHT_SHORT, RemoteSlot.RIGHT_LONG, RemoteSlot.CHANNEL_UP, RemoteSlot.CHANNEL_DOWN)) {
            val action = map.guideAction(slot, sidebarGroups)
            if (action == GuideRemoteAction.NONE || action == GuideRemoteAction.NAVIGATE) continue
            // Closing / resuming the mini player only applies while one is up.
            if (action == GuideRemoteAction.CLOSE_MINI_PLAYER || action == GuideRemoteAction.RESUME_PLAYER) continue
            add(TutorialStep(setOf(slotKey.getValue(slot)), stringResource(action.labelRes(sidebarGroups)), hold = slot in holdSlots))
        }
        add(TutorialStep(setOf(TutorialKey.BACK), stringResource(R.string.tutorial_guide_back)))
    }
    val watchSteps = buildList {
        for (slot in listOf(RemoteSlot.OK_SHORT, RemoteSlot.OK_LONG, RemoteSlot.UP_SHORT, RemoteSlot.DOWN_SHORT, RemoteSlot.UP_LONG, RemoteSlot.DOWN_LONG, RemoteSlot.LEFT_SHORT, RemoteSlot.RIGHT_SHORT, RemoteSlot.LEFT_LONG, RemoteSlot.RIGHT_LONG, RemoteSlot.PLAY_PAUSE)) {
            val action = map.playerAction(slot)
            if (action == PlayerRemoteAction.NONE) continue
            add(TutorialStep(setOf(slotKey.getValue(slot)), stringResource(action.labelRes), hold = slot in holdSlots))
        }
        add(TutorialStep(setOf(TutorialKey.BACK), stringResource(R.string.tutorial_watch_back)))
        add(TutorialStep(setOf(TutorialKey.BACK), stringResource(R.string.tutorial_watch_back_twice), twice = true))
    }
    return listOf(
        TutorialPage(
            stringResource(R.string.tutorial_basics_title),
            stringResource(R.string.tutorial_basics_intro),
            listOf(
                TutorialStep(setOf(TutorialKey.UP, TutorialKey.DOWN, TutorialKey.LEFT, TutorialKey.RIGHT), stringResource(R.string.tutorial_basics_arrows)),
                TutorialStep(setOf(TutorialKey.OK), stringResource(R.string.tutorial_basics_ok)),
                TutorialStep(setOf(TutorialKey.OK), stringResource(R.string.tutorial_basics_ok_hold), hold = true),
                TutorialStep(setOf(TutorialKey.BACK), stringResource(R.string.tutorial_basics_back)),
                TutorialStep(setOf(TutorialKey.HOME), stringResource(R.string.tutorial_basics_home)),
            ),
        ),
        TutorialPage(stringResource(R.string.tutorial_guide_title), stringResource(R.string.tutorial_guide_intro), guideSteps),
        TutorialPage(stringResource(R.string.tutorial_watch_title), stringResource(R.string.tutorial_watch_intro), watchSteps),
        TutorialPage(
            stringResource(R.string.tutorial_controls_title),
            stringResource(R.string.tutorial_controls_intro),
            listOf(
                TutorialStep(setOf(TutorialKey.LEFT, TutorialKey.RIGHT), stringResource(R.string.tutorial_controls_leftright)),
                TutorialStep(setOf(TutorialKey.OK), stringResource(R.string.tutorial_controls_ok)),
                TutorialStep(setOf(TutorialKey.DOWN), stringResource(R.string.tutorial_controls_down)),
                TutorialStep(setOf(TutorialKey.OK), stringResource(R.string.tutorial_controls_ok_hold), hold = true),
                TutorialStep(setOf(TutorialKey.BACK), stringResource(R.string.tutorial_controls_back)),
            ),
        ),
        TutorialPage(stringResource(R.string.tutorial_end_title), stringResource(R.string.tutorial_end_text), emptyList()),
    )
}

/**
 * Full-screen remote tutorial: one page per place in the app, a drawn remote
 * whose key for the highlighted line lights up (a ring fills for a hold, two
 * pulses for "twice"), the lines stepping through by themselves. Next / Done
 * moves on; Back goes to the page before, or closes on the first.
 */
@Composable
fun RemoteTutorial(onClose: () -> Unit, settingsViewModel: SettingsViewModel = hiltViewModel()) {
    val map by settingsViewModel.remoteControlMap.collectAsStateWithLifecycle(initialValue = RemoteControlMap.DEFAULT)
    val selector by settingsViewModel.guideGroupSelector.collectAsStateWithLifecycle(initialValue = "pills")
    val pages = tutorialPages(map, selector == "sidebar")
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    val page = pages[pageIndex.coerceIn(0, pages.lastIndex)]
    val last = pageIndex >= pages.lastIndex

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        BackHandler { if (pageIndex == 0) onClose() else pageIndex-- }
        var stepIndex by remember(pageIndex) { mutableIntStateOf(0) }
        LaunchedEffect(pageIndex) {
            while (page.steps.isNotEmpty()) {
                delay(2_800)
                stepIndex = (stepIndex + 1) % page.steps.size
            }
        }
        val step = page.steps.getOrNull(stepIndex)
        val nextFocus = remember { FocusRequester() }
        LaunchedEffect(pageIndex) { repeat(10) { if (runCatching { nextFocus.requestFocus() }.isSuccess) return@LaunchedEffect; delay(16) } }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 48.dp, vertical = 28.dp),
        ) {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Box(modifier = Modifier.fillMaxHeight().width(230.dp), contentAlignment = Alignment.Center) {
                    RemoteDrawing(
                        highlight = step?.keys.orEmpty(),
                        hold = step?.hold == true,
                        twice = step?.twice == true,
                        modifier = Modifier.width(190.dp).height(380.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    Text(
                        stringResource(R.string.tutorial_page_of, pageIndex + 1, pages.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(page.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(6.dp))
                    Text(page.intro, style = MaterialTheme.typography.bodyLarge.subtext(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    // Long pages (the watching page has a dozen lines) scroll
                    // along with the highlighted line; the buttons stay put.
                    val listState = rememberLazyListState()
                    LaunchedEffect(pageIndex, stepIndex) { listState.animateScrollToItem((stepIndex - 2).coerceAtLeast(0)) }
                    LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false), userScrollEnabled = false) {
                        itemsIndexed(page.steps) { i, s -> StepRow(s, active = i == stepIndex) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!last) OutlinedButton(onClick = onClose) { Text(stringResource(R.string.tutorial_skip)) }
                        Button(
                            onClick = { if (last) onClose() else pageIndex++ },
                            modifier = Modifier.focusRequester(nextFocus),
                        ) { Text(stringResource(if (last) R.string.tutorial_done else R.string.tutorial_next)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(step: TutorialStep, active: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) accent.copy(alpha = 0.16f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = keyLabel(step),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(190.dp),
        )
        Text(
            text = step.text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Hold OK", "Up / Down", "Back · Twice": the key names the app already uses. */
@Composable
internal fun keyLabel(step: TutorialStep): String {
    val names = step.keys.map { key ->
        when (key) {
            TutorialKey.UP -> stringResource(R.string.livetv_remote_key_up)
            TutorialKey.DOWN -> stringResource(R.string.livetv_remote_key_down)
            TutorialKey.LEFT -> stringResource(R.string.livetv_remote_key_left)
            TutorialKey.RIGHT -> stringResource(R.string.livetv_remote_key_right)
            TutorialKey.OK -> "OK"
            TutorialKey.BACK -> stringResource(R.string.livetv_remote_key_back)
            TutorialKey.HOME -> "Home"
            TutorialKey.PLAY_PAUSE -> stringResource(R.string.livetv_remote_key_play_pause)
            TutorialKey.CH_UP -> stringResource(R.string.livetv_remote_key_channel_up)
            TutorialKey.CH_DOWN -> stringResource(R.string.livetv_remote_key_channel_down)
        }
    }.joinToString(" / ")
    return when {
        step.hold -> stringResource(R.string.tutorial_hold) + " " + names
        step.twice -> names + " · " + stringResource(R.string.tutorial_twice)
        else -> names
    }
}

/**
 * A plain TV remote drawn to scale: the D-pad ring with OK, Back / Home /
 * Play-Pause, and the channel rocker. [highlight] keys glow in the accent and
 * pulse; a [hold] fills a ring around them, a [twice] blinks them twice.
 */
@Composable
internal fun RemoteDrawing(highlight: Set<TutorialKey>, hold: Boolean, twice: Boolean, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val body = MaterialTheme.colorScheme.surfaceVariant
    // Idle keys are neutral grey so only the key being explained is in colour.
    val key = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val ink = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val t = rememberInfiniteTransition(label = "tutorialRemote")
    val pulse by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    val cycle by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1_400, easing = LinearEasing)), label = "cycle")

    fun glow(k: TutorialKey): Float {
        if (k !in highlight) return 0f
        return when {
            hold -> (cycle * 1.3f).coerceAtMost(1f)
            twice -> if (cycle < 0.2f || cycle in 0.35f..0.55f) 1f else 0.15f
            else -> pulse
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(body, size = Size(w, h), cornerRadius = CornerRadius(w * 0.28f))
        val c = Offset(w / 2, h * 0.27f)
        val ring = w * 0.38f
        drawCircle(key, ring, c)
        // Arrow sectors light as wedges on the ring
        fun wedge(k: TutorialKey, angle: Float) {
            val g = glow(k)
            if (g > 0f) {
                drawArc(accent.copy(alpha = g), startAngle = angle - 45f, sweepAngle = 90f, useCenter = true,
                    topLeft = Offset(c.x - ring, c.y - ring), size = Size(ring * 2, ring * 2))
            }
            // the little arrow head
            val rad = Math.toRadians(angle.toDouble())
            val tip = Offset(c.x + (ring * 0.82f) * kotlin.math.cos(rad).toFloat(), c.y + (ring * 0.82f) * kotlin.math.sin(rad).toFloat())
            val back = Offset(c.x + (ring * 0.64f) * kotlin.math.cos(rad).toFloat(), c.y + (ring * 0.64f) * kotlin.math.sin(rad).toFloat())
            val perp = Offset(-kotlin.math.sin(rad).toFloat(), kotlin.math.cos(rad).toFloat()) * (ring * 0.1f)
            val p = Path().apply { moveTo(tip.x, tip.y); lineTo(back.x + perp.x, back.y + perp.y); lineTo(back.x - perp.x, back.y - perp.y); close() }
            drawPath(p, if (g > 0f) Color.White else ink)
        }
        wedge(TutorialKey.UP, 270f)
        wedge(TutorialKey.DOWN, 90f)
        wedge(TutorialKey.LEFT, 180f)
        wedge(TutorialKey.RIGHT, 0f)
        // OK in the middle
        val okR = ring * 0.46f
        drawCircle(body, okR + 3f, c)
        drawCircle(if (glow(TutorialKey.OK) > 0f) accent.copy(alpha = glow(TutorialKey.OK)) else key, okR, c)
        drawCircle(ink.copy(alpha = 0.5f), okR, c, style = Stroke(2f))
        if (hold && TutorialKey.OK in highlight) holdRing(c, okR + 10f, cycle, accent)
        // Back / Home / Play-Pause
        val rowY = h * 0.56f
        val small = w * 0.11f
        listOf(TutorialKey.BACK to w * 0.22f, TutorialKey.HOME to w * 0.5f, TutorialKey.PLAY_PAUSE to w * 0.78f).forEach { (k, x) ->
            val o = Offset(x, rowY)
            drawCircle(if (glow(k) > 0f) accent.copy(alpha = glow(k)) else key, small, o)
            symbol(k, o, small * 0.5f, if (glow(k) > 0f) Color.White else ink)
            if (hold && k in highlight) holdRing(o, small + 8f, cycle, accent)
        }
        // Channel rocker
        val rockTop = h * 0.66f
        val rw = w * 0.22f
        val rh = h * 0.26f
        val rl = Offset(w / 2 - rw / 2, rockTop)
        drawRoundRect(key, rl, Size(rw, rh), CornerRadius(rw / 2))
        listOf(TutorialKey.CH_UP to rockTop + rh * 0.25f, TutorialKey.CH_DOWN to rockTop + rh * 0.75f).forEach { (k, y) ->
            val g = glow(k)
            if (g > 0f) drawCircle(accent.copy(alpha = g), rw * 0.42f, Offset(w / 2, y))
            symbol(k, Offset(w / 2, y), rw * 0.22f, if (g > 0f) Color.White else ink)
        }
    }
}

private fun DrawScope.holdRing(center: Offset, radius: Float, progress: Float, color: Color) {
    drawArc(color, -90f, 360f * (progress * 1.3f).coerceAtMost(1f), useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2), style = Stroke(5f))
}

/** Back arrow, house, play-pause, + and -: simple strokes. */
private fun DrawScope.symbol(k: TutorialKey, c: Offset, s: Float, color: Color) {
    val st = Stroke(width = s * 0.28f)
    when (k) {
        TutorialKey.BACK -> {
            val p = Path().apply { moveTo(c.x + s * 0.6f, c.y + s * 0.5f); quadraticTo(c.x + s * 0.6f, c.y - s * 0.4f, c.x - s * 0.4f, c.y - s * 0.4f) }
            drawPath(p, color, style = st)
            drawPath(Path().apply { moveTo(c.x - s * 0.1f, c.y - s * 0.8f); lineTo(c.x - s * 0.55f, c.y - s * 0.4f); lineTo(c.x - s * 0.1f, c.y) }, color, style = st)
        }
        TutorialKey.HOME -> {
            drawPath(Path().apply {
                moveTo(c.x - s * 0.7f, c.y); lineTo(c.x, c.y - s * 0.7f); lineTo(c.x + s * 0.7f, c.y)
                moveTo(c.x - s * 0.5f, c.y - s * 0.1f); lineTo(c.x - s * 0.5f, c.y + s * 0.6f); lineTo(c.x + s * 0.5f, c.y + s * 0.6f); lineTo(c.x + s * 0.5f, c.y - s * 0.1f)
            }, color, style = st)
        }
        TutorialKey.PLAY_PAUSE -> {
            drawPath(Path().apply { moveTo(c.x - s * 0.7f, c.y - s * 0.6f); lineTo(c.x - s * 0.7f, c.y + s * 0.6f); lineTo(c.x, c.y); close() }, color)
            drawLine(color, Offset(c.x + s * 0.25f, c.y - s * 0.55f), Offset(c.x + s * 0.25f, c.y + s * 0.55f), s * 0.25f)
            drawLine(color, Offset(c.x + s * 0.65f, c.y - s * 0.55f), Offset(c.x + s * 0.65f, c.y + s * 0.55f), s * 0.25f)
        }
        TutorialKey.CH_UP -> {
            drawLine(color, Offset(c.x - s, c.y), Offset(c.x + s, c.y), s * 0.3f)
            drawLine(color, Offset(c.x, c.y - s), Offset(c.x, c.y + s), s * 0.3f)
        }
        TutorialKey.CH_DOWN -> drawLine(color, Offset(c.x - s, c.y), Offset(c.x + s, c.y), s * 0.3f)
        else -> Unit
    }
}

/**
 * First time on the main screen of a TV, once the guide has channels: offer
 * the live tour once (replaces the
 * old Live Rewind prompt; the server keeps the rewind buffer). Like that prompt
 * it waits for What's New to settle so the two never stack. Never on phones.
 */
@Composable
fun TutorialOfferGate(currentRoute: String?) {
    val context = LocalContext.current
    val isTv = com.aeriotv.android.ui.settings.rememberIsTvDevice()
    val prefs = remember {
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(context.applicationContext, com.aeriotv.android.NavEntryPoint::class.java)
            .appPreferences()
    }
    val whatsNewSeen by prefs.lastSeenWhatsNewVersion.collectAsStateWithLifecycle(initialValue = null)
    // The tour needs a guide: wait for channels, then a moment for it to paint.
    val hasChannels by TutorialDirector.demoAvailable.collectAsStateWithLifecycle()
    var guideSettled by remember { mutableStateOf(false) }
    LaunchedEffect(hasChannels) { if (hasChannels) { delay(3_000); guideSettled = true } }
    var offered by remember { mutableStateOf(tutorialOffered(context)) }
    val ready = currentRoute == com.aeriotv.android.Routes.MAIN &&
        whatsNewSeen == com.aeriotv.android.BuildConfig.VERSION_NAME && guideSettled
    if (offered || !ready || !isTv) return
    val startFocus = remember { FocusRequester() }
    com.aeriotv.android.ui.scale.AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.tutorial_offer_title)) },
        text = { Text(stringResource(R.string.tutorial_offer_text), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { markTutorialOffered(context); offered = true; TutorialDirector.requestStart() },
                modifier = Modifier.focusRequester(startFocus),
            ) { Text(stringResource(R.string.tutorial_offer_start)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { markTutorialOffered(context); offered = true }) {
                Text(stringResource(R.string.tutorial_offer_later))
            }
        },
    )
    LaunchedEffect(Unit) { repeat(10) { if (runCatching { startFocus.requestFocus() }.isSuccess) return@LaunchedEffect; delay(16) } }
}

private const val TUTORIAL_PREFS = "onboarding"
private const val KEY_TUTORIAL_OFFERED = "tutorial_offered"

private fun tutorialOffered(context: Context): Boolean =
    context.getSharedPreferences(TUTORIAL_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_TUTORIAL_OFFERED, false)

private fun markTutorialOffered(context: Context) {
    context.getSharedPreferences(TUTORIAL_PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_TUTORIAL_OFFERED, true).apply()
}
