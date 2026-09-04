<p align="center">
  <img src="art/logo.png" alt="CA'NIM Logo" width="130" height="130" style="border-radius: 28px; box-shadow: 0 8px 24px rgba(0,0,0,0.35);">
</p>

<h1 align="center">CA'NIM - Lacak Anime dan Mangamu</h1>

<p align="center">
  <strong>Tersinkronisasi langsung dengan akun MAL</strong>
</p>

<p align="center">
  <a href="https://github.com/Kh1zZ/CA-NIM/releases/latest"><img src="https://img.shields.io/badge/Download-APK%20(v3.0.0)-10B981.svg?style=for-the-badge&logo=android" alt="Download APK"></a>
  <a href="#-tech-stack--dependensi"><img src="https://img.shields.io/badge/Version-v3.0.0-0052CC.svg?style=for-the-badge" alt="Version"></a>
  <a href="#-arsitektur-dan-prinsip-desain"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-3B82F6.svg?style=for-the-badge" alt="UI"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=for-the-badge" alt="License"></a>
  <a href="#-kinerja-dan-optimasi-standar-archivetune"><img src="https://img.shields.io/badge/APK%20Size-~2.13%20MB-F59E0B.svg?style=for-the-badge" alt="Size"></a>
  <a href="#-panduan-unduh--kompilasi-aplikasi"><img src="https://img.shields.io/badge/Tests-14%20Passed-6366F1.svg?style=for-the-badge" alt="Tests"></a>
</p>

---

## 🎨 Identitas Visual & Filosofi Logo Branding

Logo resmi **CA'NIM** (`art/logo.png`) merupakan identitas visual terpadu yang diterapkan di seluruh struktur aplikasi:

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

1. **Buku Manga 3D dengan Mata Anime Ekspresif**: Buku bersampul biru elektrik dengan detail halaman putih berlapis dan pita pembatas buku (*cyan bookmark ribbon*). Sampul depan dihiasi mata karakter anime yang digambar dengan presisi—iris biru safir bergradasi dengan pantulan cahaya ganda (*sparkle*) dan garis bulu mata dramatis, mencerminkan estetika otentik dunia anime.
2. **Panel Manga Latar & Siluet Torii**: Kartu panel berbingkai putih di sudut kanan atas menampilkan awan langit, balon percakapan, dan siluet gerbang Torii tradisional, menegaskan keterikatan kuat dengan kultur manga Jepang.
3. **Lencana Sinkronisasi Awan (Cloud Sync Badge)**: Awan putih kontras di sudut kanan bawah dengan dua panah melingkar melambangkan integrasi **Single Source of Truth** langsung ke cloud **MyAnimeList (MAL)** secara instan dan dua arah (*bidirectional*).
4. **Radiant Cyber-Blue & Kilau Bintang**: Palet warna biru elektrik bergradasi diagonal dengan pancaran cahaya dinamis dan bintang kemilau (*star sparkles*) memberikan kesan futuristik, responsif, dan selaras dengan tema gelap (*Cyber Dark Native*) aplikasi.

> **Penerapan Branding Menyeluruh**:
> Master logo (`art/logo.png`) telah diadaptasi ke seluruh density Android Launcher Icons (`mipmap-mdpi` hingga `mipmap-xxxhdpi` untuk ikon standar & *circular round*), Adaptive Icon Foreground (`ic_launcher_foreground.xml`), serta aset in-app (`drawable-nodpi/ic_app_logo.png`) pada Top Bar Dasbor dan kartu informasi Pengaturan.

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

### 1. MyAnimeList sebagai Single Source of Truth
- Status judul (`watching`, `reading`, `completed`, `on_hold`, `dropped`, `plan_to_watch`, `plan_to_read`), riwayat episode/chapter/volume, skor (1–10), tanggal, catatan, dan rewatch sepenuhnya disimpan dan dikelola langsung oleh server MyAnimeList.
- Tidak ada database lokal ganda (seperti Room) yang berisiko mengalami konflik data atau desinkronisasi.

### 2. AniList GraphQL sebagai Sumber Metadata Utama
- Metadata seperti sinopsis lengkap, poster beresolusi tinggi, tahun rilis, musim, studio animasi, genre, dan format serial diambil langsung melalui AniList GraphQL API secara efisien (*batching up to 50 items*).

### 3. MediaResolver Terpusat & Pemisahan ID (`MediaRef`)
- Memisahkan secara ketat namespace **AniList ID** dan **MAL ID** (`anilistId != malId`).
- Resolusi ID dikelola terpusat oleh `MediaResolver` tanpa fabrikasi ID tiruan (*never fabricate IDs*).

