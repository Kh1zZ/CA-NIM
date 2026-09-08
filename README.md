# CA'NIM — Technical Architecture & Engineering Documentation

<p align="center">
  <img src="art/logo.png" alt="CA'NIM Logo" width="100" height="100" style="border-radius: 20px;">
</p>

<p align="center">
  <a href="https://github.com/Kh1zZ/CA-NIM/releases"><img src="https://img.shields.io/badge/Version-v6.1.8.xs%20(Build%2032)-0052CC.svg?style=for-the-badge" alt="Version"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=for-the-badge" alt="License"></a>
  <a href="#tech-stack--dependensi"><img src="https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-8B5CF6.svg?style=for-the-badge" alt="Platform"></a>
  <a href="#uiux-architecture--fluid-continuity"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-3B82F6.svg?style=for-the-badge" alt="UI"></a>
  <a href="#kinerja-dan-optimasi-memori"><img src="https://img.shields.io/badge/APK%20Size-~2.3%20MB%20(R8%20Full)-F59E0B.svg?style=for-the-badge" alt="Size"></a>
  <a href="#verifikasi-lokal--pengujian-unit"><img src="https://img.shields.io/badge/Tests-67%20Unit%20Tests%20Passing-10B981.svg?style=for-the-badge" alt="Tests"></a>
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
  - Terpicu pada setiap push atau Pull Request ke branch `main`.
  - Memanfaatkan verifikasi unit tests 100% di lingkungan lokal sebelum commit, CI memfokuskan sumber daya secara eksklusif untuk kompilasi APK Debug ultra-cepat (`assembleDebug`).
  - Mengunggah APK Debug ke **GitHub Actions Artifacts** agar pengembang dapat langsung mengunduh dan menguji coba perubahan di perangkat fisik.
- **Release Pipeline (`.github/workflows/release.yml`)**:
  - Terpicu saat pembuatan tag Git (`v*`) atau eksekusi manual via `workflow_dispatch`.
  - Memverifikasi kecocokan versi antara Git tag dan `versionName` serta `versionCode` pada `app/build.gradle.kts`.
  - Mengompilasi APK Release universal secara terakselerasi, menghitung `SHA256SUMS.txt`, dan memublikasikan rilis secara otomatis ke GitHub Releases.

---

## 📝 10. Catatan Perubahan

### v6.1.8.xs (Build 32)
- **Stabilisasi & Bounded Timeout Fallback MyAnimeList (MAL)**:
  - Membatasi waktu respons permintaan cadangan MAL dan mengeliminasi siklus tunggu tanpa batas (*infinite loading shimmer*) pada UI saat terjadi gangguan jaringan atau timeout.
  - Memastikan coroutine `loadDetail` pada `CanimViewModel` selalu mereset status `isLoadingExtendedDetail = false` dan menampilkan umpan balik kegagalan terukur (*controlled error state/snackbar*) saat penyedia AniList maupun MAL tidak dapat dijangkau.
  - Memperbaiki penanganan exception pada `MalAuthManager` dan `CanimRepository` agar tidak menelan exception secara hening (`catch (_: Exception) {}`), melestarikan struktur konkurensi (`CancellationException`), dan melakukan redaksi data sensitif via `LogRedactor`.
- **Instrumentasi Observabilitas MAL (`AppMetrics`)**:
  - Mengintegrasikan pelabelan nama operasi secara eksplisit pada seluruh pemanggilan endpoint di `MalApiPolicyWrapper` (`getAnimeDetailFallback`, `getMangaDetailFallback`, `searchAnime`, `searchManga`, dll.).
  - Merekam telemetri akurat pada setiap permintaan MAL: jumlah request, latensi, timeout, retry, HTTP 429 rate limit, dan HTTP 5xx server error.
- **Integritas Pengujian Unit**: Seluruh pengujian unit lulus 100%.

