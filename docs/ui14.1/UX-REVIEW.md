# UI flow review

This review used Android accessibility/navigation guidance and established usability
heuristics. It is a heuristic and emulator review, not a user study or certification.

| Criterion | Change and evidence |
| --- | --- |
| Visible system state | Auto/Voice remain above scrolling content; native geometry checks keep them fully visible at 320–412 dp and 100–200% text. |
| Clear next action | Step/Preview share one pinned approval. Selecting a command opens detail without typing or a second approval modal. |
| Information hierarchy | Ready commands precede optional plan background. The goal form collapses; no cat image sits above operational steps. The offline tree remains accessible. |
| User control | Stop cancels the session and clears key/camera state. Back returns detail → Plan → Home while preserving the draft and invalidating pending work. |
| Error recovery | Provider errors stay visible. A real timeout now turns Auto OFF instead of leaving an apparent running session. A possibly transmitted task is excluded from the returned queue. |
| Readable commands | Long command text wraps without inserted hyphens or ellipsis; the matrix checks every visible line and retains a readable task viewport. |
| Accessible controls | Reviewed controls have at least 48 dp height. Seven principal source-palette text/background pairs exceed 4.5:1; the lowest measured pair is 5.49:1. |
| Consistent navigation | Home / Keyboard / AI / Settings remain at the bottom; Plan / Step / Preview / Result stay within the AI workflow. |

Primary references retrieved during the review:

- https://developer.android.com/guide/topics/ui/accessibility/apps
- https://developer.android.com/guide/navigation/principles
- https://developer.android.com/develop/ui/views/layout/declaring-layout

See [native screenshots](screens/README.md), [matrix](layout-matrix/README.md),
[source-palette measurements](verification/source-contrast.json), and the
[test plan](../UI14-COMMAND-FLOW-TEST-PLAN.md). Some external heuristic manuals were
blocked by network policy and are not represented as retrieved evidence.

API31 ATD lacks a normal system IME/launcher. Real soft-keyboard and voice interaction,
TalkBack exploration, launcher-grid presentation and handset ergonomics still need
device testing. The existing app remains portrait locked; landscape support was
considered as a limitation rather than claimed as tested.
