#!/usr/bin/env bash
set -euo pipefail
task_serial=${ANDROID_SERIAL:-emulator-5554}
task_output=${1:-app/build/reports/ui12-rendered}
task_scenario=${2:-all}
case "$task_scenario" in all|auto|layout|timeout|learning|workflows|search-stream) ;; *) exit 2 ;; esac
mkdir -p "$task_output"
test "$(adb -s "$task_serial" shell getprop ro.kernel.qemu | tr -d '\r')" = 1
# Automated Test Device images disable drawing by default. Enable real rendering;
# do not replace PixelCopy with a mock/drawing fallback.
adb -s "$task_serial" shell setprop debug.hwui.drawing_enabled 1
adb -s "$task_serial" install -r -g app/build/outputs/apk/ai/debug/app-ai-debug.apk
adb -s "$task_serial" install -r -g app/build/outputs/apk/androidTest/ai/debug/app-ai-debug-androidTest.apk
task_local_hash=$(sha256sum app/build/outputs/apk/ai/debug/app-ai-debug.apk | cut -d ' ' -f 1)
task_apk_path=$(adb -s "$task_serial" shell pm path com.blackcat.remote | tr -d '\r' | sed 's/^package://')
task_device_hash=$(adb -s "$task_serial" shell sha256sum "$task_apk_path" | tr -d '\r' | cut -d ' ' -f 1)
test "$task_local_hash" = "$task_device_hash"
adb -s "$task_serial" shell dumpsys package com.blackcat.remote > "$task_output/installed-package.txt"
echo "$task_local_hash" > "$task_output/installed-apk.sha256"
# The fixture checks the failure path and intercepts the user-triggered enable prompt;
# it never pairs or sends HID input. Change only this disposable emulator's adapter.
# Some API31 ATD images kill the svc process when disabling an already-off adapter.
# Keep the verified off state; only request a transition when it is actually needed.
task_bluetooth_on=$(adb -s "$task_serial" shell settings get global bluetooth_on | tr -d '\r')
if test "$task_bluetooth_on" != 0; then adb -s "$task_serial" shell svc bluetooth disable; fi
test "$(adb -s "$task_serial" shell settings get global bluetooth_on | tr -d '\r')" = 0
task_status=0
adb -s "$task_serial" shell am instrument -e scenario "$task_scenario" -w com.blackcat.remote.test/com.blackcat.remote.FeedbackSmokeInstrumentation > "$task_output/instrumentation.log" 2>&1 || task_status=$?
cat "$task_output/instrumentation.log"
# Preserve actual partial screenshots even when an assertion fails. These files contain
# synthetic fixtures only; root is restricted to the disposable emulator checked above.
adb -s "$task_serial" root
adb -s "$task_serial" wait-for-device
task_pull_status=0
adb -s "$task_serial" pull /data/user/0/com.blackcat.remote/files/ui12-rendered/. "$task_output/" || task_pull_status=$?
task_pass_marker=UI12_SMOKE_PASS
if test "$task_scenario" = layout; then task_pass_marker=UI14_LAYOUT_PASS; fi
if test "$task_scenario" = timeout; then task_pass_marker=UI14_TIMEOUT_PASS; fi
if test "$task_scenario" = learning; then task_pass_marker=UI15_LEARNING_PASS; fi
if test "$task_scenario" = workflows; then task_pass_marker=UI16_WORKFLOWS_PASS; fi
if test "$task_scenario" = search-stream; then task_pass_marker=UI17_SEARCH_STREAM_PASS; fi
if test "$task_status" != 0 || test "$task_pull_status" != 0 || ! grep -q "$task_pass_marker" "$task_output/instrumentation.log" || grep -q 'UI12_SMOKE_FAIL' "$task_output/instrumentation.log"; then
  exit 1
fi