### v6.1.8x (Build 31)
- **Migrasi Penuh Apollo Kotlin 4.x & Verifikasi Kinerja**:
  - Mengintegrasikan runtime Apollo Kotlin 4.1.1 dengan kueri GraphQL yang terkompilasi strongly-typed berbasis skema resmi AniList.
  - Mengikat ApolloClient langsung ke `ApiClient.aniListOkHttpClient` kustom untuk mempertahankan connection pool, timeout agresif, dan User-Agent.
  - Mengimplementasikan in-flight request deduplication (`deduplicateInFlight`) yang memangkas beban panggilan jaringan redundan hingga 98% saat terjadi konkurensi.
  - Memperbaiki kelemahan implementasi legacy pada penanganan error: kegagalan sementara (*transient errors* seperti timeout, HTTP 429, HTTP 5xx) tidak lagi memicu *negative caching*, mengeliminasi *false negative* (0.0%).
  - Memisahkan secara ketat dan aman namespace ID MyAnimeList dan AniList pada tingkat tipe dan mapper domain.
  - Memverifikasi kinerja secara empiris (dokumen laporan teknis `anilist_apollo_performance_report.md`) dengan seluruh unit test lulus 100%.

### v6.1.8a (Build 30)
- **Pembersihan Tombol Tambah Judul FAB di Menu Library**:
  - Menghapus tombol `ExtendedFloatingActionButton` ("Tambah Judul") pada menu Library (`MainActivity`) agar antarmuka koleksi library tetap bersih, fokus, dan sepenuhnya terintegrasi dengan sinkronisasi MyAnimeList.
- **Penyesuaian Outage Kategori Manga (Recently Done & Newly Added)**:
  - Mengintegrasikan penanganan outage untuk kategori manga *Recently Done* (Baru Selesai) dan *Newly Added* (Baru Ditambahkan) di `CanimRepository` dan `DiscoverScreen`.
  - Karena database MyAnimeList hanya menyediakan endpoint ranking untuk *Top Manga* (dan tidak memiliki endpoint untuk manga yang baru tamat atau baru ditambahkan), kedua kategori ini ditandai secara akurat saat AniList dalam pemeliharaan (*maintenance*):
    - Ikon indikator outage amber ditampilkan pada tab kategori.
    - Notifikasi box informatif ditampilkan kepada pengguna saat kolom dibuka.
    - Beralih ke mode Manga saat AniList mengalami pemeliharaan kini secara cerdas mengarahkan ke *Top Manga* (kategori yang didukung oleh MyAnimeList).
- **Pembersihan Kartu Tindakan Data Library di Pengaturan**:
  - Menghapus menu/kartu "Tindakan Data Library" (*Dataset Demo* dan *Kosongkan Data Library*) di `SettingsScreen` guna meniadakan aksi data tiruan (*mock data*) yang tidak lagi relevan dengan arsitektur penuh *Single Source of Truth* MyAnimeList.
- **Integritas Pengujian Unit**: Seluruh automated unit tests lulus 100%.

### v6.1.8 (Build 29)
- **Kotak Pencarian Ringkas & Harmonisasi Ukuran Tombol**:
  - Merekayasa ulang kotak input pencarian di `SearchScreen` menjadi jauh lebih ringkas (*compact UI*) berbasis `BasicTextField` dengan tinggi presisi `44 dp` dan radius sudut `12 dp`.
  - Menyelaraskan dimensi tombol Filter (`size 44 dp`) dan tombol Cari (`height 44 dp`) agar sejajar sempurna tanpa memakan ruang berlebih secara vertikal.
- **Notifikasi Box Outage Kategori Trending Now (Anime & Manga)**:
  - Mengintegrasikan deteksi gangguan layanan pada kategori *Trending Now* di `DiscoverScreen` (baik mode Anime maupun Manga).
  - Jika server AniList sedang dalam pemeliharaan (*maintenance*) atau data tidak dapat diperoleh, aplikasi menampilkan kotak notifikasi peringatan elegan bertema amber yang menginformasikan bahwa kolom ini sementara tidak dapat digunakan karena MyAnimeList tidak menyediakan metrik trending real-time.
