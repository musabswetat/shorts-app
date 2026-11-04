package com.shorts.app.v2;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class PlayerActivity extends Activity {
    public static final String EXTRA_VIDEOS = "videos_json";
    public static final String EXTRA_START_INDEX = "start_index";

    private ExoPlayer player;
    private PlayerView playerView;
    private final List<String> videoUris = new ArrayList<>();
    private final List<Integer> cycle = new ArrayList<>();
    private final List<Integer> history = new ArrayList<>();
    private int cyclePosition = 0;
    private int currentIndex = -1;
    private float downX, downY;
    private boolean userSwiping = false;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveMode();
        readVideos();
        if (videoUris.isEmpty()) { finish(); return; }
        buildLayout();
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playerView.setUseController(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) playNextRandom();
            }
            @Override public void onPlayerError(androidx.media3.common.PlaybackException error) {
                Toast.makeText(PlayerActivity.this, "تعذر تشغيل هذا المقطع، سيتم الانتقال للمقطع التالي", Toast.LENGTH_SHORT).show();
                playerView.postDelayed(() -> playNextRandom(), 500);
            }
        });
        int start = Math.max(0, Math.min(getIntent().getIntExtra(EXTRA_START_INDEX, 0), videoUris.size() - 1));
        buildCycle(start);
        playIndex(start);
    }

    private void readVideos() {
        try {
            JSONArray array = new JSONArray(getIntent().getStringExtra(EXTRA_VIDEOS));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String uri = item.optString("uri", "");
                if (uri.isEmpty()) uri = item.optString("path", "");
                if (!uri.isEmpty()) videoUris.add(uri);
            }
        } catch (Exception e) { videoUris.clear(); }
    }

    private void buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        playerView = new PlayerView(this);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setUseController(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        Button close = new Button(this);
        close.setText("✕"); close.setTextColor(Color.WHITE); close.setTextSize(18);
        close.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.TOP | Gravity.RIGHT);
        closeLp.setMargins(0, dp(22), dp(10), 0);
        root.addView(close, closeLp);
        close.setOnClickListener(v -> finish());

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(0xCC151B2A);
        String[] labels = {"⌂\nالرئيسية", "⤨\nعشوائي", "↻\nتحديث"};
        for (int i = 0; i < labels.length; i++) {
            final int action = i;
            Button button = new Button(this);
            button.setText(labels[i]);
            button.setAllCaps(false);
            button.setTextColor(0xFFE5E3FF);
            button.setTextSize(12);
            button.setBackgroundColor(Color.TRANSPARENT);
            nav.addView(button, new LinearLayout.LayoutParams(0, dp(64), 1));
            button.setOnClickListener(v -> {
                if (action == 0) finish();
                else if (action == 1) reshuffle();
                else { setResult(Activity.RESULT_FIRST_USER); finish(); }
            });
        }
        FrameLayout.LayoutParams navLp = new FrameLayout.LayoutParams(-1, dp(70), Gravity.BOTTOM);
        root.addView(nav, navLp);

        playerView.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX(); downY = event.getY(); userSwiping = false; return true;
                case MotionEvent.ACTION_UP:
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (Math.abs(dy) > dp(55) && Math.abs(dy) > Math.abs(dx)) {
                        userSwiping = true;
                        if (dy < 0) playNextRandom(); else playPrevious();
                    } else if (Math.abs(dx) < dp(24) && Math.abs(dy) < dp(24)) {
                        if (player != null) { if (player.isPlaying()) player.pause(); else player.play(); }
                    }
                    return true;
            }
            return true;
        });
        setContentView(root);
    }

    private void buildCycle(int firstIndex) {
        cycle.clear();
        for (int i = 0; i < videoUris.size(); i++) if (i != firstIndex) cycle.add(i);
        Collections.shuffle(cycle, new Random(System.nanoTime()));
        cycle.add(0, firstIndex);
        cyclePosition = 0;
    }

    private void playIndex(int index) {
        if (player == null || index < 0 || index >= videoUris.size()) return;
        currentIndex = index;
        history.add(index);
        try {
            player.setMediaItem(MediaItem.fromUri(Uri.parse(videoUris.get(index))));
            player.prepare();
            player.play();
        } catch (Exception e) {
            Toast.makeText(this, "صيغة هذا الفيديو أو مصدره غير مدعوم", Toast.LENGTH_SHORT).show();
            playNextRandom();
        }
    }

    private void playNextRandom() {
        if (videoUris.size() <= 1) { if (player != null) { player.seekTo(0); player.play(); } return; }
        if (cyclePosition + 1 >= cycle.size()) {
            int previous = currentIndex;
            cycle.clear();
            for (int i = 0; i < videoUris.size(); i++) cycle.add(i);
            Collections.shuffle(cycle, new Random(System.nanoTime()));
            if (cycle.get(0) == previous && cycle.size() > 1) Collections.swap(cycle, 0, 1);
            cyclePosition = -1;
        }
        cyclePosition++;
        playIndex(cycle.get(cyclePosition));
    }

    private void playPrevious() {
        if (history.size() < 2) return;
        history.remove(history.size() - 1);
        int previous = history.remove(history.size() - 1);
        cyclePosition = cycle.indexOf(previous);
        playIndex(previous);
    }

    private void reshuffle() {
        if (videoUris.size() < 2) return;
        int previous = currentIndex;
        cycle.clear();
        for (int i = 0; i < videoUris.size(); i++) if (i != previous) cycle.add(i);
        Collections.shuffle(cycle, new Random(System.nanoTime()));
        cycle.add(0, previous);
        cyclePosition = 0;
        playNextRandom();
    }

    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
    }

    @Override protected void onResume() { super.onResume(); enterImmersiveMode(); if (player != null) player.play(); }
    @Override protected void onPause() { if (player != null) player.pause(); super.onPause(); }
    @Override protected void onDestroy() { if (player != null) { player.release(); player = null; } super.onDestroy(); }
}
