# CatAI signed update checks

Status: implemented verifier; **production trust is unprovisioned**. No production
metadata keys were generated, no signing configuration changed, no installation
permission added. This is source for review, not an enabled update channel.

Starting point/rollback: `bd7f38295404721c984ede7813a363a46ae02aba` on
`feature/ui19-evidence-driven-auto`; its APK app source is
`173946bb1172cea930dfd78ed032967b022d8fcf`. Work is isolated on
`feature/ui20-authenticated-updates`. Existing version code/name are retained;
no new release APK or baseline promotion is part of this task.

## What the user sees

Settings → Check for updates → a dedicated foreground screen. Opening Settings
or that screen makes no network request. An explicit check fetches only bounded
metadata, when trust has been provisioned. A newer authenticated release enables
**Download & verify APK**, followed by **Save verified APK…**. The latter uses
Android `ACTION_CREATE_DOCUMENT`, requiring no storage or installation permission.
The app never opens an installer. The user can subsequently open the saved file
themselves in Files and decide whether to install through Android's UI.

Currently the screen explains that secure updates are not configured. Its Check
button returns the same explanation without contacting any host. Download and
Save stay disabled. There is no unsigned/manual URL bypass or use of an OpenAI key.

Backgrounding cancels checking/downloading and discards the private APK. A deliberate
file-picker round trip preserves that one checked file, revalidates it on return,
and never resumes a network transfer. Activity/process recreation discards it.
Cancel revokes the in-memory offer and disconnects the request. No background
worker, boot receiver, retry queue, polling, Bluetooth calls or command sends exist.

## Trust that still must be provided

`ProductionUpdateTrust.configured()` returns **null**, not a sample key. Tests
generate ephemeral RSA authorities in test memory; none are production anchors.
To enable updates, a separate reviewed provisioning change must supply:

