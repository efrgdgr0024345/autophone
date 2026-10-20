# UI16: full Kali workflow coverage

All **820 Kali tool/package groups** and **3,055 Kali command entries** now have
usable workflow mappings. The broader Linux directory covers all 824 groups and
3,448 CLI entries. The 467 curated command cards, 35 task lessons and original
212 cards remain separately counted and preserved.

## Using the expanded library

Open **Kali Linux → All Kali command workflows** to browse every command through
40-item pages. Search within the directory, open a command, then choose its guide
or **Build a task command**. The tool directory offers command-specific guides and
package inspection. All 38 package-only groups have local version/status/file
workflows; no executable is invented for libraries, firmware or metapackages.

A command guide connects executable availability, the correct local manual,
a bounded manual view, and an observed help invocation when one is supplied by
the source. For example, `git add` locates `git` and reads `git-add` documentation.
It does not treat the manual name as a runnable command. A tool's installed
version and syntax must be checked on the actual computer.

The task builder preserves each field as one literal argument, including empty
values, spaces and apostrophes. An optional explicit sudo prefix appears in the
preview. Programs can interpret their own arguments and may change state or
contact network targets; prepared tasks therefore start at HIGH risk. Existing
printable-ASCII/512-character typing limits apply. Review opens the exact command
and current destination; it neither types nor executes anything. Back/close/review
clear editor fields synchronously, with no persistent draft or command log.

From the AI library picker, **Add complete guide to plan** inserts the sequence
atomically, validates the full batch and preserves the 40-step limit. A first
blank goal is set before insertion so its change handler cannot discard the
new steps. Stale callbacks cannot change a revised plan or clear a newer library.
Imported plans can use the existing reviewed text/voice/photo feedback flow.

Ordinary typing stays one-use TYPE ONLY without Enter. Unattended Auto retains
its separate supported command subset; this catalogue expansion does not widen
that policy. No frozen HID/manual/key/camera/transport code, permissions, SDKs,
dependencies or model pins changed. No shell is executed on Android and catalogue
browsing makes no network request.

## Data and evidence

- [Plain JSON](../../catalog/offline-catalog.json), [schema](../../catalog/offline-catalog.schema.json),
  [manifest](../../catalog/MANIFEST.json), [catalogue guide](../../catalog/README.md).
- `workflow_support.coverage` states the exact Kali and whole-catalogue counts.
  Tool and command workflow records reference existing IDs and source metadata.
- Native Android and JVM checks generate guides/prepared values for every Kali
  entry. Semantic validation recomputes all mappings and rejects missing coverage,
  substituted executables/help, changed discovery commands or new Auto permission.
- Host tests run production Kotlin-prepared arguments through real Bash and
  compare exact output, including apostrophes, spaces, shell symbols and empty
  strings. The command-substitution marker is never created. Three read-only
  local package inspections are also checked, with bounded output views.

Coverage is preparation/workflow support, not proof that all listed programs are
installed, globally safe or execution-tested, nor a complete expert cookbook for
every application. The source snapshot remains 1,871 pinned online documents plus
separately labelled local Bash help. Real tool behavior, keyboard layout, phone
camera/voice/IME/Keystore and Bluetooth pairing/reconnection need physical testing.
No paid API requests were needed for this offline expansion.

The emulator's initial package-service startup failure occurred before app install.
A later native run found the posted-dismiss draft-clearing issue; its correction
is included. Those failed attempts are not counted as passed checks.

## Downloads and final validation

- [CatAI-16.apk](install/CatAI-16.apk): full offline library plus existing AI/Auto features.
- [CatBT-16.apk](install/CatBT-16.apk): offline alternative; install one flavor at a time.
- [Install details](install/README.md), [source and checksums](install/SHA256SUMS.txt),
  [machine-readable verification](VERIFICATION.json).
- [New workflow screens](workflows-screens/README.md), [learning screens](learning-screens/README.md),
  [existing workflow captures](screens/README.md), [width/font matrix](layout-matrix/README.md).

APK source: `11b82c595fddec9904717a2b865daa42b8d77600`; version code 20,
`16.0-full-kali-workflows`, original signer. The published APKs are byte-for-byte
copies of the installed candidates; they were not rebuilt to change filenames.

All 180 Android unit tests (153 AI / 27 offline), 83 preservation/UI mutations,
11 catalogue mutations, 40 planner contracts, artwork and catalogue integrity
checks passed. Both builds, packaged permissions/identity/licenses/assets and
signatures passed. Lint: zero errors, 72 AI / 26 offline warnings. CodeQL includes
all 53 app Kotlin files: zero security findings and 26 quality notes. The CLI's
extra denominator file is the historical unbuilt host helper under UI14.2 docs.
Shipping DEX excludes synthetic key prefixes, test image bridges and cloud key names.

The exact APK passed **71 native emulator checks**: 11 full-Kali workflow checks,
11 learning-library checks, 22 existing workflow/Auto checks, one timeout and
26 width/font/navigation/icon checks. Galleries contain **108 actual PixelCopy
captures**. The new command-builder matrix includes the longest Kali command
name, 320/412 dp widths, 100/200% text and a reduced-height viewport. Existing
plan/Auto screens cover 320/360/390/412 dp at 100/130/200% text.

The Auto regression uses real emulated Camera2 JPEG capture and synthetic
vision/planning/HID providers. It completed two-page collection, reconciliation,
a remaining-plan revision, seven simulated Enter operations and cleanup.
These results are separate from live OpenAI or physical phone-to-computer testing.
No paid API requests were made in this update. Native cold-open timing is retained
as software-emulator data, not a claim of phone performance.
