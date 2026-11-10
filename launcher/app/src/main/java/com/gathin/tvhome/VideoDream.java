package com.gathin.tvhome;

import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.service.dreams.DreamService;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;
import android.widget.TextClock;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * idle_video_button (mateo-tv): the screensaver that plays the idle video chosen on Home (IdleVideos), muted and
 * filling the screen (centre-cropped), with a small clock in the bottom left. One video loops; Shuffle all plays
 * every video in a random order and shuffles again at the end, never repeating the one that just played.
 */
public class VideoDream extends DreamService implements TextureView.SurfaceTextureListener {
    private static final String TAG = "TvHomeVideoDream";

    private TextureView view;
    private Surface surface;
    private MediaPlayer player;
    private final List<File> queue = new ArrayList<>();
    private int index;
    private int failures;
    private int videoW, videoH;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        float density = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        view = new TextureView(this);
        view.setSurfaceTextureListener(this);
        root.addView(view, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm a");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        clock.setTypeface(getResources().getFont(R.font.outfit_regular));
        clock.setShadowLayer(8 * density, 0, 0, 0xCC000000);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        lp.leftMargin = Math.round(32 * density);
        lp.bottomMargin = Math.round(28 * density);
        root.addView(clock, lp);
        setContentView(root);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        String choice = IdleVideos.choice(this);
        List<IdleVideos.Video> videos = IdleVideos.list(this);
        queue.clear();
        for (IdleVideos.Video v : videos) {
            if (v.file.getName().equals(choice)) queue.add(v.file);
        }
        // Shuffle all, or a chosen video that is gone: every video, in a random order.
        if (queue.isEmpty() && !IdleVideos.OFF.equals(choice)) {
            for (IdleVideos.Video v : videos) queue.add(v.file);
            Collections.shuffle(queue);
        }
        if (queue.isEmpty()) {
            Log.i(TAG, "no idle video to play (choice " + choice + ", " + videos.size() + " videos)");
            finish();
            return;
        }
        index = 0;
        failures = 0;
        if (surface != null) play();
    }

    @Override
    public void onDreamingStopped() {
        release();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        release();
        super.onDetachedFromWindow();
    }

    private void play() {
        releasePlayer();
        File file = queue.get(index);
        MediaPlayer mp = new MediaPlayer();
        player = mp;
        try {
            mp.setSurface(surface);
            mp.setDataSource(file.getAbsolutePath());
            mp.setVolume(0f, 0f);
            mp.setLooping(queue.size() == 1);
            mp.setOnVideoSizeChangedListener((p, w, h) -> {
                videoW = w;
                videoH = h;
                crop();
            });
            mp.setOnPreparedListener(p -> {
                failures = 0;
                p.start();
            });
            mp.setOnCompletionListener(p -> next());
            mp.setOnErrorListener((p, what, extra) -> {
                Log.w(TAG, "cannot play " + file.getName() + " (" + what + "/" + extra + ")");
                if (++failures >= queue.size()) finish();
                else next();
                return true;
            });
            mp.prepareAsync();
        } catch (Exception e) {
            Log.w(TAG, "cannot open " + file.getName(), e);
            if (++failures >= queue.size()) finish();
            else next();
        }
    }

    /** The next video; at the end of the list a new shuffle that does not start with the one just played. */
    private void next() {
        index++;
        if (index >= queue.size()) {
            File last = queue.get(queue.size() - 1);
            Collections.shuffle(queue);
            if (queue.size() > 1 && queue.get(0).equals(last)) Collections.swap(queue, 0, queue.size() - 1);
            index = 0;
        }
        play();
    }

    /** Fill the screen without stretching: scale the video to cover it and centre it. */
    private void crop() {
        int w = view.getWidth(), h = view.getHeight();
        if (w == 0 || h == 0 || videoW == 0 || videoH == 0) return;
        float scale = Math.max(w / (float) videoW, h / (float) videoH);
        Matrix m = new Matrix();
        m.setScale(videoW * scale / w, videoH * scale / h, w / 2f, h / 2f);
        view.setTransform(m);
    }

    private void releasePlayer() {
        if (player == null) return;
        try {
            player.release();
        } catch (RuntimeException ignored) {
        }
        player = null;
    }

    private void release() {
        releasePlayer();
        if (surface != null) {
            surface.release();
            surface = null;
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        surface = new Surface(texture);
        if (!queue.isEmpty() && player == null) play();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        crop();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        release();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
    }
}
