#!/usr/bin/env bash
# 无 Gradle 构建：aapt2 → javac → d8 → zipalign → apksigner
set -euo pipefail

SDK="${ANDROID_SDK_ROOT:-/opt/android-sdk}"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/app/src/main"
OUT="$ROOT/build"
KS="$ROOT/keystore.jks"
APK_OUT="${1:-$ROOT/BOLT-7-debug.apk}"

for f in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$PLATFORM"; do
  [ -e "$f" ] || { echo "缺少构建组件: $f"; exit 1; }
done

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "[1/5] 编译资源"
"$BT/aapt2" compile --dir "$APP/res" -o "$OUT/res.zip"

echo "[2/5] 链接资源与清单"
"$BT/aapt2" link \
  -o "$OUT/app-unsigned.apk" \
  -I "$PLATFORM" \
  --manifest "$APP/AndroidManifest.xml" \
  "$OUT/res.zip" \
  --java "$OUT/gen" \
  --min-sdk-version 21 \
  --target-sdk-version 34 \
  --version-code 12 \
  --version-name "10.0"

echo "[3/5] 编译 Java"
find "$ROOT/app/src/main/java" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -source 8 -target 8 -bootclasspath "$PLATFORM" -encoding UTF-8 -nowarn \
  -d "$OUT/classes" @"$OUT/sources.txt"

echo "[4/5] 生成 dex 并打包"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --lib "$PLATFORM" --min-api 21 --release --output "$OUT/dex" @"$OUT/classes.txt"
(cd "$OUT/dex" && zip -q "$OUT/app-unsigned.apk" classes.dex)

echo "[5/5] 对齐与签名"
"$BT/zipalign" -f -p 4 "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -v -keystore "$KS" -alias bolt7 -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass bolt7game -keypass bolt7game \
    -dname "CN=BOLT-7, OU=Game, O=Pixel, L=Shenzhen, S=Guangdong, C=CN" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-key-alias bolt7 \
  --ks-pass pass:bolt7game --key-pass pass:bolt7game \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out "$APK_OUT" "$OUT/app-aligned.apk"

"$BT/apksigner" verify "$APK_OUT"
echo "构建完成: $APK_OUT"
du -h "$APK_OUT"
