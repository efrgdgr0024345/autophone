# Black Cat AI Remote — full Codex project handover

Prepared: **2026-10-12**

This document transfers the product intent, technical state, repository history, design decisions, safety boundaries, user expectations and remaining work from the previous ChatGPT development conversations into the repository itself.

It is intentionally detailed because Codex will not have access to the earlier chat history.

---

# 1. Executive summary

Black Cat AI Remote is an Android application with two related capabilities:

1. make a compatible Android phone act as a normal Bluetooth HID keyboard and mouse for a computer; and
2. help the user operate a Linux computer by proposing or locating commands, explaining them, and typing only the exact text the user has reviewed.

The project has two build variants:

- **CatAI** — Bluetooth remote, online OpenAI planner, reviewed photo feedback and offline Linux command library;
- **CatBT** — Bluetooth remote and offline Linux command library, without the OpenAI client/network mode.

The latest published candidate is **UI09**, release tag `ui09-2153ab44`.

The most important historical lesson is that Bluetooth had previously worked, then was damaged when AI and UI features were integrated too aggressively. The governing development principle is therefore:

> Preserve the working Bluetooth core and build around it.

No future work may treat the Bluetooth layer as disposable infrastructure.

UI09 passed its automated release workflow, but it has not yet received recorded physical acceptance on the user's intended Android handset and computer. It remains a prerelease and must not be called bug-free or promoted as the protected baseline.

---

# 2. Where Codex must start

Repository:

`https://github.com/efrgdgr0024345/autophone`

Codex handover branch:

`codex/ui09-handover-2026-10-12`

This branch contains the full UI09 source plus the current handover documentation.

Do not start implementation from `main`.

At the time of handover:

- default branch: `main`;
- `main` head: `83380e2a3e26a43899fe9596a08d91a3f27dba91`;
- active UI09 development branch before handover: `feature/ui09-planner-offline-library`;
- UI09 documentation head: `85ba15ec22399806f3c5bdae4795e55dd65c1f16`;
- exact released UI09 source: `2153ab44a6fb98ecb35bfd849b8e629a27748a80`.

The release source and documentation head differ only because the latter added exact download URLs and test instructions after the release. The APKs were built from `2153ab44...`, not from later documentation commits.

A comparison at handover showed `feature/ui09-planner-offline-library` and `main` had heavily diverged: the feature lineage was 111 commits ahead and 13 commits behind, with their merge base at `9771772bbe25dd8eba8d7303f7e4680aa84175df`.

Do not blindly merge `main` into the UI09 lineage or vice versa. Inspect the history and resolve deliberately. The default branch's public README is current, but its source tree is not the current UI09 implementation.

Mandatory reading order:

1. `AGENTS.md`
2. `CODEX_START_HERE.md`
3. this document
4. `docs/codex/CHAT_HISTORY_TIMELINE.md`
5. `docs/codex/NEXT_TASK.md`
6. `docs/UI09-TEST-PLAN.md`
7. `README.md`

---

# 3. User's product vision

The user wants the phone to behave like a real, ordinary Bluetooth keyboard and mouse.

The intended experience is:

1. open Black Cat on the Android phone;
2. make the phone discoverable;
3. open Bluetooth settings on the computer;
4. discover the phone as a keyboard/mouse input device;
5. pair normally;
6. type and move the mouse with no companion program installed on the computer.

The computer-side environment may be Linux, Windows or another system that accepts standard Bluetooth HID devices.

The user explicitly does not want:

- desktop companion software;
- a browser extension;
- a custom PC driver;
- Wi-Fi as the primary transport;
- a web page pretending to be the Bluetooth link;
- phone-side scan-first architecture replacing the known host-initiated pairing path;
- hidden automation;
- automatic shell execution.

The AI extension is intended to work like this:

1. the user states a Linux goal;
2. AI proposes a plan;
3. the user inspects each step;
4. the app displays the exact command and what it is expected to do;
5. the user approves one command;
6. the phone types that text into the connected computer;
7. the phone does not press Enter;
8. the user checks the computer and presses Enter manually;
9. the user reads the result or submits a reviewed photo for further help.

The offline extension is intended to provide help when no API or network connection exists. The current implementation is a large static Linux command reference and search system. It is **not** a local generative language model. Do not describe it as an offline LLM unless a real local model is later added and tested.

---

# 4. Golden rules in priority order

## 4.1 Preserve working behaviour

Working functionality outranks a cleaner rewrite.

Do not replace a working component simply because another architecture appears more elegant. Do not casually refactor frozen Bluetooth code while implementing UI, AI, catalogue or documentation work.

Before any high-risk change, answer:

- What is the last known working reference?
- Which exact files and methods will change?
- Which behaviour is protected?
- What test proves the protected behaviour did not move?
- What is the rollback commit?

