# UI17 — natural language search and live plan drafts

Start/rollback: `e109a68589ab4822b58d7871d4a6425fceb17359` on
`feature/ui16-full-kali-workflows`. Work branch: `feature/ui17-natural-search-streaming`.

## Scope

Offline search indexes the 467 curated cards, 3,448 command workflows and 824
tool/package groups. It removes conversational filler, expands common task
intents and ranks exact command names and relevant inspection commands. Every
matching topic remains accessible; command results paginate in groups of 40.
This is deterministic English task retrieval, not a local language model.

The search OS initially follows the target setting. Its visible chooser changes
only the current library's search scope. Kali-only source references are excluded
from other scopes because cross-distribution compatibility has not been established.
Common Linux references remain eligible. Package managers and explicit distribution
requirements are filtered separately; installed tools/hardware still need checking.
Catalogue browsing and search make no network request and store no query history.

Interactive plan and text-feedback requests use Responses API server-sent events,
with the existing model, strict JSON schema, endpoint, tools disabled and `store:false`.
Prose, roadmap phases and command cards render as a visibly labelled live draft.
The active draft step is highlighted. The view follows new steps until the user
scrolls; Follow latest step resumes following. Auto/Voice and Cancel remain outside
the content scroll. Native progress replaces rapid flashing. Reasoning events are
ignored; the UI displays task progress and generated plan fields only.

Drafts never enter PlanWorkspace or a command sender. Completion requires the
stream's complete final response, agreement with received deltas and the unchanged
strict production parser. Truncation, malformed/oversized streams, refusal, failure,
cancel, backgrounding and stale revisions cannot commit a partial plan. Existing
plans survive request failures. Manual feedback replacements still need Apply.
Foreground Auto's planner and reviewed photo-analysis requests retain their existing
bounded nonstreamed wire path and behavior; this update does not change their
execution/observation policy.

No transport, permission, model, dependency, camera, vault or manual-input changes.
Ordinary command approval remains TYPE ONLY; Auto's reviewed foreground exception
and supported command subset are retained. No merge or baseline promotion.

## Verification

- Search: connection/disk/RAM/Wi-Fi/DNS/service/process tasks, exact subcommands and
  single-character builtins, empty/unknown queries, deterministic full result sets,
  Kali/common Linux selection and incompatible package-manager exclusion.
- Wire: real production HTTPS client with JVM connection double; strict streamed
  request schema, display callbacks, fragmented UTF-8, escapes, truncated JSON,
  missing completion, mismatch, refusal, mixed clarification/command decisions,
  oversized events and suppressed reasoning payloads.
- Native emulator: actual library search/topic/OS chooser, production SSE parser
  with synthetic chunks into the real plan view, no partial workspace/approval,
  late callbacks after cancellation, and 320/360/390/412 dp at 100%/200% font.
- One small paid live request under the user's existing test-key authorization:
  synthetic network-inspection goal, real SSE endpoint, same production request
  serializer and production parser replay. Host transport is distinct from phone
  transport and no target command is executed.
- Full frozen-file/policy/data mutations, planner contracts, both variants' units,
  lint/build/permission/signature inspection and CodeQL before APK publication.

Physical Android/computer pairing, real HID typing/Enter in approved Auto, actual
camera optics, audio playback/dictation, IME and Keystore acceptance remain required.

## Approximately 20 MB on-device model

A compact quantized embedding model could improve paraphrase/multilingual ranking
over this deterministic baseline. It is a retrieval aid, not a reliable general
planner or command generator. The download size excludes tokenizer/runtime/index
and working memory; speed, RAM, supported phones, licensing and query accuracy
need measurements before integration. No model/runtime/download dependency is added
in UI17. Compare a representative query set against this baseline before accepting
the storage and maintenance cost. All displayed commands continue to come from the
catalogue and retain their existing review policy even if a ranker is added later.

API reference: https://developers.openai.com/api/docs/guides/streaming-responses
