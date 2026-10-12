# Black Cat AI Remote — Codex start here

Prepared: **2026-10-12**

This branch exists so a Codex project can access the complete current source plus the project decisions that previously lived only in ChatGPT conversations.

## Repository and starting branch

Repository:

`https://github.com/efrgdgr0024345/autophone`

Use this branch as the Codex project branch:

`codex/ui09-handover-2026-10-12`

Do **not** start implementation from `main`. At handover, the default branch head is:

`83380e2a3e26a43899fe9596a08d91a3f27dba91`

`main` contains the release-facing README and an older Android source tree. The current UI09 implementation lives in the lineage of:

`feature/ui09-planner-offline-library`

This Codex handover branch was created from its documentation head:

`85ba15ec22399806f3c5bdae4795e55dd65c1f16`

The exact source commit used to build the published UI09 APKs is:

`2153ab44a6fb98ecb35bfd849b8e629a27748a80`

Commit `85ba15ec...` only added the full UI09 download links and test instructions after publication. The handover commits after that are documentation only.

## Read these files before editing

1. `AGENTS.md` — non-negotiable rules.
2. `docs/codex/FULL_PROJECT_HANDOVER.md` — full technical and product handover.
3. `docs/codex/CHAT_HISTORY_TIMELINE.md` — chronological decisions, failures and lessons.
4. `docs/codex/NEXT_TASK.md` — bounded first Codex task.
5. `docs/UI09-TEST-PLAN.md` — automated and physical acceptance contract.
6. `README.md` — current release URLs and public-facing state.

## The one-sentence project goal

Make a compatible Android phone appear to a computer as a normal Bluetooth HID keyboard and mouse, then add a phone-first Linux assistant that proposes reviewable commands without taking control away from the user.

## The golden rule

> Preserve the working Bluetooth core and build around it.

The project previously had working keyboard and mouse behaviour, then damaged that behaviour while adding AI and UI features. That regression is the reason the repository contains unusually strict preservation tests.

The physically successful normal-HID source reference is:

`9771772bbe25dd8eba8d7303f7e4680aa84175df`

The required pairing model is:

1. the Android phone registers as a Bluetooth HID device;
2. the phone becomes discoverable;
3. the **computer** opens its Bluetooth settings;
4. the computer discovers and pairs with the phone;
5. the computer treats it as a normal keyboard/mouse;
6. no companion desktop software or custom driver is required.

Do not replace this with phone-side scanning, Wi-Fi, a web bridge, a desktop helper, root automation or a different Bluetooth architecture merely because it appears simpler.

## Current released candidate

Release tag:

`ui09-2153ab44`

Release page:

`https://github.com/efrgdgr0024345/autophone/releases/tag/ui09-2153ab44`

AI build:

`https://github.com/efrgdgr0024345/autophone/releases/download/ui09-2153ab44/CatAI-09.apk`

SHA-256:

`f969bbcc24390f2acca8a5e5eec04d6a2686b811f21f75683ade3fa31d75c1db`

Offline-focused build:

`https://github.com/efrgdgr0024345/autophone/releases/download/ui09-2153ab44/CatBT-09.apk`

SHA-256:

`8753552ba6703451d5e4c02e4a79e00fbe6448395493c34f6e6a0cd5f7df992f`

Both builds use the same Android application identity. Install one variant at a time.

UI09 passed the automated release gates, including policy checks, catalogue checks, unit tests, Lint, both APK builds, APK inspection, CodeQL, checksums and provenance attestation. It has **not** yet been accepted as physically working on the user's handset and computer. It remains a prerelease.

## What UI09 contains

### Shared Bluetooth remote

- standard Android Bluetooth HID registration;
- computer-initiated pairing/discoverability;
- send-text control;
- touchpad/mouse movement;
- left, middle and right click;
- full HID key panel;
- collapsed diagnostics;
- Black Cat mobile UI.

### AI flavor (`CatAI-09`)

