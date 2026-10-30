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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 启动页：浅色渐变 + 柔光 + 桌宠浮动 */
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
            getWindow().setStatusBarColor(0xFFF3F8FF);
            getWindow().setNavigationBarColor(0xFFF3F8FF);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            View dv = getWindow().getDecorView();
            dv.setSystemUiVisibility(dv.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFF7FBFF, 0xFFDFEEFF, 0xFFF4F1FF}));

        // 柔光
        View glow = new View(this);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        gd.setGradientRadius(300f);
        gd.setColors(new int[]{0x66A8D4FF, 0x22A8D4FF, 0x00FFFFFF});
        glow.setBackground(gd);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(dp(400), dp(400));
        glp.gravity = Gravity.CENTER;
        glp.bottomMargin = dp(40);
        root.addView(glow, glp);

        // 桌宠
        final ImageView pet = new ImageView(this);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(190), dp(190));
        plp.gravity = Gravity.CENTER;
        plp.bottomMargin = dp(56);
        root.addView(pet, plp);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, -2);
        blp.gravity = Gravity.BOTTOM;
        blp.bottomMargin = dp(92);
        root.addView(box, blp);

        TextView name = new TextView(this);
        name.setText("三楼 · 大肥鱼");
        name.setTextColor(0xFF1B2B44);
        name.setTextSize(21);
        name.setLetterSpacing(0.06f);
        name.setGravity(Gravity.CENTER);
        name.setAlpha(0f);
        box.addView(name);

        final TextView tip = new TextView(this);
        tip.setText("正在叫醒它");
        tip.setTextColor(0xFF7A8DA6);
        tip.setTextSize(12);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, dp(10), 0, 0);
        box.addView(tip);

        TextView skip = new TextView(this);
        skip.setText("跳过");
        skip.setTextColor(0xFF8FA3BC);
        skip.setTextSize(12);
        skip.setPadding(dp(26), dp(16), dp(26), dp(16));
        GradientDrawable sBg = new GradientDrawable();
        sBg.setColor(0x33FFFFFF);
        sBg.setCornerRadius(dp(20));
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

        name.animate().alpha(1f).setStartDelay(150).setDuration(600).start();
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
        pet.animate().translationY(-dp(7)).setDuration(1200)
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
