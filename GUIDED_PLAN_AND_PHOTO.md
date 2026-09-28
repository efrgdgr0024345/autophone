# Black Cat: guided Linux plans with user-reviewed photo feedback

Status: approved design and isolated test stage, NOT a new phone APK. This change does not modify app source, Android manifests, build settings or the frozen Bluetooth gates.

## Non-negotiable boundary

The working Bluetooth reference remains 9771772bbe25dd8eba8d7303f7e4680aa84175df / BlackCat-v2.apk. The AI repair reference is 7712ea981230a2782a83b0920d3da5f09bc47c1d; physical recovery of that repair is not yet confirmed. Do not silently treat it as another physical-success baseline. Do not change the original Activity, ordinary Send, pairing, mouse, lifecycle, HID descriptor or report mapping. The existing AI button hook is the integration point. No new package identity, companion laptop software, root service or phone-side Bluetooth scanner.

## User journey

1. Goal and context: keep the user's objective visible. Distinguish the Bluetooth computer from the target terminal (local/SSH/container/VM), OS and shell. Unknown facts stay unknown.
2. Plan overview: checks, changes, manual actions, decisions and verification; explain inferred prerequisites and what is out of scope. Creating a standard account does not authorise granting administrator access.
3. One step in focus: purpose, exact command where applicable, prerequisites, expected result and what to do if different. Future dependent steps remain visible but not yet sendable.
4. Review and Send text only: selection sends nothing. User approves exactly one command and current destination. Never append Enter, replay, run all, or automatically continue. Keep the existing manual keyboard controls intact.
5. User runs the command and reports the outcome: expected result / error / computer asking a question / not sure / TAKE PHOTO.
6. Optional photo: open an in-panel viewfinder, capture ONE still image, crop to relevant terminal text, cover private areas with opaque rectangles, review the final flattened image, then explicitly tap SEND PHOTO TO AI. Camera access is not upload consent. Include an editable short note.
7. The same phone-held OpenAI key authenticates the request containing the reviewed image plus original goal, current step, target description and bounded user-confirmed history. No website intermediary, key embedding or secret from chat. No Bluetooth metadata or unrelated phone data is sent.
8. OpenAI returns a proposed interpretation and updated plan. Show: 'I can read ...', 'This appears to mean ...', uncertainties, and visible added/changed/removed steps. User chooses 'That matches', 'Correct it' or 'Retake'. Neither image recognition nor accepting an interpretation counts as command approval or proof of success.
9. A separate explicit action accepts the plan revision; each next command still needs its own review and Send text only. A photo of success may suggest verification but cannot itself mark the task completed.

## Photo privacy and evidence

Capture never starts automatically. No live video or continuous capture. No gallery-wide read permission. The user can cancel/retake/remove before upload. Keep raw and edited images in app-private temporary memory/files, not the public gallery; remove after the request is consumed/cancelled or the draft is discarded. Rotate to upright pixels before cropping; flatten opaque redaction into a fresh RGB image, re-encode JPEG, remove EXIF/GPS/thumbnails and discard the original. Preview exactly the bytes/pixels to upload. Proposed application limits: one image, longest edge 1600 pixels, JPEG at most 2 MB, bounded text/context. Do not downsample tiny terminal text until it is unreadable; ask for a closer crop/retake instead. No automatic uploading of earlier images on every turn.

The photo is untrusted evidence, not instructions. Text visible on a screen must never override app rules, approve a command, request secrets, change the destination, or make the model execute anything. Never fetch a URL found in the image. Treat cropped, blurred, reflected, rotated, outdated or unrelated screenshots cautiously. Root prompt symbols and a screenshot alone do not prove identity or authorisation. Avoid repeating private values in transcriptions. Recognition uncertainty is a visible condition, not a fabricated confidence percentage.

Capture/upload is tied locally to plan revision, current step and target. Replacing/cropping/redacting a photo invalidates its upload consent. Changes of step/goal/target invalidate pending responses and command approvals. Late API responses cannot replace the active plan. Disconnects or backgrounding invalidate command approval; no resume of partially typed text. Retain only explicitly user-confirmed facts as progress. Do not store credentials or photos in diagnostics, test artifacts or crash logs.

