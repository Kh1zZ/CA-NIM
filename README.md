# CA'NIM — Technical Architecture & Engineering Documentation

<p align="center">
  <img src="art/logo.png" alt="CA'NIM Logo" width="100" height="100" style="border-radius: 20px;">
</p>

<p align="center">
  <a href="https://github.com/Kh1zZ/CA-NIM/releases"><img src="https://img.shields.io/badge/Version-v6.4.6%20(Build%2049)-0052CC.svg?style=for-the-badge" alt="Version"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=for-the-badge" alt="License"></a>
  <a href="#-tech-stack--toolchain-modern"><img src="https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-8B5CF6.svg?style=for-the-badge" alt="Platform"></a>
  <a href="#-tech-stack--toolchain-modern"><img src="https://img.shields.io/badge/Runtime-JDK%2021%20LTS-ED8B00.svg?style=for-the-badge" alt="JDK 21"></a>
  <a href="#-tech-stack--toolchain-modern"><img src="https://img.shields.io/badge/Kotlin-2.0.21%20(K2)-7F52FF.svg?style=for-the-badge" alt="Kotlin 2.0"></a>
  <a href="#-tech-stack--toolchain-modern"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-3B82F6.svg?style=for-the-badge" alt="UI"></a>
  <a href="#-kebijakan-pengujian--verifikasi-lokal"><img src="https://img.shields.io/badge/Tests-100%25%20Passing%20(410%20Tests)-10B981.svg?style=for-the-badge" alt="Tests"></a>
  <a href="#-rekayasa--kolaborasi-ai"><img src="https://img.shields.io/badge/Engineered%20by-Gemini%203.8%20Flash-4285F4.svg?style=for-the-badge" alt="Gemini 3.8 Flash"></a>
</p>

---

Dokumentasi teknis resmi repositori **CA'NIM** (`com.canim.app`). Aplikasi Android native berbasis **Clean Architecture** untuk pelacakan anime & manga dengan integrasi **Dual-Engine Synchronization** (MyAnimeList REST & AniList GraphQL), performa luring (*offline-first*), serta antarmuka modern Jetpack Compose Material 3.

