<p align="center">
  <img src="phone/src/main/assets/jellycine_logo.png" alt="JellyCine" width="200">
</p>

<h1 align="center">JellyCine</h1>

<p align="center">
   A Jetpack Compose based client for <strong>Jellyfin</strong> and <strong>Emby</strong> — customized for Android phones.
</p>

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.jellycine.app">
    <img src="https://img.shields.io/badge/Google_Play-Download-34A853?style=for-the-badge&logo=google-play&logoColor=white" alt="Google Play">
  </a>
  <a href="https://github.com/ztlgc/JellyCine/releases">
    <img src="https://img.shields.io/badge/GitHub-APK_Download-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub Releases">
  </a>
</p>

<p align="center">
  <a href="https://www.buymeacoffee.com/Sureshfizzy">
    <img src="https://img.shields.io/badge/Buy_Me_A_Coffee-FFDD00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black" alt="Buy Me A Coffee">
  </a>
  <a href="https://www.patreon.com/c/sureshs/membership">
    <img src="https://img.shields.io/badge/Patreon-FF424D?style=for-the-badge&logo=patreon&logoColor=white" alt="Patreon">
  </a>
</p>

---

## Android 手机定制版

面向 Emby / Jellyfin 普通视频媒体库，保留文件夹导航，提供实际观看时长、打开次数与排行、服务器公共评分，以及手机播放和声音控制。

- [下载正式 APK](https://github.com/ztlgc/JellyCine/releases/latest)
- [2026-11-09 更新说明与校验信息](docs/releases/2026-11-09.md)
- [观看统计口径](docs/SCREEN_TIME.md) · [Android 构建说明](docs/ANDROID_BUILD.md)

观看统计先在本机累计，按服务器和账号隔离；可选开启 Supabase 同步，默认关闭。

## Features

### Playback

- **Custom MPV player** set as default engine — built from a custom fork with HDR10/HDR10+/Dolby Vision support and format badges
- HDR passthrough with dynamic dataspace switching and headroom hints (Android 14+)
- Configurable tone-mapping, rendering quality, and GPU filter settings
- PCM audio gain and limiting for MPV and ExoPlayer, with optional normalization
- Spatial audio capability diagnostics
- Media3 ExoPlayer fallback when MPV cannot render
- Jellyfin FFmpeg extension integration for broad codec coverage
- In-player quality selection, audio transcoding controls, and configurable player cache
- Gesture controls (seek, volume, brightness), lock mode, and start-maximized preference
- Skip Intro button when IntroDB/TheIntroDB markers are available
- Track selection dialog with format badges and AMOLED styling
- Subtitle styling controls with improved track handling
- Google Cast with inline remote playback controls

### Discovery

- Ordinary-video libraries with folder navigation, video playback and cached catalog loading
- **In-app Trailers** with autoplay in feature carousel (capped to 720p on phones)
- Trailers & Extras section on detail screens
- **For You** personalized recommendations with watched feed
- Awards category powered by Wikidata
- Video search across accessible libraries with paged loading, ratings, resolution and duration
- Favorites tab with compact header and view-all navigation

### Seerr Integration

- Discovery, search, recommendations, and detail pages
- Request badges, request limits, and title requests
- Trailer support for Seerr detail items

### Downloads

- Offline downloads with queue, pause/resume/cancel, and persistent state recovery
- **Transcoded download support** with quality picker
- Audio track selection in download quality picker
- Season and series download with storage estimation
- Offline-aware navigation — falls back to downloaded content when network is unavailable

### Screen Time

- Actual foreground playback time and opens for ordinary `Video` items, without requiring completion
- Daily and time-of-day breakdowns, with week, month and year navigation
- Top five videos by watch time and by opens within the selected period
- Local SQLite history isolated by server and account, including offline playback

### Multi-Server & Connections

- Jellyfin and Emby support with automatic endpoint resolution
- Merge-version support with local version selection (no server-side changes required)
- **Discord Rich Presence** via official Social SDK with connection management
- **Admin Panel** with live server info, now-playing sessions, and activity log

### Platforms

| Platform | Status |
|----------|--------|
| Android Phone | Stable |

---

## Screenshots

<div align="center">
  <img src="docs/screenshots/home.jpg" alt="Home" width="30%" style="max-width:200px;min-width:100px;margin:5px" />
  <img src="docs/screenshots/details.jpg" alt="Details" width="30%" style="max-width:200px;min-width:100px;margin:5px" />
  <img src="docs/screenshots/search-immersive.jpg" alt="Search" width="30%" style="max-width:200px;min-width:100px;margin:5px" />
</div>

<div align="center">
  <img src="docs/screenshots/viewall.jpg" alt="View All" width="30%" style="max-width:200px;min-width:100px;margin:5px" />
  <img src="docs/screenshots/settings.jpg" alt="Settings" width="30%" style="max-width:200px;min-width:100px;margin:5px" />
  <img src="docs/screenshots/searchscreen.jpg" alt="Search Results" width="30%" style="max-width:200px;min-width:100px;margin:5px" />
</div>

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.3, Coroutines, Flow |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt + KSP |
| Networking | Ktor Client + OkHttp 5 |
| Images | Coil 3 |
| Player | MPV (primary), Media3 ExoPlayer (fallback) |
| Shared modules | Kotlin common models and Android libraries |

## Project Structure

```
phone/   — Phone app module (Compose UI, navigation, player, settings)
data/    — APIs, repositories, models; multiplatform networking
core/    — Shared player, preferences, and utilities
shared/  — Shared UI components and image infrastructure
```

---

## Getting Started

This checkout is developed for Android phones only. The TV and iOS projects and their platform build configuration have been removed. See [Android build notes](docs/ANDROID_BUILD.md).

### Prerequisites

- Android Studio (latest stable)
- JDK 17
- Android SDK API 37

### Build

```bash
# Android phone (ARM64)
./gradlew :phone:assembleDebug -PandroidAbis=arm64-v8a
```

APK naming: `jellycine-phone-{debug|release}-<version>-<abi>.apk`

---

## Translating

<a href="https://weblate.jellycine.org/engage/jellycine/">
<img src="https://weblate.jellycine.org/widget/jellycine/multi-auto.svg" alt="Translation status" />
</a>

Help translate JellyCine into your language on [Weblate](https://weblate.jellycine.org/engage/jellycine/).

---

## Contributing

Issues and pull requests are welcome. For large feature work, open an issue first to align on scope.

For community discussions and support, see [Discussions](https://github.com/sureshfizzy/JellyCine/discussions).

---

## Privacy

See [PRIVACY](PRIVACY) for the current privacy policy.

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
