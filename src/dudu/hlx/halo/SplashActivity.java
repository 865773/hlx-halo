package dudu.hlx.halo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 启动页：渐变底 + 桌宠待机动画 + 名字，1.8 秒后进主页（无黑边） */
public class SplashActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean jumped = false;
    private Bitmap[] frames;
    private int idx = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        FrameLayout root = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0B1B33, 0xFF123055, 0xFF0B1B33});
        root.setBackground(bg);

        // 中间：桌宠待机帧
        final ImageView pet = new ImageView(this);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(190), dp(190));
        plp.gravity = Gravity.CENTER;
        plp.bottomMargin = dp(60);
        root.addView(pet, plp);

        // 底部文字
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, -2);
        blp.gravity = Gravity.BOTTOM;
        blp.bottomMargin = dp(90);
        root.addView(box, blp);

        TextView name = new TextView(this);
        name.setText("三楼 · 大肥鱼");
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(21);
        name.setGravity(Gravity.CENTER);
        box.addView(name);

        TextView tip = new TextView(this);
        tip.setText("正在叫醒它…");
        tip.setTextColor(0x99FFFFFF);
        tip.setTextSize(12);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, dp(8), 0, 0);
        box.addView(tip);

        TextView skip = new TextView(this);
        skip.setText("跳过 ›");
        skip.setTextColor(0x99FFFFFF);
        skip.setTextSize(12);
        skip.setPadding(dp(24), dp(20), dp(24), dp(20));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-2, -2);
        slp.gravity = Gravity.TOP | Gravity.END;
        slp.topMargin = dp(28);
        slp.rightMargin = dp(12);
        root.addView(skip, slp);
        skip.setOnClickListener(v -> go());

        setContentView(root);

        // 载入 idle 帧
        try {
            frames = new Bitmap[10];
            for (int i = 0; i < frames.length; i++) {
                frames[i] = BitmapFactory.decodeStream(getAssets().open("pet/idle_" + i + ".png"));
            }
        } catch (Throwable ignored) {}
        tick(pet);

        handler.postDelayed(this::go, 1800);
    }

    private void tick(final ImageView pet) {
        if (jumped || frames == null) return;
        Bitmap bm = frames[idx % frames.length];
        if (bm != null) pet.setImageBitmap(bm);
        idx++;
        handler.postDelayed(() -> tick(pet), 130);
    }

    private void go() {
        if (jumped) return;
        jumped = true;
        handler.removeCallbacksAndMessages(null);
        try { startActivity(new Intent(this, MainActivity.class)); } catch (Throwable ignored) {}
        finish();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onBackPressed() { go(); }
}
