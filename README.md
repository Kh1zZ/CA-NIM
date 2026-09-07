# CA'NIM — Technical Architecture & Engineering Documentation

<p align="center">
  <img src="art/logo.png" alt="CA'NIM Logo" width="100" height="100" style="border-radius: 20px;">
</p>

<p align="center">
  <a href="https://github.com/Kh1zZ/CA-NIM/releases"><img src="https://img.shields.io/badge/Version-v6.1.3%20(Build%2022)-0052CC.svg?style=for-the-badge" alt="Version"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=for-the-badge" alt="License"></a>
  <a href="#tech-stack--dependensi"><img src="https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-8B5CF6.svg?style=for-the-badge" alt="Platform"></a>
  <a href="#uiux-architecture--fluid-continuity"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-3B82F6.svg?style=for-the-badge" alt="UI"></a>
  <a href="#kinerja-dan-optimasi-memori"><img src="https://img.shields.io/badge/APK%20Size-~2.3%20MB%20(R8%20Full)-F59E0B.svg?style=for-the-badge" alt="Size"></a>
  <a href="#verifikasi-lokal--pengujian-unit"><img src="https://img.shields.io/badge/Tests-65%20Unit%20Tests%20Passing-10B981.svg?style=for-the-badge" alt="Tests"></a>
</p>

---

Dokumentasi teknis resmi repositori **CA'NIM** (`com.canim.app`). Berkas ini dikhususkan untuk rekayasa perangkat lunak, arsitektur data, strategi konkurensi jaringan, optimasi memori, serta panduan kontributor. Untuk informasi promosi, pengenalan fitur visual, dan tautan unduh publik, silakan kunjungi landing page resmi di repositori [CA-NIM-LP](https://github.com/Kh1zZ/CA-NIM-LP).

---

## 🏛️ 1. Arsitektur Sistem: Dual-Engine Synchronization

CA'NIM menerapkan pemisahan tanggung jawab (*separation of concerns*) yang tegas antara **pencatatan data pengguna** dan **penyediaan metadata publik**:

```text
┌─────────────────────────────────────────────────────────────────┐
│                       CA'NIM Client UI                          │
│        (Jetpack Compose M3 + 100% Skippable Recomposition)      │
└────────────────┬───────────────────────────────▲────────────────┘
                 │ (Mutasi Tracking)             │ (Observasi StateFlow)
                 ▼                               │
┌────────────────────────────────┐ ┌──────────────────────────────┐
│         CanimViewModel         │ │         CacheManager         │
│  (Optimistic UI + Rollback)    │ │   (Bounded LRU, TTL,         │
└────────────────┬───────────────┘ │    Canonical Keys)           │
                 │                 └─────────────▲────────────────┘
                 ▼                               │
┌────────────────────────────────────────────────┴────────────────┐
│                        CanimRepository                          │
│            (Dual-Engine Coordination & Fast Failover)           │
├────────────────────────────────┬────────────────────────────────┤
│                                │                                │
│    [ENGINE A: USER TRACKING]   │     [ENGINE B: RICH METADATA]  │
│               ▼                │                ▼               │
│        MyAnimeList API         │        AniList GraphQL         │
│   - Single Source of Truth     │   - Primary Rich Media Provider│
│   - OAuth 2.0 PKCE (Hardware)  │   - High-throughput Batching   │
│   - Bidirectional Mutations    │   - MediaResolver (MAL ID Map) │
│   - Full Uncapped Pagination   │   - Cast, Crew & Studio Engine │
│   - Score & Status Authority   │   - Multi-Tier Disk/Memory LRU │
└────────────────────────────────┴────────────────────────────────┘
```

### Prinsip Utama Integrasi:
1. **MyAnimeList (MAL) sebagai Single Source of Truth**:
   - Status tracking (`watching`, `reading`, `completed`, `on_hold`, `dropped`, `plan_to_watch`, `plan_to_read`), jumlah episode/chapter, skor (1–10), dan riwayat diperbarui langsung ke server MAL.
   - Tidak menggunakan database lokal ganda (seperti Room) untuk state tontonan guna mencegah *state divergence* dan konflik multi-perangkat.
