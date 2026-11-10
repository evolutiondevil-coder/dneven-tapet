#!/usr/bin/env bash
# Runs inside the emulator job:
#  1. instrumented tests: parsers on the device + live sources -> crop -> set wallpaper;
#  2. the real app: welcome screen, "Start", main screen, info, settings (light and dark);
#  3. the Quick Settings tile and the widget's "next" action really change the wallpaper.
# Screenshots and logs go to emulator-out/ (uploaded as a build artifact).
set -uo pipefail
OUT=emulator-out
mkdir -p "$OUT"
adb logcat -c || true

shot() { sleep "${2:-3}"; adb exec-out screencap -p > "$OUT/$1.png"; }

# Taps the centre of the first UI element whose text or content-desc equals $1.
tap() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null 2>&1
  local b
  b=$(grep -oE "(text|content-desc)=\"$1\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" /tmp/ui.xml | head -n 1 |
      sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/')
  if [ -z "$b" ]; then echo "tap: '$1' not found" | tee -a "$OUT/summary.txt"; return 1; fi
  set -- $b
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}

wallpaper_state() { adb shell dumpsys wallpaper | grep -iE "id=|mWallpaperId|wallpaper id" | head -n 5; }

status=0
./gradlew --no-daemon connectedDebugAndroidTest || status=$?
cp -r app/build/reports/androidTests/connected "$OUT/test-report" 2>/dev/null || true
adb logcat -d -s DnevenTapetTest:I > "$OUT/test-log.txt" || true

# Home screen with the wallpaper the test left behind.
adb shell input keyevent KEYCODE_HOME
shot 01-home-after-tests 4

# The app from a fresh install: welcome screen, then "Start" downloads and sets the first picture.
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n bg.dnevnitapet/.ui.MainActivity
shot 02-welcome 5
tap "Започни" && shot 03-main-screen 30

# Browsing must only preview; "Избери" is what changes the wallpaper.
w0=$(wallpaper_state)
tap "Следваща" && shot 03a-browse-next 3
tap "Следваща" && shot 03b-browse-next-2 3
w1=$(wallpaper_state)
tap "Предишна" && shot 03c-browse-previous 3
tap "Избери" && shot 03d-after-choose 5
w2=$(wallpaper_state)
{
  echo "before browsing:        $w0"
  echo "after 2x Следваща:      $w1"
  echo "after Избери:           $w2"
  if [ "$w0" = "$w1" ]; then echo "OK: browsing did not change the wallpaper"; else echo "FAIL: browsing changed the wallpaper"; fi
  if [ "$w1" != "$w2" ]; then echo "OK: Избери changed the wallpaper"; else echo "FAIL: Избери did not change the wallpaper"; fi
} | tee -a "$OUT/summary.txt"

# Swipe like in a gallery: the picture follows the finger; the wallpaper does not change.
w3=$(wallpaper_state)
adb shell input swipe 950 1000 100 1000 250
shot 03g-after-swipe-left 3
adb shell input swipe 100 1000 950 1000 250
shot 03h-after-swipe-right 3
adb shell input swipe 950 1000 450 1000 2500 &
sleep 1.5
adb exec-out screencap -p > "$OUT/03i-mid-swipe.png"
wait
sleep 2
w4=$(wallpaper_state)
if [ "$w3" = "$w4" ]; then echo "OK: swiping did not change the wallpaper"; else echo "FAIL: swiping changed the wallpaper"; fi | tee -a "$OUT/summary.txt"

# Full screen: a tap on the picture hides everything, another tap brings it back.
has_text() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null 2>&1
  grep -q "text=\"$1\"" /tmp/ui.xml
}
adb shell input tap 540 900
shot 03j-full-screen 2
if has_text "Избери" || has_text "Това е тапетът ти"; then echo "FAIL: controls still visible in full screen"; else echo "OK: full screen hides the controls"; fi | tee -a "$OUT/summary.txt"
adb shell input tap 540 900
shot 03k-back-from-full-screen 2
if has_text "Следваща"; then echo "OK: second tap brings the controls back"; else echo "FAIL: controls did not come back"; fi | tee -a "$OUT/summary.txt"

# Narrow screen (360 dp) and bigger font: the buttons' labels must still fit on one line.
adb shell wm density 480
shot 03e-narrow-360dp 4
adb shell settings put system font_scale 1.3
shot 03f-narrow-big-font 4
adb shell settings put system font_scale 1.0
adb shell wm density reset
sleep 3

tap "Информация за снимката" && shot 04-info-sheet 3
adb shell input keyevent KEYCODE_BACK
sleep 2
tap "Настройки" && shot 05-settings 3
adb shell input swipe 540 1900 540 500 400; shot 06-settings-2 2
adb shell input swipe 540 1900 540 500 400; shot 07-settings-3 2
adb shell input swipe 540 1900 540 500 400; shot 08-settings-4 2
adb shell input keyevent KEYCODE_BACK
sleep 2
tap "История" && shot 09-history 3
adb shell input keyevent KEYCODE_BACK
adb shell cmd uimode night yes
shot 10-main-dark 5
tap "Настройки" && shot 11-settings-dark 3
adb shell input keyevent KEYCODE_BACK
adb shell cmd uimode night no

# Quick Settings tile.
adb shell cmd statusbar add-tile bg.dnevnitapet/.tile.NextTileService
adb shell cmd statusbar expand-settings
shot 12-quick-settings 3
adb shell cmd statusbar collapse
adb shell input keyevent KEYCODE_HOME
shot 13-home-before-tile 3
before=$(wallpaper_state)
adb shell cmd statusbar click-tile bg.dnevnitapet/.tile.NextTileService
shot 14-home-after-tile 8
after_tile=$(wallpaper_state)

# The widget's "next" button sends this broadcast.
adb shell am broadcast -n bg.dnevnitapet/.widget.NextWidgetProvider -a bg.dnevnitapet.widget.NEXT
shot 15-home-after-widget-next 8
after_widget=$(wallpaper_state)

{
  echo "wallpaper before tile:   $before"
  echo "wallpaper after tile:    $after_tile"
  echo "wallpaper after widget:  $after_widget"
} | tee -a "$OUT/summary.txt"

adb logcat -d > "$OUT/logcat-full.txt" || true
if grep -q "^FAIL" "$OUT/summary.txt"; then status=1; fi
exit $status
