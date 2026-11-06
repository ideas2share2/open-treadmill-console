# Building and testing

## Requirements

- JDK17 (set `JAVA_HOME` if it is not the default).
- Python3 and Bash for the host tests.
- Android SDK with build-tools36.0.0 and platform Android37.2, plus its licenses accepted through the SDK manager.
- An SDK location supplied through `ANDROID_HOME` or a private, ignored `local.properties`.
- Network access to the pinned Gradle distribution and Google/Mozilla/Maven dependency repositories.

Exact toolchain and dependency versions are in `toolchain.json`. The Gradle9.8.2 wrapper verifies the distribution SHA256. Android Gradle Plugin9.5.0, AndroidX Core1.19.1 and ARM64 GeckoView159 are pinned. The target/minimum is Android9/API28; the high compile SDK is a build requirement, not the tablet's required Android version.

## Host checks

```sh
./test.sh
```

Tests use temporary directories and synthetic fixtures. The script fixes its own test timezone to a synthetic daylight-saving zone; production follows the tablet's local timezone. Tests cover protocol framing, authentication, Start/Pause/Stop ordering, key gates, interrupted reads, expiry/no replay, UI responsiveness, idle/wake guards, records, profiles, calories, chart bounds and SQLite migrations/crash recovery.

The response-code check uses only synthetic inputs and JDK17 as a reference. Optional `node tests/response_code_js_test.mjs` also checks the standalone HTML script.

`./test.sh 3` repeats the fake USB worker scenarios three times. Test stubs are never packaged in the Android apps. Python database tests create temporary synthetic databases only.

## Android packages

```sh
./gradlew :app:assembleDebug :home:assembleDebug :app:lintDebug :home:lintDebug
```

Outputs are normally under `app/build/outputs/apk/debug/` and `home/build/outputs/apk/debug/`. `home` is optional for normal launcher use. For an external build directory, pass `-PconsoleBuildRoot=<absolute-build-directory>` and optionally `--project-cache-dir <cache-directory>`.

The debug build uses the builder's own local Android debug signing key. Release builds are not configured with a shared private key. Keep one consistent private signing key if you intend to update an installed copy without losing access to its data. Do not commit that key or upload private APKs.

## Scope of validation

The neutral source export was host-tested and both modules built/linted. Lint includes warnings for English-only UI strings, dedicated-console choices and custom views. This is not an Android emulator or physical treadmill certification. Package renaming, launcher behavior, display, media, Bluetooth and actual motion need separate owner checks.

## Redistribution

Do not add generated build reports or logs to source commits: they can include local filesystem paths. Keep font and interoperability licenses. A build can fetch third-party binaries into ignored caches; that does not mean they belong in the source handoff. See NOTICE.md.
