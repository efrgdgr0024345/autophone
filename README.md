# Black Cat AI Remote

Android phone remote that preserves the proven Bluetooth HID keyboard/mouse core while adding a phone-first Linux AI planner and a fully offline Linux command library.

> **UI09 is a physical-test prerelease.** All automated build, policy, unit, lint, APK inspection, CodeQL and provenance gates passed. Real Android-to-computer Bluetooth testing is still required before this version becomes the protected baseline.

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
