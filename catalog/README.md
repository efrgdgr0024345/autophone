# Offline Linux and Kali catalogue

The requested plain JSON is [offline-catalog.json](offline-catalog.json). It contains
its own counts, counting rules, sources, compatibility, verification and learning links.
[MANIFEST.json](MANIFEST.json) records exact JSON/asset hashes; the [schema](offline-catalog.schema.json)
and validator check the structure, references, lesson graphs and preserved original cards.

## Snapshot contents

- **3,448 distinct documented CLI invocations:** 3,133 binary names, 262 subcommands,
  and 53 builtin-only entries. A further eight builtin names overlap external commands;
  61 Bash builtins are documented without double-counting those shared names.
- **824 tool/package groups**, including 820 Kali groups and 1,561 Kali package sections.
  Groups can contain libraries or metapackages as well as applications.
- **467 command-example cards / 465 distinct command lines:** all 212 original cards
  plus 255 additions. Two pre-existing duplicate lines retain their original IDs/categories.
- **35 lessons, 184 linked steps, five learning paths**, and one reusable tool-discovery template.
- **14 task categories and 89 topics.** The existing categories are retained; Kali is added.
- 139 service-unit references and 80 manual-only topics are kept outside the command inventory.
- 2,519 help/version invocations actually observed in source documentation, recorded as
  reference data rather than thousands of automatically approved operational commands.

These are bounded source-snapshot counts, not a claim to enumerate all software ever
available for Linux or every possible argument combination. Reference inventory is not
execution proof or an extension of the Auto allowlist. Only the selected local practice
lessons were executed during validation; tool installation and device behavior vary.

## Structure and useful fields

`systems → taxonomy → command_examples` provides the task tree. `tools → entry_ids`
links an application/package collection to executable, subcommand or manual references.
`lessons → steps → command_id` links reusable examples in an explained sequence.
`learning_paths` joins related lessons. `workflow_templates` describes literal-name
substitution for availability and documentation discovery, without inflating example counts.

The example `tool_id` is a descriptive family label (such as `apt`), not a
foreign key into the tool/package directory; inventory records use `tool_ids` and
`entry_ids` for validated directory links.

Each example includes an exact command, explanation, expected output, risk, privilege,
compatibility, shell profiles, prerequisites, expected network effect, state-change scope,
verification, troubleshooting, cleanup and lesson/source links. Each lesson has a goal,
prerequisites, ordered steps, expected evidence, explicit error-stop transitions and next lessons.
Source versions, architectures, metapackages, package dependencies, installed sizes,
CLI option names and observed help invocations are included where the source supplies them.
Missing synopses and version information are not invented.

For example, the text learning path is:

`unused-path check → private directory → sample → bounded reads → filtering → copy → compare → exact-file cleanup`

The tool guide follows availability, local documentation and documented help, then leads
to task-specific lessons. Ordinary Next navigation never types, executes or confirms a result.
The existing one-use TYPE ONLY approval and manual Enter boundary remains unchanged.

## Research and provenance

The online research retrieved **1,871 documents**: 820 Kali package pages plus 1,051
pinned files from GNU Coreutils, util-linux, iproute2, systemd, Git, curl, OpenSSH and
OpenSSL upstream repositories. Kali's official GitLab archive contained 824 Markdown
files; four navigation/root pages are excluded from package-page counts. File URLs,
commits, hashes and retrieval outcomes are in [research](research/).

Bash's 61 builtin records came from the installed GNU Bash 5.2.37 help, queried with no
profiles or startup scripts. They are labelled local-binary evidence, not fresh online
manual retrieval or proof of the version on a Kali computer. The direct Kali/GNU/man7
websites were blocked by the environment's proxy; official GitLab/GitHub sources were used.
The failed initial URL probes are retained separately from successful inventory sources.

Manual topics are not turned into invented commands. For example, a `tc-cake(8)`
reference is not presented as `tc cake`, and OpenSSL format-option documentation is not
presented as an `openssl format-options` subcommand. Dotted utility names and shared
manual sections are preserved. Short factual metadata/labels retain upstream attribution;
complete third-party manuals are not redistributed.

[Learning-flow design](LEARNING-DESIGN.md) explains the implemented sequence and
proposes goal-first suggestions, target profiles, evidence per step and explicit
remaining-plan changes for later camera/voice integration.

## Full Kali workflow support

`workflow_support` now maps all **820 Kali tool/package groups and 3,055 Kali
CLI entries** to source-linked workflows; the broader catalogue maps all 824 groups
and 3,448 CLI entries. All 38 package-only Kali groups get local package inspection,
without inventing executables. Command workflows include availability, local manual
summary, a bounded manual view, and a simple observed help invocation when available.
The app also provides a literal argument builder and complete-guide plan import.

Use **Kali Linux → All Kali command workflows** for the entire paginated command
list. Search inside the list, open an entry, then choose its guide or task builder.
The editor supports up to 24 argument fields within the final command limit.
Each field is one literal argument, including empty values. An optional
user-selected `sudo` prefix is visible in the preview and may request a password. Shell quoting
prevents outer-shell expansion; tools can interpret their own arguments and may
change files or contact network targets. Prepared task commands start at HIGH risk
and keep the existing 512-character printable-ASCII typing limit. Building and
adding a plan are local data edits; the existing fresh TYPE ONLY approval still
applies. Unattended Auto retains its separate supported command subset.

Coverage means that every entry has a valid route through discovery, preparation
and review. It does not mean every program was installed, executed or given a
complete expert-authored task cookbook. Source versions, missing documentation,
package availability and required arguments must be checked on the actual computer.
The 467 curated examples and 35 task lessons remain separately counted.

## Reproduce or edit

The app never downloads the catalogue. The public JSON and compressed bundled asset
are generated together; catalogue browsing has no API or account requirement.
The app asset uses the opaque `.catalog` suffix to preserve its gzip bytes through
AAPT packaging. APK inspection checks both compressed and decompressed hashes.

```bash
# Optional: reproduce this exact pinned documentation snapshot outside the checkout.
python3 tools/fetch_offline_sources.py --output /workspace/security-review/ui15/research
python3 tools/research_offline_catalog.py /workspace/security-review/ui15/research

# Edit curated-commands.tsv or the linked lesson definitions, then regenerate.
python3 tools/build_offline_catalog.py
/workspace/.autophone-tools/planner-venv/bin/python tools/check_learning_catalog.py
python3 tools/check_offline_catalog.py
python3 tools/test_learning_catalog.py -v
```

Network fetching retrieves documentation only; no inventoried tool is installed or run.
Re-running the builder is offline and deterministic. Changing snapshot versions needs a
separate provenance review; do not silently relabel old sources as current. Do not edit
old catalogue chunks or existing APKs. Run both flavors' tests/Lint/build, APK inspection,
CodeQL and native learning-flow checks before publishing a candidate.

A later extension can bind lesson IDs to reviewed AI plans and camera evidence. The
schema has the source/verification/transition fields for that; this update does not
add unattended offline execution, persistent progress or a local language model.
