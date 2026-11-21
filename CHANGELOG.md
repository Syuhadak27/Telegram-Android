# TubeGram Changelog

## 13.2.6 (7307)

### Added
- Numeric **ID row** on user/group/channel profiles — tap to copy.
- Automatic update notification popup from GitHub Releases (once per day).
- Horizontal **progress bar** in the APK download dialog.

### Fixed
- Liquid Glass UI now enables the required chat blur flag so the native glass
  pipeline actually activates; rebuilds visible screens when toggled.

## 13.2.5 (7306)

### Added
- **Liquid Glass UI** — new toggle in *Settings → Chat Settings → Nekogram Features*.
  Reuses Telegram's native blur3 render-node glass pipeline (real background blur on
  supported devices) for the floating bottom navigation, folder tabs, composer and
  conversation header. Chat list ActionBar receives a frosted glass surface with
  specular rim and highlight. The switch stays in sync with the existing Lite Mode
  liquid glass option.
- New reusable `LiquidGlassHelper` rendering utility with cached glass resources.

### Changed
- Bumped version to 13.2.5.

## 13.2.4 (7305)

### Added
- In-app **Check for Updates** backed by GitHub Releases (download + install APK).
- GitHub Action now publishes the signed APK to a GitHub Release on every push.
- CI build caching: Gradle build cache, SDK components, AGP cache and ccache.
- Toggle to hide your own phone number on your profile.
- **Copy ID** action in user/chat profile menu.

### Fixed
- "Hide All Chats" tab initial selection mismatch on cold start.
- Cloud language packs re-enabled so non-bundled languages (e.g. Indonesian) work.

## 13.2.3 (7304)

### Added
- Nekogram-style features: Show/Copy Message ID, Hide Forwarded Label,
  Hide Join/Leave Messages, Forward Without Quote.
- All launcher, notification and VoIP icons replaced with the new TubeGram icon.

### Changed
- Rebranded intro screen and version strings to TubeGram.
- Disabled official update checks.

## 13.2.2 (7303)

- Base upstream Telegram Android 13.2.2.
