# Current continuation: owner-approved metadata-key setup

The user separately approved setup of the update-metadata signing key. The
existing APK signing identity and permissions must remain unchanged. Branch:
`feature/ui20-provisioned-release`; rollback `b51ef53538aa95adfd884329eda9763b63bce413`.

New offline provisioning/signing/publishing tools and 17 dedicated tests are
prepared. All 122 Python tests passed before trust integration. The owner supplied
secret `BLACKCAT_UPDATE_KEY_PASSWORD`; the real encrypted authority was generated
outside Git and its public manifest is provisioned. Never print secret values.
See [provisioned release state](../updates/PROVISIONED-RELEASE.md).
GitHub Actions secrets access was denied HTTP403 over HTTPS; no private material
was uploaded. See
[publishing prerequisites and steps](../updates/PUBLISHING.md). Finish genuine
public trust provisioning, version23/build tests and live feed verification once
the secret and protected owner backup are available. No placeholder trust,
production APK release, OCR implementation, baseline promotion or signing
configuration change is authorized by this intermediate tooling checkpoint.

# Previous continuation: signed update checks and OCR evaluation

Tested feature source: `64d3caae9d807bcd07c0caf5e8b0d23c576d3fee` on
`feature/ui20-authenticated-updates`, from approved CatAI-19 lineage
`bd7f38295404721c984ede7813a363a46ae02aba`.

See [update security design](../updates/SECURITY-DESIGN.md),
[exact-commit evidence](../updates/EVIDENCE.json) and
[OCR evaluation only](../updates/OCR-EVALUATION.md). Production update trust is
deliberately unprovisioned; no new APK/release or signing change is authorized by
this delivery. Next work needs reviewed public metadata trust and release-host
provisioning. Do not create production keys, change signing configuration, add
installation permissions, or implement OCR/observation timing changes without
the user's separate approval. Five newly surfaced CodeQL certificate-pinning
warnings in unchanged OpenAI clients remain open for a separate review.

# Previous UI19 continuation

Latest source: `173946bb1172cea930dfd78ed032967b022d8fcf` on `feature/ui19-evidence-driven-auto`.

The user approved the reliability review and meaningful natural explanations. The diagnostic milestone is now an installable physical-test candidate. See [UI19 evidence](../ui19/README.md) for exact URLs, final-APK versus unchanged-UI baseline test scope and remaining hardware acceptance.

Further work: narrow configuration contracts with prerequisites/post-checks/recovery, independent pre-Enter/camera-quality evaluation and device testing. The frozen Bluetooth/manual/camera/vault files, permissions, dependencies and model pins remain protected. Do not repeat the historical intake as a new permission requirement, merge or promote a baseline without physical acceptance.

---

# Next task — Codex UI09 intake, audit and continuation plan

This is the first bounded task for the new Codex project.

It is deliberately an intake/audit task rather than another feature implementation. UI09 has automated evidence but does not yet have recorded physical acceptance. Do not invent UI10 or rewrite Bluetooth before establishing the real current state.

---

# Objective

Produce an evidence-based current-state report for Black Cat UI09 and identify the smallest justified next step.

Do not change production source during the first pass unless a test cannot run because of a handover-document error. Any source change requires a new branch and user approval after the intake report.

---

# Required starting point

Repository:

`efrgdgr0024345/autophone`

Starting branch:

`codex/ui09-handover-2026-10-12`

This branch contains the full UI09 source and the current handover.

Do not switch to `main` for implementation. Do not merge branches during intake.

Record:

```bash
git status --short
git branch --show-current
git rev-parse HEAD
git log --oneline --decorate -12
```

Confirm that the released APK source is an ancestor of the handover lineage:

`2153ab44a6fb98ecb35bfd849b8e629a27748a80`

Confirm that later commits are documentation/handover changes rather than a silently rebuilt release.

---

# Mandatory document review

Read:

- `AGENTS.md`;
- `CODEX_START_HERE.md`;
- `docs/codex/FULL_PROJECT_HANDOVER.md`;
- `docs/codex/CHAT_HISTORY_TIMELINE.md`;
- `docs/UI09-TEST-PLAN.md`;
- `README.md`.

In the intake report, explicitly confirm the golden rule:

> Preserve the working Bluetooth core and build around it.

---

# Source inventory

Confirm that these major areas exist and summarize their responsibilities:

## Frozen/shared Bluetooth path

- `HidManager.kt`
- `HidDescriptor.kt`
- `HidReports.kt`
- `Diagnostics.kt`
- critical `MainActivity.kt` lifecycle/input path

## Shared command safety/library

- `CommandSafety.kt`
- `LibraryHidTarget.kt`
- `LinuxCommandCatalog.kt`
- `LinuxCommandLibraryPanel.kt`
- `app/src/main/assets/linux_commands_*.b64`

## AI flavor

