# Black Cat Remote

Android Bluetooth HID keyboard/mouse plus an optional OpenAI-powered Linux assistant with reviewed screen-photo feedback.

## Latest physical-test APK — UI08

Direct APK:

https://github.com/efrgdgr0024345/autophone/releases/download/ui08-6eec650c/CatAI-08.apk

SHA-256:

`b8d4eeda7a6f4a419fe51ad97617b9480db47dad7e7dd37c220291980121dc9e`

Exact source commit:

`6eec650cb3fdf2f0e5b9b463c9c20c10539eaa29`

UI08 verification / provenance:

https://github.com/efrgdgr0024345/autophone/actions/runs/36713102353

Independent Android CI:

https://github.com/efrgdgr0024345/autophone/actions/runs/36713109790

Draft PR:

https://github.com/efrgdgr0024345/autophone/pull/22

Physical acceptance checklist:

https://github.com/efrgdgr0024345/autophone/issues/23

Status: **physical-test prerelease**. Automated golden-Bluetooth behavior guards, UI08 reference-screen tests, planner/photo regressions, Android unit tests, Lint, AI + offline APK builds, packaged permission/identity/signature inspection, CodeQL and exact APK provenance attestation have passed. Physical handset/Bluetooth/camera/API acceptance has not yet been recorded.

## UI08 — approved full-screen design

UI08 implements the approved **Black Cat AI Remote — All Screens (v1)** flow while keeping the working core underneath it.

- full black cat presented on a **white background**
- operational text and controls on dark/black panels
- bright green action/status accents
- Android system/navigation insets retained so controls sit above the phone navigation area
- Main Menu:
  - Bluetooth
  - AI Assistant
  - Target System
  - Settings
- Bluetooth screen keeps the proven **computer-initiated HID pairing** architecture
- AI Assistant screens:
  - Plan
  - Step
  - Feedback
  - Preview
- Target System:
  - common Linux targets
  - saved selection
  - Edit / Custom
- Settings:
  - encrypted phone-held OpenAI API key
  - model selection
- Camera / Photo Feedback:
  - capture inside the app
  - review exact photo
  - explicit send to OpenAI
  - visible Sending to AI state

## Photo Feedback

UI08 includes the reviewed photo workflow:

1. Capture a photo of the terminal/error screen inside the app.
2. Review the exact image before upload.
3. Explicitly send it to OpenAI using the same phone-held API key.
4. OpenAI analyses it in the context of the stated goal and selected target.
5. Screen text is treated as **untrusted evidence**, not instructions.
6. Unclear evidence should produce questions rather than speculative commands.
7. Any suggested command still requires separate review and **TYPE ONLY** approval.
8. Enter is never pressed automatically.

The AI build requests camera and internet only as needed for these features. It does not require microphone or broad gallery/storage access for the in-app capture flow.

## Golden rule — preserved working core

The UI is built around the existing working lineage rather than replacing it.

Protected by regression tests:

- computer-initiated Bluetooth HID pairing/discoverability
- HidManager / HidDescriptor / HidReports / Diagnostics
- Bluetooth permissions and Activity lifecycle
- direct text typing
- mouse/touchpad HID reports
- full HID keyboard mappings
- encrypted phone-held API key flow
- OpenAI text planner
- Camera2 capture/review/photo-analysis path
- reviewed **TYPE ONLY** sender
- no automatic Enter
- no run-all
- no hidden replay

## Preserved references

Original Bluetooth V2 and API02:

https://github.com/efrgdgr0024345/autophone/releases/tag/preserved-v2-api02-20260929

UI04:

https://github.com/efrgdgr0024345/autophone/releases/tag/ui04-ab34f45d

UI06 photo-enabled lineage:

https://github.com/efrgdgr0024345/autophone/releases/tag/ui06-9c46792d

UI07:

https://github.com/efrgdgr0024345/autophone/releases/tag/ui07-c9cb8c35

UI08:

https://github.com/efrgdgr0024345/autophone/releases/tag/ui08-6eec650c

Previous releases are never overwritten.

## Android requirements

Android 9 / API 28 or newer, with Bluetooth HID Device support exposed by the handset. Camera support is required only when using Photo Feedback.
