# Black Cat Remote — Project Rules

## Required development pipeline

All substantive code changes must use a branch and pull request. Do not treat a successful compile as proof of Bluetooth correctness.

Before merge, the project should pass:
1. clean Android build on GitHub-hosted runner;
2. unit tests;
3. Android Lint;
4. manifest/security policy checks;
5. CodeQL analysis where supported;
6. AI/code review plus review of its findings;
7. artifact checksum tied to exact source commit;
8. physical Bluetooth acceptance testing for behaviours that CI cannot emulate.

## Security invariants

- No INTERNET permission.
- No analytics, advertising, telemetry or crash-reporting SDKs.
- No Accessibility Service.
- No logging or persistence of text typed by the user or derived key identities.
- Minimum Bluetooth permissions only; every added permission requires justification.
- android:allowBackup remains false unless a reviewed design change explicitly requires otherwise.
- Bluetooth HID lifecycle must not depend on the Activity remaining visible.
- Asynchronous Bluetooth/HID operations are considered successful only after their documented callback/state confirms success.
- Debug diagnostics may be extensive but must redact user input.

## Review priorities

Reviewers and AI tools should prioritize Android Bluetooth HID correctness/lifecycle, HID descriptor/report correctness, key/button release, reconnection races, API-level permissions, exported components, sensitive logging, accidental networking, unnecessary dependencies, and unintended input.

## Definition of CI success

A green CI run means the source compiled and passed automated checks. It does NOT claim that physical Android and PC Bluetooth stacks successfully paired. Physical tests remain a release gate.

See docs/V1_CODING_PLAN.md for V1 architecture and acceptance criteria.
