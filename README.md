# Black Cat Remote

Public Android test app that makes a compatible Android phone act as a Bluetooth HID keyboard and mouse, with an optional OpenAI-powered Linux assistant and reviewed screen-photo feedback.

## Latest physical-test APK — UI07

Direct APK:

https://github.com/efrgdgr0024345/autophone/releases/download/ui07-c9cb8c35/CatAI-07.apk

SHA-256:

`79d43bb4451cd71fb9345034df0b8fdc3e876e03727ff4a5579f56444b8fb9a6`

Source commit:

`c9cb8c3572c5bf25a7efd241870552c0d37460a7`

UI07 GitHub Actions verification:

https://github.com/efrgdgr0024345/autophone/actions/runs/36671838368

Independent Android CI verification:

https://github.com/efrgdgr0024345/autophone/actions/runs/36671855784

Draft PR:

https://github.com/efrgdgr0024345/autophone/pull/20

Status: **physical-test prerelease**. Automated golden-Bluetooth behavior guards, planner/photo tests, Android unit tests, Lint, both APK builds, packaged permission/identity/signature inspection, CodeQL and GitHub artifact provenance attestation passed. This is not yet physical handset/Bluetooth/camera/API acceptance.

## UI07 design and flow

UI07 is built on the tested photo-enabled UI06 lineage and follows the golden rule: preserve the known working Bluetooth/HID/photo/API core and build the new interface around it.

- large full Black Cat image on a **white background**
- operational text and controls on **dark/black panels**
- green action buttons and status accents
- display respects Android system-bar/navigation insets
- Home/Remote quick tools:
  - **Linux Assistant**
  - **Photo Feedback**
  - **Send Text**
  - **Touchpad**
  - **Keyboard**
- only the selected direct-control panel expands
- Linux Assistant flow: **Plan → Step → Preview → Result**
- saved target-system dropdown with common Linux systems
- final **Edit / custom…** target option
- diagnostics collapsed at the end of the main flow
- explicit **TYPE ONLY** approval; Enter is never sent automatically

## Photo Feedback

The AI build keeps the reviewed Photo Feedback workflow:

1. Take a photo of the terminal/error screen inside the app.
2. Review the exact photo before sending.
3. Explicitly send it to OpenAI using the same encrypted phone-held API key.
4. OpenAI analyzes it in the context of the user's stated goal and saved target system.
5. Screen text is treated as **untrusted evidence**, not instructions.
6. If the image is unclear or critical information is missing, the app expects questions rather than speculative commands.
7. Any suggested command must still be reviewed and explicitly approved through **TYPE ONLY**.
8. No automatic Enter, command execution, run-all or hidden replay.

The app requests CAMERA only in the AI flavor. It does not request broad gallery/storage access or microphone permission, and the in-app captured photo is not deliberately saved to the gallery.

## Golden rule / preserved working core

UI07 is layered above the existing working lineage. The following behavior remains protected by regression tests:

- computer-initiated Bluetooth HID pairing/discoverability
- HidManager / HidDescriptor / HidReports / Diagnostics
- Bluetooth permissions and Activity lifecycle
- direct text typing
- mouse/touchpad reports
- full HID keyboard mappings
- encrypted phone-held API key flow
- OpenAI text planner
- Camera2 capture/review/photo-analysis path
- reviewed **TYPE ONLY** sender
- no automatic Enter, run-all or replay

## Preserved references

Original Bluetooth V2 and API02:

https://github.com/efrgdgr0024345/autophone/releases/tag/preserved-v2-api02-20260929

CatAI-04 working-lineage prerelease:

https://github.com/efrgdgr0024345/autophone/releases/tag/ui04-ab34f45d

Previous photo-enabled UI06:

https://github.com/efrgdgr0024345/autophone/releases/tag/ui06-9c46792d

Previous UI03:

https://github.com/efrgdgr0024345/autophone/releases/tag/ui03-44917e02

Do not overwrite the preserved reference releases.

## Security

This is an experimental HID controller. Test candidate APKs on a non-sensitive session first. Bluetooth HID can inject keyboard and mouse input into the connected computer.

## Android requirements

Android 9 / API 28 or newer, with Bluetooth HID Device support exposed by the handset. Camera support is optional unless Photo Feedback is used.
