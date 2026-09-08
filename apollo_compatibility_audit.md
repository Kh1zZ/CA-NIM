# Apollo Kotlin 4.x Compatibility Audit — CA'NIM

**Document Version:** 1.0  
**Target Application:** CA'NIM (Android, `com.canim.app`)  
**Audit Date:** 2026-09-07  
**Scope:** Phase 0.5 — Compatibility & Toolchain Assessment (Read-Only)

---

## 1. Current Toolchain

A comprehensive inspection of CA'NIM's build configuration, Gradle wrapper, version catalogs, and environment variables confirms the following exact versions:

| Component | Current Configured Version | Verification Source |
|---|---|---|
| **Gradle Version** | `8.5` | `gradle/wrapper/gradle-wrapper.properties` (`gradle-8.5-bin.zip`) |
| **Android Gradle Plugin (AGP)** | `8.3.2` | `gradle/libs.versions.toml` (`agp = "8.3.2"`) |
| **Kotlin Version** | `1.9.22` | `gradle/libs.versions.toml` (`kotlin = "1.9.22"`) |
| **Kotlin Compiler Extension (Compose)** | `1.5.8` | `app/build.gradle.kts` (`kotlinCompilerExtensionVersion = "1.5.8"`) |
| **Compose BOM** | `2024.02.00` | `gradle/libs.versions.toml` (`composeBom = "2024.02.00"`) |
| **Java `sourceCompatibility`** | `JavaVersion.VERSION_17` | `app/build.gradle.kts` |
| **Java `targetCompatibility`** | `JavaVersion.VERSION_17` | `app/build.gradle.kts` |
| **Kotlin `jvmTarget`** | `"17"` | `app/build.gradle.kts` (`kotlinOptions { jvmTarget = "17" }`) |
| **JDK (Local Developer Machine)** | `17.0.20.1` | `C:\Users\KH1ZZ\.jdks\ms-17.0.20.1` (Microsoft OpenJDK 17.0.20.1+1-LTS) |
| **JDK (GitHub Actions CI)** | `17` | `.github/workflows/release.yml` (`temurin`, `java-version: '17'`) |
| **`compileSdk`** | `34` (Android 14) | `app/build.gradle.kts` |
| **`minSdk`** | `24` (Android 7.0 Nougat) | `app/build.gradle.kts` |
| **`targetSdk`** | `34` (Android 14) | `app/build.gradle.kts` |
| **Kotlinx Coroutines** | `1.8.0` | `gradle/libs.versions.toml` (`kotlinxCoroutines = "1.8.0"`) |
| **OkHttp Version** | `4.12.0` | `gradle/libs.versions.toml` (`okhttp = "4.12.0"`) |
| **Retrofit / Converter Gson** | `2.9.0` | `gradle/libs.versions.toml` (`retrofit = "2.9.0"`) |
| **AndroidX Security Crypto** | `1.1.0-alpha06` | `gradle/libs.versions.toml` |

---

## 2. Target Apollo Kotlin Version

- **Target Version Family:** **Apollo Kotlin 4.x** (specifically `4.0.0` or latest stable patch `4.1.1`).
- **Official Plugin ID:** `com.apollographql.apollo` *(Note: replaces legacy v3 `com.apollographql.apollo3`)*.
- **Core Runtime Artifacts:**
  - `com.apollographql.apollo:apollo-runtime`
  - `com.apollographql.apollo:apollo-normalized-cache-sqlite` *(optional, if normalized persistence is desired)*

---

## 3. Compatibility Matrix

