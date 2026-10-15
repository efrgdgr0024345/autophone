#!/usr/bin/env bash
set -euo pipefail
task_serial=${ANDROID_SERIAL:-emulator-5554}
task_output=${1:-app/build/reports/ui12-rendered}
task_scenario=${2:-all}
case "$task_scenario" in all|auto) ;; *) exit 2 ;; esac
mkdir -p "$task_output"
test "$(adb -s "$task_serial" shell getprop ro.kernel.qemu | tr -d '\r')" = 1
adb -s "$task_serial" install -r -g app/build/outputs/apk/ai/debug/app-ai-debug.apk
adb -s "$task_serial" install -r -g app/build/outputs/apk/androidTest/ai/debug/app-ai-debug-androidTest.apk
# The fixture checks the failure path and intercepts the user-triggered enable prompt;
# it never pairs or sends HID input. Change only this disposable emulator's adapter.
adb -s "$task_serial" shell svc bluetooth disable
task_status=0
adb -s "$task_serial" shell am instrument -e scenario "$task_scenario" -w com.blackcat.remote.test/com.blackcat.remote.FeedbackSmokeInstrumentation > "$task_output/instrumentation.log" 2>&1 || task_status=$?
cat "$task_output/instrumentation.log"
# Preserve actual partial screenshots even when an assertion fails. These files contain
# synthetic fixtures only; root is restricted to the disposable emulator checked above.
adb -s "$task_serial" root
adb -s "$task_serial" wait-for-device
task_pull_status=0
adb -s "$task_serial" pull /data/user/0/com.blackcat.remote/files/ui12-rendered/. "$task_output/" || task_pull_status=$?
if test "$task_status" != 0 || test "$task_pull_status" != 0 || ! grep -q 'UI12_SMOKE_PASS' "$task_output/instrumentation.log" || grep -q 'UI12_SMOKE_FAIL' "$task_output/instrumentation.log"; then
  exit 1
fi