## 4.2 Use recovery points

Every meaningful change must occur on a branch with a known starting commit.

Do not overwrite existing releases, APKs, branches or tags. Old artifacts are rollback evidence.

Use short, specific commits. Do not combine a Bluetooth change, UI redesign, API migration and dependency update in one commit.

## 4.3 Keep the user in control

No command may be sent merely because it was generated, selected, viewed, copied or accepted as an interpretation.

The only approved keyboard automation boundary is a fresh, explicit one-use **TYPE ONLY** action for the exact command and current destination.

Never:

- append Enter;
- send multiple commands automatically;
- run all steps;
- replay a prior approval;
- retry a partial transmission;
- resume after interruption;
- execute a process on Android;
- interpret a photo as authority to type;
- claim that a command succeeded without user evidence.

## 4.4 Separate layers

Bluetooth must work when AI fails.

AI must not own the Bluetooth lifecycle.

Offline command browsing must not need networking.

Photo analysis must not bypass command review.

UI code must call stable boundaries rather than duplicate the transport.

## 4.5 Be truthful about testing

A compile proves compilation.

A unit test proves only the tested contract.

An emulator proves only emulator behaviour.

CI cannot prove that a specific Android Bluetooth stack and computer host will pair, reconnect and transmit correctly.

Never use “complete”, “working”, “bug-free” or “physically verified” without the matching evidence.

## 4.6 Mobile first

The user always operates the system on a phone.

Layouts must prioritize one-handed use, readable text, large touch targets, soft-keyboard behaviour and Android system bars. A desktop-looking control panel is not acceptable.

## 4.7 Full download links are part of completion

When an APK is published for testing, the README must contain the full direct HTTPS URL, SHA-256, exact source commit, release page and test status.

Do not stop at “available under Releases”. Do not invent a URL before the release exists.

---

# 5. Approved design language

The user supplied a large multi-screen image named “Black Cat AI Remote – UI Update” as the design guide.

The essential visual requirements are preserved here because Codex cannot see that chat attachment.

## 5.1 Brand identity

- A realistic black cat with vivid green eyes is the central visual identity.
- The cat should be presented on a white or near-white background so it stands out.
- Operational text and tools sit on black, charcoal or very dark green panels.
- Bright green is reserved for primary actions, progress and success/ready state.
- The same cat identity should be retained across screens; do not substitute arbitrary clip art.
- Existing repository assets include full-cat, peek, portrait and emblem variants.

Historical artwork direction also included:

- forward-facing black cat;
- green eyes looking toward the viewer;
- more of the body visible on the main screen;
- tail historically requested on the viewer's left;
- friendly/intelligent rather than aggressive;
- tagline used elsewhere: “Stealth. Intelligence. Independence.”

The current app copy uses “Remote · Automate · Control”. Do not change branding casually.

## 5.2 Screen composition

The visual guide showed eight principal phone screens:

### Home / Remote

- large cat on white at the top;
- “Black Cat AI Remote” identity;
- connection/discoverability status;
- prominent green Make Discoverable action;
- large tiles for Linux Assistant, Send Text, Touchpad and Keyboard;
- diagnostics collapsed below normal tools.

### Bluetooth Connect

- cat peeking over the dark control panel;
- clear discoverability toggle/state;
- normal keyboard and mouse pairing explanation;
- current/known device status;
- no replacement of the computer-initiated HID pairing architecture.

### Linux Assistant — Plan

- visible four-stage progress indicator;
- goal input;
- target system dropdown;
- custom target/settings path;
- one obvious green Get Plan button.

### Linux Assistant — Step

- one step shown at a time;
- previous/next navigation;
- plain-language explanation;
- obvious statement that the user remains in control;
- no automatic Enter.

### Command Preview

- exact command in a high-contrast monospace panel;
- copy/review affordance;
- strong TYPE ONLY action;
- reminder that Enter is not sent.

### Result

- confirmation that text was typed, not executed;
- instructions to inspect the computer and press Enter manually;
- guidance for reading the result and continuing.

### Target / Settings

- target-system dropdown;
- add/edit custom systems;
- API key and model controls for the AI build;
- remember-target preferences;
- settings separated from the main goal entry.

### Photo Feedback

- cat on white;
- take-photo and reviewed-image flow;
- explicit submission to AI;
- no continuous camera upload;
- no assumption that terminal text is trusted instruction.

## 5.3 Mobile layout constraints

- Controls must not sit behind Android navigation buttons.
- Use system-bar insets.
- Avoid tiny horizontal button rows where labels truncate.
- Long commands must remain readable and selectable.
- Large font settings must not make primary actions unreachable.
- The soft keyboard must not cover the goal and Get Plan controls without a scroll path.
- The cat may visually overlap a panel edge but cannot cover text, buttons or input.
- Diagnostics and advanced details should be expandable rather than permanently consuming the screen.

