package com.aeriotv.android.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aeriotv.android.ui.tv.tvFocusScale
import kotlinx.coroutines.delay

/** One entry of the info bar's options row: an icon, a short label, and what
 *  it is set to now where that says something ("Fit", "On"). */
@Immutable
internal class PlayerOption(
    val key: String,
    val icon: ImageVector,
    val label: String,
    val value: String? = null,
    /** Drawn in the accent colour: a mode that is on (audio only, sleep). */
    val active: Boolean = false,
    val iconTint: Color? = null,
    val onClick: () -> Unit,
)

/**
 * The info bar style's options menu (hold OK, or Down from the card row): a
 * row of icons with small labels that slides up from the bottom, the same
 * options in the same order as the list it replaces, left to right. Left /
 * Right walk it, OK picks, Back or Up closes it.
 *
 * Part of the player's own screen rather than a popup window, so the hold
 * that opened it is still gated here ([OptionsMenuHoldGate]): OK is ignored
 * until a fresh press.
 */
@Composable
internal fun InfoBarOptionsBar(
    visible: Boolean,
    options: List<PlayerOption>,
    onDismiss: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(visible) {
        if (!visible) OptionsMenuHoldGate.holding = false
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(160)) { it } + fadeOut(tween(160)),
        modifier = modifier,
    ) {
        val first = remember { FocusRequester() }
        var inside by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            // Taken, and taken back for the first half second: the info bar
            // it replaces (fading out, still there) can ask for the focus
            // after this did, and then vanish with it, leaving nothing lit
            // and Left / Right going nowhere.
            repeat(FOCUS_TRIES) {
                if (!inside) runCatching { first.requestFocus() }
                delay(FOCUS_EVERY_MS)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { inside = it.hasFocus }
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Color.Black.copy(alpha = 0.65f),
                        1f to Color.Black.copy(alpha = 0.9f),
                    ),
                )
                .padding(top = 48.dp, bottom = 28.dp)
                .onPreviewKeyEvent { e ->
                    if (OptionsMenuHoldGate.swallow(e.nativeKeyEvent)) return@onPreviewKeyEvent true
                    when (e.key) {
                        // Both halves consumed: the release must not reach the
                        // player's own Back, which would close the chrome too
                        Key.Back, Key.Escape -> {
                            if (e.type == KeyEventType.KeyUp) onDismiss()
                            true
                        }
                        Key.DirectionUp -> {
                            if (e.type == KeyEventType.KeyDown) onDismiss()
                            true
                        }
                        // Nothing below the row
                        Key.DirectionDown -> true
                        else -> {
                            onInteraction()
                            false
                        }
                    }
                },
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(options, key = { _, o -> o.key }) { index, option ->
                    OptionItem(
                        option = option,
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionItem(option: PlayerOption, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val accent = MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(112.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = option.onClick)
            .focusable(interactionSource = interaction),
    ) {
        // Focus is a white disc behind a dark icon, as on the transport row
        Box(
            modifier = Modifier
                .size(56.dp)
                .tvFocusScale(focused, focusedScale = 1.08f)
                .clip(CircleShape)
                .background(if (focused) Color.White else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                tint = when {
                    focused -> Color.Black
                    option.iconTint != null -> option.iconTint
                    option.active -> accent
                    else -> Color.White
                },
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = option.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        option.value?.let { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = if (option.active) accent else Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val FOCUS_TRIES = 15
private const val FOCUS_EVERY_MS = 33L
