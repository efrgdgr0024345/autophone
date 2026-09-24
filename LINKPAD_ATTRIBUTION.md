# Linkpad-derived keyboard/mouse base

This development branch deliberately imports only the portions of **Linkpad** needed to build a Bluetooth HID keyboard and mouse.

## Upstream credit

**Linkpad**  
Author: **Devdas Kumar / Devdas-gupta**  
Original repository: https://github.com/Devdas-gupta/linkpad  
Upstream source commit used: `e08cc8daa8db87dba74f8e823f7dd84443ca6788`  
License: **MIT**

The complete upstream MIT license is preserved in `LINKPAD_LICENSE`.

The files under `upstream_linkpad/` are copied from the upstream project at the commit above as reference/source material for the derivative. Copyright remains with the upstream author as stated in the license.

## Imported core

- BluetoothManager.kt — Classic/BLE discovery and paired-device handling
- ConnectionState.kt — connection state model
- HidDescriptors.kt — upstream HID keyboard/mouse descriptor
- HidReportSender.kt — keyboard/mouse HID report implementation
- HidService.kt — HID registration, bond/connect lifecycle and reconnect handling
- HidServiceController.kt — service control/binding
- HidDescriptorTests.kt — upstream descriptor tests

## Deliberately not imported

Air Mouse/gyroscope, TV Remote, Media Remote, Quick Settings tile, boot receiver, host-profile/settings repositories, themes, onboarding, and the large multi-screen UI.

## Development approach

The imported files are kept in `upstream_linkpad/` first so there is a clear auditable copy of the upstream core. The Black Cat minimal app should then be built from/adapt these files while preserving required MIT attribution.

Product scope: **full keyboard + mouse only**, plus privacy-safe diagnostics and the minimum connection UI needed to operate them.
