package com.aeriotv.android.feature.tutorial

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.outlined.History
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeriotv.android.R
import com.aeriotv.android.core.data.guideMatchKey
import com.aeriotv.android.core.data.M3UChannel
import com.aeriotv.android.core.remote.RemoteControlMap
import com.aeriotv.android.feature.settings.SettingsViewModel
import kotlinx.coroutines.delay

/**
 * Starts the live tour when asked ([TutorialDirector.requestStart]): back to
 * the tab shell, the Live TV guide on the first channel (the same jump an EPG
 * search result uses, which also forces the guide view), then the script.
 * Lives in the nav graph because it needs the channels and the route.
 */
@Composable
fun TutorialTourHost(
    channels: List<M3UChannel>,
    currentRoute: String?,
    onBackToMain: () -> Unit,
    onJumpToGuide: (channelKey: String, startMillis: Long) -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    // The top of the guide: the lowest channel number.
    val first = remember(channels) {
        channels.filter { it.url.isNotBlank() }
            .minByOrNull { it.channelNumber?.toDoubleOrNull() ?: Double.MAX_VALUE }
    }
    LaunchedEffect(first != null) { TutorialDirector.demoAvailable.value = first != null }
    val map by settingsViewModel.remoteControlMap.collectAsStateWithLifecycle(initialValue = RemoteControlMap.DEFAULT)
    val selector by settingsViewModel.guideGroupSelector.collectAsStateWithLifecycle(initialValue = "pills")
    val firstNow by rememberUpdatedState(first)
    val mapNow by rememberUpdatedState(map)
    val sidebarNow by rememberUpdatedState(selector == "sidebar")
    val routeNow by rememberUpdatedState(currentRoute)
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        TutorialDirector.startRequests.collect {
            val channel = firstNow ?: return@collect
            onBackToMain()
            delay(400)
            onJumpToGuide(channel.guideMatchKey, System.currentTimeMillis())
            delay(2_500)
            TutorialDirector.start(scope, mapNow, sidebarNow) { routeNow?.startsWith("player/") == true }
        }
    }
    DisposableEffect(Unit) { onDispose { TutorialDirector.stop() } }
}

/**
 * The remote at the right edge while the tour runs: which part of the app,
 * the drawn remote with the key about to be pressed lit up, and what it does.
 * Never focusable, so the app underneath keeps every key.
 */
@Composable
fun TutorialTourPanel() {
    val shown by TutorialDirector.shown.collectAsStateWithLifecycle()
    val last = remember { mutableLast() }
    shown?.let { last.value = it }
    Box(modifier = Modifier.fillMaxSize().zIndex(10f), contentAlignment = Alignment.CenterEnd) {
        AnimatedVisibility(
            visible = shown != null,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            val s = last.value ?: return@AnimatedVisibility
            val bg = MaterialTheme.colorScheme.background
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, bg.copy(alpha = 0.94f), bg.copy(alpha = 0.97f)), startX = 0f, endX = 90f))
                    .padding(start = 36.dp, end = 24.dp, top = 24.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.tutorial_demo_title).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    s.section,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(12.dp))
                RemoteDrawing(
                    highlight = s.keys,
                    hold = s.hold,
                    twice = false,
                    modifier = Modifier.size(width = 120.dp, height = 240.dp),
                )
                Spacer(Modifier.height(14.dp))
                if (s.keys.isNotEmpty()) {
                    Text(
                        keyLabel(TutorialStep(s.keys, s.caption, hold = s.hold)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    s.caption,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.heightIn(min = 56.dp),
                )
                if (s.catchupNote) {
                    Spacer(Modifier.height(8.dp))
                    // The same symbol the guide draws on replayable shows.
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.Top) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Outlined.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.tutorial_demo_catchup_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.tutorial_demo_stop),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun mutableLast() = androidx.compose.runtime.mutableStateOf<TutorialDirector.Shown?>(null)
