# Next task — approved Android AI-panel UI integration

## The user-approved direction

Black Cat is an Android phone that appears to a computer as a normal Bluetooth HID keyboard/mouse. The computer discovers and pairs with the phone. No companion computer software, phone-side scan-first architecture, root service, or unrelated phone access. Preserve this method before any UI enhancement.

The user approved the compact 03.0-cat-mobile PHP demonstration and its visual design. Transfer that experience to the existing Android AI panel; do not run PHP inside Android and do not substitute a remote website for the phone-held API key. The web prototype makes real OpenAI requests but simulates keyboard delivery. The Android app must continue using its existing approved Bluetooth sending path.

## Immediate deliverable: one bounded UI change

- Keep the original MainActivity (including exactly its current three-line lazy AI entry), HidManager, HidDescriptor, HidReports and Diagnostics unchanged. Keep app identity, normal manual controls, permissions and Bluetooth lifecycle unchanged.
- Keep OpenAiPlanner, ApiKeyVault, AiTransportScope and CommandSafety behaviour unchanged for this UI-only milestone. The planner-contract/photo integration is the next separately tested milestone; do not silently bundle it into styling.
- Reproduce the approved light/off-white, dark-green and black visual language. Use the exact approved cat/emblem imagery. The face/paws may overlap card decoration but never hide text, hit targets or input. No giant hero, slogans or redundant reassurance panels. Dense, readable mobile layout with useful screen space and thumb-accessible actions.
- Settings are separate from the task. Keep the goal visible and the current step easy to inspect. Keep Plan, Step, Feedback and Preview/navigation behaviour consistent with the approved prototype where implemented, but never fake unsupported functionality.
- The existing Android schema supports explanations and individual commands; do not invent successful prerequisite/checkpoint state from that old schema. A not-yet-supported guided/photo action must be explicitly unavailable, not a pretend live feature.
- Remove redundant approval checkboxes in the AI UI. The labelled action button is the user's approval for that one reviewed request or command. Retain the review screen, exact command, actual destination, one-use approval and stale-session checks. Selecting a command alone sends nothing. Never append Enter, run all, retry or replay automatically.
- Show actual API and connection errors. Ordinary keyboard/mouse must remain usable when AI fails. Do not persist goals, commands or photographs in diagnostics.
- Include a regression test for opening/closing the panel without acquiring/releasing HID, issuing an API request or sending keyboard reports. Exercise repeated taps, cancel, stale selections and application-background transitions without altering the original Activity lifecycle.
- Check 320/360/390/412 CSS-equivalent phone widths or corresponding native layouts, large text, landscape, long commands and soft-keyboard occlusion. Record what was actually tested.

## Next milestone, already approved as a goal but not part of a styling-only change

Integrate the guided-plan contract and screen-photo feedback, based on the isolated lab and improvements in web/lab.php:
Goal/context -> plan -> one step -> explicitly approved type-only command -> user-observed result -> optional reviewed screen photo -> proposed interpretation -> user confirmation -> visible plan revision.

Use check/change/manual/decision/verify steps, dependencies, blocking questions and separately recorded human results. Manual/decision steps carry no sendable command. Do not assume a logged-in username is a requested new account. Do not turn standard-user creation into administrator creation. Distinguish a password prompt from a shell prompt; the user must finish the entire interactive operation before later commands are available.

Photo work remains inside the existing AI panel and uses the same user-held key. User captures one still, optionally crops/covers private content, reviews exactly the flattened image sent, and explicitly sends it with bounded goal/step/history context. OpenAI interprets the image; it is NOT an image-edit request. No continuous camera upload or gallery-wide reads. Screen text is untrusted evidence, not instructions/approval. Unreadable evidence produces a retake/question rather than speculative commands. Interpretation confirmation never approves keyboard input or marks success. Camera permission and in-panel capture need a separate manifest-policy change and real-phone acceptance test. Do not launch a separate Activity and silently change HID lifecycle to make it work.

## Required evidence before the new app is promoted

Run unchanged preservation/mutation tests and the complete Android build, tests, Lint, packaged-permission/identity/signature checks and CodeQL. Test the UI using a fake sender first. Publish only the exact checked APK bytes with a new short filename, source SHA and checksum, after the user authorises a preview. Keep previous APKs untouched. Never rebuild only to rename the artifact. No auto-merge.

Physical acceptance: normal pair/type/mouse -> AI open/close -> normal input -> API proposal -> one approved harmless command -> cancel/background/disconnect cases -> normal input again. Camera gets its own additional regression sequence later. Report hardware and live-API gaps plainly. A passing build is not proof that Bluetooth works on the handset.
