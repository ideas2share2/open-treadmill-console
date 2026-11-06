# Instructions for AI assistants

Read README.md, docs/TABLET_ACCESS.md and docs/AI_ASSISTANT_GUIDE.md before device work.

- This source controls powered motion equipment. Default to offline work and read-only diagnosis.
- No install or controller-app replacement until the owner currently confirms stopped/key-out readiness.
- Never remotely press Start or run unattended motion tests. A person beside the machine operates Start and keeps physical Stop/key reachable.
- Treat displayed/commanded speed separately from physically observed movement. Never claim a physical stop from telemetry alone.
- Do not guess controller fields, bypass safety controls, brute-force service codes, flash firmware or broadly remove OEM packages.
- Preserve recovery, signing keys and existing history. Do not uninstall or clear app data to solve an update problem.
- Do not make media, JavaScript or a remote API a controller bridge. Preserve Stop priority, UI watchdogs, session expiry and no-replay behavior.
- Keep owner names, account details, serials, MACs, IPs, original APKs, signing material, databases, logs and household photos out of public files and commits.
- Use synthetic fixtures for public tests. `./test.sh` must never contact ADB, real USB or a real machine.
- Build and test changes; distinguish simulated behavior from unverified real hardware. Read NOTICE.md and preserve third-party attribution.
