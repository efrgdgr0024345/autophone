# BLACK CAT V2 — PRIMARY GOAL

The computer must discover and pair with the Android phone in the normal Bluetooth Add Device workflow. The phone must provide a standard Bluetooth HID keyboard + mouse. Phone-side computer scanning is not part of the primary architecture.

Required first-pair flow:

1. Black Cat opens.
2. Register Android Bluetooth HID_DEVICE profile and composite keyboard/mouse descriptor.
3. Wait for HID registration callback.
4. User taps PAIR AS KEYBOARD/MOUSE.
5. Request Android Classic Bluetooth discoverability.
6. On computer: Bluetooth -> Add Device -> select the phone -> Pair.
7. Host connects to the already-registered HID service.
8. Black Cat reaches READY.
9. Standard keyboard and mouse reports work without computer companion software.

Every code change must preserve this direction.

Evidence:
- GhostBoard: https://github.com/ToxicOrca/ghostboard-android-bluetooth-mouse-keyboard
- MobileDeck: https://github.com/remerer/MobileDeck
- Earlier Black Cat physical test proved this handset can register BluetoothHidDevice and send working keyboard/mouse input.

Security:
- no INTERNET
- no telemetry
- no Accessibility Service
- no contacts/call logs
- no camera/microphone/files
- never log typed content

Attribution:
Any copied/adapted upstream code retains the required upstream licence/copyright attribution.
