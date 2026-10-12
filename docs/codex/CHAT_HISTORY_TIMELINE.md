# Black Cat AI Remote — conversation and decision timeline

This is a detailed project-focused reconstruction of the prior ChatGPT conversations. It is not a verbatim transcript. It records the user's decisions, corrections, bug reports, priorities and the engineering outcomes Codex needs in order to continue without repeating old mistakes.

Dates reflect the project conversation records available at handover.

---

# Phase 1 — original Android Bluetooth remote objective

## Initial goal

The user wanted an Android phone to act as a standard Bluetooth keyboard and mouse for a computer.

The essential requirement was not merely “send data over Bluetooth”. The computer had to see the phone as an ordinary input device with no companion computer software.

The desired first interface was simple:

- a text box;
- Send button;
- mouse/touch area;
- left and right click;
- expandable debug window;
- connection state and useful logs.

The user also asked whether the app could continue functioning when backgrounded and wanted Bluetooth-off detection and a clear keyboard/device display name.

## Early permission concern

An early APK produced an Android pairing/permission experience that appeared to request contacts or unrelated access. The user strongly rejected unnecessary permissions.

This established a permanent rule: request only the Bluetooth permissions required by the actual HID flow. Every new permission must have a concrete explanation.

## Early BLE success was not enough

At one stage, keyboard and mouse actions worked through a BLE/GATT-style connection, but the laptop did not enumerate the phone as a normal HID keyboard.

The user repeatedly clarified that data transmission alone was not success. The phone had to advertise/register correctly as a standard HID input device.

The user asked the development process to find a proven open-source HID project, clone the relevant approach, remove unrelated features, keep the existing UI where possible and credit the source.

This led to the Linkpad/GhostBoard-derived direction.

---

# Phase 2 — proven Bluetooth HID foundation

## Proven-source requirement

The user preferred adapting code that had already demonstrated normal keyboard/mouse behaviour rather than continuing speculative custom Bluetooth implementations.

The project imported/adapted relevant code with preserved licences and attribution.

Important sources:

- Linkpad-derived keyboard/mouse core;
- GhostBoard-derived HID pairing/lifecycle elements.

## BlackCat V1

Commit:

`ebe61292ca148d63e21ec0c7e7c02ca694385ec8`

This packaged a minimal Black Cat application around the imported/adapted HID core.

It added:

- normal keyboard and mouse descriptor;
- keyboard map;
- report sender;
- host discovery/pairing adaptations;
- diagnostics;
- usable keyboard/mouse controls;
- licences and attribution.

The V1 APK and checksum were retained as evidence.

## BlackCat V2

Commit:

`9771772bbe25dd8eba8d7303f7e4680aa84175df`

The repository describes this as the clean normal Bluetooth HID keyboard/mouse build and later governance treats it as the physically successful V2 baseline.

The user reported successful typing and mouse movement during this period and asked that the working build be locked/preserved.

A key instruction emerged:

> Keep the currently working Bluetooth code and build on top.

This became the “golden rule”.

## Pairing-model correction

The user wanted the computer to discover and pair with the phone as an input device.

The correct model was fixed as:

- phone registers as HID;
- phone becomes discoverable;
- computer initiates pairing;
- phone does not replace the flow with its own scan-first UI.

Relevant commits in this period include corrections for discoverability, pairing permissions and host-initiated flow.

The repository contains several historical experiment branches, but future work must use the V2 behaviour rather than revisiting all experiments.

---

# Phase 3 — Black Cat identity and UI direction

## Visual identity

The user provided a black-cat image and established a consistent brand direction:

- realistic black cat;
- vivid green eyes;
- forward-facing;
- friendly/intelligent expression;
- more body visible on the main screen;
- white background so the black cat stands out;
- dark/black panels for operational text and controls;
- bright green accents;
- tagline used in the wider project: “Stealth. Intelligence. Independence.”

## Strong correction on background treatment

