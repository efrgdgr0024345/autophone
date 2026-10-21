## UI19 test download — evidence-driven diagnostics

Full direct APK download URL:

https://raw.githubusercontent.com/efrgdgr0024345/autophone/b79b9e357ec0f3327f9021e78d8f251bbc88da7f/docs/ui19/install/CatAI-19.apk

Natural task explanations, explicit incomplete-output handling, stable task findings
and automatic diagnostic plan revision. Auto uses positive diagnostic argument
contracts; configuration changes remain manually reviewed. Remembered phone API keys
stay in Settings and are reused across plans. [Voice sample](https://raw.githubusercontent.com/efrgdgr0024345/autophone/b79b9e357ec0f3327f9021e78d8f251bbc88da7f/docs/ui19/live-openai/narration-sample.wav).

Source: `173946bb1172cea930dfd78ed032967b022d8fcf`. SHA-256: `3e42faac46d7a658c94e72553bce1564efebc699253639bf553f6bdacf7c357f`.
213 Android unit tests, 105 Python checks, 40 planner contracts, both builds/Lint/APK
checks and CodeQL (zero security findings). The unchanged UI passed 88 native baseline
checks; 28 affected Auto regressions and the Linux/OCR loop ran on final bytes.
Five bounded live API checks with final decoder replay. [Full evidence and screenshots](docs/ui19/README.md).
Physical phone/Bluetooth/optics/audio acceptance remains; no baseline promotion or merge.

# Black Cat AI Remote

Android phone remote that preserves the proven Bluetooth HID keyboard/mouse core while adding a phone-first Linux AI planner and a fully offline Linux command library.

> **UI17 is a physical-test candidate.** Automated checks passed; real Android-to-computer Bluetooth acceptance is still required before baseline promotion.

## UI17 test download — natural language search and live plan cards

Full direct APK download URL:

https://raw.githubusercontent.com/efrgdgr0024345/autophone/ebd126d85c1bdae3c15dbd599445a8bb63c39a11/docs/ui17/install/CatAI-17.apk

Search tasks such as “I want to check all internet connections” locally across
the eligible catalogue for the selected OS. Results show matching topics and
paginated command/tool references. Search OS initially follows the target setting;
its visible chooser changes this library session only. No model download or API key
is required for search. The 820 Kali groups and 3,055 command workflows are retained.

Interactive AI plan and text-feedback requests now stream into explanation,
roadmap and monospace command cards. Progress identifies the current draft step;
scrolling pauses following, and Follow latest step resumes it. Auto/Voice and Cancel
stay visible. Partial drafts become usable only after final stream consistency and
strict plan validation; failure or cancellation keeps the prior plan. Existing
foreground Auto execution policy, photo analysis, Bluetooth and permissions are retained.

All **197 unit tests and 83 native emulator checks** passed, with 121 actual
screenshots, zero Lint errors and zero CodeQL security findings (30 quality notes).
A paid live host OpenAI stream passed with a synthetic two-command plan: first text
3.63 seconds, completion 13.63 seconds. Emulator providers are synthetic; physical
phone/computer and continuous live camera/Bluetooth acceptance remain required.

A roughly 20 MB embedding model could improve paraphrase ranking; runtime, memory,
licensing and phone benchmarks need evaluation before integration. UI17 adds no neural
runtime, dependency, permission, endpoint or model-version upgrade.

Version code 21, original signer; APK source `9c1738a32721fcc9970a2795b6c81bb72bb5e297`.
SHA-256: `2607ba4985e06372bdcb6ca4029e330631f45c927acdc217950599bf078d4bdc`.
[Install/offline alternative](docs/ui17/install/README.md), [verification](docs/ui17/README.md),
[new search/streaming screens](docs/ui17/search-stream-screens/README.md)
and [complete layout matrix](docs/ui17/layout-matrix/README.md). Prior APKs are preserved.

## UI16 test download — full Kali workflows and command preparation

Full direct APK download URL:

https://raw.githubusercontent.com/efrgdgr0024345/autophone/c8d2d738feb99073af6661bb766905b9b0cb712f/docs/ui16/install/CatAI-16.apk

Expanded plain JSON catalogue:

https://raw.githubusercontent.com/efrgdgr0024345/autophone/11b82c595fddec9904717a2b865daa42b8d77600/catalog/offline-catalog.json

All **820 Kali tool/package groups and 3,055 Kali command entries** now have
workflow mappings. Open **Kali Linux → All Kali command workflows**, search
within the paginated list, then use its guide or literal argument builder.
Subcommands use their backing executable and own manual. The 38 package-only
groups get installed-version/status/file inspection; no executable is invented.
Complete guides can be added atomically to a local AI plan with no API request.

Argument values are quoted individually, an optional sudo prefix is explicit,
and prepared tasks open HIGH-risk exact-command review. Ordinary TYPE ONLY and
Auto's separate supported command subset are retained. The existing 467 curated
cards and 35 task lessons remain available; the full workflow mappings are
counted separately. Catalogue browsing and preparation work offline.

All 180 unit tests, 94 policy/data mutations, 40 planner contracts and 71 native
emulator checks passed. Lint: zero errors. CodeQL: zero security findings, 26
quality notes. Production-prepared literal arguments passed independent Bash
checks, alongside three local package inspections. The inventory was not all
installed or executed; target availability and physical phone/Bluetooth behavior
still need acceptance.

Version code 20, original signer; APK source `11b82c595fddec9904717a2b865daa42b8d77600`.
SHA-256: `a1048f88c6e8cfdc31f9a4d4162b6906d3e9feb6bed0316b6ccfcec616ed8820`.
[Install/offline alternative](docs/ui16/install/README.md), [verification](docs/ui16/README.md),
[new screens](docs/ui16/workflows-screens/README.md), [learning gallery](docs/ui16/learning-screens/README.md)
and [complete layout matrix](docs/ui16/layout-matrix/README.md). Old APKs are preserved.

## UI15 test download — offline Kali and linked Linux lessons

Full direct APK download URL (no ZIP):

https://raw.githubusercontent.com/efrgdgr0024345/autophone/ae9d55436b450aa878432c59917f110840bd039c/docs/ui15/install/CatAI-15.apk

Plain JSON catalogue, with totals and source metadata:

https://raw.githubusercontent.com/efrgdgr0024345/autophone/4e7ed2e60b5a44bab96bdf29decdba4e62d1d457/catalog/offline-catalog.json

Kali now has a dedicated offline menu. The expanded library includes **3,448
CLI invocations, 824 tool/package groups, 467 command cards and 35 lessons with
184 linked steps**. Guided tool discovery connects availability, local documentation
and observed help; lessons link prerequisites, expected results, verification,
troubleshooting and cleanup. Browsing uses bundled data with no API/account requirement.

Research covers 1,871 pinned online documents plus separately labelled Bash builtin
help. These are documented snapshot counts. The [catalogue guide](catalog/README.md),
[learning design](catalog/LEARNING-DESIGN.md) and [JSON manifest](catalog/MANIFEST.json)
explain provenance, counts, reproducible generation and later camera/voice integration.

All 167 unit tests, 90 policy/catalogue mutations, 40 planner contracts, 60 native
emulator checks and 32 selected Linux practice operations passed. Lint: zero errors;
CodeQL: zero security findings, 24 quality notes. Full [verification](docs/ui15/README.md),
[new learning screens](docs/ui15/learning-screens/README.md), [workflow captures](docs/ui15/screens/README.md)
and [width/font gallery](docs/ui15/layout-matrix/README.md) are available.

Version code 19; APK source `4e7ed2e60b5a44bab96bdf29decdba4e62d1d457`.
SHA-256: `09fa61eeb2371499f03c3cbe8cc34f2973f01da780fb187c64201931baf88e73`.
Original signer, Bluetooth/manual input, AI/Auto behavior and permission sets are
retained. [Install details and offline APK](docs/ui15/install/README.md).
Physical phone/computer acceptance remains required; previous APKs are preserved.

## UI14.2 test download — live API fixes and clearer controls

Full direct APK download URL (no ZIP):

https://raw.githubusercontent.com/efrgdgr0024345/autophone/f8f80b2c03e5ce202e2d0f9d2986a26d1d6e9d5e/docs/ui14.2/install/CatAI-14.2.apk

Live testing found and fixed a detailed-plan timeout and model proposals outside
Auto's supported command grammar. Planning now has a bounded longer deadline;
Cancel stays visible above the bottom navigation, including during feedback updates.
The black cat on white icon, exact-command list, pinned Auto/Voice and editable
feedback plans are retained. Ordinary command approval remains TYPE ONLY.

The revised live checks passed: an 18-command/nine-phase plan, image preflight,
clipped-page detection, readable page transcription, a five-command replan and
OpenAI PCM speech. All 161 unit tests, 83 policy mutations, 40 planner contracts
and 49 native emulator checks passed. Lint has zero errors; CodeQL has zero
security findings (22 quality notes remain). Host API checks and emulator fixtures
are separate tests; a continuous live phone-camera/Bluetooth run is still pending.

Version code 18, same signer. App source: `28fec6a187cca84f92fa14821167d660c5436eed`.
SHA-256: `937a7f18b823f39e16f7a21b0ff83df741c76bb0614eb7bd27e0cc3103bc1042`.
[Install and verification](docs/ui14.2/README.md), [workflow screenshots](docs/ui14.2/screens/README.md),
[width/font matrix](docs/ui14.2/layout-matrix/README.md) and [live API evidence](docs/ui14.2/live-openai/README.md).
Physical Bluetooth, phone optics/playback/IME/dictation and Keystore still need
acceptance. UI14/UI14.1 are superseded; their old APK bytes remain unchanged.
No merge or baseline promotion.

## UI13 test download — pinned Auto Play and Voice

Full direct APK download URL:

https://raw.githubusercontent.com/efrgdgr0024345/autophone/dd5e43a7f77ca47e937cbb2dfda63913699edfd2/docs/ui13/install/CatAI-13.apk

Auto Play and Voice ON/OFF stay at the top of the AI/task screens. Plans show exact commands
with details on tap. Full Auto captures numbered output pages, redisplays smaller slices when
needed, combines the feedback and automatically updates the remaining plan for the original goal.
Turning Auto off stops the foreground session; there is no background continuation or replay.

Source: `7db3aed5607687236fb49bf493daf38a38ce357e`. SHA-256: `e87bc0c24f3514a45dbea88cfddd05fb8d4d5f1c83cd7eaed0880e1c2f0a4ea6`.
VersionCode 15 uses the same signer as UI11/UI12.1 and supports an update install.
Local checks: 148 Android unit tests, 83 policy/mutation checks, both builds/APK inspection,
zero Lint errors, CodeQL across 46/46 files with no security-rule findings, and native
verification of 16 core plus four Auto cases (focused rerun after a software-emulator timeout).
[Install instructions and limits](docs/ui13/install/README.md), [all 36 rendered screens](docs/ui13/screens/README.md)
and [physical acceptance](docs/UI13-ADAPTIVE-AUTO-TEST-PLAN.md). Real phone, vision/voice/API
and Linux execution testing remain required. This is a test candidate; old releases are unchanged.

[15 October Auto review and actual camera images](docs/ui13/auto-review/README.md) records
the confirmed off/on restart problem, preflight feedback gaps and the limits of the attempted
image-driven demo. A full real-phone/OpenAI Auto demonstration has not been completed.

## UI12.1 test download — detailed plans and Auto

Full direct APK download URL:

https://raw.githubusercontent.com/efrgdgr0024345/autophone/409b3c536627108ceda9826971474abedb9c1f2b/docs/ui12.1/install/CatAI-12.1.apk

The exact checked APK can update UI10/UI11 directly. Source: `437e98e45812703077e10d86b8c4d7fea11b136b`.
SHA-256: `6da0a22851da44b1a2be4221a2452360ccdbd7497aa6bfcca42741ba4bf1f298`.
Local verification passed: 135 Android tests, 79 policy/mutation checks, both builds/APK inspection,
20 real-window emulator checks, zero Lint errors and CodeQL over 44/44 files. Live phone,
vision, speech/API and Keystore acceptance remain required. [Install details](docs/ui12.1/install/README.md)
and [all 36 rendered screens](docs/ui12.1/screens/README.md). This is a development test build;
the existing releases below remain unchanged.

`feature/ui12-detailed-plans-auto-assist` adds phased roadmaps with up to 40 command steps,
one-screen command approval and an explicitly approved foreground Auto session with terminal
photos and OpenAI-generated spoken explanations. Full Auto may press Enter for the reviewed
plan; ordinary AI/library typing still excludes Enter. Unsupported commands, unclear feedback,
password prompts, disconnection/backgrounding and expanded plan scope stop automatic progress.
See [scope, limits and handset acceptance](docs/UI12-AUTO-TEST-PLAN.md). This development work
has not replaced the releases below or established physical Auto/voice/API correctness.

## UI10 development — editable plans and feedback

The next update is developed on `feature/ui11-compact-controls-bluetooth`: compact Home / Keyboard / AI / Settings controls, visible Bluetooth enable feedback and foreground session support, a Black Cat launcher icon, explicit API-key checking and exclusive structured clarification/command responses. See [UI11 acceptance and scope](docs/UI11-CONTROLS-TEST-PLAN.md). It remains a hardware-test candidate; physical connection stability and live user-key requests are still required.

Branch `feature/ui10-feedback-tree-camera` adds command-tree selection into a plan, editable steps, reviewed text/voice feedback, and camera feedback that proposes an updated plan. The user explicitly applies or discards revisions; photos never confirm success or approve typing. Dictation is opt-in and requires microphone permission only in CatAI. CatBT remains Bluetooth-only and offline.

This development work has not replaced the UI09 release below. See [UI10 acceptance and privacy notes](docs/UI10-FEEDBACK-TEST-PLAN.md). Physical Bluetooth, speech-service, camera and user-key/API acceptance are still required.

## UI09 test downloads

### CatAI-09 — AI planner + offline Linux library

**Full APK download URL:**

https://github.com/efrgdgr0024345/autophone/releases/download/ui09-2153ab44/CatAI-09.apk

- Size: `3,441,281 bytes`
- SHA-256: `f969bbcc24390f2acca8a5e5eec04d6a2686b811f21f75683ade3fa31d75c1db`
- Includes the online AI planner, photo feedback and the offline Linux command library.
- The online planner requires networking and an API key. The offline library does not.

### CatBT-09 — Bluetooth remote + offline Linux library

**Full APK download URL:**

https://github.com/efrgdgr0024345/autophone/releases/download/ui09-2153ab44/CatBT-09.apk

- Size: `3,281,317 bytes`
- SHA-256: `8753552ba6703451d5e4c02e4a79e00fbe6448395493c34f6e6a0cd5f7df992f`
- Contains no AI API integration or AI network mode.
- Includes the same local Linux command tree and search system.

Both APKs use the original application identity, so install **one variant at a time**. Do not expect CatAI and CatBT to coexist as separate apps.

### Release evidence

- Release page: https://github.com/efrgdgr0024345/autophone/releases/tag/ui09-2153ab44
- Exact source commit: `2153ab44a6fb98ecb35bfd849b8e629a27748a80`
- Protected UI08 parent: `6eec650cb3fdf2f0e5b9b463c9c20c10539eaa29`
- Checksums file: https://github.com/efrgdgr0024345/autophone/releases/download/ui09-2153ab44/SHA256SUMS
- Source record: https://github.com/efrgdgr0024345/autophone/releases/download/ui09-2153ab44/SOURCE.txt

## What UI09 adds

### Guided Linux AI planner

The planner now follows the visible mobile progression:

`Plan → Step → Preview → Result`

The user enters a goal and target system, reviews the generated plan, presses **Start with step 1**, moves through one step at a time, and reviews the exact command before any typing action.

The final command action remains **TYPE ONLY**. It types the reviewed text into the currently connected Bluetooth host but does **not** press Enter. The user checks the computer and presses Enter manually.

### Offline Linux command mode

The APK contains a checked-in local reference library with:

- `212` explained Linux commands;
- `13` top-level categories;
- `63` topics;
- a `Category → Topic → Command → Detail` tree;
- ranked local search across task names, commands, explanations, tags and helpful synonyms;
- expected output, risk level, administrator requirement and compatibility notes for each command;
- no API request, account, cloud lookup or network requirement.

Browsing and copying do not require Bluetooth. Typing still requires an existing connected HID host and a separate one-use confirmation.

## Golden rules preserved

1. The working Android Bluetooth HID registration, descriptors, keyboard reports, mouse reports, lifecycle and app identity remain protected.
2. The computer initiates normal Bluetooth pairing. The app does not replace the proven method with phone-side discovery.
3. AI and offline-library code do not register, pair, connect, disconnect or close Bluetooth HID.
4. Every command is displayed before typing.
5. **TYPE ONLY never appends Enter.**
6. A changed host, Bluetooth interruption, backgrounded app or consumed approval invalidates the one-use approval.
7. Automated tests never count as a physical Bluetooth success report.

## Verify the APK before installing

Linux or macOS:

```bash
sha256sum CatAI-09.apk
sha256sum CatBT-09.apk
```

Windows PowerShell:

```powershell
Get-FileHash .\CatAI-09.apk -Algorithm SHA256
Get-FileHash .\CatBT-09.apk -Algorithm SHA256
```

The result must exactly match the SHA-256 value listed above for the APK you downloaded.

## Physical test checklist

1. Install one UI09 APK on the intended Android phone.
2. Confirm the app opens and the Black Cat phone layout fits without controls hiding behind the Android system bars.
3. From the computer, start normal Bluetooth pairing with the phone.
4. Confirm keyboard typing, mouse movement, left click and right click.
5. Turn Bluetooth off and on and confirm the app recovers safely.
6. In CatAI, create a plan and move through **Plan → Step → Preview → Result**.
7. Confirm **TYPE ONLY** types the reviewed command but does not press Enter.
8. Change or disconnect the host before approval and confirm typing is blocked or stopped.
9. Put the app in the background during an approval and confirm that approval cannot later be reused.
10. Disable networking, open the offline library, browse the command tree and search for tasks such as `network status`, `disk space`, `memory`, `ports`, `failed services` and `permissions`.
11. Confirm **Copy command** only copies text and does not type it.
12. Record the Android model/version, computer operating system and any failure or partial text seen after an interrupted send.

The more detailed test plan is in [`docs/UI09-TEST-PLAN.md`](docs/UI09-TEST-PLAN.md).

## Android requirements

- Android 9 / API 28 or newer.
- A handset whose Android build exposes Bluetooth HID Device support.
- Bluetooth permissions accepted when requested.

## Security note

This is an experimental HID controller. Test first on a non-sensitive computer session. Bluetooth HID can inject keyboard and mouse input into the connected computer. Read every command and understand its effect before manually pressing Enter.
