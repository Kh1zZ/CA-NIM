import http from 'http';
import fs from 'fs';
import path from 'path';

const PORT = 3000;
const HOST = '0.0.0.0';

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(`<!DOCTYPE html>
<html lang="id">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>CA'NIM - Android Native Project</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;600&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg: #080808;
      --card: #121212;
      --card-elevated: #1a1a1a;
      --border: #262626;
      --accent: #00FF66;
      --mal-blue: #2E51A2;
      --text-pri: #F5F5F5;
      --text-sec: #9E9E9E;
      --font: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, sans-serif;
      --mono: 'JetBrains Mono', monospace;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: var(--bg);
      color: var(--text-pri);
      font-family: var(--font);
      min-height: 100vh;
      padding: 24px 16px;
      display: flex;
      flex-direction: column;
      align-items: center;
    }
    .container {
      max-width: 900px;
      width: 100%;
    }
    .header-badge {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 6px 14px;
      background: rgba(0, 255, 102, 0.1);
      border: 1px solid rgba(0, 255, 102, 0.35);
      border-radius: 999px;
      color: var(--accent);
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 0.5px;
      margin-bottom: 16px;
    }
    .pulse-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--accent);
      box-shadow: 0 0 10px var(--accent);
    }
    h1 {
      font-size: 28px;
      font-weight: 800;
      letter-spacing: -0.5px;
      margin-bottom: 8px;
      background: linear-gradient(135deg, #FFF 40%, var(--accent) 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }
    p.lead {
      color: var(--text-sec);
      font-size: 15px;
      line-height: 1.5;
      margin-bottom: 24px;
    }
    .grid-2 {
      display: grid;
      grid-template-columns: 1fr;
      gap: 20px;
      margin-bottom: 24px;
    }
    @media (min-width: 768px) {
      .grid-2 {
        grid-template-columns: 1fr 1fr;
      }
    }
    .card {
      background: var(--card);
      border: 1px solid var(--border);
      border-radius: 14px;
      padding: 20px;
    }
    .card h3 {
      font-size: 16px;
      font-weight: 700;
      margin-bottom: 12px;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .spec-item {
      display: flex;
      justify-content: space-between;
      padding: 8px 0;
      border-bottom: 1px solid rgba(255,255,255,0.06);
      font-size: 13px;
    }
    .spec-item:last-child { border-bottom: none; }
    .spec-label { color: var(--text-sec); }
    .spec-val { font-weight: 600; color: #FFF; font-family: var(--mono); }
    .code-box {
      background: #000;
      border: 1px solid var(--border);
      border-radius: 8px;
      padding: 12px;
      font-family: var(--mono);
      font-size: 12px;
      color: #79c0ff;
      line-height: 1.6;
      overflow-x: auto;
      margin-top: 10px;
    }
    .steps {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .step {
      display: flex;
      gap: 12px;
      align-items: flex-start;
    }
    .step-num {
      width: 24px;
      height: 24px;
      border-radius: 50%;
      background: var(--accent);
      color: #000;
      font-weight: 800;
      font-size: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      margin-top: 2px;
    }
    .step-text {
      font-size: 13px;
      color: var(--text-sec);
      line-height: 1.4;
    }
    .step-text strong {
      color: #FFF;
      display: block;
      margin-bottom: 2px;
    }
    .tree-box {
      background: #000;
      border: 1px solid var(--border);
      border-radius: 10px;
      padding: 16px;
      font-family: var(--mono);
      font-size: 12px;
      color: #A0AEC0;
      line-height: 1.5;
    }
    .tree-folder { color: #60A5FA; font-weight: 600; }
    .tree-file { color: #E2E8F0; }
    .tree-tag { color: var(--accent); font-size: 11px; margin-left: 6px; }
  </style>
</head>
<body>
  <div class="container">
    <div class="header-badge">
      <div class="pulse-dot"></div>
      100% ANDROID NATIVE • KOTLIN + JETPACK COMPOSE
    </div>

    <h1>CA'NIM - Android Native Project</h1>
    <p class="lead">
      Project ini adalah <strong>100% Android Native murni</strong>. Seluruh arsitektur dibangun menggunakan <strong>Kotlin</strong>, <strong>Jetpack Compose (Material 3)</strong>, <strong>Room Database SQLite</strong>, dan <strong>OAuth2 PKCE MyAnimeList</strong>.
    </p>

    <div class="grid-2">
      <div class="card">
        <h3>🚀 Tech Stack Android Native</h3>
        <div class="spec-item">
          <span class="spec-label">Bahasa Pemrograman</span>
          <span class="spec-val">Kotlin 1.9.22</span>
        </div>
        <div class="spec-item">
          <span class="spec-label">UI Framework</span>
          <span class="spec-val">Jetpack Compose M3</span>
        </div>
        <div class="spec-item">
          <span class="spec-label">Local Database</span>
          <span class="spec-val">Android Room 2.6.1 (SQLite)</span>
        </div>
        <div class="spec-item">
          <span class="spec-label">Network & HTTP</span>
          <span class="spec-val">Retrofit 2.9 + OkHttp 4</span>
        </div>
        <div class="spec-item">
          <span class="spec-label">Auth & Security</span>
          <span class="spec-val">AndroidX Security Crypto</span>
        </div>
        <div class="spec-item">
          <span class="spec-label">State Management</span>
          <span class="spec-val">ViewModel + StateFlow</span>
        </div>
        <div class="spec-item">
          <span class="spec-label">Build System</span>
          <span class="spec-val">Gradle Kotlin DSL (.gradle.kts)</span>
        </div>
      </div>

      <div class="card">
        <h3>📱 Cara Buka & Build di Android Studio</h3>
        <div class="steps">
          <div class="step">
            <div class="step-num">1</div>
            <div class="step-text">
              <strong>Export / Sync ke GitHub</strong>
              Klik tombol Export / Push to GitHub di menu Google AI Studio.
            </div>
          </div>
          <div class="step">
            <div class="step-num">2</div>
            <div class="step-text">
              <strong>Buka di Android Studio</strong>
              Buka Android Studio, pilih <em>File > Open</em>, lalu pilih folder project ini.
            </div>
          </div>
          <div class="step">
            <div class="step-num">3</div>
            <div class="step-text">
              <strong>Build & Jalankan</strong>
              Android Studio akan otomatis mendeteksi modul <code>:app</code>. Klik tombol Run (▶) atau jalankan:
              <div class="code-box">./gradlew assembleDebug</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="card" style="margin-bottom: 24px;">
      <h3>📁 Struktur Direktori Android Native</h3>
      <div class="tree-box">
        <span class="tree-folder">/ (Project Root)</span><br>
        ├── <span class="tree-folder">app/</span> <span class="tree-tag">[Modul Utama Android]</span><br>
        │   ├── <span class="tree-file">build.gradle.kts</span> <span class="tree-tag">(Config dependensi Compose, Room, Retrofit)</span><br>
        │   └── <span class="tree-folder">src/main/</span><br>
        │       ├── <span class="tree-file">AndroidManifest.xml</span> <span class="tree-tag">(Deep link canim://oauth/callback)</span><br>
        │       ├── <span class="tree-folder">java/com/canim/app/</span><br>
        │       │   ├── <span class="tree-file">MainActivity.kt</span> <span class="tree-tag">(Entry point Compose Activity)</span><br>
        │       │   ├── <span class="tree-folder">data/local/</span> <span class="tree-tag">(Room DB, DAOs, EncryptedSharedPreferences)</span><br>
        │       │   ├── <span class="tree-folder">data/remote/</span> <span class="tree-tag">(Retrofit Jikan & MAL API)</span><br>
        │       │   ├── <span class="tree-folder">data/repository/</span> <span class="tree-tag">(MAL OAuth2 PKCE Manager)</span><br>
        │       │   ├── <span class="tree-folder">ui/screens/</span> <span class="tree-tag">(Dashboard, Library, Search, Settings)</span><br>
        │       │   ├── <span class="tree-folder">ui/viewmodel/</span> <span class="tree-tag">(CanimViewModel & State)</span><br>
        │       │   └── <span class="tree-folder">ui/theme/</span> <span class="tree-tag">(Cyber Dark AMOLED theme)</span><br>
        │       └── <span class="tree-folder">res/</span> <span class="tree-tag">(Icons, Drawables, Mipmaps, Strings)</span><br>
        ├── <span class="tree-folder">gradle/</span><br>
        │   └── <span class="tree-file">libs.versions.toml</span> <span class="tree-tag">(Version Catalog)</span><br>
        ├── <span class="tree-file">build.gradle.kts</span> <span class="tree-tag">(Root build script)</span><br>
        ├── <span class="tree-file">settings.gradle.kts</span> <span class="tree-tag">(Root settings, include(":app"))</span><br>
        └── <span class="tree-file">README.md</span>
      </div>
    </div>
  </div>
</body>
</html>`);
});

server.listen(PORT, HOST, () => {
  console.log(`Android Native Preview Server running at http://${HOST}:${PORT}`);
});
