<div align="center">

<img src="docs/icon.svg" width="112" alt="ZT Manager icon">

# ZT Manager

**A fast dual pane file manager for Android with a clean archiver style interface
and a full toolbox for power users.**

[![Build](https://github.com/ssfuisu/ZT-Manager/actions/workflows/build.yml/badge.svg)](https://github.com/ssfuisu/ZT-Manager/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/ssfuisu/ZT-Manager?label=release&sort=semver)](https://github.com/ssfuisu/ZT-Manager/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/ssfuisu/ZT-Manager/total?label=downloads)](https://github.com/ssfuisu/ZT-Manager/releases)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](#requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)](#tech-stack)

[Download](#download) | [Features](#features) | [Building](#building-from-source) | [Changelog](CHANGELOG.md) | [Contributing](CONTRIBUTING.md)

</div>

---

## Overview

ZT Manager combines a simple, distraction free browsing experience with the kind of
tools you usually need three or four separate apps for. Browse two folders side by side,
open archives like ordinary folders, edit text and binary files, inspect APKs, share
files over Wi-Fi and much more, all in one lightweight app without ads or tracking.

- Dual pane browser with a breadcrumb path bar, list and grid views
- Native 7-Zip engine: open ZIP, 7z, RAR, RAR5, TAR, ISO and many more
- Built-in text editor, hex editor, image, music, video, PDF and font viewers
- APK tools with on-device APK signing, app manager and split APK installer
- Wi-Fi file sharing through any web browser
- Recycle bin, file encryption, duplicate finder, storage analyzer and terminal
- Light, dark and black themes with seven accent colors
- English and Turkish, following the system language automatically

## Download

Get the latest signed APK from the [Releases](https://github.com/ssfuisu/ZT-Manager/releases/latest) page.

| File | Devices |
| --- | --- |
| `ZT-Manager-vX.Y.Z-arm64-v8a.apk` | 64-bit ARM, almost every phone from the last years |
| `ZT-Manager-vX.Y.Z-armeabi-v7a.apk` | 32-bit ARM, older or low end devices |

Not sure which one you need? Install `arm64-v8a`. If Android reports that the app is not
compatible, use `armeabi-v7a`. Every release also contains a `SHA256SUMS.txt` file.

### Requirements

- Android 8.0 (API 26) or newer
- "All files access" permission, so the app can manage your storage

## Features

### File browser

| | |
| --- | --- |
| Panes | Two independent panes: swipe between them on phones, side by side on tablets and in landscape. Copy or move straight to the other pane. Single pane mode is available. |
| Navigation | Breadcrumb path bar, parent entry, go to any path, bookmarks, recent locations, storage volumes with usage bars, quick access folders, home screen shortcuts for folders. |
| Selection | Tap a file icon to select, select all, invert, select a range. Long press opens the context menu for the item or the whole selection. |
| Operations | Copy, cut, paste, copy or move to any folder, rename, batch rename (pattern with counters or find and replace with regular expressions), new file and folder, delete. |
| Safety | Recycle bin with restore, confirmation before deleting, conflict handling with overwrite, skip, keep both and apply to all. |
| Display | Natural sorting by name, size, date or type, folders first, hidden files, thumbnails for images, videos, PDFs, album art and APK icons, item counts and optional sizes for folders, quick filter. |
| Details | Properties with folder size calculation and media details, permission editor (chmod, optional recursive), checksums, copy path, set date to now, symbolic links, hide from gallery (`.nomedia`). |
| Background work | Every long operation runs as a task with a progress dialog, a notification and a cancel button, and can continue in the background. |
| Root | Optional root mode uses `su` for locations the app cannot access. |

### Archives

Archives open like folders. You can extract everything or just the selected entries,
copy entries out with the clipboard, test the archive for errors and add, rename or delete
files in ZIP archives. Passwords can be remembered and are tried automatically.

| Format | Open and extract | Create |
| --- | :---: | :---: |
| ZIP, JAR, APK (including AES encrypted ZIP) | Yes | Yes, optional AES-256 password |
| 7z (including encrypted) | Yes | Yes |
| RAR, RAR5 (including encrypted) | Yes | - |
| TAR, TAR.GZ, TAR.BZ2, TAR.XZ | Yes | Yes |
| GZ, BZ2, XZ, LZMA, Z | Yes | GZ, BZ2, XZ |
| ISO, CAB, ARJ, LZH, WIM, CPIO, RPM, DEB, CHM, UDF, multi volume `.001` | Yes | - |

Compression level from store to ultra, every selected item into its own archive,
configurable ZIP file name encoding for archives made on other systems.

### Editors and viewers

- **Text editor**: line numbers, syntax highlighting for more than 20 languages, undo and
  redo, find and replace (case sensitive and regex), go to line, word wrap, auto indent,
  encoding detection and selection, save with another encoding, LF or CRLF line endings,
  read only mode. Files up to 16 MB.
- **Hex editor**: pages through files of any size, edit single bytes, go to offset,
  search for text or hex bytes, byte inspector (hex, decimal, octal, binary).
- **Image viewer**: swipe through the folder, slideshow, pinch and double tap zoom,
  rotation, EXIF details including GPS, share, delete and "Set as".
- **Music player**: whole folder as playlist, album art and tags, shuffle and repeat.
- **Video player** and **PDF viewer** with page jump and zoom.
- **SQLite editor**: browse tables page by page, inspect rows and run any SQL statement.
- **Font viewer** with custom sample text in several sizes.

### Apps and APKs

- **APK info**: label, package, version, SDK levels, native ABIs, DEX count, signing
  certificates (MD5, SHA-1, SHA-256), permissions, activities, services, receivers and
  providers, plus a decoded `AndroidManifest.xml`.
- **App manager**: user and system apps, sort by name, size, install or update date,
  launch, backup APK (split apps are saved as `.apks`), share, app settings, uninstall.
- **Split APK installer** for `.apks`, `.xapk` and `.apkm` bundles.
- **APK signer**: sign APKs with v1, v2 and v3 signatures using a key that is generated on
  the device and stored in the Android Keystore, and verify existing signatures.

### Network sharing

**Wi-Fi share** starts a small web server inside the app. Any device on the same network
can open the shown address in a browser to browse folders, download files (with resume
and video seeking), download whole folders as ZIP and upload files to the phone. An
optional password protects the share, and a QR code makes connecting from another
phone instant.

### Tools

| Tool | Description |
| --- | --- |
| Categories | Images, videos, music, documents, archives, APKs, recent and large files from the media index. |
| Search | Recursive name search with wildcards, content search, size and date filters, and find and replace across all results. |
| Folder compare | Compares two folders, optionally with subfolders, and copies missing or changed files in either direction. |
| Storage analyzer | Shows which folders and files use the most space. |
| Duplicate finder | Compares size, partial hash and full hash, then selects all copies but one. |
| Encryption | Password based file encryption (AES-256-CTR, HMAC-SHA256, PBKDF2). |
| Checksums | MD5, SHA-1, SHA-256, SHA-512, CRC32 with compare; create and verify `.md5` and `.sha256` lists. |
| Split and merge | Split large files into `.001`, `.002` parts and join them again. |
| Image converter | Batch convert to JPEG, PNG or WEBP with quality and size limits. |
| Text compare | Line based diff between two files. |
| Text tools | Base64, URL, Hex and Unicode escapes, hashes, JSON format and minify, case and line tools. |
| Terminal | Interactive `sh` or `su` shell with command history. |
| Device info | Model, Android version, CPU, RAM, screen, battery and storage. |

### Personalization and security

- System, light, dark and black (AMOLED) themes with seven accent colors
- Optional PIN lock when the app is opened or resumed
- Language follows the system setting, or pick English or Turkish in the settings

## Permissions

| Permission | Why it is needed |
| --- | --- |
| All files access / storage | Browse and manage files on internal storage and SD cards. |
| Install and delete packages | Install APKs and split APK bundles, uninstall apps from the app manager. |
| Query all packages | List installed apps in the app manager. |
| Internet and network state | Only used by Wi-Fi share for the local web server. |
| Notifications and foreground service | Show progress of long running file operations. |

ZT Manager contains no ads, analytics or trackers and never sends your files anywhere.

## Building from source

Requirements: JDK 17 and the Android SDK with platform 35.

```bash
git clone https://github.com/ssfuisu/ZT-Manager.git
cd ZT-Manager
./gradlew assembleRelease
```

APKs for both ABIs are written to `app/build/outputs/apk/release/`. Without a signing key,
local release builds are signed with the debug key. See [Signing](#signing) to use your own key.

### Project structure

```
app/src/main/java/com/ztmanager
  apk/        APK parsing, binary XML decoder, split APK installer
  archive/    Archive engine (7-Zip, zip4j, Commons Compress)
  data/       Preferences and app lock
  fs/         File operations, recycle bin, hashing, encryption, image and split tools
  share/      Wi-Fi share web server
  task/       Background tasks, progress notification, prompts
  ui/         Jetpack Compose screens (browser, editors, viewers, tools, settings)
```

### Tech stack

- Kotlin and Jetpack Compose with Material 3
- [7-Zip-JBinding-4Android](https://github.com/omicronapps/7-Zip-JBinding-4Android) for reading archives
- [zip4j](https://github.com/srikanth-lingala/zip4j) for ZIP with AES encryption
- [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/) and [XZ for Java](https://tukaani.org/xz/java.html) for creating archives

## Releases and CI

The [Build and Release](.github/workflows/build.yml) workflow builds signed `arm64-v8a`
and `armeabi-v7a` APKs on every push and pull request and keeps them as workflow artifacts.
A GitHub release is published when:

- a tag starting with `v` is pushed, for example `v1.3.0`,
- a pushed commit message contains `[release]`, or
- the workflow is started manually from the Actions tab.

The release notes are taken from the matching section of [CHANGELOG.md](CHANGELOG.md).

To publish a new version:

1. Increase `appVersionCode` and `appVersionName` in `app/build.gradle.kts`.
2. Add a section for the version to `CHANGELOG.md`.
3. Push a `v` tag, or a commit with `[release]` in its message.

### Signing

For a permanent signature, so that new versions install over older ones, add these
repository secrets under **Settings > Secrets and variables > Actions**:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | The keystore file encoded with `base64 -w 0 release.jks` |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password (optional when it equals the keystore password) |

Create a keystore with:

```bash
keytool -genkeypair -keystore release.jks -storetype PKCS12 -alias ztmanager \
  -keyalg RSA -keysize 4096 -validity 10000
```

Without these secrets the workflow signs each build with a temporary key. Such builds
install fine, but a new version cannot be installed over an older one without
uninstalling first. Never commit a keystore to the repository.

## Contributing

Bug reports, ideas and pull requests are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md)
first. Security issues should be reported as described in [SECURITY.md](SECURITY.md).

## Acknowledgements

ZT Manager is built on these open source projects:

| Project | License |
| --- | --- |
| 7-Zip-JBinding-4Android and 7-Zip by Igor Pavlov | LGPL 2.1 with the unRAR restriction |
| zip4j | Apache 2.0 |
| apksig (Android Open Source Project) | Apache 2.0 |
| ZXing core | Apache 2.0 |
| Apache Commons Compress, Commons IO, Commons Lang | Apache 2.0 |
| XZ for Java | Public domain (0BSD) |
| AndroidX and Jetpack Compose | Apache 2.0 |

The unRAR code may only be used to extract RAR archives; it must not be used to create
a RAR compatible archiver.
