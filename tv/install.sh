#!/usr/bin/env bash
# Installs the Franklin First classroom screen on one Fire TV and gives it the Home button.
#   bash install.sh <room> <tv-ip>          e.g.  bash ~/Downloads/install.sh B138 192.168.10.125
#   bash install.sh --undo <tv-ip>          give Home back to Amazon
# Keep ffumc-classroom-tv.apk and classroom.conf in the same folder as this script.
# Home-button takeover adapted from dumbtv's launcher/install.sh (MIT).
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
PKG=org.franklinfumc.classroomtv
GUARD=$PKG/$PKG.Guard
OLD=tv.dumbtv.home
TOOLS=$HOME/ffumc-tv/platform-tools
ADB=$TOOLS/adb

undo=0
if [[ ${1:-} == --undo ]]; then undo=1; shift; ROOM=; IP=${1:-}
else ROOM=${1:-}; IP=${2:-}; fi
[[ -n $IP && ( $undo == 1 || -n $ROOM ) ]] || { echo "usage: bash install.sh <room> <tv-ip>   or   bash install.sh --undo <tv-ip>"; exit 2; }

if [[ ! -x $ADB ]]; then
  echo "Downloading Android tools..."
  mkdir -p "$HOME/ffumc-tv" && cd "$HOME/ffumc-tv"
  curl -sL -o pt.zip https://dl.google.com/android/repository/platform-tools-latest-darwin.zip && unzip -oq pt.zip
  cd "$HERE"
fi
adb_() { "$ADB" -s "$IP:5555" "$@"; }
sh_() { adb_ shell "$@" </dev/null; }

"$ADB" connect "$IP:5555" >/dev/null 2>&1 || true
echo "Connecting to the TV at $IP. If the TV asks 'Allow USB debugging?', tick Always allow and choose Allow."
for i in $(seq 1 45); do
  [[ $("$ADB" -s "$IP:5555" get-state 2>/dev/null || true) == device ]] && break
  sleep 2; "$ADB" connect "$IP:5555" >/dev/null 2>&1 || true
done
[[ $("$ADB" -s "$IP:5555" get-state 2>/dev/null || true) == device ]] || { echo "Could not reach the TV. Check it is on the same Wi-Fi and ADB debugging is on."; exit 1; }

set_futurity() {
  local cur
  cur=$(sh_ settings get global alarm_manager_constants | tr -d '\r')
  [[ $cur == null ]] && cur=
  cur=$(printf '%s' "$cur" | tr ',' '\n' | { grep -v '^min_futurity=' || true; } | paste -sd, -)
  if [[ $1 == on ]]; then cur=${cur:+$cur,}min_futurity=0; fi
  if [[ -n $cur ]]; then sh_ settings put global alarm_manager_constants "$cur"
  else sh_ settings delete global alarm_manager_constants >/dev/null; fi
}
set_guard() {
  local cur
  cur=$(sh_ settings get secure enabled_accessibility_services | tr -d '\r')
  [[ $cur == null ]] && cur=
  cur=$(printf '%s' "$cur" | tr ':' '\n' | { grep -v -e "^$PKG/" -e "^$OLD/" -e '^$' || true; } | paste -sd: -)
  if [[ -n ${1:-} ]]; then cur=${cur:+$cur:}$1; fi
  sh_ settings put secure enabled_accessibility_services '""'
  sleep 1
  if [[ -n $cur ]]; then
    sh_ settings put secure enabled_accessibility_services "$cur"
    sh_ settings put secure accessibility_enabled 1
  else
    sh_ settings put secure accessibility_enabled 0
  fi
}

if [[ $undo == 1 ]]; then
  sh_ am force-stop $PKG >/dev/null 2>&1 || true
  set_futurity off
  set_guard ""
  sh_ am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1 || true
  echo "Done. Home goes to Amazon's screen again. (Remove the app completely with: $ADB -s $IP:5555 uninstall $PKG)"
  exit 0
fi

APK=$HERE/ffumc-classroom-tv.apk
CONF=$HERE/classroom.conf
[[ -f $APK ]] || { echo "Missing $APK — download it from the GitHub release into the same folder."; exit 1; }
[[ -f $CONF ]] || { echo "Missing $CONF — download it from the GitHub release into the same folder."; exit 1; }

if sh_ pm path $OLD 2>/dev/null | grep -q '^package:'; then
  echo "Removing the test home screen (dumbtv)..."
  sh_ am force-stop $OLD >/dev/null 2>&1 || true
  adb_ uninstall $OLD >/dev/null 2>&1 || true
fi

echo "Installing the Franklin First screen..."
out=$(adb_ install -r "$APK" 2>&1 | grep -m1 -E 'Success|Failure|failed' || true)
[[ $out == *Success* ]] || { echo "Install failed: $out"; exit 1; }

DIR=/sdcard/Android/data/$PKG/files
TMP=$(mktemp)
sed "s/^room=.*/room=$ROOM/" "$CONF" > "$TMP"
sh_ mkdir -p $DIR
adb_ push "$TMP" $DIR/classroom.conf >/dev/null
rm -f "$TMP"

sh_ dumpsys deviceidle whitelist +$PKG >/dev/null
sh_ cmd package set-home-activity $PKG/.Home >/dev/null 2>&1 || true
set_futurity on
set_guard $GUARD
sleep 2
sh_ am start -W -n $PKG/.Home >/dev/null
sleep 2
if sh_ dumpsys accessibility | grep -q "$PKG"; then
  echo "Done. Room $ROOM is set. Press Home on the remote to test it."
else
  echo "Installed, but Fire OS has not switched on the Home button takeover yet."
  echo "Copy this message to Claude."
  sh_ settings get secure enabled_accessibility_services
fi
