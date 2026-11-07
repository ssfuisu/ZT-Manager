# Changelog

All notable changes to ZT Manager are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the
project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.3.0] - 2026-11-07

### Added
- APK signer: sign APKs (v1, v2 and v3) with a key generated on the device and kept in
  the Android Keystore, and verify APK signatures with a detailed report.
- Folder compare: compare two folders (optionally with subfolders) and copy missing or
  changed files in either direction.
- Find and replace in all files found by a content search.
- QR code for the Wi-Fi share address.
- Saved archive passwords: remember passwords and try them automatically.
- Rename files and folders inside ZIP archives.
- Image viewer slideshow and delete.
- Context menu: copy path, open terminal here, hide from gallery (.nomedia), set date to
  now, create symbolic link.
- Thumbnails for PDF files and album art for music.
- Media details (duration, resolution, bit rate, tags) in the properties dialog.
- Optional folder sizes in the file list.
- Batch rename: `{date}` and `{time}` tokens and case conversion.
- Search filters for minimum size and modification date.

## [1.2.0] - 2026-11-07

### Added
- Wi-Fi share: built-in web server to browse, download, download folders as ZIP and
  upload files from any browser on the same network, with optional password.
- Split APK installer for `.apks`, `.xapk` and `.apkm` bundles.
- Batch image converter and resizer (JPEG, PNG, WEBP).
- Create and verify checksum lists (`.md5`, `.sha1`, `.sha256`, `.sha512`).
- Text tools: Base64, URL, Hex and Unicode escapes, hashes, JSON format and minify,
  case conversion and line tools.
- Device info screen and font viewer.
- Compress every selected item into its own archive.
- App manager sorting by name, size, install date and update date.
- Optional PIN app lock.

## [1.1.0] - 2026-11-07

### Added
- Recycle bin with restore, permanent delete and empty.
- Built-in music player, video player and PDF viewer.
- SQLite database editor with table browser and SQL console.
- Duplicate file finder.
- Password based file encryption.
- Split files into parts and merge them again.
- Archive integrity test.
- Categories: images, videos, music, documents, archives, APKs, recent and large files.
- Recent locations, range selection and home screen shortcuts for folders.
- Accent colors.
- Text editor: auto indent, save with another encoding, LF and CRLF line endings.
- Image viewer: EXIF details and "Set as".

## [1.0.0] - 2026-11-07

### Added
- First release.
- Dual pane file browser with archiver style interface, clipboard and batch operations.
- Archive browsing and extraction with the native 7-Zip engine, ZIP with zip4j.
- Archive creation: ZIP (AES), 7z, TAR, TAR.GZ, TAR.BZ2, TAR.XZ, GZ, BZ2, XZ.
- Text editor, hex editor, image viewer, APK info and app manager.
- Terminal, file search, storage analyzer, checksums and text compare.
- English and Turkish translations following the system language.
- Signed 64-bit and 32-bit release builds from GitHub Actions.

[Unreleased]: https://github.com/ssfuisu/ZT-Manager/compare/v1.3.0...HEAD
[1.3.0]: https://github.com/ssfuisu/ZT-Manager/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/ssfuisu/ZT-Manager/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/ssfuisu/ZT-Manager/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/ssfuisu/ZT-Manager/releases/tag/v1.0.0
