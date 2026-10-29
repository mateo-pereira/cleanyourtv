package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A "Continue watching" row in the dock's place (continue_row): while it has focus it sits where the dock rests and
 * the host pushes the dock down by dockShift() so only its top peeks in; Down hands focus back to the dock, which
 * slides up again while the row fades out. Up to SLOTS tiles, read from the system's Watch Next
 * list (TvContract.WatchNextPrograms), newest first, one per show. Slots without a show stay as empty outlines
 * and take no focus. The tiles show the app's banner, the show and the episode, with no poster art: the posters
 * are web links and the launcher has no network access. Needs READ_TV_LISTINGS, granted from the shell
 * (pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS); without it the row stays hidden.
 * See docs/watch-next.md for what the provider returns.
 */
final class ContinueRow {
    static final int SLOTS = 5;
    private static final String TAG = "TvHomeContinue";
    private static final Uri WATCH_NEXT = Uri.parse("content://android.media.tv/watch_next_program");
    private static final int TYPE_WATCHLIST = 3;

    interface Host {
        /** A tile took focus: move the highlight onto it. */
        void onContinueFocused(View card, int slot);
        /** Down from the tile in this column: back to the dock. */
        void onContinueDown(int column);
        /** Up from the row: to the top bar. */
        void onContinueUp();
        /** The list was read: the host may move focus to the row now. */
        void onContinueReady();
    }

    static final class Item {
        final String pkg, title, subtitle, intentUri;
        final float progress;   // 0..1, or -1 when unknown
        final long engaged;
        Drawable logo;

        Item(String pkg, String title, String subtitle, String intentUri, float progress, long engaged) {
            this.pkg = pkg;
            this.title = title;
            this.subtitle = subtitle;
            this.intentUri = intentUri;
            this.progress = progress;
            this.engaged = engaged;
        }
    }

    private final Context ctx;
    private final float density;
    private final Host host;
    private final FrameLayout container;
    private final TextView header;
    private final FrameLayout[] tiles = new FrameLayout[SLOTS];
    private final List<Item> items = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Typeface medium, regular;
    private int tileW, tileH;
    private float focusScale = 1.1f;
    private boolean placed, shown, wanted;
    private boolean animations = true, advancedEffects, reducedMotion, overshoot = true;
    private volatile boolean loading;

