package com.aeriotv.android.feature.settings

import androidx.compose.ui.res.stringResource
import com.aeriotv.android.R
import com.aeriotv.android.ui.scale.subtext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import com.aeriotv.android.ui.scale.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeriotv.android.core.category.CategoryPaletteState
import com.aeriotv.android.core.category.CustomCategoryEntry
import com.aeriotv.android.core.category.ProgramCategory
import com.aeriotv.android.core.category.parseHex
import com.aeriotv.android.ui.adaptive.adaptiveFormWidth
import com.aeriotv.android.ui.settings.OnOffIndicator
import com.aeriotv.android.ui.settings.SettingsDialogTextButton
import com.aeriotv.android.ui.settings.dpadFocusRing
import com.aeriotv.android.ui.settings.dpadFocusWash
import com.aeriotv.android.ui.settings.rememberIsTvDevice
import com.aeriotv.android.ui.textfield.aerioTextFieldKeyboardOptions
import java.util.UUID
import com.aeriotv.android.ui.adaptive.LocalTabBarBottomInset

/**
 * Sub-screen reached from Appearance > Add more categories. Two sections:
 *
 *   1. Additional buckets (Documentary, Drama, Comedy, Reality, Educational,
 *      Sci-Fi / Fantasy, Music): toggle on/off + select to pick a hex.
 *   2. Custom categories: user-defined `match` substring + hex, persisted as
 *      a JSON array.
 *
 * Custom entries take precedence over built-in buckets at resolve time so a
 * user who adds e.g. "Horror" sees horror programmes coloured even though
 * Horror isn't a built-in bucket. Mirrors iOS Settings > Guide Display > Add
 * More Categories.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMoreCategoriesScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val palette by viewModel.categoryPalette.collectAsStateWithLifecycle(initialValue = CategoryPaletteState.Default)

    var pickerTarget by remember { mutableStateOf<ProgramCategory?>(null) }
    var addCustomOpen by remember { mutableStateOf(false) }
    var editingCustom by remember { mutableStateOf<CustomCategoryEntry?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.settings2_categories_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            navigationIcon = {
                // No back arrow on Android TV -- the remote BACK pops it.
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
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        )

        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.TopCenter,
        ) {
        LazyColumn(
            modifier = Modifier.adaptiveFormWidth(),
            // 104dp bottom clears the MainScaffold NavigationBar so the
            // custom-category section + Save row at the bottom stay
            // reachable.
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = LocalTabBarBottomInset.current,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings2_categories_extra_header),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.settings2_categories_extra_text),
                    style = MaterialTheme.typography.bodySmall.subtext(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(ProgramCategory.additionalBuckets, key = { it.storageSuffix }) { bucket ->
                AdditionalBucketRow(
                    bucket = bucket,
                    enabled = palette.isBucketEnabled(bucket),
                    hex = palette.hexFor(bucket),
                    onToggle = { viewModel.setCategoryBucketEnabled(bucket, it) },
                    onPick = { pickerTarget = bucket },
                )
            }

            item { Spacer(Modifier.height(12.dp)) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings2_categories_custom_header),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.settings2_categories_custom_text),
                            style = MaterialTheme.typography.bodySmall.subtext(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { addCustomOpen = true },
                        modifier = Modifier.dpadFocusRing(
                            CircleShape,
                            washTint = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.settings2_categories_add_custom_cd),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (palette.custom.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.settings2_categories_no_custom),
                        style = MaterialTheme.typography.bodySmall.subtext(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(palette.custom, key = { it.id }) { entry ->
                    CustomEntryRow(
                        entry = entry,
                        onEdit = { editingCustom = entry },
                        onDelete = {
                            viewModel.setCustomCategories(palette.custom.filterNot { it.id == entry.id })
                        },
                    )
                }
            }
        }
        }
    }

    pickerTarget?.let { bucket ->
        HexPickerDialog(
            bucket = bucket,
            currentHex = palette.hexFor(bucket),
            onDismiss = { pickerTarget = null },
            onSave = { hex ->
                viewModel.setCategoryBucketHex(bucket, hex)
                pickerTarget = null
            },
            onReset = {
                viewModel.setCategoryBucketHex(bucket, null)
                pickerTarget = null
            },
        )
    }

    if (addCustomOpen) {
        CustomEntryDialog(
            initial = null,
            onDismiss = { addCustomOpen = false },
            onSave = { match, hex ->
                val updated = palette.custom + CustomCategoryEntry(
                    id = UUID.randomUUID().toString(),
                    match = match,
                    hex = hex,
                )
                viewModel.setCustomCategories(updated)
                addCustomOpen = false
            },
        )
    }
    editingCustom?.let { existing ->
        CustomEntryDialog(
            initial = existing,
            onDismiss = { editingCustom = null },
            onSave = { match, hex ->
                val updated = palette.custom.map {
                    if (it.id == existing.id) it.copy(match = match, hex = hex) else it
                }
                viewModel.setCustomCategories(updated)
                editingCustom = null
            },
        )
    }
}

@Composable
private fun AdditionalBucketRow(
    bucket: ProgramCategory,
    enabled: Boolean,
    hex: String,
    onToggle: (Boolean) -> Unit,
    onPick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .dpadFocusWash()
            .clickable { onToggle(!enabled) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = bucket.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(bucket.labelRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(50))
                .background(parseHex(hex))
                .border(
                    1.5.dp,
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(50),
                )
                .dpadFocusRing(RoundedCornerShape(50))
                .clickable(onClick = onPick),
        )
        Spacer(Modifier.width(10.dp))
        OnOffIndicator(on = enabled)
    }
}

@Composable
private fun CustomEntryRow(
    entry: CustomCategoryEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .dpadFocusWash()
            .clickable(onClick = onEdit)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(50))
                .background(parseHex(entry.hex))
                .border(
                    1.5.dp,
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(50),
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.match.ifBlank { stringResource(R.string.settings2_categories_unnamed) },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "#${entry.hex.uppercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.dpadFocusRing(
                CircleShape,
                washTint = MaterialTheme.colorScheme.error,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.common_delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun CustomEntryDialog(
    initial: CustomCategoryEntry?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var match by remember { mutableStateOf(initial?.match.orEmpty()) }
    var hex by remember { mutableStateOf((initial?.hex ?: "888888").uppercase()) }

    val sanitizedHex = hex.trim().removePrefix("#").uppercase()
    val hexValid = sanitizedHex.length == 6
    val canSave = match.trim().isNotEmpty() && hexValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.settings2_categories_add_custom_title else R.string.settings2_categories_edit_custom_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = match,
                    onValueChange = { match = it },
                    label = { Text(stringResource(R.string.settings2_categories_match_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = aerioTextFieldKeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (hexValid) parseHex(sanitizedHex) else androidx.compose.ui.graphics.Color.Gray)
                            .border(
                                1.5.dp,
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp),
                            ),
                    )
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = hex,
                        onValueChange = {
                            hex = it.removePrefix("#").uppercase().filter { c -> c in HEX_CHARS_FOR_CUSTOM }.take(6)
                        },
                        label = { Text(stringResource(R.string.settings2_categories_hex_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = aerioTextFieldKeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii,
                            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            SettingsDialogTextButton(
                label = stringResource(R.string.common_save),
                onClick = { if (canSave) onSave(match.trim(), sanitizedHex) },
                enabled = canSave,
            )
        },
        dismissButton = {
            SettingsDialogTextButton(label = stringResource(R.string.common_cancel), onClick = onDismiss)
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val HEX_CHARS_FOR_CUSTOM: Set<Char> = (('0'..'9') + ('A'..'F')).toSet()