2. **AniList GraphQL sebagai Penyedia Metadata Utama**:
   - Sinopsis utuh, poster resolusi tinggi (HD/ExtraLarge), banner karya, daftar genre, tanggal rilis, trailer YouTube, daftar karakter & seiyuu, staf produksi, dan filmografi studio diambil melalui AniList GraphQL API.
   - Menggunakan *batching query* (hingga 50 item per kueri) untuk mencegah *request storm* saat sinkronisasi library pengguna.
3. **Dual-Engine Fast Failover & Latency Optimization**:
   - **Happy Eyeballs (RFC 8305)**: Diaktifkan via `fastFallback(true)` pada `OkHttpClient` untuk melakukan balapan koneksi (*connection race*) antara rute IPv4 dan IPv6, mengeliminasi delay 250ms+ saat salah satu rute jaringan seluler/ISP lambat.
   - **Timeout Agresif (6s Connect / 8s Read)**: Menghindari layar tersangkut (spinner macet) jika salah satu API mengalami pemblokiran DNS/gangguan regional. Jika AniList tidak merespons dalam batas waktu, repositori seketika beralih (*seamless failover*) ke fallback MyAnimeList API v2.
   - **Connection Pool Ekstensif**: Dikonfigurasi dengan `ConnectionPool(8, 10, TimeUnit.MINUTES)` untuk mempertahankan soket HTTP/2 tetap aktif, memangkas latensi TLS handshake pada kunjungan berikutnya.
4. **Deteksi Gangguan Real-Time (Outage Detection)**:
   - Status kesehatan API dipantau melalui `pingHealth()` (AniList) dan endpoint ranking (MAL).
   - Indikator status outage dikomunikasikan secara reaktif ke `CanimUiState` (`isAniListDown`, `isMalDown`).
   - Pada Dasbor, bilah notifikasi peringatan hanya ditampilkan saat salah satu atau kedua engine mengalami gangguan, dan otomatis disembunyikan jika kondisi jaringan normal.

---

## 🎨 2. UI/UX Architecture: Invisible Continuity & Fluid Transitions

CA'NIM menerapkan filosofi desain **Invisible Continuity**: antarmuka menyatu alami melalui kedalaman kanvas (*elevation layering*), kontras tipografi hierarkis, dan pembatas mikro-subtle (`CardBorderSubtle` 12% alpha & `DividerSubtle` 8% alpha).

### Fitur Kunci Rekayasa UI:
- **Scroll Position & Alignment Preservation**:
  - `CanimViewModel` memelihara memori status gulir `detailScrollPositions: MutableMap<String, Pair<Int, Int>>` berbasis kunci kanonikal media.
  - Pada `MediaDetailScreen`, posisi item dan offset scroll disimpan secara instan saat disposisi atau navigasi anak (`DisposableEffect`) dan dipulihkan secara instan melalui `rememberLazyListState(initialFirstVisibleItemIndex, initialFirstVisibleItemScrollOffset)`.
  - Transisi antar-halaman detail di `MainActivity` menggunakan `AnimatedContent` dengan arah slide horizontal dinamis, memastikan kembali dari relasi/rekomendasi tidak mereset posisi scroll layar induk.
- **Sliding Highlight Navigation (Spring Physics)**:
  - Bilah navigasi bawah (`main_bottom_nav`) mengimplementasikan kapsul penyorot geser (*sliding highlight pill*) menggunakan `animateDpAsState` dengan spesifikasi pegas `spring(dampingRatio = 0.8f, stiffness = StiffnessMediumLow)`.
  - Komponen generik `SmoothSegmentedSelector` menggantikan sakelar tombol statis pada selektor Anime/Manga (`LibraryScreen`, `SearchScreen`) dan selektor rasio ekspor (`StatsScreen`), memberikan umpan balik visual yang mengalir mulus tanpa *layout shift*.
- **Compact Hero Metrics Strip**:
  - Bilah metrik statistik pada Dasbor dipadatkan secara ergonomis (padding horizontal 12 dp / vertikal 10 dp, tinggi pemisah hairline 22 dp, tipografi proporsional 15 sp) untuk mengoptimalkan *screen real estate* dan visibilitas kartu tontonan aktif tanpa scroll berlebih.
