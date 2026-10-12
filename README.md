# Black Cat AI Remote

Android Bluetooth HID keyboard/mouse plus a phone-first Linux AI planner, reviewed photo feedback and a fully offline Linux command library.

## Latest physical-test APKs — UI09

UI09 preserves the tested UI08 Bluetooth/HID core and adds the new planner progression and local command library. It is a **physical-test prerelease**, not yet the promoted protected baseline.

### CatAI-09 — AI planner + offline Linux library

**Full APK download URL:**

https://github.com/efrgdgr0024345/autophone/releases/download/ui09-2153ab44/CatAI-09.apk

SHA-256:

`f969bbcc24390f2acca8a5e5eec04d6a2686b811f21f75683ade3fa31d75c1db`

Size: `3,441,281 bytes`

CatAI includes the online AI planner, reviewed photo feedback and the offline Linux command library. Online AI functions require networking and a user-supplied API key. The offline library does not.

### CatBT-09 — Bluetooth remote + offline Linux library

**Full APK download URL:**

https://github.com/efrgdgr0024345/autophone/releases/download/ui09-2153ab44/CatBT-09.apk

SHA-256:

`8753552ba6703451d5e4c02e4a79e00fbe6448395493c34f6e6a0cd5f7df992f`

Size: `3,281,317 bytes`

CatBT contains no AI API integration or AI network mode. It includes the Bluetooth keyboard/mouse remote and the same local Linux command library.

Both APKs use the original application identity, so install **one variant at a time**.

## UI09 release evidence

- Release page: https://github.com/efrgdgr0024345/autophone/releases/tag/ui09-2153ab44
- Exact release source: `2153ab44a6fb98ecb35bfd849b8e629a27748a80`
- Protected UI08 parent: `6eec650cb3fdf2f0e5b9b463c9c20c10539eaa29`
- Automated verification run: https://github.com/efrgdgr0024345/autophone/actions/runs/38205227657
- Checksums: https://github.com/efrgdgr0024345/autophone/releases/download/ui09-2153ab44/SHA256SUMS
- Source record: https://github.com/efrgdgr0024345/autophone/releases/download/ui09-2153ab44/SOURCE.txt
- Development branch: https://github.com/efrgdgr0024345/autophone/tree/feature/ui09-planner-offline-library

Automated gates passed for the exact released APKs:

- frozen Bluetooth HID behaviour and mappings;
- UI09 phone-layout and planner-flow contracts;
- 212-command offline-catalogue integrity and safety checks;
- planner and photo regression tests;
- Android unit tests and Lint for both variants;
- AI and offline APK builds;
- packaged app identity, permission and signature inspection;
- CodeQL security-and-quality analysis;
- SHA-256 generation and exact APK provenance attestation.

Physical Android handset, computer pairing, keyboard, mouse, camera and API acceptance still needs to be recorded.

## UI09 — guided planner

The Linux assistant now follows the visible phone progression:

`Plan → Step → Preview → Result`

1. Enter the goal and select the target Linux system.
2. Generate and review the full plan.
3. Press **Start with step 1**.
4. Work through one step at a time.
5. Review the exact command and current Bluetooth destination.
6. Approve **TYPE ONLY**.
7. Check the computer and press Enter manually.
8. Review the result or provide a photo for follow-up help.

The user remains in control. There is no automatic Enter, run-all or hidden replay.

## UI09 — offline Linux command library

The application now carries a checked-in reference library containing:

- `212` explained Linux commands;
- `13` top-level categories;
- `63` topics;
- a `Category → Topic → Command → Detail` tree;
- ranked local search over task names, commands, explanations, tags and synonyms;
- expected output, risk, administrator requirement and compatibility notes;
- no API request, account, cloud lookup or network requirement.

The user can browse categories such as network, storage, performance, processes, services, logs, permissions, packages, security, containers and development tools. Browsing and copying do not require Bluetooth. Typing requires an already connected Bluetooth HID host and a new one-use confirmation.

## Golden rule — preserved working core

UI09 is built above the tested UI08 source rather than replacing it.

Protected by regression checks:

- computer-initiated Bluetooth HID pairing and discoverability;
- `HidManager`, `HidDescriptor`, `HidReports` and diagnostics;
- Bluetooth permissions and Activity lifecycle;
- direct text typing;
- keyboard mappings;
- mouse and touchpad HID reports;
- encrypted phone-held API-key flow;
- OpenAI text planner;
- Camera2 capture, review and photo-analysis flow;
- reviewed **TYPE ONLY** sender;
- no automatic Enter;
- no run-all;
- no hidden replay;
- no AI-managed Bluetooth lifecycle.

## Verify before installing

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

The reported SHA-256 must exactly match the value above for the selected APK.

## Physical acceptance checklist

1. Install one UI09 APK on the intended Android phone.
2. Confirm no important control is hidden behind the Android status or navigation bars.
3. Start normal Bluetooth pairing from the computer.
4. Confirm text typing, keyboard controls, mouse movement, left click and right click.
5. Turn Bluetooth off and on and confirm safe recovery.
6. In CatAI, complete **Plan → Step → Preview → Result**.
7. Confirm **TYPE ONLY** types the reviewed text and never presses Enter.
8. Disconnect or change the host before approval and confirm the action is blocked or stopped.
9. Put the app in the background during approval and confirm the approval cannot be reused.
10. Disable networking and confirm the offline library still browses and searches correctly.
11. Confirm **Copy command** copies only and does not type.
12. Test photo capture, exact-image review and explicit AI upload in CatAI.

Report the Android model/version, computer operating system, test results and any partial text visible after an interrupted typing action.

## Photo feedback

The reviewed photo workflow remains:

1. Capture a terminal or error-screen photo inside the app.
2. Review the exact image before upload.
3. Explicitly send it to the AI service using the phone-held API key.
4. Treat all screen text as **untrusted evidence**, not instructions.
5. Ask questions instead of inventing commands when evidence is unclear.
6. Review any suggested command separately before **TYPE ONLY**.

The AI build requests camera and internet only for these features. It does not require microphone or broad gallery/storage permission for the in-app capture path.

## Preserved releases

- Original Bluetooth V2 and API02: https://github.com/efrgdgr0024345/autophone/releases/tag/preserved-v2-api02-20260929
- UI04: https://github.com/efrgdgr0024345/autophone/releases/tag/ui04-ab34f45d
- UI06 photo-enabled lineage: https://github.com/efrgdgr0024345/autophone/releases/tag/ui06-9c46792d
- UI07: https://github.com/efrgdgr0024345/autophone/releases/tag/ui07-c9cb8c35
- UI08: https://github.com/efrgdgr0024345/autophone/releases/tag/ui08-6eec650c
- UI09: https://github.com/efrgdgr0024345/autophone/releases/tag/ui09-2153ab44

Previous releases are never overwritten.

## Android requirements

Android 9 / API 28 or newer, with Bluetooth HID Device support exposed by the handset. Camera support is required only for Photo Feedback.

## Security note

This is an experimental HID controller. Test first on a non-sensitive computer session. Bluetooth HID can inject keyboard and mouse input into the connected computer. Read and understand each command before manually pressing Enter.
