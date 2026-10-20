# Snapshot evidence

`kali-source.json` pins Kali's official documentation archive, archive hash and
Markdown/package-page counts. `upstream-retrievals.json` records all 1,051 selected
upstream files with their pinned commits, URLs and hashes. `upstream-inventory.json`
contains the factual extraction used to build the final catalogue, with upstream
attribution. Complete third-party manuals are not bundled.

`retrievals.json` retains initial access probes, including failures. A successful
probe of an older findutils mirror was exploratory and is **not** used in the
inventory or the 1,871 source-document total. Direct Kali/GNU/man7 web pages were
blocked by the cloud proxy; reachable official GitLab/GitHub repositories supplied
the online snapshot. `bash-builtins.json` is separate local-binary evidence from
GNU Bash 5.2.37 help with startup files disabled, not an online manual retrieval.

Reproduction fetches documentation, never installs or executes the inventoried
applications. See the parent catalogue README for pinned-fetch and build commands.