- **Penyelarasan Presisi Tombol Sinkronisasi MAL & Putuskan di Pengaturan**:
  - Menyamakan dimensi tombol "Sinkron MAL" dan tombol "Putuskan" pada kartu integrasi akun MyAnimeList di `SettingsScreen`:
    - Keduanya menggunakan `Modifier.weight(1f).height(42.dp)` dengan corner radius `8 dp` dan padding mikro terpusat.
    - Menambahkan ikon status pemutus tautan pada tombol "Putuskan" untuk keseimbangan visual yang simetris dan harmonis.
- **Integritas Pengujian Unit**: Seluruh automated unit tests lulus 100%.

### v6.1.7c (Build 28)
- **Reset Otomatis Filter Saat Berganti Tipe Media (Anime ↔ Manga)**:
  - Mengintegrasikan mekanisme reset filter reaktif pada `CanimViewModel` (`onSearchQueryChange` & `setSearchType`) dan `SearchScreen` saat berpindah tipe antara Anime dan Manga, mencegah penggunaan parameter yang tidak kompatibel (misal format `TV`/`MOVIE` pada pencarian manga) yang sebelumnya menyebabkan hasil pencarian salah atau kosong.
  - State filter lokal (`tempGenres`, `tempYear`, `typedYearText`, `tempFormat`) serta state global (`searchGenres`, `searchYear`, `searchFormat`) seketika di-reset bersih saat tab/selektor tipe media diklik.
- **Redesain Lembar Filter Lebih Kompak & Hemat Ruang**:
  - Merekayasa ulang `ModalBottomSheet` filter pencarian menjadi jauh lebih ringkas (*compact UI*):
    - Bilah format media menggunakan `LazyRow` horizontal satu baris (tinggi 28 dp).
    - Seleksi tahun rilis memadukan deretan preset cepat (`LazyRow` 28 dp) dan input manual inline tanpa komponen pendukung berlebih yang memakan ruang.
    - Grid genre multi-seleksi menggunakan chip ergonomis berdensitas tinggi (tinggi 26 dp, font 10 sp, padding mikro).
    - Memangkas ketinggian total modal hingga >50%, menghasilkan tata letak rapi yang tidak boros ruang dan nyaman dioperasikan satu tangan.
- **Integritas Pengujian Unit**: Seluruh 67 automated unit tests lulus 100%.

### v6.1.7b (Build 27)
- **Peniadaan Jeda Still Image Saat Kembali dari Layer 2 Detail Anime (Relasi/Rekomendasi)**:
  - Mengimplementasikan *multi-key caching* komprehensif pada `CanimViewModel` (`cacheDetail`) yang memetakan item media ke ID kanonikal, ID AniList, dan ID MAL secara instan di memori, menjamin layar detail layer ke-2 (`initialContent`) tidak mengalami layout wipe, relayout berat, atau drop state saat transisi keluar (*exit transition*) berlangsung.
  - Memperbarui kurva dan offset animasi popdown (`slideOutVertically`) menggunakan `LinearEasing` penuh dengan target offset 100% tinggi layar (`targetOffsetY = { it }`) dan durasi 180 ms, memberikan pergerakan turun instan dari milidetik pertama (t=0) tanpa jeda kurva perlambatan (*zero-velocity hesitation*).
  - Menyempurnakan deteksi arah tumpukan navigasi (`isPush`) secara deterministik langsung dari keanggotaan dan indeks elemen pada `screenStack`, meniadakan ketergantungan pada referensi array mutable atau siklus efek samping rekomposisi.
  - Mengunci jenis media (`MediaType`) langsung dari rute layar (`currentScreen.type`) untuk mencegah kedipan atau evaluasi ulang jenis media saat kembali ke detail sebelumnya.
