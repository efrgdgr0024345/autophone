# UI14 command flow and simulation review

UI14.1 follows the extended image test: a feedback/plan timeout was previously
re-thrown as ordinary cancellation, leaving Auto visibly ON in REVIEW_RESULT.
It now stops, clears session resources, explains the timeout and preserves only
canonical unattempted tasks. The `timeout` instrumentation scenario raises a real
`TimeoutCancellationException` after the first transmitted operation and asserts
OFF, no extra Enter, cleared camera/key and no attempted-task replay.

User-authorized continuation of UI13 on `feature/ui14-command-flow-validation`.
Rollback: `5a6968d4ffe8e8b28e4bdb021bbb9ffda17489f7`. This is a physical-test candidate,
not a promotion of the protected Bluetooth baseline.

## Changes and usability criteria

The goal collapses once a plan exists. Exact commands precede optional roadmap,
assumptions and explanations. Selecting a command opens its detail; selection sends
nothing. Step and Preview share one approval action fixed above bottom navigation.
The ordinary approval still types once without Enter. System Back returns from
detail to the plan and then Home, preserving the draft without replay.

Auto and Voice remain above the scrolling task content. Weighted buttons measure
their wrapped text at the actual available width. Controls have at least 48 dp
height. Command text wraps without added hyphenation or ellipsis. Auto setup keeps
advanced modes and limits expandable, while retaining explicit foreground consent.
The launcher uses a black cat on white with proportional adaptive-icon insets.

On stopping Auto, only canonical remaining tasks return to the planner. Generated
capture/page/cleanup commands belong to the stopped session. A task whose first
transmission began is not silently queued again, including a partial transmission.
The result describes an attempted task, not verified success. Auto can accept an
explicit memory-only temporary API key if the plan-generation key was consumed.
Provider failures are explained in the session; credentials clear on start/stop.

The review applies visibility of system state, user control, error recovery,
progressive disclosure and recognition over recall as usability heuristics. The
primary Android guidance consulted was:

- https://developer.android.com/guide/topics/ui/accessibility/apps
- https://developer.android.com/guide/navigation/principles
- https://developer.android.com/develop/ui/views/layout/declaring-layout

This is a code and emulator usability review, not a user study or accessibility
certification. Some external heuristic manuals were blocked by network policy;
they were not represented as retrieved evidence.

## Reproducible native checks

Use the existing checkout and pinned tools:

```bash
cd /workspace/autophone
source /workspace/.autophone-tools/activate.sh
gradle '-Dorg.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=1024m' \
  testOfflineDebugUnitTest testAiDebugUnitTest lintOfflineDebug lintAiDebug \
  assembleOfflineDebug assembleAiDebug assembleAiDebugAndroidTest --console=plain
```

The Android 12/API 31 AOSP Automated Test Device image supports the disposable
emulator tests. Enable real rendering with `debug.hwui.drawing_enabled=1`; these
tests use Window PixelCopy, not a replacement image renderer. API 35 images tried
in this cloud machine had SystemUI ANRs or framework crashes and were not counted
as successful coverage. Software CPU emulation is slower than a handset.

With emulator-5554 running, use a 412 by 900 px display at 160 dpi for the matrix:

```bash
adb -s emulator-5554 shell wm size 412x900
adb -s emulator-5554 shell wm density 160
bash tools/run_feedback_ui_smoke.sh /workspace/security-review/ui14/layout layout
bash tools/run_feedback_ui_smoke.sh /workspace/security-review/ui14/native all
```

The runner checks that the installed APK SHA-256 equals the local candidate.
The matrix renders 320/360/390/412 dp windows with 100%, 130% and 200% text. It
asserts visible Auto/Voice controls, a single pinned send action, a readable task
viewport, untruncated long command text, passive navigation, and the packaged icon.
The full smoke additionally covers Home/Keyboard/AI/Settings, Bluetooth-off feedback,
plan/revision flows, camera preview and JPEG review, cancellation, transient key
handling, and Auto pagination/revision/stop/error with synthetic providers/HID.

## Image-driven Linux loop

The test-only host driver executes only the two known read-only demo commands and
the app's bounded output-collection wrappers on this disposable Linux machine.
No shell runs on Android. A matching per-run ID prevents stale fixture exchange.
The phone exercises real Camera2 capture; the driver can then inject a rendered
terminal JPEG at the observer boundary. Tesseract reads that JPEG independently;
the scripted provider must actually see matching page boundaries and an idle
prompt. The first page deliberately overflows. The app must reduce its page size,
collect the saved output, reconcile the pages, replace `whoami` with an OS diagnostic,
and finish without manual intervention. This is not live OpenAI or physical HID.

```bash
# Use a fresh output directory and a unique matching ID in both commands.
adb -s emulator-5556 root
adb -s emulator-5556 wait-for-device
adb -s emulator-5556 shell am instrument -e scenario image-injected \
  -e demoRunId UNIQUE_RUN_ID -w \
  com.blackcat.remote.test/com.blackcat.remote.FeedbackSmokeInstrumentation
# In a second activated shell:
ANDROID_SERIAL=emulator-5556 /workspace/.autophone-tools/planner-venv/bin/python \
  tools/auto_image_demo_host.py /workspace/security-review/ui14/fresh-image-run \
  --run-id UNIQUE_RUN_ID --automatic-ocr --inject-vision-frame
```

Do not use `--resume` to overwrite an unrelated `/tmp/blackcat-image-demo.log`.
Use only the disposable fixture; preserve failed-run logs separately. A video-camera
stream attempt is recorded separately from observer-boundary image injection.

## Release gates and remaining limits

Run the policy/mobile/feedback/Auto preservation checks and their mutation tests,
planner contract, artwork decode, offline catalogue, both flavors' unit/Lint/build,
APK permissions/identity/signature inspection, and traced CodeQL analysis. Publish
the tested APK bytes without rebuilding to rename. Keep old releases immutable.

Live OpenAI tests require the runtime `BLACKCAT_TEST_API_KEY` binding scoped to
`api.openai.com`. Inspect presence only; never write or log its value. Proxy-held
bindings must use their supported HTTPS route. Simulated transport tests do not
establish account billing, model availability, live vision quality or spoken audio.

Physical pairing/reconnect, actual keyboard layout, mouse behavior, phone camera
focus/glare, voice output, and phone-to-computer Auto execution still need handset
acceptance. Refer to the established UI09/UI13 physical checklist. Simulation
reduces that work; it cannot establish those hardware results.