The app currently uses programmatic native Android views rather than Compose or layout XML. Do not migrate UI frameworks as an incidental change.

---

# 6. Repository history and major milestones

The detailed chronological version is in `CHAT_HISTORY_TIMELINE.md`. This section records the most important engineering milestones.

## 6.1 Early Bluetooth experiments

The project began as a simple Android remote with a text field, send button, mouse area and debug display.

Early BLE/GATT versions could transmit data, and at points the user reported typing and mouse movement, but the computer did not consistently enumerate the phone as a normal HID keyboard. The user rejected any solution that only worked through a custom service or companion mechanism.

The requirement became explicit: the phone must advertise/register as a standard Bluetooth HID keyboard/mouse from the start.

## 6.2 Proven-source investigation

The project adopted and credited parts of proven open-source approaches:

- Linkpad-derived keyboard/mouse core;
- GhostBoard-derived pairing/lifecycle work.

The repository retains their licences and attribution. Do not remove or obscure them.

## 6.3 BlackCat V1 and V2

Important commits:

- `ebe61292ca148d63e21ec0c7e7c02ca694385ec8` — BlackCat V1, minimal Linkpad-derived keyboard and mouse;
- `9771772bbe25dd8eba8d7303f7e4680aa84175df` — BlackCat V2 clean normal Bluetooth HID keyboard/mouse.

V2 is treated as the physically successful Bluetooth baseline and the golden behaviour reference.

Protected/reference branches include names such as:

- `WORKING-PHYSICAL-V2-DO-NOT-MODIFY`;
- `baseline/bluetooth-v2-9771772b`;
- `working-normal-hid-physical-success`;
- `working-v3-physical-hid-success`.

Branch names and release metadata are evidence, not administrator-enforced locks. Do not claim they are technically immutable.

## 6.4 AI integration regression

AI functionality was later integrated into the app. The user reported that this work broke the Bluetooth code.

The key lesson was that preserving only files with “HID” in their name was not enough. The Activity lifecycle, ordinary send path, application identity, permissions and UI wiring were also part of the working Bluetooth system.

This led to the API02 repair and strict policy checks.

Important repair reference:

- `7712ea981230a2782a83b0920d3da5f09bc47c1d` — API-connected repair preserving the V2 input path.

Preserved release:

`https://github.com/efrgdgr0024345/autophone/releases/tag/preserved-v2-api02-20260929`

## 6.5 UI progression

The app then advanced through several controlled UI candidates, including UI03, UI04, UI06, UI07 and UI08.

Notable references:

- UI04 release: `ui04-ab34f45d`;
- UI06 photo-enabled lineage: `ui06-9c46792d`;
- UI07 release: `ui07-c9cb8c35`;
- UI08 source/release: `6eec650cb3fdf2f0e5b9b463c9c20c10539eaa29`, tag `ui08-6eec650c`.

UI08 established the approved full-screen Black Cat design lineage above the protected Bluetooth/API/photo core.

## 6.6 UI09

UI09 added:

- clearer `Plan → Step → Preview → Result` planner progression;
- explicit **Start with step 1** action;
- a fully local Linux command library;
- 212 commands, 13 categories and 63 topics;
- category/topic/command/detail navigation;
- ranked local search;
- risk, expected output, administrator and compatibility explanations;
- offline-library access in both app flavors.

Exact release source:

`2153ab44a6fb98ecb35bfd849b8e629a27748a80`

Release tag:

`ui09-2153ab44`

Release workflow run:

`https://github.com/efrgdgr0024345/autophone/actions/runs/38205227657`

The release job and CodeQL completed successfully.

---

# 7. Current release artifacts

## 7.1 CatAI-09

Full direct URL:

`https://github.com/efrgdgr0024345/autophone/releases/download/ui09-2153ab44/CatAI-09.apk`

SHA-256:

`f969bbcc24390f2acca8a5e5eec04d6a2686b811f21f75683ade3fa31d75c1db`

Size:

`3,441,281 bytes`

Contains:

- Bluetooth keyboard/mouse remote;
- AI planner;
- encrypted phone-held API key option;
- photo feedback;
- offline Linux library.

## 7.2 CatBT-09

Full direct URL:

`https://github.com/efrgdgr0024345/autophone/releases/download/ui09-2153ab44/CatBT-09.apk`

SHA-256:

`8753552ba6703451d5e4c02e4a79e00fbe6448395493c34f6e6a0cd5f7df992f`

Size:

`3,281,317 bytes`

Contains:

- Bluetooth keyboard/mouse remote;
- offline Linux library;
- no OpenAI client/network mode.

