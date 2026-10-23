#!/usr/bin/env bash
# Runs inside the emulator: installs the release APK, measures cold start, janky frames, takes screenshots.
set -u
APK=app/build/outputs/apk/release/app-release.apk
PKG=com.akis.app
OUT=emulator-report
mkdir -p "$OUT"
adb install -r "$APK" > "$OUT/install.txt" 2>&1
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS > /dev/null 2>&1 || true

start_times() {
  : > "$1"
  for i in 1 2 3 4 5; do
    adb shell am force-stop "$PKG"
    sleep 2
    adb shell am start -W -n "$PKG/.MainActivity" | grep -E "TotalTime|WaitTime" | tr '\n' ' ' >> "$1"
    echo >> "$1"
    sleep 3
  done
}

# 1) as installed (profile is not compiled yet)
adb shell cmd package compile -f -m verify "$PKG" > /dev/null 2>&1
start_times "$OUT/start-verify.txt"
# 2) ahead-of-time compiled from the baseline profile that ships in the APK
adb shell cmd package compile -f -m speed-profile "$PKG" > "$OUT/compile-speed-profile.txt" 2>&1
start_times "$OUT/start-speed-profile.txt"

adb shell am force-stop "$PKG"
adb shell dumpsys gfxinfo "$PKG" reset > /dev/null 2>&1
adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
sleep 5
adb exec-out screencap -p > "$OUT/home.png"
adb shell dumpsys gfxinfo "$PKG" > "$OUT/gfxinfo.txt" 2>&1
adb logcat -d -s AndroidRuntime:E > "$OUT/crashes.txt" 2>&1
adb shell pidof "$PKG" > "$OUT/alive.txt" 2>&1 || echo "NOT RUNNING" >> "$OUT/alive.txt"

{
  echo "== verify (no profile)"; cat "$OUT/start-verify.txt"
  echo "== speed-profile"; cat "$OUT/start-speed-profile.txt"
  echo "== alive"; cat "$OUT/alive.txt"
  echo "== crashes"; cat "$OUT/crashes.txt"
} | tee "$OUT/summary.txt"
