# Black Cat Remote — V1 Coding Plan

## Goal

Build a deliberately small Android application that makes a compatible Android 9+ phone present itself to a computer as a standard Bluetooth HID composite **keyboard + relative mouse**.

The host computer must require **no companion application, no custom driver, no Wi-Fi connection and no Accessibility Service**.

Primary priorities, in order:

1. Standard Bluetooth HID compatibility across common Windows, Linux and macOS hosts.
2. Reliable first-run setup and reconnect behaviour.
3. Extremely useful on-screen diagnostics that can be photographed or copied when physical testing fails.
4. Minimum Android permissions and minimum dependencies.
5. No collection, persistence or logging of text typed by the user.

## V1 UI

Keep the existing simple interaction model.

Top to bottom:

1. **Compact live DEBUG window**
2. Connection/device status
3. Text entry field + SEND
4. Large relative trackpad
5. LEFT CLICK and RIGHT CLICK

### Debug window

The debug window is a core V1 feature, not a development-only overlay.

In normal mode it occupies a small area at the top of the main screen and continuously shows the newest diagnostic events.

Tapping it expands it to approximately the maximum usable screen area. The expanded view must:
- show considerably more history;
- use a readable monospace-style presentation;
- scroll vertically;
- preserve timestamps and severity;
- provide COPY DIAGNOSTICS;
- provide CLEAR;
- provide a simple way back to the normal controller;
- remain useful when photographed with another device.

Do not require ADB for normal fault reporting.

### What diagnostics should capture

Capture as much useful state as possible without recording user content:

- app version/build;
- Android version/API;
- device/manufacturer/model where Android exposes it;
- Bluetooth adapter presence/state;
- required permission state;
- HID profile-proxy request/result;
- HID service/profile callbacks;
- HID registration request and callback result;
- foreground-service lifecycle;
- Activity lifecycle where useful;
- paired/bonded host state;
- selected host name/address where appropriate for diagnostics;
- connection attempts;
- connection-state callbacks;
- disconnect reason/state when available;
- Bluetooth state changes;
- keyboard report attempted/succeeded/failed;
- keyboard release report attempted/succeeded/failed;
- mouse report attempted/succeeded/failed;
- button down/up reports;
- caught exceptions with class/message and useful stack information;
- state-machine transitions;
- timestamps;
- last error and repeated-error count.

**Never log actual characters, text-field contents, passwords, clipboard contents or individual key identities derived from the user's text.**

A safe entry is:

`KEYBOARD report sent; reportLength=8`

An unsafe entry is:

`User sent "hello123"`

Mouse diagnostics may include numeric movement/button metadata because this does not reveal typed content.

Keep a bounded in-memory ring buffer so diagnostics cannot grow without limit. Do not persist input-derived logs. Export/copy only on explicit user action.

## Architecture

### 1. Activity / UI

The Activity owns presentation only.

It must not own the lifetime of the Bluetooth HID connection.

Responsibilities:
- render connection state;
- render debug stream;
- accept text;
- convert touch gestures into high-level mouse commands;
- send commands to the HID/service layer;
- request explicit user actions such as permissions/discoverability;
- expand/collapse diagnostics.

Navigating away from the Activity must not intentionally tear down the HID connection.

### 2. HID foreground service

A foreground service owns the Bluetooth HID lifecycle.

Responsibilities:
- obtain Bluetooth HID profile proxy;
- register the HID application;
- receive and validate callbacks;
- maintain selected-host connection state;
- reconnect where Android permits and policy says it is safe;
- send HID reports;
- expose state to UI;
- emit structured diagnostics;
- unregister/release resources cleanly.

Do not treat a successful asynchronous API call as proof that the requested operation completed. State changes must be driven by the relevant Android callbacks.

### 3. Explicit state machine

Use an explicit state model rather than scattered booleans.

Example states:

`STARTING -> WAITING_PERMISSION -> ACQUIRING_PROFILE -> REGISTERING -> REGISTERED -> WAITING_HOST -> CONNECTING -> CONNECTED -> ERROR/RECOVERING`

Every transition should generate a diagnostic event.

Unexpected callbacks must be logged rather than silently ignored.

### 4. Fixed composite HID descriptor

Freeze the V1 descriptor before broad physical testing.

It should expose:
- standard keyboard;
- standard relative mouse;
- left button;
- right button;
- relative X/Y;
- reserve/support wheel from V1 if compatibility testing confirms the chosen descriptor.

Avoid descriptor changes after pairing because host operating systems may cache HID descriptors.

Use standards-based descriptors and compare them against known-working Android HID implementations before finalizing.

### 5. Keyboard encoder

Implement deterministic character-to-HID mapping.

Requirements:
- key-down and key-up/release handling;
- modifiers such as Shift;
- common printable ASCII first;
- no typed-character logging;
- unit tests for mapping and release behaviour;
- safe handling of unsupported characters rather than sending unintended keys.

V1 can explicitly define its supported text set rather than pretending arbitrary Unicode can be represented by a normal HID keyboard.

### 6. Relative mouse encoder

Trackpad movement sends deltas, not absolute screen coordinates.

Requirements:
- finger movement -> dx/dy;
- clamp/chunk values to descriptor/report limits;
- left-button down/up;
- right-button down/up;
- always release buttons correctly after cancellation/interruption;
- unit tests for positive/negative movement, limits and button release.

## Bluetooth/pairing strategy

Prefer standard Bluetooth Classic HID behaviour through Android's native `BluetoothHidDevice` API.

The PC should see the phone as an ordinary Bluetooth keyboard/mouse.

Avoid a custom PC receiver.

For initial setup, prefer the computer discovering/pairing with the phone rather than making the Android app perform broad device scanning, if this remains reliable across target Android versions.

