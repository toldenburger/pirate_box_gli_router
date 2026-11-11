#!/bin/sh
# Builds box_chat.apk without Gradle: aapt2 (resources) -> javac -> d8 (dex) -> zipalign -> apksigner.
#   ./build.sh          build + verify
# Needs: the Android SDK in ~/Android/Sdk (platform android-37.0, build-tools 37.0.0) and a JDK 17.
set -e
cd "$(dirname "$0")"

SDK=${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}
PLATFORM=$SDK/platforms/android-37.0/android.jar
BT=$SDK/build-tools/37.0.0
JDK=${JAVA_HOME:-$HOME/.local/share/mise/installs/java/temurin-17}
export PATH="$JDK/bin:$BT:$PATH"
OUT=build

rm -rf "$OUT"; mkdir -p "$OUT/compiled" "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "1/6 resources"
aapt2 compile --dir res -o "$OUT/compiled/res.zip"
aapt2 link -o "$OUT/app.unsigned.apk" -I "$PLATFORM" --manifest AndroidManifest.xml \
  --java "$OUT/gen" --min-sdk-version 26 --target-sdk-version 36 \
  --version-code 2 --version-name 1.1 "$OUT/compiled/res.zip"

echo "2/6 javac"
javac --release 17 -Xlint:all -classpath "$PLATFORM" -d "$OUT/classes" \
  $(find src "$OUT/gen" -name '*.java')

echo "3/6 d8 (dex)"
d8 --release --min-api 26 --lib "$PLATFORM" --output "$OUT/dex" \
  $(find "$OUT/classes" -name '*.class')

echo "4/6 add classes.dex to the apk"
( cd "$OUT/dex" && zip -q -j "../app.unsigned.apk" classes.dex )

echo "5/6 zipalign + sign"
[ -f debug.jks ] || keytool -genkeypair -keystore debug.jks -alias box -keyalg RSA -keysize 2048 \
  -validity 10000 -storepass android -keypass android -dname "CN=box chat" >/dev/null 2>&1
zipalign -f -p 4 "$OUT/app.unsigned.apk" "$OUT/app.aligned.apk"
apksigner sign --ks debug.jks --ks-pass pass:android --key-pass pass:android \
  --out box_chat.apk "$OUT/app.aligned.apk"

echo "6/6 verify"
apksigner verify --verbose box_chat.apk | head -4
aapt2 dump badging box_chat.apk | grep -E "^package:|sdkVersion|targetSdk|uses-permission|launchable|application-label:"
ls -l box_chat.apk | awk '{print "built:", $NF, $5, "bytes"}'
