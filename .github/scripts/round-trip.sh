#!/usr/bin/env bash
# Runs SmsRoundTripTest on the emulator and delivers real SMS to it when the test says it's ready.
set -u
out=check-output
mkdir -p "$out"
pkg=com.saraltechies.saralsmssync.debug

# A new emulator can report that it has booted before its package service is ready.
for _ in $(seq 1 60); do
  adb shell pm path android 2>/dev/null | grep -q package: && break
  sleep 5
done
installed=0
for _ in 1 2 3; do
  if ./gradlew installDebug installDebugAndroidTest --stacktrace; then installed=1; break; fi
  sleep 30
done
[ "$installed" = 1 ] || exit 1

adb shell pm grant "$pkg" android.permission.RECEIVE_SMS
adb shell pm grant "$pkg" android.permission.POST_NOTIFICATIONS 2>/dev/null
adb shell getprop ro.build.version.sdk > "$out/sdk.txt"
adb logcat -c

adb shell am instrument -w \
  -e class com.saraltechies.saralsmssync.SmsRoundTripTest \
  "$pkg.test/androidx.test.runner.AndroidJUnitRunner" > "$out/instrument.txt" 2>&1 &
test_pid=$!

ready=0
for _ in $(seq 1 90); do
  if adb logcat -d -s SaralRoundTrip:I | grep -q READY; then ready=1; break; fi
  sleep 2
done
if [ "$ready" = 1 ]; then
  adb emu sms send 5551234 "Your verification code is 482913. Do not share it."
  sleep 3
  # An automatic login SMS ends in the 11-character hash of the app it's meant for.
  hash=$(adb logcat -d -s SaralRoundTrip:I | sed -n 's/.*HASH=//p' | tail -1 | tr -d '')
  echo "Retriever hash: $hash"
  adb emu sms send 5551234 "<#> Your code is 771204 $hash"
  sleep 3
  adb emu sms send 5551234 "Your code is 650388. @example.com #650388"
else
  echo "The test never became ready" >> "$out/instrument.txt"
fi

wait "$test_pid"
adb logcat -d -s SaralRoundTrip:I > "$out/round-trip.txt"
adb logcat -d > "$out/logcat.txt"
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
adb exec-out screencap -p > "$out/screen.png" 2>/dev/null

cat "$out/instrument.txt" "$out/round-trip.txt"
grep -q "OK (1 test)" "$out/instrument.txt"
