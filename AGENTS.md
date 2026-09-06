# CA'NIM AI Development Rules & Workflow Guidelines

## 1. Branching Strategy: Debug Branch First
- **Setiap ada perubahan fitur, perbaikan, atau perubahan versionName / versionCode**: Semua perubahan wajib dikerjakan dan dimasukkan ke branch **debug** terlebih dahulu.
- **DILARANG langsung memasukkan perubahan ke main**: Branch main dikhususkan hanya untuk rilis stabil.
- Branch debug akan otomatis memicu GitHub Actions CI untuk menjalankan unit tests, mengompilasi APK Debug, dan mengunggahnya ke **GitHub Actions Artifacts** agar pengguna dapat mengunduh dan menguji coba APK secara langsung di HP.
- Penggabungan ke main (via Pull Request di Web GitHub) hanya dilakukan setelah pengguna menguji coba dan merasa yakin dengan hasilnya di HP fisik.

## 2. Standar Verifikasi & Kompilasi Lokal (Zero Local APK Builds)
- **DILARANG menjalankan ./gradlew assembleDebug atau ./gradlew assembleRelease di mesin lokal**: Kompilasi APK dilakukan secara eksklusif oleh GitHub Actions runners di cloud.
- Verifikasi lokal hanya diperbolehkan menjalankan automated unit tests:
  cmd /c "set JAVA_HOME=C:\Users\KH1ZZ\.jdks\ms-17.0.20.1&& gradlew.bat testDebugUnitTest"
- Seluruh automated unit tests wajib lulus 100% sebelum commit.

## 3. Preferensi Git Pengguna (GUI-First)
- Pengguna lebih memilih mengelola Git menggunakan antarmuka grafis (**GUI**) seperti GitHub Desktop, VS Code Git GUI, dan Web GitHub (Pull Request & Actions) dibanding CLI/terminal.
- AI tidak boleh melakukan git push otomatis; berikan panduan berbasis GUI atau biarkan pengguna melakukan push dan merge sendiri.