Both variants share application ID `com.blackcat.remote`. They are alternate builds of one application and should not be installed side by side.

Release checksum file:

`https://github.com/efrgdgr0024345/autophone/releases/download/ui09-2153ab44/SHA256SUMS`

Release source record:

`https://github.com/efrgdgr0024345/autophone/releases/download/ui09-2153ab44/SOURCE.txt`

Do not overwrite or silently replace these artifacts.

---

# 8. Android project structure

The project uses:

- Java 17;
- Gradle 8.9 supplied by CI/environment;
- Android Gradle Plugin 8.7.3;
- Kotlin 1.9.23;
- compile SDK 35;
- target SDK 35;
- minimum SDK 28;
- Kotlin coroutines Android 1.8.0;
- JUnit 4.13.2.

The repository does not rely on a checked-in Gradle wrapper for its primary workflow. GitHub Actions provisions Gradle 8.9.

## 8.1 Build flavors

`app/build.gradle` defines capability flavors:

- `offline`;
- `ai`.

UI09 version:

- versionCode: 11;
- versionName: `9.0-planner-offline-library`.

Both flavors retain the same application identity. The debug build type is intentionally non-debuggable in the preview configuration.

## 8.2 Shared source set

Path:

`app/src/main/`

Contains:

- Bluetooth/HID implementation;
- ordinary remote UI;
- shared Black Cat styling and artwork;
- command approval/sending safety;
- offline Linux catalogue and UI;
- shared command assets;
- licences.

## 8.3 AI source set

Path:

`app/src/ai/`

Adds:

- `INTERNET` and `CAMERA` permissions;
- OpenAI planner;
- API key vault;
- AI transport observation;
- planner UI;
- photo capture and analysis;
- AI-specific resources.

## 8.4 Offline source set

Path:

`app/src/offline/`

Provides the offline `AiEntry` implementation. It opens the local Linux library instead of an OpenAI planner and does not require the AI networking components.

## 8.5 Test source sets

- shared tests under `app/src/test/`;
- AI-specific tests under `app/src/testAi/`;
- Python planner contract/state tests under `planner_lab/`;
- repository policy and mutation checks under `tools/`.

---

# 9. Bluetooth and manual input architecture

## 9.1 Protected files

These are byte-frozen by the policy checker:

- `HidManager.kt`;
- `HidDescriptor.kt`;
- `HidReports.kt`;
- `Diagnostics.kt`.

Current expected Git blob identifiers are recorded in `tools/check_policy.py`.

Do not change expected hashes to make an unapproved modification pass.

## 9.2 HidManager responsibilities

`HidManager`:

- obtains the Android `HID_DEVICE` profile;
- registers the app with Bluetooth HID settings;
- exposes the discoverability intent;
- tracks registered and connected state through callbacks;
- sends keyboard and mouse reports;
- answers host report requests;
- unregisters and closes the profile on final Activity destruction.

The SDP identity currently describes Black Cat as a phone keyboard and mouse.

The known pairing path is computer initiated. `MainActivity` requests discoverability only after HID registration is confirmed.

## 9.3 HID reports

The descriptor includes keyboard and mouse report IDs.

`HidReports` contains:

- mouse button masks;
- modifier masks;
- US-ASCII character-to-HID mapping;
- shifted punctuation handling.

The reviewed command-sending path accepts printable US-ASCII only. This prevents newline, tab, escape and hidden control characters from being inserted through command approval.

The host keyboard layout can affect rendered punctuation. Automated tests cannot prove every physical host layout.

## 9.4 MainActivity

`MainActivity` is a programmatic UI and owns the ordinary Bluetooth lifecycle.

It provides:

- splash/home UI;
- discoverability button;
- Bluetooth status;
- manual send-text;
- touchpad;
- click buttons;
- full key panel;
- diagnostics;
- entry points into AI/target/settings/library features.

Policy checks freeze the critical permission, HID initialization, discoverability, text-send, event and destroy behaviour, as well as key mappings and mouse actions.

## 9.5 Diagnostics

Diagnostics must remain content-redacted.

Do not log user text or command content. Operational events may record counts and state changes.

Diagnostics are collapsed by default in normal UI.

---

# 10. Command approval and typing boundary

Shared safety code is in:

`app/src/main/java/com/blackcat/remote/CommandSafety.kt`

Key components:

## CommandPolicy

Rejects:

- blank commands;
- commands over 512 characters;
- non-printable characters;
- newline, carriage return, tab and escape controls;
- characters unsupported by the HID key map.

## HostSession

Represents the reviewed destination and connection epoch.

## CommandApproval

One-use approval created only by the confirmation UI. It cannot be reused.

## ApprovedCommandSender

