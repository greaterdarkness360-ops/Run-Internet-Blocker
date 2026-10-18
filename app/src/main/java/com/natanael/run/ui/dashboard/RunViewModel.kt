package com.natanael.run.ui.dashboard

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RunViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RunUiState())
    val uiState: StateFlow<RunUiState> = _uiState.asStateFlow()

    init {
        loadInstalledApps()
    }

    fun onEvent(event: RunUiEvent) {
        when (event) {
            is RunUiEvent.ToggleMasterVpn -> {
                if (event.enabled) {
                    _uiState.update { it.copy(showVpnPermissionDialog = true) }
                } else {
                    _uiState.update {
                        it.copy(
                            isMasterVpnActive = false,
                            showVpnPermissionDialog = false,
                            permissionDeniedMessage = null
                        )
                    }
                }
            }
            is RunUiEvent.ShowVpnPermissionDialog -> {
                _uiState.update { it.copy(showVpnPermissionDialog = true) }
            }
            is RunUiEvent.DismissVpnPermissionDialog -> {
                _uiState.update { it.copy(showVpnPermissionDialog = false) }
            }
            is RunUiEvent.OnVpnPermissionResult -> {
                if (event.granted) {
                    _uiState.update {
                        it.copy(
                            isMasterVpnActive = true,
                            showVpnPermissionDialog = false,
                            permissionDeniedMessage = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isMasterVpnActive = false,
                            showVpnPermissionDialog = false,
                            permissionDeniedMessage = "Izin VpnService ditolak. Master Switch dikembalikan ke posisi mati."
                        )
                    }
                }
            }
            is RunUiEvent.UpdateSearchQuery -> {
                _uiState.update { currentState ->
                    val updatedState = currentState.copy(searchQuery = event.query)
                    updatedState.copy(filteredApps = applyFilters(updatedState.allApps, updatedState.searchQuery, updatedState.selectedFilter))
                }
            }
            is RunUiEvent.SelectFilter -> {
                _uiState.update { currentState ->
                    val updatedState = currentState.copy(selectedFilter = event.filter)
                    updatedState.copy(filteredApps = applyFilters(updatedState.allApps, updatedState.searchQuery, updatedState.selectedFilter))
                }
            }
            is RunUiEvent.ToggleWifiBlock -> {
                updateAppBlockState(event.packageName) { app ->
                    app.copy(isWifiBlocked = event.blocked)
                }
            }
            is RunUiEvent.ToggleCellularBlock -> {
                updateAppBlockState(event.packageName) { app ->
                    app.copy(isCellularBlocked = event.blocked)
                }
            }
            is RunUiEvent.DismissPermissionWarning -> {
                _uiState.update { it.copy(permissionDeniedMessage = null) }
            }
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            val pm = getApplication<Application>().packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = try {
                pm.queryIntentActivities(intent, 0)
            } catch (e: Exception) {
                emptyList()
            }

            val appList = resolveInfos.mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                if (packageName == getApplication<Application>().packageName) return@mapNotNull null
                val appName = resolveInfo.loadLabel(pm).toString()
                val icon = try {
                    resolveInfo.loadIcon(pm)
                } catch (e: Exception) {
                    null
                }
                AppItem(
                    packageName = packageName,
                    appName = appName,
                    icon = icon,
                    isWifiBlocked = false,
                    isCellularBlocked = false
                )
            }.distinctBy { it.packageName }.sortedBy { it.appName.lowercase() }

            _uiState.update { currentState ->
                currentState.copy(
                    isLoading = false,
                    allApps = appList,
                    filteredApps = applyFilters(appList, currentState.searchQuery, currentState.selectedFilter)
                )
            }
        }
    }

    private fun updateAppBlockState(packageName: String, transform: (AppItem) -> AppItem) {
        _uiState.update { currentState ->
            val updatedAll = currentState.allApps.map { app ->
                if (app.packageName == packageName) transform(app) else app
            }
            currentState.copy(
                allApps = updatedAll,
                filteredApps = applyFilters(updatedAll, currentState.searchQuery, currentState.selectedFilter)
            )
        }
    }

    private fun applyFilters(
        apps: List<AppItem>,
        query: String,
        filter: AppFilterStatus
    ): List<AppItem> {
        return apps.filter { app ->
            val matchesQuery = query.isEmpty() ||
                    app.appName.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)

            val matchesFilter = when (filter) {
                AppFilterStatus.ALL -> true
                AppFilterStatus.BLOCKED -> app.isAnyBlocked
                AppFilterStatus.ALLOWED -> !app.isAnyBlocked
            }

            matchesQuery && matchesFilter
        }
    }
}
