# Known limits

- This is experimental powered-equipment software, not a universal firmware/console replacement. Hardware safety circuitry remains essential.
- The public renamed build is host-built/tested; it has not been physically validated on another treadmill. Android views, service/USB timing and physical behavior require owner checks.
- Access to Android service/privileged mode depends on OEM firmware. Some versions require manufacturer-assisted unlocking. No universal response code is included.
- Reported/commanded speed is not independent proof of physical motion or stopping. A separate actual-speed field can be unusable.
- Handle heart-rate acquisition remains unconfirmed. Bluetooth strap compatibility and callback behavior vary. A repeated zero pulse is not proof of sensor failure.
- Speaker Bluetooth can operate independently from Android. This app does not claim a verified speaker track/status/disconnect API; use the phone's controls.
- YouTube, YouTube Music and Spotify are third-party web services. Login, ads, playback and DRM can change or fail. There is no Netflix feature or DRM workaround.
- Estimated calories have explicit adult-model and gait/decline approximations. Demographics do not make them precise. See CALORIES.md.
- Power-loss saves are best effort. The last batch can be lost. Recovery does not resume belt motion or count outage time.
- Idle mode is an app-owned black/dim display with media release, not proof of full Android suspend or measured power savings.
- The Home helper preserves a settings entry, but cannot fix every OEM launcher policy or watchdog. It does not authorize disabling OEM safety/system components.
- Different package IDs have different private databases. Keep your signing key and never use uninstall/data-clear as an update shortcut.
