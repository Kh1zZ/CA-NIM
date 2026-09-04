# CA'NIM - Lacak Anime dan Manga (100% Android Native Edition)

Aplikasi pelacak anime dan manga **100% Android Native** berbasis **Kotlin**, **Jetpack Compose (Material 3)**, **Android Room Database (SQLite)**, dan **MyAnimeList OAuth2 Authorization Code + PKCE**.

> **Proyek ini murni Android Native (Bukan WebView, Bukan Hybrid, Bukan PWA/React).**
> Seluruh antarmuka dibangun dengan Jetpack Compose deklaratif dan kode Kotlin di dalam modul `:app`.

---

## 📱 Cara Membuka & Menjalankan di Android Studio

1. **Clone atau Buka Folder Proyek**:
   - Jika sudah sync ke GitHub: `git clone <repo-url>`
   - Buka aplikasi **Android Studio** (Hedgehog, Iguana, Jellyfish, atau versi terbaru).
2. **Open Project**:
   - Klik **File** > **Open...**
   - Pilih direktori root proyek ini (`canim` atau direktori tempat repo berada).
   - Android Studio akan otomatis mendeteksi modul `:app` dan melakukan Gradle Sync.
3. **Run App**:
   - Hubungkan HP Android fisik (dengan USB Debugging aktif) atau buat Android Emulator (AVD).
   - Klik tombol **Run (▶)** di Android Studio, atau jalankan perintah berikut di terminal:
   ```bash
   ./gradlew assembleDebug
   ```
   - APK akan ter-generate di: `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🏛 Arsitektur & Tech Stack Native

| Komponen | Implementasi | Keterangan |
|---|---|---|
| **Bahasa** | Kotlin 1.9.22 | 100% Native Kotlin |
| **UI System** | Jetpack Compose (Material Design 3) | Tidak ada WebView/HTML, deklaratif M3 |
| **Local Database** | Android Room 2.6.1 (SQLite) | Data tersimpan lokal di perangkat, offline-first |
| **Network & REST** | Retrofit 2.9.0 + OkHttp 4 | Koneksi ke Jikan v4 & MyAnimeList v2 API |
| **Authentication** | OAuth2 PKCE (`canim://oauth/callback`) | Login native via system browser tanpa client secret |
| **Secure Storage** | AndroidX Security Crypto (`EncryptedSharedPreferences`) | Penyimpanan token aman berbasis Android Keystore |
| **Image Loading** | Coil Compose 2.6.0 | Async image caching & loading native |
| **Concurrency** | Kotlin Coroutines & StateFlow | Asynchronous reactive architecture |
| **Build System** | Gradle Kotlin DSL (`.gradle.kts`) | Version Catalog (`gradle/libs.versions.toml`) |

---

## 📂 Struktur Modul Android Native (`/app`)

```text
app/
├── build.gradle.kts                   # Konfigurasi dependensi Compose, Room, Retrofit
└── src/
    ├── main/
    │   ├── AndroidManifest.xml        # Permission Internet, Deep Link OAuth intent-filter
    │   ├── java/com/canim/app/
    │   │   ├── MainActivity.kt        # Entry point ComponentActivity & Jetpack Compose Root
    │   │   ├── data/
    │   │   │   ├── local/
    │   │   │   │   ├── CanimDatabase.kt     # RoomDatabase SQLite definition
    │   │   │   │   ├── AnimeDao.kt          # DAO Query & CRUD Anime
    │   │   │   │   ├── MangaDao.kt          # DAO Query & CRUD Manga
    │   │   │   │   ├── AnimeEntity.kt       # Room Entity tabel anime_items
    │   │   │   │   ├── MangaEntity.kt       # Room Entity tabel manga_items
    │   │   │   │   └── MalSecureStorage.kt  # Android Keystore EncryptedSharedPreferences
    │   │   │   ├── model/             # Kotlin Data Classes (Anime, Manga, MAL User, dll.)
    │   │   │   ├── remote/
    │   │   │   │   ├── ApiClient.kt         # Retrofit & OkHttp builder
    │   │   │   │   ├── JikanApiService.kt   # Jikan REST API Interface
    │   │   │   │   └── MalApiService.kt     # MyAnimeList REST API Interface
    │   │   │   └── repository/
    │   │   │       ├── CanimRepository.kt   # Repository pattern data source
    │   │   │       └── MalAuthManager.kt    # PKCE Token Generator & Exchange
    │   │   └── ui/
    │   │       ├── screens/
    │   │       │   ├── DashboardScreen.kt   # Metrik, Progres Cepat (+1 Ep/+1 Ch)
    │   │       │   ├── LibraryScreen.kt     # Manajemen koleksi Anime & Manga
    │   │       │   ├── SearchScreen.kt      # Penelusuran & eksplorasi tren Jikan
    │   │       │   ├── DetailModalSheet.kt  # Modal editor status, skor, rewatch
    │   │       │   └── SettingsScreen.kt    # MyAnimeList OAuth & database controls
    │   │       ├── theme/               # Cyber Dark AMOLED (#080808) & Neon Green (#00FF66)
    │   │       └── viewmodel/
    │   │           └── CanimViewModel.kt    # UI State management & Coroutines
    │   └── res/                         # Android Vector Drawables, Mipmaps, Strings, Colors
    └── test/                            # Unit & Robolectric Tests
```

---

## ⚡ Fitur Utama

- **Local-First SQLite Room**: Database lokal persisten tanpa perlu server perantara, tetap berfungsi penuh saat offline.
- **MyAnimeList OAuth2 PKCE**: Otorisasi aman langsung di browser Android pengguna dengan skema deep link `canim://oauth/callback`, token dienkripsi menggunakan Android Keystore.
- **Quick Progress Increment**: Tombol satu-ketukan `+1 Episode` dan `+1 Chapter` di Dashboard.
- **Desain Cyber Dark Material 3**: UI modern, responsif terhadap status bar dan notch kamera, serta ramah baterai AMOLED.