- user-supplied OpenAI API key;
- optional encrypted on-phone key storage;
- structured Linux command planner;
- visible `Plan → Step → Preview → Result` flow;
- one command reviewed at a time;
- exact Bluetooth destination displayed before typing;
- one-use **TYPE ONLY** approval;
- no automatic Enter;
- reviewed Camera2 photo-feedback path;
- the complete offline Linux command library.

### Offline flavor (`CatBT-09`)

- no OpenAI client and no AI network mode;
- Bluetooth remote;
- the same local Linux command library;
- category/topic/command/detail tree;
- ranked local search;
- explanations, expected results, risk, administrator requirement and compatibility notes.

The checked catalogue currently contains 212 commands across 13 categories and 63 topics.

## Approved mobile design direction

The user supplied a multi-screen visual guide titled **“Black Cat AI Remote – UI Update”**. The image itself was provided in chat, so this handover records its required design in text:

- the black cat is prominent on a white background;
- operational panels are charcoal/black-green;
- primary actions and progress highlights are vivid green;
- the app is designed for a phone, not desktop-width layouts;
- touch targets are large and thumb friendly;
- the cat can peek over or rest paws on panels but cannot obstruct text or controls;
- settings are separate from the main task flow;
- diagnostics are collapsible and secondary;
- controls must remain above Android system navigation areas.

The reference screens were:

1. Home/Remote — full cat, connection state, discoverability and large remote tiles.
2. Bluetooth Connect — discoverability, keyboard/mouse pairing explanation and known devices/status.
3. Linux Assistant Plan — goal, target system and Get Plan.
4. Linux Assistant Step — one plan step at a time with clear next/back controls.
5. Command Preview — exact command and TYPE ONLY.
6. Result — command typed, manual Enter instruction and next observations.
7. Target/Settings — target dropdown, custom target and API/model settings.
8. Photo Feedback — take a photo or choose/review an image, then explicitly submit it for help.

UI09 implements the main design language and the guided planner/offline-library additions. Do not interpret the reference image as authority to rewrite the working Bluetooth transport.

## Immediate first action for Codex

Do not modify code on the first pass.

From the repository root:

```bash
git status --short
git branch --show-current
git rev-parse HEAD
python3 tools/check_policy.py
python3 tools/test_policy.py -v
python3 tools/check_mobile_ui.py
python3 tools/check_offline_catalog.py
python3 tools/test_mobile_ui.py -v
python3 -m pip install -r planner_lab/requirements.txt
python3 -m unittest planner_lab.test_contract -v
```

When Java/Android tooling is available, also run:

```bash
gradle testOfflineDebugUnitTest testAiDebugUnitTest \
  lintOfflineDebug lintAiDebug \
  assembleOfflineDebug assembleAiDebug \
  --continue --console=plain --stacktrace
```

Then provide a state report. The report must distinguish:

- source inspection;
- automated tests actually executed;
- build results;
- emulator/layout testing;
- things only a physical Android phone and computer can prove.

## Do not do these things during handover

- Do not merge to `main`.
- Do not promote UI09 to a protected baseline.
- Do not publish a new APK simply because the branch or documentation changed.
- Do not rebuild only to change the filename.
- Do not edit old releases or baseline branches.
- Do not loosen preservation tests to make a change pass.
- Do not replace the existing API-key flow with a website/proxy dependency.
- Do not add automatic Enter, command execution, run-all or hidden retries.
- Do not claim that CI proves Bluetooth works physically.
- Do not perform paid/live API test batches without explicit approval.
- Do not include secrets or recover a key from prior chat/testing history.

## First bounded task

The first Codex task is an **audit and continuation report**, not a redesign:

1. verify that this branch contains the complete UI09 source;
2. verify the golden Bluetooth checks;
3. verify the planner and offline catalogue checks;
4. identify any source/build/test inconsistency;
5. identify the exact physical tests still missing;
6. propose the smallest next development step;
7. wait for user approval before high-risk or Bluetooth changes.

The next code branch should be created from this handover branch only after that audit, using a name such as:

`codex/ui10-<bounded-purpose>`

Read the full handover before proposing that purpose.