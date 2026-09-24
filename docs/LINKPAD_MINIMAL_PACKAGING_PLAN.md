# Black Cat Minimal — Packaging Plan

## Objective

Package a minimal Android application whose Bluetooth keyboard/mouse implementation is derived from the proven **Linkpad** core, while retaining the simple Black Cat controller UI.

This is a fresh implementation path. Do not use the previous Black Cat Bluetooth/HID implementation as the functional base.

Upstream:
- Project: Linkpad
- Author: Devdas Kumar / Devdas-gupta
- Repository: https://github.com/Devdas-gupta/linkpad
- Pinned source: e08cc8daa8db87dba74f8e823f7dd84443ca6788
- License: MIT
- Full license preserved in LINKPAD_LICENSE
- Attribution: LINKPAD_ATTRIBUTION.md

## Non-negotiable architecture rule

The Bluetooth/HID path must remain recognisably Linkpad's proven architecture:

Classic host scan -> user selects computer -> createBond() -> wait for BOND_BONDED -> BluetoothHidDevice.connect() -> callback confirms CONNECTED -> keyboard/mouse reports.

Do not replace this lifecycle with the previous Black Cat discoverable-phone approach.

## Product scope

One app, one main controller screen.

Keep:
- full Bluetooth HID keyboard;
- relative mouse/touchpad;
- left/right/middle click;
- vertical scroll;
- host discovery/pairing;
- reconnect handling required for reliability;
- compact expandable diagnostics.

Remove/do not introduce:
- Air Mouse/gyroscope;
- media/consumer remote;
- TV remote;
- Quick Settings tile;
- boot auto-start;
- themes/navigation framework beyond what the one-screen app needs;
- multi-host OS profiles for initial release;
- analytics/telemetry;
- Internet access;
- Accessibility Service.

## UI — preserve Black Cat interaction model

Top to bottom:

1. Black Cat Remote title.
2. Compact live diagnostics box.
   - tap to expand to maximum useful screen area;
   - scrolling;
   - COPY;
   - CLEAR;
   - BACK;
   - success and failure events;
   - never log typed text/key identity.
3. Connection status.
4. FIND COMPUTER.
5. Simple discovered/paired host selector.
6. CONNECT / DISCONNECT only where necessary.
7. Text entry box.
8. SEND TEXT.
9. Large touchpad.
10. LEFT CLICK / RIGHT CLICK.
11. Full-keyboard control access.

The connection workflow should be simplified after the Linkpad lifecycle is working. Initial reliability is more important than removing diagnostic controls prematurely.

## Full keyboard definition

Support the standard practical keyboard controls exposed by Linkpad's HID implementation:
- A-Z / a-z;
- 0-9;
- normal US punctuation/symbol mapping;
- Space;
- Enter;
- Escape;
- Tab;
- Backspace;
- Delete;
- Ctrl;
- Shift;
- Alt;
- GUI/Windows/Command;
- arrow keys;
- Insert;
- Home;
- End;
- Page Up;
- Page Down;
- F1-F12.

Use Linkpad's HID descriptor/report implementation as the source of truth where practical. Do not casually alter the descriptor because hosts cache it at bond time.

The text box remains a convenience for typing normal text. Special/modifier keys require a compact full-keyboard control surface, preferably an expandable keyboard panel so the touchpad remains large.

## Mouse definition

Use Linkpad's proven mouse report format.

Required:
- relative X/Y;
- left button;
- right button;
- middle button;
- vertical wheel;
- safe movement bounds/chunking;
- guaranteed button release after cancellation/error.

Do not import Air Mouse/sensor code.

## Source integration

### Phase 1 — establish clean Linkpad-derived core

Promote/adapt these imported upstream files into the actual app package:
- BluetoothManager.kt
- ConnectionState.kt
- HidDescriptors.kt
- HidReportSender.kt
- HidService.kt
- HidServiceController.kt

Retain the upstream descriptor tests.

Where Linkpad classes depend on features we removed (preferences, profiles, notification strings, Hilt), simplify those dependencies rather than reintroducing the removed feature system.

Every copied/substantially adapted source file must carry an appropriate attribution header referring to Linkpad and its MIT licence.

### Phase 2 — remove Linkpad dependencies we do not need

HidService currently references some Linkpad infrastructure such as PreferencesRepository/Hilt/reconnect preferences. Refactor narrowly:
- remove Hilt injection;
- instantiate the report sender directly or through a tiny local owner;
- replace profile/preferences persistence with the minimum state needed;
- retain useful connection timeout and disconnect handling;
- retain callback-driven state;
- retain createBond -> BOND_BONDED -> HID connect ordering.

Do not rewrite working Bluetooth logic merely to make it stylistically consistent.

### Phase 3 — Black Cat UI adapter

The UI communicates with the Linkpad-derived service/controller through a small interface:
- startScan()
- stopScan()
- discoveredHosts
- connect(host)
- disconnect()
- connectionState
- sendKeyboardReport/action
- sendMouseMove()
- sendMouseButton()
- sendWheel()

