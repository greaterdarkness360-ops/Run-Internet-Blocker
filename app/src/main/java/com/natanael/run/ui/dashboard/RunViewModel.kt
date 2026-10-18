package com.natanael.run.ui.dashboard

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.natanael.run.service.RunVpnService
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
        val isMasterActive = RunVpnService.isMasterVpnEnabled(application)
        _uiState.update { it.copy(isMasterVpnActive = isMasterActive) }
        loadInstalledApps()
    }

    fun onEvent(event: RunUiEvent) {
        when (event) {
            is RunUiEvent.ToggleMasterVpn -> {
                if (event.enabled) {
                    _uiState.update { it.copy(showVpnPermissionDialog = true) }
                } else {
                    RunVpnService.stop(getApplication())
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
                    RunVpnService.startOrReload(getApplication())
                    _uiState.update {
                        it.copy(
                            isMasterVpnActive = true,
                            showVpnPermissionDialog = false,
                            permissionDeniedMessage = null
                        )
                    }
                } else {
                    RunVpnService.saveMasterVpnState(getApplication(), false)
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
                updateAppBlockState(event.packageName, isWifi = true, blocked = event.blocked)
            }
            is RunUiEvent.ToggleCellularBlock -> {
                updateAppBlockState(event.packageName, isWifi = false, blocked = event.blocked)
            }
            is RunUiEvent.DismissPermissionWarning -> {
                _uiState.update { it.copy(permissionDeniedMessage = null) }
            }
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            val context = getApplication<Application>()
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = try {
                pm.queryIntentActivities(intent, 0)
            } catch (e: Exception) {
                emptyList()
            }

            val wifiBlockedSet = RunVpnService.getWifiBlockedPackages(context)
            val cellularBlockedSet = RunVpnService.getCellularBlockedPackages(context)

            val appList = resolveInfos.mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                if (packageName == context.packageName) return@mapNotNull null
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
                    isWifiBlocked = wifiBlockedSet.contains(packageName),
                    isCellularBlocked = cellularBlockedSet.contains(packageName)
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

    private fun updateAppBlockState(packageName: String, isWifi: Boolean, blocked: Boolean) {
        _uiState.update { currentState ->
            val updatedAll = currentState.allApps.map { app ->
                if (app.packageName == packageName) {
                    val updatedApp = if (isWifi) app.copy(isWifiBlocked = blocked) else app.copy(isCellularBlocked = blocked)
                    RunVpnService.saveRuleChange(
                        getApplication(),
                        packageName = packageName,
                        isWifiBlocked = updatedApp.isWifiBlocked,
                        isCellularBlocked = updatedApp.isCellularBlocked
                    )
                    updatedApp
                } else app
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
