# Local OCR observation proposal — not implemented

Decision for review: a bundled Latin ML Kit observer is worth a bounded prototype
after approval. It may reduce repeated OpenAI vision calls and show fresh local
terminal text sooner. It is not yet demonstrated on this phone, not a replacement
for command-completion checks, and must not decide what to type or press Enter.

This task adds **no ML Kit dependency, camera changes, OCR worker or timing change**.
`PhotoCaptureController`, Auto's fixed five-second wait and all existing nonce/page/
completeness checks stay byte-for-byte unchanged from the approved CatAI-19 source.

## Evidence and dependency choice

The official Google sample pins bundled Latin recognition
`com.google.mlkit:text-recognition:16.0.1`, and separately documents the Play Services
alternative `com.google.android.gms:play-services-mlkit-text-recognition:19.0.1`.
Use the bundled Latin option for a proposed prototype: recognition must work after
install without downloading a model or needing Google Play Services. Do not add
Chinese/Japanese/Korean/Devanagari models without a concrete need.

Verified official sample source, commit
`4a015dba205bf1664da7a241d8b4754c1e1a579c`:

- [Bundled and Play Services dependencies](https://github.com/googlesamples/mlkit/blob/4a015dba205bf1664da7a241d8b4754c1e1a579c/android/vision-quickstart/app/build.gradle#L75)
- [Text recognizer process/close lifecycle](https://github.com/googlesamples/mlkit/blob/4a015dba205bf1664da7a241d8b4754c1e1a579c/android/vision-quickstart/app/src/main/java/com/google/mlkit/vision/demo/java/textdetector/TextRecognitionProcessor.java)
- [InputImage bitmap/media image handling and asynchronous frame closure](https://github.com/googlesamples/mlkit/blob/4a015dba205bf1664da7a241d8b4754c1e1a579c/android/vision-quickstart/app/src/main/java/com/google/mlkit/vision/demo/java/VisionProcessorBase.java)

The [ML Kit Android text-recognition guide](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
was blocked by the environment's outbound proxy (403) during this evaluation. No
exact APK-size increase, latency, confidence availability, telemetry behavior or
future compatibility is claimed from that unread page. Before integration, inspect
the exact AAR/transitive manifest/native libraries/licenses and current SDK data-
disclosure requirements; validate offline operation with captured network traffic.
Bundled inference does not by itself prove that the SDK makes zero other network
requests. Keep the dependency AI-only; CatBT boundaries remain unchanged.

## Fit with the existing Camera2 controller

Current source selects a back camera; uses a TextureView repeating preview and a
JPEG ImageReader (`maxImages=2`); acquires the latest still, copies bytes, closes
the Android Image immediately, and dispatches only the ByteArray to the UI. It
applies JPEG orientation. It does **not** expose sensor timestamps, request tags,
CaptureResult or a frame-generation identifier to the observer. Merely observing
TextureView updates is insufficient to prove a JPEG was captured after Enter.

For the first prototype, keep the same Camera2 session and still-JPEG path. Decode
a bounded bitmap off the UI thread, apply the actual JPEG/EXIF orientation once,
and feed `InputImage.fromBitmap`; own that bitmap until asynchronous OCR finishes.
Do not retain a closed Image or hand closed mediaImage to ML Kit. One inference
in flight, bounded image/ROI size, drop superseded work, and no retained bitmap
queue. Add request-generation and capture-time evidence through a separately
reviewed, minimal controller seam before using OCR as feedback. Its present byte-
only callback cannot supply the required freshness guarantee by itself.

A YUV_420_888 ImageReader side output could avoid JPEG/decode cost later, but changes
Camera2 stream combinations, backpressure and frame ownership. It risks vendor
compatibility and frozen Camera2 behavior. Defer it until measured still-JPEG
results justify the separate camera experiment; no CameraX migration is needed.

## Local freshness record

Foreground session memory only: session ID, host epoch, plan revision, command/
collection attempt, requested capture generation, monotonic request time, actual
capture timestamp with documented clock mapping, received timestamp, orientation,
frame/image digest, text blocks with geometry, raw text and parsed marker fields.
Invalidate on command send, new expected page, revision, stop, backgrounding,
disconnect, camera restart, orientation/session recreation or timeout. Late OCR
callbacks must match the complete active generation tuple before display/use.

Never write frames, terminal text, goals, commands or recognition results to logs,
files, preferences, analytics or crash reports. Tests may save explicitly synthetic
fixtures. Keep a short bounded in-memory evidence window and clear it on stop.
Exact timestamps and image digests alone do not prove the terminal display is live;
the current operation's unpredictable nonce and expected markers remain necessary.
Do not assume a Camera2 timestamp shares SystemClock's timebase when the camera's
timestamp-source characteristics do not guarantee it. Even complete, stable text
cannot establish keyboard focus or authorize another command.

## Exact marker and completeness boundary

Use the existing session/collection validators, not a second relaxed OCR grammar.
Preserve the full 24-hex nonce, operation kind, expected page/range, begin/end,
exit-code agreement, MORE/DONE/LIMIT and CAP DONE/TRUNCATED semantics. CatAI-19's
documented full-footer canonicalization is permitted only when actual suffix,
state and exit code agree. The final comparison to the active operation's marker
stays exact. No O/0, l/1, case, spacing, nonce repair or context-derived completion.

Track each page independently and reconcile only the expected ordered collection.
Missing footer, clipped range, mismatched exit code, duplicate/stale page, wrong
nonce, TRUNCATED or LIMIT remains incomplete. Readability or stable OCR cannot
upgrade it to success. Preserve bounded slicing/replanning behavior; never rerun
a possibly transmitted original task to recover missing OCR evidence. Terminal
text remains untrusted data, including text that looks like instructions.

The existing host QA Tesseract harness improved some **synthetic injected** nonce
reads with independent pixel-region recognition. That is not a phone OCR result;
its fixed-grid crops are unsuitable as a general real-camera solution. Its earlier
0/O and 1/l failure correctly stopped Auto. Preserve that failure expectation in
the ML Kit corpus rather than substituting the expected nonce.

## Replacing the five-second delay — assessment only

The current `scheduleCapture` waits five seconds, captures a still, then asks the
existing observer. A fixed wait can be too slow for short tasks and too short for
long tasks. A reviewed experiment could instead gather bounded fresh samples and
observe stability, without changing the sender or introducing new execution authority:

1. Reset the local evidence window after a confirmed send/page operation. Require
   post-operation capture generation and timestamps, excluding buffered old frames.
2. Proposed starting parameters for measurement: one still every 500–750ms, one OCR
   task at a time, ≥3 fresh observations agreeing on required markers and terminal
   text over ≥1.5s, with unchanged ROI geometry and adequate readability. These
   are hypotheses, not calibrated production constants.
3. A moving cursor/clock should not cause infinite waiting: exclude only explicitly
   identified irrelevant geometry for **stability scoring**. Do not normalize marker
   characters, strip body lines or discard potential errors/password prompts.
4. Stable text schedules/qualifies an observation; only the unchanged exact marker,
   exit and completeness checks can establish a captured result. “No change” never
   means “finished”: a paused command, pager or password prompt can be perfectly stable.
5. Timeout, disagreement, empty/unreadable text or stale evidence yields uncertain/
   incomplete observation. Retain the existing bounded retries/stop rules. No auto
   command generation, Enter, cleanup, new paging or replanning authority from OCR.

Do **not** remove the five-second wait in the observation-only prototype. Record
when the proposed stable detector would have fired alongside current behavior,
using synthetic/consented local test data. Compare first-ready latency, false-ready
rate, missed markers, page completeness, power and memory before reviewing a timing
change. Do not send extra frames to OpenAI as a side effect of local sampling.

## Review gate and acceptance experiment

After separate approval: isolated AI-only observer branch; no automatic actions.
Use Android12 and a current supported Android emulator, then real phones. Fixtures
must include scrolling/delayed output, same page repeated, wrong nonce, stale frame,
truncated long output, low light/glare/blur/skew, colored ANSI terminal output,
small fonts, split/invisible markers, password prompts and confident 0/O/1/l errors.

Measure exact-marker precision/recall, false completeness (target **zero** in the
adversarial set), page ordering, no stale callbacks after lifecycle changes,
median/p95 OCR latency, proposed time saved over five seconds, allocations/peak
memory/thermal behavior, and network capture in airplane mode and online. Verify
exact app permission and offline APK differences. No accuracy or delay-removal
decision can be made from this design note alone.
