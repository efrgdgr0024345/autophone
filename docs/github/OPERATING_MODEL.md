# GitHub operating model — Black Cat Android

GitHub is the source of truth, build system, review gate, provenance record and release archive.

## Preserved references

Do not develop on:
- `baseline/bluetooth-v2-9771772b` — physical Bluetooth reference.
- `baseline/api02-7712ea98` — API-connected repair reference.
- release `preserved-v2-api02-20260929` — exact APK bytes and source snapshots.

The names preserve intent; repository-admin branch/ruleset protection is not currently enabled, so naming alone is not described as a technical lock.

## Feature development

Use one feature branch and one draft pull request per milestone. UI03 uses:
- base: `codex/blackcat-ui-handoff`
- feature: `feature/mobile-blackcat-ui-v1`
- PR: #13

No direct development on `main` or either baseline ref.

## Pull requests are the control surface

A candidate PR records:
- exact base and candidate commits,
- intended scope and frozen behaviour,
- automated results,
- live API status,
- physical-device status,
- candidate APK SHA-256,
- build/run links and known limitations.

Keep the PR draft until physical acceptance. Green CI is not physical Bluetooth evidence.

## Actions and provenance

`.github/workflows/ui03-ci.yml` runs:
- frozen V2 policy and mutation tests,
- UI/API boundary and mutation tests,
- Android unit tests, Lint and both flavor builds,
- packaged identity/permission inspection,
- APK signature inspection,
- CodeQL,
- exact SHA-256 metadata,
- 90-day reports and candidate artifacts,
- GitHub artifact provenance attestation for the exact AI APK.

The attestation ties the APK digest to the repository/workflow/commit. It supplements the APK signature and SHA-256; it does not prove physical Bluetooth behaviour.

## Release discipline

A candidate becomes a release only after:
1. CI passes for the exact candidate commit.
2. The exact GitHub Actions artifact is tested; it is not rebuilt merely to rename it.
3. The user physically checks pairing, keyboard, mouse, AI open/close, one explicit TYPE ONLY action, cancellation/reconnection, and normal input afterward.
4. API behaviour is tested with the user-held key where relevant.
5. Source SHA, APK SHA-256, signature details, Actions run and limitations are recorded.
6. The user explicitly approves promotion.

Never overwrite old releases.

## Live OpenAI tests

Normal CI is credential-free. Paid/live API tests are separate, bounded and explicitly authorised. The GitHub secret must never be copied into source, APKs, logs, reports or chat.

## Physical evidence

Issue #11 is the acceptance record for UI03. Link the issue, PR, exact Actions run and any promoted release so future work has a complete audit trail.
