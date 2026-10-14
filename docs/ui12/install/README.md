# UI12 phone-install test build

**Superseded candidate:** use [UI12.1](../../ui12.1/install/README.md), which adds the fresh-prompt and late-revision guards. These older APK bytes remain as test evidence.

[Download CatAI-12.apk](CatAI-12.apk?raw=true) — the complete AI/camera/voice, Linux command library and Bluetooth keyboard/mouse app.

[Download CatBT-12.apk](CatBT-12.apk?raw=true) — the offline alternative. It excludes AI, camera, microphone, narration and Auto networking. These flavors share an application ID and cannot be installed side by side.

These are the exact tested APK bytes from the full source SHA in SOURCE.txt. SHA-256 and signing certificates are alongside them. The app is non-debuggable; this is a development test build signed with the existing cloud test certificate. Old APKs/releases and the protected Bluetooth baseline are unchanged.

## Install and use

1. Download CatAI-12.apk on the phone and open it. It can update UI10/UI11 directly because the signer matches. Android may ask to allow installation from that browser/file manager; disable that installation permission afterwards. The UI09 release has a different signer and cannot be updated directly; uninstalling removes settings and the locally encrypted key.
2. Ordinary Step and library detail screens now have a single approval button showing the command/destination. They still type text only; Enter remains manual. Preview is optional.
3. Ask for a broad goal with the relevant OS/access/recovery facts. The planner now provides a phased roadmap and up to 40 useful ordered commands, with explanations, expected results and safeguards. Expand phase details to read the full roadmap. Critical missing facts block executable changes while keeping the roadmap visible.
4. Auto opens the reviewed session screen. Start the live preview, keep the camera on the correct computer's terminal, focus that terminal, remove secrets from the frame and review the commands/destination before consenting. Full Auto is the default session choice requested by the user; read-only Auto and guided/manual-Enter alternatives are also available.
5. Full Auto can type and press Enter for supported reviewed commands, check terminal photos, narrate the purpose with OpenAI speech and continue. It can adopt up to three revisions containing remaining approved commands or strict read-only diagnostics. Expanded scope needs review; transmitted changes are not retried. Password prompts, unsupported/destructive commands, unclear/busy/error evidence, lost connection or changed context stop progress. An ambiguous or silent command may require manual inspection. The foreground session keeps the display awake; Stop/finish/close clear that flag. No background resume or wake lock is used.
6. OpenAI-generated voice is the default explanation provider. Settings offers OpenAI, an available offline phone voice, or text only. Speech and photo checks use the user's own API account and may incur charges. Model-metadata key validation does not establish audio-generation access or billing. Voice failures leave explanations on screen. No microphone is used for narration, and no audio/photo is saved to the gallery by the app.

## Verification

- Both builds, 132 Android unit tests (105 AI + 27 offline), 78 policy/mutation tests, 40 independent planner-lab checks and the artwork check passed.
- Lint: zero errors; 20 offline and 54 AI warnings remain.
- Exact packaged identity, permissions, non-exported Bluetooth service, licenses, offline capability split and signatures passed inspection. The signer matches UI11.
- Final native instrumentation passed 20 checks and captured 36 real app-window PNGs, including a two-step Auto loop with fake-target Enter counts, real emulated Camera2 JPEGs, session consent, display-awake cleanup and stopping before any send. A synthetic response gate makes the screenshot timing deterministic; production timing and approval checks are unchanged.
- Final CodeQL scanned 44/44 Kotlin files. It reported no security-rule finding; 15 deprecated-call notes and the inherited protected Diagnostics indentation warning remain. Static analysis does not prove absence of vulnerabilities.

[View all rendered screens](../screens/README.md). [Scope and full physical acceptance](../../UI12-AUTO-TEST-PLAN.md).

Real-phone Bluetooth, correct desktop focus/layout, live vision decisions, OpenAI speech/billing access, microphone/camera behavior and Keystore still require handset testing. HID supplies no exit code or stdout, and vision can misread a screen. Keep the terminal focused and observe the session. This is a testing candidate, not a production release or a security guarantee.
