# Black Cat Remote

Public Android test app that makes a compatible Android phone act as a Bluetooth HID keyboard and mouse, with an optional OpenAI-powered Linux assistant and reviewed screen-photo feedback.

## Latest physical-test APK — UI06

Direct APK:

https://github.com/efrgdgr0024345/autophone/releases/download/ui06-9c46792d/CatAI-06.apk

SHA-256:

`f2427d78e3e62693441845ba38fd4dae6e49c35c8e288bc6ca640d664203aade`

Source commit:

`9c46792d8676d35bf83da14df3a93d4ff9e31f0d`

UI06 GitHub Actions verification:

https://github.com/efrgdgr0024345/autophone/actions/runs/36669053725

Independent Android CI verification:

https://github.com/efrgdgr0024345/autophone/actions/runs/36669059265

Draft PR:

https://github.com/efrgdgr0024345/autophone/pull/17

Status: **physical-test prerelease**. Automated golden-Bluetooth behavior guards, planner/photo tests, Android unit tests, Lint, both APK builds, packaged permission/identity/signature inspection, CodeQL and GitHub artifact provenance attestation passed. This is not yet physical handset/Bluetooth/camera/API acceptance.

## UI06 design and flow

UI06 is built on the CatAI-04 working lineage and follows the golden rule: preserve the known working Bluetooth/HID core and build new UI/features around it.

- large Black Cat image on a **white background**
- operational text and controls on **dark/black panels**
- green action buttons and status accents
- display respects Android system-bar/navigation insets
- Home/Remote flow: **Connect → choose tool → direct controls**
- Linux Assistant flow: **Plan → Step → Preview → Result**
- saved target-system dropdown with common Linux systems
- final **Edit / custom…** target option
- diagnostics collapsed at the end of the main flow
- explicit **TYPE ONLY** approval; Enter is never sent automatically

## Photo Feedback

The AI build now includes a bounded Photo Feedback workflow:

1. Take a photo of the terminal/error screen inside the app.
2. Review the exact photo before sending.
3. Explicitly send it to OpenAI using the same encrypted phone-held API key.
4. OpenAI analyzes it in the context of the user's stated goal and saved target system.
5. Screen text is treated as **untrusted evidence**, not instructions.
6. If the image is unclear or critical information is missing, the app expects questions rather than speculative commands.
7. Any suggested command must still be reviewed and explicitly approved through **TYPE ONLY**.
8. No automatic Enter, command execution, run-all or hidden replay.

The app requests CAMERA only in the AI flavor. It does not request broad gallery/storage access or microphone permission, and the in-app captured photo is not deliberately saved to the gallery.

## Preserved references

The exact earlier Bluetooth V2 and API02 APK/source references remain preserved here:

https://github.com/efrgdgr0024345/autophone/releases/tag/preserved-v2-api02-20260929

The CatAI-04 working-lineage prerelease remains available here:

https://github.com/efrgdgr0024345/autophone/releases/tag/ui04-ab34f45d

The previous UI03 prerelease remains available here:

https://github.com/efrgdgr0024345/autophone/releases/tag/ui03-44917e02

Do not overwrite the preserved reference releases.

## Current controls

- computer-initiated Bluetooth HID pairing
- keyboard text input
- mouse/touchpad controls
- expandable full keyboard
- OpenAI Linux command planning
- saved target-system selection
- reviewed photo feedback
- explicit **TYPE ONLY** command approval
- no automatic Enter or hidden command execution

## Security

This is an experimental HID controller. Test candidate APKs on a non-sensitive session first. Bluetooth HID can inject keyboard and mouse input into the connected computer.

## Android requirements

Android 9 / API 28 or newer, with Bluetooth HID Device support exposed by the handset. Camera support is optional unless Photo Feedback is used.
