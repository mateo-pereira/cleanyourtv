# todo.md: the changes to make on another TV

Every change asked for on the first TV (Google TV Streamer, Google TV 14), in order, so they can be repeated on
another TV. Hand this file to the coding agent along with [PROMPT.md](../PROMPT.md): PROMPT.md is the safe
procedure (connect, back up first, disable rather than uninstall, close ADB at the end). This file is the list of
choices already made, so the agent should not ask about them again.

Not included, on purpose: copying the screensaver videos and the wallpaper pictures. Add your own where a step
says **[your file]**.

Steps marked **(Streamer)** were needed because of how the Google TV Streamer works. On another model, check its
folder under `examples/` and do what that folder says instead.

Files from the first TV that this list refers to (all in `work/`):

| File | What it is |
|---|---|
| `continue-row.patch` | The launcher code from the `mateo-tv` branch as a patch (Continue watching row, Google TV-style Move), plus all the `config.xml` values below |
| `theme-home/config.json` | The Home theme (wallpaper timing, dim, tile moving, hidden apps) |
| `1-telemetry.txt` ... `4-screensaver.txt` | The debloat lists used on the Streamer |
| `build-and-install.sh`, `restore.sh`, `cast-fix.sh` | Helper scripts. `build-and-install.sh` assumes this PC's JDK and SDK paths |

---

## 1. Debloat

- [ ] Follow PROMPT.md: back up first, then disable one small batch at a time and take a screenshot after each.
- [ ] **Telemetry, feedback and ads**: disable them. On a Google TV these are the packages in
      `work/1-telemetry.txt`:
      `com.google.android.feedback`, `com.android.tv.feedbackconsent`,
      `com.google.android.tv.remotecontrol.logging`, `com.google.android.federatedcompute`,
      `com.google.android.ondevicepersonalization.services`, `com.google.android.adservices.api`,
      `com.google.android.ext.adservices.api`. Add the manufacturer's own telemetry if that TV's `examples/` list has any.
- [ ] **Preloaded apps not used**: disable `com.google.android.play.games` (Play Games),
      `com.google.android.videos` (Google TV / Play Movies) and `com.google.android.marvin.talkback` (TalkBack).
- [ ] **Streaming apps not used**: YouTube TV, Tubi, Instagram and Haystack News. On the Streamer these were only
      Play Store placeholders (archived apps), so they were hidden from Home (`hidden_apps`, step 3) rather than disabled.
      If they are real installs on the other TV, disable them.
- [ ] **Keep these streaming apps**: Netflix, YouTube, Prime Video, Disney+, Hulu, Max, Peacock, Crunchyroll,
      Twitch, Spotify and Paramount+. Paramount+ (`com.cbs.ott`) was not installed on the first TV; install it from the Play Store.
- [ ] **Keep**: Google Assistant / voice search, the Chromecast receiver and firmware updates (for the TV and its
      remote), as well as everything PROMPT.md says the TV needs.
- [ ] **Disable Google's Ambient screensaver**: `com.google.android.apps.tv.dreamx` (Google TV). Step 5 replaces it.

## 2. Full clean with the new launcher

- [ ] Install the repo's launcher as Home and disable the stock launcher (`launcher/build-and-install.sh` does
      both, and checks Home before disabling anything).
- [ ] **(Streamer)** The setup wizard `com.google.android.tungsten.setupwraith` has to be disabled, or the new
      launcher can never own Home. Before disabling it, check `dumpsys wifi | grep -o 'cname=[^ ]*'`. If the saved
      Wi-Fi belongs to setupwraith, forget the network and re-join it in Settings > Network & Internet first.
- [ ] **(Streamer)** With setupwraith disabled, casting stops after every reboot. Bring it back with
      `work/cast-fix.sh` (enable setupwraith, wait for the Cast signer, disable it again). Accepted as a trade-off.

## 3. Launcher settings (`launcher/app/src/main/res/values/config.xml`)

All of these are already in `work/continue-row.patch` (step 8), so applying that patch sets them. By hand:

- [ ] `dock_apps`, 5 slots in this order: `com.wbd.stream` (Max), `com.crunchyroll.crunchyroid` (Crunchyroll),
      `com.netflix.ninja` (Netflix), `com.hulu.livingroomplus` (Hulu), `com.cbs.ott` (Paramount+).
- [ ] `columns`: `5`.
- [ ] `hidden_apps`: `com.google.android.youtube.tvunplugged`, `com.tubitv`, `com.instagram.airwave`,
      `com.haystack.android`.
- [ ] `live_tv_package`: empty. **(Streamer)** The Streamer has no tuner. On a TV with Live TV or HDMI inputs,
      keep that TV's Live TV package here.
- [ ] `app_keys`: `190:com.google.android.youtube.tv`, so the remote's YouTube button works without the stock launcher.
- [ ] `continue_row`: `true` (only exists with the `mateo-tv` code from step 8).
- [ ] `reorder_corner_button`: `true` (only exists with the `mateo-tv` code): the Rearrange apps pill in step 7.

## 4. Remote buttons

- [ ] After the stock launcher is disabled, press every button on the real remote. On the Streamer, everything
      worked except **YouTube**.
- [ ] Fix the YouTube button with the launcher's key helper: build with `INPUT_KEY_SERVICE=1` and `app_keys` from
      step 3. Trade-off accepted: holding Volume Up/Down stops after about six steps. On another remote the
      YouTube button may send a different keycode: check with the real remote (see PROMPT.md).

## 5. Screensaver: your videos with Aerial Views

