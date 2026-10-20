# UI15 offline Kali and Linux learning candidate

Kali Linux now has an offline menu, a Kali-filtered tool directory, and readiness
lessons. The existing task categories remain. Guided lessons connect purpose,
exact command, expected evidence, troubleshooting, verification and the next step.
Previous/Next changes the explanation only. Tool-discovery guides link an exact
executable availability check, local manual summary and a help invocation actually
observed in the upstream documentation, when available.

The plain [catalogue JSON](../../catalog/offline-catalog.json),
[schema](../../catalog/offline-catalog.schema.json) and
[manifest](../../catalog/MANIFEST.json) are separate downloadable files.
[Catalogue documentation](../../catalog/README.md) explains counting, provenance,
fields, learning paths and reproducible generation.

| Item | Count |
| --- | ---: |
| Distinct documented CLI invocations | 3,448 |
| Tool/package groups | 824 |
| Example command cards | 467 |
| Distinct example command lines | 465 |
| Linked lessons | 35 |
| Lesson steps | 184 |
| Learning paths | 5 |
| Retrieved online source documents | 1,871 |

The CLI total separates 3,133 binary names, 262 subcommands and 53 builtin-only
entries. Eight more Bash builtin names overlap external binaries. Service units
and manual-only topics have separate reference counts. This is a bounded snapshot,
not all Linux software or every possible argument combination. Tool references
are not proof of installation, tested execution or new Auto permission.

JSON includes source commits/hashes, supplied tool versions, architectures,
package dependencies, sizes, metapackages, options and help examples. Command
cards include compatibility, risk, privilege, prerequisites, network effects,
verification, troubleshooting, cleanup and lesson links. Lesson steps have explicit
success/next and error/stop transitions. A representative practice sequence is:

`unused path → private folder → sample → bounded reads → filter → copy → compare → exact cleanup`

The 212 original command cards and all original category data are retained.
Browsing runs from a bundled gzip asset without downloads, accounts or API calls.
Lists of thousands of references use 40-item pages; data loads off the main thread.
The black cat appears on the root page and is hidden above operational details.

The catalogue does not execute a shell on Android. Existing one-use library
approval is TYPE ONLY and does not append Enter. Full Auto behavior, transport,
camera/key storage, permissions, SDKs, dependencies and model pins are unchanged.
This update does not add persisted lesson progress or automatically bind lesson
transitions to camera observations. Those are later integration opportunities.

## Research and checks

Official Kali documentation and eight upstream families supply the online data;
Bash builtin help is separately labelled local-binary evidence. Direct Kali/GNU/man7
websites were blocked by the cloud proxy; reachable official repositories were
used. Optional documentation-domain additions were saved in the environment draft
and require publishing the environment to become active.

The emulator caught an initial packaging bug: AAPT strips `.gz` and unpacks its
content. The loader now opens an opaque `.catalog` asset; APK inspection verifies
both compressed and public JSON hashes. This failure and its correction are kept
in the verification history. Source-only JSON/unit tests would not catch it.

Only 32 selected text/Git practice operations were executed in isolated Linux
workspaces. The whole inventory was not installed or executed. Native Android
checks use a disposable API31 software emulator; physical Bluetooth, actual
keyboard layouts, phone optics/playback/dictation and Keystore still require handset
acceptance. No paid API calls were needed for this offline update. No baseline is
promoted, no PR is merged and previous APK bytes remain unchanged.

## Install and validation

- [CatAI-15.apk](install/CatAI-15.apk): AI/Auto plus the offline library.
- [CatBT-15.apk](install/CatBT-15.apk): offline alternative; install one variant at a time.
- [Install details](install/README.md), [checksums](install/SHA256SUMS.txt),
  [machine-readable verification](VERIFICATION.json).
- [New learning screens](learning-screens/README.md),
  [existing workflow captures](screens/README.md),
  [full width/font matrix](layout-matrix/README.md).
- [Learning design and proposed next integrations](../../catalog/LEARNING-DESIGN.md).

APK build source: `4e7ed2e60b5a44bab96bdf29decdba4e62d1d457`.
Version code 19, `15.0-offline-kali-learning`, original signer.
The later learning screenshot fixture only waits for scrolling to settle; the
app APK bytes are unchanged. Fixture SHAs are recorded per scenario in verification.

All 167 unit tests, 83 policy mutations, seven catalogue mutations, 40 planner
contracts, artwork decoding and catalogue integrity checks passed. Both APK
permission/identity/license/asset inspections passed; shipping DEX excludes test
image bridges, synthetic key prefixes and cloud secret names. Lint: zero errors,
70 AI / 24 offline warnings. CodeQL: all 50 app Kotlin files extracted, zero security
findings and 24 quality notes. The CLI's extra denominator file is the historical
unbuilt host helper in `docs/ui14.2/live-openai`, not missing app source.

The exact APK passed 60 native checks: 11 learning, 22 existing workflow/Auto,
one timeout and 26 width/font/navigation/icon checks. The galleries contain 98
actual PixelCopy captures. The new lesson matrix covers 320/412 dp at 100/200%;
the existing flow matrix covers 320/360/390/412 dp at 100/130/200%.
Cold-open timing is retained as a software-emulator measurement, not phone performance.
The Auto fixture uses real Camera2 JPEGs with synthetic vision/planning/HID; it is
separate from physical phone-to-computer or live OpenAI evidence.
