# Black Cat — governing development rules

## Golden behaviour
The physically successful V2 source is 9771772bbe25dd8eba8d7303f7e4680aa84175df. Do not write to locked branches or overwrite BlackCat-v2.apk. The computer discovers/pairs with the phone; no phone-side scan-first architecture.

Freeze the full input path, not just files named HID. check_policy.py must verify MainActivity equals the golden blob after removal of exactly one reviewed lazy AI button hook. Normal manual Send, mouse, permission flow, pairing and lifecycle must not depend on AI state. Freeze HidManager, HidDescriptor, HidReports and Diagnostics byte-for-byte. Any future changes require a separately approved regression experiment and physical evidence; never silently relax these gates.

## AI separation and credentials
AI is optional and lazy. Offline variant has no Internet permission or AI client. User explicitly approved INTERNET only in the AI variant for direct requests to OpenAI. Key entry/storage stays on the phone: no hardcoded key, website, proxy or credential in git/CI/logs. Optional Android Keystore storage has no plaintext fallback. Do not claim it makes a compromised phone safe.

Model responses are untrusted proposals. No API handler can type, press Enter, execute a process or manage Bluetooth. The user reviews one command and explicitly confirms TYPE ONLY. Guard AI input/cancellation/session changes without changing ordinary manual input. Do not log API keys, goals, commands or derived key identities. Preserve licences/attribution.

## Verification and release
Use a feature branch and PR. Run full build, actual tests, Lint, policy/APK checks and CodeQL; upload complete reports on failure. No suppressions merely to get green. Report test counts and inherited limitations honestly. Publish the exact tested APK bytes with commit/checksum/certificate; never rebuild solely for a filename change or overwrite a prior preview. No merge to main until physical regression review.

A green run is not proof of Bluetooth hardware, on-device Keystore, host keyboard layout, background survival or successful shell execution. Root cause remains unconfirmed without handset evidence. See AI_COMMAND_ASSISTANT.md and docs/AI02_INSTALL.md.
