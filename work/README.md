# Google TV Streamer: what was changed, and how to change it back

Model: Google TV Streamer (kirkwood), Google TV 14, build UTTK.260317.003. Cleaned on 2026-10-28.
Everything was **disabled**, not uninstalled, and can be undone. The commands below are run from the repo
folder (`D:\Random Code\cleanyourtv`) in Git Bash, with `TV_HOST` set to the Streamer's IP:

```bash
export TV_HOST=<tv ip>
```

## What was done

| Step | List file | What |
|---|---|---|
| 1 | `work/1-telemetry.txt` | Google feedback, remote logging, ad services and federated learning: disabled |
| 2 | `work/2-apps.txt` | Play Games, Google TV (Play Movies), TalkBack: disabled |
| 3 | `work/3-setupwraith.txt` | The setup wizard: disabled (needed so the new launcher can be Home) |
| 4 | `work/4-screensaver.txt` | Google's Ambient screensaver: disabled |
| - | - | Stock launcher (`launcherx`) disabled by `launcher/build-and-install.sh`, new launcher (`com.gathin.tvhome`) is Home |
| - | - | Screensaver: Aerial Views, alternating `/sdcard/Movies/sakunaVmahoraga.mp4` and `uzuiVgyu.mp4` (shuffle off), muted, after 5 minutes idle (was 10); sleep stays at 20 minutes |
| - | - | Developer options > "Stay awake" switched off (it was on, and kept the screensaver from ever starting). The Streamer now goes to standby after 20 idle minutes |
| - | `work/theme-home/` | Home wallpaper: the blue flower picture at night, the green one by day, dimmed 30%; hold OK on a tile to move it |
| - | - | Private DNS: `dns.adguard-dns.com` (ad and tracker blocking) |
| - | - | Remote's YouTube button: mapped by the launcher's InputKeyService |

Kept on purpose: Google Assistant, Cast receiver, phone remote, Play Store and Play Services, Gboard,
Bluetooth, the remote's firmware updater, and every streaming app you use. YouTube TV, Tubi, Instagram and
Haystack News are only archived Play Store placeholders (no app on the device), so they were hidden from
the launcher instead of disabled.

## Things to know

- **Casting stops after every reboot or power cut.** The Cast receiver loads its credentials from the setup
  wizard, which has to stay disabled. To bring casting back, turn ADB on (below) and run
  `bash work/cast-fix.sh`. It takes about 30 seconds; don't press Home while it runs.
- **Holding Volume Up/Down stops after about six steps** because the YouTube-button helper is on. Taps
  still work. To get the ramp back and give up the YouTube button:
  `tools/venv/Scripts/python.exe tools/tv.py "settings put secure enabled_accessibility_services ''"`
- **AdGuard DNS** blocks trackers and ad servers. It does not remove YouTube ads, or ads that Hulu, Max,
  Peacock and Prime Video stitch into the video itself. To turn it off:
  `tools/venv/Scripts/python.exe tools/tv.py "settings put global private_dns_mode opportunistic"`
  (or Settings > Network & Internet > Private DNS).
- The Wi-Fi pill shows "Wi-Fi" instead of the network name: on Android 14 the name needs Location on.

## Turning ADB back on (needed for every command here)

1. Settings > System > Developer options > **Wireless debugging** on > *Pair device with pairing code*.
2. In PowerShell, with Google's adb (installed at `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`):
   `adb pair <ip>:<pair port> <code>`, then `adb connect <ip>:<port shown on the Wireless debugging screen>`,
   then `adb tcpip 5555`.
3. When finished, switch Wireless debugging and USB debugging off again, then reboot. Port 5555 is
   unencrypted.

## Re-enable one app

```bash
tools/venv/Scripts/python.exe tools/tv.py "pm enable --user 0 <package name>"
```

Package names are in the list files above (for example `com.google.android.videos` for Google TV).
Archived apps (YouTube TV, Tubi, Instagram, Haystack) are simply installed from the Play Store again; to
show them in the launcher, remove them from `hidden_apps` in the config file below and rebuild.

## Change the dock order, the dock size or the apps

Edit `launcher/app/src/main/res/values/config.xml`:

- `dock_apps`: the dock, left to right. Now Max (`com.wbd.stream`), Crunchyroll
  (`com.crunchyroll.crunchyroid`), Netflix (`com.netflix.ninja`), Hulu (`com.hulu.livingroomplus`),
  Paramount+ (`com.cbs.ott`). Paramount+ shows up as soon as you install it from the Play Store; until
  then the first grid app (Disney+) fills its slot.
- `columns`: dock slots and grid width (now 5).
- `hidden_apps`: apps never shown as tiles.

Then rebuild and reinstall: `bash work/build-and-install.sh`.