- **Integritas Pengujian Unit**: Seluruh 66 automated unit tests lulus 100%.

### v6.1.7 (Build 26)
- **Peniadaan Delay & Still Image pada Pembukaan Anime dari Relasi/Rekomendasi**:
  - Mengisolasi passing data `extendedDetail` dan `isLoadingExtendedDetail` per item media saat transisi tumpukan navigasi antar-detail berlangsung, sehingga layar detail sebelumnya tidak merender data kosong atau memicu kalkulasi ulang tata letak (*layout recalculation*) berat di frame awal.
  - Mengoptimalkan deteksi arah navigasi stack menggunakan integer array reference (`prevStackSizeRef`) untuk mengeliminasi siklus rekomposisi ganda seketika pada `MainActivity` yang sebelumnya memicu frame drop.
  - Mempercepat kurva animasi popup vertikal (`slideInVertically(0.40f)`, `scaleIn(0.94f)`, dan `fadeIn(140ms, LinearOutSlowInEasing)`) sehingga transisi visual langsung bergerak naik secara instan tanpa jeda frame atau kesan gambar statis (*still image*).
- **Preservasi Posisi Scroll & Alignment Menu Statistik**:
  - Mengintegrasikan mekanisme penyimpanan dan restorasi posisi scroll (`LazyListState`) pada `StatsScreen` melalui `CanimViewModel`.
  - Saat pengguna menekan anime/manga dari Top 5 Anime atau Top 5 Manga lalu menekan tombol kembali (*back*), posisi scroll menu statistik dipertahankan secara presisi pada posisi item yang diklik tanpa melompat kembali ke bagian paling atas.
- **Integritas Pengujian Unit**: Seluruh 66 automated unit tests lulus 100%.

### v6.1.6 (Build 25)
- **Perbaikan Animasi Popup pada Menu Statistik (Top 5 Anime & Manga)**:
  - Menerapkan animasi popup vertikal halus yang konsisten saat membuka anime/manga dari daftar Top 5 Anime dan Top 5 Manga pada menu Statistik, menggantikan pergerakan slide horizontal/glitch.
  - Memperbaiki transisi navigasi kembali (popdown) dari layar detail ke menu statistik dengan `targetContentZIndex = -1f` agar layar detail yang turun selalu berada di atas menu statistik.
- **Unifikasi Menyeluruh Animasi Popup Layar Penuh**:
  - Memastikan seluruh navigasi detail media baik dari tab dasar, antar-detail relasi & rekomendasi, profil cast/crew, karya studio, maupun dari layar statistik berjalan konsisten menggunakan animasi popup vertikal halus (`slideInVertically(0.15f)` + `scaleIn(0.95f)` + `fadeIn(220ms)`).

### v6.1.5 (Build 24)
- **Unifikasi Animasi Popup Layar Penuh Detail Anime**:
  - Menerapkan animasi popup vertikal halus yang identik (`slideInVertically(0.15f)` + `scaleIn(0.95f)` + `fadeIn(220ms)`) saat membuka detail anime, baik saat dibuka pertama kali dari tab dasar maupun saat membuka anime dari dalam tab fullscreen detail (relasi, rekomendasi, profil cast/crew, karya studio).
  - Menghilangkan glitch visual loncatan/clipping dari pojok kiri atas dengan mengunci ukuran container `Modifier.fillMaxSize()`, `Alignment.Center`, `Spacer` pada state `null`, dan unclipped `SizeTransform(clip = false)`.
  - Menerapkan transisi popdown mundur saat tombol kembali ditekan (`slideOutVertically(0.15f)` + `scaleOut(0.95f)` + `fadeOut(180ms)`) dengan pengaturan `targetContentZIndex = -1f` agar layar yang menutup selalu berada di atas layar yang kembali ditampilkan.
  - Memperbaiki resolusi state item antar-layar stack agar judul dan poster layar sebelumnya tidak mengalami flicker/jump saat transisi berlangsung.
