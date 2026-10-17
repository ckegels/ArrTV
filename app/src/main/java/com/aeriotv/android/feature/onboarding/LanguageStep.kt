package com.aeriotv.android.feature.onboarding

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aeriotv.android.R
import com.aeriotv.android.core.locale.AppLanguage
import com.aeriotv.android.core.locale.AppLocale
import com.aeriotv.android.core.pip.findActivity
import com.aeriotv.android.feature.onboarding.components.SourceTypeCard
import com.aeriotv.android.ui.scale.subtext
import com.aeriotv.android.ui.settings.dpadFocusRing

/**
 * First step of a first run: the app's language, before any text the user has
 * to read. Shown once per install ([languageChosen] / [markLanguageChosen]);
 * Settings › Appearance › Language changes it afterwards. The title is in both
 * languages so it reads whatever the device is set to.
 */
@Composable
fun LanguageStep(onDone: () -> Unit) {
    val context = LocalContext.current
    val current = remember { AppLocale.current(context) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.main_onboarding_language_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.main_onboarding_language_subtitle, stringResource(R.string.app_name)),
                style = MaterialTheme.typography.bodyMedium.subtext(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            AppLanguage.entries.forEachIndexed { i, option ->
                SourceTypeCard(
                    icon = Icons.Outlined.Language,
                    title = option.nativeName ?: stringResource(R.string.language_system),
                    subtitle = null,
                    modifier = Modifier
                        .then(if (i == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        .clip(RoundedCornerShape(16.dp))
                        .dpadFocusRing(RoundedCornerShape(16.dp), washTint = MaterialTheme.colorScheme.primary)
                        .clickable {
                            markLanguageChosen(context)
                            if (option == current) {
                                onDone()
                            } else {
                                // Restarts the screen in the new language; the
                                // mark above makes it come back on the welcome page.
                                context.findActivity()?.let { AppLocale.set(it, option) } ?: onDone()
                            }
                        },
                    trailing = if (option == current) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                )
            }
        }
    }
}

private const val ONBOARDING_PREFS = "onboarding"
private const val KEY_LANGUAGE_CHOSEN = "language_chosen"

/** True once the first-run language step has been answered on this install. */
fun languageChosen(context: Context): Boolean =
    context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LANGUAGE_CHOSEN, false)

/** Written before the language is applied (which may restart the activity). */
fun markLanguageChosen(context: Context) {
    context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE).edit()
        .putBoolean(KEY_LANGUAGE_CHOSEN, true).commit()
}
