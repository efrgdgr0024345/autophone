# UI13 phone-install test candidate

[Download CatAI-13.apk](CatAI-13.apk?raw=true) — AI planner, camera feedback, Auto Play,
OpenAI voice, Linux command library and Bluetooth keyboard/mouse.

[Download CatBT-13.apk](CatBT-13.apk?raw=true) — offline alternative. Both flavors share
an application ID; install one at a time. CatBT excludes AI/camera/microphone/voice/Auto.

These exact APK bytes are built from the source SHA in SOURCE.txt, signed with the
existing cloud test certificate, non-debuggable, versionCode 15 / 13.0-adaptive-auto.
They can update UI10/UI11/UI12.1 directly when those use the same cloud test signer.
Older releases and the protected physical Bluetooth baseline are unchanged.

## Use

1. Download the complete CatAI APK on the phone and open it. No ZIP is needed.
2. Open AI. **Auto Play** and **Voice** stay at the top, above scrolling task content.
   Auto is off/disabled until a plan exists. Voice toggles speech without opening Settings;
   enabling voice here selects OpenAI speech. Settings also offers an offline phone voice.
3. Give a goal. The plan lists exact commands; tap a command to open its explanation,
   expected result, risk and approval. Ordinary manual typing still excludes Enter.
4. Tap Auto Play. Review the goal/destination and session consent, start the live camera
   preview and keep the correct terminal focused and clearly framed. Full Auto may enter
   commands, add diagnostic steps and revise the remaining plan for the original goal.
5. During the run, the top **Auto ON** button immediately stops/closes the session. The
   adjacent **Voice ON/OFF** control immediately changes spoken explanations. Turning
   Auto off ends that session; there is no background continuation or automatic replay.
6. Auto captures a command's output once on Linux, then inserts exact page-read commands
   into the live plan. It photographs, collects and reconciles numbered pages. If a page
   does not fit, it can show a smaller slice. New diagnostics and direction changes appear
   automatically, without asking for ordinary scrolling/photos or approving each step.
7. Noninteractive sudo requires credentials already available on the computer. The app
   does not enter passwords. Unsupported commands, unusable camera views, lost connection,
   backgrounding or global limits stop further input. Changes are not retried automatically.

Photos and OpenAI speech use your API account and can incur charges. Upfront consent
shows the bounded session limits. Capture files are private temporary files on the Linux
target, capped at 1 MiB and deleted by a visible cleanup command after collection. Stopping
before cleanup can leave that private /tmp/bc-* folder on Linux. Stop does not send cleanup
keys into an interrupted/unknown terminal. Phone photos, keys, pages and audio are in memory.

[All rendered screens](../screens/README.md).
[Full scope, limits, automated checks and physical acceptance](../../UI13-ADAPTIVE-AUTO-TEST-PLAN.md).

Real-phone Bluetooth, keyboard layout/focus, camera framing, live OpenAI vision/speech and
Keystore need physical acceptance. Emulator fixtures use synthetic vision/planner/HID and
real emulated Camera2 JPEG capture; they do not establish real terminal recognition or
successful hardening. This is a test candidate, not a whole-system security guarantee.

## Verification of these bytes

- App source: `7db3aed5607687236fb49bf493daf38a38ce357e`. Later changes only adjusted emulator fixtures and delivery documents.
- 148 Android unit tests (121 AI + 27 offline), 83 policy/mutation checks, 40 independent planner-lab cases and the artwork check passed.
- Both APK builds, packaged identity/permissions/capabilities and signatures passed. Signer matches UI11/UI12.1.
- Lint: zero errors, 59 AI and 20 offline warnings.
- CodeQL scanned all 46 Kotlin files: no security-rule findings; 17 quality notes remain (15 deprecated calls, the inherited Diagnostics indentation note and one PRNG allocation note).
- Native checks: 16 core cases passed in the full run, then all four Auto cases passed in a focused rerun after extending the software emulator-only timeout. That verifies pinned controls, consent/preflight, two pages reaching the planner, a new diagnostic replacing the remaining plan, cleanup/completion, and immediate Stop. Original timeout and final passing logs are linked in the screen gallery.
- Shell integration tests verify paged output without rerunning the original command, final unterminated lines, escaped controls, real exit codes, smaller slices, exact-file cleanup, a 1 MiB output cap without SIGPIPE, and successful 3 MB command file writes.

These checks do not establish real phone Bluetooth, live OpenAI voice/vision, camera framing or successful Linux hardening.

Full direct APK URL:

https://raw.githubusercontent.com/efrgdgr0024345/autophone/dd5e43a7f77ca47e937cbb2dfda63913699edfd2/docs/ui13/install/CatAI-13.apk