During design discussion, the user repeatedly corrected the assistant when marketing-style or wrongly coloured mockups were produced.

The required rule was explicit:

> The black cat goes on the white background. Text and operational controls go on black/dark panels.

The design image was meant to show all actual UI screens, not a decorative marketing poster.

## Cat movement idea

The user suggested a future animation where the cat reacts when a menu changes size or shape, as though the expanding menu pushes the cat aside.

This remains a future optional enhancement. It must not compromise layout stability, touch targets or Bluetooth behaviour.

## Phone-first emphasis

The user emphasized that the system is always used on a phone.

The UI must therefore avoid desktop-style density and must keep controls above Android navigation buttons. Settings should live on a separate tab/screen rather than crowding the primary workflow.

---

# Phase 4 — AI Linux assistant concept

## User-controlled AI workflow

The user wanted to state a goal such as needing root access or wanting to inspect a Linux system.

The AI should:

- explain what the goal means;
- propose a plan;
- show commands;
- explain each command;
- identify privilege/risk;
- allow the user to select what to type;
- keep the user in control.

The assistant must be able to handle contextual feedback such as “you need to be root” and adapt the plan rather than blindly typing another command.

## No silent execution

The intended division was always:

- AI proposes;
- user reviews;
- phone types through Bluetooth;
- user decides whether to press Enter.

The user did not authorize an autonomous shell agent on the computer.

## Photo-of-screen idea

The user wanted to be able to photograph the computer screen/terminal so AI could understand errors or results.

This eventually became the reviewed Camera2 Photo Feedback path.

The photo is context/evidence, not permission to execute whatever text appears in the image.

## PHP demonstration

Before full Android integration, the user requested a PHP page that simulated the AI assistant using real API responses.

That prototype informed the Android UI and flow but was not meant to become a server dependency for the Android app.

---

# Phase 5 — regression caused by AI integration

## User report

After AI functionality was integrated, the user reported that the Bluetooth code had been broken.

The user explicitly asked to return to the last working version and preserve it.

This was the project's major process failure.

## Root lesson

The earlier preservation approach focused too narrowly on files named `HidManager`, `HidDescriptor`, etc.

In reality, Bluetooth behaviour also depended on:

- `MainActivity` lifecycle;
- permission timing;
- application identity;
- ordinary send gating;
- UI callback wiring;
- initialization and close timing;
- packaging/manifest differences.

Future policy checks therefore freeze the full behaviour path, not only HID-named files.

## API02 repair

Commit:

`7712ea981230a2782a83b0920d3da5f09bc47c1d`

The repair restored the original Activity/input path while making AI optional and lazy.

The Bluetooth V2 and API02 references were preserved together in:

`preserved-v2-api02-20260929`

The user wanted both references kept rather than one being silently replaced.

---

# Phase 6 — guided plan and photo safety work

## Richer planner concepts

The project explored a richer guided-plan model with step types such as:

- check;
- change;
- manual;
- decision;
- verify.

It also explored dependencies, human-confirmed outcomes, photo observations and plan revisions.

The Python `planner_lab` captures much of this work.

## Important safety conclusions

The conversations established that:

- missing critical context should produce questions, not speculative commands;
- a request to create a user does not imply making that user an administrator;
- password prompts and shell prompts are different;
- interactive operations must complete before later dependent commands;
- unreadable photos should produce a retake/question;
- model output cannot declare its own command successful;
- photo interpretation confirmation is not command approval;
- selecting a step is not approval;
- no partial send may automatically resume.

## Lab limitations

Earlier synthetic planner evaluation found many rejected/failed cases. That was treated as evidence of problems to solve, not a reason to weaken safety checks.

The richer lab was not silently merged into the Android production schema. The Android app still uses a simpler strict ordered-command structure.

---

# Phase 7 — UI03 through UI08 controlled redesigns

## Release discipline

The user expected each APK candidate to have:

- a short filename;
- exact source commit;
- SHA-256;
- release page;
- full direct download URL in README;
- clear statement of what was and was not tested.

