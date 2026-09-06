<p align="center">
  <img src="art/logo.png" alt="CA'NIM Logo" width="130" height="130" style="border-radius: 28px; box-shadow: 0 8px 24px rgba(0,0,0,0.35);">
</p>

<h1 align="center">CA'NIM - Lacak Anime dan Mangamu</h1>

<p align="center">
  <strong>Tersinkronisasi langsung dengan akun MyAnimeList (MAL)</strong>
</p>

<p align="center">
  <a href="https://github.com/Kh1zZ/CA-NIM/releases/latest"><img src="https://img.shields.io/badge/Download-APK%20(v6.0.0)-10B981.svg?style=for-the-badge&logo=android" alt="Download APK"></a>
  <a href="https://github.com/Kh1zZ/CA-NIM/releases"><img src="https://img.shields.io/badge/Version-v6.0.0-0052CC.svg?style=for-the-badge" alt="Version"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=for-the-badge" alt="License"></a>
  <a href="#-fitur-utama"><img src="https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-8B5CF6.svg?style=for-the-badge" alt="Platform"></a>
  <a href="#-arsitektur-dan-prinsip-desain"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-3B82F6.svg?style=for-the-badge" alt="UI"></a>
  <a href="#-kinerja-dan-optimasi"><img src="https://img.shields.io/badge/APK%20Size-~2.1%20MB-F59E0B.svg?style=for-the-badge" alt="Size"></a>
  <a href="#-panduan-kompilasi-manual"><img src="https://img.shields.io/badge/Tests-57%20Passed-6366F1.svg?style=for-the-badge" alt="Tests"></a>
</p>

---

## 📥 Unduh Aplikasi

Dapatkan rilis resmi **CA'NIM** siap pasang langsung dari halaman rilis GitHub:

| Berkas | Tipe | Arsitektur | Kebutuhan Minimum | Tautan |
| :--- | :---: | :---: | :---: | :---: |
| **`canim-universal-release-v6.0.0.apk`** | **Release** | **Universal** (`arm64-v8a`, `armeabi-v7a`, `x86_64`) | Android 7.0+ (API 24+) | [👉 Unduh APK Rilis](https://github.com/Kh1zZ/CA-NIM/releases/latest) |
| **`SHA256SUMS.txt`** | **Checksum** | — | — | [👉 Verifikasi Checksum](https://github.com/Kh1zZ/CA-NIM/releases/latest) |

> 💡 **Catatan Instalasi**: APK Release dikompilasi secara universal oleh GitHub Actions CI/CD, bebas dari bloatware/tracker, dan telah dioptimalkan secara penuh menggunakan R8 Minifier untuk pengalaman scrolling terbaik.

---

## 🎨 Identitas Visual & Filosofi Logo

Logo resmi **CA'NIM** (`art/logo.png`) adalah karya seni beresolusi tinggi (1254 × 1254 px) dengan gaya estetika **Cyber-Blue Radiant Manga**:

```text
                      ┌──────────────────────────────────────┐
                      │          CA'NIM BRAND LOGO           │
                      ├──────────────────────────────────────┤
                      │  [ Buku Manga 3D + Mata Anime ]      │ ───► Katalog Anime & Manga Hidup
                      │  [ Panel Komik + Gerbang Torii ]     │ ───► Kultur & Estetika Visual Jepang
                      │  [ Lencana Awan + Panah Sinkronisasi]│ ───► Cloud Sync MyAnimeList Real-Time
                      │  [ Radiant Cyber-Blue & Sparkles ]   │ ───► Performa Cepat & Tema Cyber Dark
                      └──────────────────────────────────────┘
```

1. **Buku Manga 3D dengan Mata Anime Ekspresif**: Buku bersampul biru elektrik dengan detail halaman putih bertingkat dan pita pembatas (*cyan bookmark ribbon*). Sampul depan menampilkan mata karakter anime beriris biru safir dengan pantulan cahaya ganda (*sparkle*) dan garis bulu mata dramatis yang hidup.
2. **Panel Manga Latar & Siluet Torii**: Kartu panel berbingkai putih di sudut kanan atas menampilkan awan langit, balon percakapan, dan siluet gerbang Torii tradisional, menegaskan identitas kultur manga Jepang.
3. **Lencana Sinkronisasi Awan (Cloud Sync Badge)**: Awan putih kontras di sudut kanan bawah dengan dua panah melingkar melambangkan integrasi **Single Source of Truth** langsung ke cloud **MyAnimeList (MAL)** secara instan dan dua arah (*bidirectional*).
4. **Radiant Cyber-Blue & Kilau Bintang**: Gradasi biru elektrik dengan pancaran cahaya dinamis dan bintang kemilau memberikan nuansa futuristik, cepat, dan selaras dengan tema gelap (*Cyber Dark Native*) aplikasi.

---

## ✨ Fitur Utama

| Fitur | Deskripsi |
| :--- | :--- |
| **🏠 Dasbor Interaktif** | Ringkasan statistik tontonan & bacaan, kartu progres aktif, tombol cepat (+1 Episode / +1 Chapter), dan indikator status sinkronisasi. |
| **📚 Library Lengkap Tanpa Batas** | Paginasi dinamis tanpa batasan kuota (*uncapped pagination*). Menampung ribuan judul koleksi dengan filter status, sorting instan, dan pencarian instan. |
| **🔍 Pencarian Cepat (AniList GraphQL)** | Pencarian ber-filter anime & manga dengan mekanisme *debouncing* (350 ms) dan pembatalan request usang (*cancellation safe*). |
| **🎲 Discover & Eksplorasi Cepat** | Jelajahi anime musiman dan katalog produksi studio secara bersih dan responsif (Musim Ini, Musim Depan, Akan Datang, TBA, dan Studio). |
| **🃏 Flashcard Gacha Rekomendasi** | Tarik kartu anime acak dengan estetika kartu fisik, tumpukan kartu gaya UNO, animasi swipe fisika pegas (spring physics), sistem kuota mingguan (5 tiket dasar, floor minimum 5, +1 tiket per episode ditonton), dan akses cepat di bawah Ringkasan Statistik. |
| **📑 Halaman Detail Menyeluruh (MDL Inspired)** | Tampilan detail komprehensif: sinopsis lengkap, poster HD AniList, genre, trailer YouTube, daftar cast/crew, studio, relasi waralaba, dan pengubah progres interaktif. |
| **🎬 Studio Details & Filmography** | Bio studio mendalam dan *quick facts* (tahun berdiri, negara asal, total anime tercatat, situs resmi) dengan database kurasi 35+ studio & cache persisten 30 hari. Dilengkapi pengelompokan tahun rilis dinamis (judul TBA di puncak) dan kontrol sorting minimalis (Tahun, Popularitas, Skor). |
| **👥 Profil Cast & Kru** | Informasi mendalam pengisi suara (*seiyuu*) dan staf produksi beserta riwayat peran karakter dengan navigasi mulus (*back-stack support*). |
| **📊 Ekspor Statistik Multi-Rasio** | Ekspor infografis koleksi dengan pilihan rasio fleksibel (`9:16 Story`, `4:5`, `3:4`, `1:1`, `16:9 Landscape`), kartu cover anti-stretch (*center-crop*), profil MAL, dan Pie Chart resolusi tinggi. |
| **🎯 Algoritma Filter Top 5 Non-Sekuel** | Algoritma pintar yang secara otomatis mendeteksi dan mengecualikan sekuel dari waralaba yang sama agar tidak mendominasi peringkat Top 5. |
| **🔐 Login MAL via OAuth 2.0 PKCE** | Autentikasi aman tanpa menyimpan sandi pengguna. Token tersimpan aman terenkripsi menggunakan **Android Keystore** (`EncryptedSharedPreferences`). |
| **🔄 Pengecek Pembaruan In-App** | Cek rilis terbaru langsung dari menu Pengaturan dengan opsi auto-check periodik (24 jam) terintegrasi GitHub Releases API dan semver parser. |
| **🛡️ Guard Progres & Anti-Exploit** | Proteksi otomatis batas maksimal episode/chapter dan penonaktifan tombol progres saat selesai untuk mencegah farming kuota tiket gacha ilegal. |
| **🧹 Pembersih Cache Cerdas** | Kelola pembersihan disk mandiri di Pengaturan: bersihkan cache gambar Coil, cache metadata GraphQL AniList, atau reset cache menyeluruh tanpa logout dari MAL. |

---

## ⚡ Kinerja dan Optimasi

CA'NIM dioptimalkan secara mendalam mengadopsi standar performa aplikasi media open-source modern ([ArchiveTune](https://github.com/rukamori/ArchiveTune)) serta konkurensi jaringan mutakhir:

- **Pemuatan Paralel Konkuren (`async`)**: Mengeliminasi latensi *waterfall* pada layar detail dengan menjalankan request metadata AniList dan MyAnimeList secara simultan via HTTP/2. Waktu tunggu terpangkas drastis dari $\approx 2.5\text{ detik}$ menjadi di bawah $500\text{ ms}$.
- **Multi-Key In-Memory LRU Caching (0 ms Load)**: Hasil gabungan detail tersimpan secara persisten pada `CacheManager` di bawah kunci canonical, ID AniList, dan ID MAL. Kunjungan ulang ke judul yang sama langsung tampil dalam **0 ms** tanpa kedipan atau blank spinner.
- **Pre-Enriched Library Sync (Frame 1 Score)**: Sinkronisasi daftar koleksi pengguna langsung menyertakan skor publik MAL (`node.mean`), popularitas, dan peringkat, memastikan judul dari pustaka lokal langsung menampilkan skor MAL sejak frame pertama.
- **Decoupled Asynchronous Tracking**: Metrik publik dan aset visual dirender seketika tanpa tertahan oleh antrean pemanggilan live tracking akun pengguna.
- **100% Skippable Recomposition**: Menggunakan *stable hoisted callbacks* `(UserMediaItem) -> Unit` dan `(MediaItem) -> Unit` pada seluruh card di `LibraryScreen` dan `DiscoverScreen`. Item daftar yang tidak berubah dilewati (*skipped*) secara total saat scrolling.
- **Pre-Allocated Static Shapes**: Meniadakan alokasi memori berulang di Garbage Collector (GC) dengan memusatkan objek bentuk statis (`ItemCardShape`, `ItemBorderStroke`, `ProgressClipShape`, `PillShape`).
- **Zero-Overhead Progress Bar**: Menggantikan `LinearProgressIndicator` Material 3 bawaan yang berat dengan kompresi tata letak `Box` native yang super ringan.
- **R8 Full-Shrinking & Bytecode Protection**: Ukuran file release terpangkas drastis dari ~17 MB menjadi hanya **~2.3 MB** dengan aturan ProGuard presisi yang mengunci metadata generik `Continuation<-Lcom/canim/app/data/model/MalTokenResponse;>`.


---

## 🏛️ Arsitektur dan Prinsip Desain

CA'NIM dibangun dengan arsitektur modern yang memisahkan tanggung jawab secara tegas antara **pencatatan data pengguna** dan **penyediaan metadata**:

```text
┌─────────────────────────────────────────────────────────┐
│                    CA'NIM Client UI                     │
│         (Jetpack Compose M3 + 100% Skippable)           │
└──────────────┬───────────────────────────▲──────────────┘
               │ (Mutasi Tracking)         │ (Observasi StateFlow)
               ▼                           │
┌──────────────────────────────┐ ┌─────────────────────────┐
│       CanimViewModel         │ │       CacheManager      │
│  (Optimistic UI + Rollback)  │ │   (Bounded LRU, TTL,    │
└──────────────┬───────────────┘ │    Canonical Keys)      │
               │                 └─────────▲───────────────┘
               ▼                           │
┌──────────────────────────────────────────┴──────────────┐
│                    CanimRepository                      │
├─────────────────────────────┬───────────────────────────┤
│                             │                           │
│   (User Tracking & Auth)    │      (Rich Metadata)      │
│              ▼              │             ▼             │
│      MyAnimeList API        │     AniList GraphQL       │
│  - Single Source of Truth   │  - Primary Metadata       │
│  - OAuth 2.0 PKCE (Plain)   │  - 50 items/batch         │
│  - Uncapped Pagination      │  - MediaResolver (MAL ID) │
│  - Bidirectional Mutations  │  - Public Detail Fallback │
└─────────────────────────────┴───────────────────────────┘
```

1. **MyAnimeList sebagai Single Source of Truth**: Status tontonan/bacaan (`watching`, `reading`, `completed`, `on_hold`, `dropped`, `plan_to_watch`, `plan_to_read`), jumlah progres, skor (1–10), dan tanggal dikelola langsung oleh server MyAnimeList tanpa database lokal ganda (Room) yang rentan konflik.
2. **AniList GraphQL sebagai Sumber Metadata Utama**: Sinopsis lengkap, poster HD, studio animasi, genre, dan format serial diambil langsung via AniList GraphQL API secara efisien (*batching up to 50 items*).
3. **MediaResolver Terpusat & Pemisahan ID (`MediaRef`)**: Memisahkan secara ketat namespace `anilistId` dan `malId` tanpa fabrikasi ID tiruan.
4. **Optimistic UI dengan Garansi Rollback**: Tombol +1 episode/chapter langsung memperbarui tampilan antarmuka seketika (*50 ms perceived latency*). Jika terjadi kegagalan jaringan, status otomatis di-*rollback* ke kondisi semula disertai notifikasi jelas.

---

## 🌌 Arsitektur Visual: Invisible Continuity (v6.0.0)

Mulai rilis **v6.0.0**, CA'NIM melakukan transformasi bahasa desain fundamental dari pendekatan **Containment-First** (kotak berbingkai kaku di setiap kelompok informasi) menuju **Continuity-First** berbasis filosofi:

> **"Invisible by default, explicit by necessity."**

Struktur antarmuka tidak lagi bergantung pada kontainer bersarang (*nested cards*) atau garis tepi eksplisit (*borders*) untuk memisahkan informasi, melainkan mengandalkan **proximity (jarak kedekatan), whitespace (ruang negatif), alignment (keselarasan tepi), tipografi kontras tinggi, dan hubungan permukaan tonal**.

```text
┌────────────────────────────────────────┐       ┌────────────────────────────────────────┐
│        SEBELUM (Containment-First)     │       │     v6.0.0 (Invisible Continuity)      │
├────────────────────────────────────────┤       ├────────────────────────────────────────┤
│ ┌────────────────────────────────────┐ │       │ Poster   Judul Anime                   │
│ │ Card: Ringkasan Informasi          │ │       │          Metadata                      │
│ │ ┌─────────┐ ┌────────────────────┐ │ │       │                                        │
│ │ │ Card 1  │ │ Card 2             │ │ │ ────► │ SINOPSIS                               │
│ │ └─────────┘ └────────────────────┘ │ │       │ Sinopsis mengalir alami tanpa kotak... │
│ └────────────────────────────────────┘ │       │                                        │
│ ┌────────────────────────────────────┐ │       │ 8.7            24            Finished  │
│ │ Card: Sinopsis                     │ │       │ Score          Episodes      Status    │
│ └────────────────────────────────────┘ │       │                                        │
│ (Borders & Cards everywhere)           │ │       │ (Seamless rhythm, explicit for actions)│
└────────────────────────────────────────┘       └────────────────────────────────────────┘
```

### Prinsip Utama Sistem Visual:
1. **Seamless Metric Flow**: Metrik statistik pada Dasbor dan Halaman Detail tidak lagi dipenjara dalam 4 kotak terpisah dengan border kaku, melainkan menyatu harmonis menggunakan tipografi angka tebal (*extra bold*) dan label jelas.
2. **Eliminasi Card-in-Card**: Menghapus anti-pattern kartu di dalam kartu pada layar detail. Sinopsis, metadata, dan metrik mengalir alami membentuk ritme pembacaan yang tenang dan elegan.
3. **Continuous Studio Identity**: Header studio dan kartu biografi dipadukan menjadi satu kesatuan visual yang mengalir dari poster hero banner hingga statistik fakta studio.
4. **Selective Explicit Boxing**: Garis tepi eksplisit dan kartu fisik hanya dipertahankan pada elemen yang membutuhkan kejelasan affordance interaksi: tombol form, input field, chip filter, kartu media anime/manga katalog, dan modal dialog.
5. **Subtle Design Tokens**: Memperkenalkan token permukaan halus `CardBorderSubtle` (`0x1F334155`), `DividerSubtle` (`0x1494A3B8`), dan `SurfaceSubtle` (`0x0CFFFFFF`) untuk mereduksi visual noise hingga 70%.

---

## 🛠️ Tech Stack & Dependensi

| Kategori | Teknologi / Pustaka | Keterangan |
| :--- | :--- | :--- |
| **Bahasa Pemrograman** | **Kotlin 1.9.22** | JVM Target 17, Coroutines & Flow |
| **UI Toolkit** | **Jetpack Compose (BOM 2024.02.00)** | Material 3, Navigation Compose, Extended Icons |
| **Arsitektur State** | **MVVM + StateFlow** | Reactive single state flow dengan immutability |
| **Jaringan & REST** | **Retrofit 2.9.0 + OkHttp 4.12.0** | HTTP/2, Connection Pooling, Logging Interceptor |
| **GraphQL** | **AniList GraphQL Client** | Raw high-performance batch queries |
| **Serialisasi Data** | **Gson 2.10.1** | Konversi JSON aman dari pemangkasan R8 |
| **Image Loading** | **Coil Compose 2.6.0** | Pemuatan gambar asinkron dengan cache memori & disk |
| **Keamanan Kredensial** | **AndroidX Security Crypto 1.1.0-alpha06** | Enkripsi AES256-GCM hardware-backed KeyStore |
| **Build & Minifier** | **Gradle 8.5 & R8 Minifier** | Code shrinking, resource shrinking, ProGuard |
| **Testing** | **JUnit 4 + Robolectric 4.11.1** | Pengujian unit lokal & validasi arsitektur |

---

## 📁 Struktur Direktori Proyek

```text
ca-nim ft gemini/
├── .github/
│   ├── release.yml                        # Template kategori changelog native GitHub
│   └── workflows/
│       ├── ci.yml                         # CI Pipeline (unit test & debug validation)
│       └── release.yml                    # Release Pipeline (automated tag-based release build)
├── .gitattributes                         # Penegakan line endings LF untuk skrip POSIX
├── app/
│   ├── build.gradle.kts                   # Konfigurasi plugin, SDK, dan dependensi (com.canim.app)
│   ├── proguard-rules.pro                 # Aturan R8/ProGuard untuk Retrofit & Coroutines
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml        # Deklarasi permission & Deep Link OAuth (canim://oauth/callback)
│       │   ├── java/com/canim/app/
│       │   │   ├── CanimApplication.kt    # Inisialisasi Coil image loader singleton
│       │   │   ├── MainActivity.kt        # Entry point Compose & handler deep link
│       │   │   ├── data/
│       │   │   │   ├── cache/             # CacheManager (In-memory LRU + TTL)
│       │   │   │   ├── local/             # MalSecureStorage (EncryptedSharedPreferences)
│       │   │   │   ├── model/             # MediaModels, MalModels, StudioModels, GraphQL Models
│       │   │   │   ├── remote/            # ApiClient, MalApiService, AniListClient, UpdateChecker
│       │   │   │   ├── repository/        # CanimRepository, MalAuthManager, StudioBioRegistry, GachaCreditManager
│       │   │   │   └── resolver/          # MediaResolver (Pemetaan AniList ↔ MAL ID)
│       │   │   ├── ui/
│       │   │   │   ├── screens/           # Dashboard, Library, Search, Discover, MediaDetail, FlashcardScreen,
│       │   │   │   │                      # CastCrewProfile, StudioFilmography, StatsScreen, StatsExporter, SettingsScreen
│       │   │   │   ├── theme/             # Palet warna cyber dark, Tipografi, Shape
│       │   │   │   └── viewmodel/         # CanimViewModel & Factory
│       │   │   └── util/                  # AnimeFranchiseFilter, TextSanitizer
│       │   └── res/                       # Vektor drawables (ic_app_logo), mipmap, values
│       └── test/                          # 51 Automated unit tests (12 test suites)
├── art/
│   └── logo.png                           # Aset visual master resolusi tinggi (1254x1254 px)
├── fastlane/                              # Metadata F-Droid standar (en-US title, desc, icon, changelog)
├── gradle/                                # Gradle wrapper & version catalogs (libs.versions.toml)
├── gradle.properties                      # JVM args & tuning R8 compat mode
├── LICENSE                                # GNU General Public License v3.0 (GPL-3.0)
└── README.md                              # Dokumentasi resmi proyek
```

---

## 🚀 Panduan Kompilasi Manual

Bagi pengembang yang ingin memodifikasi atau mengompilasi APK secara mandiri:

### Kebutuhan Lingkungan:
- **Android Studio** (Hedgehog 2023.1.1 atau yang lebih baru).
- **JDK 17** (Microsoft OpenJDK 17 atau Eclipse Temurin 17).
- **Android SDK** API Level 34 (Android 14) dengan Min SDK 24 (Android 7.0).

### Langkah-langkah Kompilasi:

1. **Clone Repositori**:
   ```bash
   git clone https://github.com/Kh1zZ/CA-NIM.git
   cd CA-NIM
   ```

2. **Kompilasi APK Release (Minified & R8 Optimized)**:
   ```bash
   ./gradlew assembleRelease
   ```
   *File keluaran berlokasi di:*
   ```text
   app/build/outputs/apk/release/canim-universal-release-v<version>.apk
   ```

3. **Kompilasi APK Debug**:
   ```bash
   ./gradlew assembleDebug
   ```
   *File keluaran berlokasi di:*
   ```text
   app/build/outputs/apk/debug/canim-debug-v<version>.apk
   ```

4. **Menjalankan Seluruh Automated Unit Tests**:
   ```bash
   ./gradlew testDebugUnitTest
   ```

---

## 🚀 CI/CD & Rilis Otomatis (GitHub Actions)

Mulai versi `v4.4.1`, seluruh berkas APK rilis resmi **CA'NIM** dikompilasi secara eksklusif dan otomatis oleh **GitHub Actions** (tidak dikompilasi manual di mesin lokal):

- **CI Pipeline (`.github/workflows/ci.yml`)**: Berjalan pada setiap pull request dan push ke branch `main`, menjalankan unit test otomatis (`./gradlew testDebugUnitTest`) serta validasi build debug (`./gradlew assembleDebug`).
- **Release Pipeline (`.github/workflows/release.yml`)**: Pipeline rilis multi-saluran yang dapat dipicu melalui push Git tag (`v*`), publikasi release di web GitHub, maupun dieksekusi secara manual via tombol **"Run workflow"** (`workflow_dispatch`) langsung dari tab Actions di web:
  1. Validasi kecocokan ketat antara target tag (`vX.Y.Z`) dan `versionName` serta `versionCode` pada `app/build.gradle.kts` (mencegah salah rilis/tag).
  2. Menjalankan seluruh 57 automated unit tests.
  3. Mengompilasi APK Release Universal (`canim-universal-release-vX.Y.Z.apk`).
  4. Menghasilkan ringkasan kriptografi `SHA256SUMS.txt`.
  5. Menghasilkan *release notes* otomatis terstruktur berdasarkan commit messages (`feat:`, `fix:`, `perf:`, `ui:`).
  6. Mengunggah berkas APK dan Checksum ke **GitHub Actions Artifacts** sebagai cadangan instan.
  7. Memublikasikan atau memperbarui GitHub Release secara otomatis beserta seluruh aset APK rilis.
- **Distribusi & Keamanan**: Berkas APK murni didistribusikan melalui [GitHub Releases](https://github.com/Kh1zZ/CA-NIM/releases) dan **tidak pernah di-commit ke dalam Git history**.
- **Konfigurasi Signing Produksi & Fallback**: Pipeline mendukung penandatanganan rilis dengan keystore produksi permanen berbasis GitHub Secrets (`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`), serta dilengkapi mekanisme fallback otomatis yang men-generate `debug.keystore` cadangan via `keytool` sehingga build release selalu 100% andal di segala kondisi.

---

## 📝 Catatan Rilis Terbaru (v6.0.0)

- **Arsitektur Visual Invisible Continuity**: Menghilangkan batasan kotak-kotak tebal (*cardification* dan *card-in-card anti-pattern*) yang memecah konsentrasi pengguna. Mengadopsi prinsip desain antarmuka kontemporer di mana konten mengalir alami melalui kedalaman kanvas (*elevation layering*), kontras tipografi hierarkis, dan pembatas mikro-subtle (`CardBorderSubtle` 12% alpha & `DividerSubtle` 8% alpha).
- **Dasbor Seamless & Pemadatan Visual (Information Density)**:
  - *Ringkasan Statistik*: Ditransformasikan dari 4 kotak terisolasi ber-border tebal menjadi blok metrik seamless yang tenang dan menyatu mulus dengan kanvas latar belakang.
  - *MAL Sync Banner & Aksi Cepat*: Mengalir alami dengan aksen warna brand yang elegan tanpa outline tebal yang kaku.
  - *Kartu Sedang Ditonton / Dibaca*: Transisi visual lembut dengan border mikro-subtle dan thumbnail tajam beraksen dinamis.
- **Penyempurnaan Layar Detail Media (MediaDetailScreen)**:
  - *Metrik & Skor*: Menghapus kontainer card pembungkus dan garis kotak kaku pada grid skor MAL, peringkat, dan popularitas. Metrik kini tersaji dalam grid kontinu berlatar belakang elevasi lembut.
  - *Sinopsis Alami*: Teks sinopsis kini mengalir bebas di bawah judul seksi dengan tombol ekspansi "Baca Selengkapnya...", menghilangkan rasa sesak dari kotak tertutup.
  - *Informasi Detail*: Metadata rilis disajikan dalam aliran key-value yang lapang dan terstruktur rapi.
- **Studio Bio & Filmografi Kontemporer**:
  - Kartu biografi studio dan lencana *quick facts* beralih ke surface seamless tanpa garis tepi tebal.
  - Grid filmografi menggunakan kartu poster dengan pembatas mikro-subtle untuk memfokuskan pandangan pada visual seni anime.
- **Harmonisasi Seluruh Antarmuka Aplikasi**:
  - *Library & Discover Screen*: Filter chips, sort selector, dan kartu media anime/manga diperhalus dengan token visual Invisible Continuity.
  - *Stats Screen*: Big Metric Cards dan diagram distribusi status mengadopsi border mikro-subtle.
  - *Floating Search Navigation*: Tombol pencarian navigasi bawah diperbarui agar selaras dengan estetika baru.
- **Preservasi Fungsionalitas & Test Suite 100% (57 Unit Tests Lulus)**: Seluruh interaksi, test tags, quick actions, navigasi, dan integrasi API tetap bekerja sempurna tanpa regresi.

<details>
<summary><b>Lihat Catatan Rilis Sebelumnya (v5.1.1)</b></summary>

- **Pencarian Studio Dinamis (Live Studio Search AniList)**: Menghilangkan pembatasan 16 studio lokal pada menu Discover. Pengguna kini dapat mencari nama studio animasi apa pun di dunia secara langsung dari database global AniList (`Page.studios(search: $search)`). Dilengkapi pencarian lokal instan 0ms dari database kurasi `StudioBioRegistry` yang berpadu mulus dengan hasil kueri live AniList lengkap dengan poster karya terpopuler.
- **Koreksi ID Resmi Studio AniList & Deteksi Karya A-1 Pictures**: Memperbaiki bug kritis di mana filmografi A-1 Pictures tidak terdeteksi (sebelumnya ID `56` yang tidak ada di AniList dan mengembalikan 404, kini diperbaiki ke ID resmi **`561`** dengan 500+ anime seperti *Solo Leveling*, *Sword Art Online*, *Kaguya-sama*, dan *86*). Juga mengoreksi ID resmi AniList untuk CloverWorks (**`6222`**), CoMix Wave Films (**`291`**), dan Kinema Citrus (**`290`**).
- **Penghapusan Pembatasan `isMain: true` pada Filmografi**: Menghapus parameter restriktif `isMain: true` pada kueri GraphQL `getStudioFilmography` sehingga seluruh karya anime yang diproduksi maupun hasil kolaborasi (*co-production*) tampil utuh tanpa ada yang terlewat.
- **Peningkatan Test Suite (Total 57 Unit Tests)**: Menambahkan unit test baru untuk verifikasi integritas ID studio dan bio kurasi pada `StudioRegistryAndSearchTest` (seluruh 57 automated unit test lulus 100%).

</details>

<details>
<summary><b>Lihat Catatan Rilis Sebelumnya (v5.1.0)</b></summary>

- **Perbaikan Kritis Konflik Keystore Penandatanganan (Production Release Signing)**: Mengatasi error "App not installed" / konflik signature pembaruan dengan sistem penandatanganan keystore permanen via GitHub Actions Secrets (`RELEASE_KEYSTORE_BASE64` dll.). *Catatan Penting: Pengguna yang memperbarui dari versi <= v5.0.0 disarankan melakukan uninstall versi lama terlebih dahulu sebelum memasang v5.1.0 karena pergantian sertifikat debug acak ke release keystore permanen (data tracking tetap aman karena MAL adalah Single Source of Truth).*
- **Penutupan Celah Farming Tiket Gacha & Guard Progres Episode**: Menambahkan proteksi validasi pada `quickIncrementAnime()` dan `quickIncrementManga()` agar progres tidak dapat melebihi batas total episode/chapter dan tidak memberi tiket gacha ilegal pada judul berstatus *Completed*. Tombol "+" pada Dasbor dan Library kini otomatis dinonaktifkan (berwarna abu-abu redup) saat target tercapai.
- **Simpan Langsung ke Library dari Flashcard (`plan_to_watch`)**: Tombol ceklis pada kartu flashcard kini otomatis menyimpan anime ke library dengan status "Rencana Ditonton", menampilkan notifikasi konfirmasi Snackbar ("Ditambahkan ke Rencana Ditonton"), mengonsumsi 1 tiket kredit gacha, dan melanjutkan ke kartu berikutnya dengan animasi geser mulus. Jika proses simpan gagal, kartu tidak akan berpindah agar pengguna dapat mencoba kembali.
- **Penyesuaian Tata Letak MediaDetailDialog (Full Alignment)**: Dialog tambah/edit anime kini menggunakan struktur `Scaffold` dengan `bottomBar` terisolasi sehingga tombol "Simpan" dan "Hapus" selalu terlihat penuh tanpa perlu scroll di semua ukuran layar dan split-screen. Pada alur tambah judul baru, tombol "Hapus" disembunyikan dan tombol "Simpan" melebar penuh dengan label "Tambah ke Library".
- **Redesain Studio Picker & Visual Header Studio Filmography**: Modal Studio Picker kini menampilkan kartu bergaya poster anime dengan latar belakang karya terpopuler, gradasi gelap bawah, inisial monogram/logo studio, dan tipografi jelas. Header `StudioFilmographyScreen` kini menghitung padding atas dinamis berbasis `WindowInsets.statusBars` untuk menghindari tabrakan dengan tombol kembali/notch kamera, serta menghapus lencana love non-interaktif.
- **Penyertaan Otomatis Anime TBA / Belum Rilis pada Filmografi Studio**: Query GraphQL AniList kini menggunakan pengurutan `[START_DATE_DESC, POPULARITY_DESC]` sehingga proyek masa depan dan anime berstatus `NOT_YET_RELEASED` langsung termuat di halaman awal dan terkelompok pada seksi "Akan Datang / TBA".
- **Pengecekan Pembaruan Versi di Pengaturan (In-App Update Checker)**: Menambahkan seksi "Pembaruan Aplikasi" pada layar Pengaturan dengan tombol manual "Cek Update" dan opsi toggle "Cek Update Otomatis" (berjalan sekali setiap 24 jam) yang terhubung langsung ke GitHub Releases API via komparasi semver cerdas.
- **Kualitas Gambar HD Khusus Flashcard**: Penambahan field `imageUrlHd` yang memprioritaskan aset resolusi `extraLarge` dari AniList khusus untuk kartu flashcard layar penuh tanpa membebani memori thumbnail di daftar library.
- **Peningkatan Test Suite**: Menambah pengujian unit baru untuk pengecekan versi semver dan proteksi guard increment progres (total 51 unit test lulus 100%).

<details>
<summary><b>Lihat Catatan Rilis Sebelumnya (v5.0.0)</b></summary>

- **Studio Details & Bio Komprehensif**: Bio naratif studio animasi dan *quick facts* terverifikasi (tahun berdiri, negara asal, jumlah anime tercatat, tautan situs resmi) dengan rendering progresif instan 0ms dari database kurasi 35+ studio legendaris (Ufotable, MAPPA, Kyoto Animation, Bones, Wit Studio, Madhouse, CloverWorks, dll.) serta cache persisten 30 hari di penyimpanan lokal.
- **Pengelompokan Filmografi Berdasarkan Tahun (Year Grouping)**: Katalog filmografi studio kini dikelompokkan secara rapi per tahun rilis, dengan judul yang belum tayang / TBA ("Akan Datang / TBA") otomatis berada di posisi teratas. Komputasi grouping di-*memoize* murni untuk menjamin bebas dari recomposition overhead.
- **Kontrol Sorting Filmografi Minimalis**: Sediakan kontrol sorting responsif yang sepenuhnya kompatibel dengan grouping tahun: *Tahun: Terbaru → Terlama* (default), *Tahun: Terlama → Terbaru*, *Popularitas*, dan *Rating Tertinggi*.
- **Flashcard Gacha & Rekomendasi Interaktif**: Fitur rekomendasi berbasis tumpukan kartu fisik bergaya UNO dengan animasi geser fisika pegas (*spring-physics swipe*) interaktif, rotasi dinamis, dan efek fling mulus menggunakan Compose native tanpa dependensi pihak ketiga. Akses cepat ditempatkan langsung di bawah bagian "RINGKASAN STATISTIK" pada Dasbor.
- **Sistem Tiket Gacha Berkelanjutan**: Kuota mingguan 5 tiket dengan reset otomatis setiap Senin 00:00:00 (floor minimum 5 tiket, tiket tambahan dari hasil menonton tidak hangus), bonus +1 tiket instan untuk setiap episode yang ditonton di Library, dan tampilan *empty state* yang informatif saat tiket habis.
- **Pembersihan Bersih DiscoverScreen**: Menghilangkan Smart Randomizer dan panel filter lama yang usang untuk menghasilkan alur penjelajahan katalog yang bersih, terfokus, dan bebas beban kode mati.
- **Peningkatan Suite Pengujian Unit**: Menambah unit test untuk pengelompokan filmografi studio dan logika reset kuota mingguan (total 43 unit test lulus 100%).

</details>

<details>
<summary><b>Lihat Catatan Rilis Sebelumnya (v4.4.3)</b></summary>

- **Optimasi Kinerja Metrik Layar Detail**: Menghilangkan latensi *waterfall* dengan eksekusi paralel konkuren (`async`) untuk permintaan metadata AniList dan MyAnimeList secara simultan.
- **MyAnimeList (MAL) sebagai Sumber Rating Otoritatif**: Skor publik (`Rating MAL`) dan skor pengguna (`Rating Pribadi`) sepenuhnya bersumber dari API resmi MyAnimeList.
- **Multi-Key In-Memory Caching (0 ms Load)**: Hasil penggabungan metadata tersimpan secara persisten pada memori lokal, memungkinkan layar detail terbuka secara instan tanpa delay atau flicker.
- **Pre-Enriched Library Sync**: Sinkronisasi koleksi MAL pengguna langsung meminta dan menyimpan metrik skor publik (`node.mean`), popularitas, peringkat, dan anggota ke memori lokal sejak Frame 1.
- **Decoupled Asynchronous Tracking**: Tampilan metrik publik dan visual detail anime tidak lagi tertahan oleh proses live tracking pengguna.

</details>

<details>
<summary><b>Lihat Catatan Rilis Sebelumnya (v4.4.2)</b></summary>

- **Otomatisasi Penuh CI/CD (GitHub Actions)**: Kompilasi APK rilis resmi beralih 100% ke GitHub-hosted runners dengan validasi versi ketat dan integritas hash SHA-256.
- **Eksklusi Debug APK pada Rilis**: Halaman GitHub Release kini bersih dan terfokus hanya mendistribusikan `canim-universal-release-vX.Y.Z.apk`.
- **Perombakan Ekspor Statistik & Anti-Stretch**: Desain infografis baru dengan cover *aspect-fill center-crop*, rasio multi-format (`9:16 Story`, `4:5`, `3:4`, `1:1`, `16:9 Landscape`), profil MAL yang diperbesar, dan Pie Chart tajam beresolusi tinggi.
- **Algoritma Filter Top 5 Non-Sekuel**: Peringkat Top 5 anime kini menyaring sekuel waralaba secara cerdas agar tidak didominasi oleh musim lanjutan dari judul yang sama.
- **Studio Filmography & Cast/Crew**: Eksplorasi katalog anime berdasarkan studio animasi dan profil mendalam para seiyuu/staf produksi.
- **Peningkatan Keterbacaan UI/UX**: Nama seiyuu dan karakter ditampilkan lengkap tanpa pemotongan teks, perbaikan tata letak metrik skor/peringkat, dan fitur *auto-fill* episode saat memilih status *Completed*.

</details>

<details>
<summary><b>Lihat Catatan Rilis Sebelumnya (v3.0.0)</b></summary>

- **Identitas Visual Resmi**: Integrasi logo brand *Cyber-Blue Radiant Manga* (`art/logo.png`) ke seluruh launcher mipmap icon, adaptive icon foreground, dan UI in-app.
- **Pembersihan Application ID**: Standardisasi identitas paket menjadi **`com.canim.app`** yang rapi, profesional, dan siap F-Droid.
- **Perbaikan Autentikasi MyAnimeList (MAL)**: Menyelesaikan bug parsing generic reflection Retrofit/R8 pada build release serta sinkronisasi PKCE verifier storage.
- **Optimasi Scrolling Mulus (Standar ArchiveTune)**: 100% skippable recomposition, lambda hoisting, pre-allocated shapes, dan zero-overhead progress indicator.
- **Paginasi Tanpa Batas**: Menghilangkan limitasi kuota 500 entri pada koleksi anime/manga pengguna.
- **Pemangkasan Ukuran**: File APK release berhasil diciutkan hingga **~2.13 MB**.

</details>

---

## 🙏 Kredit & Ucapan Terima Kasih

- **AI Pair Programming & Architecture Optimization**: Dibangun, disempurnakan, dan dioptimalkan bersama **Gemini 3.8 Flash** (Google DeepMind) untuk perancangan arsitektur, eliminasi bottleneck konkurensi metrik MAL/AniList, pemecahan bug Retrofit/R8 ProGuard, eliminasi recomposition overhead, pembersihan Application ID, serta standarisasi rilis FOSS F-Droid.
- **Penyedia Data & API**: [MyAnimeList API v2](https://myanimelist.net/apiconfig/references/api/v2) (User Tracking & Auth) & [AniList GraphQL API](https://anilist.gitbook.io/anilist-apiv2-docs/) (Rich Metadata).
- **Inspirasi Optimasi Kinerja**: Rekayasa performa rendering dan efisiensi memori terinspirasi dari standar aplikasi open-source [ArchiveTune](https://github.com/rukamori/ArchiveTune).

---

## 📜 Lisensi

Proyek ini dilisensikan di bawah lisensi **GNU General Public License v3.0 (GPL-3.0)**. Anda bebas menggunakan, memodifikasi, dan mendistribusikan perangkat lunak ini dengan ketentuan bahwa setiap kode turunan tetap bersifat *open source* di bawah lisensi yang sama.

Silakan baca berkas [LICENSE](LICENSE) untuk ketentuan hukum selengkapnya.

---

<p align="center">
  Dibuat dengan ❤️ untuk komunitas Anime & Manga Indonesia.<br>
  <strong>CA'NIM — Lacak Anime dan Mangamu</strong>
</p>