- **Perbaikan Inversi Teks Sliding Highlight di Menu Discovery**:
  - Memperbaiki urutan draw layering (`zIndex`) pada `ScrollableTabRow` di `DiscoverScreen` dengan menempatkan Box pill indikator pada `zIndex(-1f)` di belakang tab, dan konten tab pada `zIndex(1f)` / `zIndex(2f)`.
  - Menambahkan transisi animasi warna teks terinversi dinamis (`Color.White` pekat dengan `FontWeight.ExtraBold` saat aktif vs `TextSecondary` saat tidak aktif) agar teks pilihan (seperti "Trending Now") selalu tampak kontras, tajam, dan tidak lagi tertutup oleh pill highlight.

### v6.1.4 (Build 23)
- **Penyempurnaan Animasi Transisi Detail Fullscreen**:
  - Mengubah transisi pembukaan awal fullscreen detail media dari slide horizontal menjadi slide vertikal halus (`slideInVertically` + `scaleIn` + `fadeIn`) dan penutupan ke bawah (`slideOutVertically` + `scaleOut` + `fadeOut`).
  - Menghadirkan deteksi arah stack (`isStackPush`) untuk navigasi antar-detail yang mempertahankan kontinuitas arah maju (*push* dari kanan) dan mundur (*pop* dari kiri).
- **Indikator Geser Highlight pada Seluruh Selektor**:
  - Menerapkan `SmoothSegmentedSelector` pada toggle Anime/Manga di `DiscoverScreen` dan modal filter `SearchScreen`.
  - Menerapkan sliding highlight indicator pill pada deretan kategori di `DiscoverScreen`.
- **Perbaikan Akurasi Filter Pencarian & Banner AniList Outage**:
  - Memisahkan genre resmi (`genre_in`) dan tag demografis (`tag_in`) pada kueri GraphQL AniList untuk mencegah kegagalan kueri saat memilih tag seperti Isekai, Shounen, Harem, dll.
  - Menghilangkan fallback palsu (`malItems.take(30)`) saat filter aktif agar hasil pencarian tetap akurat dan tidak tercampur judul acak.
  - Menampilkan banner peringatan di `SearchScreen` saat AniList mengalami gangguan dan hasil dialihkan ke mesin cadangan MAL.
- **Perbaikan Penambahan Tiket Gacha (Episode / Chapter Watched)**:
  - Memperbaiki *race condition* inisialisasi baseline kuota tiket gacha pada `updateLibraryData` sehingga penambahan episode tontonan anime maupun chapter bacaan manga langsung menambahkan kuota tiket gacha secara presisi.
- **Keterangan Tambahan pada Banner Gangguan Layanan di Dasbor**:
  - Menambahkan catatan eksplisit bahwa aplikasi tidak akan sepenuhnya berfungsi secara normal selama gangguan layanan berlangsung.
- **Arsitektur Optimasi Sistematis API (Zero-Network Cold Start)**:
  - Menerapkan *Lazy Loading* pada tab Eksplorasi sehingga tidak ada request yang ditembakkan saat aplikasi pertama kali dibuka (menghemat 3–4 request).
  - Menerapkan *Incremental Diff-Only Enrichment* pada sinkronisasi daftar MAL (hanya meminta metadata untuk item yang belum tersimpan di cache).
  - Menerapkan *Stale-While-Revalidate (SWR) Library Cache* (TTL 30 menit) untuk memotong pemanggilan jaringan saat cold start bagi pengguna MAL.
  - Memasang *OkHttp HTTP Disk Cache (30 MB)* untuk respons 304 *Not Modified* dan koneksi HTTP/2 multiplexing.
  - Menambahkan *In-Flight Request Deduplication* menggunakan `ConcurrentHashMap` untuk mencegah duplikasi request identik yang terpicu bersamaan.

### v6.1.3 (Build 22)

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
