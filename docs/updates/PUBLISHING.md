# Owner-approved update authority and publishing tools

The user separately approved setup of the update-metadata signing key. This does
not authorize changing the APK signing identity, installation permissions, OCR,
Bluetooth or planner. Work starts at `b51ef53538aa95adfd884329eda9763b63bce413` on
`feature/ui20-provisioned-release`.

## Current prerequisite

The actual production key has not been generated while the owner's encryption
passphrase is unavailable. Add **a secret environment variable** to the Codex
environment attached to this chat:

- Name: `BLACKCAT_UPDATE_KEY_PASSWORD`
- Value: a unique password-manager-generated passphrase, preferably 32+ characters
  (the tool enforces at least 24).

Save the passphrase in the owner's password manager. Do not paste it in chat,
source code, commands, public files, GitHub issues or a network/domain allowlist.
This is a passphrase for encrypting the new signing key, not an OpenAI API key or
the existing Android APK signing credential.

GitHub's Actions secrets API returned HTTP403 over **HTTPS** for the connected
integration. No private key or password was uploaded there. Automated custody/
scheduled refresh cannot be claimed until repository-secret access is arranged
or another owner-controlled signing service is established.

## Tooling

`python tools/update_release.py --help` provides:

- `generate-key`: creates AES256-encrypted PKCS8 RSA3072 private key outside the
  repository; mode0600 file/mode0700 directory; refuses overwrites, repository
  paths, symlinks and missing/short secret passphrases. Writes public SPKI JSON
  and a public fingerprint only. Never changes the APK key.
- `kotlin-trust`: renders public-only source from that public manifest. It is not
  used to populate the app until a real production authority is created.
- `sign`: checks the original APK signer, package, new version, exact AI
  permissions, non-debuggable flag, size/hash and immutable APK URL, then signs
  the app's exact domain-separated metadata protocol. Independently verifies its
  output. Later sequences require prior authentic metadata and cannot roll back.
- `verify`: verifies signature, payload identity, lifetime and strict host policy.
- `publish`: downloads and verifies the actual public APK first, checks that it
  contains the pinned public authority, then writes public signed metadata to an
  isolated `blackcat-update-feed` branch. No private keys/passwords go to GitHub.
  Ref writes are non-forcing; existing feed sequence/version floors are checked.

The proposed feed URL is
`https://raw.githubusercontent.com/efrgdgr0024345/autophone/blackcat-update-feed/updates/ai.json`.
It is **not a live feed until publication succeeds**. APK URLs must use complete
40-hex immutable commits. Both metadata and APK hosts are exactly
`raw.githubusercontent.com`, with no HTTP/query/credential URL fallback.

## Remaining release work

1. Receive the passphrase through secret environment settings; create and verify
   the encrypted real authority, with an owner-controlled backup outside Git.
2. Commit only public trust data; wire it into the checker. Update the tests and
   guards to pin that exact public fingerprint. No test authority fallback.
3. Set CatAI-20/build23, retaining the original APK signer and flavor boundaries.
4. Build and test exact committed source, including real hosted feed/client
   interoperability, lifecycle failures, downgrade/tamper/expiry cases, file
   export and unchanged Bluetooth/planner/Auto behavior. Review the five existing
   CodeQL OpenAI certificate-pinning warnings explicitly.
5. Publish the tested APK bytes, sign their immutable URL, publish/reverify the
   feed, and provide the actual install URL. Manually install this bootstrap APK;
   a subsequent newer version is required to demonstrate an offered upgrade.
6. Establish a controlled refresh job before representing this as a maintained
   update service. Metadata expires after six days (app maximum seven); signing
   again requires an increased sequence, even with an unchanged APK version.

Current tools are tested with explicitly test-only authorities; no production
key, trust-enabled APK, metadata feed or new release is claimed at this stage.
