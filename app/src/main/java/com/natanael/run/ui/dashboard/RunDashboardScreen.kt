package com.natanael.run.ui.dashboard

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

@Composable
fun RunDashboardScreen(
    uiState: RunUiState,
    onEvent: (RunUiEvent) -> Unit,
    onRequestVpnPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopBarSection(
                isMasterActive = uiState.isMasterVpnActive,
                onMasterToggle = { enabled ->
                    onEvent(RunUiEvent.ToggleMasterVpn(enabled))
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            ProtectionStatusBanner(isMasterActive = uiState.isMasterVpnActive)

            uiState.permissionDeniedMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                PermissionWarningBanner(
                    message = message,
                    onDismiss = { onEvent(RunUiEvent.DismissPermissionWarning) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            FilterSection(
                searchQuery = uiState.searchQuery,
                selectedFilter = uiState.selectedFilter,
                allApps = uiState.allApps,
                onSearchChange = { query -> onEvent(RunUiEvent.UpdateSearchQuery(query)) },
                onFilterSelect = { filter -> onEvent(RunUiEvent.SelectFilter(filter)) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                AppListSection(
                    apps = uiState.filteredApps,
                    onToggleWifi = { pkg, blocked -> onEvent(RunUiEvent.ToggleWifiBlock(pkg, blocked)) },
                    onToggleCellular = { pkg, blocked -> onEvent(RunUiEvent.ToggleCellularBlock(pkg, blocked)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (uiState.showVpnPermissionDialog) {
        VpnPermissionConfirmationDialog(
            onConfirm = {
                onEvent(RunUiEvent.DismissVpnPermissionDialog)
                onRequestVpnPermission()
            },
            onDismiss = {
                onEvent(RunUiEvent.DismissVpnPermissionDialog)
            }
        )
    }
}

@Composable
fun TopBarSection(
    isMasterActive: Boolean,
    onMasterToggle: (Boolean) -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlobeNetworkBrandIcon(
                    isActive = isMasterActive,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "RUN",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "by Natanael",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    Text(
                        text = "Local Network Isolation Engine",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = isMasterActive,
                onCheckedChange = onMasterToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF10B981)
                )
            )
        }
    }
}

@Composable
fun GlobeNetworkBrandIcon(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val activeColor = Color(0xFF10B981)
    val idleColor = Color(0xFF64748B)
    val currentColor = if (isActive) activeColor else idleColor

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - 4.dp.toPx()

        if (isActive) {
            drawCircle(
                color = activeColor.copy(alpha = 0.25f),
                radius = radius * pulseScale,
                center = center
            )
        }

        drawCircle(
            color = currentColor,
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )

        // Equator
        drawLine(
            color = currentColor,
            start = Offset(center.x - radius, center.y),
            end = Offset(center.x + radius, center.y),
            strokeWidth = 1.5.dp.toPx()
        )

        // Latitude curves
        drawArc(
            color = currentColor,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius / 2f),
            size = androidx.compose.ui.geometry.Size(radius * 2f, radius),
            style = Stroke(width = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        )

        // Nodes
        val nodes = listOf(
            Offset(center.x - radius * 0.5f, center.y - radius * 0.3f),
            Offset(center.x + radius * 0.5f, center.y - radius * 0.3f),
            Offset(center.x, center.y + radius * 0.5f)
        )

        for (node in nodes) {
            drawCircle(
                color = currentColor,
                radius = 3.dp.toPx(),
                center = node
            )
            drawLine(
                color = currentColor.copy(alpha = 0.7f),
                start = center,
                end = node,
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@Composable
fun ProtectionStatusBanner(isMasterActive: Boolean) {
    val backgroundColor = if (isMasterActive) Color(0xFF10B981) else Color(0xFF475569)
    val statusText = if (isMasterActive) "ISOLASI JARINGAN AKTIF" else "ENGINE IDLE (STANDBY)"
    val statusDesc = if (isMasterActive)
        "Filter lokal VpnService berjalan. Koneksi aplikasi dibatasi sesuai aturan."
    else
        "Sistem isolasi dinonaktifkan. Seluruh aplikasi memiliki akses internet penuh."

    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = statusText,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = statusDesc,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun PermissionWarningBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Tutup",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun FilterSection(
    searchQuery: String,
    selectedFilter: AppFilterStatus,
    allApps: List<AppItem>,
    onSearchChange: (String) -> Unit,
    onFilterSelect: (AppFilterStatus) -> Unit
) {
    val totalCount = allApps.size
    val blockedCount = remember(allApps) { allApps.count { it.isAnyBlocked } }
    val allowedCount = remember(allApps) { allApps.count { !it.isAnyBlocked } }

    Column {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Cari aplikasi atau package...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppFilterStatus.entries.forEach { filter ->
                val count = when (filter) {
                    AppFilterStatus.ALL -> totalCount
                    AppFilterStatus.BLOCKED -> blockedCount
                    AppFilterStatus.ALLOWED -> allowedCount
                }
                val isSelected = selectedFilter == filter

                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterSelect(filter) },
                    label = { Text("${filter.label} ($count)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}

@Composable
fun AppListSection(
    apps: List<AppItem>,
    onToggleWifi: (String, Boolean) -> Unit,
    onToggleCellular: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (apps.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Tidak ada aplikasi ditemukan.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(
                items = apps,
                key = { it.packageName },
                contentType = { "app_row_item" }
            ) { app ->
                AppRowItem(
                    app = app,
                    onToggleWifi = { blocked -> onToggleWifi(app.packageName, blocked) },
                    onToggleCellular = { blocked -> onToggleCellular(app.packageName, blocked) }
                )
            }
        }
    }
}

@Composable
fun AppRowItem(
    app: AppItem,
    onToggleWifi: (Boolean) -> Unit,
    onToggleCellular: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconImage(icon = app.icon, appName = app.appName)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                ConnectionBadge(
                    isWifiBlocked = app.isWifiBlocked,
                    isCellularBlocked = app.isCellularBlocked
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Wi-Fi Toggle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (app.isWifiBlocked) Icons.Outlined.Wifi else Icons.Default.Wifi,
                    contentDescription = "Wi-Fi",
                    tint = if (app.isWifiBlocked) Color.Red else Color(0xFF10B981),
                    modifier = Modifier.size(18.dp)
                )
                Switch(
                    checked = app.isWifiBlocked,
                    onCheckedChange = onToggleWifi,
                    modifier = Modifier.height(30.dp),
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Color.Red.copy(alpha = 0.8f)
                    )
                )
                Text(
                    text = if (app.isWifiBlocked) "Wi-Fi Blok" else "Wi-Fi Aktif",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Cellular Toggle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.CellTower,
                    contentDescription = "Seluler",
                    tint = if (app.isCellularBlocked) Color.Red else Color(0xFF10B981),
                    modifier = Modifier.size(18.dp)
                )
                Switch(
                    checked = app.isCellularBlocked,
                    onCheckedChange = onToggleCellular,
                    modifier = Modifier.height(30.dp),
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Color.Red.copy(alpha = 0.8f)
                    )
                )
                Text(
                    text = if (app.isCellularBlocked) "Seluler Blok" else "Seluler Aktif",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AppIconImage(
    icon: Drawable?,
    appName: String
) {
    if (icon != null) {
        val bitmap = remember(icon) {
            try {
                icon.toBitmap(width = 48, height = 48).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = appName,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
            return
        }
    }

    // Fallback Avatar with initial
    val initial = appName.firstOrNull()?.uppercaseChar() ?: 'A'
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial.toString(),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = 18.sp
        )
    }
}

@Composable
fun ConnectionBadge(
    isWifiBlocked: Boolean,
    isCellularBlocked: Boolean
) {
    val (statusText, color) = when {
        isWifiBlocked && isCellularBlocked -> "TERISOLASI TOTAL" to Color.Red
        isWifiBlocked -> "WIFI DIBLOKIR" to Color(0xFFF59E0B)
        isCellularBlocked -> "SELULER DIBLOKIR" to Color(0xFFF59E0B)
        else -> "TERHUBUNG" to Color(0xFF10B981)
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = statusText,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun VpnPermissionConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Aktifkan Isolasi Jaringan RUN")
        },
        text = {
            Text(
                text = "Aplikasi RUN memerlukan izin VpnService untuk menyaring lalu lintas data secara lokal di perangkat Anda tanpa mengirimkan data ke server luar.\n\nApakah Anda ingin melanjutkan?"
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Izinkan & Aktifkan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
