# UI10 phone-install preview

[Download the complete AI/camera/voice APK](CatAI-10.apk?raw=true) — about 3.4 MB. This is the file for the requested Linux-assistant milestone. It includes the offline command catalogue; no additional app package is needed.

[Download the Bluetooth/offline-only APK](CatBT-10.apk?raw=true) — about 3.3 MB. This alternative excludes Internet, camera and microphone capabilities. Both variants share the same application ID and cannot be installed side by side.

The files are the exact existing, verified APK bytes from source `84518cc33cd3ff1daa904a77c20e1da6128bc57c`. They were copied, not rebuilt for delivery. SHA-256 and signer metadata are alongside them. These are development test builds signed with the existing cloud debug certificate; the application itself is non-debuggable. This delivery is not a production release or baseline promotion.

## Install and try it

1. Download `CatAI-10.apk` on the phone, open it and allow installation from that browser/file manager if Android asks. Restrict that installation permission again after installing.
2. Android 9 or newer is supported. Android 12+ uses the modern Bluetooth permissions; the minimum has not been changed to Android 12 only. OS version alone does not establish security or current patches.
3. If UI09 is installed, this build cannot replace it directly: its signing certificate is different. Do not uninstall casually. Uninstalling removes app settings and the locally encrypted API key. Have the key available independently before choosing a fresh installation. A signing error is not a reason to disable Android signature verification.
4. Browse the Linux command library locally. For AI planning and photo analysis, enter your own OpenAI API key in the phone's Settings; account/model availability and billing must be valid. Dictation needs an Android speech-recognition service, with on-device recognition preferred when available.
5. To type an approved plan/library command, pair from the computer's Bluetooth settings after choosing Make Discoverable in the app. Review the destination and exact command. The reviewed workflow types text only; inspect the computer and press Enter yourself.

## Remaining work before a finished release

1. Stabilize the hosted screenshot test. Latest documentation-only runs failed during PixelCopy before the app window had a backing surface. The exact application source previously passed 9 local UI checks and both hosted UI runs; the newer failure is in screenshot capture, and current CI must not be described as entirely green until corrected.
2. Test this exact APK on the intended Android phone and Linux computer: installation, Bluetooth discovery/pairing, keyboard layout, mouse/buttons, reconnect, screen lock/background and interrupted sends. Verify fresh destination approval and no replay/automatic Enter in the reviewed workflow.
3. Test actual microphone permission/service behavior and actual camera photographs. Check denial, cancellation, retake, unclear terminal output, secret removal before upload, and explicit review before submitting feedback or applying a replacement plan.
4. Test live OpenAI plan generation, terminal-photo analysis and feedback revisions with a user-held phone key; check authentication, service/network failures, the 2,000-character context budget and stale responses. Confirm stored-key behavior on the real handset. Emulator fixtures do not establish these outcomes.
5. Check the full task flow at small phone widths, large fonts, soft keyboard and long commands; correct real-device defects and rerun the affected tests plus the release gates.
6. Resolve or explicitly decide the inherited security findings for ordinary manual remote input and release distribution. The strict reviewed plan/library sender adds session and control-character safeguards; the protected ordinary manual sender remains separate and has not been hardened by this UI10 work. Review serial sends, host/session changes, control characters, failures/key release and sensitive-input handling without casually rewriting the proven Bluetooth baseline.
7. Establish securely managed, consistent release signing and a deliberate version/upgrade strategy. Current app identity and version remain the handover values (versionCode 11, versionName 9.0-planner-offline-library). The UI10 filename identifies the development milestone. Old signing keys have not been recovered or copied.
8. Record hardware acceptance, address material findings and remaining Lint warnings, run preservation/unit/Lint/APK/CodeQL/CI gates against the final source, then review the draft PR and publish the exact checked release APK with source/checksums. Merge or release publication still requires authorization and must not overwrite earlier releases.

Detailed handset acceptance: [UI10 feedback test plan](../../UI10-FEEDBACK-TEST-PLAN.md). These APKs complete the requested implementation milestone for testing; they do not establish production readiness, physical Bluetooth correctness or successful terminal commands.
