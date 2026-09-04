<p align="center">
  <img src="art/logo.png" alt="CA'NIM Logo" width="130" height="130" style="border-radius: 28px; box-shadow: 0 8px 24px rgba(0,0,0,0.35);">
</p>

<h1 align="center">CA'NIM - Lacak Anime dan Mangamu</h1>

<p align="center">
  <strong>Tersinkronisasi langsung dengan akun MAL</strong>
</p>

<p align="center">
  <a href="#-tech-stack--dependensi"><img src="https://img.shields.io/badge/Version-v3.0.0-0052CC.svg?style=for-the-badge" alt="Version"></a>
  <a href="#-fitur-utama"><img src="https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-10B981.svg?style=for-the-badge" alt="Platform"></a>
  <a href="#-arsitektur-dan-prinsip-desain"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-3B82F6.svg?style=for-the-badge" alt="UI"></a>
  <a href="#-kinerja-dan-optimasi-standar-archivetune"><img src="https://img.shields.io/badge/APK%20Size-~2.08%20MB-F59E0B.svg?style=for-the-badge" alt="Size"></a>
  <a href="#-panduan-kompilasi--menjalankan-aplikasi"><img src="https://img.shields.io/badge/Tests-14%20Passed-6366F1.svg?style=for-the-badge" alt="Tests"></a>
</p>

---

## 🎨 Analisis & Filosofi Ikon Aplikasi (`ic_app_icon`)

Ikon resmi **CA'NIM** (`ic_app_icon.xml` & `mipmap-xxxhdpi/ic_launcher.png`) dirancang dengan elemen visual yang merepresentasikan esensi aplikasi:

```text
                      ┌──────────────────────────────────────┐
                      │            CA'NIM ICON               │
                      ├──────────────────────────────────────┤
                      │  [ Buku Manga 3D + Mata Anime ]      │ ───► Katalog Anime & Manga
                      │  [ Panel Komik + Gerbang Torii ]     │ ───► Kultur & Estetika Visual
                      │  [ Lencana Awan + Panah Sinkronisasi]│ ───► Cloud Sync MyAnimeList Real-Time
                      │  [ Squircle Cyber & Deep Blue/Green ]│ ───► Modern Dark Theme & Native
                      └──────────────────────────────────────┘
```

1. **Buku Manga 3D dengan Mata Anime**: Melambangkan buku komik dan katalog tontonan yang hidup. Goresan detail kelopak mata, iris bergradasi, dan pantulan cahaya (*sparkle*) mencerminkan ekspresi emosional media anime & manga.
2. **Panel Manga Latar & Siluet Torii**: Menghadirkan atmosfer khas komik strip Jepang dengan balon dialog (*speech bubble*), awan langit, dan siluet gerbang Torii tradisional.
3. **Lencana Sinkronisasi Awan (Cloud Sync Badge)**: Ikon awan putih dengan dua panah melingkar di sudut kanan bawah menandakan integrasi **Single Source of Truth** langsung ke cloud **MyAnimeList (MAL)** tanpa ketergantungan pada database offline pihak ketiga.
4. **Bentuk Squircle & Palet Cyber**: Menggabungkan sudut melengkung dinamis dengan kontras aksen neon yang selaras dengan tema gelap (*Dark Mode Native*) antarmuka aplikasi.

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

## ⚡ Kinerja dan Optimasi (Standar ArchiveTune)

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

## 🚀 Panduan Kompilasi & Menjalankan Aplikasi

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

2. **Kompilasi APK Release (Direkomendasikan)**:
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

<p align="center">
  Dibuat dengan ❤️ untuk komunitas Anime & Manga Indonesia.<br>
  <strong>CA'NIM — Lacak Anime dan Mangamu</strong>
</p>
