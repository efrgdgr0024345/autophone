# Black Cat UI09 — planner and offline Linux library test plan

UI09 is built on the exact tested UI08 source commit `6eec650cb3fdf2f0e5b9b463c9c20c10539eaa29`.

## Golden rules

1. Preserve the existing Android Bluetooth HID registration, descriptor, keyboard reports, mouse reports, pairing model, lifecycle and app identity.
2. The computer remains responsible for starting normal Bluetooth pairing. The phone does not replace the proven flow with phone-side discovery.
3. AI and offline command browsing never manage the Bluetooth lifecycle.
4. Commands are shown and reviewed before any typing action.
5. `TYPE ONLY` never appends Enter. The user checks the computer and presses Enter manually.
6. A host change, Bluetooth interruption, app backgrounding or consumed approval invalidates the one-use typing approval.
7. Automated checks do not replace physical Android/computer Bluetooth testing.

## UI09 scope

### Guided AI planner

- The visual progression is `Plan → Step → Preview → Result`.
- A generated plan has an explicit **Start with step 1** action.
- The user moves through one step at a time on a phone-sized screen.
- The exact command and Bluetooth destination are shown again before `TYPE ONLY`.
- Photo feedback remains an explicit reviewed action.

### Offline Linux library

- 212 checked-in Linux command references.
- 13 categories and 63 topics.
- Tree flow: `Category → Topic → Command → Detail`.
- Ranked local search covers titles, command text, explanations, topics, tags and helpful synonyms.
- Each detail page shows what the command does, the expected result, risk, administrator requirement and compatibility.
- The library performs no API request and does not need an account or API key.
- Browsing and copying work without a Bluetooth connection; typing requires an existing connected HID host.

## Automated release gates

The UI09 workflow must pass all of these before publishing an APK candidate:

- frozen Bluetooth/HID policy checks;
- mobile UI contract checks;
- offline catalogue integrity and safety checks;
- planner and photo contract regression tests;
- Android unit tests for both variants;
- Android lint for both variants;
- AI and offline APK assembly;
- APK identity, permission and signature inspection;
- CodeQL security-and-quality analysis;
- SHA-256 generation and exact-source provenance attestation.

## Required physical test

The release remains a prerelease until a person tests the APK on the intended Android phone and a real computer:

1. Install over the previous app where appropriate and confirm the original app identity is retained.
2. Confirm the normal computer-initiated Bluetooth pairing flow.
3. Confirm keyboard typing, mouse movement, left click and right click.
4. Turn Bluetooth off and on and confirm safe recovery.
5. Open the AI planner and complete Plan, Step, Preview and Result navigation.
6. Confirm `TYPE ONLY` sends the reviewed text but not Enter.
7. Disconnect or change the host before approval and confirm typing is blocked or stopped.
8. Put the app in the background during approval and confirm the approval is invalidated.
9. Open the offline library with networking disabled, browse the tree and search common tasks.
10. Confirm copying does not type, and typing still requires a separate final approval.

Record the Android model/version, computer operating system, test results and any visible partial text after an interrupted send.
