# GitHub operating model — Black Cat Android

GitHub is the source of truth, build system, review gate, provenance record and release archive for this project.

## Immutable references by convention

Do not develop on these refs:
- `baseline/bluetooth-v2-9771772b` — physical Bluetooth reference.
- `baseline/api02-7712ea98` — API-connected repair reference.
- release `preserved-v2-api02-20260929` — exact APK bytes and source snapshots.

These refs are preserved but repository-admin branch/ruleset protection is not currently enabled. Do not describe naming alone as a lock.

## Development branches

Every new milestone gets its own feature branch and draft PR. Current UI work:
- base: `codex/blackcat-ui-handoff`
- feature: `feature/mobile-ui-v1`

No direct development on `main` or either baseline branch.

## Pull request is the control surface

The PR body must contain:
- exact base/reference commit,
- intended scope,
- explicit files/behaviour that must remain unchanged,
- automated results,
- live API test status,
- physical-device test status,
- candidate APK SHA-256,
- any known warnings or limitations.

Keep the PR draft until the user has physically accepted the candidate. Never use a green CI badge as proof of Bluetooth behaviour.

## Actions

`.github/workflows/mobile-ui-candidate.yml` provides:
- exact-file Bluetooth preservation checks against API02,
- policy and mutation tests,
- planner/photo contract regression tests,
- Android unit tests and Lint,
- both flavor builds,
- packaged identity/permission inspection,
- APK signature inspection,
- CodeQL,
- SHA-256 records,
- 90-day candidate/report artifacts,
- GitHub artifact provenance attestation on branch builds.

A candidate is an artifact, not a release.

## Release discipline

A new APK becomes a release only after:
1. CI passed for the exact commit.
2. The exact artifact was downloaded from that run; do not rebuild merely to rename.
3. The user physically tested normal pairing/type/mouse before and after AI.
4. API behaviour was tested using the user-held key, if relevant.
5. Source SHA, APK SHA-256 and signature details are recorded.
6. The user explicitly approves promotion.

Old releases are never overwritten.

## Supply-chain verification

Candidate branch builds use GitHub artifact attestations for the exact AI APK. The attestation links the artifact digest to repository/workflow/commit provenance. It supplements, not replaces, the APK signature and SHA-256 record.

## Live API tests

Paid/live OpenAI tests are not part of normal CI. Deterministic tests run without credentials. A live batch must be separately bounded and explicitly authorised. The GitHub Actions secret must never be copied into repository files, APKs, logs or chat.

## Physical acceptance issue

Keep one issue per candidate with the physical sequence and actual observations. Close it only when the device test is complete or explicitly abandoned. Link the issue, PR, Actions run and release candidate so future work has a complete audit trail.
