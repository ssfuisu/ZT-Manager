# Contributing to ZT Manager

Thank you for helping to improve ZT Manager.

## Reporting bugs

Open an issue with the **Bug report** template and include:

- the app version (Settings > About) and the APK variant (`arm64-v8a` or `armeabi-v7a`),
- the device model and Android version,
- the steps to reproduce the problem and what you expected to happen,
- for archive problems, the archive format and whether it is encrypted.

## Suggesting features

Use the **Feature request** template. Describe the problem you want to solve, not only
the solution, and mention similar apps if they already offer the feature.

## Pull requests

1. Fork the repository and create a branch from `main`.
2. Keep each pull request focused on one change.
3. Follow the existing code style (Kotlin official style, four spaces, no wildcard imports).
4. All user visible text must live in `res/values/strings.xml` with a translation in
   `res/values-tr/strings.xml`. Do not hard code text in Kotlin files.
5. Do not use emoji in code, resources, commit messages or documentation.
6. Make sure `./gradlew assembleRelease` succeeds. The CI workflow builds every pull request.
7. Add an entry to the `Unreleased` section of `CHANGELOG.md`.

## Adding a translation

1. Copy `app/src/main/res/values/strings.xml` to `app/src/main/res/values-<code>/strings.xml`.
2. Translate every string. Keep format placeholders such as `%1$s` and `%1$d` unchanged.
3. Add the locale to `app/src/main/res/xml/locales_config.xml` and to the language list in
   `ui/settings/SettingsScreen.kt`.

## Project layout

See the [project structure](README.md#project-structure) section of the README.
