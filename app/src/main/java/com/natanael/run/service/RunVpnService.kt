package com.natanael.run.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.natanael.run.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream

/**
 * Engine Backend Utama (@backend-dev): RunVpnService
 *
 * Cara Kerja Isolasi Jaringan Lokal (Sinkhole / Blackhole VPN):
 * 1. Menggunakan VpnService.Builder().addAllowedApplication(packageName) khusus untuk
 *    aplikasi yang sedang diblokir pada jenis jaringan aktif saat ini (Wi-Fi atau Seluler).
 *    Di Android, memanggil addAllowedApplication() membuat HANYA aplikasi dalam daftar tersebut
 *    yang masuk ke antarmuka VPN lokal (tun0), sedangkan aplikasi lain yang diizinkan tetap
 *    menggunakan jalur internet normal dengan kecepatan penuh.
 * 2. Menangkap seluruh trafik IPv4 ("0.0.0.0/0") dan IPv6 ("::/0") beserta DNS dari aplikasi
 *    yang diblokir, lalu membuang (drop) seluruh paket di memori lokal tanpa meneruskannya ke internet.
 * 3. Memantau perpindahan jaringan fisik (Wi-Fi <-> Data Seluler) secara real-time menggunakan
 *    ConnectivityManager.NetworkCallback dan memuat ulang tabel isolasi secara otomatis.
 */
class RunVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vpnInterface: ParcelFileDescriptor? = null
    private var packetDrainJob: Job? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallbackRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            reloadVpnTunnelIfActive()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            reloadVpnTunnelIfActive()
        }

        override fun onLost(network: Network) {
            reloadVpnTunnelIfActive()
        }
    }

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        registerUnderlyingNetworkMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_VPN -> {
                stopVpnIsolation()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_OR_RELOAD_VPN, null -> {
                startForegroundNotification()
                establishSinkholeTunnel()
                return START_STICKY
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        // Dipanggil sistem Android jika pengguna mematikan VPN dari Pengaturan Sistem
        saveMasterVpnState(applicationContext, false)
        stopVpnIsolation()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        unregisterUnderlyingNetworkMonitor()
        stopVpnIsolation()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Membangun ulang antarmuka tun0 setiap kali pengguna mengubah toggle Wi-Fi / Seluler
     * atau saat ponsel berpindah antara Wi-Fi dan Data Seluler.
     */
    @Synchronized
    private fun establishSinkholeTunnel() {
        try {
            // Tutup tunnel lama sebelum membuka konfigurasi baru
            packetDrainJob?.cancel()
            try {
                vpnInterface?.close()
            } catch (_: Exception) {
            }
            vpnInterface = null

            val isWifiActive = isPhysicalWifiConnected()
            val wifiBlockedApps = getWifiBlockedPackages(applicationContext)
            val cellularBlockedApps = getCellularBlockedPackages(applicationContext)

            // Pilih daftar paket yang harus diisolasi berdasarkan jaringan fisik saat ini
            val targetBlockedPackages: Set<String> = if (isWifiActive) {
                wifiBlockedApps
            } else {
                cellularBlockedApps
            }

            val builder = Builder()
                .setSession("RUN Local Network Blocker")
                .setMtu(1500)
                // Alamat lokal sinkhole IPv4 & IPv6
                .addAddress("10.111.222.1", 32)
                .addAddress("fd00:1:fd00:1:fd00:1:fd00:1", 128)
                // Tangkap seluruh rute IPv4 dan IPv6 (krusial untuk WhatsApp / WA Business)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                // Arahkan DNS aplikasi yang diblokir ke sinkhole agar resolusi domain langsung gagal
                .addDnsServer("10.111.222.2")
                .addDnsServer("fd00:1:fd00:1:fd00:1:fd00:2")
                .setBlocking(true)

            if (targetBlockedPackages.isEmpty()) {
                // PENTING: Jika belum ada aplikasi yang diblokir pada jaringan aktif saat ini,
                // masukkan nama paket RUN sendiri agar VpnService tidak memblokir seluruh ponsel.
                try {
                    builder.addAllowedApplication(applicationContext.packageName)
                } catch (_: Exception) {
                }
            } else {
                var addedCount = 0
                targetBlockedPackages.forEach { pkgName ->
                    if (pkgName != applicationContext.packageName) {
                        try {
                            builder.addAllowedApplication(pkgName)
                            addedCount++
                        } catch (_: Exception) {
                            // Abaikan jika paket sudah dihapus dari perangkat
                        }
                    }
                }
                if (addedCount == 0) {
                    builder.addAllowedApplication(applicationContext.packageName)
                }
            }

            vpnInterface = builder.establish()
            saveMasterVpnState(applicationContext, vpnInterface != null)
            updateForegroundNotification(
                blockedCount = targetBlockedPackages.size,
                isWifi = isWifiActive
            )

            // Jalankan coroutine pembuang paket (Sinkhole Drain Loop)
            val descriptor = vpnInterface?.fileDescriptor
            if (descriptor != null) {
                packetDrainJob = serviceScope.launch {
                    val inputStream = FileInputStream(descriptor)
                    val buffer = ByteArray(32767)
                    while (isActive) {
                        try {
                            val length = inputStream.read(buffer)
                            if (length <= 0) break
                            // Seluruh paket dari aplikasi yang diblokir langsung dibuang (dropped)
                        } catch (_: Exception) {
                            break
                        }
                    }
                }
            }
        } catch (_: Exception) {
            saveMasterVpnState(applicationContext, false)
        }
    }

    private fun reloadVpnTunnelIfActive() {
        if (isMasterVpnEnabled(applicationContext)) {
            establishSinkholeTunnel()
        }
    }

    private fun stopVpnIsolation() {
        saveMasterVpnState(applicationContext, false)
        packetDrainJob?.cancel()
        packetDrainJob = null
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    /**
     * Mendeteksi apakah jaringan fisik non-VPN yang aktif saat ini menggunakan Wi-Fi.
     * Jika tidak ada Wi-Fi non-VPN yang aktif, diasumsikan menggunakan Data Seluler.
     */
    private fun isPhysicalWifiConnected(): Boolean {
        val cm = connectivityManager ?: return false
        val allNetworks = cm.allNetworks
        for (network in allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            // Abaikan interface virtual VPN itu sendiri
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            ) {
                return true
            }
        }
        return false
    }

    private fun registerUnderlyingNetworkMonitor() {
        if (networkCallbackRegistered) return
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
            networkCallbackRegistered = true
        } catch (_: Exception) {
        }
    }

    private fun unregisterUnderlyingNetworkMonitor() {
        if (!networkCallbackRegistered) return
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
        networkCallbackRegistered = false
    }

    private fun startForegroundNotification() {
        createNotificationChannel()
        val notification = buildStatusNotification(blockedCount = 0, isWifi = isPhysicalWifiConnected())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateForegroundNotification(blockedCount: Int, isWifi: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildStatusNotification(blockedCount, isWifi))
    }

    private fun buildStatusNotification(blockedCount: Int, isWifi: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val modeText = if (isWifi) "Mode Wi-Fi" else "Mode Seluler"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("RUN Isolasi Jaringan Aktif ($modeText)")
            .setContentText("$blockedCount aplikasi sedang diputus dari akses internet")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RUN VPN Blocker Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menampilkan status isolasi jaringan lokal RUN"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START_OR_RELOAD_VPN = "com.natanael.run.action.START_OR_RELOAD_VPN"
        const val ACTION_STOP_VPN = "com.natanael.run.action.STOP_VPN"

        private const val CHANNEL_ID = "run_vpn_isolation_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS_NAME = "run_network_blocker_prefs"
        private const val KEY_MASTER_ACTIVE = "master_vpn_active"
        private const val KEY_WIFI_BLOCKED_SET = "wifi_blocked_packages"
        private const val KEY_CELLULAR_BLOCKED_SET = "cellular_blocked_packages"

        fun startOrReload(context: Context) {
            val intent = Intent(context, RunVpnService::class.java).apply {
                action = ACTION_START_OR_RELOAD_VPN
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RunVpnService::class.java).apply {
                action = ACTION_STOP_VPN
            }
            context.startService(intent)
        }

        fun saveRuleChange(
            context: Context,
            packageName: String,
            isWifiBlocked: Boolean,
            isCellularBlocked: Boolean
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val wifiSet = prefs.getStringSet(KEY_WIFI_BLOCKED_SET, emptySet())?.toMutableSet() ?: mutableSetOf()
            val cellSet = prefs.getStringSet(KEY_CELLULAR_BLOCKED_SET, emptySet())?.toMutableSet() ?: mutableSetOf()

            if (isWifiBlocked) wifiSet.add(packageName) else wifiSet.remove(packageName)
            if (isCellularBlocked) cellSet.add(packageName) else cellSet.remove(packageName)

            prefs.edit()
                .putStringSet(KEY_WIFI_BLOCKED_SET, wifiSet)
                .putStringSet(KEY_CELLULAR_BLOCKED_SET, cellSet)
                .apply()

            // Jika Master Switch sedang aktif, langsung muat ulang tabel routing tun0
            if (isMasterVpnEnabled(context)) {
                startOrReload(context)
            }
        }

        fun getWifiBlockedPackages(context: Context): Set<String> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getStringSet(KEY_WIFI_BLOCKED_SET, emptySet())?.toSet() ?: emptySet()
        }

        fun getCellularBlockedPackages(context: Context): Set<String> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getStringSet(KEY_CELLULAR_BLOCKED_SET, emptySet())?.toSet() ?: emptySet()
        }

        fun saveMasterVpnState(context: Context, isActive: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_MASTER_ACTIVE, isActive)
                .apply()
        }

        fun isMasterVpnEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_MASTER_ACTIVE, false)
        }
    }
}
