## Scope
<!-- What changes, and what deliberately does not. -->

## Preserved behaviour
- [ ] MainActivity Bluetooth lifecycle unchanged
- [ ] HidManager / HidDescriptor / HidReports / Diagnostics unchanged
- [ ] normal manual keyboard/mouse independent of AI
- [ ] existing OpenAI/key/transport code unchanged unless this PR explicitly scopes it
- [ ] no automatic Enter, replay, run-all or hidden execution

## Evidence
- Base/reference commit:
- Candidate commit:
- Actions run:
- Candidate APK SHA-256:
- GitHub artifact attestation:
- CodeQL / tests / Lint:

## Live API
- [ ] not tested
- [ ] tested with user-held key
Notes:

## Physical device acceptance
- [ ] not started
- [ ] computer-initiated pair/discovery
- [ ] ordinary keyboard
- [ ] mouse
- [ ] AI open/close then ordinary input
- [ ] real API request
- [ ] one exact TYPE ONLY action
- [ ] cancel/disconnect/reconnect regression
- [ ] ordinary input still works afterward

## Promotion
- [ ] PR remains draft until physical evidence is recorded
- [ ] exact tested artifact retained
- [ ] older APK/release not overwritten
- [ ] user explicitly approved promotion
