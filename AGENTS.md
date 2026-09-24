# CA'NIM AI Rules & Workflow

## 1. Zero Local APK Builds
- DILARANG jalankan `./gradlew assembleDebug`/`assembleRelease` lokal — build APK hanya via GitHub Actions.
- Verifikasi lokal hanya unit test:
  `cmd /c "set JAVA_HOME=C:\Program Files\Java\jdk-21.0.12.1&& gradlew.bat testDebugUnitTest"`
- Semua unit test wajib 100% lulus sebelum commit.

## 2. Git: GUI-First
- User pakai GUI (GitHub Desktop/VS Code Git/Web GitHub) bukan CLI.
- AI dilarang `git push` otomatis — beri panduan GUI, biarkan user push/merge sendiri.

## 3. Project Overview
- Native Android app (Kotlin) — `applicationId com.canim.app`, minSdk 24, targetSdk 34, compileSdk 34.
- Clean Architecture: `domain` (pure Kotlin, zero Android deps) → `data` (Room/Retrofit/Apollo/Hilt) → `ui` (Jetpack Compose M3, UDF).
- Dual-engine: **MyAnimeList REST** (authoritative user tracking, OAuth2 PKCE) + **AniList Apollo GraphQL 4** (rich metadata). `MediaResolver` bridges non-deterministic IDs.
- Networking guarded by `AdaptiveRateLimiter` (header-driven backoff); local-first with Room offline mutation queue + SWR in-memory cache.

## 4. Toolchain
- JDK 21 LTS · Kotlin 2.0.21 (K2) · Gradle 8.10.2 · AGP 8.7.3 · Hilt 2.51.1 + KSP · Apollo 4.0.0 · Retrofit 2.9/OkHttp 4.12 · Room 2.6.1 · Coil 2.6.
- Versi dependency terpusat di `gradle/libs.versions.toml` (`alias(libs.plugins.*)`).

## 5. Source Layout (`app/src/main/java/com/canim/app/`)
- `data/` — `cache/` SWR, `local/` Room + managers, `model/` DTO/payload, `remote/` (`anilist/`, `mal/`, `AdaptiveRateLimiter.kt`, `RequestPolicy.kt`), `repository/`, `resolver/`.
- `domain/` — `model/`, `repository/` (interfaces), `usecase/` (single-purpose interactors).
- `ui/` — `components/`, `navigation/`, `screens/`, `theme/`, `viewmodel/`.
- `di/` — Hilt modules. `notification/`, `widget/`, `util/` pendukung.

## 6. Kebijakan Pengujian Ramping (Lean Testing Policy)
- DILARANG menambah unit test berlebihan, redundant, atau mikro-test sepele.
- Fokus hanya pada *critical core business logic*: use case domain, algoritma sync/rate limiting, transaksi Room.
- Pertahankan test suites ramping dan cepat. Test: JUnit4 + Robolectric + `kotlinx-coroutines-test` di `app/src/test/`.

## 7. Version Bump & Release (via GEMINI.md)
Saat diminta naikkan versi:
1. Update `versionCode` & `versionName = "vX.Y.Z"` di `app/build.gradle.kts`.
2. Tambahkan catatan perubahan versi baru di `CHANGELOG.md` secara berurutan tanpa loncat.
3. JANGAN update `README.md` kecuali diminta eksplisit.
4. Commit + tag lokal: `git commit -m "build: bump version to vX.Y.Z (versionCode N)"` lalu `git tag vX.Y.Z`.
5. Beri user command push (`git push origin main`) — AI tidak push.
6. CI: `.github/workflows/release.yml` otomatis berjalan pada setiap push `main` (serta tag `v*`) untuk build APK Release resmi dan mempublikasikannya ke GitHub Releases (tanpa checksum SHA-256). Tidak ada build APK debug (`ci.yml` dihapus).

## 8. Kebijakan Perencanaan & Persetujuan (Planning & Approval)
1. Setiap perubahan (kode/desain) dari agent WAJIB diawali dengan membuat *implementation plan* yang harus disetujui oleh dev/user.
2. TANPA persetujuan dev secara eksplisit, TIDAK ADA kode yang boleh dieksekusi atau ditulis.
