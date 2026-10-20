# UI17 — local task search and live plan cards

UI17 is a physical-test candidate, version code 21 (`17.0-search-streaming`).

[Install CatAI-17.apk](install/CatAI-17.apk) · [Offline CatBT-17.apk](install/CatBT-17.apk)

[New search/streaming screens](search-stream-screens/README.md) ·
[Command workflows](workflows-screens/README.md) · [Lessons](learning-screens/README.md) ·
[Plan/camera/Auto screens](screens/README.md) · [Layout matrix](layout-matrix/README.md)

## Behavior

Search accepts task phrases such as “I want to check all internet connections”.
It searches the eligible catalogue for the visible OS scope, lists matching topics,
and paginates command/tool references. Exact names and common task intents are ranked
locally. The index covers 467 curated cards, 3,448 CLI workflows and 824 tool/package
groups. Kali retains all 820 documented groups and 3,055 command workflows. These
counts describe the bundled snapshot, not installed programs on a particular computer.

The search OS initially follows Settings' target. Changing Search OS affects this
library session only. Kali-source-only references stay in Kali scope; general Linux
references and explicit distribution/package-manager requirements are handled separately.
Installed packages, shell and hardware still need checking through the guides. Queries
are not logged or saved. Catalogue indexing runs off the UI thread and is reused;
Unicode-aware normalization avoids Android regex matcher allocation during cold loading.

Interactive AI plan requests and text-feedback updates show a labelled live draft:
explanation paragraphs, roadmap phases and separate monospace command cards. Progress
identifies the step being drafted. The view follows new steps until you scroll; Follow
latest step resumes it. Auto/Voice and Cancel stay outside the content scroll. Edit goal
cancels an active request before reopening the input. Native progress avoids rapid flashing;
private model reasoning events are not displayed.

Partial output never enters the usable plan or a sender. Completion must match the
received stream and pass the unchanged strict proposal parser and command checks. The
reader bounds wire/event/text size, JSON depth and retained draft paths. Large 40-step
streams have space for token-event metadata without relaxing command or preview bounds.
Failure/cancel/backgrounding discard the draft, retain the prior plan and reject late
callbacks. Manual feedback replacements still need Apply. Auto's existing foreground
execution policy and photo-analysis wire path are retained.

## Validation

- 197 Android unit tests; 94 policy/catalogue mutation tests; 40 planner contracts;
  one artwork decode test. Frozen Bluetooth/manual/camera/vault/transport checks passed.
- Both variants built; zero Lint errors; exact permissions, original identity,
  non-debuggable packaging, licences and signatures verified. No test-key or camera-bridge
  marker strings in shipped DEX.
- CodeQL covered all 58 Kotlin source files, including tests:
  zero security findings and 30 quality notes. Triage is in
  [checks/codeql-triage.json](checks/codeql-triage.json).
- 83 native emulator checks and 121 real PixelCopy captures from the exact
  downloadable CatAI APK, including 320/360/390/412 dp and 100%/200% text. The expanded
  workflow checks prepare all 820 Kali groups and 3,055 commands without executing
  inventoried programs. Ordinary selection, browsing and plan edits send no keys.
- One paid live host OpenAI SSE request with a synthetic goal returned two commands/two
  phases: first text 3.63 seconds, complete 13.63 seconds. The production serializer and
  final production stream/parser replay passed. [Live metadata](live-openai/README.md).
  This is separate from emulator fixtures; no key was logged, saved or shipped.

Native planner/observer/HID providers are synthetic; Camera2 captures are real emulator
JPEGs. These checks do not prove a continuous live phone-camera/OpenAI/Bluetooth loop.
Physical pairing/reconnection, optics, real typing/Enter, voice playback/dictation, IME
and hardware Keystore acceptance remain required. No baseline promotion or merge.

## Approximately 20 MB on-device model

A compact embedding model could improve paraphrase/multilingual result ranking. It
would retrieve catalogue entries rather than generate commands. Download size excludes
runtime, tokenizer, vector index and working RAM; accuracy and latency need phone
benchmarks before adding it. UI17 uses deterministic English task retrieval and adds no
model download, neural runtime, dependency, permission, endpoint or model-version upgrade.

## Provenance

APK source: `9c1738a32721fcc9970a2795b6c81bb72bb5e297`. Rollback: `e109a68589ab4822b58d7871d4a6425fceb17359`.

CatAI SHA-256: `2607ba4985e06372bdcb6ca4029e330631f45c927acdc217950599bf078d4bdc` (6,490,241 bytes).

CatBT SHA-256: `6417e0ba478a32f08ed051e2dc14b5cb13aa96829e51da94d9b44c170a966c5e` (6,097,893 bytes).

Original signer SHA-256: `fc5c0b42b9da40c2507d8f2e77ed92db9e023740aa1a44d1a3b2c14a6e9f4764`.

[Machine-readable verification](VERIFICATION.json) · [Install/checksums](install/README.md) ·
[Scope and test plan](../UI17-SEARCH-STREAMING-TEST-PLAN.md).
