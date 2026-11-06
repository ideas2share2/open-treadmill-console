# Accessing the built-in Android tablet

This is the initial access process, before building or installing a replacement app. Console software and service-access policies vary. These steps describe a known Android-console route; they do not guarantee access on every treadmill.

## 1. Make setup safe and record a baseline

1. Stop the belt using the normal controls.
2. Remove the magnetic safety key. Stay off the belt.
3. Leave the console powered for software setup.
4. Privately record the model number, Android version, app/firmware versions and symptoms. Keep serial numbers, QR registration labels, account screens and household photos out of public issues.
5. Confirm what still works through the OEM manual controls before changing software. A login error, tablet crash and motor/power fault are different problems.

Do not open powered electrical enclosures, bypass the safety key, flash firmware or reset the whole tablet as a first troubleshooting step.

## 2. Enter service / privileged mode where supported

A legacy sequence documented by the [QZ Companion project](https://github.com/cagnulein/QZCompanionNordictrackTreadmill#readme) is:

1. Return to the main iFIT screen.
2. Tap an empty area that does not activate a control **10 times**.
3. Wait **7 seconds**.
4. Tap the same area **10 more times**.

Availability depends on software. Some consoles respond immediately; some show a **Privileged Mode** dialog with a challenge and request a response from customer care. A challenge is not a universal password. Use the manufacturer's supported service/unlock process when one is required. Do not publish a unit's challenge, a response code or account information. Do not assume a reset or an Internet unlock recipe applies to your firmware.

### Community reference for a response-code prompt

The QZ instructions also describe a community route using [getresponsecode.com](https://getresponsecode.com/): enter the current challenge from your own console, then enter the returned response locally on the console. This is a third-party service, not manufacturer support, and compatibility varies. Review its privacy/trust implications first; it should not need an account password, serial number or registration photo. No real challenge or response from a machine is included here.

If the gesture or documented owner-access route is unavailable, stop at that point and ask the manufacturer or a qualified repairer for the correct access route. This repository does not supply a service-code generator, root exploit or OEM firmware image.

## 3. Reach Android Settings

After service access is granted, use the Android navigation controls or launcher to reach the tablet's home screen. Some versions reveal navigation by swiping upward from the bottom edge. Open the Settings gear. Menu placement differs by tablet.

Open **About tablet**. Privately note the Android version and model. This public project targets Android 9; do not apply Android 11 wireless-pairing steps to Android 9 just because a tutorial says “wireless debugging.”

## 4. Enable Developer options and debugging

1. In About tablet, find **Build number**.
2. Tap Build number **7 times** to enable Developer options.
3. Return to Settings. Look under **System → Advanced → Developer options**, or the location provided by the device.
4. Enable **USB debugging** and accept the local confirmation.
5. Install the official [Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools) on the computer.

The official [ADB documentation](https://developer.android.com/tools/adb) explains debugging authorization. Enabling debugging does not authorize treadmill movement.

## 5. Connect only through an authorized debugging route

If the tablet exposes a USB data connection, connect it normally and run:

```sh
adb devices -l
```

Some older consoles already expose authorized ADB over the trusted local network after debugging is enabled. Only if that route is available on your device, find its address in Android's network settings and connect to that exact address:

```sh
adb connect "<TABLET_IP>:5555"
adb devices -l
```

Replace the placeholder locally; do not copy a stranger's address. Do not scan unrelated devices, expose ADB to the Internet, add router port forwarding, or assume port 5555 is available on every console. If TCP debugging is not available, use the supported USB/service method. This guide does not silently enable a network debugging daemon or require root.

On the tablet, accept **Allow USB debugging?** only for your own computer after checking the displayed authorization prompt. For temporary access, leave **Always allow from this computer** unchecked. An `unauthorized` device needs a local approval; changing software repeatedly will not replace that approval.

Target the selected device explicitly when more than one is listed:

```sh
adb -s "<DEVICE_SERIAL>" shell getprop ro.product.model
adb -s "<DEVICE_SERIAL>" shell getprop ro.build.version.release
```

Keep the key removed. These are identification reads, not motor tests. Do not publish the returned serial/address or full diagnostic logs.

## 6. Preserve recovery before changing the console

Record the installed package names and versions privately. Preserve permitted copies of original packages and a supported recovery route. Do not upload OEM APKs or private backups here. Confirm the public app can build and the hardware/protocol assumptions match before installing.

An OEM launcher or watchdog can bring the original app back. Do not respond by broadly disabling/uninstalling packages. Have your assistant identify the specific component and explain a narrow, reversible plan first. Keep working access to Android Settings and the existing launcher until the new app is locally verified.

Read [INSTALLATION.md](INSTALLATION.md) and [AI_ASSISTANT_GUIDE.md](AI_ASSISTANT_GUIDE.md) next. These access steps alone do not make the treadmill compatible with this app.

## 7. End the session

Disconnect the exact ADB connection after work. Turn debugging off or revoke temporary authorizations when they are no longer needed. Preserve your working app signing key and local recovery notes privately. No unattended test or controller write should remain queued.
