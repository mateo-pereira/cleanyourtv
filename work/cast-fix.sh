#!/usr/bin/env bash
# Bring casting back after a reboot of the Google TV Streamer (examples/google-tv-streamer-4k-kirkwood).
# The Cast receiver loads its device credentials from the setup wizard, which has to stay disabled
# for the new launcher to own Home. Enable it briefly, wait for the signer, disable it again.
# Do NOT press Home on the remote while this runs. Usage: TV_HOST=<tv ip> bash work/cast-fix.sh
set -uo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST to the IP of the Streamer}"
PY=tools/venv/bin/python; [ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
W=com.google.android.tungsten.setupwraith
"$PY" tools/tv.py "logcat -c"
"$PY" tools/tv.py "pm enable --user 0 $W"
ok=""
for i in $(seq 1 12); do
  sleep 5
  if "$PY" tools/tv.py "logcat -d -s cast_shell" | grep -q "Created singleton instance of ClientAuthSigner"; then ok=1; break; fi
done
"$PY" tools/tv.py "pm disable-user --user 0 $W"
"$PY" tools/tv.py "pm list packages -d | grep -c $W" | grep -q 1 && echo "setupwraith disabled again." \
  || echo "WARNING: setupwraith is still enabled - run: $PY tools/tv.py 'pm disable-user --user 0 $W'"
if [ -n "$ok" ]; then echo "Cast credentials loaded: casting works until the next reboot."
else echo "No ClientAuthSigner line within 60 s; try casting anyway, or run this again."; fi
