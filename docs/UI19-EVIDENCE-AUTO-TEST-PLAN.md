# UI19 — meaningful explanations and evidence-driven diagnostics

User-approved first reliability milestone. Start/rollback:
`267e886d9e3a63d0627bd2d29c18f64051dc6d0b`; branch
`feature/ui19-evidence-driven-auto`. Preserve all frozen HID/manual/transport,
camera, vault, permission, dependency, model and signing boundaries.

## Intended behavior

Auto applies positive argument grammars for supported diagnostic actions, in
addition to existing shell/control-character guards. A tool name or model risk
label does not authorize configuration changes. Unsupported steps retain the
ordinary reviewed manual path without automatic Enter. Later configuration
contracts need prerequisites, scope and independent post-change verification;
they are not silently authorized by this diagnostic milestone.

Each original task has a stable foreground identity and state. Capture/page/cleanup
helpers share that task. Transmission is an attempt; interrupted transmission is
uncertain. Only final complete evidence can label a task "Evidence checked".
Replacing remaining steps preserves earlier identities and marks unstarted
replacements. This is photo-supported evidence, not proof against a compromised host.

Capture retains up to 1 MiB plus one overflow-detection byte, drains stdout to
avoid SIGPIPE, and prints a mandatory DONE/TRUNCATED suffix with the exit code.
Overflow produces incomplete evidence, exact-file cleanup and a narrower diagnostic
revision. Sixteen-page limits also remain explicit. The original command is not
rerun merely to obtain another page. Output is temporary, private and foreground-only.

Spoken explanations use concise natural sentences about purpose and findings,
without generic reassurance, raw command recital or paid narration for every
internal helper. Actual task findings are spoken after collection/reconciliation.
Voice timeout/failure falls back to visible text. Existing OpenAI speech/model,
phone-held key selection and optional offline phone voice stay intact.

Auto shows its actual phase and paused state. Session limits are 120 photo requests,
12 plan revisions, 80 paid speech requests and 45 minutes. Stopping cannot undo a
Linux command already running. A saved API key keeps key editing in Settings.

## Verification required before APK publication

- Diagnostic grammar acceptance/rejection, including dangerous arguments on familiar tools.
- Real Linux helpers: complete snapshots, byte-boundary overflow, exit codes, pagination,
  resize, permissions, exact cleanup and no repeated original execution.
- Stable task identities, interrupted/uncertain states, deadline invalidation, stale
  markers and truncated/limited/failed evidence never becoming success.
- Independent OCR against rendered Linux output, including clipped pages and automatic
  remaining-plan revision. Test-only HID/observer bridge must never ship in the app.
- Existing units/policy/mutation/planner/catalogue/artwork gates, both Lint/builds,
  permissions/signatures/APK inspection and traced CodeQL.
- Full native workflow, Auto, timeout, streaming, catalogue and width/font checks.
  Record APK hashes for every run and label captures from earlier candidates explicitly.
- Bounded synthetic live API checks when the dedicated test key is available;
  distinguish these from simulated providers and physical transport.

No phone shell, desktop companion, new permissions, dependency/model upgrades,
automatic restart, merge or physical-baseline promotion. Remaining later milestones
include independently evaluated pre-Enter camera readback, richer dependency/recovery
contracts, camera quality measurements and actual handset/Bluetooth acceptance.

## Live footer-format correction and final regression scope

Candidate source `5dc08a9be6fdd312d3fed1ef5cbb8b749763f089`, APK
`9b3ef1a5c0cad9b5892f43328e649c2839539f6ddc6d49d6e414c1fd861f247f`,
passed all 88 native checks and the actual Linux/OCR image loop. A focused live
overflow request correctly reported incomplete output but placed the full printed
footer in `page_marker`. The decoder expected its prefix and would pause.

The final production correction is isolated to `AutoObservation.kt`: accept either
the exact prefix or a full printed footer whose separate exit-code and state fields
agree. Never repair nonce characters, guess missing text or copy request context.
Wrong nonce/code/state/suffix values still cannot match a current operation.
Replay the actual live response through the final decoder. The independent OCR
driver now deliberately returns full capture/cleanup footers to exercise this path.

Re-run all mandatory policy/unit/Lint/APK/CodeQL gates, then the affected native
Auto/key/stop/timeout flows and the Linux image loop on the final APK. Retain the
full 88-check UI baseline for unchanged streaming/catalogue/width/font code, with
its own source and APK hash. Verify that the production diff from that baseline
contains only the observation codec. Do not claim baseline screenshots came from
the final APK or add their counts to final-APK regressions without distinguishing them.
