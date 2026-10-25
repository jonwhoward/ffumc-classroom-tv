#!/usr/bin/env bash
# Builds app/build/ffumc-classroom-tv.apk from the bare Android SDK tools (no Gradle).
# Adapted from dumbtv's launcher/build.sh (MIT). Needs JDK 17 and an Android SDK with
# platforms;android-28 and build-tools;34.0.0.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

VERSION_CODE=${VERSION_CODE:-1}
VERSION_NAME=${VERSION_NAME:-1.0}
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:?set ANDROID_HOME}}
JAR=$SDK/platforms/android-28/android.jar
BT=$SDK/build-tools/${BUILD_TOOLS:-34.0.0}
[[ -f $JAR ]] || { echo "error: $JAR missing" >&2; exit 1; }
[[ -x $BT/aapt2 ]] || { echo "error: $BT missing" >&2; exit 1; }
JAVAC=${JAVA_HOME:+$JAVA_HOME/bin/}javac
ZXING=libs/zxing-core.jar   # QR codes; the CI workflow downloads it from Maven Central
[[ -f $ZXING ]] || { echo "error: $ZXING missing" >&2; exit 1; }

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/gen" "$WORK/classes" build

"$BT/aapt2" compile --dir res -o "$WORK/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml -A assets "$WORK/res.zip" \
  --min-sdk-version 28 --target-sdk-version 28 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --java "$WORK/gen" -o "$WORK/app.apk"

find src "$WORK/gen" -name '*.java' > "$WORK/sources.txt"
"$JAVAC" --release 8 -Xlint:all -Xlint:-options -encoding UTF-8 \
  -classpath "$JAR:$ZXING" -d "$WORK/classes" @"$WORK/sources.txt"

find "$WORK/classes" -name '*.class' > "$WORK/classes.txt"
"$BT/d8" --release --min-api 28 --lib "$JAR" --output "$WORK" @"$WORK/classes.txt" "$ZXING"
(cd "$WORK" && zip -q -X app.apk classes.dex)

"$BT/zipalign" -f -p 4 "$WORK/app.apk" "$WORK/aligned.apk"
"$BT/apksigner" sign --ks ffumc.keystore --ks-pass pass:ffumctv2026 --key-pass pass:ffumctv2026 \
  --out build/ffumc-classroom-tv.apk "$WORK/aligned.apk"
"$BT/apksigner" verify build/ffumc-classroom-tv.apk
echo "built app/build/ffumc-classroom-tv.apk ($(wc -c < build/ffumc-classroom-tv.apk | tr -d ' ') bytes)"