1. An independently authenticated **public** metadata-verification key, key ID and
   fingerprint, with ownership/revocation/rotation arrangements. RSA 3072–8192 bits,
   X.509 SPKI public encoding, fixed `SHA256withRSA` verification (PKCS#1 v1.5).
   Private metadata keys stay outside the app/repo/cloud task. This task creates none.
2. An HTTPS metadata URL, exact metadata and APK redirect host sets, and the reviewed
   channel (`testing`, for example). No endpoint or wildcard trust is inferred from
   an existing download link. Query-bearing CDN/signed URLs are deliberately unsupported.
3. A publishing process that signs the exact final APK's size/hash, package, version,
   flavor, channel and original signer; manages monotonically increasing release
   sequences; refreshes expiry within seven days; and preserves immutable artifacts.
4. A bootstrap app build containing these reviewed anchors, then live valid/invalid
   service, download, Android file-picker, and physical update-install acceptance.

Downloaded metadata cannot add roots or redirect allowlists. Rotation of metadata
keys or the APK signer requires a separately reviewed trust update. A new APK signer
is rejected even if Android would accept a rotation lineage. No placeholder host,
key or test authority can make the production build report a verified update.

## Wire contract

The HTTPS response is an ASCII JSON object with exactly two string fields:
`payload`, `signature`. Both are canonical padded standard Base64 (no whitespace
inside the values). Maximum envelope size: 16,384 bytes. Payload is limited to
12,000 Base64 characters; signature to 1,400. The signed message is the literal
ASCII bytes `BlackCat signed update v1`, followed by one NUL byte, followed by the
**exact decoded payload bytes**. No JSON reserialization occurs before verification.
Do not sign the Base64 spelling, JSON envelope or a digest string instead.

Decoded payload is a flat ASCII JSON object with exactly these fields:

| Field | Type / meaning |
| --- | --- |
| `schema` | Integer `1` |
| `key_id` | String selecting an already provisioned public key |
| `package` | String, exact running application ID |
| `flavor` | String `ai`; offline update networking is unsupported |
| `channel` | String matching provisioned channel |
| `sequence` | Positive integer release sequence |
| `issued`, `expires` | Integer UTC epoch seconds; positive, expires after issued, lifetime ≤604800 seconds |
| `version_code` | Integer 1–2100000000, matching APK; download must be newer than installed |
| `version_name` | 1–64 ASCII letters, digits, dots, underscores or hyphens; exact APK match |
| `apk_size` | Integer 1–104857600, exact byte count |
| `apk_sha256` | 64 lowercase hex characters, digest of exact APK bytes |
| `signer_sha256` | 64 lowercase hex characters, digest of original DER signing certificate |
| `apk_url` | Restricted absolute HTTPS URL |

Field order and ordinary JSON whitespace may vary. No duplicate/unknown fields,
nesting, escaped strings, non-ASCII, numeric strings, leading-zero numbers,
decimals, exponents, booleans or nulls are accepted. String values cannot contain
backslashes or embedded quotes. Integers have at most 18 digits. The parser does
not coerce types. Algorithm selection is not controlled by downloaded data.

## Download and APK checks

Metadata and APK have **separate exact host sets**. Every initial URL and redirect
must use HTTPS, lowercase exact host spelling and port 443/default; no userinfo,
query, fragment, percent escapes, backslashes, double-slash or dot path components.
Paths use only ASCII letters/digits and `/._~-`. At most two redirects (301/302/303/
307/308), with loops blocked. No credentials or cookies are added by this client;
the app does not install a global cookie handler. Default Android TLS certificate
and hostname verification are retained; no permissive trust manager or verifier.
Only HTTP 200, identity/no content encoding, bounded bodies and exact declared
content length are accepted. Partial responses, HTTP downgrade, forbidden hosts,
oversize, short reads and errors fail. Connect timeout 5s, read timeout 10s,
monotonic per-fetch deadline 90s (an in-progress blocking read can add up to 10s).

After an explicit download into private, non-backed-up storage:

1. Require exact signed APK size and SHA-256.
2. Run Google `apksig:8.7.3` cryptographic verification across API28 through this
   device's API level; require verification with APK v2 or v3 and exactly one signer.
   Certificate extraction alone is **not** considered signature verification.
3. Independently inspect Android's parsed package, long version code, version name,
   current signer and requested permissions. Both signature parsers must agree.
4. Require metadata and manifest to match the running package and **current** signer,
   newer version and exact installed permission set. This also blocks replacing the
   AI build with the same-package/signer offline build, and blocks silent new permissions.
5. Recheck private-file bytes, identity, expiry and durable rollback floor immediately
   before export. Hash/count copied bytes and recheck again after export. Failure
   removes the private download and attempts to delete incomplete selected output.

The added library is AI-only and pinned to the existing AGP toolchain version;
there is no SDK/Gradle/Kotlin/model migration. Offline has neither these classes,
the verifier dependency nor Internet/camera/microphone additions. Manifests retain
the exact prior permission sets; the new UpdateActivity is non-exported. No
`REQUEST_INSTALL_PACKAGES`, provider, broad storage access or silent install exists.

## Rollback, replay and failure state

After authentication, identity, freshness and floor validation, a synchronized
Android AtomicFile persists highest accepted metadata sequence, version, exact
payload SHA-256 and last wall time in `noBackupFilesDir`. It fsyncs and reads the
record back before accepting the offer. A failed APK download does not lower this
floor; retrying the **identical** authenticated metadata remains possible. Reusing
a sequence with different payload bytes is rejected. Publisher corrections need a
higher sequence and cannot lower the highest accepted version.

The installed version independently prevents downgrade. Corrupt, oversized or
incomplete first-write state blocks updates rather than silently resetting. Signed
metadata must be unexpired, issued no more than 300s in the future, and device time
cannot move backwards by more than 300s from persisted time. Rechecked at download,
verification and export; network failures never return “up to date”.

Limits: app-local state is not a hardware monotonic counter. Clearing app data,
uninstallation or privileged filesystem rollback removes/restores its history;
installed-version protection still applies after a normal data clear. A compromised
phone, trusted signing authority, or substantially wrong device clock is outside
the network-attacker guarantee. Expiry bounds a freeze window; it cannot prove the
server offered the newest release. A file provider can alter an exported copy;
verification guarantees the bytes written by this app, not permanent control of a
user-selected external document. Android still checks package signatures on install.
No claim is made that an unprovisioned channel has been tested live.

## Test record

See [EVIDENCE.json](EVIDENCE.json) and accompanying logs for exact tested source,
APK digests, public signing continuity, unit/emulator checks and trust blockers.
Test fixtures are explicitly separate from production trust. No production keys,
API keys, keystores or passwords belong in that evidence.
