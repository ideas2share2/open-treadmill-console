# Working with an AI assistant

Give the assistant this repository, your exact model and a description of the failure. Keep identifying details local. Read [TABLET_ACCESS.md](TABLET_ACCESS.md) together before doing anything to the tablet.

A useful starting prompt is:

> I own this treadmill and want to assess whether this source can help restore manual use. Start with read-only identification and code review. Do not install, launch a controller app, remove OEM software or send any motion command yet. Explain compatibility and recovery gaps. Keep my serial numbers, account details, photos and network addresses out of the public repository.

## Phase 1: Identify and preserve

Confirm the Android version, machine model, USB controller family, available service/debug route and current app versions. Do not confuse a powered tablet with a working motor controller. A Wi-Fi connection does not prove that the assistant can reach the console.

Make a local inventory and preserve a supported recovery route before any package changes. Never publish original APKs, accounts, cookies, signing keys, full USB captures, logs, unit serial numbers, registration labels or household photos. Record only the minimum non-identifying compatibility information publicly.

## Phase 2: Review and test offline

Use JDK 17 and Python 3 to run `./test.sh`. Build both apps with the pinned wrapper and Android SDK. Inspect model-specific read/write fields, normal session verification, USB ownership and the controller's documented limits. Read [ARCHITECTURE.md](ARCHITECTURE.md) before proposing changes.

The fake-controller tests are useful regression checks. They do not prove that a different physical machine interprets commands the same way. Do not “try a range of opcodes” on a real machine or brute-force modes and settings.

Keep UI/media processing separate from the USB worker. Do not make a web page, remote server, browser script, screen tapper or AI tool responsible for real-time motor safety. Media should never acquire a bridge that can call treadmill controls.

## Phase 3: Read-only controller validation

Have the owner beside the machine, belt stopped and key removed. Establish the declared metadata/session identity before considering motor commands. Reject bad checksums, malformed lengths and unexpected device types. An optional heart-rate field failure must not disable or destabilize the main controller session.

A returned target speed is not an independent measurement of belt motion. This controller family can report an unusable zero in its separate actual-speed field. Never label a missing measurement as a physically verified stop.

Do not guess heart-rate bytes or invent a separate handle-contact signal because zero pulse data is disappointing. The optional grip support is unresolved. Keep that fact explicit.

## Phase 4: Owner-approved installation

Prepare the exact package and rollback/recovery instructions first. Obtain a current owner confirmation that the belt is stopped and the safety key removed before replacing a controller app. Use a normal in-place update with the same package and signing identity to retain data. Do not uninstall or clear data to fix an update error.

The public package IDs are deliberately generic and independent. They are not a migration route for an unrelated app's private history. Schema4 data is not compatible with an older schema3 APK through a blind downgrade.

An installation result proves only that Android accepted a package. Ask the owner to open the app locally with the key still out and check Settings access, profiles, History, media Sources and idle/wake behavior. Do not remotely launch the controller app or press Start as part of an install script.

## Phase 5: A separate supervised physical test

Before any movement test, obtain explicit current readiness from a person beside the treadmill. They must be able to reach the physical Stop and remove the key. They operate Start locally. Begin with an empty belt at the lowest supported setting, then verify physical movement and stopping, key behavior and incline response. Never run such a test with nobody near the machine.

Use the physical Stop/key if a software Stop is not confirmed. Do not repeatedly retry commands after a disconnect, expired session or uncertain state. No queued Start, speed, incline or finish-level request may replay after reconnect.

The on-screen Stop requests a zero incline only after a stopped/key-in state is confirmed. Pause preserves incline. Key removal cancels leveling rather than storing it for later key insertion. Do not change that behavior incidentally while adjusting UI or records.

## Phase 6: Report limits precisely

Separate build/test success, package installation, fresh metadata, accepted commands, displayed readings and owner-observed physical behavior. State which remain unchecked. No host test certifies firmware, emergency-stop circuitry, real speed or an untested machine.

Leave the machine in an owner-confirmed stopped/key-out condition and disconnect debugging after the work. Provide a short local handoff with exact installed version, app-data status, unresolved limits and recovery steps. Exclude that private handoff from public commits.