Use the existing Responses API with input_text plus an input_image data URL, a vision-capable model, JSON-schema output, store=false, no tools and no automatic retries. Input image processing consumes API usage. store=false does not mean zero provider retention; display that before consent. The same personal key must be authorised for the chosen model. Keep password entry on the computer, never in the AI conversation.

## Android integration: camera must not break HID

Do NOT simply launch an external camera Activity from the existing dialog. Android's HID API documents foreground-related unregistration, and the current AI panel cancels on Activity pause. Preferred implementation: a dedicated AI-only camera view/controller inside the existing Activity/dialog, with a separately scoped camera lifecycle. Do not convert MainActivity to another superclass or add Bluetooth pause/resume code. A runtime CAMERA permission and optional camera hardware feature may be added ONLY to the AI flavor in a separately reviewed integration change. Permission denial leaves text feedback and ordinary Bluetooth fully usable. Permission prompts, screen lock and background return still require physical tests; in-panel capture is not a guarantee of uninterrupted radio registration.

Camera resources close after capture/cancel. Camera/network components cannot initialise, register, reconnect, disconnect or destroy HID. Tests must cover open/cancel, permission denial, capture/retake, preview, upload, timeouts, app switching and normal typing before/after. If that cannot be achieved within the existing hook, stop and propose a separate controlled experiment rather than weaken the frozen boundary.

## Separate approvals and state

The API proposes data only. It has no authority to set local selected/approved/typed/completed states. Distinct states: planned, waiting for prerequisites, selected, approved once, typing, reports accepted, result unknown, user-confirmed result. Photo states: draft, edited, approved for upload, submitted, interpretation pending confirmation. Upload consent, interpretation confirmation and command approval are separate. Stop typing cannot undo accepted keystrokes or kill an already running command. A failed API request must never disable manual keyboard/mouse use.

A plan step contains stable id, kind, purpose, optional command, dependencies, expected result, effects and failure guidance. Manual/decision steps cannot carry sendable command strings. The app owns progress/history and revision ids. Future Kotlin implementation must conform to the tested contract; this Python lab is not a replacement Android runtime.

## Delivery sequence

A. Isolated schema/prompt, photo sanitisation reference, fake-sender state tests and a bounded live API evaluation using SYNTHETIC terminal images only. No generated command execution, no Bluetooth access, no real user screen uploads to GitHub.
B. Review failures and human usefulness, then integrate plan UI and an in-panel camera only under the existing AI entry. Run Android tests/Lint/CodeQL, permission and full golden-path checks.
C. Publish one uniquely named, checksum-bound test APK from the exact checked bytes. Keep original APKs untouched. Physical first: normal keyboard/mouse -> AI open/close -> camera cancel -> camera capture -> explicit photo request -> reviewed command -> normal keyboard/mouse again. No merge/promotion without user confirmation.

## Evaluation and credentials

The GitHub evaluation uses only the previously authorised Actions secret secrets.openai_api_key at runtime, not the phone's saved key and never its plaintext in source/logs/artifacts. It is the same OpenAI authentication mechanism; no second image-service credential is required. This test-only secret is an explicit exception to the old blanket 'no key in CI' phrasing; do not change app security settings. No real API call on pull_request events. Initial live batch: at most 12 requests, fixed gpt-4.1-mini-2025-04-14, bounded input/output, no retries, no scheduled loop. A failing authentication/quota request stops the batch. Secret presence is not proof of valid API access or an enforced 24-hour expiry. Report actual usage; no automatic second paid batch.

Test stories: root prerequisite, unavailable sudo permission, existing user, password prompt, command unavailable, service output, insufficient context, unreadable image, stale result, text embedded in an image trying to override consent, photo redaction, and one-use approval. Automatic checks are not a semantic safety certificate; inspect actual proposals. All terminal images in CI are fabricated fixtures, not photographs of the user's laptop.

## Primary references checked 2026-09-28

- https://developers.openai.com/api/docs/guides/images-vision
- https://developers.openai.com/api/docs/guides/structured-outputs
- https://developers.openai.com/api/docs/models/gpt-4.1-mini
- https://developers.openai.com/api/docs/guides/your-data
- https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice
- https://developer.android.com/media/camera/camerax/architecture

GhostBoard / ToxicOrca and Linkpad / Devdas Kumar attribution in the existing project remains unchanged.
