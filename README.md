# Moto Files

A Kotlin/Jetpack Compose file manager and gallery, designed for a minimal Motorola-inspired experience. This branch is being developed in gated phases; unfinished destinations are explicitly marked rather than pretending to perform file operations.

## Delivery plan (six phases)

1. **Foundation (complete):** clean Android project, adaptive Material color (including AMOLED), mode switching, Files home, Gallery shell, navigation, storage meter.
2. **Browsing and media (in progress):** permission-scoped MediaStore timeline and albums, SAF folder browser, multi-select, image swipe/zoom viewer; paging, integrated audio/video/document playback and richer memories remain future work.
3. **File operations and trash (in progress):** SAF copy/cut/paste/rename, app-private soft-delete backup, restore, scheduled 30-day purge, confirmed empty-bin, overflow-only hard delete. Archive creation/extraction and folder operations remain outstanding.
4. **Security (in progress):** custom PIN/password/tap-pattern verification, strong-biometric app unlock with in-app fallback, AES-GCM Vault imports and image preview, optional separate credential, organized Settings and saved themes. Credential recovery, gesture-drawn pattern and Vault video/document preview remain future work.
5. **Advanced tools (in progress):** exact duplicate review and safe Trash cleanup, SHA-256/MD5, name/extension/size/tag search, ZIP create/extract, JPEG export, PDF merge/split, temporary read-only Wi-Fi downloads. 7z/TAR, video compression, cache cleaner, uploads and more powerful search are outstanding.
6. **Quality and release:** accessibility, adaptive layouts, animations, tests, background-work reliability, performance, privacy review and CI.

## Architecture target

```
app/src/main/java/com/moto/files/
  navigation/             app-level destinations and mode state
  ui/theme/               dynamic color and explicit theme modes
  ui/components/          reusable expressive UI primitives
  ui/files/               file browsing and selection
  ui/gallery/             media timeline, albums and viewer
  data/files/             SAF tree adapter
  data/trash/             recycle metadata and purge worker (phase 3)
  data/security/          credentials and encrypted vault (phase 4)
  domain/                 use cases, validation, and operation results
  tools/                  opt-in advanced utilities (phase 5)
```

Android Studio: open the repository, use JDK 17 and Android SDK 35, sync Gradle, then run `app`. Media read permission is requested only when opening Gallery. Files browsing uses Android’s folder picker and retains read access only to the selected tree. The storage meter reports filesystem usage; it does not scan personal files. No write or deletion functionality is exposed yet.

## Phase 3 safety notes

Trash copies selected SAF files into app-private storage before requesting deletion from the provider. Restore requires the original folder grant to remain valid. WorkManager checks expiry at launch and periodically (scheduling is inexact on Android). These copies are not encrypted; sensitive material belongs in the planned vault, not Trash. Directory operations and MediaStore gallery deletion are not yet implemented. Android may revoke provider permissions; failures are shown instead of silently reporting success.

## Phase 4 security boundaries

Vault import encrypts a **copy** using a credential-derived AES-256-GCM key; it does not silently remove the original. Vault always asks for a credential, even after biometric app unlock. BiometricPrompt offers an explicit in-app credential button, never Android device credential. The current pattern is a sequence of six or more tapped points, not a drawn gesture. App backup and screenshots are disabled. Credentials cannot currently be changed or reset; forgetting one makes the corresponding data inaccessible. Vault index filenames and Trash contents are not encrypted. This phase requires on-device security review before handling sensitive real-world data.

## Phase 5 tool limits

Tools operate in one selected SAF folder (not the entire device). Duplicate detection hashes matching-size files and only moves extra copies to Trash after explicit confirmation; it does not erase caches or residual files. ZIP extraction accepts flat paths only and limits sizes/counts; partial extraction may remain on error. PDF split creates separate page files; protected documents may fail. Wi-Fi sharing is read-only, bound to the active Wi-Fi IPv4 address and closes when leaving the screen or backgrounding. It uses unencrypted HTTP and an ephemeral bearer link: use only on trusted networks. No FTP or upload service is claimed.
