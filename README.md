# ZT Manager

ZT Manager is an Android file manager that combines a clean, archiver style
interface with a large set of power-user tools.

## Features

### File browser
- Dual panes: swipe between panels on phones, side by side on tablets and in landscape
- Breadcrumb path bar, parent (`..`) entry, list and grid views
- Copy, cut, paste, move and copy to the other pane, copy and move to any folder
- Rename, batch rename (pattern with counters or find and replace with regex)
- New file and folder, delete with confirmation
- Multi selection by tapping file icons, select all, invert selection
- Sort by name (natural order), size, date or type; folders first; hidden files
- Quick filter for the current folder, go to any path
- Thumbnails for images, videos and APK icons
- Bookmarks, storage volumes with usage bars, quick access folders
- Properties with folder size calculation, permissions editor (chmod)
- Background tasks with progress dialog, notification and cancel support
- Optional root mode (uses `su` for locations the app cannot access)
- Recycle bin with restore, permanent delete and empty
- Categories: images, videos, music, documents, archives, APKs, recent and large files
- Recent locations, range selection, home screen shortcuts for folders
- Accent colors and light, dark and black themes

### Archives
- Browse archives like folders: ZIP, 7z, RAR, RAR5, TAR, GZ, BZ2, XZ, TGZ, ISO, CAB,
  ARJ, LZH, WIM, CPIO, RPM, DEB, JAR, APK and more (native 7-Zip engine)
- Extract here, extract to a named folder, extract to any folder or to the other pane
- Extract selected entries only, copy entries out of an archive with the clipboard
- Password protected archives (ZIP, 7z, RAR)
- Create ZIP (with optional AES-256 password), 7z, TAR, TAR.GZ, TAR.BZ2, TAR.XZ, GZ, BZ2, XZ
- Compression level from store to ultra
- Add files to and delete files from ZIP archives
- Configurable ZIP file name encoding
- Archive integrity test

### Tools
- Text editor: line numbers, syntax highlighting, undo and redo, find and replace
  (case sensitive and regex), go to line, word wrap, auto indent, encoding selection,
  save with another encoding, LF and CRLF line endings, read only mode
- Hex editor: paged viewer for large files, byte editing, go to offset, text and hex search
- Image viewer with swipe, pinch zoom, double tap zoom, rotation, EXIF info and "Set as"
- Music player with folder playlist, album art, shuffle and repeat
- Video player and PDF viewer
- SQLite database editor: browse tables page by page and run SQL statements
- APK tools: package details, SDK levels, ABIs, signatures (MD5, SHA-1, SHA-256),
  permissions and components, decoded `AndroidManifest.xml`, install
- App manager: list user and system apps, launch, backup APK (split APKs as `.apks`),
  share, open app settings, uninstall
- Checksums: MD5, SHA-1, SHA-256, SHA-512 and CRC32 with compare
- File search by name (wildcards) and content
- Storage analyzer showing the largest folders and files
- Duplicate file finder (size, partial hash and full hash comparison)
- File encryption with a password (AES-256-CTR with HMAC-SHA256, PBKDF2 key derivation)
- Split files into parts and merge them again
- Text compare (line diff) for two files
- Terminal with `sh` or `su`

### Languages
The app follows the system language automatically. English and Turkish are included,
and the language can also be changed in the app settings.

## Downloads

Signed release APKs are published on the [Releases](../../releases) page:

- `ZT-Manager-vX.Y.Z-arm64-v8a.apk` for 64-bit ARM devices (most phones)
- `ZT-Manager-vX.Y.Z-armeabi-v7a.apk` for 32-bit ARM devices

Requires Android 8.0 (API 26) or newer.

## Building

```
./gradlew assembleRelease
```

The APKs are written to `app/build/outputs/apk/release/`.

## Continuous integration and releases

The workflow in `.github/workflows/build.yml` builds signed 64-bit and 32-bit release APKs
on every push and uploads them as workflow artifacts. A GitHub release with both APKs is
published when:

- a tag starting with `v` is pushed (for example `v1.0.1`),
- a pushed commit message contains `[release]` (the tag `v<versionName>` is created), or
- the workflow is started manually from the Actions tab.

Bump `appVersionCode` and `appVersionName` in `app/build.gradle.kts` before each release.

### Signing

Release APKs are signed in CI. For a permanent signature (required so that new versions
install over older ones), add these repository secrets under
Settings, Secrets and variables, Actions:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | the keystore file encoded with `base64 -w 0 release.jks` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password (can be left empty if it equals the keystore password) |

A keystore can be created with:

```
keytool -genkeypair -keystore release.jks -storetype PKCS12 -alias ztmanager \
  -keyalg RSA -keysize 4096 -validity 10000
```

When the secrets are not set, the workflow signs the APKs with a temporary key generated
for that run. Those builds install fine, but each release then has a different signature.
Never commit the keystore to the repository.

## License

ZT Manager uses 7-Zip-JBinding-4Android (LGPL 2.1, includes the unRAR restriction),
zip4j, Apache Commons Compress and XZ for Java.
