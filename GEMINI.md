# CA'NIM Project Rules & Guidelines

## 1. Automatic CI/CD Release on Version Bump
When the user instructs to update/bump version (e.g., "update version", "update versionname", "naikkan versi"):
1. **Update Versions**: Increment `versionCode` and set `versionName = "vX.Y.Z"` in `app/build.gradle.kts`.
2. **Update Documentation**: Update version badges, download table, and latest release notes in `README.md`.
3. **Commit & Tag**:
   - `git add -A`
   - `git commit -m "build: bump version to vX.Y.Z (versionCode N)"`
   - `git tag vX.Y.Z`
4. **Push to Remote**:
   - `git push origin main`
   - `git push origin vX.Y.Z`
5. **Monitor GitHub Actions to Completion**:
   - Query GitHub Actions API to track the triggered `Release` workflow (`Build & Publish Release`).
   - Monitor the job until it reaches `completed` with `conclusion: success`.
   - If any step fails, investigate the workflow log immediately, locate the root error, apply the fix, and re-run.
   - Only report completion once the new release with its APK is published on GitHub.

## 2. STRICTLY NO LOCAL APK BUILDS
- **NEVER** run `./gradlew assembleRelease` or `./gradlew assembleDebug` on the local machine.
- Local APK builds and binaries are prohibited.
- Compilation, minification (R8), packaging, and distribution are 100% delegated to **GitHub Actions**.
- Local command execution is strictly limited to unit tests (`./gradlew testDebugUnitTest`).
