# UI13 adaptive Auto Play candidate

Rollback: e3fd22487442e555b8cd8a14d17eebcd917b2ac1 (UI12.1 documentation head).
Branch: feature/ui13-adaptive-auto. Android versionCode 15, versionName 13.0-adaptive-auto.

## Requested behavior

The AI screen pins Auto Play and Voice above the scrolling task views. Auto is visibly off
until a plan exists and a foreground session is started. The active Auto screen pins the
on/off and voice controls too. Turning Auto off closes/cancels the session immediately;
there is no background continuation or automatic resume. Voice can be switched between
OpenAI speech and text without visiting Settings. Narration explains purpose/results and
is awaited before replacing it with the next explanation. Existing Settings still offers
phone speech as well. No paid API tests are performed by fixtures.

The plan lists all exact commands; selecting one opens its explanation and metadata.
During Auto, the live list shows pending/current/observed operations, with expandable
explanations. Observed does not mean human-confirmed success. Every inserted capture,
page, smaller slice, cleanup or revised task command is published into the plan before
transmission. Manual editing remains available after stopping Auto.

## Output collection and automatic adaptation

Full Auto consent covers the original goal, destination, automatic Enter, new supported
diagnostics and remaining-plan changes, and automatic OpenAI photo uploads. The existing
camera/transport/key-vault/ordinary sender and four frozen HID files are unchanged.

A supported command is wrapped by app code, not model-authored scripts. A 96-bit random
private /tmp/bc-* directory on the Linux target holds stdout/stderr and the actual exit
code. umask 077 protects the directory/files. Execution is noninteractive (stdin closed,
sudo normalized to -n, pagers disabled), with a 1 MiB output-file limit. Linux /bin/sh,
standard coreutils/awk/sed and a US-compatible HID keyboard layout are required. The
working directory of the original command is preserved. An unavailable credential fails;
the app never types a password or assumes privilege.

A capture marker establishes completion. Output control bytes are escaped with cat -v,
folded at 64 columns and displayed eight lines per numbered slice. Unique markers include
the page and line range, plus the captured exit code and MORE/DONE/LIMIT. Vision must
recognize the marker and a fresh shell prompt; readable pages require both boundaries
and the complete body. If only the footer fits, the app inserts a smaller slice of the
same saved output (down to two lines), without rerunning the original command. Busy
commands receive more photos (at most 12 waits), unreadable views at most three retries.
Password prompts and inability to establish a safe terminal stop further input.

Each legible page is retained in phone memory and sent as untrusted evidence with the
next photo. The final page reconciles the entire collected result against the original
expected result. Then a visible exact-file cleanup command removes the three capture
files and their empty directory. A failure, incomplete result or changed direction
requests a full replacement remaining plan using the original goal, collected pages,
remaining commands and transmitted-command history. Safe diagnostics should resolve
ordinary missing system facts instead of asking the user to scroll or photograph.
Supported new commands may be adopted without another approval in the consented session.
Already-transmitted changes cannot be repeated, including quoted/sudo -n aliases.
A genuine human decision/credential or unsupported command still needs human handling.

Bounds: 16 pages per original command, 120 total photo checks including preflight,
12 model plan revisions and 240 visible session steps. Reaching the output-page limit
cleans up then requests focused diagnostics; global limits stop. The model wire remains
strict, maximum 40 commands per proposal. Collected page bodies must fit their displayed line range (at most 522 characters
for an eight-line page; wire ceiling 1024); revision evidence is bounded to 14,000 characters and the request to
32,768. No claim of whole-system security follows merely from reaching the end.

Stopping before cleanup can leave a private capture directory on the Linux target;
it is disclosed before consent. Stop never sends a cleanup command into an unknown or
interrupted terminal. Phone keys/photos/audio/page text remain session memory only;
no new phone permissions/storage, server, desktop companion or dependency is added.

## Validation

Required before publishing: all preservation/contract mutation checks, offline catalogue,
AI/offline unit suites, both Lint/build/APK inspection/signature checks, native app-window
render/camera smoke and CodeQL. Record exact source and tested artifact bytes separately.

New unit cases exercise real generated /bin/sh commands against synthetic Linux files:
multiple pages, a final line without newline, private permissions, no rerun after the
input file is removed, escaped terminal controls, nonzero exit codes, page limit,
exact-file cleanup and smaller slices without skipped lines. State tests cover new
commands, no replay of changes/aliases, visible insertions/revisions, stale captures,
bounded busy recapture and completion with no remaining commands.

The disposable Android emulator fixture uses actual Camera2 JPEG captures and PixelCopy
screenshots, with synthetic vision/planner/HID boundaries. It checks pinned Auto/Voice
while scrolling, voice toggles, explicit consent/preflight, two pages, their combined
evidence reaching a fake planner, automatic replacement with a previously unlisted
kernel diagnostic, cleanup, completion and immediate Stop during a countdown. This is
not live OCR, paid OpenAI speech or physical Bluetooth verification.

Physical acceptance: install over UI11/UI12.1; confirm top controls at small/large text,
phone camera pointed at a real terminal, actual output markers/pages/smaller-page recovery,
new diagnostics visible, real OpenAI interpretation/narration and voice-off behavior,
Stop during narration/typing/photo/planning, Bluetooth interruption/background handling,
noninteractive sudo failure, long-output narrowing, and temporary Linux cleanup. Verify
normal keyboard/mouse and ordinary type-only approval still work. No baseline promotion
or release merge is authorized by automated results alone.
