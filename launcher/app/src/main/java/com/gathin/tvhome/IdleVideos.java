package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.provider.Settings;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * idle_video_button (mateo-tv): the videos VideoDream plays while the TV is idle, and which of them the owner
 * picked on Home. The videos are the files in the TV's Movies folder; idle_video_titles gives them names
 * ("file=Title"), and a file without one shows its name. The choice is one video (played on a loop), all of
 * them shuffled, or none, which hands the screensaver to the launcher's clock instead.
 *
 * Switching the screensaver writes Settings.Secure, so the launcher needs WRITE_SECURE_SETTINGS
 * (pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS); reading the Movies folder needs
 * READ_MEDIA_VIDEO (pm grant com.gathin.tvhome android.permission.READ_MEDIA_VIDEO).
 */
final class IdleVideos {
    static final String SHUFFLE = "*shuffle*";
    static final String OFF = "*off*";
    private static final String PREFS = "idle_video";
    private static final String KEY = "choice";
    private static final String SCREENSAVER = "screensaver_components";
    private static final String VIDEO_DREAM = "com.gathin.tvhome/.VideoDream";
    private static final String CLOCK_DREAM = "com.gathin.tvhome/.ClockDream";

    static final class Video {
        final File file;
        final String title;

        Video(File file, String title) {
            this.file = file;
            this.title = title;
        }
    }

    private IdleVideos() {
    }

    /** The videos in the Movies folder: the titled ones in idle_video_titles order, then the rest by name. */
    static List<Video> list(Context ctx) {
        Map<String, String> titles = new LinkedHashMap<>();
        for (String entry : ctx.getResources().getStringArray(R.array.idle_video_titles)) {
            int eq = entry.indexOf('=');
            if (eq > 0 && eq < entry.length() - 1) titles.put(entry.substring(0, eq).trim(), entry.substring(eq + 1).trim());
        }
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).matches(".+\\.(mp4|mkv|webm|mov|m4v)"));
        List<Video> out = new ArrayList<>();
        if (files == null) return out;
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (Map.Entry<String, String> t : titles.entrySet()) {
            for (File f : files) if (f.getName().equals(t.getKey())) out.add(new Video(f, t.getValue()));
        }
        for (File f : files) {
            if (titles.containsKey(f.getName())) continue;
            String name = f.getName();
            out.add(new Video(f, name.substring(0, name.lastIndexOf('.'))));
        }
        return out;
    }

    /** SHUFFLE (the default), OFF, or a video's file name. */
    static String choice(Context ctx) {
        return prefs(ctx).getString(KEY, SHUFFLE);
    }

    /** The choice's name for the menu and the toast. */
    static String label(Context ctx, String choice) {
        if (OFF.equals(choice)) return ctx.getString(R.string.idle_video_none);
        if (SHUFFLE.equals(choice)) return ctx.getString(R.string.idle_video_shuffle);
        for (Video v : list(ctx)) if (v.file.getName().equals(choice)) return v.title;
        return choice;
    }

    /**
     * Saves the choice and points the system screensaver at VideoDream, or at the clock for OFF. False when the
     * launcher may not write the setting (WRITE_SECURE_SETTINGS not granted); the choice is saved either way.
     */
    static boolean apply(Context ctx, String choice) {
        prefs(ctx).edit().putString(KEY, choice).apply();
        try {
            return Settings.Secure.putString(ctx.getContentResolver(), SCREENSAVER, OFF.equals(choice) ? CLOCK_DREAM : VIDEO_DREAM);
        } catch (SecurityException e) {
            return false;
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
