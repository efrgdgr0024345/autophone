# Black Cat UI14.1 testing candidate

UI14.1 fixes a timeout discovered by the extended Linux/image test. A plan or photo
request could time out and leave Auto visibly ON after its coroutine had ended.
Auto now stops, shows the timeout, clears the camera/key, and returns only canonical
unattempted tasks. A native regression test raises a real
`TimeoutCancellationException` after the first operation and checks that behavior.
UI14 is superseded; its old APK bytes remain immutable evidence.

## Install and review

- [CatAI-14.1.apk](install/CatAI-14.1.apk) — AI, Auto and offline command library.
- [CatBT-14.1.apk](install/CatBT-14.1.apk) — offline alternative.
- [Checksums](install/SHA256SUMS.txt) · [Verification](VERIFICATION.json)
- [All workflow screens](screens/README.md) · [Width/font matrix](layout-matrix/README.md)
- [Usability review and references](UX-REVIEW.md)

Both variants share `com.blackcat.remote`, version code **17**, version name
`14.1-timeout-recovery`. Install one variant at a time. The signing certificate
matches the previous candidate; an ordinary same-signer upgrade preserves app data.
These remain physical-test candidates, not a promoted Bluetooth baseline.

## Command-flow improvements

The ready plan shows exact commands before optional detail; the goal form collapses
and remains editable. The offline Linux tree stays accessible below the plan.
Selecting a command opens its explanation. Step/Preview use one approval fixed
above Home / Keyboard / AI / Settings. Ordinary approval remains TYPE ONLY.

Auto/Voice stay at the top while task content scrolls. Setup retains foreground
session consent, with advanced modes and limits expandable. Stopping returns
canonical remaining tasks, excludes a possibly transmitted task, and removes
session-only capture/page/cleanup commands from the runnable plan. An explicit
temporary Auto key clears on start/stop. Provider failures remain visible.

Controls use at least 48 dp height; command text wraps without discretionary
hyphens or ellipsis. The launcher is a black cat on white, with proportional
adaptive-icon insets. The usability review applied Android accessibility and
navigation guidance, visibility of state, error recovery, user control and
progressive disclosure. It is not a user study or accessibility certification.

## Verified candidate

Build source: `d16c23efb33ffd63a8f920b2de6242a5e178be0e`.

| Check | Result |
| --- | --- |
| Unit tests | 134 AI + 27 offline; no failures/errors/skips |
| Policy mutation tests | 83 passed |
| Planner contract / artwork decode | 40 / 1 passed |
| Offline catalogue | 212 commands, 13 categories, 63 topics |
| Lint | Zero errors; 67 AI and 21 offline warnings |
| APKs | Exact permissions, original identity, non-debuggable, same signer; test bridge absent |
| CodeQL | 48 Kotlin/Java files; zero security findings, 22 quality notes |
| Native workflow | 21 checks passed, including real Camera2 JPEG capture and Auto adaptation with synthetic providers/HID |
| Timeout regression | 1 native check passed: Auto OFF, useful error, key/camera cleared, no replay |
| Native layout | 26 checks passed across 320/360/390/412 dp and 100/130/200% text |

The exact APK bytes were installed and checksum-verified before native runs, then
copied for delivery without rebuilding to rename. The galleries contain 38 workflow
frames, 36 matrix frames and Android's packaged-icon render. CodeQL's quality notes
are 19 deprecated calls and three style/usage notes; [SARIF](verification/codeql.sarif)
and test logs are retained.

The fresh independent [Linux/image loop](image-loop/README.md) passed 13 operations,
14 image observations and one automatic plan revision, without a driver restart,
manual provider response or command replay. It resized clipped pages, reconciled
all evidence, sent the new diagnostic and removed both temporary capture directories.
It decodes the production HID report stream,
executes the allowlisted disposable Linux commands, renders their actual output,
and uses independent OCR on those JPEGs. The phone exercises Camera2, then the
test substitutes terminal images at the observer boundary. This is distinct from
a working emulated video-camera stream or a live OpenAI vision request.

## Limits

`BLACKCAT_TEST_API_KEY` was absent from the running environment during validation,
and configuration metadata reported no saved binding. The OpenAI endpoint was
reachable, but authenticated planning, live vision and paid spoken audio were not
run. Simulated HTTP/provider tests do not establish current account/model access
or billing. Keys must be supplied securely through the environment/phone settings,
never through chat or the repository.

API35 image attempts had framework/SystemUI failures and are not counted as
successful coverage. API31 ATD lacks a normal system IME and launcher. Soft-keyboard
display, dictation, launcher-grid appearance, spoken audio, handset focus/glare,
physical Bluetooth pairing/reconnect, keyboard layout, mouse behavior and actual
phone-to-computer Auto still require physical acceptance. The app remains portrait
locked. No merge or baseline promotion was performed.

See the [test plan](../UI14-COMMAND-FLOW-TEST-PLAN.md) for commands and methodology.