Types one printable character at a time while continually checking that the current session still matches the approved host.

It never sends Enter.

If the host changes, Bluetooth disconnects, the operation is cancelled or a report is rejected, transmission stops. A partial command is not automatically resumed.

The sender may issue a final key-release report only if the reviewed host is still current.

The offline library uses `LibraryHidTarget` to observe the current existing HID connection without acquiring or managing the Bluetooth profile.

---

# 11. AI planner architecture

## 11.1 Entry/UI

Primary file:

`app/src/ai/java/com/blackcat/remote/AiEntry.kt`

The AI panel is a full-screen native Android dialog using programmatic views.

Opening the panel must not:

- issue an API request;
- send keyboard reports;
- initialize or close HID;
- alter pairing;
- open Settings automatically.

Current stages:

`Plan → Step → Preview → Result`

The plan page contains:

- goal input;
- target dropdown and custom target editor;
- photo-feedback entry;
- Get Plan;
- offline-library entry;
- generated plan and steps;
- Start with step 1.

A user may inspect the plan and then select a step. Selection alone sends nothing.

The Step page explains one selected command, expected result, risk and warnings.

The Preview page displays the current Bluetooth destination and exact command before approval.

The Result page explains that text was typed and Enter was not sent.

## 11.2 Current planner limitations

The Android planner schema in `CommandPlan.kt` is an ordered list of suggested commands with explanations, not the full richer dependency/checkpoint model explored in `planner_lab`.

The current Android app does not fully enforce:

- step dependency completion;
- check/change/manual/decision/verify state types;
- human-confirmed result state across a full plan;
- persistent plan progress;
- automatic incorporation of terminal results into the same plan state machine.

UI09 improves the visible progression, but it should not be described as a complete autonomous workflow engine.

The separate Python planner lab contains a richer experimental state model. Do not silently replace the production Android schema with it. A migration needs a bounded design, compatibility strategy, tests and user approval.

There is also a wording inconsistency worth auditing: older Result-page copy may state that photo/text result analysis is not enabled even though the app has a separate Photo Feedback panel. Treat this as a candidate UI bug, verify the current source and fix only with tests.

## 11.3 Plan contract

`CommandPlan.kt` defines strict structured output:

- plan explanation;
- assumptions;
- clarification questions;
- up to 12 commands;
- title;
- command;
- explanation;
- low/medium/high risk;
- administrator flag;
- expected result;
- warnings.

If clarification questions exist, commands must be empty.

The command must be one printable ASCII line, within the command-policy length, with no hidden controls.

## 11.4 OpenAI request

`OpenAiPlanner.kt` uses:

`https://api.openai.com/v1/responses`

Current source model list:

- `gpt-4.1-mini`;
- `gpt-4.1`.

These identifiers reflect the current code, not a guarantee that they remain the best or available models. Any model/API change must be checked against current official OpenAI documentation and tested independently. Do not change model names from memory.

The request:

- uses a strict JSON schema;
- disables tools;
- sets tool choice to none;
- requests `store=false`;
- uses bounded output;
- has connection/read timeouts;
- blocks redirects to protect the key;
- performs no automatic retry;
- limits response size;
- maps common HTTP failures to user-visible errors.

The API client has no Bluetooth or command-target reference.

## 11.5 API key storage

`ApiKeyVault.kt` provides optional encrypted phone-held storage using Android security primitives.

There is no plaintext fallback.

Users may also use a key for one request without saving it.

Do not claim that encrypted local storage protects a key on a fully compromised phone.

Do not move the key to a website, proxy, repository, CI variable or Codex environment without explicit design approval.

## 11.6 AI transport scope

`AiTransportScope.kt` observes the existing HID session while the AI panel is active.

It must not:

- initialize HID;
- register the HID app;
- pair;
- connect;
- disconnect;
- close the manager.

It tracks session/epoch changes and invalidates AI transmission when the Activity pauses or the host changes.

---

# 12. Photo feedback architecture

AI photo classes include:

- `PhotoCaptureController.kt`;
- `PhotoFeedbackPanel.kt`;
- `PhotoAnalysis.kt`;
- `OpenAiPhotoAnalyzer.kt`.

The intended flow is:

1. user opens Photo Feedback;
2. user captures one still image inside the app;
3. user reviews the exact image;
4. user explicitly sends it to OpenAI;
5. screen content is treated as untrusted evidence;
6. unclear content produces questions/retake guidance instead of speculative commands;
7. a resulting command remains separate from photo approval;
8. the command still requires exact review and TYPE ONLY.

The Camera2 controller does not use an external camera Activity and does not cross the HID boundary.

The AI manifest adds Camera and Internet only. Do not add broad gallery/storage or microphone permissions merely for convenience.

