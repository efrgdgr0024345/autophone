# CatAI-02 — repair preview, physical test required

This is a test build, not confirmation that the reported Bluetooth fault has been fixed on your phone.

1. Keep a copy of the known-working BlackCat-v2.apk. Do not delete its GitHub baseline.
2. Stop/remove CatAI-01 (the previous separate com.blackcat.remote.ai app), and close any other Bluetooth-keyboard apps. Android permits only one HID registration at a time. Merely being installed is not proof of a conflict.
3. Install CatAI-02.apk. It now uses the original com.blackcat.remote identity. If Android accepts the update, there is no need to uninstall the original app. If it refuses because signing certificates differ, uninstall ONLY the existing Black Cat test app, then install this preview. Uninstalling erases local app settings/saved keys. Do not bypass Android signature or malware checks.
4. First test normal keyboard and mouse WITHOUT opening AI. Use the original computer-side pairing workflow. No automatic removal of existing Bluetooth bonds is required by the app.
5. Only after normal input works, tap AI in the existing COPY LOG/CLEAR row. Enter a NEW personal key on the phone, optionally save it, enter your target OS/shell and a read-only goal such as showing the current username. Do not paste keys into chat or logs.
6. Generate -> read explanation -> select ONE command -> review destination -> TYPE ONLY. Verify the text on the computer and press Enter yourself. The app cannot see the terminal or prove a command succeeded.
7. Test Cancel, leaving the AI panel, connection loss, saved-key reopen and Forget. No automatic retry/resume is intended. Previously typed characters must be inspected/cleared manually.

The offline CatBT-02.apk is an alternative control build with no Internet, not a second companion app. Both variants have the same package ID; do not try to run both together.

The four V2 core files and the complete original Activity (except a lazy AI button hook) are checked against golden hashes. No extra pause/resume hooks or AI gates wrap normal input. The key stays in phone-local encrypted storage; only an authenticated HTTPS API request reaches OpenAI. No website participates.

No production signing key was configured for these historical previews. APK signature/certificate information is supplied, but seamless upgrading across independently signed old builds is not guaranteed. This release publishes exactly the tested CI artifact, without rebuilding it. Never confuse a matching source commit with identical APK bytes.

Known inherited limitations are intentionally not rewritten in this regression repair: Activity-owned HID lifecycle/background survival, full modifier combinations, US keyboard layout and pre-login connectivity need their own physical tests. A successful build/CodeQL result is not a security certification or a hardware test.