Display the selected/connected host clearly so input is not accidentally sent to the wrong computer.

## Permissions and privacy

Request only permissions demonstrated to be necessary.

Expected modern Android Bluetooth permissions include CONNECT and ADVERTISE. Add SCAN only if implementation/testing proves it is genuinely required for the selected workflow.

Do not request:
- Internet;
- location unless a legacy Android compatibility requirement is proven and narrowly scoped;
- contacts;
- camera;
- microphone;
- SMS;
- files/photos;
- Accessibility;
- device-admin privileges.

The final manifest must be audited as part of CI/release review.

Do not add analytics, crash-reporting SDKs, advertising SDKs or telemetry.

Disable/avoid backup of sensitive/transient app state.

## Failure-first design

Every major operation must have:
- precondition check;
- attempted-operation diagnostic;
- success callback diagnostic;
- failure/exception diagnostic;
- timeout/watchdog where an asynchronous callback can fail to arrive;
- user-readable current state.

Examples:

`HID_REGISTER request submitted`

followed by either:

`HID_REGISTER callback: registered=true`

or a clearly visible timeout/error.

This prevents the UI from claiming success merely because an API invocation returned.

## First-run flow

1. Launch.
2. Run compatibility/self-check.
3. Check Android/API and Bluetooth hardware.
4. Explain/request only required Nearby Devices permissions.
5. Ensure Bluetooth is enabled through normal Android system flow.
6. Acquire HID profile.
7. Register fixed composite HID descriptor.
8. Confirm registration callback.
9. Offer pairing/discoverability flow.
10. Observe bond/connection callbacks.
11. Display exact connected host.
12. Enable keyboard/mouse controls only when state is genuinely ready.

Each step emits debug entries.

## Self-test

Add a local self-test that can validate software components without a physical PC:

- descriptor sanity/known expected bytes;
- keyboard mapping;
- keyboard press/release generation;
- mouse report generation;
- movement limits/chunking;
- button press/release;
- diagnostic redaction rules;
- state-machine transitions.

The UI diagnostics should distinguish **software self-test passed** from **physical Bluetooth host tested**.

## Background/lifecycle acceptance requirement

Mandatory test:

1. Connect phone to host.
2. Verify keyboard and mouse.
3. Press Home.
4. Open another phone application.
5. Return.
6. Lock/unlock phone where practical.
7. Toggle screen off/on.
8. Confirm whether HID registration and host connection survive.
9. If Android/OEM tears the service down, record the exact callbacks/lifecycle events and recover cleanly.

The desired behaviour is that temporarily leaving the controller UI does not require re-pairing.

## Automated quality gates

Every change should run as many checks as practical before producing a test APK:

- clean Gradle build;
- unit tests;
- Android lint;
- Kotlin/compiler warnings review;
- manifest permission inspection;
- dependency inspection;
- static search for accidental `INTERNET` permission;
- static search for analytics/telemetry dependencies;
- static search/review for logging of text-field contents;
- tests for HID encoders;
- tests for state machine;
- tests for diagnostic ring buffer/redaction;
- APK contents/manifest inspection;
- SHA-256 of release/test APK;
- source commit recorded alongside artifact.

Warnings should not simply be ignored to obtain a green build. Either fix them or document why they are safe.

## Reference implementations

Use existing projects as references, not as blind copy/paste sources.

Priority:
1. Android official `BluetoothHidDevice` API/behaviour.
2. Proven cross-platform Android Bluetooth HID projects.
3. Small implementations useful for descriptor/report logic.
4. Our own implementation where required.

Projects identified during design research include:
- `jqssun/android-bt-remote`
- `Devdas-gupta/linkpad`
- `Abdk4Moura/relay-hid`
- `raghavk92/Kontroller` / related Kontroller implementations

Before borrowing code, verify its licence and preserve required attribution/licence notices.

## Physical host test matrix

Start with:
- Windows host;
- normal Linux host.

Then broaden to:
- macOS if available;
- additional Windows Bluetooth adapters;
- additional Linux Bluetooth stacks;
- additional Android handset/OEM versions.

For each host record:
- pairing result;
- keyboard result;
- mouse result;
- reconnect after app relaunch;
- background/foreground behaviour;
- Bluetooth off/on recovery;
- host reboot reconnect behaviour;
- phone reboot behaviour;
- any need to forget/re-pair.

## Definition of V1 success

V1 is successful when a supported Android phone can:

- install normally;
- request only narrowly necessary permissions;
- pair as a standard Bluetooth HID keyboard/mouse;
- require no PC-side software;
- send supported text reliably;
- move the pointer through a relative touchpad;
- perform left/right clicks;
- clearly identify the connected host;
- avoid network capability and telemetry;
- avoid retaining/logging typed content;
- survive ordinary Activity backgrounding where Android permits;
- recover clearly from Bluetooth/service failures;
- produce sufficiently detailed on-screen diagnostics that a user can photograph or copy them for remote diagnosis.

## Implementation order

1. Preserve/review current repository and establish baseline build.
2. Add structured diagnostic/event subsystem and debug UI first.
3. Add explicit HID/service state machine.
4. Finalize and test composite HID descriptor.
5. Implement/refactor keyboard reports.
6. Implement/refactor relative mouse reports.
7. Implement permission + first-run flow.
8. Implement host selection/pairing/reconnection.
9. Add foreground-service lifecycle handling.
10. Add self-tests and unit tests.
11. Add CI quality/security checks.
12. Build signed/test APK and publish SHA-256 + commit.
13. Physical Windows/Linux test using expanded diagnostics.
14. Fix only from observed evidence; keep descriptor stable unless a standards/compatibility defect requires changing it.