    ContinueRow(Context ctx, ViewGroup root, int index, float density, Host host) {
        this.ctx = ctx;
        this.density = density;
        this.host = host;
        medium = ctx.getResources().getFont(R.font.outfit_medium);
        regular = ctx.getResources().getFont(R.font.outfit_regular);
        container = new FrameLayout(ctx);
        container.setClipChildren(false);
        container.setClipToPadding(false);
        // Room under the tiles for the focused one's zoom: the root clips the container to its own bounds.
        container.setPadding(0, 0, 0, Math.round(24 * density));
        // Invisible rather than gone while hidden: it stays laid out, so the glass can be fitted to a tile the
        // moment focus arrives.
        container.setAlpha(0f);
        container.setVisibility(View.INVISIBLE);
        header = new TextView(ctx);
        header.setText(R.string.continue_watching);
        header.setTextColor(0xB3FFFFFF);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        header.setTypeface(medium);
        header.setLetterSpacing(0.22f);
        // Single line first: setSingleLine replaces the transformation that setAllCaps installs.
        header.setSingleLine(true);
        header.setAllCaps(true);
        container.addView(header, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < SLOTS; i++) {
            tiles[i] = makeTile(i);
            container.addView(tiles[i]);
        }
        root.addView(container, index, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** The same motion settings as the app tiles (AppAdapter), so Left and Right feel the same on both rows. */
    void setMotion(boolean animations, boolean advancedEffects, boolean reducedMotion, boolean overshoot) {
        this.animations = animations;
        this.advancedEffects = advancedEffects;
        this.reducedMotion = reducedMotion;
        this.overshoot = overshoot;
        focusScale = !animations ? 1f : advancedEffects ? 1.12f : 1.10f;
    }

    int count() {
        return items.size();
    }

    boolean hasFocus() {
        return container.hasFocus();
    }

    /** How far the dock moves down while the row is in its place: the row's height and a gap. */
    int dockShift() {
        return tileH + Math.round(28 * density);
    }

    boolean placed() {
        return placed;
    }

    /**
     * Lines the tiles up with the dock: the first dock card's left edge and width, the column pitch, and the dock
     * cards' top at rest, which the tiles take over while the row has focus.
     */
    void place(int firstLeft, int pitch, int cardW, float dockTop) {
        // Margins, not translations: the glass is placed from layout positions (offsetDescendantRectToMyCoords),
        // which ignore a translation.
        int h = Math.round(cardW * 0.62f);
        int headerH = Math.round(30 * density);
        boolean resized = cardW != tileW || h != tileH;
        tileW = cardW;
        tileH = h;
        for (int i = 0; i < SLOTS; i++) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) tiles[i].getLayoutParams();
            int left = firstLeft + i * pitch;
            if (resized || lp.width != tileW || lp.leftMargin != left || lp.topMargin != headerH) {
                lp = new FrameLayout.LayoutParams(tileW, tileH);
                lp.topMargin = headerH;
                lp.leftMargin = left;
                tiles[i].setLayoutParams(lp);
            }
            if (resized) styleTile(i);
        }
        FrameLayout.LayoutParams hp = (FrameLayout.LayoutParams) header.getLayoutParams();
        int headerLeft = firstLeft + Math.round(4 * density);
        if (hp.leftMargin != headerLeft) {
            hp.leftMargin = headerLeft;
            header.setLayoutParams(hp);
        }
        FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) container.getLayoutParams();
        int top = Math.round(dockTop) - headerH;
        if (cp.topMargin != top) {
            cp.topMargin = top;
            container.setLayoutParams(cp);
        }
        placed = true;
    }

    /** Shows the row in the dock's place, once it is placed and has a show. */
    void show() {
        wanted = true;
        update();
    }

    /** The dock has focus again, or the grid scrolled: fade the row out. */
    void hide() {
        wanted = false;
        update();
    }

    private void update() {
        boolean visible = wanted && placed && !items.isEmpty();
        if (visible == shown) return;
        shown = visible;
        container.animate().cancel();
        if (visible) {
            container.setVisibility(View.VISIBLE);
            if (animations) container.animate().alpha(1f).setDuration(220).start();
            else container.setAlpha(1f);
        } else if (animations) {
            container.animate().alpha(0f).setDuration(160)
                    .withEndAction(() -> { if (!shown) container.setVisibility(View.INVISIBLE); }).start();
        } else {
            container.setAlpha(0f);
            container.setVisibility(View.INVISIBLE);
        }
    }

    /** Focus the tile in this column, or the last show when the column is empty. False when there is none. */
    boolean focus(int column) {
        if (!placed || items.isEmpty()) return false;
        show();
        return tiles[Math.max(0, Math.min(column, items.size() - 1))].requestFocus();
    }

    /** Reads the Watch Next list off the main thread, then fills the tiles. */
    void refresh(Set<String> hidden) {
        if (loading) return;
        loading = true;
        new Thread(() -> {
            List<Item> fresh = query(ctx, hidden);
            PackageManager pm = ctx.getPackageManager();
            for (Item it : fresh) {
                try {
                    Drawable d = pm.getApplicationBanner(it.pkg);
                    it.logo = d != null ? d : pm.getApplicationIcon(it.pkg);
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
            main.post(() -> {
                loading = false;
                setItems(fresh);
            });
        }, "continue-row").start();
    }

    private void setItems(List<Item> fresh) {
        boolean hadFocus = container.hasFocus();
        int focusedSlot = -1;
        for (int i = 0; i < SLOTS; i++) if (tiles[i].isFocused()) focusedSlot = i;
        items.clear();
        items.addAll(fresh);
        for (int i = 0; i < SLOTS; i++) bind(i);
        if (items.isEmpty() && hadFocus) host.onContinueDown(0);
        else if (hadFocus && focusedSlot >= items.size()) tiles[items.size() - 1].requestFocus();
        update();
        if (!items.isEmpty()) host.onContinueReady();
    }

    static List<Item> query(Context ctx, Set<String> hidden) {
        List<Item> out = new ArrayList<>();
        PackageManager pm = ctx.getPackageManager();
        Map<String, Item> newest = new HashMap<>();
        // No selection: without ACCESS_ALL_EPG_DATA a selection is refused (docs/watch-next.md).
        try (Cursor c = ctx.getContentResolver().query(WATCH_NEXT, null, null, null, null)) {
            if (c == null) return out;
            while (c.moveToNext()) {
                String pkg = str(c, "package_name");
                String title = str(c, "title");
                if (pkg.isEmpty() || title.isEmpty() || hidden.contains(pkg)) continue;
                if ("0".equals(str(c, "browsable"))) continue;
                if (num(c, "watch_next_type", -1) == TYPE_WATCHLIST) continue;
                if (pm.getLeanbackLaunchIntentForPackage(pkg) == null) continue;
                long pos = num(c, "last_playback_position_millis", -1);
                long dur = num(c, "duration_millis", -1);
                float progress = pos > 0 && dur > 0 ? Math.min(1f, pos / (float) dur) : -1f;
                if (progress >= 0.97f) continue;   // finished: the row would only point at the credits
                long engaged = num(c, "last_engagement_time_utc_millis", 0);
                Item it = new Item(pkg, title, subtitle(c, pos, dur), str(c, "intent_uri"), progress, engaged);
                String key = pkg + "\n" + title;
                Item old = newest.get(key);
                if (old == null || engaged > old.engaged) newest.put(key, it);
            }
        } catch (SecurityException e) {
            Log.i(TAG, "no READ_TV_LISTINGS: pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS");
            return out;
        } catch (RuntimeException e) {
            Log.w(TAG, "watch next query failed", e);
            return out;
        }
        out.addAll(newest.values());
        out.sort((a, b) -> Long.compare(b.engaged, a.engaged));
        return out.size() > SLOTS ? new ArrayList<>(out.subList(0, SLOTS)) : out;
    }

    private static String subtitle(Cursor c, long pos, long dur) {
        List<String> parts = new ArrayList<>();
        String season = str(c, "season_display_number");
        String episode = str(c, "episode_display_number");
        if (!season.isEmpty()) parts.add("S" + season);
        if (!episode.isEmpty()) parts.add("E" + episode);
        String episodeTitle = str(c, "episode_title");
        if (!episodeTitle.isEmpty()) parts.add(episodeTitle);
        if (pos > 0 && dur > pos) {
            long min = Math.max(1, Math.round((dur - pos) / 60000.0));
            parts.add(min + " min left");
        }
        return TextUtils.join(" · ", parts);
    }

    private static String str(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        if (i < 0 || c.isNull(i)) return "";
        String s = c.getString(i);
        return s == null ? "" : s.trim();
    }

    private static long num(Cursor c, String col, long fallback) {
        int i = c.getColumnIndex(col);
        if (i < 0 || c.isNull(i)) return fallback;
        try {
            return c.getLong(i);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    // ---- tiles ---------------------------------------------------------------------------------------

    private static final int ID_LOGO = 1, ID_TITLE = 2, ID_META = 3, ID_TRACK = 4, ID_FILL = 5;

    private FrameLayout makeTile(int slot) {
        FrameLayout t = new FrameLayout(ctx);
        t.setClipToOutline(true);
        ImageView logo = new ImageView(ctx);
        logo.setId(ID_LOGO);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        t.addView(logo);
        TextView title = text(ID_TITLE, 14, 0xFFFFFFFF, medium);
        TextView meta = text(ID_META, 11, 0xB3FFFFFF, regular);
        meta.setLetterSpacing(0.04f);
        t.addView(title);
        t.addView(meta);
        View track = new View(ctx);
        track.setId(ID_TRACK);
        track.setBackgroundColor(0x40FFFFFF);
        View fill = new View(ctx);
        fill.setId(ID_FILL);
        fill.setBackgroundColor(0xFFFFFFFF);
        t.addView(track);
        t.addView(fill);
        // The app tiles' zoom (AppAdapter.Holder): the same scale, durations and overshoot, so the glass and the
        // tile move exactly as they do on the dock and the grid.
        t.setOnFocusChangeListener((v, has) -> {
            v.animate().cancel();
            float s = has ? focusScale : 1f;
            if (!animations) {
                v.setScaleX(1f);
                v.setScaleY(1f);
                v.setTranslationZ(0f);
            } else if (reducedMotion) {
                v.animate().scaleX(s).scaleY(s)
                        .translationZ(has && advancedEffects ? 8f * density : 0f)
                        .setDuration(has ? Motion.REDUCED_MS : 60)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
            } else {
                v.animate().scaleX(s).scaleY(s)
                        .translationZ(has && advancedEffects ? 8f * density : 0f)
                        .setDuration(advancedEffects ? (has ? 220 : 170) : (has ? 260 : 180))
                        .setInterpolator(has && !overshoot ? new android.view.animation.DecelerateInterpolator(1.5f)
                                : new android.view.animation.OvershootInterpolator(has ? (advancedEffects ? 0.7f : 1.6f) : 0f))
                        .withLayer().start();
            }
            if (has) host.onContinueFocused(v, slot);
        });
        t.setOnKeyListener((v, keyCode, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) {
                return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
            }
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    host.onContinueDown(slot);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    host.onContinueUp();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (slot == 0) return true;
                    tiles[slot - 1].requestFocus();
                    v.playSoundEffect(android.view.SoundEffectConstants.NAVIGATION_LEFT);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (slot >= items.size() - 1) return true;
                    tiles[slot + 1].requestFocus();
                    v.playSoundEffect(android.view.SoundEffectConstants.NAVIGATION_RIGHT);
                    return true;
                default:
                    return false;
            }
        });
        t.setOnClickListener(v -> {
            if (slot < items.size()) open(items.get(slot));
        });
        return t;
    }

    private TextView text(int id, int sp, int color, Typeface face) {
        TextView tv = new TextView(ctx);
        tv.setId(id);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        tv.setTypeface(face);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    /** Sizes the children for the current tile size. */
    private void styleTile(int slot) {
        FrameLayout t = tiles[slot];
        int m = Math.round(12 * density);
        int logoH = Math.round(Math.min(20 * density, tileH * 0.2f));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, logoH);
        lp.leftMargin = m;
        lp.topMargin = Math.round(10 * density);
        t.findViewById(ID_LOGO).setLayoutParams(lp);
        lp = new FrameLayout.LayoutParams(tileW - 2 * m, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        lp.leftMargin = m;
        lp.bottomMargin = Math.round(33 * density);
        t.findViewById(ID_TITLE).setLayoutParams(lp);
        lp = new FrameLayout.LayoutParams(tileW - 2 * m, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        lp.leftMargin = m;
        lp.bottomMargin = Math.round(17 * density);
        t.findViewById(ID_META).setLayoutParams(lp);
        lp = new FrameLayout.LayoutParams(tileW - 2 * m, Math.round(3 * density), Gravity.BOTTOM);
        lp.leftMargin = m;
        lp.bottomMargin = Math.round(8 * density);
        t.findViewById(ID_TRACK).setLayoutParams(lp);
        bind(slot);
    }

    private void bind(int slot) {
        FrameLayout t = tiles[slot];
        boolean filled = slot < items.size();
        Item it = filled ? items.get(slot) : null;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(tileW > 0 ? AppAdapter.cornerRadius(tileW, density) : 12 * density);
        if (filled) bg.setColor(0xE6141519);
        else bg.setStroke(Math.max(1, Math.round(density)), 0x33FFFFFF);
        t.setBackground(bg);
        t.setFocusable(filled);
        t.setFocusableInTouchMode(false);
        t.setContentDescription(filled ? it.title : null);
        ImageView logo = t.findViewById(ID_LOGO);
        logo.setImageDrawable(filled ? it.logo : null);
        logo.setVisibility(filled && it.logo != null ? View.VISIBLE : View.GONE);
        TextView title = t.findViewById(ID_TITLE);
        TextView meta = t.findViewById(ID_META);
        title.setText(filled ? it.title : "");
        meta.setText(filled ? it.subtitle : "");
        boolean bar = filled && it.progress >= 0f;
        View track = t.findViewById(ID_TRACK);
        View fill = t.findViewById(ID_FILL);
        track.setVisibility(bar ? View.VISIBLE : View.GONE);
        fill.setVisibility(bar ? View.VISIBLE : View.GONE);
        if (bar && tileW > 0) {
            int m = Math.round(12 * density);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    Math.max(1, Math.round((tileW - 2 * m) * it.progress)), Math.round(3 * density), Gravity.BOTTOM);
            lp.leftMargin = m;
            lp.bottomMargin = Math.round(8 * density);
            fill.setLayoutParams(lp);
        }
        if (!filled && t.isFocused()) t.clearFocus();
    }

    /**
     * Opens the episode through the row's own link, but only into the app that published the row: the link is that
     * app's data, so it must not be able to start another app's (or this launcher's) activities or grant URI access.
     * Anything else opens the app at its start screen.
     */
    private void open(Item it) {
        PackageManager pm = ctx.getPackageManager();
        Intent launch = null;
        if (!it.intentUri.isEmpty()) {
            try {
                Intent i = Intent.parseUri(it.intentUri, Intent.URI_INTENT_SCHEME);
                i.setSelector(null);
                i.removeFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ResolveInfo ri = pm.resolveActivity(i, 0);
                if (ri != null && ri.activityInfo != null && it.pkg.equals(ri.activityInfo.packageName)) {
                    i.setPackage(it.pkg);
                    launch = i;
                }
            } catch (java.net.URISyntaxException | RuntimeException e) {
                Log.w(TAG, "bad intent_uri from " + it.pkg, e);
            }
        }
        if (launch == null) {
            launch = pm.getLeanbackLaunchIntentForPackage(it.pkg);
            if (launch == null) return;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            ctx.startActivity(launch);
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "could not open " + it.pkg, e);
        }
    }
}
