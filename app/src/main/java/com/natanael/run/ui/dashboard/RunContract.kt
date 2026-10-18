package com.natanael.run.ui.dashboard

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable

@Immutable
data class AppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val isWifiBlocked: Boolean = false,
    val isCellularBlocked: Boolean = false
) {
    val isAnyBlocked: Boolean get() = isWifiBlocked || isCellularBlocked
    val isFullyBlocked: Boolean get() = isWifiBlocked && isCellularBlocked
}

enum class AppFilterStatus(val label: String) {
    ALL("Semua"),
    BLOCKED("Diblokir"),
    ALLOWED("Diizinkan")
}

@Immutable
data class RunUiState(
    val isMasterVpnActive: Boolean = false,
    val showVpnPermissionDialog: Boolean = false,
    val permissionDeniedMessage: String? = null,
    val searchQuery: String = "",
    val selectedFilter: AppFilterStatus = AppFilterStatus.ALL,
    val allApps: List<AppItem> = emptyList(),
    val filteredApps: List<AppItem> = emptyList(),
    val isLoading: Boolean = false
)

sealed interface RunUiEvent {
    data class ToggleMasterVpn(val enabled: Boolean) : RunUiEvent
    data class OnVpnPermissionResult(val granted: Boolean) : RunUiEvent
    data class UpdateSearchQuery(val query: String) : RunUiEvent
    data class SelectFilter(val filter: AppFilterStatus) : RunUiEvent
    data class ToggleWifiBlock(val packageName: String, val blocked: Boolean) : RunUiEvent
    data class ToggleCellularBlock(val packageName: String, val blocked: Boolean) : RunUiEvent
    data object ShowVpnPermissionDialog : RunUiEvent
    data object DismissVpnPermissionDialog : RunUiEvent
    data object DismissPermissionWarning : RunUiEvent
}
