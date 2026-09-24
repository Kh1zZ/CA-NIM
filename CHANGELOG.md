# Changelog

All notable changes to CA'NIM will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow strictly sequential Semantic Versioning without jumping (e.g. v6.2.4 -> v6.2.5).

## [v6.5.3] - 2026-09-24
### Changed
- **Stats Exporter Redesign**: Merombak total desain export statistik (Story, Portrait, Square, Landscape) dengan sistem layout *Seamless Bento Grid*.
  - Mengimplementasikan teknik *Double-Bezel* berlatar *Ethereal Glass* untuk *high-end premium look*.
  - Pemotongan cover anime/manga (No Stretch) menggunakan algoritma berbasis `Matrix`.
  - Optimalisasi tata letak (Zero Empty Space) untuk memaksimalkan seluruh area kanvas pada setiap rasio gambar.
  - Memperbarui Header (CA-NIM APK) dan Footer (link download Github) pada semua versi rasio hasil export.

## [v6.5.2] - 2026-09-24
### Fixed
- **Edge-to-Edge UI Clipping**: Memperbaiki masalah elemen UI yang terpotong oleh *status bar* pada perangkat Android modern.
  - Menambahkan pengaman `statusBarsPadding()` pada kontainer popup notifikasi global (seperti *Rate Limit* & *Library Syncing*).
  - Menghapus *hardcoded window insets* pada `TopAppBar` di fitur Flashcard Gacha agar header menyesuaikan ukuran *safe area* secara otomatis.

## [v6.5.1] - 2026-09-24
### Optimized
- **Background Notification Aggressiveness & Doze Bypass**:
  - Mengubah alarm schedule dari `ELAPSED_REALTIME` menjadi `ELAPSED_REALTIME_WAKEUP` dan memangkas interval ke 15 menit agar pengecekan notifikasi episode tayang tidak tertahan sistem Doze Android.
- **I/O Threading di Secure Storage**:
  - Mengeliminasi *synchronous disk blocking* di UI thread dengan mengganti 6 instans `SharedPreferences.commit()` menjadi `.apply()` pada modul otentikasi MAL dan state tersimpan, mengurangi potensi *micro-stutter*.

## [v6.5.0] - 2026-09-17
### Fixed
- **Persistent Multi-Layer Screen Stack (Anti-Scroll Reset & Anti-Push Blink)**:
  - Merombak arsitektur perenderan `PredictiveBackOverlayContainer` menjadi persistent multi-layer screen stack berbasis `activeRoutes`. Setiap layar pada stack tetap terpasang di Compose composition tree pada z-index masing-masing dan tidak lagi dipindahkan atau dihancurkan antar-Box.
  - Menghilangkan flicker/blink reset posisi scroll pada saat push screen baru (misal: Detail -> Cast & Crew) karena layar Detail di lapisan bawah tetap hidup pada posisi scroll aktifnya.
  - Mempertahankan `LazyListState`, Coil memory cache, dan viewmodel state secara utuh saat kembali dari child screen (misal: Detail -> Cast & Crew -> Back) tanpa re-initialization atau re-fetching.
- **Real-Time Continuous Scroll State Persistence**:
  - Mengimplementasikan pengamat scroll real-time via `snapshotFlow` pada `MediaDetailScreen`, `CastCrewProfileScreen`, `StudioFilmographyScreen`, dan `FullCastListScreen`. Posisi scroll (`index` & `offset`) kini terus disinkronkan ke ViewModel secara live, mengeliminasi race condition `onDispose` saat pergantian route.
  - Normalisasi `itemKey` deterministik pada `MediaDetailScreen` berdasarkan ID media awal sehingga key tidak pernah berubah saat metadata sekunder (Room/GraphQL) selesai dimuat.

### Changed
- Bumped app version to v6.5.0 (versionCode 57).

## [v6.4.13] - 2026-09-17
### Fixed
- **Animasi Navigasi Seamless ala Animite (Card Expansion & Kinematics Overhaul)**:
  - **Card Expansion saat Buka Detail (0 -> 1)**: Membuka media detail dari menu kini menampilkan efek ekspansi card yang seamless (skala membesar dari `0.92f` ke `1.0f`, elevasi halus `56dp` ke atas, fade-in, dan sudut rounded `24dp` yang menghalus ke `0dp` dengan tab background scrim `40%`).
  - **Card Collapse saat Kembali ke Menu (1 -> 0)**: Kembali ke tab menu menyusutkan layar detail kembali ke ukuran card (`1.0f` ke `0.92f`, slide-down `56dp`, fade-out, rounded `24dp`) memperlihatkan tab menu yang sudah standby tanpa kedipan hitam.
  - **Multi-layer Parallax (N -> N+1)**: Navigasi nested (Detail -> Cast/Crew -> Full Cast) meluncur horizontal mulus dengan parallax `-22%` dan darkening scrim `35%` pada layer bawah.
  - **Predictive Back Gesture Organik & Anti-Nyangkut**: Mengikuti gerakan jari secara live dengan scale dan corner radius adaptif. Pembatalan gesture dilindungi `withContext(NonCancellable)` sehingga spring reset ke `0f` berjalan tuntas 100% tanpa risiko freeze.
  - **Zero-Blink Commit**: Animasi commit gesture meluncur penuh ke `1.0f` sebelum memutasi `screenStack` dengan guard deterministik `wasGesturePop`.