| Build Tool / Dependency | Apollo Kotlin 4.x Requirement | CA'NIM Current | Status | Notes |
|---|---|---|---|---|
| **Gradle** | `8.0` or higher (7.6+ minimum) | `8.5` | ✅ **COMPATIBLE** | Full support for Gradle 8.5 configuration cache and task avoidance. |
| **Android Gradle Plugin (AGP)** | `8.0.0` or higher | `8.3.2` | ✅ **COMPATIBLE** | Seamless integration with AGP 8.3 variant APIs. |
| **Kotlin Language & KGP** | `1.9.20` or higher / `2.0.x` | `1.9.22` | ✅ **COMPATIBLE** | Fully compatible with Kotlin 1.9.22 without requiring K2 / Kotlin 2.0. |
| **Java / JVM Bytecode Target** | Java `11` or Java `17` | Java `17` | ✅ **COMPATIBLE** | Toolchain and CI are already standardized on LTS JDK 17. |
| **Android `minSdk`** | API `21`+ | API `24` | ✅ **COMPATIBLE** | API 24 satisfies Apollo's baseline requirements. |
| **Android `compileSdk`** | API `31`+ | API `34` | ✅ **COMPATIBLE** | Fully meets requirements. |
| **Kotlinx Coroutines** | `1.6.0`+ | `1.8.0` | ✅ **COMPATIBLE** | Apollo 4 coroutine flow and async dispatchers natively support 1.8.0. |
| **OkHttp Integration** | `4.x` (or native HTTP engine) | `4.12.0` | ✅ **COMPATIBLE** | Custom OkHttpClient can be seamlessly passed to `ApolloClient.Builder().okHttpClient(...)`. |

---

## 4. Required Changes (When Transitioning to Implementation)

No changes to the foundational compiler or SDK versions are required. When adoption begins in subsequent phases, only the following project additions will be needed:

1. **Gradle Plugin Registration**:
   - In `gradle/libs.versions.toml`:
     ```toml
     [versions]
     apollo = "4.1.1" # Or 4.0.0

     [libraries]
     apollo-runtime = { group = "com.apollographql.apollo", name = "apollo-runtime", version.ref = "apollo" }

     [plugins]
     apollo = { id = "com.apollographql.apollo", version.ref = "apollo" }
     ```
   - In root `build.gradle.kts`:
     ```kotlin
     alias(libs.plugins.apollo) apply false
     ```
   - In `app/build.gradle.kts`:
     ```kotlin
     plugins {
         alias(libs.plugins.android.application)
         alias(libs.plugins.kotlin.android)
         alias(libs.plugins.apollo)
     }
     ```

2. **Apollo Plugin Configuration Block**:
   - In `app/build.gradle.kts`:
     ```kotlin
     apollo {
         service("anilist") {
             packageName.set("com.canim.app.graphql")
             schemaFiles.from("src/main/graphql/schema.graphqls") // or schema.json
             generateFragmentImplementations.set(true)
         }
     }
     ```

3. **GraphQL Schema & Queries Directory Structure**:
   - Directory: `app/src/main/graphql/`
   - Download/place AniList schema: `app/src/main/graphql/schema.graphqls`
   - GraphQL operation documents: `app/src/main/graphql/*.graphql`

---

## 5. Optional Upgrades That Should NOT Be Performed Yet

To preserve 100% build stability and avoid breaking other parts of the application, the following upgrades must be **strictly avoided**:

1. ❌ **Do NOT upgrade Kotlin to 2.0.x**:
   - **Reason:** CA'NIM uses Jetpack Compose with compiler extension `1.5.8`. Kotlin 2.0 removes the classic `kotlinCompilerExtensionVersion` and requires migrating the entire build to the Jetpack Compose Gradle Plugin (`org.jetbrains.kotlin.plugin.compose`), which is a major, disruptive overhaul completely outside the scope of GraphQL.
2. ❌ **Do NOT upgrade Gradle to 8.7+ or 9.x**:
   - **Reason:** Gradle 8.5 is completely stable with AGP 8.3.2. Upgrading Gradle can break AGP compatibility or trigger deprecation warnings.
3. ❌ **Do NOT upgrade AGP to 8.4+ / 8.5+**:
   - **Reason:** Current AGP 8.3.2 builds Universal APKs and release signing in GitHub Actions without issue.
4. ❌ **Do NOT upgrade to Apollo Kotlin 5.x**:
   - **Reason:** Apollo Kotlin 5 targets Kotlin 2.0+ and Gradle 9. Apollo Kotlin 4.x is the established, fully supported target for Kotlin 1.9.22.
