#!/usr/bin/env bash
# Runs inside the emulator job: instrumented tests (live sources -> crop -> set wallpaper),
# then screenshots of the home screen and of the app in light and dark theme.
set -uo pipefail
mkdir -p emulator-out
adb logcat -c || true

status=0
./gradlew --no-daemon connectedDebugAndroidTest || status=$?
cp -r app/build/reports/androidTests/connected emulator-out/test-report 2>/dev/null || true
adb logcat -d -s DnevenTapetTest:I > emulator-out/test-log.txt || true

# Home screen with the wallpaper the test left behind.
adb shell input keyevent KEYCODE_HOME
sleep 4
adb exec-out screencap -p > emulator-out/1-home-screen.png

# The app itself (fresh install shows the welcome screen).
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n bg.dnevnitapet/.ui.MainActivity
sleep 5
adb exec-out screencap -p > emulator-out/2-app-light.png
adb shell cmd uimode night yes
sleep 4
adb exec-out screencap -p > emulator-out/3-app-dark.png
adb shell cmd uimode night no

adb logcat -d > emulator-out/logcat-full.txt || true
exit $status