A design image showed “Choose from Gallery”. If implementing or confirming such a path, use the Android system picker/scoped mechanism and keep broad storage permissions forbidden. Do not claim the current build has a fully tested gallery flow unless verified in source and on-device.

No continuous camera upload, background capture or gallery-wide scanning is permitted.

---

# 13. Offline Linux command library

Shared files:

- `LinuxCommandCatalog.kt`;
- `LinuxCommandLibraryPanel.kt`;
- `LibraryHidTarget.kt`;
- `linux_commands_00.b64` through `linux_commands_06.b64`.

The catalogue is checked into the APK as compressed/base64 asset chunks and loaded locally.

Current validated counts:

- 212 commands;
- 13 categories;
- 63 topics.

The UI tree is:

`Category → Topic → Command → Detail`

The detail view includes:

- command title;
- exact command;
- plain-language explanation;
- expected result/output;
- risk level;
- administrator requirement;
- compatibility notes;
- tags/search terms.

Search is local and ranks matches across titles, command text, explanations, category/topic, tags and synonyms.

The library covers broad areas such as:

- system information;
- network state, routing, DNS, Wi-Fi and ports;
- storage and filesystems;
- CPU, memory and performance;
- processes;
- services and boot;
- logs;
- users and permissions;
- packages and updates;
- SSH and security checks;
- containers;
- development tools.

Browsing, searching and copying do not need Bluetooth. Typing a library command requires a ready existing HID connection and a fresh one-use approval.

The library is a reference, not proof that every command exists on every distribution or that every output is identical. Compatibility text and target-system context matter. Do not present it as an authoritative replacement for current distribution documentation.

Risk labels are guidance, not a formal safety proof.

The offline build must remain network-free for these functions.

---

# 14. Security and privacy model

## 14.1 Shared restrictions

Forbidden unless separately reviewed:

- analytics;
- advertising SDKs;
- telemetry;
- crash-reporting services;
- Accessibility Service;
- broad storage permissions;
- microphone access;
- cleartext traffic;
- backup of app data;
- embedded API keys;
- hidden command queues;
- native process execution for AI commands.

## 14.2 Flavor-specific permissions

The shared manifest contains the minimum Bluetooth permissions needed across Android API levels.

The offline flavor must not acquire the AI networking/client capability.

The AI flavor may add the reviewed Internet and Camera permissions only for direct OpenAI and photo functions.

## 14.3 Sensitive data

Do not log:

- typed text;
- goals;
- command contents;
- terminal image contents;
- API keys;
- secrets;
- passwords;
- derived key identities.

Do not recover or reuse any API key that may have appeared in a previous conversation or test environment.

No paid live API batch is authorized merely because the project moved to Codex.

## 14.4 Photo trust boundary

Terminal text in an image may contain malicious or misleading content. It is evidence, not an instruction source.

Photo interpretation does not approve a command, prove an outcome or mark a step complete.

---

# 15. Test and policy architecture

## 15.1 Bluetooth preservation checker

`tools/check_policy.py` freezes the core Bluetooth source and critical `MainActivity` behaviour.

It verifies:

- expected Git blob hashes;
- permission set;
- application identity;
- backup/cleartext policy;
- key mapping;
- mouse and send-text wiring;
- no phone-side discovery/bonding additions;
- no AI Bluetooth management;
- licence copies.

Do not modify the checker and source together simply to make a prohibited change pass.

## 15.2 UI checker

`tools/check_mobile_ui.py` verifies:

- frozen AI transport/API files;
- required Black Cat assets;
- system-bar handling;
- planner navigation order;
- target-system controls;
- no redundant approval checkboxes;
- no API request on panel open;
- photo permission and trust boundaries;
- standalone target/settings panels;
- reviewed photo command path.

Mutation tests in `tools/test_mobile_ui.py` confirm that representative regressions are actually rejected.

## 15.3 Offline catalogue checker

`tools/check_offline_catalog.py` validates:

- counts and structure;
- unique IDs/commands as specified;
- safe one-line command constraints;
- no automatic Enter path;
- no API dependency;
- visible AI-build and offline-build entry points.

## 15.4 Planner lab

`planner_lab/` contains a richer experimental guided-plan and photo contract, fixtures and state machine tests.

It tests properties including:

- manual steps cannot contain sendable commands;
- hidden controls are rejected;
- dependencies require human confirmation;
- photo approval is not command approval;
- unreadable photos cannot produce speculative commands;
- changed inputs invalidate stale results;
- one-use approval;
- host changes reject send;
- partial sends are not resumed;
- model output cannot self-mark completion.

This lab informs future design but is not automatically identical to the Android production schema.

## 15.5 Standard commands

From the repository root:

