<p align="center">
  <img src="art/logo.png" alt="CA'NIM Logo" width="130" height="130" style="border-radius: 28px; box-shadow: 0 8px 24px rgba(0,0,0,0.35);">
</p>

<h1 align="center">CA'NIM - Lacak Anime dan Mangamu</h1>

<p align="center">
  <strong>Tersinkronisasi langsung dengan akun MyAnimeList (MAL)</strong>
</p>

<p align="center">
  <a href="https://github.com/Kh1zZ/CA-NIM/releases/latest"><img src="https://img.shields.io/badge/Download-APK%20(v3.0.0)-10B981.svg?style=for-the-badge&logo=android" alt="Download APK"></a>
  <a href="https://github.com/Kh1zZ/CA-NIM/releases"><img src="https://img.shields.io/badge/Version-v3.0.0-0052CC.svg?style=for-the-badge" alt="Version"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=for-the-badge" alt="License"></a>
  <a href="#-fitur-utama"><img src="https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-8B5CF6.svg?style=for-the-badge" alt="Platform"></a>
  <a href="#-arsitektur-dan-prinsip-desain"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-3B82F6.svg?style=for-the-badge" alt="UI"></a>
  <a href="#-kinerja-dan-optimasi"><img src="https://img.shields.io/badge/APK%20Size-~2.13%20MB-F59E0B.svg?style=for-the-badge" alt="Size"></a>
  <a href="#-panduan-kompilasi-manual"><img src="https://img.shields.io/badge/Tests-14%20Passed-6366F1.svg?style=for-the-badge" alt="Tests"></a>
</p>

---

## 📥 Unduh Aplikasi

Dapatkan rilis resmi **CA'NIM** siap pasang langsung dari halaman rilis GitHub:

