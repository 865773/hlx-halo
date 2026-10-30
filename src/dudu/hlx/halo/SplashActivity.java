package dudu.hlx.halo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** 启动页：竖屏布局里居中播放 boot.mp4（横屏视频不拉伸，加标题和遮罩），播完进主页 */
public class SplashActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean jumped = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // 视频：按宽度铺满、16:9 居中，不裁不拉伸
        final VideoView vv = new VideoView(this);
        FrameLayout.LayoutParams vlp = new FrameLayout.LayoutParams(-1, -2);
        vlp.gravity = Gravity.CENTER;
        root.addView(vv, vlp);

        // 上下渐变感：底部一块深色条 + 标题
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        bottom.setPadding(0, 0, 0, 90);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, -2);
        blp.gravity = Gravity.BOTTOM;
        root.addView(bottom, blp);

        ImageView logo = new ImageView(this);
        try {
            logo.setImageBitmap(BitmapFactory.decodeStream(getAssets().open("dafeiyu.png")));
        } catch (Throwable ignored) {}
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(96, 96);
        llp.bottomMargin = 14;
        bottom.addView(logo, llp);

        TextView name = new TextView(this);
        name.setText("三楼 · 大肥鱼");
        name.setTextColor(0xEEFFFFFF);
        name.setTextSize(20);
        name.setGravity(Gravity.CENTER);
        bottom.addView(name);

        TextView tip = new TextView(this);
        tip.setText("正在叫醒它…");
        tip.setTextColor(0x99FFFFFF);
        tip.setTextSize(12);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, 8, 0, 0);
        bottom.addView(tip);

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
                // 视频铺满宽度，高度按比例
                int vw = mp.getVideoWidth(), vh = mp.getVideoHeight();
                if (vw > 0 && vh > 0) {
                    int sw = getResources().getDisplayMetrics().widthPixels;
                    int h = (int) (sw * (vh * 1.0f / vw));
                    ViewGroup.LayoutParams lp2 = vv.getLayoutParams();
                    lp2.width = sw;
                    lp2.height = h;
                    vv.setLayoutParams(lp2);
                }
                vv.start();
            });
            vv.setOnCompletionListener(mp -> go());
            vv.setOnErrorListener((mp, what, extra) -> { go(); return true; });
        } catch (Throwable t) {
            go();
        }
        handler.postDelayed(this::go, 9000);
    }

    private void go() {
        if (jumped) return;
        jumped = true;
        handler.removeCallbacksAndMessages(null);
        try { startActivity(new Intent(this, MainActivity.class)); } catch (Throwable ignored) {}
        finish();
    }

    @Override
    public void onBackPressed() { go(); }
}
