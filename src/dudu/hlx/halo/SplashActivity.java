package dudu.hlx.halo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 启动页：浅色渐变 + 白色圆角卡片 + 桌宠（保持原始比例）+ 加载点 */
public class SplashActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean jumped = false;
    private Bitmap[] frames;
    private int idx = 0;
    private int dot = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(0xFFF6F9FF);
            getWindow().setNavigationBarColor(0xFFF6F9FF);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            View dv = getWindow().getDecorView();
            dv.setSystemUiVisibility(dv.getSystemUiVisibility()
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFF8FBFF, 0xFFE9F2FF, 0xFFF7F4FF}));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-1, -2);
        clp.gravity = Gravity.CENTER;
        clp.bottomMargin = dp(40);
        root.addView(center, clp);

        // 白色圆角卡片
        FrameLayout card = new FrameLayout(this);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xCCFFFFFF);
        cardBg.setCornerRadius(dp(34));
        cardBg.setStroke(dp(1), 0x33A8C8FF);
        card.setBackground(cardBg);
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(6));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(dp(210), dp(250));
        center.addView(card, cardLp);

        // 桌宠：保持 241:300 比例，不拉伸
        final ImageView pet = new ImageView(this);
        pet.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pet.setAdjustViewBounds(true);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(168), dp(209));
        plp.gravity = Gravity.CENTER;
        card.addView(pet, plp);

        TextView name = new TextView(this);
        name.setText("三楼 · 大肥鱼");
        name.setTextColor(0xFF20324D);
        name.setTextSize(19);
        name.setLetterSpacing(0.08f);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.topMargin = dp(26);
        center.addView(name, nlp);

        final TextView tip = new TextView(this);
        tip.setText("正在叫醒它");
        tip.setTextColor(0xFF8DA0B8);
        tip.setTextSize(12);
        tip.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = dp(10);
        center.addView(tip, tlp);

        TextView skip = new TextView(this);
        skip.setText("跳过");
        skip.setTextColor(0xFF9AAEC6);
        skip.setTextSize(12);
        skip.setPadding(dp(22), dp(10), dp(22), dp(10));
        GradientDrawable sBg = new GradientDrawable();
        sBg.setColor(0x99FFFFFF);
        sBg.setCornerRadius(dp(18));
        sBg.setStroke(dp(1), 0x22A8C8FF);
        skip.setBackground(sBg);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-2, -2);
        slp.gravity = Gravity.TOP | Gravity.END;
        slp.topMargin = dp(30);
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
        dots(tip);
        handler.postDelayed(this::go, 1900);
    }

    private void tick(final ImageView pet) {
        if (jumped || frames == null) return;
        Bitmap bm = frames[idx % frames.length];
        if (bm != null) pet.setImageBitmap(bm);
        idx++;
        handler.postDelayed(() -> tick(pet), 130);
    }

    private void floatUp(final View pet) {
        if (jumped) return;
        pet.animate().translationY(-dp(6)).setDuration(1200)
                .withEndAction(() -> pet.animate().translationY(0).setDuration(1200)
                        .withEndAction(() -> floatUp(pet)).start()).start();
    }

    private void dots(final TextView tip) {
        if (jumped) return;
        dot = (dot + 1) % 4;
        StringBuilder sb = new StringBuilder("正在叫醒它");
        for (int i = 0; i < dot; i++) sb.append("·");
        tip.setText(sb.toString());
        handler.postDelayed(() -> dots(tip), 420);
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
