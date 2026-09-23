# Copilot review instructions — Black Cat Remote

This repository is a security-conscious Android Bluetooth HID keyboard/mouse. Prioritize correctness and security over style.

Check especially: BluetoothHidDevice callback-driven state; Activity/background/foreground-service lifecycle; HID registration/connection races; composite keyboard/mouse descriptors and report lengths/IDs; guaranteed key/button release; API-level permissions; accidental INTERNET permission/networking; analytics/telemetry; logging or persistence of typed text/key identities; exported components; false CONNECTED/READY states; unbounded diagnostic memory; sensitive diagnostic leakage.

Invariants: no Internet permission; no telemetry; no Accessibility Service; never log typed content; minimum permissions; service lifetime not owned solely by Activity; asynchronous calls require callback/state confirmation; CI does not replace physical Bluetooth testing.

Flag violations explicitly and suggest the smallest safe correction.
