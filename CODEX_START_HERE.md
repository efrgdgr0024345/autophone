# Black Cat — Codex handover

Prepared 2026-09-29. This is a repository handover, NOT proof that a Codex cloud environment or coding task has been created.

## Start here

Repository: `efrgdgr0024345/autophone`
Handover branch: `codex/blackcat-ui-handoff`
Starting source: `9448fa94a66c1a88c14e296058c6a2c543bc268f` (the isolated guided-plan/photo lab on top of the API02 repair).

Do not start implementation from `main`, CatAI-01, or an old root-level APK. The repair, lab and approved web prototype were not merged into main. Read `AGENTS.md`, `docs/codex/NEXT_TASK.md`, `GUIDED_PLAN_AND_PHOTO.md`, and `docs/codex/STATE_AND_EVIDENCE.md` before editing.

The latest request is to preserve the Bluetooth and API-connected references, then implement the approved mobile Black Cat design in the Android AI panel. The PHP site is a visual/interaction reference, not the Android runtime or a new server dependency.

## Approved prototype attachment

The companion `BlackCat-Codex-Handoff.zip` supplied in the ChatGPT conversation contains the exact approved `web/lab.php` (03.0-cat-mobile), its embedded artwork extracted for reuse, design references, the recorded UI checks, and saved synthetic API evaluation outputs. These new attachment files are not assumed to exist in GitHub yet. Import them from the bundle before implementation; do not invent or redraw missing artwork.

Expected approved PHP SHA-256:
`7237133ba67b9b4d7c981361aba8c8395a338c1890464465869a90b8c39f6375`

The bundle provides `codex_handoff/ASSETS_SHA256SUMS` and an import helper. Import only verified reference files, without overwriting app source or repository policies. Until that import, report the asset handover as pending rather than claiming it has happened.

## Preserved references

| Reference | Source commit | Original APK SHA-256 |
|---|---|---|
| Bluetooth-only V2 | 9771772bbe25dd8eba8d7303f7e4680aa84175df | 111e4d4fd35b3ddffddbecee18bf2972f79f95856f9bd395b0ccf75d469d4136 |
| API-connected repair 02 | 7712ea981230a2782a83b0920d3da5f09bc47c1d | 7fc937cfcc6a023c2d2f85896ebbec7dd4b9400f7b1b96bc11e35468beaf00ee |

Exact old APKs and source snapshots are preserved at:
https://github.com/efrgdgr0024345/autophone/releases/tag/preserved-v2-api02-20260929

Keep all baseline branches and releases. Their names do not enforce administrative locks. GitHub reported them unprotected at handover; this connection cannot enable repository-admin protection. Never claim otherwise.

## Environment and verification

Use the existing versions, not upgrades as part of a UI task: Java 17; Gradle 8.9; Android Gradle Plugin 8.7.3; Kotlin 1.9.23; Android compile/target SDK 35; minimum SDK 28. The repository uses a supplied Gradle installation, not a checked-in Gradle wrapper.

Useful checks from repository root:

```sh
python3 tools/check_policy.py
python3 tools/test_policy.py -v
python3 -m pip install -r planner_lab/requirements.txt
python3 -m unittest planner_lab.test_contract -v
gradle testOfflineDebugUnitTest testAiDebugUnitTest lintOfflineDebug lintAiDebug assembleOfflineDebug assembleAiDebug --continue --console=plain --stacktrace
```

After importing the approved UI:

```sh
php -l web/lab.php
php web/lab.php --self-test
```

Install missing development tools through the Codex environment setup with the user's normal permissions. Do not change app versions, SDK targets, security tests or dependency pins merely to suit the environment. Report missing tools instead of claiming a build passed. GitHub Actions remains the independent APK test/release path. No full Android build was rerun solely for this documentation handover.

## First Codex task

Create a feature branch from this handover. Verify/import the attached prototype. Implement the approved compact cat-themed layout INSIDE the existing AI panel while preserving the baseline Bluetooth path and existing API/key-storage/sender components. Run relevant tests and provide the actual diff/results. Do not merge, publish, run paid API batches or modify baseline references automatically. Use `docs/codex/NEXT_TASK.md` as the bounded acceptance contract.

The account owner must select/create the Codex environment using the existing repository. This file does not register an environment, configure billing, or start a background agent.