```bash
python3 tools/check_policy.py
python3 tools/test_policy.py -v
python3 tools/check_mobile_ui.py
python3 tools/check_offline_catalog.py
python3 tools/test_mobile_ui.py -v
python3 -m pip install -r planner_lab/requirements.txt
python3 -m unittest planner_lab.test_contract -v
```

Full Android verification when tools are available:

```bash
gradle testOfflineDebugUnitTest testAiDebugUnitTest \
  lintOfflineDebug lintAiDebug \
  assembleOfflineDebug assembleAiDebug \
  --continue --console=plain --stacktrace
```

The release workflow additionally inspects APK identity/permissions/signatures, packages the exact APKs, computes checksums, attests provenance and runs CodeQL.

---

# 16. UI09 CI and release state

Workflow:

`.github/workflows/ui09-ci.yml`

The successful release run was:

`38205227657`

The final jobs passed:

- Verify and build UI09;
- CodeQL;
- Publish exact UI09 prerelease.

Automated gates included:

- frozen Bluetooth/HID behaviour;
- UI09 planner and offline-library contracts;
- 212-command catalogue integrity;
- planner/photo regression tests;
- Android unit tests;
- Lint;
- both flavor builds;
- APK identity/permission/signature inspection;
- CodeQL;
- checksum verification;
- exact APK provenance attestation.

This is strong automated evidence, but it is not physical acceptance.

The UI09 workflow is branch-filtered to `feature/ui09-planner-offline-library`. Do not push documentation-only changes to that release branch merely to trigger another build. This dedicated Codex handover branch avoids accidental publication.

---

# 17. Required physical acceptance

UI09 cannot be promoted until a person tests the intended APK on a real compatible Android phone and computer.

Record:

- phone model;
- Android version;
- computer model/OS;
- Bluetooth adapter if known;
- APK filename and SHA;
- previous app version/install method;
- each test result;
- logs for failures, with user content redacted.

Required sequence:

1. Verify APK checksum.
2. Install one variant.
3. Confirm original application identity/upgrading behaviour.
4. Start normal computer-side Bluetooth pairing.
5. Confirm phone becomes discoverable only through the intended flow.
6. Confirm keyboard typing.
7. Confirm touchpad movement.
8. Confirm left click.
9. Confirm right click.
10. Confirm disconnect/reconnect.
11. Turn Bluetooth off/on and confirm safe state/recovery.
12. Confirm ordinary remote input still works after opening/closing AI or library screens.
13. Generate a harmless AI plan.
14. Move through Plan, Step, Preview and Result.
15. Confirm TYPE ONLY types exact text and does not press Enter.
16. Change/disconnect the host before approval and confirm block/stop.
17. Background the app during approval and confirm invalidation.
18. Test Camera2/photo review and explicit send in CatAI.
19. Disable networking and browse/search the offline library.
20. Confirm copy does not type.
21. Confirm library typing requires a separate approval.
22. Interrupt a send and report any visible partial text; confirm it does not resume.

A physical failure must be investigated from the exact tested APK/source, not “fixed” by rewriting the entire stack.

---

# 18. Current known limitations and risks

## 18.1 Physical status unknown

UI09 has not yet been physically accepted. Do not infer Bluetooth, Camera2, Keystore or API success from CI.

## 18.2 Default branch does not contain current source

Codex must use the handover branch. Starting from `main` would lose major UI09 source additions.

## 18.3 Heavy branch divergence

The current feature lineage and `main` diverged from the V2 base. Any future consolidation needs a deliberate merge plan and full regression run.

## 18.4 Programmatic UI complexity

`AiEntry.kt` and `LinuxCommandLibraryPanel.kt` are large programmatic UI files. They are maintainability risks, but that is not permission to rewrite them. Refactor only in small, behaviour-preserving steps with tests.

## 18.5 Planner is not a full autonomous state machine

The Android production planner uses a simpler ordered-command schema than the experimental planner lab. Visible step progression does not mean every dependency and human-result state is enforced.

## 18.6 Offline mode is not a local AI model

The offline feature is a curated command catalogue and search system. It cannot answer arbitrary new questions like a generative model.

## 18.7 Model/API drift

The source currently lists `gpt-4.1-mini` and `gpt-4.1` and uses the Responses API contract implemented at the time. OpenAI model availability and API fields can change. Verify official current documentation before a migration and preserve deterministic tests.

## 18.8 Keyboard layout

The command sender maps US-ASCII HID usages. Physical output may differ on non-US host keyboard layouts, especially punctuation. This requires real-host testing and a separate design if broader layout support is desired.

## 18.9 Command availability varies

Linux distributions differ. Some reference commands may require packages, privileges, systemd, procfs, iproute2, NetworkManager, container tools or distribution-specific paths.

## 18.10 Risk labels are advisory

