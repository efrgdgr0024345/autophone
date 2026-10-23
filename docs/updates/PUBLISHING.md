# Owner-approved update authority and feed renewal

The owner separately approved creating the metadata-signing key. It is now a real
RSA3072 key encrypted with AES256 PKCS8 and the secret environment variable
`BLACKCAT_UPDATE_KEY_PASSWORD` (at least 24 characters). Its public trust manifest is
`updates/production-trust-v1.json`. This password is not an OpenAI API key. OpenAI
credentials are not required to sign, publish, check, download or install updates.
The APK signing identity and installation permissions were not changed.

Only public metadata/trust/APKs are on GitHub. The encrypted private key is outside
Git at `/workspace/.blackcat-update-signing/private/metadata-key-v1.pem` (0600;
parent 0700). The encrypted owner backup is
`/workspace/BlackCat-update-signing-key.encrypted.pem` (0600). Download that backup
privately and retain it outside this temporary workspace; store its passphrase
separately in a password manager. Workspace copies are not durable external custody.
Do not put either private key or passphrase in source, chat, issues or public files.
Losing the key requires a separate trust-transition/bootstrap review; do not silently
replace it. Existing APK signing-key custody remains a separate concern.

## Live feed and current deadline

https://raw.githubusercontent.com/efrgdgr0024345/autophone/blackcat-update-feed/updates/ai.json

Sequence 2 points to CatAI-20.1/build24. It expires **29 October 2026 at 00:55:58 EDT**
(`2026-10-29T04:55:58Z`). The app fails closed after expiry, even if the same APK is
still latest. The installed app otherwise continues to work. Six-day metadata is
intentional freshness protection; it needs regular renewal with an increasing
sequence, including when the APK is unchanged. The client maximum lifetime is 7 days.
Never extend expiry without resigning or disable freshness verification.

## What remains blocked

The connected GitHub integration returned HTTP403 over HTTPS for both Actions
secrets listing and repository encryption-public-key access. No private material
was uploaded there. **No scheduled renewal job is configured or claimed.** Adding
this Codex environment password does not create a GitHub Actions secret.

For future automation the repository owner can open GitHub → autophone → Settings →
Secrets and variables → Actions → New repository secret. A reviewed workflow would
need separately stored encrypted metadata-key material and its encryption password,
restricted publishing permissions, serialized/non-forcing updates, and an exact
reviewed release-tool checkout. Do not add secrets without that workflow/custody
review, make a signing job available to untrusted PRs, or publish a private key file.
No such workflow is activated in this delivery. Manual renewal remains available.

## Manual renewal by the trusted release operator

Use this reviewed feature source and Android build-tools 35.0.0. Keep the existing
secret environment variable available without printing it. The following commands
renew the same build24 only; a new APK needs its own source/test/artifact review.
Run from the repository root in this environment. Run one publisher at a time.

1. Fetch the current public envelope to a private work directory. Inspect its
   authenticity with the reviewed public root; `sign --previous` verifies it even
   when it has expired. Do not invent a lower prior sequence or use `--initial`.

```bash
source /workspace/.autophone-tools/activate.sh
mkdir -p /workspace/security-review/update-renewal
curl --fail --proto '=https' --max-time 30 \
  https://raw.githubusercontent.com/efrgdgr0024345/autophone/blackcat-update-feed/updates/ai.json \
  -o /workspace/security-review/update-renewal/previous.json
```

2. Determine the next sequence from that exact prior envelope. This step reads
   public metadata only; the signing tool authenticates it in the next step.

```bash
update_sequence=$(python - <<'PY'
import base64,json
from pathlib import Path
outer=json.loads(Path('/workspace/security-review/update-renewal/previous.json').read_bytes())
print(json.loads(base64.b64decode(outer['payload']))['sequence']+1)
PY
)
python tools/update_release.py sign \
  --trust updates/production-trust-v1.json \
  --private-key /workspace/.blackcat-update-signing/private/metadata-key-v1.pem \
  --apk docs/ui20/install/CatAI-20.1.apk \
  --apk-url https://raw.githubusercontent.com/efrgdgr0024345/autophone/cb900fa3def981ee72c2c01a0fbb7cc8de6ab6ea/docs/ui20/install/CatAI-20.1.apk \
  --aapt "$ANDROID_HOME/build-tools/35.0.0/aapt" \
  --apksigner "$ANDROID_HOME/build-tools/35.0.0/apksigner" \
  --sequence "$update_sequence" \
  --previous /workspace/security-review/update-renewal/previous.json \
  --output /workspace/security-review/update-renewal/renewed.json
```

3. Publish. This re-downloads the immutable hosted APK, verifies actual bytes,
   package/version/permissions/original cryptographic APK signer and embedded
   public authority, authenticates the existing remote sequence/version floor,
   and performs non-forcing Git ref publication. A concurrent change fails closed.

```bash
python tools/update_release.py publish \
  --trust updates/production-trust-v1.json \
  --metadata /workspace/security-review/update-renewal/renewed.json \
  --aapt "$ANDROID_HOME/build-tools/35.0.0/aapt" \
  --apksigner "$ANDROID_HOME/build-tools/35.0.0/apksigner"
```

4. Re-fetch the stable URL, verify it with `update_release.py verify --trust ...
   --metadata ...`, record the new expiry and test Settings → Check for updates.
   Never overwrite old APK commits or reuse a sequence with different metadata.

The release tool also has `generate-key`, `kotlin-trust`, `sign`, `verify` and
`publish` help. **Do not run generate-key again for renewal.** It intentionally
refuses existing files, repository paths, symlinks and absent/short passwords.
Tests use distinct ephemeral authorities; none are accepted by the shipped trust.