### 4. Optimistic UI dengan Garansi Konsistensi
- Aksi penambahan episode (+1), chapter (+1), atau perubahan status langsung diperbarui di layar seketika (*0 ms latency perceived*).
- Permintaan jaringan dikirim ke MAL di latar belakang. Jika terjadi kegagalan jaringan atau sesi kedaluwarsa, UI otomatis di-*rollback* ke status awal disertai pesan galat yang informatif.

---

## ⚡ Kinerja dan Optimasi

CA'NIM v3.0 telah dioptimalkan secara mendalam untuk mencapai pengalaman scrolling ultra-mulus setara aplikasi media kelas atas:

- **100% Skippable Recomposition**: Menggunakan *stable hoisted callbacks* `(UserMediaItem) -> Unit` dan `(MediaItem) -> Unit` pada seluruh card di `LibraryScreen` dan `DiscoverScreen`. Ketika pengguna men-scroll daftar ratusan item, item yang tidak berubah tidak akan pernah direkomposisi ulang.
- **Pre-Allocated Static Shapes**: Menghilangkan alokasi objek GC (*Garbage Collection*) saat scrolling dengan memusatkan bentuk statis (`ItemCardShape`, `ItemBorderStroke`, `ProgressClipShape`, `PillShape`).
- **Zero-Overhead Progress Bar**: Menggantikan `LinearProgressIndicator` Material 3 bawaan yang berat dengan kompresi tata letak `Box` native yang ringan.
- **R8 Full-Shrinking & Bytecode Protection**: Ukuran file release terpangkas drastis dari ~17 MB menjadi hanya **~2.08 MB** dengan aturan ProGuard presisi yang menjaga keutuhan `Continuation` dan generic parameter Retrofit.

---

## ✨ Fitur Utama

| Fitur | Deskripsi |
| :--- | :--- |
| **🏠 Dasbor Interaktif** | Ringkasan statistik total tontonan, progres episode/chapter, widget akses cepat (+1 Ep), dan indikator status koneksi (*Online/Offline*). |
| **📚 Library Lengkap Tanpa Batas** | Paginasi dinamis tanpa batasan 500 entri (*uncapped pagination*). Menampung ribuan judul koleksi dengan filter status, sorting instan, dan pencarian cepat. |
| **🔍 Pencarian Cepat (AniList GraphQL)** | Pencarian ber-filter anime & manga dengan mekanisme *debouncing* (350 ms) dan pembatalan request usang (*cancellation safe*). |
| **🎲 Discover & Smart Randomizer** | Telusuri anime populer dan trending per musim, dilengkapi fitur pengacak (*Randomizer*) yang secara ketat menyaring judul yang sudah berstatus *Completed*. |
| **📑 Dialog Detail Menyeluruh** | Tampilan sinopsis lengkap, cover HD, genre tags, pengubah progres interaktif, penilaian skor, dan tombol sinkronisasi MAL. |
| **🔐 Login MAL via OAuth 2.0 PKCE** | Autentikasi aman tanpa menyimpan kata sandi pengguna. Token tersimpan terenkripsi menggunakan **Android Keystore** (`EncryptedSharedPreferences`). |
| **🧹 Pembersih Cache Cerdas** | Manajemen cache mandiri di menu Pengaturan: bersihkan cache gambar Coil, cache metadata AniList, atau reset cache secara menyeluruh tanpa menghapus akun MAL. |

---

## 🛠️ Tech Stack & Dependensi

CA'NIM dibangun sepenuhnya menggunakan pustaka Android modern berstandar industri:

| Kategori | Teknologi / Pustaka | Keterangan |
| :--- | :--- | :--- |
| **Bahasa Pemrograman** | **Kotlin 1.9.22** | JVM Target 17, Coroutines & Flow |
| **UI Toolkit** | **Jetpack Compose (BOM 2024.02.00)** | Material 3, Navigation Compose, Extended Icons |
| **Arsitektur State** | **MVVM + StateFlow** | Reactive single state flow dengan immutability |
| **Jaringan & REST** | **Retrofit 2.9.0 + OkHttp 4.12.0** | HTTP/2, Connection Pooling, Logging Interceptor |
| **GraphQL** | **AniList GraphQL Client** | Raw high-performance batch queries |
| **Serialisasi Data** | **Gson 2.10.1** | Konversi JSON yang aman dari pemangkasan R8 |
| **Image Loading** | **Coil Compose 2.5.0** | Pemuatan gambar asinkron dengan cache memori & disk |
| **Keamanan Kredensial** | **AndroidX Security Crypto 1.1.0-alpha06** | Enkripsi AES256-GCM hardware-backed KeyStore |
| **Build & Minifier** | **Gradle 8.5 & R8 Minifier** | Code shrinking, resource shrinking, ProGuard |
| **Testing** | **JUnit 4 + Robolectric 4.11.1** | Pengujian unit lokal & validasi arsitektur |

