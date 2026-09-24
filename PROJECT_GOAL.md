# Black Cat Remote — fixed project goal

The governing requirement for every design, code and test decision is:

> **An Android phone behaves like a normal Bluetooth HID keyboard/mouse to a computer.**

Required user flow:

1. Android registers the Bluetooth HID device profile.
2. User taps **PAIR FROM COMPUTER**.
3. Android becomes discoverable for normal Bluetooth pairing.
4. Ubuntu/Windows/macOS discovers the phone from its standard Bluetooth UI.
5. The computer pairs with it without companion software.
6. The HID connection reaches READY.
7. Keyboard/mouse reports from the phone are received as ordinary HID input.

## Non-goals

- Do not make phone-side scanning for computers the primary pairing architecture.
- Do not require PC companion software.
- Do not use Wi-Fi/network transport.
- Do not add Accessibility, Internet, contacts, files, camera or microphone permissions.
- Do not replace the Linkpad-derived HID core without a specific physical-test failure that requires a core change.

## Development gate

Before accepting a change, ask: **Does this directly help the Android phone behave like a standard Bluetooth HID keyboard/mouse?**

If not, it does not belong in the core project.

## Current architecture

The HID implementation remains derived from Linkpad by Devdas Kumar / Devdas-gupta under the MIT licence. See LINKPAD_ATTRIBUTION.md and LINKPAD_LICENSE.

Physical testing remains authoritative for Bluetooth interoperability.