| Berkas | Tipe | Arsitektur | Kebutuhan Minimum | Tautan |
| :--- | :---: | :---: | :---: | :---: |
| **`canim-universal-release-v4.4.1.apk`** | **Release** | **Universal** (`arm64-v8a`, `armeabi-v7a`, `x86_64`) | Android 7.0+ (API 24+) | [👉 Unduh APK Rilis](https://github.com/Kh1zZ/CA-NIM/releases/latest) |
| **`canim-debug-v4.4.1.apk`** | **Debug** | **Universal** | Android 7.0+ (API 24+) | [👉 Unduh APK Debug](https://github.com/Kh1zZ/CA-NIM/releases/latest) |
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
| **🏠 Dasbor Interaktif** | Ringkasan statistik tontonan & bacaan, kartu progres aktif, tombol cepat (+1 Episode / +1 Chapter), dan indikator status koneksi jaringan. |
| **📚 Library Lengkap Tanpa Batas** | Paginasi dinamis tanpa batasan kuota 500 entri (*uncapped pagination*). Menampung ribuan judul koleksi dengan filter status, sorting instan, dan pencarian instan. |
| **🔍 Pencarian Cepat (AniList GraphQL)** | Pencarian ber-filter anime & manga dengan mekanisme *debouncing* (350 ms) dan pembatalan request usang (*cancellation safe*). |
| **🎲 Discover & Smart Randomizer** | Jelajahi anime populer dan rilis per musim. Fitur *Smart Randomizer* otomatis menyaring judul yang sudah berstatus *Completed*. |
| **📑 Dialog Detail Menyeluruh** | Tampilan sinopsis lengkap, cover HD, tag genre, pengubah progres interaktif, rating skor (1–10), dan tombol sinkronisasi langsung ke MAL. |
| **🔐 Login MAL via OAuth 2.0 PKCE** | Autentikasi aman tanpa menyimpan sandi pengguna. Token tersimpan aman terenkripsi menggunakan **Android Keystore** (`EncryptedSharedPreferences`). |
| **🧹 Pembersih Cache Cerdas** | Kelola pembersihan disk mandiri di menu Pengaturan: bersihkan cache gambar Coil, cache metadata GraphQL AniList, atau reset cache menyeluruh tanpa logout dari MAL. |

---

## ⚡ Kinerja dan Optimasi

CA'NIM v3.0 dioptimalkan secara mendalam mengadopsi standar performa aplikasi media open-source modern ([ArchiveTune](https://github.com/rukamori/ArchiveTune)):

- **100% Skippable Recomposition**: Menggunakan *stable hoisted callbacks* `(UserMediaItem) -> Unit` dan `(MediaItem) -> Unit` pada seluruh card di `LibraryScreen` dan `DiscoverScreen`. Item daftar yang tidak berubah dilewati (*skipped*) secara total saat scrolling.
- **Pre-Allocated Static Shapes**: Meniadakan alokasi memori berulang di Garbage Collector (GC) dengan memusatkan objek bentuk statis (`ItemCardShape`, `ItemBorderStroke`, `ProgressClipShape`, `PillShape`).
- **Zero-Overhead Progress Bar**: Menggantikan `LinearProgressIndicator` Material 3 bawaan yang berat dengan kompresi tata letak `Box` native yang super ringan.
- **R8 Full-Shrinking & Bytecode Protection**: Ukuran file release terpangkas drastis dari ~17 MB menjadi hanya **~2.13 MB** dengan aturan ProGuard presisi yang mengunci metadata generik `Continuation<-Lcom/canim/app/data/model/MalTokenResponse;>`.

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
4. **Optimistic UI dengan Garansi Rollback**: Tombol +1 episode/chapter langsung memperbarui tampilan antarmuka seketika (*0 ms perceived latency*). Jika terjadi kegagalan jaringan, status otomatis di-*rollback* ke kondisi semula disertai notifikasi jelas.

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
ca-nim-opt-v2.1/
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
│       │   │   │   ├── model/             # MediaModels, MalModels, GraphQL Models
│       │   │   │   ├── remote/            # ApiClient, MalApiService, AniListClient
│       │   │   │   ├── repository/        # CanimRepository, MalAuthManager
│       │   │   │   └── resolver/          # MediaResolver (Pemetaan AniList ↔ MAL ID)
│       │   │   └── ui/
│       │   │       ├── screens/           # Dashboard, Library, Search, Discover, Detail, Settings
│       │   │       ├── theme/             # Palet warna cyber dark, Tipografi, Shape
│       │   │       └── viewmodel/         # CanimViewModel & Factory
│       │   └── res/                       # Vektor drawables (ic_app_logo), mipmap, values
│       └── test/                          # 14 Unit test suite
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
- **Release Pipeline (`.github/workflows/release.yml`)**: Terpicu secara otomatis ketika sebuah Git tag rilis dibuat dan di-push (`v*`, contoh: `v4.4.1`, `v4.5.0`):
  1. Validasi kecocokan ketat antara Git tag (`vX.Y.Z`) dan `versionName` serta `versionCode` pada `app/build.gradle.kts` (mencegah salah rilis/tag).
  2. Menjalankan seluruh automated unit tests.
  3. Mengompilasi APK Release Universal (`canim-universal-release-vX.Y.Z.apk`) dan APK Debug (`canim-debug-vX.Y.Z.apk`).
  4. Menghasilkan ringkasan kriptografi `SHA256SUMS.txt`.
  5. Menghasilkan *release notes* otomatis terstruktur berdasarkan commit messages (`feat:`, `fix:`, `perf:`, `ui:`).
  6. Memublikasikan GitHub Release beserta seluruh aset APK.
- **Distribusi & Keamanan**: Berkas APK murni didistribusikan melalui [GitHub Releases](https://github.com/Kh1zZ/CA-NIM/releases) dan **tidak pernah di-commit ke dalam Git history**.
- **Konfigurasi Signing Produksi (Opsional)**: Saat ini release APK menggunakan konfigurasi signing bawaan Android debug key sehingga langsung siap dipasang. Untuk mengonfigurasi keystore rilis mandiri di masa depan, tambahkan GitHub Secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, dan `RELEASE_KEY_PASSWORD`.

---

## 📝 Catatan Rilis (v3.0.0)

- **Identitas Visual Resmi**: Integrasi logo brand *Cyber-Blue Radiant Manga* (`art/logo.png`) ke seluruh launcher mipmap icon, adaptive icon foreground, dan UI in-app.
- **Pembersihan Application ID**: Standardisasi identitas paket menjadi **`com.canim.app`** yang rapi, profesional, dan siap F-Droid.
- **Perbaikan Autentikasi MyAnimeList (MAL)**: Menyelesaikan bug parsing generic reflection Retrofit/R8 pada build release serta sinkronisasi PKCE verifier storage.
- **Optimasi Scrolling Mulus (Standar ArchiveTune)**: 100% skippable recomposition, lambda hoisting, pre-allocated shapes, dan zero-overhead progress indicator.
- **Paginasi Tanpa Batas**: Menghilangkan limitasi kuota 500 entri pada koleksi anime/manga pengguna.
- **Pemangkasan Ukuran**: File APK release berhasil diciutkan hingga **~2.13 MB**.

---

## 🙏 Kredit & Ucapan Terima Kasih

- **AI Pair Programming & Architecture Optimization**: Dibangun, disempurnakan, dan dioptimalkan bersama **Gemini 3.8 Flash** (Google DeepMind) untuk perancangan arsitektur, pemecahan bug Retrofit/R8 ProGuard, eliminasi recomposition overhead, pembersihan Application ID, serta standarisasi rilis FOSS F-Droid.
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
