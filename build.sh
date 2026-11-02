#!/bin/bash
set -e
cd /mnt/sdcard/wifi/app/src/main
SDK=/usr/lib/android-sdk
BT=$SDK/build-tools/36.0.0
PLAT=$SDK/platforms/android-35
FW=/mnt/sdcard/wifi/framework_stub
B=/tmp/wtbuild
rm -rf "$B"; mkdir -p "$B/fw_comp" "$B/fw_res" "$B/res_comp" "$B/R_gen" "$B/classes" "$B/dex"

# framework stub (android namespace attrs)
$BT/aapt2 compile -o "$B/fw_comp" "$FW/res/values/attrs.xml"
$BT/aapt2 link -o "$B/fw_res/framework-stub.apk" --manifest "$FW/AndroidManifest.xml" "$B/fw_comp"/*.flat

# app resources
$BT/aapt2 compile -o "$B/res_comp" res/values/strings.xml

# link app (warn on manifest validation so stub 'private' attrs don't hard-fail)
$BT/aapt2 link -o "$B/unsigned.apk" --manifest AndroidManifest.xml -I "$B/fw_res/framework-stub.apk" --warn-manifest-validation --java "$B/R_gen" "$B/res_comp"/*.flat

# compile java
javac -source 8 -target 8 -cp "$PLAT/android.jar:$B/R_gen" -d "$B/classes" java/com/ollie/wifitester/MainActivity.java

# dex
$BT/d8 --release --output "$B/dex" --lib "$PLAT/android.jar" "$B/classes"/com/ollie/wifitester/*.class

# package
cd "$B"
mkdir -p pkg && cd pkg
rm -f ../unaligned.apk
unzip -o -q ../unsigned.apk
cp ../dex/classes.dex .
zip -r -0 ../unaligned.apk AndroidManifest.xml resources.arsc classes.dex
cd "$B"

# align + sign
$BT/zipalign -p 4 unaligned.apk aligned.apk
keytool -genkeypair -alias wfkey -keyalg RSA -keystore debug.keystore -storepass android -keypass android -dname "CN=WifiTester Debug" -validity 10000 2>/dev/null || true
$BT/apksigner sign --ks debug.keystore --ks-key-alias wfkey --ks-pass pass:android --key-pass pass:android --out /mnt/sdcard/wifi/WifiTester.apk aligned.apk
echo "APK: /mnt/sdcard/wifi/WifiTester.apk"
ls -lh /mnt/sdcard/wifi/WifiTester.apk
$BT/aapt2 dump badging /mnt/sdcard/wifi/WifiTester.apk 2>&1 | head -8
