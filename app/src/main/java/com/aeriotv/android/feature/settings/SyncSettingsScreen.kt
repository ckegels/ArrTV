package com.aeriotv.android.feature.settings

import com.aeriotv.android.ui.scale.subtext
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aeriotv.android.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeriotv.android.core.pip.findActivity
import com.aeriotv.android.core.sync.DriveSyncManager
import com.aeriotv.android.core.sync.SyncCategory
import com.aeriotv.android.core.sync.SyncConfig
import com.aeriotv.android.ui.adaptive.adaptiveFormWidth
import com.aeriotv.android.ui.settings.SettingsActionRow
import com.aeriotv.android.ui.settings.SettingsDetailTopBar
import com.aeriotv.android.ui.settings.SettingsDialogTextButton
import com.aeriotv.android.ui.settings.SettingsSection
import com.aeriotv.android.ui.settings.SettingsToggleRow
import com.aeriotv.android.ui.settings.dpadFocusRing
import com.aeriotv.android.ui.settings.settingsRowCard
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch
import com.aeriotv.android.ui.adaptive.LocalTabBarBottomInset

/**
 * Settings > Sync sub-screen. Mirrors iOS Settings > iCloud Sync layout.
 *
 * Sign-in is a two-step flow:
 *   1. Tap "Sign in with Google" → Credential Manager surfaces the
 *      account picker → we get the user's email + GoogleId token.
 *   2. We immediately call AuthorizationClient for the Drive AppData
 *      scope. If the user has previously granted, we get an access
 *      token directly. Otherwise we launch the consent IntentSender via
 *      an ActivityResultLauncher and parse the result on return.
 *
 * After step 2 succeeds the per-category toggles + Push / Pull / Clear
 * unlock. The "missing OAuth config" red banner shows only when the
 * BuildConfig.GOOGLE_DRIVE_WEB_CLIENT_ID field is empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    onBack: () -> Unit,
    viewModel: SyncSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val masterEnabled by viewModel.masterEnabled.collectAsStateWithLifecycle(initialValue = false)
    val accountEmail by viewModel.accountEmail.collectAsStateWithLifecycle(initialValue = "")
    val lastPush by viewModel.lastPushAt.collectAsStateWithLifecycle(initialValue = 0L)
    val lastPull by viewModel.lastPullAt.collectAsStateWithLifecycle(initialValue = 0L)
    val statusObj by viewModel.driveStatus.collectAsState()
    val clearStatus by viewModel.clearStatus.collectAsState()
    val pushStatus by viewModel.pushStatus.collectAsState()
    val pullStatus by viewModel.pullStatus.collectAsState()
    var pushConfirmOpen by remember { mutableStateOf(false) }
    var pullConfirmOpen by remember { mutableStateOf(false) }
    val configured = remember { SyncConfig.isConfigured() }

    // One-time disclosure that server credentials sync to Drive in cleartext
    // (audit task #53). initialValue=true keeps the dialog from flashing before
    // the real flag loads; the flow settles well before the user can toggle.
    val credsSyncDisclosed by viewModel.credentialsSyncDisclosed
        .collectAsStateWithLifecycle(initialValue = true)
    var credsSyncDisclosureOpen by remember { mutableStateOf(false) }

    // Silently restore a persisted Drive session on open so the screen shows
    // signed-in (and Push/Pull work) without a manual re-login.
    LaunchedEffect(Unit) { viewModel.restoreSessionIfPossible() }

    // Action results are point-in-time feedback; don't let a stale "Synced 5
    // categories" line greet the next visit (PlaylistDetailScreen pattern).
    DisposableEffect(Unit) {
        onDispose { viewModel.clearActionStatuses() }
    }

    var inFlight by remember { mutableStateOf(false) }
    // When Sign-in with Google is tapped on a build without an OAuth client
    // ID baked in, surface an explanatory dialog instead of silently doing
    // nothing — the prior "disabled button + no feedback" UX had testers
    // believing the integration itself was broken.
    var notConfiguredDialogOpen by remember { mutableStateOf(false) }

    // Consent intent launcher for step 2 of sign-in. AuthorizationClient may
    // return either an access token outright or a pendingIntent we have to
    // launch; this catches the result of the latter path.
    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.acceptConsentResult(result.data)
        inFlight = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsDetailTopBar(title = stringResource(R.string.settings2_section_sync), onBack = onBack)

        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.TopCenter,
        ) {
        val signedIn = statusObj is DriveSyncManager.Status.SignedIn
        // Single coroutine-launching closure shared between the inline Sign-In
        // button below the account card and any future entry point that wants
        // to kick off the flow.
        val triggerSignIn = {
            val activity = context.findActivity()
            if (activity != null) {
                inFlight = true
                scope.launch {
                    val email = viewModel.signInWithGoogle(activity)
                    if (email == null) {
                        inFlight = false
                        Toast.makeText(context, context.getString(R.string.settings2_sync_sign_in_failed), Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    when (val driveResult = viewModel.requestDriveScope()) {
                        is DriveSyncManager.RequestResult.Authorized -> {
                            inFlight = false
                            Toast.makeText(context, context.getString(R.string.settings2_sync_signed_in_as, email), Toast.LENGTH_SHORT).show()
                        }
                        is DriveSyncManager.RequestResult.NeedsConsent -> {
                            consentLauncher.launch(
                                IntentSenderRequest.Builder(driveResult.intentSender).build(),
                            )
                            // inFlight cleared by the launcher callback.
                        }
                        DriveSyncManager.RequestResult.Failed,
                        null -> {
                            inFlight = false
                            Toast.makeText(context, context.getString(R.string.settings2_sync_drive_auth_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        LazyColumn(
            // fillMaxHeight bounds the LazyColumn so its inner viewport can
            // scroll past the first screen of content — without it, the column
            // sized to wrap its contents and Settings -> Sync was stuck on
            // whatever fit above the bottom edge.
            modifier = Modifier.adaptiveFormWidth().fillMaxHeight(),
            // 104dp bottom clears the MainScaffold NavigationBar so the
            // Actions card (Push/Pull + Clear Drive Data) isn't clipped
            // when signed in.
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = LocalTabBarBottomInset.current,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!signedIn) {
                item { SignedOutWelcomeBanner() }
            }
            item {
                SettingsSection(
                    header = stringResource(R.string.settings2_sync_drive_header),
                    footer = stringResource(R.string.settings2_sync_drive_footer),
                ) {
                    AccountRow(
                        signedIn = signedIn,
                        email = accountEmail,
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.settings2_sync_enabled),
                        subtitle = if (signedIn)
                            stringResource(R.string.settings2_sync_enabled_subtitle_signed_in)
                        else
                            stringResource(R.string.settings2_sync_enabled_subtitle_signed_out),
                        checked = masterEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setMasterEnabled(enabled)
                            // First time sync is turned on, disclose that server
                            // credentials are part of the Drive snapshot.
                            if (enabled && !credsSyncDisclosed) {
                                credsSyncDisclosureOpen = true
                            }
                        },
                    )
                }
            }
            // Sign-in / sign-out lives in its own row below the account card so
            // the button has breathing room instead of getting squeezed into
                // the right edge of a multi-line description row.
                item {
                    if (signedIn) {
                        SignOutButton(
                            enabled = !inFlight,
                            onClick = {
                                viewModel.signOut()
                                Toast.makeText(context, context.getString(R.string.settings2_sync_signed_out), Toast.LENGTH_SHORT).show()
                            },
                        )
                    } else {
                        SignInWithGoogleButton(
                            // Stay enabled even without OAuth config so the
                            // tap surfaces the explanation dialog. inFlight
                            // is the only true disabled state — prevents
                            // double-launching the credential picker.
                            enabled = !inFlight,
                            onClick = {
                                if (!configured) {
                                    notConfiguredDialogOpen = true
                                } else {
                                    triggerSignIn()
                                }
                            },
                        )
                        if (!configured) {
                            Spacer(Modifier.height(8.dp))
                            DeveloperConfigHint()
                        }
                    }
                }

            item {
                SettingsSection(
                    header = stringResource(R.string.settings2_sync_categories_header),
                    // Glitzbr had to ask what triggers a sync, so say it.
                    // Android is NOT Apple's model: no live KVS push, just
                    // DriveSyncWorker every PERIOD_HOURS on an unmetered
                    // connection, plus the manual buttons below.
                    footer = stringResource(R.string.settings2_sync_categories_footer),
                ) {
                    SyncCategory.entries.forEach { category ->
                        val enabled by viewModel.categoryEnabled(category).collectAsStateWithLifecycle(initialValue = true)
                        SettingsToggleRow(
                            title = stringResource(category.displayNameRes),
                            subtitle = stringResource(category.subtitleRes),
                            checked = enabled,
                            onCheckedChange = { viewModel.setCategoryEnabled(category, it) },
                        )
                        // Slice of App Preferences rather than a category of
                        // its own: the map rides preferences.v1.json and each
                        // SyncCategory owns exactly one Drive file, so a second
                        // category pointing at that file would upload it twice.
                        // Sits directly under its parent so the nesting reads.
                        if (category == SyncCategory.Preferences) {
                            val shareRemoteMap by viewModel.syncRemoteControlMap
                                .collectAsStateWithLifecycle(initialValue = true)
                            SettingsToggleRow(
                                title = stringResource(R.string.settings2_sync_remote_map),
                                subtitle = stringResource(R.string.settings2_sync_remote_map_subtitle),
                                checked = shareRemoteMap,
                                onCheckedChange = { viewModel.setSyncRemoteControlMap(it) },
                            )
                        }
                    }
                }
            }

            if (signedIn) {
                item {
                    SettingsSection(
                        header = stringResource(R.string.settings2_sync_actions_header),
                        footer = stringResource(R.string.settings2_sync_actions_footer, formatTimestamp(lastPush), formatTimestamp(lastPull)),
                    ) {
                        // Inline spinner + result line replaced the old Toasts,
                        // which never surfaced on Android TV (rows looked dead).
                        val actionRunning =
                            clearStatus is SyncSettingsViewModel.ActionStatus.Running ||
                                pushStatus is SyncSettingsViewModel.ActionStatus.Running ||
                                pullStatus is SyncSettingsViewModel.ActionStatus.Running
                        // "Sync Now" (push-then-pull in one tap) was removed in
                        // favor of the explicit one-way Push/Pull actions: after a
                        // blank install overwrote a good Drive backup, the user
                        // wants every manual sync to state its direction. The
                        // periodic background worker still does push-then-pull.
                        SettingsActionRow(
                            label = stringResource(R.string.settings2_sync_push),
                            subtitle = stringResource(R.string.settings2_sync_push_subtitle),
                            leadingIcon = Icons.Filled.CloudUpload,
                            running = pushStatus is SyncSettingsViewModel.ActionStatus.Running,
                            statusLine = when (val s = pushStatus) {
                                is SyncSettingsViewModel.ActionStatus.Success -> s.message
                                is SyncSettingsViewModel.ActionStatus.Failure -> s.message
                                else -> null
                            },
                            statusIsError = pushStatus is SyncSettingsViewModel.ActionStatus.Failure,
                            onClick = {
                                if (inFlight || actionRunning) return@SettingsActionRow
                                pushConfirmOpen = true
                            },
                        )
                        SettingsActionRow(
                            label = stringResource(R.string.settings2_sync_pull),
                            subtitle = stringResource(R.string.settings2_sync_pull_subtitle),
                            leadingIcon = Icons.Filled.CloudDownload,
                            running = pullStatus is SyncSettingsViewModel.ActionStatus.Running,
                            statusLine = when (val s = pullStatus) {
                                is SyncSettingsViewModel.ActionStatus.Success -> s.message
                                is SyncSettingsViewModel.ActionStatus.Failure -> s.message
                                else -> null
                            },
                            statusIsError = pullStatus is SyncSettingsViewModel.ActionStatus.Failure,
                            onClick = {
                                if (inFlight || actionRunning) return@SettingsActionRow
                                pullConfirmOpen = true
                            },
                        )
                        SettingsActionRow(
                            label = stringResource(R.string.settings2_sync_clear),
                            leadingIcon = Icons.Filled.CloudOff,
                            destructive = true,
                            running = clearStatus is SyncSettingsViewModel.ActionStatus.Running,
                            statusLine = when (val s = clearStatus) {
                                is SyncSettingsViewModel.ActionStatus.Success -> s.message
                                is SyncSettingsViewModel.ActionStatus.Failure -> s.message
                                else -> null
                            },
                            statusIsError = clearStatus is SyncSettingsViewModel.ActionStatus.Failure,
                            onClick = {
                                if (inFlight || actionRunning) return@SettingsActionRow
                                viewModel.runClearRemote()
                            },
                        )
                    }
                }
            }
        }
        }
    }

    if (pushConfirmOpen) {
        com.aeriotv.android.ui.scale.AlertDialog(
            onDismissRequest = { pushConfirmOpen = false },
            title = { Text(stringResource(R.string.settings2_sync_push_confirm_title)) },
            text = {
                Text(
                    stringResource(R.string.settings2_sync_push_confirm_text),
                )
            },
            confirmButton = {
                SettingsDialogTextButton(
                    label = stringResource(R.string.settings2_sync_push_confirm_button),
                    onClick = {
                        pushConfirmOpen = false
                        viewModel.runPushOnly()
                    },
                )
            },
            dismissButton = {
                SettingsDialogTextButton(label = stringResource(R.string.common_cancel), onClick = { pushConfirmOpen = false })
            },
        )
    }

    if (pullConfirmOpen) {
        com.aeriotv.android.ui.scale.AlertDialog(
            onDismissRequest = { pullConfirmOpen = false },
            title = { Text(stringResource(R.string.settings2_sync_pull_confirm_title)) },
            text = {
                Text(
                    stringResource(R.string.settings2_sync_pull_confirm_text),
                )
            },
            confirmButton = {
                SettingsDialogTextButton(
                    label = stringResource(R.string.settings2_sync_pull_confirm_button),
                    onClick = {
                        pullConfirmOpen = false
                        viewModel.runPullOnly()
                    },
                    destructive = true,
                )
            },
            dismissButton = {
                SettingsDialogTextButton(label = stringResource(R.string.common_cancel), onClick = { pullConfirmOpen = false })
            },
        )
    }

    if (credsSyncDisclosureOpen) {
        com.aeriotv.android.ui.scale.AlertDialog(
            onDismissRequest = {
                credsSyncDisclosureOpen = false
                viewModel.markCredentialsSyncDisclosed()
            },
            title = { Text(stringResource(R.string.settings2_sync_creds_title)) },
            text = {
                Text(
                    stringResource(R.string.settings2_sync_creds_text),
                )
            },
            confirmButton = {
                SettingsDialogTextButton(
                    label = stringResource(R.string.settings2_sync_got_it),
                    onClick = {
                        credsSyncDisclosureOpen = false
                        viewModel.markCredentialsSyncDisclosed()
                    },
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (notConfiguredDialogOpen) {
        com.aeriotv.android.ui.scale.AlertDialog(
            onDismissRequest = { notConfiguredDialogOpen = false },
            title = { Text(stringResource(R.string.settings2_sync_not_set_up)) },
            text = {
                Text(
                    stringResource(R.string.settings2_sync_not_configured_text),
                )
            },
            confirmButton = {
                SettingsDialogTextButton(
                    label = stringResource(R.string.settings2_sync_got_it),
                    onClick = { notConfiguredDialogOpen = false },
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Friendly intro banner shown while the user hasn't signed in. The OAuth
 * config check moved to its own [DeveloperConfigHint] beneath the button,
 * so this card stays user-facing copy regardless of build state.
 */
