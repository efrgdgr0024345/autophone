# UI11 phone-install preview

[Download CatAI-11.apk](CatAI-11.apk?raw=true) — the complete AI/camera/voice, Linux command library and Bluetooth keyboard/mouse app, about 3.5 MB.

[Download CatBT-11.apk](CatBT-11.apk?raw=true) — the offline Bluetooth alternative, about 3.3 MB. These variants share an application ID and cannot be installed side by side.

These are the exact tested APK bytes from source `85c02ec0859ae0a6d9f37b392d97e4191b00ceca`, copied without rebuilding. SHA-256 and signing certificate metadata are alongside the files. They are development test builds, with a non-debuggable application. Existing releases have not been overwritten or promoted.

## Install

1. Download `CatAI-11.apk` on your phone and open it. If Android asks, allow installation from that browser or file manager, then disable that installation permission after installing.
2. This certificate matches the previous UI10 test APK, so UI11 can update UI10 directly. It differs from the UI09 release certificate; Android cannot update UI09 with this APK. Uninstalling removes local settings and the encrypted API key, so preserve access to your own key before deciding on a fresh installation.
3. Android 9 or newer remains supported. Android 12+ still requires Nearby devices permission for Bluetooth keyboard/mouse operation. This update does not remove that required permission or raise the minimum OS version.
4. Use Settings for Bluetooth setup, target system, model, API-key checking and logs. Keyboard provides phone-keyboard text entry, dictation, the mouse pad and special keys. AI provides the compact Plan → Step → Preview → Result flow and reviewed photo/typed/spoken feedback.

## What changed

- Home / Keyboard / AI / Settings navigation, restored manual controls, compact task headers and the approved Black Cat launcher icon.
- Visible Bluetooth-off feedback and the Android enable prompt after explicit setup. A visible connected-device foreground session helps prevent Android from unregistering background HID sessions. The four protected Bluetooth core files remain unchanged.
- Explicit API-key/model access verification, optional encrypted remembered keys and temporary memory-only keys.
- Latest verified default model `gpt-6.1-sol`, strict structured responses, exclusive clarification/command decisions, separate command and explanation fields, and bounded parsing. The model checker confirms metadata access; billing and generation permission still require an actual request.

## Verification and remaining phone tests

Both builds, 106 Android unit tests, policy/mutation checks, Lint with zero errors, APK inspection and CodeQL checks passed. The native UI run passed 16 checks and captured 29 real app-window screenshots using synthetic API responses; a hosted full verification run also passed. One duplicate hosted run failed while waiting for an attached Settings screenshot window, so hosted screenshot capture is still intermittent.

Real-phone Bluetooth stability, pairing/reconnection, background/screen-lock behavior, keyboard layout, physical camera/microphone behavior, Keystore storage and live OpenAI requests still require handset testing. The ordinary protected manual sender retains its documented limitations; reviewed AI/library commands require fresh approval, type text only, and never append Enter. This APK is a testing milestone, not a completed production release.

See [UI11 acceptance plan](../../UI11-CONTROLS-TEST-PLAN.md) for the full checks and remaining work.
