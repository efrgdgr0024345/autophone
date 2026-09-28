# Guided planner + photo feedback lab

Read ../GUIDED_PLAN_AND_PHOTO.md first. This is an isolated prototype/test contract, NOT an Android feature or APK. The same schema and control invariants must be used by the later Kotlin implementation. No Android code, build setting, frozen-source check or live Bluetooth path is changed in this stage.

Local checks (no API key):

```
python -m pip install -r planner_lab/requirements.txt
python -m unittest planner_lab.test_contract -v
python -m planner_lab.run_eval --output planner-results/dry
```

Live evaluation is deliberately separate, explicitly opted in, fixed model and at most 12 requests. Only the known synthetic fixtures are accepted by the runner. No option reads a directory of user photos. No generated command is passed to a shell, subprocess or Bluetooth sender.

```
python -m planner_lab.run_eval --live --max-cases 12 --output planner-results/live
```

OPENAI_API_KEY is injected by GitHub Actions from the previously authorised secrets.openai_api_key only for that run step. It is not supplied as a command-line argument, logged, committed, packaged or downloaded. The app will continue using the user's phone-held key; this lab does not change app credential storage.

Workflow: a push on test/guided-photo-planner runs deterministic tests. A paid batch runs only when its commit message includes [live-planner-12], only on the first run attempt, and never on pull_request. Subsequent changes do not automatically incur API usage. Authentication, quota or network errors stop the batch with no retries. Manual workflow dispatch requires explicit live opt-in. There is no schedule. A per-case request/output cap bounds usage, but is not a promise of provider billing accuracy or a claim that GitHub enforces a dollar cap.

results.json contains returned proposals from fabricated test scenarios, usage and limited automated indicators. It contains no real screen captures, API key or request headers. SUMMARY.md is a brief result table. An indicators_passed result is NOT a guarantee of correct, safe or useful Linux commands. Review the full synthetic proposals. Contract failures and API failures remain failures; never silently drop difficult cases.

The state model uses FakeKeyboard, whose only effect is appending characters to an in-memory list. It tests that photo approval, result interpretation, revision acceptance and command approval are distinct. Pixel preprocessing tests cover crop, flattened opaque redaction and EXIF removal. These tests do not exercise an Android camera, native UI, device permissions, real keyboard layout or Bluetooth radio.

Next integration gate: implement step-based AI screens and in-panel capture behind the existing AI button without changing MainActivity or the HID files. AI-only camera permission must be reviewed separately; keep text feedback usable if permission is denied. Physical camera/permission/background transitions must be tested before publishing a new known-working baseline. Current CatAI-02 stays the existing test APK; no new APK is produced by this lab.
