# Third-Party Attribution

## Linkpad

Black Cat Remote's Bluetooth host discovery, bonding, and HID connection lifecycle is informed by and may incorporate adapted portions of **Linkpad**, created by **Devdas Kumar / Devdas-gupta**.

Original project: https://github.com/Devdas-gupta/linkpad

Linkpad is licensed under the **MIT License**.

Copyright and license notices from Linkpad must be preserved for any source copied or substantially adapted from that project.

Black Cat Remote deliberately does not import Linkpad's unrelated feature set. The design target is limited to a Bluetooth HID keyboard and mouse plus diagnostics.

### Linkpad concepts retained/referenced

- Android `BluetoothHidDevice` HID-device architecture
- Classic Bluetooth host discovery
- host-initiated-from-phone bonding via `BluetoothDevice.createBond()`
- waiting for `ACTION_BOND_STATE_CHANGED / BOND_BONDED` before HID connect
- foreground-service ownership of the HID connection lifecycle
- connection timeout/reconnect patterns where adopted
- standards-based keyboard/mouse HID reporting patterns where adopted

### Linkpad features intentionally excluded

- air mouse / gyroscope
- media remote / consumer controls
- TV remote
- Quick Settings tile
- boot receiver / auto-start
- multi-host OS profiles
- themed multi-screen UI
- Hilt dependency injection
- onboarding
- vibration and unrelated convenience features

See the upstream Linkpad LICENSE for the complete MIT license text.