### Changed
- Bumped app version to v6.4.13 (versionCode 56).

## [v6.4.12] - 2026-09-16
### Fixed
- **Predictive Back Gestures & Stage Transitions (Animite-Inspired)**: Overhauled overlay screen navigation with a dedicated `PredictiveBackOverlayContainer` using Android's `PredictiveBackHandler`. Swipe gestures track the user's thumb in real-time with smooth scaling, corner rounding, and parallax reveal of the previous screen, supporting cancellation and spring-back.
- **Two-Layer Stack Rendering & Zero Background Leaks**: Replaced the legacy single-element `AnimatedContent` switcher with a true two-layer managed backstack container. When overlay depth is greater than 1, the immediate underlying screen remains composed with an opaque `BlackBg` directly behind the active screen, completely shielding and preventing the active tab (`DiscoverScreen`) from ever flashing or leaking through.
- **Consecutive Multi-Back Navigation Stutter**: Eliminated the visual blink and layout interruption occurring on consecutive back actions (back 2x, 3x) across nested screens (Detail -> Cast VA -> Detail). The underlying screen is already measured and drawn, enabling instantaneous, silky-smooth transitions.

### Changed
- Bumped app version to v6.4.12 (versionCode 55).

## [v6.4.11] - 2026-09-16
### Fixed
- **Anti-False Cache for Incomplete AniList Metadata**: Anime details that fail to load cast & crew due to AniList API errors (such as 429 rate limits, cooldowns, or network timeouts) are excluded from being treated or stored as valid completed cache entries. Complete cache is only registered once all anime metadata along with cast & crew are successfully populated without warnings.
- **Automatic Metadata & Cast Re-request on Open**: Re-opening any anime with incomplete metadata, fallback status, or missing cast automatically dispatches a fresh AniList API request with live loading indicators, resolving transient failures seamlessly without stale state lock-in.
- **Multi-Layer Back Navigation Visual Blink**: Fixed transition flicker and visual stutter occurring during consecutive back navigation across 2 or more stack layers (e.g., Detail -> Cast VA -> Detail -> Back -> Back). Implemented explicit push/pop navigation state tracking (`isPushNavigation`) in `GlobalViewModel` and synchronized `DetailViewModel` restoration without leaking child metadata during exit transitions.

### Changed
- **Rate Limiter & Burst Request Tuning**: Reduced burst capacity and adjusted token refill intervals for both AniList and MyAnimeList APIs (AniList burst 6, refill 500ms, max concurrent 4; MAL burst 4, refill 700ms, max concurrent 3) to prevent API throttling and rate-limit violations while maintaining responsive UI performance.
- Bumped app version to v6.4.11 (versionCode 54).

## [v6.4.10] - 2026-09-16
### Fixed
- **Overlay Navigation Animations**: Eliminated visual glitch where the background Discover screen flashed frozen during slide animations between overlay screens (Media Detail, Cast/Crew Profile, Studio Filmography, and Full Cast List). Seamless in-stack horizontal push/pop transitions keep screens opaque without transparent fade-outs or scale gaps that expose the underlying tab.
- **Cast/Crew Filmography Back Navigation Spinner**: Fixed infinite loading spinner occurring when pressing Back from a media detail opened via Cast/Crew filmography. Preserved `selectedCastCrewProfile` in `DetailViewModel` when opening child media details and added automatic cached profile restoration and reload fallback in `MainActivity`.
- **Studio Filmography Scroll State Preservation**: Resolved scroll alignment resetting to the top when navigating back to Studio Filmography from an anime detail. Persisted `firstVisibleItemIndex` and `firstVisibleItemScrollOffset` across unmount and restore cycles via `StudioViewModel`.
- **Cast & Crew Profile and Full Cast Scroll Retention**: Extended scroll position preservation to Cast & Crew Profile and Full Cast & Crew screens, ensuring users return to their exact scroll position after viewing child items.

### Changed
- Bumped app version to v6.4.10 (versionCode 53).

