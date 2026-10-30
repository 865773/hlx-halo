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
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 启动页：深色渐变 + 光晕 + 桌宠呼吸 + 跳跃加载点 */
public class SplashActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean jumped = false;
    private Bitmap[] frames;
    private int idx = 0;
    private int dot = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF050B18, 0xFF0E2A4A, 0xFF071320}));

        // 光晕
        View glow = new View(this);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        gd.setGradientRadius(320f);
        gd.setColors(new int[]{0x553FA8FF, 0x113FA8FF, 0x00000000});
        glow.setBackground(gd);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(dp(420), dp(420));
        glp.gravity = Gravity.CENTER;
        glp.bottomMargin = dp(50);
        root.addView(glow, glp);

        // 桌宠
        final ImageView pet = new ImageView(this);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(200), dp(200));
        plp.gravity = Gravity.CENTER;
        plp.bottomMargin = dp(70);
        root.addView(pet, plp);

        // 文字块
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, -2);
        blp.gravity = Gravity.BOTTOM;
        blp.bottomMargin = dp(96);
        root.addView(box, blp);

        TextView name = new TextView(this);
        name.setText("三楼 · 大肥鱼");
        name.setTextColor(Color.WHITE);
        name.setTextSize(22);
        name.setLetterSpacing(0.08f);
        name.setGravity(Gravity.CENTER);
        name.setAlpha(0f);
        box.addView(name);

        final TextView tip = new TextView(this);
        tip.setText("正在叫醒它");
        tip.setTextColor(0x99BCD7FF);
        tip.setTextSize(12);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, dp(10), 0, 0);
        box.addView(tip);

        TextView skip = new TextView(this);
        skip.setText("跳过");
        skip.setTextColor(0x88FFFFFF);
        skip.setTextSize(12);
        skip.setPadding(dp(26), dp(18), dp(26), dp(18));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-2, -2);
        slp.gravity = Gravity.TOP | Gravity.END;
        slp.topMargin = dp(30);
        slp.rightMargin = dp(10);
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
        floatUp(pet);

        name.animate().alpha(1f).setStartDelay(200).setDuration(600).start();
        dots(tip);
        skip.animate().alpha(0.9f).setDuration(800).start();

        handler.postDelayed(this::go, 2000);
    }

    private void tick(final ImageView pet) {
        if (jumped || frames == null) return;
        Bitmap bm = frames[idx % frames.length];
        if (bm != null) pet.setImageBitmap(bm);
        idx++;
        handler.postDelayed(() -> tick(pet), 130);
    }

    /** 轻微上下浮动 */
    private void floatUp(final View pet) {
        if (jumped) return;
        pet.animate().translationY(-dp(8)).setDuration(1100)
                .withEndAction(() -> pet.animate().translationY(0).setDuration(1100)
                        .withEndAction(() -> floatUp(pet)).start()).start();
    }

    /** 加载点跳动 */
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
