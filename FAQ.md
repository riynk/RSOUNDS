# Rsounds FAQ

This FAQ covers the current development branch, `RY-DEV`. Settings and interface labels can change as the project evolves.

## Music library and playback

### Why are some songs missing from the library?

- Check whether the files are supported by Android's media framework.
- Check the app's minimum song-duration filter.
- Confirm that the folder is accessible to the app and that Android has indexed the files.
- Re-scan or refresh the media library, then restart the app if the media index is stale.

### Why do songs appear more than once?

Duplicate entries can be caused by duplicate files or stale Android MediaStore records. Check the source folders first. If the files are not duplicated, refresh the media index using your device's available media-storage troubleshooting steps.

### Which audio formats are supported?

Playback depends on the codecs and formats supported by the Android version and device. Support can therefore vary between devices.

### Can Rsounds stream or download music?

No. Rsounds is intended for music stored locally on the device. Optional online lyric translation is not music streaming.

## Lyrics

### How do I add synchronized lyrics?

Rsounds supports timestamped LRC lyrics. You can use the app's lyrics editor to paste or edit timestamped lyrics, or provide a matching LRC file through the supported local lyrics workflow.

A timestamped LRC line looks like this:

```text
[00:04.20]Example lyric line
```

Use the track title and artist metadata consistently when naming external lyric files so that they can be matched to the correct song.

### What are Replace Cover and Over Cover?

- **Replace Cover** displays the lyrics in place of the cover artwork in the Now Playing experience.
- **Over Cover** displays lyrics over the artwork.

The dedicated lyrics screen is a separate lyrics surface. Lyrics appearance preferences are intended to stay consistent across the supported lyric surfaces.

### Can I adjust lyric appearance?

Yes. The lyrics settings provide controls for text size and line spacing. Bold text is the default lyric weight. Text color follows the default theme text color rather than a separate lyric palette.

### How do I correct lyrics that are early or late?

Use the per-track lyric timing offset to shift that song's lyrics. If the timing gradually drifts during playback, use the drift adjustment. These values are stored per track so that corrections do not unintentionally affect every song.

### How does online lyric translation work?

When enabled, Rsounds requests translations online and displays them below the corresponding lyric lines. You can select a target language and turn translation off when it is not needed. Successful translations are cached to reduce repeated requests.

Translation requires an internet connection and depends on the availability, limits, and behavior of the configured third-party translation service. A failed translation request does not mean local playback or the original lyrics are unavailable.

### How does the blurred lyric background choose artwork?

The background can use available track metadata artwork or artist artwork, depending on the selected source option. If the preferred artwork is unavailable, the app uses its configured fallback behavior. The background setting applies to the Over Cover and dedicated lyrics surfaces; Replace Cover keeps its cover-replacement presentation.

## Appearance and settings

### Where can I change the app appearance?

Open the app's Settings and look for the appearance or personalization section. Exact labels and grouping may change during development.

### Why does an option look different from an older screenshot?

Screenshots in the repository may predate recent UI and settings changes. Use the current build as the source of truth for available options.

## Installation and development builds

### Where can I download a test APK?

Open the [Android CI workflow](https://github.com/riynk/Rsounds/actions/workflows/android.yml), choose a successful run on `RY-DEV`, and download the `Rsounds-debug-apk` artifact. Workflow artifacts expire after 14 days.

### Is the debug APK a production release?

No. The CI artifact is a debug build intended for testing. It is not a substitute for a properly configured and signed release build.

### How can I build Rsounds locally?

Install JDK 21 and a compatible Android SDK, then run:

```bash
./gradlew lint
./gradlew app:assembleNormalDebug
./gradlew app:assembleFdroidDebug
```

See [README.md](README.md) for project structure and additional build details.

## Reporting a problem

Before reporting a bug, check whether it can be reproduced on the latest successful build from `RY-DEV`. Include the device model, Android version, app build, steps to reproduce, expected behavior, and actual behavior. Remove personal data from logs and screenshots before sharing them.