## [v6.4.9] - 2026-09-15
### Fixed
- **Movie vs TV Format Consistency**: Resolved critical data inconsistency where anime movies (*Kimi no Na wa*, *Koe no Katachi*, *Spirited Away*, *Jujutsu Kaisen 0*, etc.) appeared correctly as `MOVIE` / "Film Layar Lebar" in Search, but were mistakenly displayed as `"TV"` with `"1 Episode"` in Discover and Library screens.
- **MAL REST API Media Type Field**: Requested `media_type` in all MAL REST endpoints (`anime/ranking`, `user/{username}/animelist`, `anime/{id}`, `anime/season`, `manga/ranking`, etc.) and dynamically mapped `movie`, `tv`, `ova`, `ona`, `special`, `music` in `MediaMappingUtils` and `MalAuthManager` instead of hardcoding `format = "TV"`.
- **AniList GraphQL Detail Format Integration**: Added `format`, `episodes`, `chapters`, and `volumes` to AniList GraphQL fragment `ExtendedMediaDetailFields`, mapped them in `AniListApolloMapper`, and prioritized AniList format over ambiguous fallbacks in `DetailRepositoryImpl` and `DetailViewModel`.
- **Library Sync Format Preservation**: Prevented library reconciliation and server sync in `LibraryRepositoryImpl` from overwriting authoritative `"MOVIE"` format with stale or generic server types.
- **Movie UI Presentation**:
  - Hid redundant "Total Episode: 1 Episode" row for movies in `MediaDetailScreen` and replaced the generic TV icon with `Icons.Default.Movie` labeled "Film Layar Lebar".
  - Enhanced `MediaDisplayFormatter.formatDuration` to display `"$durationMinutes menit (Durasi Penuh)"` instead of `"per episode"` for movies.
  - Formatted movie progress in `LibraryScreen`, `DashboardScreen`, and `MediaDetailDialog` to clearly show "Ditonton (Movie)" / "Belum Ditonton" instead of confusing episode counters like `"0/1 ep"`.

### Changed
- Bumped app version to v6.4.9 (versionCode 52).

## [v6.4.8] - 2026-09-15
### Added
- **Adaptive Navigation Rail**: Introduced side `AdaptiveNavigationRail` on the start edge for Android tablets, foldables, and wide displays (\(\ge 600\text{dp}\)) to preserve vertical viewing space in landscape mode and eliminate stretched navigation bars.

### Fixed
- **Navigation Bar System Insets & Cutoff**: Fixed bottom navigation bar being cut off on smartphones with 3-button navigation (Back/Home/Recents) or gesture pills by applying `navigationBarsPadding()` to the inner navigation container within an edge-to-edge `Surface`.
- **Overlay Navigation Duplication**: Automatically hides the root bottom navigation bar when overlay screens (MediaDetailScreen, FullCastListScreen, CastCrewProfileScreen) are open, preventing double navigation bars or duplicate system insets.
- **Header Row Overflow on Narrow Aspect Ratios**: Constrained user header text with weight and ellipsis in Dashboard and Settings screens, preventing long usernames from pushing the MAL synchronization badge off-screen.
- **Dynamic Backdrop & Responsive Selectors**: Made media detail backdrop height responsive across screen sizes (300dp on tablets, 220dp on phones) and center-constrained wide segmented selectors (`widthIn(max = 520.dp)`) in Library and Search screens.

### Changed
- Bumped app version to v6.4.8 (versionCode 51).

## [v6.4.7] - 2026-09-15
### Added
- **Background Notification Scheduler**: Lightweight, battery-efficient background scheduler using `AlarmManager` with inexact repeating (~30m interval) and `goAsync()` BroadcastReceiver. Dispatches timely notifications for newly aired episodes, plan-to-watch premieres, and app updates with near-zero idle RAM/CPU footprint.
- **Plan to Watch Airing Alerts**: Automatically detects when anime in the user's "Rencana" (Plan to Watch) status starts broadcasting for the season and dispatches deduplicated notification alerts.

### Fixed
- **Library Direct Add Metadata Enrichment**: Fixed bug where adding anime (e.g., Gintama) directly to library without opening detail screen failed to enrich metadata and cover images. Added batch AniList query fallback (`getMediaBatchByMalIds`), bidirectional ID resolution in `saveUserMediaItem`, and safe local entry merging on MAL sync reconciliation to prevent wiping high-quality local cover art.
- **Empty String Cover Masking**: Fixed cover image resolution in `MediaDetailScreen` where blank string `""` from user tracking items blocked high-res AniList fallback images.
- **Library Sorting & Filtering Robustness**: Hardened library sorting fallback for titles (checking title and titleEnglish), scores, and update timestamps when metadata fields are sparse.

### Changed
- **Library Status Filter Reordering**: Status filter chips in the Library screen now place "Ditonton" (Watching) / "Dibaca" (Reading) on the far left as default, followed by "Semua" (All), "Selesai", "Ditunda", "Ditinggalkan", and "Rencana".
- Bumped app version to v6.4.7 (versionCode 50).

