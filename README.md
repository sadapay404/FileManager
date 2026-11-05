# Moto Files — Material Expressive File Manager & Gallery (Motorola / Hello UI)

Kotlin + Jetpack Compose + Material 3 app with a dual-mode shell:

- **Files mode** — quick-access categories, storage analyzer bar, multi-select with
  cut/copy/paste/rename, archive (ZIP/7z/TAR), hashes, and a Recycle Bin
  (30-day auto-purge, restore / empty-now).
- **Gallery mode** — Photos / Albums / Memories tabs, date-sorted grid, swipe viewer
  with pinch-to-zoom.
- **Security** — custom in-app PIN/Password/Pattern lock with brute-force lockout;
  native biometric prompt whose cancel/"Use PIN" routes back to the app's own PIN;
  AES-256-GCM hidden vault with shared or separate credential.
- **Themes** — Light, Dark (charcoal), AMOLED (true black), System.
- **Tools** — duplicate finder & cleaner, Wi-Fi HTTP sharing (Wi-Fi-interface-only bind,
  per-session token), photo/video compression, MD5/SHA-256, PDF merge/split,
  tags + smart search.

## CI
`.github/workflows/build-apk.yml` compiles `assembleDebug` and `assembleRelease` on
GitHub Actions (JDK 17 + Android SDK 35) and uploads the APKs as artifacts on every
push to `arena/b3aff871-filemanager`.

## Local build
JDK 17 + Android SDK 35, then `gradle :app:assembleDebug` (or open in Android Studio).