- **Flashcard Gacha Rules & Credit Lifecycle**:
  - Kuota dasar mingguan: 5 tiket (reset otomatis setiap Senin pukul 00:00:00 dengan batas lantai minimum 5).
  - Penghasilan tiket: +1 tiket untuk setiap episode/chapter yang ditandai selesai ditonton di Library.
  - **Konsumsi Tiket**: Pengurangan tiket terjadi **hanya saat kartu dibuang (swipe kiri / tombol silang) atau disimpan (swipe kanan / tombol tambah)**. Membalik kartu (*flip to front*) tidak memotong tiket sehingga saat tersisa 1 tiket pengguna tetap dapat melihat dan mempertimbangkan rekomendasi sebelum bertindak.

---

## ⚡ 3. Kinerja dan Optimasi Memori

1. **Sub-200ms Latency Layar Detail**:
   - *Instant Synthesis (0 ms)*: Saat navigasi dibuka, ViewModel langsung mensintesis data awal dari metadata lokal yang tersedia sehingga poster, skor, format, dan studio tampil seketika.
   - *Disk LRU Cache (< 10 ms)*: Serialisasi detail media tersimpan otomatis pada disk cache lokal.
   - *Two-Phase Progressive Loading*: Data karakter/seiyuu dan staf langsung di-emit pada fase pertama tanpa menunggu kueri sekunder.
2. **100% Skippable Recomposition**:
   - Seluruh callback lambda pada `MainActivity`, `DashboardScreen`, `LibraryScreen`, dan `SearchScreen` diisolasi menggunakan `remember`.
   - Node daftar mengimplementasikan parameter `key` stabil dan `contentType` eksplisit, menjamin frame rate stabil 60–120 FPS saat *fast fling scroll*.
3. **Optimasi Memori Bitmap Coil (RGB_565)**:
   - Mengaktifkan konfigurasi `.allowRgb565(true)` pada Coil `ImageLoader` di `CanimApplication`, memangkas konsumsi RAM decoding poster anime/manga hingga ~50% dan meniadakan jeda Garbage Collection (GC thrashing).
4. **Multi-Key In-Memory LRU Caching**:
   - Hasil kueri tersimpan di `CacheManager` di bawah multi-kunci (ID AniList, ID MAL, dan Canonical Key). Kunjungan ulang ke judul yang sama langsung disajikan dalam **0 ms**.
   - Scheduler di ViewModel secara otomatis membersihkan entri kedaluwarsa setiap 15 menit melalui `CacheManager.pruneExpired()`.
5. **R8 Full-Mode Shrinking**:
   - Konfigurasi `android.enableR8.fullMode=true` dengan aturan ProGuard presisi (`-allowaccessmodification`, `-repackageclasses`, dan peniadaan overhead `ComposerKt.sourceInformation`), menghasilkan ukuran rilis APK ultra-ringkas **~2.3 MB**.

---

## 🔄 4. Aliran Data & Penanganan Pencarian

- **Pencarian Reaktif dengan Filter Tunggal (Filter-Only Search)**:
  - Alur kueri `_searchQueryFlow` (debounce 300 ms) memvalidasi status filter aktif (`searchGenres`, `searchYear`, `searchFormat`).
  - Ketika pengguna menerapkan filter tanpa mengetik teks judul, mesin pencari secara otomatis meminta data berdasarkan popularitas (`POPULARITY_DESC` pada AniList GraphQL, atau `ranking_type = "bypopularity"` dengan kuota 100 entri pada MAL fallback), memastikan hasil selalu relevan dan tidak menghasilkan layar kosong.
- **Optimistic UI dengan Garansi Rollback**:
  - Tombol aksi cepat (+1 Progres) memperbarui status antarmuka seketika (< 50 ms). Jika mutasi jaringan ke server MAL gagal, data otomatis di-*rollback* ke status sebelumnya disertai notifikasi Snackbar.

---

## 🔐 5. Keamanan dan Autentikasi

- **OAuth 2.0 PKCE (Proof Key for Code Exchange)**:
  - Autentikasi MAL berjalan menggunakan alur PKCE tanpa client secret di sisi aplikasi.
  - Penanganan otorisasi melalui Custom URL Scheme (`canim://oauth/callback`).
- **Android Keystore & EncryptedSharedPreferences**:
  - Akses token dan refresh token disimpan di dalam `MalSecureStorage` menggunakan modul `androidx.security.crypto.EncryptedSharedPreferences` dengan algoritma enkripsi hardware-backed `AES256_GCM` (MasterKey).
  - Kredensial sandi pengguna tidak pernah diminta, diproses, maupun disimpan oleh aplikasi.

---

## 🛠️ 6. Tech Stack & Dependensi

