# Black Cat UI14 testing candidate

**Superseded:** the extended Linux/image test subsequently found that a timed-out
plan request could leave Auto showing ON after its coroutine ended. Use UI14.1
when available. The UI14 APK bytes are retained as historical evidence, not an
accepted Auto candidate. See [UI14.1](../ui14.1/README.md).

UI14 simplifies command review, keeps Auto/Voice visible, preserves remaining tasks
when Auto stops, explains API failures in the session, and uses a black cat on white
for the launcher. The Bluetooth core, manual typing/mouse path, encrypted key vault,
Camera2 controller, model pins and permission sets retain their reviewed boundaries.

## Install

Use **CatAI-14.apk** for the Linux assistant and Auto. CatBT-14.apk is the offline
alternative. Both share `com.blackcat.remote`; install one at a time. These are
non-debuggable, signed physical-test candidates, not a new protected baseline.
The signing certificate matches UI13, so an ordinary same-signer upgrade is possible.

- [AI APK](install/CatAI-14.apk)
- [Offline APK](install/CatBT-14.apk)
- [SHA-256 checksums](install/SHA256SUMS.txt)
- [Machine-readable verification](VERIFICATION.json)

## What changed

- Ready plans show exact commands before optional background information. The goal
  form collapses, and the offline Linux tree remains available below the plan.
- A selected command opens its explanation. Step/Preview use one approval button
  fixed above navigation. Ordinary approval types once without Enter.
- Auto and Voice stay at the top. Auto setup retains explicit session consent;
  advanced modes and limits expand when needed. The exact operations remain in
  the live plan while Auto collects evidence and changes its remaining tasks.
- Stopping returns canonical remaining tasks. A possibly transmitted task and its
  generated collection helpers cannot silently become a fresh queued command.
- A temporary Auto key can be entered explicitly for that session. It is cleared
  from the field on start and cleared from session state on stop. Authentication,
  billing and other provider failures remain visible instead of a generic failure.
- Buttons meet the 48 dp minimum-height criterion. Long commands wrap without
  discretionary hyphens or ellipsis. The launcher artwork has a white background
  and proportional adaptive-icon insets.

## Review the screens

These are native Android window captures containing synthetic test data. They are
not design mock-ups or evidence of physical Bluetooth/OpenAI success.

[Complete screen gallery](screens/README.md) · [Phone width/text-size matrix](layout-matrix/README.md)

## Evidence and limits

The exact candidate built from `37897cb894d3b5a22f55afb076d7655ce2d27948` passed
161 unit tests, 83 policy-mutation tests, 40 planner-contract tests, artwork decode,
the 212-command catalogue, both flavors' Lint/build, APK permission/identity/signing
inspection, and CodeQL. CodeQL examined 48 Kotlin/Java source files: zero security
findings and 22 quality notes (19 deprecated calls and three style/usage notes).
Lint has zero errors, with 67 AI and 21 offline warnings retained in the report.

The native Android 12/API31 ATD full smoke passed 21 checks, including actual
Camera2 JPEG capture and a complete Auto pagination/revision cycle using synthetic
vision/planner/HID responses. The layout matrix separately checks 12 combinations
of phone width/text size plus navigation and packaged-icon checks. See
[test plan](../UI14-COMMAND-FLOW-TEST-PLAN.md) and the verification JSON for recorded
outcomes, the independent Linux/image loop, and limits.

OpenAI connectivity was reachable, but `BLACKCAT_TEST_API_KEY` was absent from the
running cloud process and the configuration reported no saved binding. Therefore
paid live planning, OpenAI vision and spoken audio were not run. Never treat the
simulated HTTP/provider checks as live model or account validation.

The API35 images attempted on this cloud host had framework/SystemUI failures;
they are not counted as passed device coverage. ATD has no normal system IME or
launcher, so native icon rendering and editable input do not establish a real
launcher-grid or soft-keyboard interaction result. The app remains portrait-locked.
Physical pairing/reconnect, keyboard layout, mouse movement, handset camera
focus/glare, dictation, spoken audio and actual phone-to-computer Auto remain to be
checked using the established physical test plan. No merge or baseline promotion
was performed.
