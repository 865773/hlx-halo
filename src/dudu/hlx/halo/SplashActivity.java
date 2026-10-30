package dudu.hlx.halo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 启动页：干净排版——渐变底 + 桌宠 + 名字 + 细进度条 */
public class SplashActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean jumped = false;
    private Bitmap[] frames;
    private int idx = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(0xFFF6FAFF);
            getWindow().setNavigationBarColor(0xFFF6FAFF);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            View dv = getWindow().getDecorView();
            dv.setSystemUiVisibility(dv.getSystemUiVisibility()
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFF9FCFF, 0xFFEAF3FF, 0xFFFFF8F4}));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-1, -2);
        clp.gravity = Gravity.CENTER;
        clp.bottomMargin = dp(30);
        root.addView(col, clp);

        // 桌宠背后的柔和圆底
        FrameLayout petWrap = new FrameLayout(this);
        View blob = new View(this);
        GradientDrawable blobBg = new GradientDrawable();
        blobBg.setShape(GradientDrawable.OVAL);
        blobBg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        blobBg.setGradientRadius(180f);
        blobBg.setColors(new int[]{0x55A8D8FF, 0x2233A0FF, 0x00FFFFFF});
        blob.setBackground(blobBg);
        petWrap.addView(blob, new FrameLayout.LayoutParams(dp(230), dp(230)));
        ((FrameLayout.LayoutParams) blob.getLayoutParams()).gravity = Gravity.CENTER;

        final ImageView pet = new ImageView(this);
        pet.setScaleType(ImageView.ScaleType.FIT_CENTER);
        petWrap.addView(pet, new FrameLayout.LayoutParams(dp(200), dp(250)));
        ((FrameLayout.LayoutParams) pet.getLayoutParams()).gravity = Gravity.CENTER;
        col.addView(petWrap, new LinearLayout.LayoutParams(dp(240), dp(260)));

        // 三颗小气泡
        for (int bi = 0; bi < 3; bi++) {
            View bub = new View(this);
            GradientDrawable bb = new GradientDrawable();
            bb.setShape(GradientDrawable.OVAL);
            bb.setColor(bi == 0 ? 0x667FC8FF : (bi == 1 ? 0x55FFC7E0 : 0x55BBA8FF));
            bub.setBackground(bb);
            int size = dp(10 + bi * 6);
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(size, size);
            blp.gravity = Gravity.CENTER;
            blp.leftMargin = dp(bi == 0 ? -96 : (bi == 1 ? 86 : -70));
            blp.topMargin = dp(bi == 0 ? -120 : (bi == 1 ? -60 : 40));
            root.addView(bub, blp);
            bub.setAlpha(0.55f);
            bubble(bub, bi * 350);
        }

        TextView name = new TextView(this);
        name.setText("三楼 · 大肥鱼");
        name.setTextColor(0xFF23334B);
        name.setTextSize(20);
        name.setLetterSpacing(0.1f);
        name.setGravity(Gravity.CENTER);
        name.setAlpha(0f);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.topMargin = dp(18);
        col.addView(name, nlp);

        TextView sub = new TextView(this);
        sub.setText("今天也一起逛三楼呀 ~");
        sub.setTextColor(0xFF9DB0C7);
        sub.setTextSize(11);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-2, -2);
        subLp.topMargin = dp(8);
        col.addView(sub, subLp);

        // 细进度条
        final FrameLayout barWrap = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x1A2B4A73);
        bg.setCornerRadius(dp(2));
        barWrap.setBackground(bg);
        final View bar = new View(this);
        GradientDrawable fg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF7CC0FF, 0xFFA48CFF});
        fg.setCornerRadius(dp(2));
        bar.setBackground(fg);
        barWrap.addView(bar, new FrameLayout.LayoutParams(dp(24), dp(3)));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(132), dp(3));
        blp.topMargin = dp(22);
        col.addView(barWrap, blp);

        TextView skip = new TextView(this);
        skip.setText("跳过");
        skip.setTextColor(0xFF9DB0C7);
        skip.setTextSize(12);
        skip.setPadding(dp(20), dp(9), dp(20), dp(9));
        GradientDrawable sBg = new GradientDrawable();
        sBg.setColor(0x88FFFFFF);
        sBg.setCornerRadius(dp(16));
        skip.setBackground(sBg);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-2, -2);
        slp.gravity = Gravity.TOP | Gravity.END;
        slp.topMargin = dp(28);
        slp.rightMargin = dp(16);
        root.addView(skip, slp);
        skip.setOnClickListener(v -> go());

        setContentView(root);

        try {
            frames = new Bitmap[10];
            for (int i = 0; i < frames.length; i++) {
                frames[i] = BitmapFactory.decodeStream(getAssets().open("pet/idle_" + i + ".png"));
            }
        } catch (Throwable ignored) {}
        tick(pet);
        floatUp(pet);
        name.animate().alpha(1f).setStartDelay(120).setDuration(520).start();

        barWrap.post(() -> bar.animate()
                .translationX(barWrap.getWidth() - dp(24))
                .setDuration(1750)
                .start());

        handler.postDelayed(this::go, 1900);
    }

    private void tick(final ImageView pet) {
        if (jumped || frames == null) return;
        Bitmap bm = frames[idx % frames.length];
        if (bm != null) pet.setImageBitmap(bm);
        idx++;
        handler.postDelayed(() -> tick(pet), 130);
    }

    /** 小气泡慢慢往上飘，飘走再从下面回来 */
    private void bubble(final View v, int delay) {
        if (jumped) return;
        v.setTranslationY(dp(30));
        v.setAlpha(0.1f);
        v.animate().translationY(-dp(26)).alpha(0.6f).setStartDelay(delay).setDuration(1800)
                .withEndAction(() -> v.animate().alpha(0f).setDuration(400)
                        .withEndAction(() -> { if (!jumped) bubble(v, 200); }).start()).start();
    }

    private void floatUp(final View pet) {
        if (jumped) return;
        pet.animate().translationY(-dp(6)).setDuration(1300)
                .withEndAction(() -> pet.animate().translationY(0).setDuration(1300)
                        .withEndAction(() -> floatUp(pet)).start()).start();
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
