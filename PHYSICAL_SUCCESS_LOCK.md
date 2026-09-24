# LOCKED PHYSICAL SUCCESS BASELINE — DO NOT MODIFY

This branch is intentionally frozen at commit:

`9771772bbe25dd8eba8d7303f7e4680aa84175df`

## Physical result

Confirmed by the user on real Android + computer hardware:

- Black Cat registers as Android Bluetooth HID.
- User taps PAIR AS KEYBOARD/MOUSE.
- Phone becomes discoverable.
- Computer uses its normal Bluetooth Add Device workflow.
- Computer discovers/selects the phone.
- Host-initiated pairing succeeds.
- Keyboard/mouse HID workflow works.

This is the architecture the project was created to achieve.

## APK

`BlackCat-v2.apk`

SHA-256:

`111e4d4fd35b3ddffddbecee18bf2972f79f95856f9bd395b0ccf75d469d4136`

## Preservation rule

DO NOT commit new development to this branch.
DO NOT force-update this branch.
DO NOT rewrite this baseline.

All future work must branch FROM this commit/baseline. If a future change breaks pairing, compare against this exact commit and APK.

The governing goal remains computer-initiated pairing like a normal Bluetooth HID keyboard/mouse.
