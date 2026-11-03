package com.shorts.app.v2;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.io.File;
import java.util.ArrayList;

public class PlayerActivity extends Activity {

    public static final String EXTRA_PATHS = "video_paths";
    public static final String EXTRA_NAMES = "video_names";
    public static final String EXTRA_INDEX = "start_index";

    private ExoPlayer player;
    private PlayerView playerView;

    private TextView titleView;
    private TextView counterView;

    private ArrayList<String> paths;
    private ArrayList<String> names;

    private int currentIndex;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        applyImmersiveMode();

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        paths = getIntent().getStringArrayListExtra(EXTRA_PATHS);
        names = getIntent().getStringArrayListExtra(EXTRA_NAMES);

        currentIndex = getIntent().getIntExtra(
                EXTRA_INDEX,
                0
        );

        if (paths == null || paths.isEmpty()) {
            finish();
            return;
        }

        if (names == null) {
            names = new ArrayList<>();
        }

        FrameLayout root = new FrameLayout(this);

        root.setBackgroundColor(0xFF000000);

        playerView = new PlayerView(this);

        playerView.setUseController(false);

        playerView.setResizeMode(
                AspectRatioFrameLayout.RESIZE_MODE_FIT
        );

        root.addView(
                playerView,
                new FrameLayout.LayoutParams(-1, -1)
        );

        TextView back = new TextView(this);

        back.setText("‹  المكتبة");
        back.setTextColor(0xFFFFFFFF);
        back.setTextSize(14);
        back.setGravity(Gravity.CENTER);
        back.setPadding(22, 12, 22, 12);
        back.setBackground(pill());

        FrameLayout.LayoutParams backParams =
                new FrameLayout.LayoutParams(
                        -2,
                        -2,
                        Gravity.TOP | Gravity.START
                );

        backParams.setMargins(18, 35, 0, 0);

        root.addView(back, backParams);

        back.setOnClickListener(v -> finish());

        titleView = new TextView(this);

        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTextSize(14);
        titleView.setMaxLines(2);
        titleView.setShadowLayer(8, 0, 2, 0xCC000000);

        FrameLayout.LayoutParams titleParams =
                new FrameLayout.LayoutParams(
                        -1,
                        -2,
                        Gravity.BOTTOM
                );

        titleParams.setMargins(20, 0, 20, 42);

        root.addView(titleView, titleParams);

        counterView = new TextView(this);

        counterView.setTextColor(0xCCFFFFFF);
        counterView.setTextSize(12);

        FrameLayout.LayoutParams counterParams =
                new FrameLayout.LayoutParams(
                        -2,
                        -2,
                        Gravity.BOTTOM | Gravity.END
                );

        counterParams.setMargins(0, 0, 20, 16);

        root.addView(counterView, counterParams);

        setContentView(root);

        player = new ExoPlayer.Builder(this).build();

        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {

            @Override
            public void onPlayerError(PlaybackException error) {

                Toast.makeText(
                        PlayerActivity.this,
                        "تعذر تشغيل الفيديو. قد لا يدعم الهاتف ترميزه.",
                        Toast.LENGTH_LONG
                ).show();
            }

            @Override
            public void onPlaybackStateChanged(int state) {

                if (state == Player.STATE_ENDED &&
                        currentIndex < paths.size() - 1) {

                    playIndex(currentIndex + 1);
                }
            }
        });

        GestureDetector detector = new GestureDetector(
                this,
                new GestureDetector.SimpleOnGestureListener() {

                    @Override
                    public boolean onDown(MotionEvent event) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapUp(MotionEvent event) {

                        if (player.isPlaying()) {
                            player.pause();
                        } else {
                            player.play();
                        }

                        return true;
                    }

                    @Override
                    public boolean onFling(
                            MotionEvent first,
                            MotionEvent second,
                            float velocityX,
                            float velocityY
                    ) {

                        float dy = second.getY() - first.getY();

                        float dx = second.getX() - first.getX();

                        if (Math.abs(dy) > Math.abs(dx) &&
                                Math.abs(dy) > 85 &&
                                Math.abs(velocityY) > 100) {

                            if (dy < 0) {

                                playIndex(
                                        Math.min(
                                                currentIndex + 1,
                                                paths.size() - 1
                                        )
                                );

                            } else {

                                playIndex(
                                        Math.max(
                                                currentIndex - 1,
                                                0
                                        )
                                );
                            }

                            return true;
                        }

                        return false;
                    }
                }
        );

        playerView.setOnTouchListener(
                (view, event) -> detector.onTouchEvent(event)
        );

        playIndex(
                Math.max(
                        0,
                        Math.min(currentIndex, paths.size() - 1)
                )
        );
    }

    private android.graphics.drawable.Drawable pill() {

        android.graphics.drawable.GradientDrawable drawable =
                new android.graphics.drawable.GradientDrawable();

        drawable.setColor(0x99090B12);
        drawable.setCornerRadius(60);
        drawable.setStroke(1, 0x66FFFFFF);

        return drawable;
    }

    private void applyImmersiveMode() {

        WindowCompat.setDecorFitsSystemWindows(
                getWindow(),
                false
        );

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(
                        getWindow(),
                        getWindow().getDecorView()
                );

        controller.hide(
                WindowInsetsCompat.Type.systemBars()
        );

        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {

        super.onWindowFocusChanged(focus);

        if (focus) {
            applyImmersiveMode();
        }
    }

    private void playIndex(int index) {

        if (player == null ||
                paths == null ||
                paths.isEmpty()) {
            return;
        }

        currentIndex = index;

        File file = new File(paths.get(index));

        if (!file.exists()) {

            Toast.makeText(
                    this,
                    "ملف الفيديو غير موجود.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        titleView.setText(
                index < names.size()
                        ? names.get(index)
                        : file.getName()
        );

        counterView.setText(
                (index + 1) + " / " + paths.size()
        );

        player.setMediaItem(
                MediaItem.fromUri(Uri.fromFile(file))
        );

        player.prepare();
        player.play();
    }

    @Override
    protected void onStop() {

        super.onStop();

        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {

        if (player != null) {
            player.release();
            player = null;
        }

        super.onDestroy();
    }
}
