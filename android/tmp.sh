#!/usr/bin/env bash
# Flatten the nested system-image, write a minimal AVD config by hand,
# then launch the emulator.
set -u

SDK=/Volumes/SSD1/Storage/android-sdk
SYSIMG_DIR="$SDK/system-images/android-37.0/google_apis_playstore_ps16k/arm64-v8a"

# Flatten arm64-v8a/arm64-v8a -> arm64-v8a
if [ -d "$SYSIMG_DIR/arm64-v8a" ]; then
  echo "[flatten] $SYSIMG_DIR"
  mv "$SYSIMG_DIR/arm64-v8a"/* "$SYSIMG_DIR/" 2>/dev/null
  for f in "$SYSIMG_DIR/arm64-v8a"/.*; do
    b="$(basename "$f")"
    [ "$b" = "." ] && continue
    [ "$b" = ".." ] && continue
    mv "$f" "$SYSIMG_DIR/" 2>/dev/null
  done
  rmdir "$SYSIMG_DIR/arm64-v8a" 2>/dev/null || true
fi

find "$SDK" -name '._*' -type f -delete 2>/dev/null

echo "[verify] system-image contents:"
ls "$SYSIMG_DIR" | head -10

# Synthesize a package.xml for the system image so the emulator finds it.
if [ ! -f "$SYSIMG_DIR/package.xml" ]; then
  echo "[synth] $SYSIMG_DIR/package.xml"
  cat > "$SYSIMG_DIR/package.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:repository xmlns:ns2="http://schemas.android.com/repository/android/common/02"
                xmlns:ns6="http://schemas.android.com/sdk/android/repo/sys-img2/03">
  <localPackage path="system-images;android-37.0;google_apis_playstore_ps16k;arm64-v8a" obsolete="false">
    <type-details xsi:type="ns6:sysImgDetailsType" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
      <api-level>37</api-level>
      <tag><id>google_apis_playstore</id><display>Google Play</display></tag>
      <vendor><id>google</id><display>Google Inc.</display></vendor>
      <abi>arm64-v8a</abi>
    </type-details>
    <revision><major>1</major></revision>
    <display-name>Google Play arm64 System Image</display-name>
  </localPackage>
</ns2:repository>
XML
fi

# Create AVD by hand (no avdmanager needed).
AVD_HOME="$HOME/.android/avd"
AVD_NAME="muxy_pixel"
mkdir -p "$AVD_HOME/${AVD_NAME}.avd"

cat > "$AVD_HOME/${AVD_NAME}.ini" <<EOF
avd.ini.encoding=UTF-8
path=$AVD_HOME/${AVD_NAME}.avd
path.rel=avd/${AVD_NAME}.avd
target=android-37
EOF

cat > "$AVD_HOME/${AVD_NAME}.avd/config.ini" <<EOF
AvdId=${AVD_NAME}
PlayStore.enabled=true
abi.type=arm64-v8a
avd.ini.displayname=Muxy Pixel
avd.ini.encoding=UTF-8
disk.dataPartition.size=6G
hw.accelerometer=yes
hw.audioInput=yes
hw.battery=yes
hw.camera.back=virtualscene
hw.camera.front=emulated
hw.cpu.arch=arm64
hw.cpu.ncore=4
hw.dPad=no
hw.device.manufacturer=Google
hw.device.name=pixel_7
hw.gps=yes
hw.gpu.enabled=yes
hw.gpu.mode=auto
hw.initialOrientation=portrait
hw.keyboard=yes
hw.lcd.density=420
hw.lcd.height=2400
hw.lcd.width=1080
hw.mainKeys=no
hw.ramSize=4096
hw.sdCard=yes
hw.sensors.orientation=yes
hw.sensors.proximity=yes
hw.trackBall=no
image.sysdir.1=system-images/android-37.0/google_apis_playstore_ps16k/arm64-v8a/
runtime.network.latency=none
runtime.network.speed=full
sdcard.size=512M
showDeviceFrame=no
skin.dynamic=yes
tag.display=Google Play
tag.id=google_apis_playstore
vm.heapSize=512
EOF

echo
echo "[avd] created $AVD_NAME at $AVD_HOME/${AVD_NAME}.avd"
echo
echo "Now launch with:"
echo "  ANDROID_HOME=$SDK $SDK/emulator/emulator -avd $AVD_NAME"
