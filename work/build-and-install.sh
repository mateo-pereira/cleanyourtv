#!/usr/bin/env bash
# Rebuild the launcher from launcher/app/src/main/res/values/config.xml and install it on the Streamer.
# Usage (from the repo root): TV_HOST=<tv ip> bash work/build-and-install.sh
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST to the IP of the Streamer}"
export JAVA_HOME="${JAVA_HOME:-$(ls -d "$LOCALAPPDATA"/Programs/jdk17/jdk-17* | head -1)}"
export ANDROID_HOME="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
# Already Home: the script then installs over itself and disables nothing.
# INPUT_KEY_SERVICE=1: the YouTube button (keycode 190) via app_keys; costs the held-volume ramp here.
INPUT_KEY_SERVICE="${INPUT_KEY_SERVICE:-1}" bash launcher/build-and-install.sh
# continue_row (personal branch personal/continue-watching): let the launcher read the Watch Next list.
PY=tools/venv/bin/python; [ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
"$PY" tools/tv.py "pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS 2>/dev/null && echo continue-row-granted || true"
