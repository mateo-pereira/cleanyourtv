#!/usr/bin/env bash
# Undo everything done to this Google TV Streamer, newest step first.
# Needs ADB on again: Settings > System > Developer options > USB debugging on (then accept the prompt),
# and port 5555 opened once with Wireless debugging + `adb tcpip 5555` if it refuses.
# Usage (from the repo root): TV_HOST=<tv ip> bash work/restore.sh
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST to the IP of the Streamer}"
PY=tools/venv/bin/python; [ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
# Launcher theme (wallpaper) off, screensaver delay back to 10 minutes.
bash tools/theme.sh --reset || true
"$PY" tools/tv.py "settings put system screen_off_timeout 600000"
"$PY" tools/tv.py "settings put global stay_on_while_plugged_in 3"
# Screensaver back to Google's Ambient mode, and Private DNS off (harmless if never set).
for l in work/4-screensaver.txt work/3-setupwraith.txt work/2-apps.txt work/1-telemetry.txt; do
  [ -f "$l" ] && bash tools/restore.sh "$l" com.google.android.apps.tv.launcherx
done
"$PY" tools/tv.py "settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop"
"$PY" tools/tv.py "settings put global private_dns_mode opportunistic"
# The new launcher's key helper, if it was switched on.
"$PY" tools/tv.py "settings put secure enabled_accessibility_services ''"
# Stock launcher back as Home (restore.sh above re-enables it; this makes it the default again).
"$PY" tools/tv.py "pm enable --user 0 com.google.android.apps.tv.launcherx"
"$PY" tools/tv.py "cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity"
echo "Done. To remove the new launcher completely: $PY tools/tv.py 'pm uninstall com.gathin.tvhome'"
echo "(that one is a normal app you installed, not a system package). Then reboot the Streamer."
