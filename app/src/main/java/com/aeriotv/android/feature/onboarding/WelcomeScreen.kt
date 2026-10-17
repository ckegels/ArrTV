package com.aeriotv.android.feature.onboarding

import com.aeriotv.android.ui.theme.decorSecondary
import com.aeriotv.android.ui.scale.subtext
import com.aeriotv.android.ui.theme.textAccent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aeriotv.android.R
import androidx.compose.ui.focus.focusRequester
import com.aeriotv.android.ui.settings.dpadFocusRing
import com.aeriotv.android.feature.onboarding.components.SourceTypeCard
import androidx.compose.ui.res.stringResource

/**
 * Cold-start welcome surface. Mirrors iOS App Store screenshot IMG_1076: AerioTV
 * brand block + supported source types + Sync / Detect-Home-WiFi info cards +
 * "Connect a Server" CTA + "Skip for now" link.
 *
 * Layout adapts to viewport: a short-but-wide screen (Android TV at 960dp x
 * 540dp, tablets in landscape, foldables unfolded) switches to a two-column
 * presentation so the CTA stays above the fold and the user can reach it with
 * a D-pad without scrolling. Portrait phones get the original vertical stack.
 */
@Composable
fun WelcomeScreen(
    /** The main way in: Dispatcharr username and password login. */
    onConnectServer: () -> Unit,
    onSkip: () -> Unit,
    /** Xtream Codes or M3U, picked on the Other options page (unsupported fallbacks). */
    onChooseSource: (com.aeriotv.android.core.data.SourceType) -> Unit = {},
    /**
     * Optional Sign-in-with-Google handler. When provided, a pill appears
     * between Connect-a-Server and Skip on the welcome screen so the user
     * can authenticate Drive Sync up front instead of being routed back to
     * Settings later. Setting this null hides the row entirely (e.g. on
     * builds that ship without a Google Cloud OAuth client ID baked in).
     */
    onSignInWithGoogle: (() -> Unit)? = null,
    googleSignInInProgress: Boolean = false,
    /** Local network discovery: picking a Dispatcharr server found on the
     *  subnet opens its login with the address filled in. Null = no scan. */
    onPickDiscoveredServer: ((DiscoveredServer) -> Unit)? = null,
) {
    // First run: the language comes before anything there is to read.
    val context = androidx.compose.ui.platform.LocalContext.current
    var languageDone by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(languageChosen(context))
    }
    if (!languageDone) {
        LanguageStep(onDone = { languageDone = true })
        return
    }
    // Other options: Google restore and the unsupported sources, one step away
    // so the welcome page itself is just the Dispatcharr login.
    var showOther by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(false)
    }
    if (showOther) {
        androidx.activity.compose.BackHandler { showOther = false }
        OtherOptionsPage(onSignInWithGoogle, googleSignInInProgress, onChooseSource)
        return
    }
    val onOtherSources = { showOther = true }
    val config = LocalConfiguration.current
    val isTv = com.aeriotv.android.feature.livetv.rememberLiveTvFormFactor().isTv
    // Two-column when the viewport is meaningfully wider than tall (tablet
    // landscape, foldable unfolded). TV is excluded: it uses the centered
    // single column to mirror the tvOS welcome layout.
    val twoColumn = !isTv && config.screenWidthDp >= 720 && config.screenHeightDp < 720

    // Scan once for Dispatcharr servers on the local network; any found show
    // up as the first option.
    val discovered: @Composable () -> Unit = if (onPickDiscoveredServer != null) {
        val (servers, scanning) = rememberServerDiscovery()
        ({ DiscoveredServersBlock(servers, scanning, onPickDiscoveredServer) })
    } else {
        {}
    }
    if (twoColumn) WelcomeTwoColumn(onConnectServer, onSkip, onOtherSources, onSignInWithGoogle, googleSignInInProgress, discovered)
    else WelcomeSingleColumn(onConnectServer, onSkip, onOtherSources, onSignInWithGoogle, googleSignInInProgress, discovered)
}

@Composable
private fun WelcomeSingleColumn(
    onConnectServer: () -> Unit,
    onSkip: () -> Unit,
    onOtherSources: () -> Unit,
    onSignInWithGoogle: (() -> Unit)?,
    googleSignInInProgress: Boolean,
    discovered: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        WelcomeAmbientOrbs(modifier = Modifier.fillMaxSize())
        // Mirrors the tvOS WelcomeView: a single centered column, brand on top,
        // the three supported source types, ONE Sync card, a Connect-a-Server
        // row, then Skip. Sized to fit a 1080p Android TV (~540dp tall, half
        // tvOS's 1080pt logical height) without scrolling. Centered so it sits
        // balanced; verticalScroll is only a last-ditch fallback for an
        // unusually short viewport.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrandBlock()
            Spacer(Modifier.height(16.dp))
            WelcomeActions(onConnectServer, onSkip, onOtherSources, onSignInWithGoogle, googleSignInInProgress, discovered)
        }
    }
}

