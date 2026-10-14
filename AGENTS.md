# Black Cat AI Remote — governing rules for Codex

This repository has a long history of physically working Bluetooth code being damaged while adding UI and AI features. These rules are not suggestions. They are the development hierarchy for every task.

## Mandatory reading order

Before editing any source, read:

1. `CODEX_START_HERE.md`
2. `docs/codex/FULL_PROJECT_HANDOVER.md`
3. `docs/codex/CHAT_HISTORY_TIMELINE.md`
4. `docs/codex/NEXT_TASK.md`
5. `docs/UI09-TEST-PLAN.md`
6. the current `README.md`

Do not begin from `main`. The default branch contains release-facing documentation and an older source tree. The complete UI09 source and this handover are on:

`codex/ui09-handover-2026-10-12`

This handover branch was created from UI09 branch head:

`85ba15ec22399806f3c5bdae4795e55dd65c1f16`

The exact source used to build the released UI09 APKs was:

`2153ab44a6fb98ecb35bfd849b8e629a27748a80`

The difference is documentation only: commit `85ba15ec...` added exact release URLs and testing instructions after the APKs were published.

---

# Priority 0 — the golden rule

## Preserve the working Bluetooth core and build around it

The physically successful normal-HID reference is:

`9771772bbe25dd8eba8d7303f7e4680aa84175df`

The computer discovers and pairs with the Android phone as a standard Bluetooth HID keyboard/mouse. No desktop companion application, browser bridge, custom PC driver, Wi-Fi transport, root service or phone-side scan-first replacement is permitted unless the user explicitly approves a separate experiment.

Never rewrite or casually clean up working Bluetooth code. Never infer that a newer architecture is better simply because it is cleaner. A working baseline outranks a theoretical redesign.

The following source is protected by policy checks and must remain byte-for-byte stable unless the user approves a separately isolated Bluetooth experiment:

- `app/src/main/java/com/blackcat/remote/HidManager.kt`
- `app/src/main/java/com/blackcat/remote/HidDescriptor.kt`
- `app/src/main/java/com/blackcat/remote/HidReports.kt`
- `app/src/main/java/com/blackcat/remote/Diagnostics.kt`

The ordinary `MainActivity` Bluetooth, permission, typing, touchpad, keyboard and lifecycle behaviour is also guarded. UI work must wrap the known input path rather than replacing it.

Never edit locked/reference branches or overwrite old APKs and releases. Previous artifacts are evidence and rollback points.

---

# Priority 1 — user control

AI and the offline command library may propose and explain commands. They may not execute shell commands on Android or on the target computer.

Required interaction boundary:

1. show the goal and target system;
2. show a reviewable plan;
3. show one step at a time;
4. show the exact command;
5. show the actual current Bluetooth destination;
6. require a fresh one-use confirmation;
7. type text only;
8. never append Enter;
9. require the person to inspect the computer and press Enter manually.

Selecting a command, opening a screen, generating a plan, confirming a photo interpretation, copying text or navigating the tree must never type anything.

No run-all, no automatic retry, no background replay, no hidden queue and no resuming a partially transmitted command.

A host change, Bluetooth interruption, Activity backgrounding, plan revision or consumed approval invalidates the approval.

---

# Priority 2 — separation of concerns

Keep these layers independent:

- Bluetooth/HID core: registration, descriptor, reports, pairing callbacks and connection state.
- Manual remote UI: direct send-text, touchpad, clicks and keyboard keys.
- AI planner: OpenAI request, strict plan parsing, plan/step/preview/result UI.
- Photo feedback: reviewed Camera2 capture and explicit analysis request.
- Offline Linux library: local catalogue, tree navigation, search and explanations.
- Diagnostics: redacted operational state only.

The AI client must not register, connect, disconnect, pair, close or otherwise manage Bluetooth. The offline catalogue must not perform network requests. The Bluetooth path must continue to work when AI fails or is unavailable.

---

# Priority 3 — credentials and privacy

There is no repository, CI or proxy-held OpenAI key for normal app operation.

The AI variant accepts a user-supplied key on the phone. Optional storage uses Android Keystore-backed encryption and has no plaintext fallback. Never hardcode, log, export, recover or copy a key from chat, Actions secrets, local files or previous test environments.

Do not log or persist:

- API keys;
- user goals;
- command text;
- terminal photographs;
- typed text;
- Bluetooth MAC addresses beyond the bounded UI session information already reviewed;
- derived key identities.

The offline flavor has no AI networking. The AI flavor has the reviewed `INTERNET` and `CAMERA` additions required for AI/photo functions, in addition to the shared minimum Bluetooth permissions. The user's UI10 request authorizes `RECORD_AUDIO` in the AI flavor only for explicit, foreground spoken goals/feedback. Request it only after a dictation action; do not save audio, start recording when a screen opens, or submit a transcript automatically. Prefer on-device recognition and disclose when the Android speech provider may process audio online. Do not add microphone capability to the offline flavor, Accessibility Service, broad storage/gallery permissions, analytics, advertising, telemetry or crash-reporting SDKs.

`android:allowBackup` remains false. Cleartext traffic remains disabled.

---

# Priority 4 — mobile-first UI

The application is always operated on a phone.

The approved visual language is:

- black cat on white or near-white space;
- operational text and controls on dark charcoal/black-green panels;
- vivid green primary actions and state highlights;
- large, obvious touch targets;
- minimal horizontal crowding;
- no controls hidden behind Android status/navigation bars;
- settings separate from the task flow;
- expandable diagnostics kept out of the normal workflow;
- the cat may overlap card decoration but must never obscure text, input or hit targets.

The current planner flow is:

`Plan → Step → Preview → Result`

The offline tree is:

`Category → Topic → Command → Detail`

Test at phone widths equivalent to 320, 360, 390 and 412 dp, with large text, soft keyboard, long commands and landscape considered.

---

# Priority 5 — change control

Use a new feature branch from this handover branch for substantive work. Do not work directly on `main`, baseline branches or release tags.

Before editing:

- record current branch and full commit SHA;
- run the preservation checks;
- identify exactly which files will change;
- explain what will remain untouched;
- establish a rollback point.

Prefer small commits and exact guarded changes. Do not mix Bluetooth, UI, API, catalogue and dependency changes in one commit.

Never upgrade SDKs, Gradle, Kotlin, dependencies or models merely because a newer version exists. Dependency or API migrations need their own justification and tests.

---

# Priority 6 — testing and release truthfulness

A successful compile is not proof of Bluetooth correctness. A green CI run is not proof of physical pairing, reconnection, keyboard layout, Camera2 behaviour, on-device Keystore operation or successful shell execution.

Before publishing a candidate, run the full relevant policy, unit, Lint, APK inspection and CodeQL gates. Publish the exact tested APK bytes with source SHA and SHA-256. Never rebuild solely to rename a file. Never overwrite a previous release.

UI09 is currently a physical-test prerelease. It must not be promoted as the protected working baseline until the user records physical Android/computer acceptance.

Required manual evidence is in `docs/UI09-TEST-PLAN.md`.

---

# First response expected from Codex

Before changing code, report:

- current branch and commit;
- whether the full UI09 source is present;
- whether protected file hashes pass;
- build/tool availability;
- tests actually run and their results;
- the exact proposed next change;
- files that would change;
- rollback point;
- physical tests that Codex cannot perform.

Do not claim completion, bug-free status or physical Bluetooth success without evidence.
