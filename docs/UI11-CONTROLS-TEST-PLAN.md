# UI11 — compact controls, visible Bluetooth setup and structured feedback

Development branch: `feature/ui11-compact-controls-bluetooth`.
Rollback: `94ae00a36de4a9d1b6df3ad3302dabb14d0f2ad2` (the UI10 continuation).

## Requested changes

- Primary navigation is Home / Keyboard / AI / Settings. Keyboard exposes editable phone text input, explicit dictation, touchpad/clicks and expandable special keys. Transcripts never send automatically. CatBT uses the phone keyboard's voice input and adds no app microphone permission.
- Settings contains target, model, key verification, Bluetooth setup/disconnect and collapsed redacted diagnostics. AI stage tabs appear at the top; large artwork is retained on Home rather than above operational controls. Adaptive launcher artwork reuses the approved Black Cat emblem; a monochrome cat marks the Bluetooth session notification.
- Bluetooth setup shows a prominent "Failed: Bluetooth is off" message and requests Android's enable consent, with system Bluetooth Settings as fallback. Nearby-device permission and setup are initiated by a setup action. Pairing still starts from the computer.
- A user-started connected-device foreground session maintains the process importance Android HID requires when another system screen covers the app. It contains no HID manager or transmission code, uses START_NOT_STICKY, and stops on Bluetooth-off, explicit stop or final Activity destruction. Connection preservation is distinct from permission to type: AI/library approvals still expire on background/interruption, with no partial-send replay.
- Disconnect computer requests disconnection and stops foreground support. The frozen core remains registered while the Activity is alive; a bonded computer may reconnect while the app is visible. Final Activity destruction retains the original unregister/close path. This is not an always-on reconnect service.

## API formatting and key checking

Verified model default: `gpt-6.1-sol`; economical alternative: `gpt-6-luna`. Both official model pages document image input and structured output. The Responses requests use low reasoning effort and an 8,192-token output budget because the budget includes reasoning as well as visible JSON. Requests have bounded timeouts, store=false, no tools, no retry batches and no app-held cloud key.

The strict JSON schema includes a nested decision with mutually exclusive modes:

| Mode | Questions | Commands |
| --- | --- | --- |
| clarify | 1–8 | empty |
| commands | empty | bounded ordered list |

Each command has exact single-line printable text and separate explanation, expected result, risk, administrator flag and warnings. Prompt guidance excludes Markdown/prose from command fields and requests one operation per step. The parser still rejects mixed/unknown/unsafe decisions rather than guessing a command from free text. In-memory command/plan/photo types and the reviewed one-use TYPE ONLY boundary are preserved. Neither model output nor a photo marks a step successful.

Explicit Check & use key performs HTTPS GET of the selected model's metadata. Feedback distinguishes rejected credentials, missing metadata permission, model access, rate limits and network/TLS failures. A successful check establishes key acceptance and model visibility; billing, Responses-write permission and actual image/generation requests still require user testing. Redirects are blocked, response size is bounded, and no provider body/key is logged.

Remembering is unchecked by default. Verified temporary keys are memory-only for a later explicit request; verified remembered keys use the unchanged Android Keystore vault. Failed checks keep existing saved credentials. Edits/model changes/background/dismissal cancel checks and invalidate late results. Using a saved key or Forget clears any previously queued temporary key. Opening camera consumes no key; explicit Send photo consumes the next-use key.

## Preserved core and reviewed exceptions

HidManager, HidDescriptor, HidReports and Diagnostics remain unchanged. Exact manual key/mouse/report mapping is preserved; UI callbacks serialize diagnostics on the main thread. Ordinary manual input remains distinct from the strict reviewed command sender. Existing manual-send limitations from the earlier security review have not been silently redesigned in this update.

AiTransportScope, ApiKeyVault, PhotoCaptureController and CommandSafety remain unchanged. There is no desktop helper, phone-side scan, location/storage permission, shell execution, automatic Enter in the reviewed workflow, analytics or new dependency/SDK migration. FOREGROUND_SERVICE and FOREGROUND_SERVICE_CONNECTED_DEVICE are normal permission additions for the explicit Bluetooth session. POST_NOTIFICATIONS is not requested; Android 13+ can therefore show the foreground session in its active-app/task controls while limiting notification-drawer visibility.