@Composable
private fun WelcomeTwoColumn(
    onConnectServer: () -> Unit,
    onSkip: () -> Unit,
    onOtherSources: () -> Unit,
    onSignInWithGoogle: (() -> Unit)?,
    googleSignInInProgress: Boolean,
    discovered: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        WelcomeAmbientOrbs(modifier = Modifier.fillMaxSize())
        WelcomeTwoColumnRow(onConnectServer, onSkip, onOtherSources, onSignInWithGoogle, googleSignInInProgress, discovered)
    }
}

@Composable
private fun WelcomeTwoColumnRow(
    onConnectServer: () -> Unit,
    onSkip: () -> Unit,
    onOtherSources: () -> Unit,
    onSignInWithGoogle: (() -> Unit)?,
    googleSignInInProgress: Boolean,
    discovered: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        // Left column: brand + supported source types. Centered vertically.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            BrandBlock(alignStart = true)
        }
        // Right column: info cards + CTAs. The Connect button sits in the
        // viewport center on TV so the focus ring lands on it at cold start,
        // which is what a remote-driven flow wants.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .widthIn(max = 540.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            WelcomeActions(onConnectServer, onSkip, onOtherSources, onSignInWithGoogle, googleSignInInProgress, discovered)
        }
    }
}

@Composable
private fun BrandBlock(alignStart: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (alignStart) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        BrandLogo()
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.main_welcome_tagline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.textAccent,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.main_welcome_devices),
            style = MaterialTheme.typography.bodySmall.subtext(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The welcome page's choices, in order: Dispatcharr servers found on the
 * network and the Dispatcharr login (the main way in: the app is built for
 * Dispatcharr with Dispatch More), then Other options (Google restore and the
 * unsupported sources) and Skip as quiet links.
 */
@Composable
private fun WelcomeActions(
    onConnectServer: () -> Unit,
    onSkip: () -> Unit,
    onOtherSources: () -> Unit,
    onSignInWithGoogle: (() -> Unit)?,
    googleSignInInProgress: Boolean,
    discovered: @Composable () -> Unit,
) {
    discovered()
    Spacer(Modifier.height(12.dp))
    // Focus starts on the main way in, so a remote's OK goes straight to the login.
    val connectFocus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { connectFocus.requestFocus() } }
    Box(modifier = Modifier.focusRequester(connectFocus)) {
        ConnectServerRow(onClick = onConnectServer)
    }
    Spacer(Modifier.height(8.dp))
    // The two quiet links share a row so the page fits a 1080p TV (~540dp).
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LinkRow(text = stringResource(R.string.main_welcome_other_sources), onClick = onOtherSources)
        SkipRow(onSkip = onSkip)
    }
}

/**
 * Other options, one step from the welcome page: restore from Google, and the
 * sources the app plays but does not support (Xtream Codes, M3U), with why.
 */
@Composable
private fun OtherOptionsPage(
    onSignInWithGoogle: (() -> Unit)?,
    googleSignInInProgress: Boolean,
    onChooseSource: (com.aeriotv.android.core.data.SourceType) -> Unit,
) {
    val firstFocus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        WelcomeAmbientOrbs(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.main_welcome_other_sources),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            val cardModifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .dpadFocusRing(RoundedCornerShape(16.dp), washTint = MaterialTheme.colorScheme.primary)
            if (onSignInWithGoogle != null) {
                Box(modifier = Modifier.focusRequester(firstFocus)) {
                    SyncCard(inProgress = googleSignInInProgress, onClick = onSignInWithGoogle)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.main_welcome_other_sources_header),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.main_choose_unsupported_note, stringResource(R.string.app_name)),
                style = MaterialTheme.typography.bodySmall.subtext(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SourceTypeCard(
                icon = Icons.Filled.Tv,
                title = "Xtream Codes",
                subtitle = stringResource(R.string.main_source_xtream_subtitle),
                modifier = cardModifier
                    .then(if (onSignInWithGoogle == null) Modifier.focusRequester(firstFocus) else Modifier)
                    .clickable { onChooseSource(com.aeriotv.android.core.data.SourceType.XtreamCodes) },
            )
            SourceTypeCard(
                icon = Icons.Filled.Description,
                title = "M3U + EPG",
                subtitle = stringResource(R.string.main_source_m3u_subtitle),
                modifier = cardModifier.clickable { onChooseSource(com.aeriotv.android.core.data.SourceType.M3uUrl) },
            )
        }
    }
}

