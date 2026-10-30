package dudu.hlx.halo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** 启动页：播放 boot.mp4，播完或点击跳过进入主页 */
public class SplashActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean jumped = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        final VideoView vv = new VideoView(this);
        root.addView(vv, new FrameLayout.LayoutParams(-1, -1));

        TextView skip = new TextView(this);
        skip.setText("跳过 ›");
        skip.setTextColor(0xCCFFFFFF);
        skip.setTextSize(13);
        skip.setPadding(30, 24, 30, 24);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-2, -2);
        slp.gravity = Gravity.TOP | Gravity.END;
        slp.topMargin = 40;
        slp.rightMargin = 16;
        root.addView(skip, slp);
        skip.setOnClickListener(v -> go());

        setContentView(root);

        try {
            File f = new File(getCacheDir(), "boot.mp4");
            if (!f.exists() || f.length() < 10000) {
                InputStream in = getAssets().open("boot.mp4");
                FileOutputStream out = new FileOutputStream(f);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close();
                in.close();
            }
            vv.setVideoPath(f.getAbsolutePath());
            vv.setOnPreparedListener((MediaPlayer mp) -> {
                mp.setLooping(false);
                vv.start();
            });
            vv.setOnCompletionListener(mp -> go());
            vv.setOnErrorListener((mp, what, extra) -> { go(); return true; });
        } catch (Throwable t) {
            go();
        }
        // 兜底：8 秒还没播完也进主页
        handler.postDelayed(this::go, 8000);
    }

    private void go() {
        if (jumped) return;
        jumped = true;
        handler.removeCallbacksAndMessages(null);
        try {
            startActivity(new Intent(this, MainActivity.class));
        } catch (Throwable ignored) {}
        finish();
    }

    @Override
    public void onBackPressed() {
        go();
    }
}
