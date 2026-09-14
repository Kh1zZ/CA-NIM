# Changelog

All notable changes to CA'NIM will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow strictly sequential Semantic Versioning without jumping (e.g. v6.2.4 -> v6.2.5).

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