The Activity must not own the HID lifetime.

### Phase 4 — diagnostics adapter

Add Black Cat structured diagnostics around the proven lifecycle without changing its semantics.

Log:
- app/service lifecycle;
- permissions;
- adapter state;
- scan start/stop/failure;
- discovered host name and bond state (avoid unnecessary full identifiers in screenshots);
- selected host;
- createBond accepted/rejected;
- every bond-state transition;
- HID proxy acquisition;
- registerApp request + callback;
- connect request + connection callback;
- timeout;
- ACL disconnect;
- reconnect attempt/result;
- report accepted/rejected counts;
- RELEASE_ALL_INPUT;
- caught exceptions;
- state transitions.

Explicit labels:
PASS / FAIL / WARN / INFO.

Never log:
- text-field content;
- passwords;
- individual typed characters/key identities;
- clipboard;
- contacts;
- unrelated personal information.

Use a bounded in-memory ring buffer.

## Permissions

Start from Linkpad's working requirements, then reduce.

Expected:
- BLUETOOTH_CONNECT
- BLUETOOTH_SCAN
- legacy BLUETOOTH / BLUETOOTH_ADMIN only where required for <= Android 11
- FOREGROUND_SERVICE
- FOREGROUND_SERVICE_CONNECTED_DEVICE

Do not include BLUETOOTH_ADVERTISE unless testing proves it is needed. Linkpad's selected pairing architecture does not rely on making the phone discoverable.

Do not include:
- INTERNET;
- contacts;
- call logs;
- camera;
- microphone;
- files/photos;
- Accessibility;
- location on modern Android;
- boot completed;
- vibration;
- wake lock unless later physical evidence proves one is essential.

## Security/safety invariants

- No network capability.
- No telemetry.
- No typed-content logging/persistence.
- No stale text queue after disconnect.
- Neutral keyboard/mouse state after error/disconnect.
- Central RELEASE_ALL_INPUT.
- Connection state comes from callbacks, not optimistic API return values.
- A host change cancels pending input.
- Unsupported text characters fail safely rather than generate guessed keys.
- Diagnostics remain bounded.
- Android components exported only where required.

## Build/tooling

Use a clean Kotlin Android project compatible with Linkpad's source level:
- minSdk 28;
- current supported target/compile SDK;
- JDK 17;
- pinned Gradle/Android Gradle Plugin versions;
- minimal dependencies.

Prefer keeping Kotlin because Linkpad's proven implementation is Kotlin. Do not translate its Bluetooth core to Java merely to match the old Black Cat app.

## Automated verification

Before physical installation:
1. clean build;
2. unit tests;
3. upstream HidDescriptorTests;
4. new keyboard mapping/report tests;
5. mouse report/bounds/release tests;
6. state-machine/bond lifecycle tests where practical;
7. Android Lint;
8. CodeQL;
9. manifest inspection;
10. assert no INTERNET;
11. assert no Accessibility Service;
12. dependency inspection;
13. static sensitive-logging review;
14. APK SHA-256 tied to exact commit.

Do not baseline/suppress new Lint errors just to obtain green CI.

## Git workflow

Development branch: blackcat-linkpad-minimal.

Before merge:
- focused commits;
- PR into main only when the app packages/builds;
- Android CI green;
- CodeQL green;
- review warnings;
- preserve Linkpad attribution/license.

Use a new APK filename, e.g.:
BlackCat-Linkpad-Minimal-v1.apk

Do not overwrite the existing V3/V4 test APK filenames.

## Physical acceptance test

Fresh test should deliberately forget/remove the old Bluetooth bond first because HID descriptors/services may be cached.

Test sequence:
1. install new APK;
2. launch;
3. grant only Nearby Devices Bluetooth permissions;
4. confirm HID registration;
5. FIND COMPUTER;
6. confirm Ubuntu/Windows host appears in app;
7. select host;
8. accept pairing on host;
9. confirm bond callback;
10. confirm HID CONNECTED;
11. confirm READY;
12. type basic text;
13. test Shift/punctuation;
14. test Ctrl/Alt/GUI and special keys;
15. test arrows/F-keys;
16. move mouse;
17. left/right/middle click;
18. wheel;
19. background Activity and return;
20. disconnect/reconnect;
21. Bluetooth off/on;
22. host reboot/reconnect.

Capture expanded diagnostics at the first failure rather than changing random settings.

## Definition of success

The laptop should treat the connection as a standard Bluetooth HID keyboard/mouse through the Linkpad-derived lifecycle. No PC companion software is allowed.

The app should remain deliberately much smaller than Linkpad while preserving the part of Linkpad that already works.

## Attribution release requirement

Every source distribution/repository retains:
- LINKPAD_LICENSE;
- LINKPAD_ATTRIBUTION.md;
- original author/copyright notices required by MIT.

App About/credits text should state:
"Bluetooth HID implementation derived from Linkpad by Devdas Kumar (MIT License)" and link to the original project.
