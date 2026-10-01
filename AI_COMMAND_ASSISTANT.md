# Black Cat AI repair 02 — phone-held key, V2 behaviour preserved

## Governing requirement

Computer discovers/pairs with the phone as a standard Bluetooth HID keyboard/mouse. Do not introduce phone-side scanning or a companion program. The user-reported physically successful source is 9771772bbe25dd8eba8d7303f7e4680aa84175df. That commit and its locked branches/APK are not modified by this repair.

## What changes in this repair

Restore MainActivity from that exact baseline, including initialisation, permission requests, discoverability, callback handling, manual Send, mouse controls and lifecycle. One explicitly delimited hook adds an AI button to the existing diagnostics action row; it does not access HID, credentials or the network at startup. Removing this hook must reproduce the exact golden Activity blob. Four companion HID/diagnostic files remain byte-for-byte frozen too. CI enforces both checks, with tests proving that unauthorised Activity modifications fail.

The AI panel is a dialog within the existing Activity. Its own lifecycle observer cancels only AI work. It does not register, pair, connect, disconnect or close the Bluetooth service. Normal manual input never depends on AI readiness, host metadata, connection epochs or model results. The AI-only transport adapter observes system connection changes and rejects stale command approvals. Android report acceptance does not prove delivery or execution; review actual text before pressing Enter.

Both build variants use the original com.blackcat.remote application identity. This removes the deliberate side-by-side identity introduced in CatAI-01. An already-installed CatAI-01 still has its old separate identity and must be stopped/removed for the next test. Multiple installed apps alone do not prove the cause of the reported failure: actual logs from the failing phone were not supplied. Competing HID registrations and altered Activity input paths are regression risks addressed here, not a conclusively isolated root cause.

## Phone-only API key

There is no website proxy, key download or embedded credential. The user enters a replacement personal key in the app. Optional Save encrypts it using Android Keystore-backed AES-GCM and excludes ciphertext from backup. Forget removes the local copy, not the provider credential. No key from chat is used, copied, logged or compiled. A compromised phone can still expose a usable credential; this personal BYOK arrangement is not the recommended architecture for shared production keys.

Requests go directly to the fixed HTTPS OpenAI Responses endpoint after explicit Generate consent, with store=false, no tools, finite limits and no redirects or automatic retries. Only the goal/target/model and authentication are sent. Bluetooth metadata, clipboard and terminal output are not uploaded. Responses are proposals only; unknown/incomplete/malformed outputs are not sendable.

## User control

Explanation, assumptions, questions and separate commands remain visible. Selection alone types nothing. The user confirms the exact command and destination. TYPE ONLY sends a bounded single printable-ASCII line, never Enter. Hidden controls and unsupported characters are rejected before transmission. Changing goal/target/model invalidates the old proposal. Dismiss, background, disconnect and cancellation invalidate AI work; nested confirmation windows are dismissed too. No output is treated as proof of task completion.

## Scope and validation

This repair deliberately does not refactor frozen V2's background lifecycle, modifier-key semantics or low-level reports. Background HID persistence is NOT newly guaranteed. It fixes integration regressions by restoring the tested path, not by proving every inherited behaviour correct.

CI builds/tests/lints both variants, reviews full reports, verifies packaged identity/permissions/licences/debug flags/signatures, and runs CodeQL. The APK built in that run is the APK published; no packaging-time rebuild. Every preview has a commit-specific release tag and checksum; no clobbering the golden APK or earlier previews.

Physical Bluetooth regression, on-phone key storage, live user-key API, small-screen UI and cancellation tests remain mandatory. See docs/AI02_INSTALL.md. Green automated checks cannot establish that the reported radio failure is fixed on the user's handset.

## Credits and primary references

GhostBoard / ToxicOrca HID lineage and original MIT licence are retained. Linkpad / Devdas Kumar references and licence remain credited. No upstream work is claimed as original.

- Android BluetoothHidDevice: https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice
- Android Keystore: https://developer.android.com/privacy-and-security/keystore
- OpenAI key safety: https://help.openai.com/en/articles/5112595-best-practices-for-api-key-safety
- OpenAI structured output: https://developers.openai.com/api/docs/guides/structured-outputs