To rearrange with the remote, no rebuild needed: hold OK on a tile (or press the remote's Menu key) and
choose **Move**. The arrows move the app, and one OK puts it down and saves. The same
menu has **Replace with another app** for a dock slot. The order you set on the TV is kept by the launcher
itself; this switch is `tile_menu` in `work/theme-home/config.json`. On `mateo-tv`, Move works as on Google TV: it is
the first item, the app is picked up straight away, and one OK puts it down and saves.

To move several apps in one go, scroll to the bottom of Home: the small **Rearrange apps** pill sits in the bottom right
corner after the last row. OK on it, then OK picks an app up, the arrows move it and Back puts it down; Back again (or
OK on the pill) finishes and saves. It is `reorder_corner_button` in `launcher/app/src/main/res/values/config.xml`.

## Continue watching row

The Continue watching row is a personal addition to the launcher, on the git branch
`mateo-tv` of your fork (the repo's own launcher has no such row: it needs the extra permission
`READ_TV_LISTINGS`). It reads Android's Continue watching list, newest first and one tile per show; OK opens
that episode in its app, empty slots stay as outlines and are skipped by the remote. It refreshes every time
Home comes back, so a show you just watched appears when you return.

It sits in the dock's place at the bottom of the screen, with the dock peeking in underneath, so the middle of the
wallpaper stays clear. Home (and coming back from a show you opened from the row) lands on it. Down slides the
dock up into its usual spot and the row fades out; Down again goes into the app grid. Up from the dock brings the
row back; Up from the row goes to the top bar.

- Only apps that publish to that list appear. Today that is Netflix; Max, Crunchyroll, Prime Video and others
  may start once you are signed in and watching. YouTube does not publish one.
- No poster art: the apps publish their posters as web links and the launcher has no internet access.
- `bash work/build-and-install.sh` builds whatever branch is checked out. Stay on
  `mateo-tv` (`git switch mateo-tv`) when you rebuild, or the row and the Google TV-style Move are gone.
  Turn it off with `<bool name="continue_row">false</bool>` in `launcher/app/src/main/res/values/config.xml`.
- The permission is granted by `work/build-and-install.sh`; by hand:
  `tools/venv/Scripts/python.exe tools/tv.py "pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS"`.

## Change the wallpaper or the look

The wallpaper is the theme in `work/theme-home/`: the pictures in `wallpapers/` (1920x1080 JPEGs) and
`config.json`. `wallpaper_moods` says which picture shows when: `02-day.jpg` for `morning` and `evening`,
`01-night.jpg` for `night`. Without a location the launcher counts morning from 07:00 and night from about
19:00. Other keys: `wallpaper_dim` 0-90, `dock_opacity` 30-100, and the `hidden_apps` that keep the four Play
Store placeholders and Aerial Views off Home. Change a file or a number, then push it again:
`bash tools/theme.sh work/theme-home`.

The ten stills from the first video are kept in `work/video-stills/`. To use them again, copy them into
`work/theme-home/wallpapers/` and list them in `wallpaper_moods` (or remove `wallpaper_moods` to rotate through
every picture). A new still from a video:

```bash
ffmpeg -ss 95 -i "D:/Mateo/Downloads/sakunaVmahoraga.mp4" -frames:v 1 -vf "scale=-2:1080,crop=1920:1080" -q:v 3 work/theme-home/wallpapers/03-still.jpg
```

(`-ss` is the second of the video). The launcher itself never plays video, by design; your video plays as
the screensaver. Ready-made themes are under `themes/` (each has a `screenshot.jpg`):
`bash tools/theme.sh themes/<name>`. `bash tools/theme.sh --reset` goes back to plain black. The keys are
described in `themes/README.md`.

Screensaver delay: `tools/venv/Scripts/python.exe tools/tv.py "settings put system screen_off_timeout 300000"`
(milliseconds: 300000 is the current 5 minutes, 600000 the original 10), or Settings > System > Energy.

## Change the screensaver video

Copy another mp4 to the Streamer's `Movies` folder:
`tools/venv/Scripts/python.exe tools/b64push.py <file.mp4> /sdcard/Movies/<name>.mp4`
(in Git Bash, run `export MSYS_NO_PATHCONV=1` first). Aerial Views plays every video it finds there, one after
the other in filename order (Shuffle is off under Aerial Views > Settings > Playlist; tick it for random order).
On the remote during the screensaver, Right skips to the next video.
To go back to the launcher's clock:
`tools/tv.py "settings put secure screensaver_components com.gathin.tvhome/.ClockDream"`.

## Undo everything

```bash
bash work/restore.sh
```

It takes the wallpaper theme off, puts the screensaver delay back to 10 minutes and "Stay awake" back on,
re-enables every package
from the four lists, makes the stock Google TV home the default again,
switches the key helper and Private DNS off, and puts Google's Ambient screensaver back. Reboot afterwards.
The new launcher stays installed as an ordinary app until you remove it (the script prints how).

The original backup (package lists, settings, getprop) is in `backup/`.