The project moved through controlled UI branches/releases rather than overwriting one APK.

## UI04

A published example was:

`CatAI-04.apk`

Release tag:

`ui04-ab34f45d`

The user referred back to this build as a known downloadable point and asked for later UI changes to preserve working Bluetooth underneath it.

## UI06 and UI07

These iterations advanced the approved Black Cat visual language and photo-enabled lineage while retaining release checks.

Important tags:

- `ui06-9c46792d`;
- `ui06-approved-cb6d6878`;
- `ui07-c9cb8c35`.

## UI08 design guide

The user supplied a complete multi-screen image labelled:

“Black Cat AI Remote – UI Update”

The image's own subtitle reinforced the rule:

“Golden rule: preserve working Bluetooth core, build UI around it.”

It showed:

1. Home/Remote;
2. Bluetooth Connect;
3. Linux Assistant Plan;
4. Linux Assistant Step;
5. Command Preview;
6. Result;
7. Target/Settings;
8. Photo Feedback.

The user asked to use it as the design guide and proceed, with a key focus on making the AI planner work reliably and letting the user move through a plan.

UI08 release source:

`6eec650cb3fdf2f0e5b9b463c9c20c10539eaa29`

Release tag:

`ui08-6eec650c`

UI08 applied the approved screen language over the protected UI07/Bluetooth/AI/photo core.

---

# Phase 8 — offline Linux command mode request

## User request

Alongside the online planner, the user requested an offline mode containing a large body of Linux commands and explanations.

The desired interaction was a tree:

- select a broad area such as Network;
- expand to a task such as Check network status;
- continue expanding to a specific command;
- open an explanation of what the command does and what to expect.

The user also asked for a helpful search method.

## Interpretation and implementation

The current offline mode is a local command library, not a local language model.

It contains:

- 212 command entries;
- 13 categories;
- 63 topics;
- category/topic/command/detail navigation;
- local ranked search;
- command explanations;
- expected results;
- risk level;
- administrator requirement;
- compatibility notes;
- tags and synonyms.

The catalogue is packaged in compressed/base64 asset chunks and loaded locally without an API request.

Both CatAI and CatBT include this library.

## Offline typing boundary

Browsing and copying are harmless local actions and do not require Bluetooth.

Typing from the library still requires:

- current connected HID host;
- exact command review;
- one-use approval;
- TYPE ONLY;
- no Enter.

---

# Phase 9 — UI09 implementation and release

## New branch

UI09 work occurred on:

`feature/ui09-planner-offline-library`

It was based on the tested UI08 source rather than main.

## Planner changes

UI09 changed the visible order to:

`Plan → Step → Preview → Result`

It added an explicit:

**Start with step 1**

button after a plan is generated.

The goal was to make the phone workflow clearer and let the user proceed intentionally rather than treating the plan as a static list.

## Offline library integration

UI09 integrated the local command tree/search into both variants.

The AI build includes a visible “Browse Offline Linux Library” entry.

The offline build uses the Assistant entry to open the local library and contains no OpenAI planner.

## Protected-source process

The large UI source was modified through exact-guard transformations rather than rewriting frozen files by hand.

Policy checks confirmed:

- V2 HID/lifecycle behaviour remained frozen;
- UI09 planner flow was present;
- offline catalogue counts and safety constraints passed;
- no automatic Enter/API path was added to offline mode.

## Initial CI issue and fix

A bootstrap workflow initially created the intended source diff successfully but failed to push because a GitHub Actions token was not allowed to modify a workflow file without workflow permission.

The process was corrected so the workflow change and generated source commit were separated. The guarded source commit was then pushed.

## Test-regression issue and fix

The first UI09 verification run exposed an outdated mutation test that still searched for the old UI08 tab order/label.

The current tree passed the new policy, but the mutation test no longer altered the source and therefore did not raise the expected assertion.

The test was updated to mutate the new `Preview → Result` order correctly.

