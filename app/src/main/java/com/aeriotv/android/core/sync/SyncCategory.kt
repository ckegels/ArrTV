package com.aeriotv.android.core.sync

import androidx.annotation.StringRes
import com.aeriotv.android.R

/**
 * One category of synced data. iOS mirrors the same taxonomy under iCloud
 * Sync > Sync Categories. Each category persists to its own Drive AppData
 * file ([fileName]) so partial-category sync is atomic.
 */
enum class SyncCategory(
    @StringRes val displayNameRes: Int,
    @StringRes val subtitleRes: Int,
    val fileName: String,
    val storageSuffix: String,
) {
    Playlists(
        displayNameRes = R.string.core_sync_category_playlists,
        subtitleRes = R.string.core_sync_category_playlists_subtitle,
        fileName = "playlists.v1.json",
        storageSuffix = "playlists",
    ),
    WatchProgress(
        displayNameRes = R.string.core_sync_category_watch_progress,
        subtitleRes = R.string.core_sync_category_watch_progress_subtitle,
        fileName = "watch_progress.v1.json",
        storageSuffix = "watch_progress",
    ),
    Reminders(
        displayNameRes = R.string.core_sync_category_reminders,
        subtitleRes = R.string.core_sync_category_reminders_subtitle,
        fileName = "reminders.v1.json",
        storageSuffix = "reminders",
    ),
    Favorites(
        displayNameRes = R.string.core_sync_category_favorites,
        subtitleRes = R.string.core_sync_category_favorites_subtitle,
        fileName = "favorites.v1.json",
        storageSuffix = "favorites",
    ),
    Watchlist(
        displayNameRes = R.string.core_sync_category_watchlist,
        subtitleRes = R.string.core_sync_category_watchlist_subtitle,
        fileName = "watchlist.v1.json",
        storageSuffix = "watchlist",
    ),
    Preferences(
        displayNameRes = R.string.core_sync_category_preferences,
        subtitleRes = R.string.core_sync_category_preferences_subtitle,
        fileName = "preferences.v1.json",
        storageSuffix = "preferences",
    ),
    Credentials(
        displayNameRes = R.string.core_sync_category_credentials,
        subtitleRes = R.string.core_sync_category_credentials_subtitle,
        fileName = "credentials.v1.json",
        storageSuffix = "credentials",
    );

    fun enabledStorageKey(): String = "syncCategory.$storageSuffix"
}
