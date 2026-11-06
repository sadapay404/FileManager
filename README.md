# Moto Files

A Kotlin/Jetpack Compose file manager and gallery, designed for a minimal Motorola-inspired experience. This branch is being developed in gated phases; unfinished destinations are explicitly marked rather than pretending to perform file operations.

## Delivery plan (six phases)

1. **Foundation (complete):** clean Android project, adaptive Material color (including AMOLED), mode switching, Files home, Gallery shell, navigation, storage meter.
2. **Browsing and media (in progress):** permission-scoped MediaStore timeline and albums, SAF folder browser, multi-select, image swipe/zoom viewer; paging, integrated audio/video/document playback and richer memories remain future work.
3. **File operations and trash (in progress):** SAF copy/cut/paste/rename, app-private soft-delete backup, restore, scheduled 30-day purge, confirmed empty-bin, overflow-only hard delete. Archive creation/extraction and folder operations remain outstanding.
4. **Security (in progress):** custom PIN/password/tap-pattern verification, strong-biometric app unlock with in-app fallback, AES-GCM Vault imports and image preview, optional separate credential, organized Settings and saved themes. Credential recovery, gesture-drawn pattern and Vault video/document preview remain future work.
5. **Advanced tools (in progress):** exact duplicate review and safe Trash cleanup, SHA-256/MD5, name/extension/size/tag search, ZIP create/extract, JPEG export, PDF merge/split, temporary read-only Wi-Fi downloads. 7z/TAR, video compression, cache cleaner, uploads and more powerful search are outstanding.
6. **Quality and release (foundation complete):** Gradle wrapper, CI unit test / APK build, format-aware viewers and fallback. Accessibility, adaptive tablet layouts, performance profiling, privacy review and device testing remain release blockers.

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

## Phase 6: format compatibility and release checks

The browser classifies formats using both the SAF provider MIME type and the filename extension. Built-in previews use Coil/Android image decoders (JPEG, PNG, GIF, WebP, HEIC/HEIF and other device-supported image formats), Media3/installed Android decoders (common video and audio containers/codecs), Android PdfRenderer, and a bounded UTF-8 plain-text reader. Gallery video playback now uses Media3. Every file has an **Open with** fallback using a read-only content URI grant, including Office/OpenDocument files, e-books, archives, RAW photos and unusual codecs; availability depends on apps installed on the device. Unknown formats remain accessible, but no application can guarantee decoding *every* format or codec on every Motorola model. An "Open with" button does not imply an installed handler exists.

| Format family | Built in | Fallback |
| --- | --- | --- |
| JPEG/PNG/WebP/GIF, device-supported HEIF/AVIF/RAW | Image viewer | Open with |
| MP4/MKV/WebM/MOV, MP3/AAC/FLAC/OGG and device-supported codecs | Media3 player | Open with |
| PDF | Page renderer, merge, split | Open with |
| UTF-8 text/Markdown/CSV/JSON/XML | Bounded text reader | Open with |
| DOC/DOCX/XLS/XLSX/PPT/PPTX/ODT/ODS/ePub | None | Installed document/reader app |
| ZIP | Create and flat-file extract in Tools | Open with |
| 7z/TAR/RAR and other formats | None | Installed archive app |

CI runs `:app:testDebugUnitTest :app:assembleDebug` (passed on the phase-6 branch). Install an SDK 35 / JDK 17 toolchain locally to reproduce. Manual acceptance tests still required: permission-denied/partial media grant, revoked SAF grants, SD-card providers, empty/large directories, backup/restore failure injection, authentication cancellation, Vault lock on background, PDF and codec failures, landscape/tablet layout and TalkBack. This is a phase-gated prototype, not a security-audited production release.
