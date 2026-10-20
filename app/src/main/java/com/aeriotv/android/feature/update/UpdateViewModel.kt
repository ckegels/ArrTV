package com.aeriotv.android.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeriotv.android.core.preferences.AppPreferences
import com.aeriotv.android.core.update.UpdateManager
import com.aeriotv.android.core.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Thin facade over the flavor-bound [UpdateManager] for the update prompt
 * (UpdateGate) and the Settings App Updates screen. In the play flavor the
 * manager is a no-op with isEnabled=false, so every surface hides itself.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val manager: UpdateManager,
    private val preferences: AppPreferences,
) : ViewModel() {

    val isEnabled: Boolean get() = manager.isEnabled
    val state: StateFlow<UpdateState> = manager.state

    /** What's New sequencing: the launch prompt waits until the current
     *  version's notes were seen/seeded so two sheets never stack. */
    val lastSeenWhatsNewVersion: Flow<String> = preferences.lastSeenWhatsNewVersion

    fun autoCheck() = viewModelScope.launch { manager.check(manual = false) }
    /** True while a manual check (Settings button) is talking to GitHub. */
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    /**
     * Check now (the rail's Update, Settings' check button). Somebody asked,
     * so an update found is downloaded and installed at once instead of first
     * asking "Update available?"; up to date or a failed check is a short
     * notice rather than a popup.
     */
    fun manualCheck() {
        if (_checking.value) return
        _checking.value = true
        viewModelScope.launch {
            try {
                manager.check(manual = true)
                when (val after = manager.state.value) {
                    is UpdateState.Available -> manager.startDownload()
                    is UpdateState.UpToDate -> notice("ArrTV is up to date")
                    is UpdateState.Error -> if (after.info == null) notice(after.message)
                    else -> Unit
                }
            } finally {
                _checking.value = false
            }
        }
    }

    private fun notice(text: String) =
        android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
    fun resumePending() = viewModelScope.launch { manager.resumePending() }
    fun download() = manager.startDownload()
    fun install() = manager.install()
    fun later() = manager.skipAvailableVersion()
    fun dismissError() = manager.dismissError()
    fun refreshInstallPermission() = manager.refreshInstallPermission()

    /** Automatic update checks (App Updates screen), on by default. */
    val autoCheck: Flow<Boolean> = preferences.updateAutoCheck
    fun setAutoCheck(value: Boolean) = viewModelScope.launch { preferences.setUpdateAutoCheck(value) }
}
