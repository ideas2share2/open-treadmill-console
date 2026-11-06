# Installation and recovery

Read TABLET_ACCESS.md and AI_ASSISTANT_GUIDE.md first. The owner must confirm the belt is stopped and safety key removed before an installation. No remote movement test belongs in this procedure.

## Before installation

1. Verify the exact machine/Android/controller compatibility privately.
2. Preserve a supported recovery route, permitted original package backups and working Settings/launcher access.
3. Build and test this source locally. Keep your signing key outside the repository.
4. Confirm you are targeting the intended authorized ADB device.
5. Have the owner confirm stopped/key-out readiness again if the machine has been used since preparation.

## Main app

After that explicit authorization, a local installer can use:

```sh
adb -s "<DEVICE_SERIAL>" install -r app/build/outputs/apk/debug/app-debug.apk
```

The package ID is `org.opentread.console`. Do not replace the placeholder with a guessed device. `-r` preserves existing data for the same package/signing identity. A differently signed update will fail; uninstalling to make it pass deletes access to that app's data and is not the default recovery action.

A package with a different application ID has a separate data sandbox. This public package does not automatically import another console app's private History or browser credentials. Database schema4 uses additive migrations, but older schema3 builds cannot be blindly reinstalled after schema4 opens.

Have the owner open the app locally with the key still removed. First startup may create or migrate its local database and verify its controller session. Check the profile editor, known History, Sources escape and five-minute stopped-only idle/wake behavior without starting a workout. Missing weight leaves calorie estimates unavailable; add weight locally before a later workout.

## Optional Home/recovery app

The `home` module is `org.opentread.home`. It has no network or USB permission and no motor code. It opens the main console once for a Home event and retains buttons for manually opening the console or Android Settings after a failure. It is not a substitute for restoring the original supported launcher.

Only after the main app and recovery route are locally checked, the owner may separately authorize installation of `home/build/outputs/apk/debug/home-debug.apk` and choose it through Android's supported default-Home interface. Do not force a Home component or disable an OEM launcher when Settings access is uncertain. No generic vendor-package removal list is supplied.

## OEM app or watchdog returns

Some tablets have a service that restarts the original UI or competes for USB. Identify its exact role and side effects before proposing a narrowly reversible change. Do not broadly uninstall packages, remove firmware, stop a motor controller service or disable a safety component. Keep original package names/versions and recovery instructions private and local.

## Storage or update failures

Read the error and verify the current package/data state. Do not clear application data or uninstall it. Keep build caches and release archives off the tablet where possible. Normal Android disposable-cache management is different from deleting app data; any cleanup still needs a scoped plan and owner authorization.

## After work

Disconnect the exact ADB connection. Leave the key removed and no commands queued. First motion testing is a separate supervised operation with a person beside the machine using local Start and physical Stop/key. See the AI assistant guide.