| Komponen | Spesifikasi / Pustaka | Peran Teknis |
| :--- | :--- | :--- |
| **Bahasa & JVM** | Kotlin `1.9.22` / Java 17 | Coroutines, StateFlow, FlowPreview |
| **UI Toolkit** | Jetpack Compose BOM `2024.02.00` | Material 3, Animation Core, Foundation |
| **Networking** | OkHttp `4.12.0` + Retrofit `2.9.0` | HTTP/2, ConnectionPool, FastFallback, REST |
| **GraphQL Client** | Raw High-Performance OkHttp Client | Batch GraphQL execution |
| **Serialisasi** | Google Gson `2.10.1` | JSON Parsing aman dari R8 minifier |
| **Image Loading** | Coil Compose `2.6.0` | Disk & RAM caching, RGB_565 decoding |
| **Keamanan** | AndroidX Security Crypto `1.1.0-alpha06` | AES256-GCM Keystore token storage |
| **Build & Packaging**| Android Gradle Plugin `8.5.0` + R8 Full | ProGuard minification & bytecode inlining |
| **Unit Testing** | JUnit 4, Robolectric `4.11.1` | 65 automated regression test suites |

---

## 📁 7. Struktur Direktori Proyek

```text
ca-nim/
├── .github/
│   └── workflows/
│       ├── ci.yml                         # Automated unit test & debug APK artifact build
│       └── release.yml                    # Tag-driven production build, checksum, & release publishing
├── app/
│   ├── build.gradle.kts                   # Konfigurasi dependensi, SDK 34, dan versionCode/Name
│   ├── proguard-rules.pro                 # Aturan R8 full-mode optimization
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml        # Permission, Deep Link OAuth (canim://oauth/callback)
│       │   ├── java/com/canim/app/
│       │   │   ├── CanimApplication.kt    # Inisialisasi Coil ImageLoader singleton (RGB_565)
│       │   │   ├── MainActivity.kt        # Root Compose container, sliding nav bar, overlay transitions
│       │   │   ├── data/
│       │   │   │   ├── cache/             # CacheManager (In-memory bounded LRU + TTL disk cache)
│       │   │   │   ├── local/             # MalSecureStorage (AES256-GCM EncryptedSharedPreferences)
│       │   │   │   ├── model/             # MediaModels, MalModels, StudioModels, GraphQL Schema DTOs
│       │   │   │   ├── remote/            # ApiClient, MalApiService, AniListClient, UpdateChecker
│       │   │   │   ├── repository/        # CanimRepository, MalAuthManager, GachaCreditManager
│       │   │   │   └── resolver/          # MediaResolver (Pemetaan canonical AniList ↔ MAL ID)
│       │   │   ├── ui/
│       │   │   │   ├── components/        # SmoothSegmentedSelector
│       │   │   │   ├── screens/           # DashboardScreen, LibraryScreen, SearchScreen, DiscoverScreen,
│       │   │   │   │                      # MediaDetailScreen, FlashcardScreen, StatsScreen, StatsExporter
│       │   │   │   ├── theme/             # Cyber Dark palette, Typography, Elevation Tokens
│       │   │   │   └── viewmodel/         # CanimViewModel & CanimUiState
│       │   │   └── util/                  # AnimeFranchiseFilter, TextSanitizer
│       │   └── res/                       # Vector drawables (ic_app_logo), mipmaps, values
│       └── test/                          # 65 Automated Unit Tests (DualEngine, Gacha, Navigation, Cache)
├── fastlane/                              # F-Droid standard metadata (title, short/full desc, changelog)
├── gradle.properties                      # JVM args, R8 full mode configuration
├── LICENSE                                # GNU General Public License v3.0 (GPL-3.0)
└── README.md                              # Dokumentasi teknis proyek
```

---

## 🧪 8. Verifikasi Lokal & Kebijakan Kompilasi

### Kebijakan Kompilasi (Zero Local APK Builds):
Sesuai standar integrasi proyek CA'NIM, **tidak diperbolehkan menjalankan `./gradlew assembleDebug` atau `./gradlew assembleRelease` pada komputer lokal**. Seluruh kompilasi berkas biner (APK) dilakukan secara eksklusif oleh GitHub Actions runners di cloud untuk menjamin lingkungan build yang steril, bersih dari artefak lokal, serta terverifikasi secara kriptografis.

