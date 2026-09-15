# Changelog

All notable changes to CA'NIM will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow strictly sequential Semantic Versioning without jumping (e.g. v6.2.4 -> v6.2.5).

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
