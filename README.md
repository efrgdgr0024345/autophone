# Black Cat Remote

Public Android test app that makes a compatible Android phone act as a Bluetooth HID keyboard and mouse, with an optional OpenAI-powered Linux command assistant.

## Latest physical-test APK — UI03

Direct APK:

https://github.com/efrgdgr0024345/autophone/releases/download/ui03-44917e02/CatAI-03.apk

SHA-256:

`bed3000a138dc91345706fdc63cbca577a2ef82189e008782dbe8ef230da508f`

Source commit:

`44917e02a5cdf2d35a729224e3f26140adb08a43`

GitHub Actions verification:

https://github.com/efrgdgr0024345/autophone/actions/runs/36647341601

Status: **physical-test prerelease**. Automated Android build/tests, Lint, package/signature inspection, CodeQL and GitHub artifact provenance attestation passed. This does **not** yet count as physical Bluetooth acceptance; that is tracked in issue #11.

## Preserved references

The exact earlier Bluetooth V2 and API02 APK/source references are preserved here:

https://github.com/efrgdgr0024345/autophone/releases/tag/preserved-v2-api02-20260929

Do not overwrite the preserved reference releases.

## Current controls

- Computer-initiated Bluetooth HID pairing
- Keyboard text input
- Mouse/touchpad controls
- Optional OpenAI Linux command planning
- Explicit **TYPE ONLY** command approval
- No automatic Enter or hidden command execution

## Security

This is an experimental HID controller. Test candidate APKs on a non-sensitive session first. Bluetooth HID can inject keyboard and mouse input into the connected computer.

## Android requirements

Android 9 / API 28 or newer, with Bluetooth HID Device support exposed by the handset.
