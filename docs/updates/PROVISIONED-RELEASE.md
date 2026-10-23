# CatAI-20 trust-enabled release work

The owner separately authorized creation of the update-metadata signing authority
and supplied its encryption passphrase through secret environment settings.
The real RSA3072 authority has now been generated. Its encrypted private key and
encrypted owner backup are outside the repository, mode0600, with mode0700 working
directory. No private key/password is printed, committed, put in the app or uploaded
to GitHub. The owner must retain the encrypted backup and password separately.

Public SPKI SHA-256:
`8cb2f3d61ba630c526a2b3dd47449f540e1a2666414da6166655eba441147a1f`.
Public manifest: `updates/production-trust-v1.json`.
The APK signer remains the existing certificate
`fc5c0b42b9da40c2507d8f2e77ed92db9e023740aa1a44d1a3b2c14a6e9f4764`.
These are different authorities for different purposes. Android signing configuration
and installation permissions are unchanged.

Branch `feature/ui20-provisioned-release`, rollback
`b51ef53538aa95adfd884329eda9763b63bce413`. CatAI-20 bootstrap version23 contains
only the public metadata authority and a fixed HTTPS feed/host policy. New tests
pin the exact approved fingerprint and reject synthetic test authorities.

The isolated `blackcat-update-feed` branch will contain public signed metadata,
not private keys. APK links use immutable full commits. No live feed or published
APK is claimed before the hosted-byte checks and publication commands succeed.

The native live-feed fixture checks the real Android HTTPS/signature/rollback path.
With a genuinely newer release, it also checks the actual downloaded APK through
apksig and Android manifest inspection, then production export through ContentResolver
after an intercepted test-only file-picker selection. The test provider exists only
in the instrumentation APK, restricts callers by UID/package/signature and disposable
emulator hardware, and cannot be included in a production APK. This fixture does
not represent a physical Files picker or unattended installation.

GitHub Actions secrets access remains HTTP403 over HTTPS. Automated scheduled feed
refresh cannot be represented as active. The publisher creates six-day metadata;
the owner must arrange controlled renewal before expiry. Offline OCR remains a
documentation-only evaluation. Bluetooth/manual/planner/Camera2/Auto behavior and
the OpenAI key vault are preserved.
