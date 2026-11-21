#!/usr/bin/env bash
# Builds RedFilter.apk with no Gradle: aapt2 compile+link -> javac -> d8 -> aapt add -> zipalign -> apksigner.
# Usage: bash build.sh [install]
#   "install" also sends the app to a phone over USB and does the one-time setup (see README).
# Needs: a JDK (17 works) and the Android SDK with build-tools and one platform. Override the defaults with
#   ANDROID_SDK_ROOT, BUILD_TOOLS (version folder name), PLATFORM (e.g. android-36), JAVA_HOME, KEYSTORE,
#   in the environment or in a file local.env next to this script.
set -euo pipefail
P="$(cd "$(dirname "$0")" && pwd)"
[ -f "$P/local.env" ] && . "$P/local.env"   # optional, not in the repo: your own paths
SDK="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/${BUILD_TOOLS:-36.0.0}"
JAR="$SDK/platforms/${PLATFORM:-android-36}/android.jar"
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
KS="${KEYSTORE:-$HOME/.android/debug.keystore}"
PKG=io.github.nostramadeus.redfilter
B="$P/build"
rm -rf "$B"; mkdir -p "$B/classes" "$B/dex"

"$BT/aapt2" compile --dir "$P/res" -o "$B/res.zip"
"$BT/aapt2" link -o "$B/base.apk" -I "$JAR" --manifest "$P/AndroidManifest.xml" "$B/res.zip"
javac -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$JAR" -d "$B/classes" $(find "$P/src" -name '*.java')
D8="$BT/d8"; [ -f "$BT/d8.bat" ] && D8="$BT/d8.bat"
"$D8" --min-api 28 --lib "$JAR" --output "$B/dex" $(find "$B/classes" -name '*.class')
cp "$B/dex/classes.dex" "$B/classes.dex"
(cd "$B" && "$BT/aapt" add base.apk classes.dex >/dev/null)
"$BT/zipalign" -f 4 "$B/base.apk" "$B/aligned.apk"
SIGN="$BT/apksigner"; [ -f "$BT/apksigner.bat" ] && SIGN="$BT/apksigner.bat"
"$SIGN" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --out "$P/RedFilter.apk" "$B/aligned.apk"
echo "built $P/RedFilter.apk"

if [ "${1:-}" = install ]; then
  A="$SDK/platform-tools/adb"
  APK="$P/RedFilter.apk"; command -v cygpath >/dev/null && APK="$(cygpath -w "$APK")"
  "$A" install -r "$APK"   # some phones ask for a confirmation on the screen
  # lets the app reconnect its own service; some phones refuse this until "Disable permission monitoring" is on
  "$A" shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS || true
  "$A" shell settings put secure enabled_accessibility_services $PKG/$PKG.FilterService
  "$A" shell settings put secure accessibility_enabled 1
  "$A" shell cmd deviceidle whitelist +$PKG
fi
