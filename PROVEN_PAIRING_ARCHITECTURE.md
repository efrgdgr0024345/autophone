# PROVEN IMPLEMENTATION FINDING — READ FIRST

## This is the primary architecture for Black Cat

Black Cat's required first-pairing workflow is **computer-initiated, like a normal Bluetooth keyboard/mouse**.

Do NOT make phone-side scanning/selecting the computer the primary workflow.

## Source-level evidence

### GhostBoard
Upstream: https://github.com/ToxicOrca/ghostboard-android-bluetooth-mouse-keyboard

GhostBoard's actual source implements the required pattern:

1. acquire Android `BluetoothProfile.HID_DEVICE`;
2. register a composite keyboard + mouse using `BluetoothHidDevice.registerApp()`;
3. SDP uses `BluetoothHidDevice.SUBCLASS1_COMBO`;
4. wait for `onAppStatusChanged(... registered=true)`;
5. request Classic Bluetooth discoverability with `BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE`;
6. user opens Bluetooth Add Device **on the PC**;
7. PC selects/pairs with the Android phone;
8. PC may initiate the HID connection to the already-registered HID service;
9. `onConnectionStateChanged(... STATE_CONNECTED)` confirms success;
10. keyboard and mouse reports are then sent over the standard HID connection.

GhostBoard explicitly handles the case where Android-side `hid.connect(device)` does not succeed by waiting for the PC to connect from its side instead of repeatedly forcing connection.

### MobileDeck
Upstream: https://github.com/remerer/MobileDeck

Its Android manifest independently confirms the pairing direction requires `BLUETOOTH_CONNECT` + `BLUETOOTH_ADVERTISE`. Its pairing model registers the HID keyboard and then makes the Android device discoverable for host-side pairing.

## Black Cat implementation rule

Primary path:

```
Open Black Cat
  -> register keyboard/mouse HID FIRST
  -> HID_REGISTERED
  -> PAIR AS KEYBOARD/MOUSE
  -> Android supported discoverability prompt
  -> computer: Bluetooth / Add Device
  -> computer selects phone
  -> normal Bluetooth bond
  -> host/HID connection callback
  -> READY
```

The phone must not require the user to find/select the computer for normal first pairing.

## Components to retain

From GhostBoard:
- host-initiated pairing lifecycle;
- `SUBCLASS1_COMBO`;
- register HID before discoverability;
- `ACTION_REQUEST_DISCOVERABLE`;
- allow host to initiate HID connection.

From Linkpad:
- mature keyboard report handling;
- six-key rollover;
- mouse coalescing / ~125 Hz;
- input-state safety;
- useful HID callback handling.

From Black Cat:
- minimal UI;
- strict permission checks;
- structured diagnostics;
- no Internet/telemetry;
- no typed-content logging;
- CI/Lint/CodeQL.

## Required permissions

Modern Android primary path:
- `BLUETOOTH_CONNECT`
- `BLUETOOTH_ADVERTISE`
- foreground connected-device service permissions where required.

`BLUETOOTH_SCAN` is not required for the primary normal-keyboard pairing flow and should only exist if a separately labelled fallback genuinely needs it.

## Non-negotiable review question

Every Bluetooth change must answer:

**Does this preserve or improve computer-initiated pairing like a normal physical Bluetooth keyboard/mouse?**

If not, do not make it part of the primary workflow.
