# WORKING BASELINE — V3 Physical HID Success

## Status

**KNOWN WORKING PHYSICAL BASELINE — DO NOT OVERWRITE OR REFACTOR WITHOUT PRESERVING THIS BRANCH.**

Branch: `working-v3-physical-hid-success`

Physical test result reported 2026-09-24:

- Android handset: ZTE Z2577 / Android 16 / API 36.
- Linux host: Ubuntu.
- Android `BluetoothHidDevice` profile proxy acquired successfully.
- HID application registration callback returned `registered=true`.
- Ubuntu bonded with the phone.
- HID connection reached `CONNECTED` and application state `READY`.
- **Keyboard text input works on the physical Ubuntu host.**
- **Relative mouse movement works on the physical Ubuntu host.**
- Existing composite HID descriptor is therefore a known-working descriptor for this phone/host combination.

## Preservation rule

Treat the source and descriptor on this branch as the rollback/reference implementation.

Future work should happen on new branches. Do not alter this branch merely to improve UI, pairing presentation, reconnect behaviour, naming, or diagnostics.

If a later build stops sending keyboard or mouse input, compare it against this branch first.

## Known non-blocking observations

- Ubuntu presents the Bluetooth device primarily using the handset identity (observed as Optus X-Total 2 / Type Phone), rather than presenting initial discovery exactly like a dedicated commercial keyboard.
- The app's HID profile nevertheless connected successfully and delivered real keyboard and mouse input.
- Later reconnect attempts were observed spending time in CONNECTING; reconnect behaviour remains an area for separate testing/hardening.
- The phone-level Bluetooth pairing UI may offer optional contacts/call-history sharing. This is not required by Black Cat Remote and should remain unchecked.

## Security properties of this baseline

- No INTERNET permission.
- No Accessibility Service.
- No analytics/telemetry.
- Typed content is not written to diagnostics.
- HID lifecycle is held by the connected-device foreground service.
- Diagnostic state is bounded/in-memory.
- Input-release safety path is present.

## Development rule from this point

**Working HID behaviour has priority over cosmetic or workflow improvements.**

Before changing Bluetooth/HID code:
1. branch from a known-good point;
2. preserve the descriptor/report format unless evidence requires changing it;
3. run Android CI and CodeQL;
4. retain this branch as rollback;
5. physically retest keyboard and mouse after changes.
