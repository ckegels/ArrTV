package com.aeriotv.android.feature.onboarding

import com.aeriotv.android.ui.scale.subtext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aeriotv.android.ui.settings.TvSettingsMetrics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aeriotv.android.core.data.SourceType
import com.aeriotv.android.feature.onboarding.components.SourceTypeCard
import com.aeriotv.android.ui.adaptive.rememberViewport
import com.aeriotv.android.ui.settings.dpadFocusRing
import com.aeriotv.android.ui.settings.rememberIsTvDevice
import androidx.compose.ui.res.stringResource
import com.aeriotv.android.R

/**
 * Mirrors iOS App Store screenshot IMG_1077: "Add Playlist" back stack frame
 * with "Choose Source Type" header and three rounded cards (Dispatcharr,
 * Xtream Codes, M3U + EPG). Tapping a card pushes to the Configure form.
 *
 * Per the no-bundled-media rule, this surface ships zero hardcoded URLs - the
 * cards only describe types, the user picks one and supplies their own URL on
 * the next screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseSourceTypeScreen(
    onBack: () -> Unit,
    onChoose: (SourceType) -> Unit,
    /** False on the welcome page's "Other sources": the Dispatcharr login is
     *  already its main card there, so only the unsupported fallbacks show. */
    showDispatcharr: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.main_add_playlist_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    // TV hides the back arrow below, which collapses the
                    // navigation-icon slot and drops the title onto Material's
                    // 16dp inset - inside the title-safe margin. Re-establish
                    // the same left edge the Settings rail uses.
                    modifier = if (rememberIsTvDevice()) {
                        Modifier.padding(start = TvSettingsMetrics.topAppBarTitleOverscanPadding)
                    } else {
                        Modifier
                    },
                )
            },
            navigationIcon = {
                // No back arrow on Android TV -- the remote BACK steps back
                // (out of the Add Playlist wizard). Phones/tablets keep it.
                if (!rememberIsTvDevice()) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        )

        val vp = rememberViewport()
        // tvOS parity: the three source types are a single vertical stack of
        // rounded rows (not a 3-up grid), centered with a tighter max width so
        // the rows aren't stretched edge-to-edge on a wide TV.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = vp.gutter, vertical = 12.dp),
            contentAlignment = androidx.compose.ui.Alignment.TopCenter,
        ) {
            Column(
                modifier = if (vp.onboardingMaxWidth != androidx.compose.ui.unit.Dp.Unspecified)
                    Modifier.widthIn(max = vp.onboardingMaxWidth).fillMaxWidth()
                else
                    Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(if (showDispatcharr) R.string.main_choose_source_title else R.string.main_choose_other_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                if (showDispatcharr) {
                    Text(
                        text = stringResource(R.string.main_choose_source_subtitle),
                        style = MaterialTheme.typography.bodyMedium.subtext(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(4.dp))

                    // The main way in: the app is built for Dispatcharr with Dispatch More.
                    SourceTypeCard(
                        icon = Icons.Filled.Key,
                        title = stringResource(R.string.main_source_dispatcharr_title),
                        subtitle = stringResource(R.string.main_source_dispatcharr_subtitle),
                        modifier = Modifier.tappable { onChoose(SourceType.DispatcharrUserPass) },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.main_choose_unsupported_header),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = stringResource(R.string.main_choose_unsupported_note, stringResource(R.string.app_name)),
                    style = MaterialTheme.typography.bodySmall.subtext(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                SourceTypeCard(
                    icon = Icons.Filled.Tv,
                    title = "Xtream Codes",
                    subtitle = stringResource(R.string.main_source_xtream_subtitle),
                    modifier = Modifier.tappable { onChoose(SourceType.XtreamCodes) },
                )
                SourceTypeCard(
                    icon = Icons.Filled.Description,
                    title = "M3U + EPG",
                    subtitle = stringResource(R.string.main_source_m3u_subtitle),
                    modifier = Modifier.tappable { onChoose(SourceType.M3uUrl) },
                )
            }
        }
    }
}

@Composable
private fun Modifier.tappable(onClick: () -> Unit): Modifier =
    // Clip to the SourceTypeCard rounded shape BEFORE the clickable so the D-pad
    // focus indication follows the rounded corners, not a squared rectangle.
    this.clip(RoundedCornerShape(16.dp))
        .dpadFocusRing(RoundedCornerShape(16.dp), washTint = MaterialTheme.colorScheme.primary)
        .clickable(onClick = onClick)