A “low” risk command can expose sensitive information or behave differently in unusual environments. The user must still review it.

## 18.11 Photo/result wording may be inconsistent

Audit Result-page copy versus the separate working Photo Feedback panel. Do not leave contradictory capability statements.

## 18.12 Release mutability

GitHub release metadata previously reported `immutable=false`. Treat releases as preservation records by policy, not cryptographic immutability. Never overwrite them.

---

# 19. User working preferences

The user expects direct, concrete progress rather than vague planning language.

Important preferences:

- keep the golden rule visible;
- do not ask the user to repeat repository facts that can be inspected;
- provide exact branch, commit, release and file information;
- use short APK filenames;
- put full direct HTTPS APK links in README;
- preserve the known working build before adding features;
- do not call something tested unless it was actually tested;
- distinguish automated checks from physical checks;
- settings should be separate from the main task;
- design for a phone first;
- no unnecessary companion tools;
- no hidden automatic acceptance;
- user remains responsible for pressing Enter;
- maintain an expandable debug/diagnostics path;
- report errors clearly rather than swallowing them.

The user can be frustrated by repeated clarification questions when repository inspection can resolve the answer. Inspect first, then ask only for information that cannot be derived.

---

# 20. Coding and review rules

## Before editing

Provide:

- current branch;
- full SHA;
- current test state;
- intended files;
- untouched protected files;
- risk level;
- rollback commit;
- acceptance tests.

## During editing

- keep changes small;
- preserve layer boundaries;
- include errors and cancellation paths;
- do not swallow exceptions silently;
- do not log sensitive data;
- do not add dependencies for trivial tasks;
- comment security intent, not obvious syntax;
- avoid unbounded retries or response sizes;
- keep UI responsive and cancellable;
- retain accessibility labels and readable touch targets.

## After editing

Report:

- exact files changed;
- exact commands/tests run;
- test counts and failures;
- build artifacts and hashes if created;
- known limitations;
- physical tests not performed;
- rollback point;
- proposed next step.

Do not auto-merge, publish or promote without explicit authority.

---

# 21. Definition of done for future APK candidates

A candidate is ready for user testing only when:

1. work occurred on a separate feature branch;
2. golden Bluetooth checks pass unchanged;
3. relevant mutation tests pass;
4. Android unit tests pass for both flavors;
5. Lint passes under the repository policy;
6. both APKs build if shared source changed;
7. APK identity and permissions are inspected;
8. CodeQL passes where applicable;
9. exact APK bytes are retained;
10. SHA-256 is recorded;
11. exact source SHA is recorded;
12. release is created without overwriting an earlier release;
13. README contains a full direct HTTPS URL;
14. release is labelled as prerelease until physical acceptance;
15. physical testing instructions are included.

A candidate becomes a protected physical baseline only after the user records successful real-device regression results.

---

# 22. Recommended first Codex report

Before proposing a code change, Codex should return a report with this structure:

```text
BLACK CAT CODEX INTAKE REPORT

Repository:
Current branch:
Current commit:
UI09 source present:
Release source relationship:
Protected Bluetooth checks:
Planner checks:
Offline catalogue checks:
Android tools available:
Builds performed:
Automated results:
Physical tests impossible in this environment:
Known inconsistencies found:
Smallest recommended next task:
Files that task would change:
Files guaranteed untouched:
Rollback point:
Risk rating:
```

The likely next useful task is to audit the UI09 source and user-test feedback, not to invent UI10 requirements before physical testing.

---

# 23. Absolute prohibitions without a separate approved task

Do not:

- rewrite the Bluetooth stack;
- change HID descriptors or reports;
- replace computer-side pairing with phone-side scanning;
- add a companion desktop application;
- add automatic Enter;
- execute shell commands from Android;
- add run-all;
- add hidden retry/replay;
- persist or log user command text;
- embed API keys;
- move credentials to an unapproved server;
- add analytics/ads/telemetry;
- add Accessibility Service;
- add microphone or broad storage permissions;
- relax policy tests to accept an unreviewed change;
- merge UI09 to main automatically;
- overwrite old releases;
- publish a new APK for documentation-only changes;
- claim physical success from CI;
- claim the offline library is a generative AI model;
- claim a command succeeded without user-observed evidence.

---

# 24. Final project philosophy

The Black Cat project is not measured by the number of features added.

It is measured by whether:

- the phone still works as the intended keyboard/mouse;
- the user understands and controls every command;
- the code can be audited and rolled back;
- the app remains useful when AI fails;
- offline help remains genuinely offline;
- the UI remains usable on a phone;
- releases are reproducible and traceable;
- claims are supported by evidence.

Protect progress. Make the smallest justified change. Test honestly. Preserve the cat and the working foundation.