- [ ] Install **Aerial Views** (`com.neilturner.aerialviews`) from the Play Store.
- [ ] Copy **[your file]** videos to `/sdcard/Movies/` (`tools/b64push.py <file> /sdcard/Movies/<name>.mp4`,
      after `export MSYS_NO_PATHCONV=1` in Git Bash), then:
      `am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Movies/<name>.mp4`.
- [ ] `pm grant com.neilturner.aerialviews android.permission.READ_MEDIA_VIDEO`
- [ ] `settings put secure screensaver_components com.neilturner.aerialviews/.ui.screensaver.DreamActivity`
- [ ] In Aerial Views > **Media Sources**, turn **off** Videos by Apple, Videos by Amazon, Videos by Jetson
      Creative and Videos by Robin Fourcade. Turn **on** Local or USB storage. "Test local search settings" should
      find your videos.
- [ ] In Aerial Views > **Settings > Playlist**: **untick Shuffle**, so the videos alternate in filename order.
      **Audio mode** must be "Mute audio from videos" (no sound). Leave "Loop photos and videos until skipped" and
      "Random start position" unticked.

## 6. Screensaver timing

- [ ] Start the screensaver after **5 minutes** idle: `settings put system screen_off_timeout 300000`.
- [ ] Turn **Stay awake** off: `settings put global stay_on_while_plugged_in 0`. It was on, and it stops Android
      from ever starting the screensaver. Side effect accepted: the TV goes to standby after the sleep timer
      (20 minutes on the Streamer, Settings > System > Energy).
- [ ] Check it works: leave Home alone past the delay, then `dumpsys dreams | grep mCurrentDream` should name Aerial Views.

## 7. Home wallpaper and look (a theme)

- [ ] Copy `work/theme-home/` (with its `config.json`) and put **[your file]** pictures in its `wallpapers/` folder,
      as 1920x1080 JPEGs: `01-night.jpg` (the night picture) and `02-day.jpg` (the day picture).
- [ ] The `config.json` from the first TV:
      - `wallpaper: true`, `wallpaper_rotation_minutes: 10`
      - `wallpaper_moods`: `02-day.jpg` for morning and evening, `01-night.jpg` for night (night is from about 19:00
        to 07:00 without a location)
      - `wallpaper_dim: 30`, `wallpaper_true_color: true` (only on a TV with 2 GB of RAM or more), `dock_opacity: 70`,
        `ambient_glow: false`
      - `tile_menu: true`
      - `hidden_apps`: the four from step 3 plus `com.neilturner.aerialviews`
- [ ] Push it: `bash tools/theme.sh work/theme-home`.
- [ ] **Rearranging with the remote, as on Google TV**: `tile_menu: true` turns on the menu, and the `mateo-tv`
      launcher code (step 8) makes it work like Google TV. Hold OK (or press Menu) on any app; **Move** is the first
      item; the app is picked up at once (blue glass, the other apps dimmed); the arrows move it; **one OK (or Back)
      puts it down and saves**. Check: move an app one slot and back, and confirm the order stays after Home.
- [ ] **Rearrange apps button in the bottom right corner** (`reorder_corner_button`, `mateo-tv` code): a small pill
      after the last row of apps, reached with Down from the last row or Right from the last app. OK on it starts
      rearranging several apps in a row (OK picks one up, the arrows move it, Back puts it down); Back again, or OK
      on the pill, finishes and saves. It takes the App library pill's place, so it only shows with `app_library` off.

## 8. Continue watching row (launcher code, personal)

- [ ] Get the code: it lives on the **`mateo-tv`** branch of `github.com/mateo-pereira/cleanyourtv`. Clone that fork
      and `git switch mateo-tv`. Without the fork, `git apply work/continue-row.patch` on a clean `main` (from the repo
      root) gives the same code. Either way every `config.xml` value from step 3 comes with it.
- [ ] What it does: five tiles reading Android's Continue watching list (newest first, one per show, opening that
      episode in its app). Empty slots stay as outlines that the remote skips. No poster art, because the launcher
      has no internet access.
- [ ] Motion: the row's tiles zoom and the glass springs exactly as on the dock and the grid, so Left and Right
      feel the same on every row.
- [ ] Where it sits: **in the dock's place** at the bottom of the screen, with the dock peeking in underneath, so it
      never covers the middle of the wallpaper. Home lands on it. Down slides the dock up and the row fades out; Up
      from the dock brings it back; Up from the row goes to the top bar.
- [ ] Build and install with `INPUT_KEY_SERVICE=1` (step 4), then grant:
      `pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS` (`work/build-and-install.sh` does both).
- [ ] Only apps that publish Continue watching appear. On the first TV that was Netflix; sign in to the other apps
      and watch something to see whether they publish too.
- [ ] Check: Home lands on the row with the glass on the first show. Home pressed from a row tile, the dock or the
      grid must not leave the row overlapping the dock.

## 9. Ad-blocking DNS

- [ ] `settings put global private_dns_specifier dns.adguard-dns.com`, then
      `settings put global private_dns_mode hostname`. The router there does no DNS filtering of its own.
      Undo: `settings put global private_dns_mode opportunistic`.
- [ ] What it does and doesn't do: it blocks trackers and separate ad servers. It does not block YouTube ads, or ads
      that Hulu, Max, Peacock and Prime Video build into the stream. Check an app still loads afterwards.

## 10. Finish

- [ ] Reboot once and compare `pm list packages -d` with what was disabled. **(Streamer)** Then run `work/cast-fix.sh`.
- [ ] Write the other TV's own `restore.sh` and README (PROMPT.md asks for both).
- [ ] Close ADB (PROMPT.md, "Back up first"): Revoke USB debugging authorizations, switch USB debugging and Wireless
      debugging off, reboot, and `tools/adb-closed.py <tv ip>` must report port 5555 closed.

---

*Changes asked for later go below this line.*
