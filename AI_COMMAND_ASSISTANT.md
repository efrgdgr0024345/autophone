# AI Command Assistant — scope, safeguards and validation

## Approved goal

Branch from the exact locked, physically tested BlackCat-v2 source. Keep computer-initiated Bluetooth
HID pairing, keyboard and mouse. Add an OPTIONAL OpenAI command-proposal interface. A user supplies
a natural-language goal and target OS/shell; OpenAI returns explanation, assumptions, questions and
ordered commands. The user reviews/selects exactly one command before typing it over the existing HID.
The AI must never send commands automatically or claim it can read the computer.

## Implementation

- The four V2 HID/diagnostics files are byte-for-byte pinned in `tools/check_policy.py`.
- MainActivity retains pairing/manual controller UI and opens AI as a dialog in the SAME Activity,
  avoiding destruction of the Activity-owned V2 HID manager when entering AI.
- Product flavors: offline has no network/client/vault; ai adds only INTERNET and uses a separate app ID.
- OpenAI Responses API, fixed `https://api.openai.com/v1/responses`, JSON Schema structured output,
  no tools, `store=false`, bounded input/output, finite timeouts, no retries or redirect following.
- Initial selectable API models: gpt-4.1-mini (default), gpt-4.1. Availability depends on the user's account.
- Goal <=2000 chars, target <=500, max 12 commands, command <=512 printable US-ASCII characters.
- Invalid JSON, refusals, incomplete responses, unsupported characters and ambiguous mixed
  questions/commands produce no sendable plan. Schema conformance does NOT imply safe commands.
- Output is plain native text, not HTML/WebView or executable Markdown.
- The network client receives only the entered goal/target/model and key for authentication. It never
  receives device names/MACs, HID diagnostics, screen, clipboard, terminal output or control callbacks.
- A selected command is snapshotted with the exact local HID host session for confirmation.
- Two explicit checks require human review and an empty terminal/US-layout/Caps-Lock-off confirmation.
- One-use approval; no input merely from generating/selecting. TYPE ONLY sends one line, never Enter.
- Stop/background/dismiss/disconnect/session change cancel remaining keystrokes. There is no durable queue.
- All API/key operations are off the main thread; UI updates and HID send admission are on the main thread.
- Results describe Android report acceptance, NOT host delivery, execution or task success.

## Credentials and privacy

No key is requested through ChatGPT, embedded, logged, shipped or committed. User-entered personal
BYOK mode is deliberate; shared production credentials should instead remain behind a controlled
backend. OpenAI recommends not deploying keys into mobile apps: this preview cannot make a phone
impossible to compromise. Use your own restricted project key, monitor billing and revoke when needed.
Key saving is optional. A fresh Android Keystore AES-GCM nonce protects ciphertext in noBackupFilesDir;
no plaintext fallback; Forget removes ciphertext and its wrapping key. It does NOT revoke the API key.
Key/goal fields disable view-state saving, autofill and personalised-learning hints. AI dialogs use
FLAG_SECURE; the original Bluetooth diagnostics panel remains available for troubleshooting photos.
Plaintext exists transiently while sending API requests; JVM Strings cannot be reliably zeroised.
No automatic goal/response/history persistence. Closing/backgrounding cancels and clears the AI UI.
`store=false` does not promise zero retention; OpenAI abuse monitoring/other platform retention applies.
INTERNET permission is app-wide in the AI APK; code restricts its client to OpenAI, not an OS-enforced
per-domain sandbox. There are no third-party runtime network libraries or telemetry SDKs added.

## What this does NOT prove/fix

V2's successful physical report is the user-reported baseline, not an AI/hardware integration test.
The V2 Activity-owned HID lifecycle and manual modifier-button limitations are inherited; this change
makes no new guarantee about background HID survival or full keyboard combinations. No descriptor,
pairing or radio code changes are mixed into the AI extension. The human must verify terminal focus,
layout, existing input and the final characters before pressing Enter. Privileged/destructive shell
commands remain possible and potentially harmful even if a model labels them low risk. A prompt,
keyword list or JSON schema cannot certify their semantics. Cancellation cannot retract already-typed
characters or guarantee an already-accepted API request incurs no cost.

## Tests / release gates

Automated tests include: all 95 printable ASCII mappings; rejection of LF/CR/TAB/ESC/bidi/Unicode;
full pre-validation; no Enter/modifier injection; one-use approval; wrong host; reconnected session;
mid-command disconnect; cancellation release; report failure; schema payload; no tools/storage;
model refusal, malformed/truncated response, command limits and clarification handling.
Both offline and AI APKs undergo full lint, unit tests, manifest/identity/credential-debuggability
inspection and CodeQL. Reports are uploaded on failure, not only on success. Golden blob checks must pass.

Physical/API gates BEFORE promoting beyond preview:
1. Install the AI app alongside, but do not RUN concurrently with, the locked V2 keyboard app.
2. Verify the original computer-side discover/pair, text and mouse path on the actual phone/laptop.
3. Open AI panel; check the HID remains registered/connected. No API request before Generate consent.
4. Use your own key IN THE APP: wrong key, offline, denied quota, valid request, cancellation.
5. Generate an initial read-only goal (show the current user). Verify selection alone types nothing.
6. Review and TYPE ONLY; verify every character and absence of Enter, then deliberately run it.
7. Disconnect/background/cancel halfway through a long benign command; inspect partial text; no replay.
8. Try a new session/host before confirming: approval must be rejected, never silently redirected.
9. Save/close/reopen/Forget; test Android Keystore on the actual device, including reinstall/lost-key handling.
10. Check tiny-screen layout, keyboard opening, credential masking and no content in copied Bluetooth logs.

Live API requests are NOT testable without an authorised user key. Do not fabricate passing results.
CI-signed previews are not production signing: signing-key management remains a release task.

## Primary references checked for this implementation

- OpenAI Structured Outputs: https://developers.openai.com/api/docs/guides/structured-outputs
- OpenAI GPT-4.1 mini: https://developers.openai.com/api/docs/models/gpt-4.1-mini
- OpenAI API key safety: https://help.openai.com/en/articles/5112595-best-practices-for-api-key-safety
- OpenAI data controls: https://developers.openai.com/api/docs/guides/your-data
- Android Keystore: https://developer.android.com/privacy-and-security/keystore