/**
 * Sync opt-in card, the Drive analog of the tvOS WelcomeView iCloud card: one
 * card with an On/Off status pill. On the welcome screen sync is always Off
 * (signing in transitions onboarding to the restore-progress screen), so the
 * card is the tap target that launches Google sign-in. Hidden entirely when
 * the build ships without an OAuth client id (onClick null).
 */
@Composable
private fun SyncCard(inProgress: Boolean, onClick: (() -> Unit)?) {
    if (onClick == null) return
    SourceTypeCard(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !inProgress, onClick = onClick),
        icon = Icons.Filled.CloudOff,
        title = stringResource(R.string.main_welcome_sync_title),
        subtitle = stringResource(R.string.main_welcome_sync_subtitle),
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.decorSecondary),
                )
                Text(
                    text = if (inProgress) "..." else stringResource(R.string.common_off),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        },
    )
}

/** "Connect a Server" as a single-line row with a trailing chevron, mirroring
 *  the tvOS WelcomeView nav row (not a full-bleed gradient button). */
@Composable
private fun ConnectServerRow(onClick: () -> Unit) {
    SourceTypeCard(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        icon = Icons.Filled.Key,
        title = stringResource(R.string.main_welcome_connect_dispatcharr),
        subtitle = stringResource(R.string.main_welcome_connect_dispatcharr_subtitle),
        trailing = {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SkipRow(onSkip: () -> Unit) {
    TextButton(onClick = onSkip) {
        Text(
            text = stringResource(R.string.main_welcome_skip_for_now),
            color = MaterialTheme.colorScheme.textAccent,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun BrandLogo() {
    // Mirrors iOS WelcomeView's `.shadow(color: aerioCyan @ 0.45, radius: 20, y: 8)`
    // under the logo. Compose's shadow modifier only renders elevation drop-shadows,
    // not colored glows, so we draw a soft radial gradient halo behind the logo
    // bitmap instead — same visual effect, API-agnostic.
    val accent = MaterialTheme.colorScheme.primary
    // Sized to read as a hero logo while still letting the whole stacked page
    // fit a ~540dp-tall Android TV without scrolling.
    val haloSize = 84.dp
    val imageSize = 56.dp
    Box(
        modifier = Modifier.size(haloSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(haloSize)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to accent.copy(alpha = 0.55f),
                        0.35f to accent.copy(alpha = 0.28f),
                        0.7f to accent.copy(alpha = 0.06f),
                        1.0f to Color.Transparent,
                    ),
                    center = Offset(size.width / 2f, size.height / 2f + 8.dp.toPx()),
                    radius = size.minDimension / 2f,
                ),
                center = Offset(size.width / 2f, size.height / 2f + 8.dp.toPx()),
                radius = size.minDimension / 2f,
            )
        }
        Image(
            painter = painterResource(id = R.drawable.aerio_logo),
            contentDescription = null,
            modifier = Modifier.size(imageSize),
        )
    }
}

/**
 * Two soft, blurred accent orbs in opposite corners — mirrors iOS WelcomeView
 * lines 22-35 where SwiftUI uses `Circle().blur(radius: 80)` on top of the
 * navy background. Compose's Modifier.blur is API 31+, so the same look here
 * comes from radial gradients with carefully tuned color stops.
 */
@Composable
private fun WelcomeAmbientOrbs(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Canvas(modifier = modifier) {
        // Top-left orb (cyan)
        val tlCenter = Offset(-50.dp.toPx(), -30.dp.toPx())
        val tlRadius = 360.dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to primary.copy(alpha = 0.18f),
                    0.4f to primary.copy(alpha = 0.10f),
                    0.75f to primary.copy(alpha = 0.03f),
                    1.0f to Color.Transparent,
                ),
                center = tlCenter,
                radius = tlRadius,
            ),
            center = tlCenter,
            radius = tlRadius,
        )
        // Bottom-right orb (teal)
        val brCenter = Offset(size.width + 50.dp.toPx(), size.height - 80.dp.toPx())
        val brRadius = 320.dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to secondary.copy(alpha = 0.16f),
                    0.4f to secondary.copy(alpha = 0.08f),
                    0.75f to secondary.copy(alpha = 0.02f),
                    1.0f to Color.Transparent,
                ),
                center = brCenter,
                radius = brRadius,
            ),
            center = brCenter,
            radius = brRadius,
        )
    }
}

/** Linear cyan→teal gradient matching iOS LinearGradient.accentGradient
 * (top-leading to bottom-trailing). */
@Composable
private fun accentBrush(): Brush = Brush.linearGradient(
    colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
    ),
)

/** Icon filled with a Brush instead of a solid tint — mirrors iOS FeaturePill's
 * `.foregroundStyle(LinearGradient.accentGradient)` on the SF Symbol. */
@Composable
private fun GradientIcon(
    imageVector: ImageVector,
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawRect(brush = brush, blendMode = BlendMode.SrcIn)
                },
        )
    }
}