This is an example of the desired process: fix the test to represent the intended regression, not weaken the production rule.

## Successful release run

Final source commit:

`2153ab44a6fb98ecb35bfd849b8e629a27748a80`

Workflow run:

`38205227657`

Final jobs passed:

- Verify and build UI09;
- CodeQL;
- Publish exact UI09 prerelease.

The run produced:

- `CatAI-09.apk`;
- `CatBT-09.apk`;
- `SHA256SUMS`;
- `SOURCE.txt`;
- release notes and provenance evidence.

## README roadblock cleared

The user previously asked development to continue until a full direct URL could be placed in README for testing.

That point was reached.

CatAI direct URL:

`https://github.com/efrgdgr0024345/autophone/releases/download/ui09-2153ab44/CatAI-09.apk`

CatBT direct URL:

`https://github.com/efrgdgr0024345/autophone/releases/download/ui09-2153ab44/CatBT-09.apk`

The default branch README was updated with full links, hashes, source and test instructions.

## Remaining physical test

The user has not yet supplied the physical UI09 test results in the project record.

Therefore UI09 remains a physical-test prerelease.

---

# Phase 10 — handover to Codex

## User request

The user decided the time had come to move the project into a Codex project.

The user explained that Codex can access project files but not the prior ChatGPT conversations.

The required handover therefore needed to include:

- the full project objective;
- current source/release state;
- golden rules;
- coding rules;
- design direction;
- important commits/branches;
- known regressions;
- remaining testing;
- enough history to prevent Codex repeating old mistakes.

## Handover branch

A dedicated branch was created from the full UI09 documentation head:

`codex/ui09-handover-2026-10-12`

This avoids two problems:

1. Codex importing `main` and seeing the older source tree;
2. documentation-only changes triggering a new UI09 APK/release workflow.

The handover branch contains current rules and detailed project documents but does not represent a new APK release.

---

# Persistent user decisions Codex must remember

1. The golden rule is always first: preserve working Bluetooth and build around it.
2. The phone must be a standard Bluetooth HID input device.
3. The computer initiates pairing.
4. No companion desktop application.
5. No hidden command execution.
6. TYPE ONLY never sends Enter.
7. User reviews one step/command at a time.
8. AI and offline library must not own Bluetooth lifecycle.
9. The app is phone-first.
10. Black cat is on white; operational content is on dark panels.
11. Settings are separate.
12. Diagnostics are expandable and redacted.
13. Full HTTPS APK links belong in README.
14. Short APK filenames are preferred.
15. Do not say “tested” unless that exact test was run.
16. CI is not physical Bluetooth proof.
17. Old working releases and branches are never overwritten.
18. No API keys in repository, CI logs or handover.
19. Offline library is local and useful without network.
20. Future animation is optional and must not damage usability.

---

# Things that appeared solved but are not fully proven

- UI09 automated builds passed, but physical pairing is not recorded.
- The AI planner can generate strict command lists, but the complete richer dependency state machine is not in the production Android flow.
- The offline library is large, but it is not a generative offline AI.
- Photo feedback exists, but real-device Camera2 and live API behaviour still require physical acceptance.
- Keystore code exists, but on-device behaviour and device-compromise resistance are not proven by CI.
- Full direct APK URLs exist, but successful installation on the intended handset is not yet recorded.
- The app uses US-ASCII HID mapping; host keyboard layouts can differ.

---

# Expected Codex behaviour after reading this timeline

Codex should not immediately start rewriting source.

It should first:

1. confirm the handover branch and commit;
2. inspect the full UI09 source;
3. run the policy and test commands;
4. report current state and discrepancies;
5. preserve the release source/reference relationship;
6. wait for user feedback or approval for the next bounded task;
7. create a new branch for any actual development.

The safest next engineering input is likely physical UI09 test feedback. Without it, Codex may perform a source/UI audit, but it must not invent a Bluetooth fix for a failure that has not been observed.