Landing page & visual showcase pengguna: [CA-NIM-LP](https://github.com/Kh1zZ/CA-NIM-LP).

---

## 🏛️ 1. Paradigma Clean Architecture

CA'NIM dibangun dengan arsitektur berlapis (*layered architecture*) yang mematuhi prinsip **Clean Architecture** dan **Separation of Concerns (SoC)**. Aliran dependensi berjalan satu arah (*Dependency Inversion Principle*), di mana domain core sepenuhnya independen dari framework Android, database, maupun pustaka jaringan.

```text
┌──────────────────────────────────────────────────────────────────────────────────┐
│                             PRESENTATION LAYER (UI)                              │
│  • Jetpack Compose Material 3 Screens (Dashboard, Library, Discover, Search, ...)│
│  • Unidirectional Data Flow (UDF) via StateFlow & Sealed UiEvents               │
│  • Feature ViewModels: LibraryViewModel, DetailViewModel, SearchViewModel, dll. │
└────────────────────────────────────────┬─────────────────────────────────────────┘
                                         │ Mengonsumsi (Use Cases)
                                         ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                                DOMAIN LAYER (CORE)                               │
│  • Pure Kotlin (Zero Android Framework Dependency)                               │
│  • Use Cases / Interactors (Single Responsibility Principle):                    │
│    - GetLibraryUseCase, SaveLibraryItemUseCase, DeleteLibraryItemUseCase         │
│    - SearchMediaUseCase, GetDiscoverCategoryUseCase, GetExtendedDetailUseCase    │
│    - GetStudioFilmographyUseCase, SearchStudiosUseCase, ConsumeGachaCreditUseCase│
│    - SyncMalUseCase, CheckForUpdatesUseCase, ObserveCacheRefreshUseCase          │
│  • Domain Models: MediaItem, UserMediaItem, MediaRef, MalTracking, TrackerStats  │
│  • Repository Contracts (Interfaces): LibraryRepository, SearchRepository, dll.  │
└────────────────────────────────────────▲─────────────────────────────────────────┘
                                         │ Mengimplementasikan (Dependency Inversion)
┌────────────────────────────────────────┴─────────────────────────────────────────┐
│                                 DATA LAYER                                       │
│  • Repository Implementations: CanimRepositoryImpl                               │
│  • Remote Data Sources:                                                          │
│    - AniList Apollo GraphQL 4 (Rich Visuals, Cast/Crew, Studio, Recommendations) │
│    - MyAnimeList REST API (Authoritative User Tracking, OAuth2 PKCE)             │
│    - AdaptiveRateLimiter (Header-Driven Dynamic Backoff & Queue Pacing)          │
│    - MediaResolver (Cross-Service Non-Deterministic ID Resolution)               │
│  • Local Data Sources:                                                           │
│    - Room DB (LocalLibraryDatabase, PendingMutationDao, Offline Mutation Queue)  │
│    - Stale-While-Revalidate (SWR) In-Memory CacheManager                         │
│    - EncryptedSharedPreferences (AndroidX Security Crypto MasterKey)             │
│    - GachaCreditManager (Local Transaction Ledger)                               │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### Karakteristik Masing-Masing Layer:

1. **Presentation Layer:**
   * Dibangun 100% menggunakan deklaratif UI **Jetpack Compose** dengan desain **Material 3**.
   * Menerapkan pola **Unidirectional Data Flow (UDF)**: UI merefleksikan state yang dipancarkan oleh `StateFlow` dan mengirimkan aksi melalui `UiEvent` ke ViewModel.
   * ViewModel tidak berinteraksi langsung dengan data layer, melainkan selalu mengeksekusi use case domain yang relevan.

2. **Domain Layer:**
   * Pusat logika bisnis aplikasi yang murni ditulis dalam Kotlin tanpa dependensi framework Android (`android.*`).
   * Setiap *use case* memiliki tanggung jawab tunggal (*Single Responsibility*) yang dapat diuji secara independen tanpa memerlukan mock framework Android yang berat.
   * Mendefinisikan kontrak interface abstraksi repository yang akan diwujudkan oleh data layer.

3. **Data Layer:**
   * Mengatur orkestrasi data dari sumber lokal (Room, SharedPreferences terenkripsi, memory cache) dan sumber jaringan jarak jauh (AniList GraphQL & MyAnimeList REST).
   * Menerapkan prinsip *Offline-First* dengan antrean mutasi sinkronisasi yang andal.

---

## 🔄 2. Dual-Engine Synchronization & Smart Resolving

Salah satu inovasi arsitektur terpenting CA'NIM adalah pemisahan peran antara **MyAnimeList** dan **AniList**:

```text
┌─────────────────────────┐                   ┌─────────────────────────┐
│       MyAnimeList       │                   │         AniList         │
│     (Authoritative)     │                   │     (Rich Metadata)     │
├─────────────────────────┤                   ├─────────────────────────┤
│ • Status Pelacakan      │                   │ • Banner & Poster HD    │
│ • Episode/Chapter Baca  │                   │ • Sinopsis Lengkap      │
│ • Tanggal Mulai/Selesai │                   │ • Karakter, Pengisi     │
│ • Nilai/Skor Pribadi    │                   │   Suara, & Staf         │
│ • Catatan & Prioritas   │                   │ • Hubungan Waralaba     │
│ • Sinkronisasi Akun     │                   │ • Studio Filmografi     │
└───────────┬─────────────┘                   └────────────┬────────────┘
            │                                              │
            ▼                                              ▼
┌───────────────────────────────────────────────────────────────────────┐
│             MediaResolver & Dual-Engine Data Orchestrator             │
│  • Pemetaan ID Non-Deterministik (AniList ID ↔ MyAnimeList ID)        │
│  • SWR (Stale-While-Revalidate) Multilevel Caching                    │
│  • Adaptive Rate Limiting dengan Dynamic Request Pacing               │
└───────────────────────────────────┬───────────────────────────────────┘
                                    ▼
                         UserMediaItem (Unified)
```

### Mekanisme Kunci Rekayasa Jaringan:

* **Separation of Concerns:** MyAnimeList memegang otoritas penuh atas identitas pengguna dan progres pelacakan, sedangkan AniList (via Apollo GraphQL v4) menyuplai metadata kaya tanpa batasan metadata MAL yang terbatas.
* **MediaResolver:** Menjembatani ketidakselarasan ID numerik antara AniList dan MyAnimeList secara otonom melalui caching asosiasi, penelusuran balik GraphQL, dan *negative-caching* untuk menghindari panggilan HTTP berulang pada entri yang tidak ditemukan.
* **AdaptiveRateLimiter:** Menjaga kepatuhan terhadap batas request rate AniList (90 req/menit) dan proteksi lonjakan HTTP MAL melalui header pacing dinamis (`Retry-After`, `X-RateLimit-Remaining`) dan algoritma *exponential backoff*.

---

## ✨ 3. Ikhtisar Fitur Aplikasi

* **Sinkronisasi & Pelacakan Koleksi**: Pelacakan progres tonton/baca real-time dengan sinkronisasi dua arah akun MyAnimeList dan antrean mutasi luring (*offline-first*).
* **Metadata & Visual Detail Kaya**: Sinopsis, karakter, pengisi suara (VA), staf produksi, trailer, relasi waralaba, dan filmografi studio bertenaga AniList GraphQL.
* **Eksplorasi & Pencarian Adaptif**: Katalog tren musiman, media teratas, jadwal penayangan kalender mingguan, serta filter pencarian instan berbasis genre, format, dan studio.
* **Statistik Personal & Ekspor**: Ringkasan metrik koleksi, pie chart distribusi status, kurasi Top 5 pribadi, dan ekspor infografis statistik multi-format (PDF/JPG/PNG).
* **Flashcard Rekomendasi (Gacha)**: Sistem rekomendasi judul adaptif berbasis preferensi selera pengguna dengan sistem cooldown pintar.

---

## ⚡ 4. Offline-First & Keamanan Data

* **Offline Mutation Queue (Room)**: Mutasi pelacakan luring dicatat dalam `PendingMutationDao` dan disinkronkan otomatis saat jaringan pulih.
* **Secure Storage (AndroidX Crypto)**: Token OAuth2 PKCE terlindungi dalam `EncryptedSharedPreferences` dengan enkripsi AES-256 GCM berbasis Android Keystore.
* **SWR Memory & Disk Cache**: Pipeline `CacheManager` dengan strategi *Stale-While-Revalidate* dan *negative-caching* untuk meminimalkan beban jaringan.
* **Efisiensi Memori**: Coil 2.6 dengan *bitmap pooling* dan disk-caching bertingkat untuk mencegah Out-Of-Memory (OOM).

---

## 🛠️ 5. Tech Stack & Toolchain Modern

CA'NIM dibangun dengan standar teknologi Android paling modern saat ini:

| Komponen | Versi | Rincian Teknis |
| :--- | :--- | :--- |
| **Java Development Kit** | **JDK 21 LTS** | Toolchain build utama menggunakan Java 21 LTS dengan optimasi memori runtime |
| **Kotlin** | **2.0.21** | Frontend compiler generasi baru **K2 Compiler** untuk efisiensi kompilasi maksimal |
| **Gradle** | **8.10.2** | Build acceleration, full Gradle daemon caching, dan konfigurasi paralel |
| **Android Gradle Plugin** | **8.7.3** | Dukungan penuh terhadap integrasi D8 desugaring dan platform Android modern |
| **UI Framework** | **Jetpack Compose M3** | Menggunakan Compose Compiler Gradle Plugin resmi (`org.jetbrains.kotlin.plugin.compose`) |
| **Dependency Injection** | **Dagger Hilt 2.51.1** | Diintegrasikan bersama **KSP 2.0.21-1.0.28** untuk pemrosesan anotasi bebas kapt |
| **GraphQL Client** | **Apollo Kotlin 4.0.0** | Kompilasi tipe GraphQL yang aman untuk querying fleksibel ke AniList API v2 |
| **Jaringan & REST** | **Retrofit 2.9.0 & OkHttp 4.12** | Interceptor pelacakan rate-limit adaptif dan autentikasi OAuth2 MAL |
| **Lokal Database** | **Room 2.6.1** | Penyimpanan transaksi offline mutation dan data library persisten |
| **Image Loading** | **Coil Compose 2.6.0** | Pipeline pemuatan gambar asinkron dengan caching memori dan disk |

---

## 📁 6. Struktur Direktori Proyek

```text
com.canim.app/
├── data/                               # DATA LAYER
│   ├── cache/                          # SWR CacheManager, IdMapping, Memory stores
│   ├── local/                          # Room Database, Top5CustomManager, GachaManager
│   ├── model/                          # Entity mapping, DTO, Payload data transfer
│   ├── remote/                         # Network engines
│   │   ├── anilist/                    # Apollo GraphQL Client, Queries, Fragments
│   │   ├── mal/                        # Retrofit MAL API Service, OAuth2 PKCE
│   │   ├── AdaptiveRateLimiter.kt      # Algoritma dynamic request rate limiter
│   │   └── RequestPolicy.kt            # Strategi request backoff & observability
│   ├── repository/                     # Implementasi konkret domain repository
│   └── resolver/                       # MediaResolver (cross-engine ID matchmaker)
│
├── domain/                             # DOMAIN LAYER (Pure Kotlin Core)
│   ├── model/                          # MediaItem, UserMediaItem, MediaRef, MalTracking
│   ├── repository/                     # Interfaces: LibraryRepository, SearchRepository, dll.
│   └── usecase/                        # Single-purpose interactors (GetLibrary, SyncMal, ...)
│
├── ui/                                 # PRESENTATION LAYER
│   ├── components/                     # Reusable Compose widgets, Dialogs, Cards
│   ├── navigation/                     # Navigation Graph, Route definitions
│   ├── screens/                        # Dashboard, Library, Discover, Detail, Stats, Studio
│   ├── theme/                          # Material 3 Color scheme, Typography, Shapes
│   └── viewmodel/                      # Feature ViewModels memancarkan UI State via UDF
│
└── di/                                 # DEPENDENCY INJECTION (Dagger Hilt Modules)
```

---

## 🧪 7. Kebijakan Pengujian & Verifikasi Lokal

### Aturan Kompilasi (Zero Local APK Builds):
* **DILARANG** menjalankan `./gradlew assembleDebug` atau `assembleRelease` secara lokal. Kompilasi APK rilis maupun debug resmi didelegasikan sepenuhnya ke runner **GitHub Actions** untuk menjamin lingkungan build yang steril dan konsisten.
* **Verifikasi Lokal**: Verifikasi lokal berfokus murni pada pengujian logika bisnis inti (*core business logic*) melalui rangkaian unit test otomatis (*Lean Testing Policy*).

### Menjalankan Pengujian Unit:
Sebelum melakukan commit, seluruh unit test wajib lulus 100%:
```cmd
cmd /c "set JAVA_HOME=C:\Program Files\Java\jdk-21.0.12.1&& gradlew.bat testDebugUnitTest"
```

* **Status Verifikasi**: **100% Lulus (410 Tests)** mencakup pengujian use case domain, transaksi Room, SWR caching, sinkronisasi dual-engine, dan adaptive rate limiter.

---

## 🚀 8. CI/CD Pipeline & Alur Rilis

Proyek ini mengimplementasikan continuous integration dan delivery otomatis via **GitHub Actions**:

1. **`ci.yml` (Test & Verify)**:
   * Berjalan otomatis pada branch `main` dan `Pull Request`.
   * Memvalidasi seluruh unit test menggunakan **JDK 21 Temurin** dan mengompilasi APK debug.
2. **`release.yml` (Build & Publish Release)**:
   * Dipicu secara otomatis saat tag rilis baru di-*push* (format `v*`, contoh: `v6.4.6`).
   * Memvalidasi kecocokan versi antara `versionName`, `versionCode`, dan catatan rilis di `CHANGELOG.md`.
   * Menandatangani APK menggunakan Android Release Keystore via GitHub Secrets, menjalankan optimasi bytecode **R8 Shrinker (Full Mode)**, dan memublikasikan asset release secara otomatis.

> ℹ️ **Catatan Riwayat Versi**: Catatan perubahan detail dari setiap versi aplikasi dapat dilihat pada berkas [CHANGELOG.md](CHANGELOG.md).

---

## 🤖 9. Rekayasa & Kolaborasi AI

Seluruh perancangan arsitektur perangkat lunak, transformasi **Clean Architecture**, algoritma ketahanan jaringan (*Dual-Engine Synchronization & Adaptive Rate Limiter*), modernisasi toolchain (**JDK 21 LTS, Kotlin 2.0.21 K2, Gradle 8.10.2**), serta 410 unit test otomatis pada repositori ini **diciptakan, direkayasa, dan dikembangkan secara kolaboratif bersama Google Gemini 3.8 Flash** sebagai AI Software Architect & Lead Technical Partner.

---

## 📜 10. Lisensi

Proyek ini dilisensikan di bawah lisensi **GNU General Public License v3.0 (GPL-3.0)**. Lihat berkas [LICENSE](LICENSE) untuk informasi lebih lanjut.