## [v6.4.6] - 2026-09-14
### Added
- **Manual Top 5 Anime & Manga Customization**: User can now manually curate their Top 5 Anime and Manga via an interactive 5-slot picker with edit/save toggles, persistent storage, and strict 5-item validation before saving or exporting.
- **Continuous Rank Badges in Discovery**: Top Anime and Top Manga categories in the Discover screen now display rank numbers (`#1`, `#2`, `#3`, ...) on cards.
- **Clickable Metadata Chips**: Genre tags on media detail screens navigate directly to Search with active genre filters; ranking indicators navigate to Discovery Top Anime or Top Manga.

### Fixed
- **Studio Filmography Sorting**: Restored sort-order filtering (Oldest to Newest, Newest to Oldest) by including sort key in CacheManager and Apollo query variables.
- **People/Cast Occupation & Role Sanitize**: Stripped verbose episode tags from occupations and standardized Original Creator tags to "Author".
- **UI Alignment**: Balanced top and bottom padding on Dashboard anime statistics card and centered the counter inside the Stats screen pie chart without redundant "Total" text.

### Changed
- Bumped app version to v6.4.6 (versionCode 49).

## [v6.4.5] - 2026-09-14
### Added
- **2D Diagonal & Vertical Day Selector Animation**: Airing calendar day indicator now animates smoothly across rows (horizontal, vertical, and diagonal movement) inside a unified 2D container.
- **Dual-Engine Flashcard Cooldown**: Flashcard gacha 14-day cooldown now records and evaluates both MAL ID and AniList ID with lazy pruning to eliminate cross-engine duplicates.
- **Targeted Candidate Discovery Pool**: Adaptive gacha recommendation replaces generic seasonal fallback with breadth-bonus exploration to provide personalized candidates matching user genre tastes without querying seasonal defaults.

### Changed
- Bumped app version to v6.4.5 (versionCode 48).

## [v6.4.4] - 2026-09-14
### Fixed
- **Airing Calendar Day Selector**: Fixed height constraint bug where day highlight sliding indicator box expanded vertically and took up the entire screen.
- **Universal Shimmer Animation**: Replaced standard static image loaders with `CanimAsyncImage` (subtle blue shimmer pulse) across all screens displaying anime and manga media (MediaDetailScreen, AiringCalendarScreen, DiscoverScreen, SearchScreen, FlashcardScreen, StatsScreen, FullCastListScreen, CastCrewProfileScreen, StudioFilmographyScreen, and MediaDetailDialog).

### Changed
- Bumped app version to v6.4.4 (versionCode 47).

## [v6.4.3] - 2026-09-14
### Added
- Integrated CHANGELOG.md for release page notes and sequential semver verification in CI/CD pipelines.

### Changed
- Production release version bump to v6.4.3.

## [v6.4.2] - 2026-09-13
### Added
- Occupation/pekerjaan section on cast and crew profile cards with localized Indonesian roles.
- Smooth sliding pill indicator animation on Airing Calendar day selector and Library status/sort options.
- Global modern pulsating cyber loading transition during MAL OAuth login token exchange.
- Shimmer pulse loading animation with subtle blue accent for asynchronously loading image covers.
- Floating top notification banner on Library synchronization start and automatic dismissal.

### Fixed
- Discovery Manga tab Trending Now returning anime items instead of manga items.
- Real people (voice actors, directors, creators, staff) badge normalized to unified 'PEOPLE' tag instead of 'KARAKTER'.

### Performance
- Tuned AdaptiveRateLimiter parameters for MAL and AniList to minimize UI latency and improve responsiveness.

## [v6.4.1] - 2026-09-13
### Fixed
- DualEngineResilience test assertions and version code synchronization.

## [v6.4.0] - 2026-09-13
### Added
- Fullcast and crew detail view integration with offline caching.
- Native MAL OAuth2 authorization and session management.

## [v6.3.4] - 2026-09-13
### Fixed
- Hardcoded version bump assertion removed from DualEngineResilienceTest.

## [v6.3.3] - 2026-09-12
### Changed
- Production release version bump and dependency sync.

## [v6.3.2] - 2026-09-12
### Changed
- Routine maintenance release with performance optimizations.

## [v6.3.1] - 2026-09-12
### Changed
- Minor bug fixes and rate limiter resilience adjustments.

## [v6.3.0] - 2026-09-12
### Added
- Modular ViewModels architecture (GlobalViewModel, LibraryViewModel, SearchViewModel, DiscoverViewModel, DetailViewModel, StudioViewModel, GachaViewModel, UpdateViewModel).
- Upgraded Android build toolchain to Kotlin 2.0.21.
- Offline-first mutation queue with Room database and AdaptiveRateLimiter backoff strategy.
