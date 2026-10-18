package com.natanael.run

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.natanael.run.ui.dashboard.RunDashboardScreen
import com.natanael.run.ui.dashboard.RunUiEvent
import com.natanael.run.ui.dashboard.RunViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: RunViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val granted = result.resultCode == Activity.RESULT_OK
        viewModel.onEvent(RunUiEvent.OnVpnPermissionResult(granted))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface {
                    val uiState by viewModel.uiState.collectAsState()

                    RunDashboardScreen(
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        onRequestVpnPermission = { requestVpnPermission() }
                    )
                }
            }
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            viewModel.onEvent(RunUiEvent.OnVpnPermissionResult(true))
        }
    }
}
