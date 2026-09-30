# Black Cat Remote

Public Android test app that makes a compatible Android phone act as a Bluetooth HID keyboard and mouse, with an optional OpenAI-powered Linux command assistant.

## Latest physical-test APK — UI04

Direct APK:

https://github.com/efrgdgr0024345/autophone/releases/download/ui04-ab34f45d/CatAI-04.apk

SHA-256:

`7ea318fba91b33da9e0508e4e17c849c28a83fd9e00c136b21ec0a45c635f704`

Source commit:

`ab34f45d7ef09f06e4d41873079b168e20df01f6`

GitHub Actions verification:

https://github.com/efrgdgr0024345/autophone/actions/runs/36661260468

Draft PR:

https://github.com/efrgdgr0024345/autophone/pull/15

Status: **physical-test prerelease**. Automated Bluetooth-behavior guards, Android build/tests, Lint, package/signature inspection, CodeQL and GitHub artifact provenance attestation passed. This is not yet physical Bluetooth acceptance.

UI04 applies the Black Cat design across the main Bluetooth keyboard/mouse screen and the AI assistant. The AI target system is now a saved dropdown of common Linux targets with a final **Edit / custom…** option.

## Preserved references

The exact earlier Bluetooth V2 and API02 APK/source references are preserved here:

https://github.com/efrgdgr0024345/autophone/releases/tag/preserved-v2-api02-20260929

The previous UI03 physical-test prerelease remains available here:

https://github.com/efrgdgr0024345/autophone/releases/tag/ui03-44917e02

Do not overwrite the preserved reference releases.

## Current controls

- Computer-initiated Bluetooth HID pairing
- Keyboard text input
- Mouse/touchpad controls
- Black Cat styling throughout the app
- Optional OpenAI Linux command planning
- Saved target-system dropdown with **Edit / custom…**
- Explicit **TYPE ONLY** command approval
- No automatic Enter or hidden command execution

## Security

This is an experimental HID controller. Test candidate APKs on a non-sensitive session first. Bluetooth HID can inject keyboard and mouse input into the connected computer.

## Android requirements

Android 9 / API 28 or newer, with Bluetooth HID Device support exposed by the handset.
