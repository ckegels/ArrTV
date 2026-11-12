package com.aeriotv.android.feature.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compact modern layout: opens the left navigation rail and focuses the
 * selected tab's item. Null while the layout is off (the top tab bar is in
 * use). The guide's group sidebar calls it for a Left press, so Left from the
 * guide opens the groups and Left again opens the menu.
 */
val LocalTvOpenNavRail = staticCompositionLocalOf<(() -> Boolean)?> { null }

/** Width of the open rail. */
internal val TvNavRailWidth = 220.dp

/**
 * The compact modern layout's navigation: a left rail that replaces the top
 * tab bar. Hidden (placed just off the left edge) until it takes focus, so
 * the page below uses the full screen; while any item is focused it slides
 * in over the page with icons and labels.
 *
 * Focus contract (see MainScaffold):
 * - Left or Up leaving the page lands on the SELECTED item ([itemRequesters]).
 * - OK selects that tab and returns focus to the page.
 * - Right or Back returns to the page without changing tab ([onReturn]).
 * Items stay focusable while hidden so a focus request never races the
 * slide-in; off-screen, they are left of everything, so the page's own
 * Up/Down focus search never reaches them.
 */
@Composable
internal fun TvNavRail(
    items: List<AppTab>,
    selected: AppTab,
    itemRequesters: Map<AppTab, FocusRequester>,
    onSelect: (AppTab) -> Unit,
    onReturn: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** "ArrTV look" action above Settings (not a tab); null hides it. */
    onApplyLook: (() -> Unit)? = null,
    /** "Update" action at the very bottom (not a tab); null hides it. */
    onUpdate: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    val offset by animateDpAsState(
        targetValue = if (open) 0.dp else -TvNavRailWidth,
        animationSpec = tween(durationMillis = 150),
        label = "tvNavRailOffset",
    )
    Column(
        modifier = modifier
            .offset(x = offset)
            .width(TvNavRailWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 24.dp)
            .onFocusChanged {
                open = it.hasFocus
                onFocusChanged(it.hasFocus)
            }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionRight, Key.Back -> { onReturn(); true }
                    // The rail is the left edge: nothing further left.
                    Key.DirectionLeft -> true
                    else -> false
                }
            }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { tab ->
            // ArrTV look and Settings follow the tabs; Update has the bottom.
            if (tab == AppTab.Settings && onApplyLook != null) {
                TvNavRailRow(
                    icon = Icons.Filled.AutoAwesome,
                    label = "ArrTV look",
                    selected = false,
                    onClick = onApplyLook,
                )
            }
            TvNavRailItem(
                tab = tab,
                selected = tab == selected,
                onClick = { onSelect(tab) },
                modifier = itemRequesters[tab]?.let { Modifier.focusRequester(it) } ?: Modifier,
            )
        }
        if (onUpdate != null) {
            Spacer(Modifier.weight(1f))
            TvNavRailRow(
                icon = Icons.Filled.SystemUpdate,
                label = "Update",
                selected = false,
                onClick = onUpdate,
            )
        }
    }
}

@Composable
private fun TvNavRailItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = TvNavRailRow(
    icon = if (selected) tab.iconSelected else tab.iconUnselected,
    label = tab.label,
    selected = selected,
    onClick = onClick,
    modifier = modifier,
)

@Composable
private fun TvNavRailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    // The tab bar's convention: FOCUS is the white platter with dark ink,
    // SELECTED keeps the accent, everything else is bare.
    val pillTween = tween<Color>(durationMillis = 150)
    val background by animateColorAsState(
        targetValue = when {
            focused -> Color.White
            selected -> MaterialTheme.colorScheme.primary
            else -> Color.Transparent
        },
        animationSpec = pillTween,
        label = "tvNavRailItemBackground",
    )
    val foreground by animateColorAsState(
        targetValue = when {
            focused -> Color.Black
            selected -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = pillTween,
        label = "tvNavRailItemForeground",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .onPreviewKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (ok && e.type == KeyEventType.KeyUp) onClick()
                ok
            }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            color = foreground,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
