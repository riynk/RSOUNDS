# Contributing to Rsounds

Thank you for considering a contribution to Rsounds. This repository is an Android local music player and is developed on the `RY-DEV` branch.

## Before making a change

- Check existing issues and recent commits for related work.
- For a substantial feature, architecture change, or refactor, open an issue or discussion first to confirm the intended direction.
- Keep changes focused and avoid unrelated formatting or dependency updates.
- Do not create forks or duplicate repositories as part of the project's normal development workflow; coordinate changes against the original repository when you have permission.

## Bug reports

A useful bug report should include:

- Device model and Android version.
- Rsounds build or commit, preferably from `RY-DEV`.
- Clear reproduction steps.
- Expected result and actual result.
- Relevant logs or screenshots, with personal information and secrets removed.

Search existing issues before opening a new report. Submit one distinct problem per issue where practical.

## Feature requests

Describe the user problem, expected behavior, relevant screens or settings, and any constraints. Screenshots or short recordings can help explain UI behavior. Avoid prescribing a large implementation before the behavior has been discussed.

## Development environment

- JDK 21.
- Android SDK compatible with compile SDK 36.
- Use the checked-in Gradle wrapper.
- The primary modules are `:app` and `:appthemehelper`.

Useful checks:

```bash
./gradlew lint
./gradlew app:assemble
./gradlew app:assembleNormalDebug
./gradlew app:assembleFdroidDebug
```

## Architecture and implementation guidelines

- The app uses Kotlin and Java, XML layouts, Fragments, ViewBinding, AndroidX, Material Components, and Gradle Kotlin DSL. Follow established patterns in the affected area.
- Keep flavor-specific dependencies and implementation in `app/src/normal` or `app/src/fdroid`. Shared code in `app/src/main` must not depend on classes that exist only in one flavor.
- Use the existing dependency injection, repository, and ViewModel patterns rather than adding ad-hoc service construction.
- Keep disk, database, MediaStore, network, and expensive artwork work off the main thread.
- Keep playback, service callbacks, notifications, widgets, and Android Auto responsive.
- Put user-visible strings in Android resources and preserve localization conventions.
- If a Room schema changes, provide and register a proper database migration.
- Keep dependency versions in `gradle/libs.versions.toml`.

## Product scope

Rsounds is a local music player. Do not add music downloading or hosted music-streaming behavior without an explicit product decision. Optional network-dependent features, such as lyric translation, must fail gracefully and must not block local playback.

Retro Music may be used as a reference for user-visible behavior where appropriate, but contributions to Rsounds must be implemented independently. Do not copy source code from another project.

## Pull requests

- Target the `RY-DEV` branch unless maintainers specify otherwise.
- Explain the problem and the approach.
- List relevant build or lint checks and their results.
- Include before/after screenshots for UI changes when available.
- Call out known limitations, migration requirements, or external service dependencies.
- Keep credentials, signing files, API keys, and local property files out of commits.

## License

By contributing, you agree that your contribution is distributed under the project's [GNU General Public License v3.0](LICENSE.md). Please follow the repository's [Code of Conduct](CODE_OF_CONDUCT.md).