5. ❌ **Do NOT enable `apollo-http-cache`**:
   - **Reason:** `apollo-http-cache` relies on disk cache formatting that on API < 26 requires Java 8 desugaring (`isCoreLibraryDesugaringEnabled = true`). CA'NIM already has a rock-solid, bounded in-memory `CacheManager` with canonical keying.

---

## 6. Recommended Configuration

For maximum resilience, zero regression, and reuse of CA'NIM's proven network configurations:

1. **Client Engine Reuse**:
   - Use `com.apollographql.apollo.network.okHttpClient` extension to bind Apollo to CA'NIM's dedicated `ApiClient.aniListOkHttpClient`.
   - This reuses CA'NIM's 10s connect, 15s read, and 10s write timeouts, isolated connection pool, and User-Agent headers.

2. **Error Handling Architecture**:
   - In Apollo Kotlin 4, `apolloClient.query(...).execute()` returns an `ApolloResponse<D>` containing:
     - `response.data` (parsed strongly-typed models)
     - `response.errors` (list of `com.apollographql.apollo.api.Error`)
     - `response.exception` (captures `ApolloHttpException`, `ApolloNetworkException`, etc., instead of throwing directly)
   - Map these responses directly into CA'NIM's newly established `AniListResult<T>` sealed class to maintain architectural consistency across the repository layer.

3. **Incremental Coexistence**:
   - Apollo code generation can reside under `com.canim.app.graphql`.
   - The existing `AniListClient` and `MediaResolver` can incrementally delegate individual queries (e.g. `MediaDetail`, `Search`, `CastCrew`) to Apollo queries without big-bang regressions.

---

## 7. Risks & Mitigations

| Risk | Impact | Mitigation Strategy |
|---|---|---|
| **Compilation Slowdown from Code Generation** | Moderate | Place GraphQL files strictly in `src/main/graphql/` and enable Gradle build caching. Apollo 4 task avoidance ensures code is only regenerated when `.graphql` files change. |
| **Apollo Model vs UI Model Divergence** | Low | Keep existing domain models (`MediaItem`, `ExtendedMediaDetail`, `MediaRelationItem`) intact; map Apollo generated types into domain models in the repository layer. Never expose Apollo generated classes directly to Jetpack Compose UI. |
| **Schema Size & Introspection** | Low | Download the AniList GraphQL schema (`schema.graphqls`) once into version control (`app/src/main/graphql/schema.graphqls`) rather than fetching dynamically at build time. |
| **ID Namespace Collision** | High (if unaddressed) | Maintain the strictly enforced invariant: never assign MAL IDs to AniList IDs. Apollo's generated queries will enforce typed arguments (`id: Int`, `idMal: Int`), preventing accidental parameter confusion at compile-time. |

---

## 8. Exact Next Steps

### Recommendation Pursuant to Decision Rule:
> **Recommend proceeding without a toolchain upgrade.**  
> Apollo Kotlin 4.x (`4.1.1` or `4.0.0`) is **100% compatible** with CA'NIM's existing toolchain (Kotlin 1.9.22, AGP 8.3.2, Gradle 8.5, JDK 17, minSdk 24, Coroutines 1.8.0, OkHttp 4.12.0).  
> **NO toolchain, Kotlin, Java, AGP, or Gradle upgrades are necessary or recommended.**

### Proposed Sequence for Subsequent Phases:
1. **Phase 1: Dependency & Plugin Addition (Isolated)**
   - Add Apollo 4.x version catalog entries and apply the plugin in `build.gradle.kts` and `app/build.gradle.kts`.
   - Fetch and commit AniList `schema.graphqls`.
   - Run automated unit tests to verify zero impact on existing compilation.
2. **Phase 2: Single Query Proof-of-Concept**
   - Write `.graphql` document for a single query (e.g. `HealthPing` or `ResolveMalId`).
   - Generate models and verify automated unit tests pass.
3. **Phase 3: Incremental Query Migration**
   - Migrate complex queries (`ExtendedDetails`, `Search`, `Discover`) with unit test verification at each step.
4. **Phase 4: Cleanup & Final Verification**
   - Deprecate raw string queries while keeping `AniListResult` and `CacheManager` intact.
