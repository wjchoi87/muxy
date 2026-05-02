#!/usr/bin/env bash
# Build, install, and launch the Android Muxy client on the muxy_pixel emulator.
# Usage:
#   scripts/run-mobile.sh              # build + install + launch
#   scripts/run-mobile.sh stop         # force-stop the app on the emulator
#   scripts/run-mobile.sh restart      # stop, then build + install + launch
#   scripts/run-mobile.sh logs         # tail logcat for the app
set -euo pipefail

SDK="${ANDROID_HOME:-/Volumes/SSD1/Storage/android-sdk}"
ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"
AVD_NAME="${MUXY_AVD:-muxy_pixel}"
PKG="com.muxy.app"
ACTIVITY="$PKG/.MainActivity"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"

cmd="${1:-run}"

stop_app() {
  if "$ADB" get-state >/dev/null 2>&1; then
    "$ADB" shell am force-stop "$PKG" 2>/dev/null && echo "Muxy stopped" || echo "Muxy not running"
  else
    echo "No device attached"
  fi
}

case "$cmd" in
  stop)
    stop_app
    exit 0
    ;;
  restart)
    stop_app
    ;;
  logs)
    exec "$ADB" logcat -v color -s "MuxyClient:* AndroidRuntime:E System.err:W $PKG:*"
    ;;
  run|"")
    ;;
  *)
    echo "Unknown command: $cmd"
    echo "Usage: $0 [run|stop|restart|logs]"
    exit 1
    ;;
esac

# 1. Boot emulator if no device attached.
if ! "$ADB" get-state 1>/dev/null 2>&1; then
  echo "Starting emulator '$AVD_NAME'..."
  ANDROID_AVD_HOME="" ANDROID_HOME="$SDK" \
    "$EMULATOR" -avd "$AVD_NAME" -no-snapshot-save >/tmp/muxy-emulator.log 2>&1 &
  EMU_PID=$!
  echo "  emulator pid=$EMU_PID  log=/tmp/muxy-emulator.log"
  echo -n "  waiting for boot"
  for _ in $(seq 1 120); do
    if "$ADB" wait-for-device >/dev/null 2>&1 && \
       [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
      echo " ready"
      break
    fi
    echo -n "."
    sleep 2
  done
fi

# 2. Build debug APK.
echo "Building debug APK..."
GRADLE_USER_HOME="$HOME/.gradle" \
JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home}" \
  "$ROOT_DIR/gradlew" -q -p "$ROOT_DIR" \
    -Pandroid.builder.sdkDownload=false \
    assembleDebug

if [ ! -f "$APK" ]; then
  echo "APK not found at $APK"
  exit 1
fi

# 3. Install + launch.
echo "Installing $APK"
"$ADB" install -r "$APK" >/dev/null
echo "Launching $ACTIVITY"
"$ADB" shell am start -n "$ACTIVITY" >/dev/null

LOCAL_IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "unknown")
echo
echo "Muxy running on emulator '$AVD_NAME'"
echo "Connect using: 10.0.2.2:4865 (emulator <-> Mac host) or $LOCAL_IP:4865 (real device)"
echo "Tail logs:   scripts/run-mobile.sh logs"
