# State and evidence at the Codex handover

## Preserve history; do not repeat the regression

V2 (9771772b) was explicitly reported working by the user and is the original physical Bluetooth reference. CatAI-01 changed MainActivity, ordinary-send gating/lifecycle and the Android application identity while keeping only four HID-named files unchanged. The user reported Bluetooth failure before testing AI. Those changes were risks and violated the intended boundary; the exact handset failure was not conclusively isolated from logs.

CatAI-02 (7712ea98) restores the original Activity with only the lazy AI-button hook, original application identity, normal controls and four unchanged helper files. The user most recently asked to preserve the Bluetooth version and the API-connected version as the two working references. Record that request without manufacturing detailed device-test logs or saying either is defect-free. Existing PR #9's earlier physical-testing caveat remains part of history.

The preservation release `preserved-v2-api02-20260929` now contains the exact APKs/source snapshots and checksum records. It has immutable=false in GitHub metadata and baseline branches report protected=false. Preservation is not administrator-enforced locking.

## Repository map

- main: eab976708ebc2fd30d5a254ecd4c50ab80546826 at handover; not the starting point for this UI work.
- baseline/bluetooth-v2-9771772b: 9771772bbe25dd8eba8d7303f7e4680aa84175df.
- baseline/api02-7712ea98 and fix/ai-preserve-v2: 7712ea981230a2782a83b0920d3da5f09bc47c1d.
- PR #9: API02 repair, historically draft/unmerged.
- test/guided-photo-planner / PR #10: 9448fa94a66c1a88c14e296058c6a2c543bc268f, adds isolated lab/planning docs without app-source changes.
- codex/blackcat-ui-handoff: derived from 9448fa94; only handover docs and a credential-free source/check packaging workflow are added here.
- web/lab.php: approved 03.0-cat-mobile prototype imported from the companion handover attachment, not presumed already in GitHub.

Live refs can change: fetch current branch/PR state before any future write. Do not rewrite/depend on truncated SHA strings when exact values are available.

## Test evidence and limitations

The prior API02 reports recorded 6 baseline guard tests, 21 AI unit tests, 14 offline test executions and successful build/CodeQL. Lint had zero blocking errors but 44 AI and 22 offline warnings; do not call the application warning-free.

The guided lab run 36374998234 used a bounded live batch of 12 synthetic cases, including 8 fabricated terminal images; no generated commands were executed. Saved summary: 23,473 input tokens and 6,756 output tokens; only 2 cases passed the implemented indicators and 10 were rejected by the contract. These are development failures to analyse, not evidence of a production-ready planner. Some questions/commands were blocked too broadly; missing usernames, mutually exclusive sudo paths and continuing after unreadable images were genuine problems. Do not loosen safety gates just to improve a score.

The approved PHP prototype reports 22 backend checks, 60 browser-workflow checks, 39 layout/photo checks and 12 HTTP/request checks from its creation. This handover preserves those reports, not falsely re-executing the whole browser suite. Its real API implementation remained; test runs used controlled responses. New paid requests require a fresh, explicit bounded approval.

## Credentials and privacy

No credential value from chat is included. A key previously pasted into chat should be treated as exposed and replaced; never recover/copy it into any handover. GitHub secret name `openai_api_key` was available in an earlier workflow. Its current validity/expiry is unknown. Presence is not proof of API access, automatic expiry or spend control. Do not read/export it or inject it into Codex/project files. No new live requests are authorised by migration.

Android uses direct OpenAI requests with an optional encrypted on-phone key. The PHP demo uses a key in browser-tab memory which passes through PHP per request; no storage by the script. This web-only arrangement is not a decision to add a website dependency to Android. Keep both privacy models distinct.

## Design and source provenance

Preserve the original GhostBoard/ToxicOrca and Linkpad/Devdas Kumar attribution/licences. Artwork consists of user-provided/generated Black Cat material, including forward-facing black fur and green eyes and the approved face/paws integration. The requested Library folder was `/linux usb project/Graphical design for black cat project`; tool folder listing returned no contents, so do not claim every image was verified as that folder's final master. The actual approved prototype and bundled images are the immediate UI reference.

Only project-relevant source, specifications, artwork and synthetic results are transferred. The raw conversation, exposed credentials, personal laptop/cafe photographs, local TLS private keys and unrelated Black Cat projects are intentionally excluded.
