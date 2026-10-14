# UI12 — detailed plans, one approval and foreground Auto

The user explicitly requested automatic execution through the reviewed plan, including Enter,
and OpenAI-generated spoken explanations. UI11 source/delivery `84ff5219962608955317239cda520d2c5779b857`
is the rollback point. Work is isolated on `feature/ui12-detailed-plans-auto-assist`.

## Intended behavior

- Plan responses carry a detailed phased roadmap, assumptions, clarification questions and up
  to 40 ordered command steps. Broad security goals cover inventory, recovery/backups, supported
  OS/patching, users/privileges, authentication/SSH, network exposure/firewalls, updates,
  monitoring, application/data permissions, verification and maintenance. Critical missing facts
  still block executable changes; the full roadmap remains visible. No model can guarantee an
  unhackable system. Simple tasks should not be padded.
- Each command has an explanation, expected result, risk and warnings. Selecting/reading/copying
  does not type. The Step page and offline detail page show the exact destination and command,
  with one final approval button. Their ordinary sender still excludes Enter and invalidates
  stale host/plan snapshots. Preview remains optional.
- Revision context supports the full ordered command list up to a bounded 32,768-character
  request, without silent truncation. Goal input remains 2,000 characters. Manual photo feedback
  retains the roadmap and following steps instead of replacing the whole task with one suggestion.
- Auto opens a separate visible foreground session. Opening it makes no API/HID request.
  The user starts a live camera preview, reviews the destination/commands, keeps the terminal
  focused, checks the frame for secrets, and explicitly approves automatic paid photo requests.
  A preflight image must appear to show an idle shell prompt before any typing.
- Full Auto types supported reviewed commands and presses Enter once, explains their purpose,
  waits five seconds, captures one terminal photo and asks OpenAI to assess it. It advances only
  for the exact visible command, expected-looking output, evidence and a fresh shell prompt.
  These are visual assessments, never human-confirmed success or verified exit codes.
- Alternative modes run only exact allowlisted read-only checks, or pause for approval/manual
  Enter at every step. Full Auto pauses for unsupported/destructive commands; it is not an
  arbitrary shell-script runner. Password prompts, editors/pagers, busy/unclear screens and
  failed checks stop progress. No partial send or failed change is automatically retried.
- Error feedback can generate a revised remaining plan. At most three revision requests are
  allowed in a session. Full Auto can adopt revisions containing only remaining originally
  approved commands or strict read-only diagnostics, never a repeated transmitted change.
  Expanded scope is proposed for review and stops the current session.
- The Stop control is always at the top. Backgrounding, navigation, destruction, host/epoch or
  plan change stop typing/capture/network/playback and clear session key/audio/photo references.
  Nothing resumes automatically. An already issued network request or command cannot be undone
  by Stop; inspect partial text/output before a fresh session.
- OpenAI speech uses the official `/v1/audio/speech` API, `gpt-4o-mini-tts`, `marin`, and bounded
  in-memory 24 kHz mono 16-bit PCM playback. It speaks the model's friendly explanation and what
  to expect, rather than reading command syntax. The voice is disclosed as AI-generated.
  Voice is configurable in Settings: OpenAI, an explicitly offline phone voice, or text only.
  No microphone is used for narration. Provider/audio failures leave explanations on screen.

Official speech documentation checked on 14 October 2026:
https://developers.openai.com/api/docs/guides/text-to-speech

## Boundaries and limitations

The four frozen HID files, existing manual keyboard/mouse path, encrypted vault, passive HID
scope and Camera2 controller remain unchanged. No dependency, SDK, app ID, Bluetooth pairing,
permission expansion, Wi-Fi transport or desktop helper is introduced. AI alone gains the TTS
service package-visibility query; offline still has no Internet/camera/microphone permission.
The new Enter exception is isolated to the explicitly approved foreground Auto sender.

HID cannot establish which desktop window has focus, return an exit code, read stdout or prove
that all characters reached the correct application. Vision can misread a screen, including a
prompt or error. Keep the terminal focused, the camera aimed at it, and watch the session. Auto
does not solve these inherent transport limitations. A broad security plan needs user-provided
OS, access and recovery facts; SSH/firewall changes need particular lockout precautions.
Commands requiring passwords/manual prompts or unsupported editing/shell syntax stop for the
person. Five seconds is a first observation interval, not proof that a long command finished.

## Automated evidence

Record actual outcomes before delivering an APK: frozen policy/mutation checks, strict roadmap
and clarification contracts, detailed-plan revision tests, Auto session/approval/Enter/host/stop/
scope tests, conservative vision contracts, narration request/privacy checks, both flavor unit
tests/Lint/APK inspection, real-window emulator fixtures using synthetic API/HID results,
signatures/checksums and CodeQL. Fixtures must never send a paid request or real HID report.

## Required phone and computer acceptance

1. Upgrade the UI11 test APK; verify Home/Keyboard/AI/Settings, Bluetooth and normal manual keys.
2. Request a comprehensive hardening plan with known distro, local/remote access, essential
   ports/services and recovery access. Check all roadmap phases and ordered checks/changes/
   verification, specific safeguards and rollback. Check clarification does not invent facts.
3. Select a step/library command and approve it once on the same screen. Verify no second dialog
   and no Enter outside Auto. Change host, background or edit the plan before approval; reject
   the stale action. Test phone widths 320/360/390/412 dp, large text and the soft keyboard.
4. On a disposable Linux session, select text-only narration first. Open Auto; verify no request
   or typing on open. Check camera framing, commands, risk and destination before explicit Start.
   Verify preflight rejects a covered camera, non-terminal window and password prompt.
5. Test Full Auto first with `id`, `whoami`, `uname -a`. Confirm exactly one Enter per command,
   visible photo check between steps, no advance on unreadable or unexpected output, and no
   claim of security/exit-code certainty. Keep the terminal focused.
6. Test a reviewed reversible change on a disposable target, authentication beforehand, long
   output/pagers, slow commands, deliberate errors and an exhausted photo/revision budget.
   Verify no password typing, partial-send retry, replay or expanded-scope execution.
7. Stop during the countdown, typing, pending photo, API response and speech playback. Disconnect,
   change host, turn Bluetooth off, lock/background/navigate and kill the Activity. Confirm no
   Enter or next command after invalidation, no replay on resume, and camera/audio indicators
   end. Inspect partial text before manually clearing or starting a fresh session.
8. With the user-held phone key, test paid vision, proposed and bounded automatic revisions,
   speech/model access/billing/network failures and natural explanation playback. Verify mute,
   volume, Bluetooth audio route and denial/cancellation. API-key metadata checking does not
   establish audio-generation access. No live API/hardware acceptance is inferred from fixtures.
9. Confirm temporary keys are session-only and cleared on background/close; encrypted remembered
   keys still work with Android Keystore. Check no goals/commands/photos/audio/keys in diagnostics
   or gallery. Both variants retain original licenses and app identity/signing upgrade behavior.

Do not promote the Bluetooth baseline or production release without recorded hardware evidence.
