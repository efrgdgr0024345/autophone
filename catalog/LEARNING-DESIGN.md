# Learning flows for Black Cat

A large command dictionary is useful for lookup. The main learning unit should be
a task with a goal, prerequisites, a sequence and a verifiable outcome. The shipped
35 lessons, five paths, 184 steps and tool-discovery template establish that structure.

## Present behavior

Start with a task category, Kali readiness, a learning path in the JSON, or a tool.
A lesson explains why each command is needed, what output to look for, and when to
stop. Its steps reference reusable command IDs. Next opens the next explanation;
it does not assert that the prior command ran or succeeded. Verification and exact
cleanup are steps in the same task, not unrelated entries elsewhere in a dictionary.
Tool discovery checks the literal executable, reads local documentation, and offers
a simple help invocation only when observed in the source.

For example: check that the practice path is unused; create a private folder;
write a known sample; display bounded lines; filter; copy; compare; clean up only
the exact files the lesson created. A missing tool, unexpected existing folder or
mismatched result has an explicit error/stop route. A package reference is not
misrepresented as one generic installation or execution command.

## Full-directory preparation and plans

All 820 Kali groups and 3,055 commands now have workflow mappings. Commands
connect backing-executable discovery to their own local manual (including
subcommands), a bounded manual view and observed help. Package-only groups
connect installed version, status and file evidence. The argument editor creates
literal argument values and an optional explicit sudo prefix, then opens exact
HIGH-risk command review. Complete guides can be added atomically to the existing
AI plan without a network request. Those plans can use the existing reviewed
text/voice/photo feedback flow; automatic lesson completion and persisted learning
progress are still separate future features. Auto retains its reviewed command subset.

## Proposed integration after this update

1. **Goal-first suggestions.** Offer a small set of relevant lesson paths from the
   person's goal and experience level. Keep the full directory as a secondary lookup.
2. **Verified target profile.** Use reviewed distro/version and tool-availability
   evidence to select compatible branches. A source snapshot version must never be
   treated as the version installed on the person's computer.
3. **Evidence per step.** Associate an observation with a lesson step and plan
   revision: typed feedback, spoken transcript or a reviewed terminal photo. Record
   user-asserted and machine-observed outcomes separately. Do not turn navigation
   or a photo upload into an implicit success mark.
4. **Explain changed direction.** When evidence changes the remaining plan, show
   the new purpose, exact command, reason for the change, and retained/obsolete
   steps. Prefer bounded output that can fit in camera frames. Reuse the existing
   Auto observation/collection boundaries when integrating; lesson metadata alone
   must not authorize automatic execution or retries.
5. **Useful progress.** Offer opt-in local progress with explicit reset/export;
   avoid retaining terminal images, command output or personal machine details by
   default. A persistent learning record needs its own privacy/storage review.
6. **Authoring and maintenance.** Add curated task recipes in small reviewed groups;
   track their source snapshot, distro/version constraints, last validation method
   and evidence. Update references reproducibly. Keep manual-only topics, package
   groups, executable names and complete command examples separately counted.

These are integration proposals, not claims that persisted progress, target
profiling or camera-driven lesson completion is implemented in this candidate.
The shipped JSON supplies compatibility, prerequisite, expected-result,
verification and transition fields to support that later work.
