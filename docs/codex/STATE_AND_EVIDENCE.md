# Black Cat — current state and evidence

Prepared: **2026-10-12**

This file replaces the obsolete September handover snapshot. The detailed source of truth is `FULL_PROJECT_HANDOVER.md`.

## Current branches and commits

- Default branch: `main`
- `main` head at handover: `83380e2a3e26a43899fe9596a08d91a3f27dba91`
- Latest UI09 development/documentation head: `85ba15ec22399806f3c5bdae4795e55dd65c1f16`
- Exact UI09 release source: `2153ab44a6fb98ecb35bfd849b8e629a27748a80`
- Codex handover branch: `codex/ui09-handover-2026-10-12`
- Tested UI08 parent: `6eec650cb3fdf2f0e5b9b463c9c20c10539eaa29`
- Physically successful Bluetooth V2 reference: `9771772bbe25dd8eba8d7303f7e4680aa84175df`
- API02 preservation reference: `7712ea981230a2782a83b0920d3da5f09bc47c1d`

Do not use `main` as the implementation starting point. It has the current public README but an older source tree.

## Latest released candidate

Release:

`https://github.com/efrgdgr0024345/autophone/releases/tag/ui09-2153ab44`

AI APK:

`https://github.com/efrgdgr0024345/autophone/releases/download/ui09-2153ab44/CatAI-09.apk`

SHA-256:

`f969bbcc24390f2acca8a5e5eec04d6a2686b811f21f75683ade3fa31d75c1db`

Offline-focused APK:

`https://github.com/efrgdgr0024345/autophone/releases/download/ui09-2153ab44/CatBT-09.apk`

SHA-256:

`8753552ba6703451d5e4c02e4a79e00fbe6448395493c34f6e6a0cd5f7df992f`

Both APKs use application ID `com.blackcat.remote`; install one variant at a time.

## Automated release evidence

Workflow run:

`https://github.com/efrgdgr0024345/autophone/actions/runs/38205227657`

Final jobs succeeded:

- Verify and build UI09;
- CodeQL;
- Publish exact UI09 prerelease.

The run covered:

- frozen Bluetooth/HID policy checks;
- UI09 mobile planner contract;
- offline catalogue checks;
- 212 commands, 13 categories and 63 topics;
- planner/photo regression tests;
- shared and AI Android tests;
- Android Lint;
- AI and offline APK assembly;
- APK identity, permission and signature inspection;
- CodeQL;
- SHA-256 and provenance attestation.

## Evidence boundary

The automated run does **not** prove:

- physical Android HID registration;
- computer discovery/pairing;
- keyboard/mouse operation on the user's hardware;
- Bluetooth reconnection or off/on recovery;
- Camera2 operation on the intended phone;
- Android Keystore behaviour on the intended phone;
- host keyboard-layout correctness;
- current OpenAI model/account access;
- successful execution of any Linux command.

UI09 remains a physical-test prerelease.

## Current product scope

Shared:

- normal Bluetooth HID keyboard/mouse;
- send text;
- touchpad and clicks;
- HID key panel;
- diagnostics;
- offline Linux command library.

AI flavor:

- user-held OpenAI key;
- strict structured command proposal;
- Plan → Step → Preview → Result;
- reviewed photo feedback;
- one-use TYPE ONLY approval;
- no automatic Enter.

Offline flavor:

- no OpenAI client/network mode;
- local tree/search/explanation library;
- same reviewed Bluetooth typing boundary.

## Important unresolved items

- Physical UI09 acceptance has not been recorded.
- The production Android planner is simpler than the experimental guided-plan state machine.
- Offline mode is a static command library, not a local LLM.
- Result-page wording may conflict with the separate Photo Feedback feature and should be audited.
- Current source model IDs/API fields may require a future official-documentation verification task.
- `main` and the UI09 lineage are heavily diverged; no automatic merge is authorized.

## Credential state

No API key is part of this handover.

Any key previously used in chat or an isolated test environment must not be recovered, copied or embedded. Moving to Codex does not authorize live paid API testing.

## Source provenance

Preserve:

- Linkpad licence/attribution;
- GhostBoard licence/attribution;
- existing Black Cat artwork assets;
- previous release tags and APKs;
- the exact V2/API02/UI08/UI09 references.

For current instructions, read:

- `AGENTS.md`;
- `CODEX_START_HERE.md`;
- `FULL_PROJECT_HANDOVER.md`;
- `CHAT_HISTORY_TIMELINE.md`;
- `NEXT_TASK.md`.