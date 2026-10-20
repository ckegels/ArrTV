package com.aeriotv.android.feature.settings

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.aeriotv.android.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeriotv.android.ui.TmdbAttribution
import com.aeriotv.android.ui.adaptive.LocalTabBarBottomInset
import com.aeriotv.android.ui.adaptive.rememberViewport
import com.aeriotv.android.ui.scale.subtext
import com.aeriotv.android.ui.settings.SettingsDetailTopBar
import com.aeriotv.android.ui.settings.SettingsSection
import com.aeriotv.android.ui.settings.SettingsSelectionRow
import com.aeriotv.android.ui.settings.SettingsToggleRow
import com.aeriotv.android.ui.settings.dpadFocusRing
import com.aeriotv.android.ui.settings.rememberIsTvDevice
import com.aeriotv.android.ui.textfield.SecretRevealIconButton
import com.aeriotv.android.ui.textfield.rememberSecretRevealState
import com.aeriotv.android.ui.tv.TvKeyboardOnOkHost
import com.aeriotv.android.ui.tv.tvFormFieldInput

/**
 * Settings > Movies & TV Shows. Library refresh cadence, TMDB poster lookup,
 * and the Movies & Series display scale.
 *
 * Settings phase 1 regroup: the refresh cadence and Program Posters rows came
 * from App Behaviors, the scale row from Appearance. Keys, control types and
 * copy are carried over unchanged, including the TV-only gate on the refresh
 * cadence rows.
 */
@Composable
fun MoviesAndTvShowsSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val isTv = rememberIsTvDevice()
    val vodRefreshHours by viewModel.vodLibraryRefreshHours.collectAsStateWithLifecycle(initialValue = 24)
    val programPostersTmdb by viewModel.programPostersTmdbEnabled.collectAsStateWithLifecycle(initialValue = false)
    val savedTmdbKey by viewModel.tmdbApiKey.collectAsStateWithLifecycle(initialValue = "")
    val tmdbKeyState by viewModel.tmdbKeyTestState.collectAsStateWithLifecycle()
    val scaleMovies by viewModel.displayScaleMovies.collectAsStateWithLifecycle(initialValue = 1.0f)

    TvKeyboardOnOkHost {
        Column(modifier = Modifier.fillMaxSize()) {
            val context = LocalContext.current
            SettingsDetailTopBar(title = stringResource(R.string.settings_movies_title), onBack = onBack)

            val vp = rememberViewport()
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = if (vp.formMaxWidth != Dp.Unspecified) {
                        Modifier.widthIn(max = vp.formMaxWidth)
                    } else {
                        Modifier
                    },
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = LocalTabBarBottomInset.current,
                    ),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // MARK: Refresh library
                    //
                    // Shown on every form factor (Apple parity): the cadence is
                    // read by the shared OnDemandViewModel, so it always applied
                    // everywhere even while the rows were TV-only.
                    item("refresh-library") {
                        SettingsSection(header = stringResource(R.string.settings_movies_refresh_library)) {
                            listOf(
                                0 to R.string.settings_movies_refresh_every_launch,
                                24 to R.string.settings_movies_refresh_daily,
                                168 to R.string.settings_movies_refresh_weekly,
                            ).forEach { (hours, label) ->
                                SettingsSelectionRow(
                                    label = stringResource(label),
                                    selected = vodRefreshHours == hours,
                                    onClick = { viewModel.setVodLibraryRefreshHours(hours) },
                                )
                            }
                            Text(
                                text = stringResource(R.string.settings_movies_refresh_note),
                                style = MaterialTheme.typography.bodySmall.subtext(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }

                    // MARK: Posters
                    item("posters") {
                        SettingsSection(
                            header = stringResource(R.string.settings_movies_posters_header),
                            footer = stringResource(R.string.settings_movies_posters_footer),
                        ) {
                            SettingsToggleRow(
                                title = stringResource(R.string.settings_movies_tmdb_fallback),
                                subtitle = stringResource(R.string.settings_movies_tmdb_fallback_subtitle),
                                checked = programPostersTmdb,
                                onCheckedChange = viewModel::setProgramPostersTmdbEnabled,
                            )
                            if (programPostersTmdb) {
                                var keyDraft by remember(savedTmdbKey) { mutableStateOf(savedTmdbKey) }
                                val keyReveal = rememberSecretRevealState()
                                TmdbAttribution(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                    long = false,
                                    isTv = isTv,
                                )
                                OutlinedTextField(
                                    value = keyDraft,
                                    onValueChange = {
                                        keyDraft = it
                                        viewModel.resetTmdbKeyTestState()
                                    },
                                    label = { Text(stringResource(R.string.settings_movies_tmdb_key_label)) },
                                    singleLine = true,
                                    visualTransformation = keyReveal.transformation,
                                    trailingIcon = {
                                        SecretRevealIconButton(state = keyReveal, contentLabel = stringResource(R.string.main_secret_label_key))
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .tvFormFieldInput(
                                            horizontalFocusEscape = true,
                                            okSuppressed = { keyReveal.controlFocused },
                                        ),
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.testTmdbKey(keyDraft) },
                                        enabled = keyDraft.isNotBlank() &&
                                            tmdbKeyState != SettingsViewModel.TmdbKeyTestState.Testing,
                                        modifier = Modifier.dpadFocusRing(RoundedCornerShape(50)),
                                    ) { Text(stringResource(R.string.settings_movies_tmdb_test)) }
                                    TextButton(
                                        onClick = { viewModel.saveTmdbKey(keyDraft) },
                                        enabled = keyDraft.isNotBlank() || savedTmdbKey.isNotBlank(),
                                        modifier = Modifier.dpadFocusRing(RoundedCornerShape(50)),
                                    ) { Text(stringResource(R.string.common_save)) }
                                    Spacer(Modifier.weight(1f))
                                    val (statusText, statusColor) = when (tmdbKeyState) {
                                        SettingsViewModel.TmdbKeyTestState.Testing ->
                                            stringResource(R.string.settings_movies_tmdb_checking) to MaterialTheme.colorScheme.onSurfaceVariant
                                        SettingsViewModel.TmdbKeyTestState.Valid ->
                                            stringResource(R.string.settings_movies_tmdb_valid) to androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                        SettingsViewModel.TmdbKeyTestState.Invalid ->
                                            stringResource(R.string.settings_movies_tmdb_invalid) to MaterialTheme.colorScheme.error
                                        SettingsViewModel.TmdbKeyTestState.Saved ->
                                            stringResource(R.string.settings_movies_tmdb_saved) to androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                        SettingsViewModel.TmdbKeyTestState.Idle ->
                                            "" to MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    if (statusText.isNotEmpty()) {
                                        Text(
                                            statusText,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = statusColor,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // MARK: Display Scale
                    settingsCard(
                        header = context.getString(R.string.settings_display_scale_header),
                        footer = context.getString(R.string.settings_movies_scale_footer),
                    ) {
                        ScaleSliderRow(
                            label = stringResource(R.string.settings_movies_scale_label),
                            value = scaleMovies,
                            onValueChange = viewModel::setDisplayScaleMovies,
                        )
                    }
                }
            }
        }
    }
}
