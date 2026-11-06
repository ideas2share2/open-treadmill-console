# Privacy and publication

This is a clean, source-only export for a project identity. It does not include the working machine's old repository/history, original package ID, personal profile names, household photos, real network addresses, unit identifiers, signing material, OEM APK backups, session logs, cookies, workout databases or private deployment notes.

Default profiles are generic slot IDs and display names. The Android application IDs are generic. Production dates follow the tablet's own timezone rather than a hard-coded household timezone. Protocol fixtures use synthetic replies; example personal measurements in tests are synthetic. Font and dependency authors are unrelated third-party licensors whose attribution must remain.

## What a pseudonym does and does not do

A separate project account can reduce public linkage to a real name. It does not guarantee anonymity. GitHub can retain account, network/device and service-usage information, and other people can make inferences from future activity. See [GitHub's privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).

A neutral repository/organization name alone does not hide the account that owns, authors or pushes material. Using a no-reply email does not hide the visible account name. Do not publish through an identifying account and assume a later rename will erase the association. GitHub explains [commit email configuration](https://docs.github.com/en/account-and-profile/how-tos/email-preferences/setting-your-commit-email-address) and the difficulty of [removing sensitive data after publication](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository).

## Before the first public push

- Review the entire exact source tree and fresh commit history, not just the README.
- Run `python3 scripts/privacy_scan.py`. It checks common secret/address/path patterns and forbidden artifacts; it cannot recognize every personal clue or guarantee anonymity.
- Check the publishing account's public name, bio, avatar, email, location, personal links, organizations, followers, stars and prior repositories for links you do not want.
- Use that account's own GitHub no-reply email for both author and committer, and avoid signatures/credentials inherited from a different identity.
- Start with a fresh repository. Do not copy a private `.git` directory, original release archive, build output, ZIP, original APK or signing key.
- Keep public copies source-only until binaries, signing metadata and third-party redistribution requirements have been separately reviewed.
- Keep the account's private email preferences and future browser-authored commits private too. A carefully authored first commit does not control later account behavior.

## Ongoing contributions and issues

Crop and redact screenshots before sharing. Remove reflections, family names, personal accounts, profile details, video account details, registration QR codes and serial labels. Strip image metadata. Prefer synthetic diagrams over household photos.

Do not paste full ADB, USB, browser, Android, build or database dumps into issues. They can contain local paths, serials, MACs, IPs and account/session information. Share a small redacted excerpt and generic compatibility facts. Preserve required third-party copyright notices rather than treating their authors as personal data belonging to the publisher.

Do not star, fork, cross-link, sign or comment from an identifying account if keeping the identities separate matters to you. Avoid unrelated profile pictures or handles reused elsewhere. These practices reduce obvious linkage; they do not provide a guarantee against attribution.

## Local data in the app

Profile details, History and saved browser state remain in the tablet's app sandbox. The console app does not upload workout/profile records to a project server. Media websites still make their own network requests and have their own privacy policies. Optional Bluetooth devices and Android debugging have separate permissions.