### Menjalankan Pengujian Unit Otomatis:
Verifikasi lokal diwajibkan menjalankan suite pengujian unit otomatis sebelum melakukan commit perubahan:

```powershell
# Jalankan seluruh 65 unit tests secara lokal
./gradlew testDebugUnitTest
```

---

## 🚀 9. CI/CD Pipeline & Alur Rilis

- **CI Pipeline (`.github/workflows/ci.yml`)**:
  - Terpicu pada setiap push atau Pull Request ke branch `main`, `debug`, dan `dev`.
  - Menjalankan linting dan unit tests otomatis (`testDebugUnitTest`).
  - Mengompilasi APK Debug dan mengunggahnya ke **GitHub Actions Artifacts** agar pengembang dapat langsung mengunduh dan menguji coba perubahan di perangkat fisik.
- **Release Pipeline (`.github/workflows/release.yml`)**:
  - Terpicu saat pembuatan tag Git (`v*`) atau eksekusi manual via `workflow_dispatch`.
  - Memverifikasi kecocokan versi antara Git tag dan `versionName` serta `versionCode` pada `app/build.gradle.kts`.
  - Menjalankan unit tests, mengompilasi APK Release universal, menghitung `SHA256SUMS.txt`, dan memublikasikan rilis secara otomatis ke GitHub Releases.

---

## 📝 10. Catatan Perubahan: v6.1.3 (Build 22)

- **Flashcard Credit Lifecycle Rule**:
  - Mengubah titik konsumsi kuota tiket gacha dari saat membalik kartu menjadi hanya ketika kartu dibuang (*discard*) atau disimpan (*save*).
  - Pengguna dengan 1 tiket tersisa tetap dapat membalik dan membaca detail kartu secara penuh.
- **Scroll Position & Alignment Preservation**:
  - Mengintegrasikan pelacakan `detailScrollPositions` pada `CanimViewModel` dan restorasi status pada `MediaDetailScreen`.
  - Navigasi bolak-balik dari relasi/rekomendasi kini mempertahankan posisi scroll dan alignment konten sebelumnya secara presisi tanpa reset ke atas.
- **Sliding Highlight Navigation & Selectors**:
  - Menambahkan animasi penyorot geser berbasis pegas fisika (*spring-physics sliding indicator*) pada bilah navigasi bawah (`MainActivity`).
  - Menghadirkan komponen generik `SmoothSegmentedSelector` untuk selektor Anime/Manga di `LibraryScreen` & `SearchScreen`, serta selektor rasio di `StatsScreen`.
- **Real-Time API Outage Detection Banner**:
  - Menambahkan pemantauan kesehatan berkala pada mesin AniList dan MyAnimeList.
  - Menampilkan banner peringatan di Dasbor secara bersyarat ketika salah satu atau kedua API mengalami gangguan, dan menyembunyikannya secara otomatis saat normal.
- **Compact Hero Metrics**:
  - Memadatkan tata letak `dashboard_hero_metrics` pada Dasbor (padding lebih ringkas, hairline separator 22 dp, ukuran font proporsional) untuk meningkatkan densitas informasi.
- **Resilient Filter-Only Search**:
  - Memperbaiki kueri pencarian dengan filter aktif tanpa teks judul agar mengembalikan daftar anime/manga terpopuler (`bypopularity`, limit 100 entri pada fallback).
- **Dual-Engine Latency Optimization**:
  - Mengaktifkan `fastFallback(true)` (Happy Eyeballs RFC 8305) dan memperluas pool koneksi pada `ApiClient.kt`.
  - Menurunkan batas timeout koneksi ke 6 detik dan baca ke 8 detik untuk mempercepat peralihan engine cadangan.
- **Pembaruan Label Rasio Ekspor**:
  - Menyederhanakan label rasio pada `StatsExporter` murni menjadi `"9:16"` dan `"16:9"` tanpa sufiks repetitif.
- **Integritas Pengujian Unit**: Seluruh 65 automated unit tests lulus 100%.

---

## 📜 11. Lisensi

CA'NIM dilisensikan di bawah **GNU General Public License v3.0 (GPL-3.0)**. Setiap kode turunan wajib tetap bersifat sumber terbuka (*open source*) di bawah lisensi yang sama.

Silakan pelajari berkas [LICENSE](LICENSE) untuk informasi lisensi selengkapnya.
