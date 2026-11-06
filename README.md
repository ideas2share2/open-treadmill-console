# Open Treadmill Console

An experimental, source-only Android console for owners who want to keep usable treadmill hardware running with a simple manual interface. It includes a guide for working with an AI assistant, starting with access to the built-in Android tablet.

**Start here: [Access the Android tablet](docs/TABLET_ACCESS.md). Then read the [AI assistant guide](docs/AI_ASSISTANT_GUIDE.md).**

This project controls powered exercise equipment. It is a starting point for model-specific, supervised work, not a universal replacement console or a safety certification. Remove the safety key for setup and installation. A person must be beside the machine with the physical Stop/key reachable for any later movement test. An AI assistant must never remotely press Start or run an unattended movement test.

## What is included

- Native manual workout dashboard with local hold-to-start, Pause, Stop, speed, incline and fan controls.
- Four editable profiles; long press a profile name to edit name, age, sex, weight and height. Each slot has its own accent and keeps its History when renamed.
- Local workout History, weekly/monthly comparisons, personal bests, daily streaks and a small quarter-mile track.
- Estimated active calories from saved per-workout weight, speed and incline. Assumptions and limits are in [CALORIES.md](docs/CALORIES.md).
- One optional embedded browser session for YouTube, YouTube Music and Spotify, with a native Sources escape button. Default convenience PIN: **2468**. This gate is not security and never locks treadmill controls.
- Optional Bluetooth heart-rate support and read-only grip-pulse diagnostics. Grip readings remain unconfirmed on the reference controller. Phone-speaker Bluetooth is a separate hardware feature; the app does not pretend it can control that receiver.
- Five-minute stopped-only idle presentation, browser release, dim clock and a first-touch wake guard.
- Batched SQLite history writes and best-effort interruption recovery without restoring belt motion.
- An optional minimal Home/recovery app that can open the console and leave Tablet settings accessible.
- Fake-USB controller tests, synthetic protocol fixtures, recorder tests, migrations and SQLite crash-recovery tests. No real USB, ADB or network access is used by `test.sh`.

## Hardware scope and current status

The starting implementation came from an Android 9 / Argon2-class NordicTrack Commercial 2950, model NTL19221.0, using a FitPro V1 USB controller. The interface looks for USB vendor `0x213c`, product `0x0002`, and performs normal controller metadata/session verification. Other machines and firmware can differ even when their screens look similar.

The public source has neutral package IDs, profile IDs and branding. It has been built and tested on a host computer. **This renamed public build has not been physically validated on another treadmill.** Test results prove simulated software behavior, not real belt speed, stopping distance, incline movement or emergency-stop hardware.

This is an independent project, not endorsed by NordicTrack, iFIT, QZ, YouTube, Spotify or any equipment manufacturer. No OEM firmware, OEM APK, account database or vendor signing material is included. Netflix support is not included.

## Build and tests

Requires JDK 17, Python 3, and Android SDK packages listed in [BUILD.md](docs/BUILD.md).

```sh
./test.sh
./gradlew :app:assembleDebug :home:assembleDebug :app:lintDebug :home:lintDebug
```

The Gradle wrapper has a pinned distribution checksum. Dependencies are fetched from Google, Maven Central and Mozilla. Builders use their own local signing key; no project signing key is supplied. Build artifacts are deliberately excluded from this repository.

Read [INSTALLATION.md](docs/INSTALLATION.md) before installing anything. The public package is `org.opentread.console`; the optional Home package is `org.opentread.home`. A build or install command is never permission to test motion.

## Documentation

- [Android tablet access, from the beginning](docs/TABLET_ACCESS.md)
- [Offline legacy response-code calculator and algorithm](docs/RESPONSE_CODE.md)
- [AI assistant working guide](docs/AI_ASSISTANT_GUIDE.md)
- [Build and tests](docs/BUILD.md)
- [Installation and recovery](docs/INSTALLATION.md)
- [Architecture and controller behavior](docs/ARCHITECTURE.md)
- [Calorie estimates](docs/CALORIES.md)
- [Privacy and publication](PRIVACY.md)
- [Known limitations](docs/LIMITATIONS.md)
- [Validation of the source export](docs/VALIDATION.md)
- [Third-party notices](NOTICE.md)

## License

Project code is offered under the [MIT License](LICENSE). Third-party software and fonts retain their own licenses; see [NOTICE.md](NOTICE.md) and the notices in `app/src/main/assets/licenses/`.
