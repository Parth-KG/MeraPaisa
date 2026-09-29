#!/usr/bin/env bash
# Screenshots every gallery case, every sheet and dialog, and the widget in day and in night, on
# the connected phone, and pulls them into build/gallery-after (or the directory given as $1).
#
# Only ever touches the debug build, com.kg.merapaisa.debug, which installs beside a release build
# rather than over it. The tests run through `am instrument` rather than Gradle's connected task,
# because that task uninstalls the debug app when it finishes.
#
# The widget's day and night colours are resolved by the launcher from the phone's own night mode,
# so the widget pass runs twice with the phone flipped between them. The phone's setting is put
# back afterwards, even if a pass fails.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 2

OUT="${1:-build/gallery-after}"
APP=com.kg.merapaisa.debug
RUNNER="$APP.test/androidx.test.runner.AndroidJUnitRunner"
REMOTE="/sdcard/Android/data/$APP/files/gallery"

adb get-state >/dev/null

./gradlew -q assembleDebug assembleDebugAndroidTest
adb install -r -t app/build/outputs/apk/debug/app-debug.apk >/dev/null
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null

adb shell input keyevent KEYCODE_WAKEUP
adb shell rm -rf "$REMOTE"

failed=0
run() {
  local result
  result=$(adb shell am instrument -w -e class "$1" "$RUNNER" | tr -d '\r')
  printf '%s: %s\n' "${1##*.}" "$(printf '%s\n' "$result" | grep -E '^(OK|FAILURES)' || echo 'no result')"
  printf '%s\n' "$result" | grep -q '^OK' || { printf '%s\n' "$result" | tail -20; failed=1; }
}

run com.kg.merapaisa.ui.gallery.DesignGalleryTest
run com.kg.merapaisa.ui.gallery.WindowGalleryTest

original=$(adb shell cmd uimode night | tr -d '\r' | awk '{print $NF}')
trap 'adb shell cmd uimode night "$original" >/dev/null' EXIT
for mode in no yes; do
  adb shell cmd uimode night "$mode" >/dev/null
  sleep 2
  run com.kg.merapaisa.ui.gallery.WidgetGalleryTest
done

rm -rf "$OUT"
mkdir -p "$OUT"
adb pull "$REMOTE/." "$OUT" >/dev/null
echo "$(ls "$OUT" | wc -l | tr -d ' ') images in $OUT"
exit $failed
