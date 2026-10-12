#!/usr/bin/env bash
set -euo pipefail
task_serial=${ANDROID_SERIAL:-emulator-5554}
task_output=${1:-app/build/reports/ui10-rendered}
mkdir -p "$task_output"
test "$(adb -s "$task_serial" shell getprop ro.kernel.qemu | tr -d '\r')" = 1
adb -s "$task_serial" install -r -g app/build/outputs/apk/ai/debug/app-ai-debug.apk
adb -s "$task_serial" install -r -g app/build/outputs/apk/androidTest/ai/debug/app-ai-debug-androidTest.apk
adb -s "$task_serial" shell am instrument -w com.blackcat.remote.test/com.blackcat.remote.FeedbackSmokeInstrumentation > "$task_output/instrumentation.log" 2>&1
cat "$task_output/instrumentation.log"
if ! grep -q 'UI10_SMOKE_PASS' "$task_output/instrumentation.log" || grep -q 'UI10_SMOKE_FAIL' "$task_output/instrumentation.log"; then
  exit 1
fi
# These files contain fixture screenshots only; root is restricted to our disposable emulator.
adb -s "$task_serial" root
adb -s "$task_serial" wait-for-device
adb -s "$task_serial" pull /data/user/0/com.blackcat.remote/files/ui10-rendered/. "$task_output/"
