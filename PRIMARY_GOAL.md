# PRIMARY PRODUCT GOAL — Normal Bluetooth Keyboard/Mouse Pairing

**This file is a governing requirement for every future coding change.**

## User-visible goal

Black Cat must behave as closely as Android permits to a normal physical Bluetooth keyboard + mouse:

1. Open Black Cat on the Android phone.
2. Black Cat registers its composite Bluetooth HID keyboard/mouse service **before pairing**.
3. User puts Black Cat into pairing mode / makes the phone discoverable.
4. On the laptop/computer, open the normal Bluetooth **Add device** screen.
5. The computer discovers the phone.
6. User selects the phone **on the computer** and accepts normal Bluetooth pairing.
7. Black Cat detects the completed bond and establishes/accepts the HID connection.
8. The computer receives standard keyboard and mouse input with no companion software.

The phone scanning for/selecting the computer is NOT the primary first-pair workflow. It may remain only as a clearly secondary compatibility/reconnect fallback if genuinely useful.

The Bluetooth display name is secondary. The critical requirement is **host-initiated discovery/pairing and standard HID keyboard/mouse operation**.

## Evidence / implementation direction

This is not speculative. Multiple Android BluetoothHidDevice projects document the desired host-initiated flow:

- GhostBoard (MIT): open app -> Make Discoverable -> PC Add Bluetooth device -> select phone -> pair -> keyboard/mouse.
  https://github.com/ToxicOrca/ghostboard-android-bluetooth-mouse-keyboard
- MobileDeck: Register HID -> Make discoverable -> Windows pairs with the advertised Android device -> keyboard HID.
  https://github.com/remerer/MobileDeck
- Relay HID: Android registers as Bluetooth HID peripheral and host pairs from its Bluetooth Settings.
  https://github.com/Abdk4Moura/relay-hid
- Cordless: host opens Add Bluetooth device and picks the phone; Android native HID role provides keyboard/mouse.
  https://github.com/vdb86/Cordless

Our own earlier V3 physical test also proved that this handset can register BluetoothHidDevice and deliver working keyboard + mouse reports to Ubuntu after host-side pairing.

## Required Android architecture

- Register BluetoothHidDevice and SDP descriptor first.
- Request BLUETOOTH_ADVERTISE in addition to BLUETOOTH_CONNECT.
- Enter Classic discoverable mode only from an explicit user Pair action via Android's supported ACTION_REQUEST_DISCOVERABLE UI.
- Listen for ACTION_BOND_STATE_CHANGED.
- On BOND_BONDED, attempt/confirm HID connection using the already-registered HID profile.
- Preserve foreground-service ownership while connected.
- Preserve RELEASE_ALL_INPUT / neutral input state on errors/disconnect.
- Do not require contacts, call logs, Internet, Accessibility, camera, microphone, files or unrelated permissions.

## UI implication

Primary connection control should become:

**PAIR AS KEYBOARD/MOUSE**

with status showing:
- Bluetooth Off
- HID Registering
- Ready to Pair
- Discoverable / Waiting for computer
- Pairing
- HID Connecting
- READY

Computer-side instructions should say:
**On the computer: Bluetooth -> Add device -> select this phone -> Pair.**

Phone-side FIND COMPUTER must not be presented as the normal pairing method.

## Change-review checklist

Before accepting any Bluetooth/pairing change, ask:
1. Does this move us toward host-initiated pairing like a normal keyboard?
2. Is HID registered before discoverability/pairing?
3. Does it avoid requiring the user to select the computer on the phone?
4. Does it preserve standard keyboard + mouse HID?
5. Does it avoid unrelated permissions/access?
6. Does diagnostics explicitly report every success/failure stage?

If a proposed change conflicts with this goal, do not implement it unless it is clearly isolated as an optional fallback and documented as such.
