# Black Cat UI14.2 testing candidate

Live testing found two issues that simulations had not established: a comprehensive
plan could exceed the 60-second deadline, and a structured replan could include
commands outside Auto's supported grammar. UI14.2 extends the bounded planning
window and explicitly teaches those restrictions to the model. It also keeps Cancel
pinned above the bottom navigation while a request is running, including Results
with a collapsed goal. The existing command policy still rejects unsupported output.

## Install

- [CatAI-14.2.apk](install/CatAI-14.2.apk) — AI, Auto and offline command library.
- [CatBT-14.2.apk](install/CatBT-14.2.apk) — offline alternative.
- [Checksums](install/SHA256SUMS.txt) · [Verification](VERIFICATION.json)
- [Live API evidence and methodology](live-openai/README.md)
- [New candidate workflow screenshots](screens/README.md)
- [Usability review](../ui14.1/UX-REVIEW.md) · [Earlier width/font gallery](../ui14.1/layout-matrix/README.md)

Version code **18**, name **14.2-live-api-validation**, package `com.blackcat.remote`.
The original signing certificate is retained for an ordinary update install.
Install one variant at a time. This is a physical-test candidate; no baseline was
promoted and no old APK was overwritten.

Build source: `28fec6a187cca84f92fa14821167d660c5436eed`.

## Live results

All six revised paid checks passed: an 18-command/nine-phase security plan, idle
terminal preflight, clipped-page detection, readable page transcription, an
evidence-driven five-command remaining plan, and non-silent OpenAI PCM speech.
The production codecs created and parsed the wire data; all proposed commands
passed Full Auto's unchanged grammar. The detailed plan took 65 seconds, which
would exceed the previous deadline. Planning now allows up to three minutes for
reading, with an outer deadline covering connection time. Cancel/Stop remain available.

The full initial failures and the separately measured 90-second plan are retained.
These checks use verified host HTTPS and synthetic images. They establish live
API access and production wire/parser compatibility, not Android-network or
physical phone/Bluetooth execution. See the live report for exact boundaries.

## Retained UI and Auto behavior

Exact commands appear before optional details; tapping opens explanations. Auto
Play and Voice remain at the top. Goal editing, the offline Linux tree, one ordinary
TYPE ONLY approval, camera feedback, canonical remaining-task handoff and the black
cat on white icon remain in place. Full Auto alone has the explicitly approved
foreground Enter/automatic collection behavior. It stops on interruption, uncertain
feedback, password prompts or unsupported commands; no background continuation or
replay was added.

## Physical checks still needed

Actual phone/computer pairing and reconnection, keyboard layout, mouse behavior,
camera focus/glare, phone playback/dictation/IME and Keystore behavior need handset
acceptance. API31 ATD emulator results do not establish those. Earlier API35 images
had framework failures and are not counted as passed coverage. The app remains
portrait locked. The original [UI14.1 verification](../ui14.1/README.md) retains the
larger Linux/OCR-loop evidence and its documented injection limitations.

## Final regression evidence

- 161 unit tests, 83 policy/mutation checks, 40 planner contracts, artwork decoding
  and the 212-command offline catalogue passed.
- Both builds and packaged permissions/identity/signatures passed. Shipping DEX
  contains no test image bridge, synthetic key or cloud-secret binding name.
- Lint: zero errors, 67 AI / 21 offline warnings. CodeQL scanned all 48 Java/Kotlin
  files: zero security findings, 22 quality notes.
- The exact APK passed 22 native workflow checks, one real timeout-cancellation
  regression and 26 layout/navigation/icon checks: **49 checks** in total. Widths
  were 320/360/390/412 dp at 100/130/200% font sizes.
- The galleries contain 39 workflow/timeout captures and 37 matrix/icon captures.
  The app's actual installed APK hash was checked against the candidate before each run.

The native Auto fixture completed two-page collection, reconciliation, automatic
replacement of the remaining task, seven simulated HID Enter operations and cleanup.
It uses real Camera2 JPEG capture with synthetic observation/planning/HID. The larger
13-operation Linux/OCR loop belongs to UI14.1 and was not rerun for this candidate;
its unchanged collection path is covered by the current native fixture. These
results and the six separate live API cases must not be combined into a claim of
one continuous live OpenAI camera-to-computer run.
