# CatAI-19 — evidence-driven diagnostics and natural explanations

[Download CatAI-19.apk](install/CatAI-19.apk) · [Listen to the OpenAI voice sample](live-openai/narration-sample.wav)

This is a physical-test candidate, version code 22 (`19.0-evidence-auto`), with the
original application identity and signer. It can update CatAI-17. Saved encrypted
phone keys are reused; temporary-key controls disappear from Auto when a key is saved.
Configuration changes use manual review in this diagnostic milestone. Auto runs only
supported diagnostic argument contracts and may add diagnostic steps for the same goal.

## What changed

- Natural spoken purpose and findings, without reading commands or narrating every
  page/cleanup helper. Voice failure/limits leave text visible and show Voice TEXT.
- Positive diagnostic argument grammars; familiar tool names and model risk labels
  cannot authorize firewall, package, permission or other configuration changes.
- Capture overflow is explicit incomplete evidence. Output is collected once,
  paged/resized automatically, reconciled, and used to revise remaining tasks.
- Exact marker prefixes and full printed footers are normalized only when their
  separate code/state fields agree. Nonces are never repaired or guessed.
- Stable task identities retain observed findings; attempted, uncertain, replaced
  and evidence-checked tasks remain distinct. Internal operations are expandable.
- Actual Auto phase, pause state and session limits remain visible. Stop does not
  undo commands already running on Linux. Foreground consent never resumes automatically.

## First phone test

1. Install CatAI-19 over the existing app. Use Settings for the target and API key;
   select **Remember encrypted key on this phone** once to reuse it across plans.
2. Pair normally from the computer. Verify manual typing and punctuation with a
   compatible US keyboard layout. Use a Linux POSIX/Bash terminal for output wrappers.
3. If camera access is not yet granted, open Photo Feedback once to grant it.
   Ask for three read-only health checks: OS identification, disk space and failed
   services. Review the goal, exact commands and destination before starting Auto.
4. Keep the terminal focused and camera framed clearly without secrets. Auto and
   Voice stay at the top; Stop prevents further input and clears session resources.

## Verification and limits

- 213 Android unit tests; 105 Python policy/catalogue/artwork/harness checks;
  40 planner contracts. Frozen Bluetooth/manual/transport/camera/vault checks passed.
- Both variants built and inspected, zero Lint errors, original signer and exact
  permissions. No test key/bridge markers in production DEX.
- CodeQL covered all 61 Kotlin files: zero security-rule findings and
  30 quality notes. See [triage](checks/codeql-triage.json).
- Full 88-check UI baseline passed before the final observation-decoder-only fix.
  All production UI source is unchanged from that baseline. Final APK: 28
  affected Auto/key/stop/timeout native checks, plus the independent Linux image loop.
  Baseline captures carry their own source/hash; they are explicitly not final-APK captures.
- Actual Linux output, independent OCR, clipped-page resizing and automatic plan
  revision passed through the final app. Capture and cleanup replies deliberately
  use full printed footers. The fixture can reread nonce cells from the same image with a hexadecimal alphabet,
  without substituting expected values. HID and planning are test providers; terminal frames
  are injected at the observer boundary while real Camera2 capture is exercised.
- Five paid synthetic host API calls covered planning, vision, truncation and speech.
  Real responses replay through the final production parser. Request source was the
  earlier candidate; final schema is unchanged and marker-prefix instructions clarified.

There is no continuous physical phone-camera/OpenAI/Bluetooth proof. Handset pairing,
reconnection, keyboard layout/focus, optics, voice playback/dictation and hardware
Keystore acceptance remain required. Photos are advisory, not proof that a system
is secure. No merge, physical-baseline promotion or overwrite of earlier APKs.

## Screens and evidence

[Final Auto/key/evidence screens](evidence/README.md) · [Final main/Auto workflow](native/README.md) ·
[Final timeout](timeout/README.md) · [Unchanged-UI streaming baseline](search-stream/README.md) ·
[Command workflow baseline](workflows/README.md) · [Lesson baseline](learning/README.md) ·
[Width/font baseline](layout/README.md) · [Linux image demo](linux-image-demo/README.md) ·
[Machine-readable verification](VERIFICATION.json) · [Scope and test plan](../UI19-EVIDENCE-AUTO-TEST-PLAN.md)

## Downloads and provenance

CatAI: install/CatAI-19.apk

SHA-256: `3e42faac46d7a658c94e72553bce1564efebc699253639bf553f6bdacf7c357f` (6,522,185 bytes).

Offline alternative: install/CatBT-19.apk

SHA-256: `d555e83b83faac606185c3024786e437dde47f33f28d7cfef41b2aa7a25343ed` (6,097,885 bytes).

Install one variant at a time; they share `com.blackcat.remote`.
Source: `173946bb1172cea930dfd78ed032967b022d8fcf`. Signer SHA-256: `fc5c0b42b9da40c2507d8f2e77ed92db9e023740aa1a44d1a3b2c14a6e9f4764`.
The exact tested bytes were copied to these names without rebuilding.
