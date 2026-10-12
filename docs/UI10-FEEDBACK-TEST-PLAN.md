# UI10 — editable Linux plans and reviewed feedback

Development branch: `feature/ui10-feedback-tree-camera`.
Rollback/source parent: `63648e85eb333741a1858979123a70b3cff5a209` (`codex/ui09-handover-2026-10-12`).
The existing UI09 release remains a separate physical-test prerelease; this work does not promote or overwrite it.

The user's new reference image guides the white cat header, charcoal task panels, green actions, and Plan → Step → Preview → Result progression. Native Android views and the valid portrait/peek/emblem assets are retained. Rendered testing found that the original full-cat WebP was truncated by 623 bytes and could not be decoded; UI10 replaces that broken file with a white-background full-cat asset generated from the existing approved portrait. The replacement is checksum-guarded, and header fitting avoids cropping the cat or overlapping text.

## What changed

- Browse the existing 212-command Category → Topic → Command → Detail tree and add an entry to the current plan without an API request or Bluetooth input.
- Edit or remove a step; titles, exact commands, explanations and expected results remain reviewable. Edited commands are conservatively labelled HIGH risk and require renewed review.
- Move between individual steps, copy one reviewed command, and use the existing one-use TYPE ONLY sender. Enter is never appended by the reviewed planner/library path.
- Enter editable terminal feedback on Result, including clarification answers and changed circumstances. Request a replacement plan explicitly, review the proposed replacement, then apply or discard it.
- Capture and review an exact in-memory Camera2 image in the current goal/target/step context. Explicitly upload it for analysis, then return its interpretation and proposed commands to the plan for separate review. Photos cannot mark success or approve typing.
- Fix the inherited camera-screen crash by placing decoration on a FrameLayout around the TextureView, which cannot display its own background drawable.
- Dictate a goal or feedback with explicit consent and an AI-only runtime microphone permission. Transcripts are editable and are never submitted automatically. Prefer Android on-device recognition; if unavailable, disclose the speech provider's possible online processing. No audio recording is written by the app.
- Only a user action records a step's observed success. Sending text or accepting model output does not imply shell execution or successful completion.
- Backgrounding cancels API requests, dictation, pending revisions and typing approvals. The open planner retains its in-memory draft and observes a fresh transport session on return. Closing/destroying the Activity discards that state; there is no persistent plan history or partial-send replay.

## Preserved boundaries

HidManager, HidDescriptor, HidReports, Diagnostics and MainActivity remain unchanged. AiTransportScope, OpenAiPlanner, CommandPlan, ApiKeyVault, PhotoAnalysis, OpenAiPhotoAnalyzer and Camera2 capture implementation remain unchanged. No dependency, SDK, model or transport migration is included. Both flavors retain the original application ID and non-debuggable preview setting.

The only added application permission is RECORD_AUDIO in the AI flavor, justified by the user's spoken-feedback request. Offline remains Bluetooth-only, without camera, microphone or Internet. No storage/gallery permission, analytics, Accessibility Service, automatic Enter, run-all or phone-side command execution was added.

The production planner retains its strict ordered-command schema. This is an editable, human-controlled workflow, not an autonomous dependency/execution engine. Feedback revisions use the existing 2,000-character request budget with the goal, current/remaining commands and actual user-confirmed commands; requests that do not fit are rejected without silently truncating context. Users can shorten feedback or reduce the editable plan, and review a proposed replacement before it becomes current. A tree-first selection fills an initially blank goal with the selected task title without submitting it.

## Local/CI commands

```bash
python3 tools/check_policy.py
python3 tools/test_policy.py -v
python3 tools/check_mobile_ui.py
python3 tools/test_mobile_ui.py -v
python3 tools/check_offline_catalog.py
python3 tools/check_feedback_ui.py
python3 tools/test_feedback_ui.py -v
python3 -m unittest planner_lab.test_contract -v
gradle testOfflineDebugUnitTest testAiDebugUnitTest lintOfflineDebug lintAiDebug \
  assembleOfflineDebug assembleAiDebug --continue --console=plain --stacktrace
```

Use an isolated environment with the pinned planner_lab requirements installed. In this cloud instance, source `/workspace/.autophone-tools/activate.sh` and use `/workspace/.autophone-tools/planner-venv/bin/python` for planner_lab.

APK inspection must confirm exact flavor permissions, app identity, non-debuggable status and signature. Run CodeQL security-and-quality analysis; record inherited quality findings rather than suppressing them.

## Rendered UI checks and screenshots

`FeedbackSmokeInstrumentation` is an AI-only, credential-free instrumentation runner restricted to a disposable emulator. It renders real app pages using explicitly synthetic plan/analysis fixtures and captures each app window with PixelCopy, excluding unrelated emulator/system overlays. It never presses a typing-approval button or sends an API request. Screenshot protection is cleared only for these seeded test dialogs; production FLAG_SECURE remains enabled. App-window screenshots do not establish that the emulator's own System UI is healthy.

```bash
gradle assembleAiDebug assembleAiDebugAndroidTest
adb install -r -g app/build/outputs/apk/ai/debug/app-ai-debug.apk
adb install -r -g app/build/outputs/apk/androidTest/ai/debug/app-ai-debug-androidTest.apk
adb shell am instrument -w com.blackcat.remote.test/com.blackcat.remote.FeedbackSmokeInstrumentation
```

Check for the current run's UI10_SMOKE_PASS marker, not merely adb's exit status. UI10_SMOKE_FAIL or a missing pass marker is a failure. Screenshots are written in the target app's private `files/ui10-rendered` test directory. Retrieve only fixture images from the disposable emulator; do not weaken production screenshot/privacy protections on a real user phone.

The runner covers home/remote, Bluetooth controls, settings, target, library tree/search/detail, generated plan, step editor, proposed replacement, disconnected preview, Result, voice consent, and camera review/analysis. Its AI response and transmitted-result displays are fixtures, not proof of live API or physical HID success. An emulated Camera2 capture is distinct from acceptance on the intended handset.

## Required handset/computer acceptance

1. Re-run the UI09 Bluetooth, ordinary keyboard/mouse, reconnect, background and layout checks from docs/UI09-TEST-PLAN.md.
2. Browse/search the tree without networking or Bluetooth and add a command to a local plan. Nothing should type.
3. Edit the step; verify hidden controls are rejected and old confirmations cannot be reused.
4. Generate a harmless plan using a user-held key. Move through Plan, Step, Preview and Result. Verify one exact command is typed without Enter.
5. Confirm a result manually, enter an error or changed circumstance, request a replacement and keep the original until Apply updated plan is pressed.
6. Change feedback/target or background the app during a request; verify stale replies and typing approvals cannot be applied or replayed.
7. Grant microphone permission only after choosing dictation. Review/correct the transcript; check denial, absent speech service, network-dependent provider, cancellation and background behavior. Nothing should submit automatically.
8. Grant camera permission explicitly, capture/review/retake a clear terminal image, remove secrets, and approve upload. Verify unclear images ask questions; photo feedback never marks success or types a command.
9. Check 320, 360, 390 and 412 dp phone widths, large text, soft keyboard and long commands; all actions must remain scrollable and above system bars.
10. Record handset/model/API, host OS/layout, APK checksum and any partial text after interruption. Current cloud results do not establish these physical outcomes or current account/model availability.

No new release or merge is authorized by this document. Keep the feature PR draft until required physical evidence is reviewed.