- `AiEntry.kt`
- `AiTransportScope.kt`
- `ApiKeyVault.kt`
- `CommandPlan.kt`
- `OpenAiPlanner.kt`
- photo capture/analysis/panel classes

## Offline flavor

- `app/src/offline/java/com/blackcat/remote/AiEntry.kt`

## Tests/policy

- `tools/check_policy.py`
- `tools/test_policy.py`
- `tools/check_mobile_ui.py`
- `tools/test_mobile_ui.py`
- `tools/check_offline_catalog.py`
- `planner_lab/`
- Android unit-test source sets

Do not refactor any of these during inventory.

---

# Required automated checks

Run from repository root:

```bash
python3 tools/check_policy.py
python3 tools/test_policy.py -v
python3 tools/check_mobile_ui.py
python3 tools/check_offline_catalog.py
python3 tools/test_mobile_ui.py -v
python3 -m pip install -r planner_lab/requirements.txt
python3 -m unittest planner_lab.test_contract -v
```

Record the exact output summary and test counts.

Do not report a test as passed if it was skipped because a tool or dependency was missing.

When Java/Android tooling is available, run:

```bash
gradle testOfflineDebugUnitTest testAiDebugUnitTest \
  lintOfflineDebug lintAiDebug \
  assembleOfflineDebug assembleAiDebug \
  --continue --console=plain --stacktrace
```

Record:

- Gradle version;
- Java version;
- generated APK paths;
- test totals;
- Lint result and warnings;
- APK sizes and SHA-256 if builds succeed.

Do not publish those local builds and do not call them UI09 release artifacts. The published release is tied to source `2153ab44...`.

---

# Required source audit

Without changing code, inspect and report on these questions:

1. Does the current production planner visibly implement `Plan → Step → Preview → Result`?
2. Is **Start with step 1** present and functional from source inspection?
3. Can a user still select later commands directly, and does the production Android schema enforce dependencies?
4. Does the Result page contain wording that contradicts the separate Photo Feedback capability?
5. Does opening/closing AI acquire, close or otherwise manage HID?
6. Is any API request triggered merely by opening a panel?
7. Can selection, copy, search or photo review type a command?
8. Is Enter excluded from both AI and library reviewed send paths?
9. Does the offline flavor contain any Internet permission or OpenAI client path?
10. Does the AI flavor request only reviewed additional permissions?
11. Do both variants share the original application identity?
12. Does the offline library validate to exactly 212 commands, 13 categories and 63 topics?
13. Are long commands, large fonts, soft keyboard and narrow screens adequately covered by current tests, or only by static/source contracts?
14. Do current OpenAI model identifiers and request fields require a future official-doc verification task?
15. Are there any swallowed errors, stale wording or unreachable UI actions that can be demonstrated from source/tests?

Distinguish:

- confirmed defect;
- suspected issue;
- design limitation;
- untested physical behaviour.

---

# Physical testing gap report

Codex cannot claim these without real hardware evidence:

- phone registers successfully as HID;
- computer discovers/pairs;
- keyboard/mouse reports work on the intended devices;
- disconnect/reconnect works;
- Bluetooth off/on recovery works;
- Camera2 works on the intended handset;
- Android Keystore works as intended;
- host layout renders every ASCII character correctly;
- OpenAI request works with the user's current account/model access;
- a shell command produces its expected result.

List exactly which physical feedback should be requested from the user next. Reuse `docs/UI09-TEST-PLAN.md` rather than inventing a new incompatible checklist.

---

# Required intake response format

```text
BLACK CAT CODEX INTAKE REPORT

Repository:
Branch:
Commit:
Released UI09 source relationship:
Working tree clean:

DOCUMENTS REVIEWED
- ...

SOURCE MAP
- Bluetooth:
- Manual remote:
- AI planner:
- Photo feedback:
- Offline library:
- Tests/policy:

AUTOMATED CHECKS
- Command:
- Result:
- Test count:
- Notes:

ANDROID BUILD
- Tools available:
- Java:
- Gradle:
- AI build:
- Offline build:
- Lint:
- APK paths/hashes:

AUDIT FINDINGS
- Confirmed defects:
- Suspected issues:
- Known limitations:
- Security/privacy observations:

PHYSICAL TESTS STILL REQUIRED
- ...

SMALLEST RECOMMENDED NEXT TASK
- Purpose:
- Why now:
- Proposed branch:
- Files expected to change:
- Protected files guaranteed untouched:
- Tests required:
- Risk:
- Rollback point:
```

---

# What not to do in this task

- Do not modify Bluetooth source.
- Do not merge to main.
- Do not publish a new release.
- Do not bump version numbers.
- Do not change model names without current official verification.
- Do not add dependencies.
- Do not run a paid live API batch.
- Do not add a server/proxy.
- Do not weaken tests.
- Do not claim physical success.
- Do not call the offline catalogue an offline LLM.

The task is complete when the intake report is evidence-based, reproducible and specific enough for the user to approve the next bounded branch.
