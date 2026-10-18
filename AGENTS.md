AGENTS.md — Pedoman Arsitektur & Spesifikasi Proyek "RUN" untuk Google Jules

Berkas ini dibaca secara otomatis oleh Google Jules (jules.google) untuk memahami struktur proyek, kontrak data, spesifikasi UI/UX Jetpack Compose, serta pipeline CI/CD GitHub Actions yang harus dibangun dari nol.

1. Identitas & Filosofi Produk

Nama Aplikasi: RUN

Atribusi Brand: by Natanael (ditampilkan secara elegan sebagai sub-label di samping judul RUN)

Application ID / Namespace: com.natanael.run

Konsep: Android Local Network Blocker & Digital Disconnection — membantu pengguna mengisolasi aplikasi tertentu dari akses Wi-Fi dan/atau Data Seluler secara lokal menggunakan android.net.VpnService tanpa mengirim data ke server eksternal.

2. Struktur Direktori yang Wajib Dibentuk oleh Jules

Saat membangun atau memperbarui repositori ini dari nol, pastikan seluruh struktur berkas berikut terbentuk secara lengkap dan valid:

.
├── AGENTS.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── .github/
│   └── workflows/
│       └── android-build.yml
└── app/
    ├── build.gradle.kts
    └── src/
        └── main/
            ├── AndroidManifest.xml
            └── java/
                └── com/
                    └── natanael/
                        └── run/
                            ├── MainActivity.kt
                            └── ui/
                                └── dashboard/
                                    ├── RunContract.kt
                                    ├── RunViewModel.kt
                                    └── RunDashboardScreen.kt


3. Kontrak Data & State Management (Frozen Contract)

Seluruh kode pada paket com.natanael.run.ui.dashboard wajib mematuhi kontrak data berikut tanpa mengubah nama properti:

AppItem (@Immutable data class):

packageName: String

appName: String

icon: Drawable? = null

isWifiBlocked: Boolean = false

isCellularBlocked: Boolean = false

Properti turunan: isAnyBlocked: Boolean, isFullyBlocked: Boolean

AppFilterStatus (enum class):

ALL("Semua"), BLOCKED("Diblokir"), ALLOWED("Diizinkan")

RunUiState (@Immutable data class):

Menyimpan isMasterVpnActive, showVpnPermissionDialog, permissionDeniedMessage, searchQuery, selectedFilter, allApps, filteredApps, dan isLoading.

RunUiEvent (sealed interface):

Menerapkan pola MVI (Unidirectional Data Flow) yang diproses melalui RunViewModel.onEvent(event: RunUiEvent) menggunakan MutableStateFlow dan StateFlow.

4. Spesifikasi Modul UI Jetpack Compose (RunDashboardScreen.kt)

TopBarSection:

Menampilkan ikon vektor kustom GlobeNetworkBrandIcon (Canvas bola dunia dengan simpul jaringan dan animasi pulse saat aktif), judul RUN, teks atribusi by Natanael, serta Master Switch Global.

Menampilkan ProtectionStatusBanner yang membedakan secara tegas antara status ISOLASI JARINGAN AKTIF (aksen hijau teal #10B981) dan ENGINE IDLE (STANDBY) (slate).

FilterSection:

Menyediakan OutlinedTextField untuk pencarian instan berdasarkan nama aplikasi atau packageName.

Menampilkan 3 FilterChip (Semua, Diblokir, Diizinkan) beserta penghitung jumlah aplikasi secara real-time.

AppListSection & AppRowItem:

Menggunakan LazyColumn dengan key = { it.packageName } dan contentType = { "app_row_item" } agar bebas jank/stutter.

Setiap baris menampilkan ikon aplikasi (atau avatar inisial jika Drawable bernilai null), appName, packageName berfont monospace, lencana status koneksi, serta 2 sakelar independen (Wi-Fi dan Seluler) dengan ikon vektor kustom.

Penanganan Izin VpnService.prepare():

Memunculkan VpnPermissionConfirmationDialog saat Master Switch dinyalakan pertama kali sebelum memicu ActivityResultContracts.StartActivityForResult().

Jika izin ditolak atau dibatalkan pengguna, mengembalikan Master Switch ke posisi mati (false) secara anggun dan menampilkan PermissionWarningBanner.

5. Konfigurasi Build & GitHub Actions CI/CD

JDK & Kotlin: Gunakan JDK 17 (JavaVersion.VERSION_17), Android Gradle Plugin 8.5.2, Kotlin 1.9.24, compileSdk = 34, minSdk = 26, dan Compose BOM 2024.06.00.

GitHub Actions (.github/workflows/android-build.yml):

Gunakan actions/checkout@v4, actions/setup-java@v4 (Temurin 17), android-actions/setup-android@v3, dan gradle/actions/setup-gradle@v3 (Gradle 8.7) untuk menjalankan gradle assembleDebug --stacktrace tanpa memerlukan berkas binary gradle-wrapper.jar di dalam repositori.

Unggah hasil app/build/outputs/apk/debug/app-debug.apk menggunakan actions/upload-artifact@v4 dengan nama artefak RUN-by-Natanael-debug-apk.