Application ID remains com.blackcat.remote. UI11 has versionCode 12 and versionName 11.0-compact-controls. Local preview signing uses the retained development certificate; compare its signer to UI10 before offering a direct upgrade. The older UI09 release uses a different certificate. A fresh install removes old local settings/keys; do not casually uninstall to bypass a signing error.

## Automated checks

Run all policy/mobile/feedback mutation tests, catalogue validation, artwork decode and planner_lab tests; both Android variant unit tests, Lint, builds, APK identity/permissions/service/icon/signer checks; CodeQL; and the AI-only rendered fixture runner.

The native runner renders real app windows with explicitly synthetic key/API/typing data, checks the enable prompt without enabling/pairing Bluetooth, verifies request cancellation and strict parsing, and captures an actual emulator Camera2 JPEG. It sends no live API request or HID input. Require UI11_SMOKE_PASS from the current run; missing markers, assertions, capture errors or collection failures remain failures. Backing-surface waits are bounded; captures never fall back to fabricated screenshots.

## Required phone and computer acceptance

1. Install the exact UI11 APK and confirm its icon, version and Home / Keyboard / AI / Settings layout. Verify target, key, model and logs are reachable only through Settings controls; check 320/360/390/412 dp widths, large text, keyboard, long commands and system bars.
2. With Bluetooth off, choose setup. Read the visible failure, accept/deny Android's enable dialog and test the fallback Settings path. Denying permissions or enablement must not start a background session or trigger repeated prompts.
3. Enable/setup and pair from the computer. Verify phone typing, supported punctuation/host layout, deliberate special keys, touchpad movement and click press/release. Test dictation, cancellation/denial/absent speech service; review text before sending.
4. Observe a sustained connection while opening Android Settings, covering/backgrounding the app and turning the screen off. Confirm the foreground session notice and explicit disconnect/stop behavior. Record OEM/battery conditions and any disconnect event. The foreground-rule fix is source-backed but does not prove this particular handset's cause or behavior.
5. Interrupt approved AI/library typing by backgrounding, disconnecting, toggling Bluetooth or changing host. Verify approval expires and partially sent input does not resume. Opening AI/library/camera/settings never types.
6. Check invalid/valid/restricted keys, metadata permission and selected-model access with a user-held key. Verify optional encrypted saving, next-use memory selection, Forget, denial/network/TLS errors, cancellation and no late key replacement. Do not share a key or terminal secrets in logs/screenshots.
7. Generate real plans and clarification-only responses. Verify separate command/explanation fields and one reviewed step. Submit changed text/voice feedback and apply or discard proposed revisions; uncertainty is not success.
8. Capture a real terminal photo, review/retake it, remove secrets and explicitly upload. Test unclear images and restricted/billing/model failures. Review photo interpretation and proposed commands before applying; never auto-type or press Enter.
9. Record phone/model/API, computer OS/layout, APK SHA-256, signer and actual results. Correct demonstrated defects and rerun affected/final gates before baseline promotion or release.

## Official evidence

- https://developers.openai.com/api/docs/models/gpt-6.1-sol
- https://developers.openai.com/api/docs/models/gpt-6-luna
- https://developers.openai.com/api/docs/guides/structured-outputs
- https://github.com/aosp-mirror/platform_frameworks_base/blob/android-12.0.0_r1/core/java/android/bluetooth/BluetoothHidDevice.java
- https://github.com/msft-mirror-aosp/platform.packages.apps.Bluetooth/blob/android-12.0.0_r1/src/com/android/bluetooth/hid/HidDeviceService.java

This remains a phone-test development candidate. Automated/emulated results do not prove physical Bluetooth stability, phone Keystore/speech/camera behavior, current account/model access or shell-command success. No old release or baseline is overwritten.
