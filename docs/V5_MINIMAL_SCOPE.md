# V5 Scope — Minimal Full Keyboard + Mouse

V5 has one product purpose: make a compatible Android phone operate as a standard Bluetooth HID **full keyboard and mouse**.

## Keep

- Linkpad-informed Classic Bluetooth scan -> select host -> createBond -> BOND_BONDED -> HID connect lifecycle.
- Foreground HID service.
- Composite keyboard + mouse HID.
- Full practical keyboard controls: printable US-layout keys, modifiers (Ctrl/Shift/Alt/GUI), Enter, Escape, Tab, Backspace, Delete, arrows, Home/End, Page Up/Down, Insert, F1-F12.
- Mouse: relative movement, left/right/middle click, vertical wheel; horizontal pan only if retained descriptor compatibility requires it.
- Simple text-entry/send mode plus explicit special/modifier keys where required.
- Compact/expandable privacy-safe diagnostics.
- Connection/reconnection safety and RELEASE_ALL_INPUT.
- No PC companion software.

## Remove / do not add

- Air mouse and all sensor/gyroscope code.
- Media/consumer remote controls.
- TV remote.
- Power/input/channel/color-key remote functions.
- Quick Settings tile.
- Boot receiver/autostart.
- Vibration.
- Wake lock unless physical evidence later proves it essential.
- Multi-host OS profiles in V5.
- Themes and elaborate navigation.
- Analytics, telemetry, Internet access, Accessibility Service.
- Contacts, microphone, camera, files/photos.
- Feature dependencies unrelated to keyboard/mouse.

## Attribution rule

Linkpad by Devdas Kumar / Devdas-gupta must be credited in source documentation and any copied/adapted source must retain the MIT-required copyright/license notice. Never present upstream work as original Black Cat Remote work.

Original: https://github.com/Devdas-gupta/linkpad
