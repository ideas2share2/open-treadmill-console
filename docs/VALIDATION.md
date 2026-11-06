# Validation of this source export

The neutral public source was tested independently after package/profile anonymization. No real treadmill, ADB connection or USB device was used during this validation. No raw build or device log is included in the repository.

- `./test.sh`: PASS, including pure protocol/authentication/control/profile/calorie/records/plot/idle suites, additive SQLite migrations, WAL/process-crash recovery and production controller/recorder host tests.
- `./gradlew :app:assembleDebug :home:assembleDebug :app:lintDebug :home:lintDebug`: PASS using the committed wrapper.
- Main app lint:0errors,25warnings. Optional Home lint:0errors,4warnings. Warnings concern dedicated-device/API choices, English UI strings, custom views and existing touch accessibility behavior; they are not physical-safety certification.
- Wrapper distribution SHA256 checked against the official Gradle checksum endpoint.
- `scripts/privacy_scan.py`: PASS. A separate local owner-specific denylist scan also passed; that private denylist and private validation logs are not published.
- Offline legacy response calculator: Python and standalone HTML each matched JDK17 over every synthetic0..999999seed;224 rare Java/community-helper differences documented. No live console or service queries.
- Local Markdown links checked. Dependency/font attribution and license files retained. Source export excludes signing material, APKs, database/account files, photographs and private deployment records.

These checks verify simulated request ordering, data logic and buildability. They do not prove Android touch rendering, actual machine compatibility, physical belt speed/stopping, incline travel, emergency-stop hardware, media service playback or grip heart-rate acquisition. The neutral package names and optional Home must be checked locally with the owner after a separately authorized install.