---

## 📁 Struktur Direktori Proyek

```text
ca-nim-opt-v2.1/
├── app/
│   ├── build.gradle.kts                   # Konfigurasi plugin, SDK, dan dependensi
│   ├── proguard-rules.pro                 # Aturan R8/ProGuard untuk Retrofit & Coroutines
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml        # Deklarasi permission & Deep Link OAuth
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
│       │   └── res/                       # Vektor drawables (ic_app_icon), mipmap, values
│       └── test/                          # 14 Unit test suite
├── art/
│   └── logo.png                           # Aset visual resolusi tinggi untuk dokumentasi
├── gradle/                                # Gradle wrapper & version catalogs (libs.versions.toml)
├── gradle.properties                      # JVM args & tuning R8 compat mode
└── README.md                              # Dokumentasi resmi proyek
```

---

## 🚀 Panduan Unduh & Kompilasi Aplikasi

Anda dapat langsung mengunduh paket APK siap pakai dari halaman rilis GitHub resmi atau melakukan kompilasi manual sendiri dari kode sumber.

### 📥 1. Unduh Langsung via GitHub Releases (Direkomendasikan)
Cara tercepat untuk langsung menginstal dan menggunakan CA'NIM di perangkat Android tanpa perlu alat pengembang:

- **Halaman Rilis Resmi**: 👉 [GitHub Releases — CA-NIM](https://github.com/Kh1zZ/CA-NIM/releases)
- **Unduh Rilis Terkini (v3.0.0)**: 👉 [Download app-release.apk (v3.0.0)](https://github.com/Kh1zZ/CA-NIM/releases/latest)
  - **Ukuran File**: **~2.13 MB**
  - **Arsitektur**: **Universal** (kompatibel penuh dengan `arm64-v8a`, `armeabi-v7a`, `x86_64`, dan `x86`)
  - **Kebutuhan Sistem**: Android 7.0 Nougat (API 24) atau yang lebih baru

---

### 🛠️ 2. Kompilasi Manual dari Kode Sumber (Manual Compile)

Bagi pengembang yang ingin memodifikasi atau mengompilasi APK sendiri:

#### Prasyarat Lingkungan:
- **Android Studio** (Hedgehog 2023.1.1 atau yang lebih baru).
- **JDK 17** (Microsoft OpenJDK 17 atau Eclipse Temurin 17).
- **Android SDK** API Level 34 (Android 14) dengan Min SDK 24 (Android 7.0).

#### Langkah-langkah Kompilasi:

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
   app/build/outputs/apk/release/app-release.apk
   ```

3. **Kompilasi APK Debug**:
   ```bash
   ./gradlew assembleDebug
   ```
   *File keluaran berlokasi di:*
   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```

4. **Menjalankan Seluruh Unit Test**:
   ```bash
   ./gradlew testDebugUnitTest
   ```

---

## 📝 Catatan Rilis & Version Control (v3.0.0)

### Apa yang Baru di Versi 3.0.0:
- **Perbaikan Autentikasi MyAnimeList (MAL)**:
  - Menyelesaikan error `java.lang.ClassCastException` saat login pada build Release dengan menonaktifkan R8 FullMode dan mengunci metadata generik `Continuation<-Lcom/canim/app/data/model/MalTokenResponse;>`.
  - Menerapkan penulisan disk sinkron (`.commit()`) pada penyimpanan PKCE verifier agar data otorisasi tidak hilang saat browser dibuka.
  - Menangani error redirect callback dan mereset `intent.data` untuk mencegah penggunaan ulang kode otorisasi satu kali pakai (*single-use code*).
- **Optimasi Scrolling Mulus Setara ArchiveTune**:
  - Implementasi *lambda hoisting* dan tipe parameter stabil pada seluruh komponen daftar untuk menjamin *100% skippable recomposition*.
  - Menghilangkan `LinearProgressIndicator` bawaan dan menggantinya dengan `Box` native yang bebas beban GC.
- **Arsitektur Tanpa Database Lokal**:
  - Mengeliminasi Room SQLite sepenuhnya. MAL menjadi satu-satunya sumber kebenaran data koleksi pengguna (*Single Source of Truth*).
  - Resolusi cerdas metadata via AniList GraphQL dengan fallback otomatis ke endpoint publik MAL.

---

## 🙏 Kredit & Ucapan Terima Kasih

- **AI Pair Programming & Architecture Optimization**: Dibangun, disempurnakan, dan dioptimalkan bersama **Gemini 3.8 Flash** (Google DeepMind) untuk penataan arsitektur, pemecahan bug Retrofit/R8 ProGuard, eliminasi recomposition overhead, serta standarisasi rilis FOSS F-Droid.
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
