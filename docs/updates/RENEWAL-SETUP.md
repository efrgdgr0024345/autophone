# Guided owner setup: signed-feed renewal

Prepared on `feature/ui20-feed-renewal` from
`89a5c6a4fb6d9569628f06aa33b72c4c8d032ace`. No app files, APKs, signing settings,
permissions, baseline branches or live metadata were changed. There is no active
renewal workflow yet. The template lives under `docs/updates/`, not `.github/`.

The template checks out exact helper source
`b57b90cd7f4e08fccc7a18125801f339f2974e9a`. The helper verifies the existing feed
against the approved public root, downloads the same immutable APK without
redirects, checks its signed bytes and APK identity, and renews only sequence and
timestamps. It cannot select a new release. The existing publisher independently
rechecks the hosted APK/public root, signatures and current remote rollback floor
before a non-forcing write. The final live ref and signed bytes are checked again.
An authentic expired envelope can be renewed; future-dated or unauthentic data fails.
Only public renewal results are logged. Private temporary key files are 0600 in a
0700 directory outside the checkout and are deleted on normal success/failure.
Hard interruption relies on GitHub's disposable hosted-runner teardown.

## Owner pages, one step at a time

1. Secrets page:
   https://github.com/efrgdgr0024345/autophone/settings/secrets/actions
2. **New repository secret** → Name `BLACKCAT_UPDATE_KEY_PASSWORD` → Secret is
   the same existing encryption password saved in the owner's password manager.
   It is not an OpenAI key or the contents of the PEM file. Click **Add secret**.
3. **New repository secret** again → Name `BLACKCAT_UPDATE_SIGNING_KEY_ENCRYPTED`
   → Secret is the complete existing encrypted PEM file, including its
   `BEGIN ENCRYPTED PRIVATE KEY` and `END ENCRYPTED PRIVATE KEY` lines.
   It must match the retained public root. Do not generate a new key or decrypt it
   into a public file. Click **Add secret**. GitHub will not reveal saved values.
4. Review the template `docs/updates/renew-signed-feed.yml` and its pinned helper.
   Adding repository secrets alone does not activate any job. Repository writers
   able to alter workflows may gain access to repository secrets; restrict write
   access and protect the default branch. The owner must keep an independent
   encrypted backup and password separately outside this temporary workspace.
5. After review, add the exact template as
   `.github/workflows/renew-signed-feed.yml` on the repository's default branch.
   Do not merge the application feature branches merely to activate this workflow.
   Creating that workflow is a separate owner-controlled step in the guided flow.
6. Open https://github.com/efrgdgr0024345/autophone/actions →
   **Renew signed update feed** → **Run workflow**, using the default branch.
   Inspect the result; a configured secret is not proof of a successful run.
7. Verify the real public feed signature, increased sequence, unchanged APK identity
   and new expiry, then test Settings → Check for updates on the phone.

The daily schedule is 07:17 UTC. GitHub scheduling may be delayed or disabled for
inactive repositories; inspect runs and enable failure notifications. The workflow
needs Contents-write permission and permits no pull-request events. Secrets are
passed only to the pinned helper step, not as shell interpolations. No artifact or
cache upload step is included. SDK build-tools 35.0.0 absence or token restrictions
cause failure without bypassing checks. No Actions execution, automatic renewal,
or physical phone acceptance is claimed until the owner completes activation and
a real run is verified. Manual renewal remains available in `PUBLISHING.md`.

## Local evidence

Helper source `b57b90cd7f4e08fccc7a18125801f339f2974e9a`: 10 new renewal tests plus
17 existing release-tool tests pass. Test keys are ephemeral and distinct from the
real authority; GitHub/network/APK-inspector interactions in these unit tests are
simulated. Real OpenSSL signatures, expired-envelope verification, unchanged release
identity, key-file permissions, no-redirect/bounded/hash download checks, future and
tamper rejection, and final publication readback/race failures are covered. The
public production root is read, never its private key. Logs: `renewal-tests.log`. Full Python discovery also passes all 132 tests
(`renewal-full-python-tests.log`).

Workflow YAML structure, shell syntax and safety constraints are checked locally.
These checks do not replace the first GitHub-hosted execution. No production key
was created, rotated, uploaded or used to publish during template preparation.