@Composable
private fun SignedOutWelcomeBanner() {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(0.5.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp).padding(top = 2.dp),
        )
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings2_sync_not_set_up),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.settings2_sync_welcome_text),
                style = MaterialTheme.typography.labelSmall.subtext(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Tiny dev-facing note that surfaces only when the OAuth client ID is
 * missing from BuildConfig. Sits below the disabled Sign-In button instead
 * of dominating the screen with a red error banner. End-user releases ship
 * with the ID baked in and never see this row.
 */
@Composable
private fun DeveloperConfigHint() {
    Text(
        text = stringResource(R.string.settings2_sync_dev_config_hint),
        style = MaterialTheme.typography.labelSmall.subtext(),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun AccountRow(signedIn: Boolean, email: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .settingsRowCard(focused = false)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(
                    if (signedIn) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (signedIn)
                    androidx.compose.material.icons.Icons.Filled.AccountCircle
                else
                    androidx.compose.material.icons.Icons.Outlined.AccountCircle,
                contentDescription = null,
                tint = if (signedIn) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (signedIn) stringResource(R.string.settings2_sync_signed_in_to_drive) else stringResource(R.string.settings2_sync_not_signed_in),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = when {
                    signedIn && email.isNotBlank() -> email
                    signedIn -> stringResource(R.string.settings2_sync_account_connected)
                    else -> stringResource(R.string.settings2_sync_sign_in_prompt)
                },
                style = MaterialTheme.typography.bodySmall.subtext(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Full-width Sign-Out CTA — pairs visually with [SignInWithGoogleButton]
 * (same rounded-pill height and stretch). Renders below the account card
 * when signed in so the destructive action has space to breathe.
 */
@Composable
private fun SignOutButton(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .border(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(50))
            .dpadFocusRing(RoundedCornerShape(50), washTint = MaterialTheme.colorScheme.error)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.settings2_sync_sign_out),
            color = if (enabled) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Google-branded Sign in button. We render a Compose approximation that
 * follows Google's brand guidelines (white background, 1px outline, Google G
 * mark on the left, "Sign in with Google" label). Avoids embedding a raster
 * since the brand asset has strict size/colour rules and the rendering here
 * stays consistent across light/dark themes.
 */
@Composable
private fun SignInWithGoogleButton(enabled: Boolean, onClick: () -> Unit) {
    // Google ships two officially-permitted button styles: light (white BG /
    // dark text) and dark (#131314 BG / white text). Both must use the
    // full four-color G mark — the only freedom callers have is which
    // background variant they pick. The dark variant lands much better
    // against AerioTV's navy app surface than the white pill the previous
    // cut used, while staying compliant with Google's brand guidelines.
    // Tokens:
    //  - background #131314 (Google's "Dark" button surface)
    //  - 1dp outline #8E918F (Google's "Dark" 1dp stroke)
    //  - text #E3E3E3 (Google's "Dark" foreground)
    val bg = if (enabled) Color(0xFF131314) else Color(0xFF131314).copy(alpha = 0.55f)
    val stroke = Color(0xFF8E918F).copy(alpha = if (enabled) 1f else 0.55f)
    val fg = if (enabled) Color(0xFFE3E3E3) else Color(0xFFE3E3E3).copy(alpha = 0.55f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, stroke, RoundedCornerShape(50))
            // Brand pill stays untouched at rest; the white ring only draws under D-pad focus.
            .dpadFocusRing(RoundedCornerShape(50))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(
                id = com.aeriotv.android.R.drawable.ic_google_g,
            ),
            contentDescription = null,
            // Tint.Unspecified preserves the four-color brand mark; tinting
            // would flatten it to a single colour which Google disallows.
            tint = Color.Unspecified,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = stringResource(R.string.settings2_sync_sign_in_with_google),
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

// Phase 61b: removed the inline GoogleGMark approximation — the button now
// renders the official four-color brand mark from res/drawable/ic_google_g.xml.

@Composable
private fun formatTimestamp(value: Long): String {
    if (value <= 0L) return stringResource(R.string.settings2_sync_never)
    val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    return df.format(Date(value))
}
