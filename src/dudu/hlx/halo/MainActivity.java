package dudu.hlx.halo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * 三楼·大肥鱼 主界面
 * 三个板块：主页 / 人设 / 设置，所有子页面走页面栈，返回键退回上一页
 */
public class MainActivity extends Activity {

    // 页面 id
    private static final int P_HOME = 0, P_PERSONA = 1, P_SETTINGS = 2;
    private static final int P_PERM = 10, P_AICONFIG = 11, P_IMG = 12, P_MEMORY = 13,
            P_ABOUT = 14, P_GUIDE = 15, P_LOG = 16, P_USERMEM = 17,
            P_WINDOW = 18, P_USERMEM_DETAIL = 19;

    private static final int C_PRIMARY = 0xFF2F6BFF;
    private static final int C_SECONDARY = 0xFF17C3B2;
    private static final int C_BG = 0xFFF5F7FC;
    private static final int C_SURFACE = 0xFFFFFFFF;
    private static final int C_SURFACE_VAR = 0xFFEEF2FA;
    private static final int C_TEXT = 0xFF17203A;
    private static final int C_TEXT2 = 0xFF7986A6;
    private static final int C_OUTLINE = 0xFFE3E9F5;
    private static final int C_WARN = 0xFFF59E0B;
    private static final int C_DANGER = 0xFFD64545;

    private SharedPreferences sp;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout content;
    private TextView tabHome, tabPersona, tabSettings;
    private LinearLayout tabBar;
    private final ArrayList<Integer> stack = new ArrayList<>();
    private long curMemUid = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("fish", MODE_PRIVATE);
        applyDebugFlag();
        getWindow().setStatusBarColor(C_BG);
        getWindow().setNavigationBarColor(C_SURFACE);
        if (Build.VERSION.SDK_INT >= 23)
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        if (sp.getString("hlx_key", "").isEmpty()) { showLogin(); return; }
        buildShell();
        reportUsage();
        autoRestoreFish();
        autoSignInIfNeeded();
        handler.postDelayed(() -> { try { checkUpdate(false); } catch (Exception ignored) {} }, 1500);
        if (sp.getString("ai_key", "").isEmpty() || !sp.getBoolean("guide_done", false)) {
            sp.edit().putInt("guide_step", 0).apply();
            showPage(P_GUIDE, false);
        } else {
            showPage(P_HOME, true);
        }
    }

    // ==================== 外壳 ====================

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        View line = new View(this);
        line.setBackgroundColor(C_OUTLINE);
        root.addView(line, new LinearLayout.LayoutParams(-1, 1));

        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackgroundColor(C_SURFACE);
        tabBar.setPadding(0, dp(6), 0, dp(6));
        tabHome = tab("🏠", "主页");
        tabPersona = tab("🐟", "人设");
        tabSettings = tab("⚙️", "设置");
        tabBar.addView(tabHome, new LinearLayout.LayoutParams(0, -1, 1f));
        tabBar.addView(tabPersona, new LinearLayout.LayoutParams(0, -1, 1f));
        tabBar.addView(tabSettings, new LinearLayout.LayoutParams(0, -1, 1f));
        root.addView(tabBar, new LinearLayout.LayoutParams(-1, dp(60)));
        setContentView(root);

        tabHome.setOnClickListener(v -> showPage(P_HOME, true));
        tabPersona.setOnClickListener(v -> showPage(P_PERSONA, true));
        tabSettings.setOnClickListener(v -> showPage(P_SETTINGS, true));
    }

    private TextView tab(String emoji, String name) {
        TextView t = new TextView(this);
        t.setText(emoji + "\n" + name);
        t.setTextSize(11);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(C_TEXT2);
        return t;
    }

    /** 显示页面；tab=true 表示切换底部板块（清空栈） */
    private int curTab = 0;
    private int animDir = 1;

    private void showPage(int id, boolean isTab) {
        if (isTab) curTab = id;
        if (stack.isEmpty() || stack.get(stack.size() - 1) != id) stack.add(id);

        TextView[] tabs = {tabHome, tabPersona, tabSettings};
        int active = id;
        if (id >= 10) {
            // 子页面：高亮所属 tab
            active = (id == P_PERM) ? P_SETTINGS : id;
        }
        for (int i = 0; i < 3; i++) {
            tabs[i].setTextColor(i == id ? C_PRIMARY : C_TEXT2);
            tabs[i].setTypeface(i == id ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }

        content.removeAllViews();
        View v;
        switch (id) {
            case P_HOME: v = buildHome(true); break;
            case P_PERSONA: v = buildPersona(); break;
            case P_SETTINGS: v = buildSettings(); break;
            case P_PERM: v = buildPermPage(); break;
            case P_AICONFIG: v = buildAiConfig(true); break;
            case P_IMG: v = buildImgConfig(); break;
            case P_MEMORY: v = buildMemoryPage(); break;
            case P_USERMEM: v = buildUserMemList(); break;
            case P_ABOUT: v = buildAbout(); break;
            case P_GUIDE: v = buildGuide(); break;
            case P_LOG: v = buildLog(); break;
            case P_WINDOW: v = buildWindowPage(); break;
            case P_USERMEM_DETAIL: v = buildUserMemDetail(curMemUid); break;
            default: v = buildHome(true);
        }
        content.addView(v);
        v.setAlpha(0.35f);
        v.setTranslationX(animDir * dp(10));
        v.setScaleX(0.985f);
        v.setScaleY(0.985f);
        v.animate().alpha(1f).translationX(0f).scaleX(1f).scaleY(1f)
                .setDuration(420)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从系统设置回来时刷新引导页，让按钮状态跟着变
        if (content != null && stack.size() > 0 && stack.get(stack.size() - 1) == P_GUIDE) {
            showPage(P_GUIDE, false);
        }
    }

    private float swipeX = 0, swipeY = 0;
    private long swipeT = 0;

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ev.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
            swipeX = ev.getX(); swipeY = ev.getY(); swipeT = System.currentTimeMillis();
        } else if (ev.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
            float dx = ev.getX() - swipeX, dy = ev.getY() - swipeY;
            long dt = System.currentTimeMillis() - swipeT;
            if (Math.abs(dx) > 150 && Math.abs(dx) > Math.abs(dy) * 1.6f && dt < 700) {
                int[] order = {P_HOME, P_PERSONA, P_SETTINGS};
                int idx = 0;
                for (int i = 0; i < order.length; i++) if (order[i] == curTab) idx = i;
                idx = dx < 0 ? (idx + 1) % 3 : (idx + 2) % 3;
                animDir = dx < 0 ? 1 : -1;
                showPage(order[idx], true);
                return true;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onBackPressed() {
        if (stack.size() > 1) {
            stack.remove(stack.size() - 1);
            int prev = stack.get(stack.size() - 1);
            stack.remove(stack.size() - 1);
            animDir = -1;
            showPage(prev, false);
            return;
        }
        super.onBackPressed();
    }

    // ==================== 主页 ====================

    private View buildHome(boolean withTab) {
        final ScrollView sv = new ScrollView(this);
        final float[] pullStart = {0};
        final boolean[] pulling = {false};
        sv.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                pulling[0] = sv.getScrollY() == 0;
                pullStart[0] = e.getY();
            } else if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP && pulling[0]) {
                if (e.getY() - pullStart[0] > 220) {
                    toast("刷新中…");
                    final View cv = content;
                    cv.animate().translationY(dp(30)).alpha(0.7f).setDuration(160)
                            .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                            .withEndAction(() -> {
                                showPage(P_HOME, true);
                                cv.setTranslationY(dp(18));
                                cv.setAlpha(0.85f);
                                cv.animate().translationY(0f).alpha(1f).setDuration(320)
                                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f))
                                        .start();
                                toast("已刷新");
                            }).start();
                }
                pulling[0] = false;
            }
            return false;
        });
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(20));
        sv.addView(root);

        // 顶部状态卡
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{C_PRIMARY, 0xFF5B8CFF});
        hg.setCornerRadius(dp(22));
        hero.setBackground(hg);
        hero.setPadding(dp(20), dp(18), dp(20), dp(16));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        ImageView av = new ImageView(this);
        av.setBackground(round(0x33FFFFFF, 40));
        av.setPadding(dp(5), dp(5), dp(5), dp(5));
        loadAccountAvatar(av);
        top.addView(av, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout who = new LinearLayout(this);
        who.setOrientation(LinearLayout.VERTICAL);
        who.setPadding(dp(12), 0, 0, 0);
        TextView n = new TextView(this);
        n.setText(sp.getString("hlx_nick", "葫芦侠用户"));
        n.setTextSize(16);
        n.setTextColor(Color.WHITE);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        who.addView(n);
        TextView s = new TextView(this);
        s.setText("Lv." + sp.getInt("hlx_level", 0) + " · UID " + sp.getLong("hlx_uid", 0));
        s.setTextSize(11);
        s.setTextColor(0xCCFFFFFF);
        s.setPadding(0, dp(3), 0, 0);
        who.addView(s);
        top.addView(who, new LinearLayout.LayoutParams(0, -2, 1f));

        boolean on = sp.getBoolean("fish_on", false);
        TextView badge = new TextView(this);
        badge.setText(on ? "● 运行中" : "○ 已停止");
        badge.setTextSize(11);
        badge.setTextColor(Color.WHITE);
        badge.setBackground(round(on ? 0x3316A34A : 0x33FFFFFF, 12));
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        top.addView(badge);
        hero.addView(top);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setPadding(0, dp(14), 0, 0);
        ensureToday();
        stats.addView(heroStat("点赞", sp.getInt("today_like", 0)));
        stats.addView(heroStat("评论", sp.getInt("today_comment", 0)));
        stats.addView(heroStat("收藏", sp.getInt("today_favorite", 0)));
        stats.addView(heroStat("关注", sp.getInt("today_follow", 0)));
        stats.addView(heroStat("打赏", sp.getInt("today_tip", 0)));
        stats.addView(heroStat("发帖", sp.getInt("today_post", 0)));
        hero.addView(stats);

        TextView moodLine = new TextView(this);
        moodLine.setText("心情 " + moodLabel() + "   ·   亲密度基准 " + avgFavor());
        moodLine.setTextSize(11);
        moodLine.setTextColor(0xCCFFFFFF);
        moodLine.setPadding(0, dp(10), 0, 0);
        hero.addView(moodLine);

        root.addView(hero, lp(0, 0, 0, 14));

        // 开关卡
        LinearLayout ctrl = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = new TextView(this);
        t1.setText("大肥鱼");
        t1.setTextSize(16);
        t1.setTextColor(C_TEXT);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(t1);
        TextView t2 = new TextView(this);
        t2.setText(on ? sp.getString("state_text", "正在决定要做什么…")
                : "打开后它会在后台自己思考、自己去逛");
        t2.setTextSize(12);
        t2.setTextColor(C_TEXT2);
        t2.setPadding(0, dp(4), dp(8), 0);
        texts.addView(t2);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        final Switch sw = new Switch(this);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener((b, checked) -> {
            if (checked && !readyToRun()) {
                sw.setChecked(false);
                sp.edit().putInt("guide_step", 0).apply();
                showPage(P_GUIDE, false);
                return;
            }
            if (checked) tryStart();
            else {
                sp.edit().putBoolean("fish_on", false).apply();
                stopService(new Intent(this, HlxFishService.class));
                showPage(P_HOME, true);
            }
        });
        row.addView(sw);
        ctrl.addView(row);

        if (on) {
            ctrl.addView(divider(dp(12)));
            Button go = new Button(this);
            go.setText("现在让它干点事");
            go.setTextSize(13);
            go.setTextColor(C_PRIMARY);
            go.setBackground(round(0x142F6BFF, 20));
            go.setOnClickListener(v -> {
                Intent i = new Intent(this, HlxFishService.class);
                i.setAction(HlxFishService.ACT_ROAM);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
                else startService(i);
                toast("去问它想干嘛了 🐟");
            });
            ctrl.addView(go, new LinearLayout.LayoutParams(-1, dp(42)));
        }


        root.addView(ctrl, lp(0, 0, 0, 14));

        // 最近动态（卡片）
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView rt = new TextView(this);
        rt.setText("最近动态");
        rt.setTextSize(15);
        rt.setTextColor(C_TEXT);
        rt.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(rt, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView more = new TextView(this);
        more.setText("全部 ›");
        more.setTextSize(12);
        more.setTextColor(C_PRIMARY);
        more.setOnClickListener(v -> showPage(P_LOG, false));
        head.addView(more);
        root.addView(head, lp(4, 0, 4, 10));

        List<String[]> cards = readCards(6);
        if (cards.isEmpty()) {
            LinearLayout empty = card();
            TextView e = new TextView(this);
            e.setText("还没有动态。\n打开上面的开关，它就会开始自己逛、自己决定做什么。");
            e.setTextSize(12);
            e.setTextColor(C_TEXT2);
            e.setLineSpacing(dp(4), 1f);
            empty.addView(e);
            root.addView(empty);
        } else {
            for (String[] c : cards) root.addView(actCard(c), lp(0, 0, 0, 10));
        }
        return sv;
    }

    private View heroStat(String name, int v) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        TextView tv = new TextView(this);
        tv.setText(String.valueOf(v));
        tv.setTextSize(17);
        tv.setTextColor(Color.WHITE);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        c.addView(tv);
        TextView tn = new TextView(this);
        tn.setText(name);
        tn.setTextSize(10);
        tn.setTextColor(0xAAFFFFFF);
        tn.setGravity(Gravity.CENTER);
        c.addView(tn);
        c.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        return c;
    }

    /** 一条动态卡片 */
    private View actCard(String[] c) {
        LinearLayout card = card();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        if ("B".equals(c[0])) {
            LinearLayout row0 = new LinearLayout(this);
            row0.setOrientation(LinearLayout.HORIZONTAL);
            row0.setGravity(Gravity.CENTER_VERTICAL);
            TextView tm = new TextView(this);
            tm.setText(c[1]);
            tm.setTextSize(11);
            tm.setTextColor(C_TEXT2);
            row0.addView(tm, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView bd = new TextView(this);
            String bn = c[2] == null ? "" : c[2];
            bd.setText("🔀 换到板块「" + bn + "」");
            bd.setTextSize(12);
            bd.setTextColor(C_SECONDARY);
            bd.setBackground(round(0x1417C3B2, 10));
            bd.setPadding(dp(8), dp(3), dp(8), dp(3));
            row0.addView(bd);
            card.addView(row0);
            return card;
        }
        boolean isRound = "R".equals(c[0]);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView time = new TextView(this);
        time.setText(c[1]);
        time.setTextSize(11);
        time.setTextColor(C_TEXT2);
        head.addView(time, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView tag = new TextView(this);
        if (isRound) {
            tag.setText("第 " + c[2] + " 轮 · " + intentLabel(c[3]));
        } else {
            tag.setText(c[3]);
        }
        tag.setTextSize(11);
        tag.setTextColor(isRound ? C_SECONDARY : C_PRIMARY);
        tag.setBackground(round(isRound ? 0x1417C3B2 : 0x142F6BFF, 10));
        tag.setPadding(dp(8), dp(3), dp(8), dp(3));
        head.addView(tag);
        card.addView(head);

        if (isRound) {
            if (c[4] != null && !c[4].isEmpty()) {
                TextView board = new TextView(this);
                board.setText("进入板块：「" + c[4] + "」");
                board.setTextSize(13);
                board.setTextColor(C_TEXT);
                board.setTypeface(Typeface.DEFAULT_BOLD);
                board.setPadding(0, dp(8), 0, 0);
                card.addView(board);
            }
            if (c[5] != null && !c[5].isEmpty()) {
                TextView think = new TextView(this);
                think.setText("💭 " + c[5]);
                think.setTextSize(11);
                think.setTextColor(C_TEXT2);
                think.setPadding(0, dp(6), 0, 0);
                card.addView(think);
            }
            return card;
        }

        TextView title = new TextView(this);
        title.setText(c[2]);
        title.setTextSize(14);
        title.setTextColor(C_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLineSpacing(dp(3), 1f);
        title.setPadding(0, dp(8), 0, 0);
        card.addView(title);

        if (c[5] != null && !c[5].isEmpty()) {
            TextView au = new TextView(this);
            au.setText("@" + c[5]);
            au.setTextSize(11);
            au.setTextColor(C_TEXT2);
            au.setPadding(0, dp(4), 0, 0);
            card.addView(au);
        }

        if (c[4] != null && !c[4].isEmpty()) {
            TextView reply = new TextView(this);
            reply.setText(c[4]);
            reply.setTextSize(13);
            reply.setTextColor(C_TEXT);
            reply.setLineSpacing(dp(4), 1f);
            reply.setBackground(round(C_SURFACE_VAR, 12));
            reply.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.topMargin = dp(10);
            card.addView(reply, rp);
        }

        if (c.length > 6 && c[6] != null && !c[6].isEmpty()) {
            TextView th = new TextView(this);
            th.setText("💭 " + c[6]);
            th.setTextSize(11);
            th.setTextColor(C_TEXT2);
            th.setPadding(0, dp(8), 0, 0);
            card.addView(th);
        }
        return card;
    }

    private String intentLabel(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String x : s.split("\\+")) {
            String one = "roam".equals(x) ? "逛帖子"
                    : "post".equals(x) ? "发帖"
                    : "signin".equals(x) ? "签到"
                    : "rest".equals(x) ? "休息" : x;
            if (sb.length() > 0) sb.append(" + ");
            sb.append(one);
        }
        return sb.toString();
    }

    // ==================== 人设 ====================

    private View buildPersona() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(20));
        sv.addView(root);
        root.addView(pageTitle("人设"), lp(0, 0, 0, 12));

        // 基本信息
        LinearLayout basic = card();
        basic.addView(sectionTitle("基本信息"));

        basic.addView(fieldLabel("名字"));
        final EditText etName = input("它叫什么", InputType.TYPE_CLASS_TEXT);
        etName.setText(sp.getString("fish_name", "大肥鱼"));
        basic.addView(etName, lp(0, 8, 0, 14));

        // 主人 ID 不再显示在界面，内部固定（见 ensureOwner）
        final EditText etOwner = input("", InputType.TYPE_CLASS_NUMBER);
        etOwner.setText("0");
        etOwner.setVisibility(View.GONE);

        basic.addView(fieldLabel("标签（逗号分隔）"));
        final EditText etTags = input("仿真,真人感,甜美,粘人", InputType.TYPE_CLASS_TEXT);
        etTags.setText(sp.getString("fish_tags", "仿真,真人感,甜美,粘人"));
        basic.addView(etTags, lp(0, 8, 0, 16));

        Button save = new Button(this);
        save.setText("保存");
        save.setTextColor(Color.WHITE);
        save.setTextSize(14);
        save.setBackground(round(C_PRIMARY, 22));
        save.setOnClickListener(v -> {
            sp.edit()
                    .putString("fish_name", etName.getText().toString().trim())
                    .putString("fish_tags", etTags.getText().toString().trim())
                    .apply();
            toast("已保存");
            showPage(P_PERSONA, true);
        });
        basic.addView(save, new LinearLayout.LayoutParams(-1, dp(46)));
        root.addView(basic, lp(0, 0, 0, 14));

        // 形象（文字，用于文生图）
        LinearLayout lookCard = card();
        LinearLayout lh = new LinearLayout(this);
        lh.setOrientation(LinearLayout.HORIZONTAL);
        lh.setGravity(Gravity.CENTER_VERTICAL);
        TextView lt = new TextView(this);
        lt.setText("形象");
        lt.setTextSize(15);
        lt.setTextColor(C_TEXT);
        lt.setTypeface(Typeface.DEFAULT_BOLD);
        lh.addView(lt, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView ldef = new TextView(this);
        ldef.setText("恢复默认");
        ldef.setTextSize(12);
        ldef.setTextColor(C_PRIMARY);
        ldef.setOnClickListener(v -> {
            sp.edit().remove("look").apply();
            toast("已恢复默认形象");
            showPage(P_PERSONA, true);
        });
        lh.addView(ldef);
        lookCard.addView(lh);

        TextView ltip = new TextView(this);
        ltip.setText("用文字描述它的样子，发帖配图时会按这段描述生成图片");
        ltip.setTextSize(11);
        ltip.setTextColor(C_TEXT2);
        ltip.setPadding(0, dp(6), 0, dp(10));
        lookCard.addView(ltip);

        final EditText etLook = new EditText(this);
        etLook.setText(sp.getString("look", FishBrain.LOOK_DEFAULT));
        etLook.setTextSize(12);
        etLook.setTextColor(C_TEXT);
        etLook.setBackground(round(C_SURFACE_VAR, 14));
        etLook.setPadding(dp(14), dp(12), dp(14), dp(12));
        etLook.setGravity(Gravity.TOP);
        etLook.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etLook.setVerticalScrollBarEnabled(true);
        etLook.setOnTouchListener((v, e) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        lookCard.addView(etLook, new LinearLayout.LayoutParams(-1, dp(110)));

        Button saveL = new Button(this);
        saveL.setText("保存形象");
        saveL.setTextColor(Color.WHITE);
        saveL.setTextSize(13);
        saveL.setBackground(round(C_SECONDARY, 22));
        saveL.setOnClickListener(v -> {
            String s = etLook.getText().toString().trim();
            if (s.isEmpty()) sp.edit().remove("look").apply();
            else sp.edit().putString("look", s).apply();
            toast("形象已保存");
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, dp(44));
        slp.topMargin = dp(12);
        lookCard.addView(saveL, slp);
        root.addView(lookCard, lp(0, 0, 0, 14));

        // 人格设定
        LinearLayout pc = card();
        LinearLayout ph = new LinearLayout(this);
        ph.setOrientation(LinearLayout.HORIZONTAL);
        ph.setGravity(Gravity.CENTER_VERTICAL);
        TextView pt = new TextView(this);
        pt.setText("人格设定");
        pt.setTextSize(15);
        pt.setTextColor(C_TEXT);
        pt.setTypeface(Typeface.DEFAULT_BOLD);
        ph.addView(pt, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView pdef = new TextView(this);
        pdef.setText("恢复默认");
        pdef.setTextSize(12);
        pdef.setTextColor(C_PRIMARY);
        pdef.setOnClickListener(v -> {
            sp.edit().remove("persona").apply();
            toast("已恢复默认人格");
            showPage(P_PERSONA, true);
        });
        ph.addView(pdef);
        pc.addView(ph);

        final EditText etPer = new EditText(this);
        etPer.setText(sp.getString("persona", FishBrain.PERSONA_DEFAULT));
        etPer.setTextSize(12);
        etPer.setTextColor(C_TEXT);
        etPer.setBackground(round(C_SURFACE_VAR, 14));
        etPer.setPadding(dp(14), dp(12), dp(14), dp(12));
        etPer.setGravity(Gravity.TOP);
        etPer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etPer.setVerticalScrollBarEnabled(true);
        etPer.setOnTouchListener((v, e) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(-1, dp(260));
        elp.topMargin = dp(12);
        pc.addView(etPer, elp);

        Button saveP = new Button(this);
        saveP.setText("保存人格");
        saveP.setTextColor(Color.WHITE);
        saveP.setTextSize(13);
        saveP.setBackground(round(C_SECONDARY, 22));
        saveP.setOnClickListener(v -> {
            String s = etPer.getText().toString().trim();
            if (s.isEmpty()) sp.edit().remove("persona").apply();
            else sp.edit().putString("persona", s).apply();
            toast("人格已保存");
        });
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(-1, dp(44));
        sp2.topMargin = dp(12);
        pc.addView(saveP, sp2);
        root.addView(pc);

        return sv;
    }

    // ==================== 设置 ====================

    private View buildSettings() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(20));
        sv.addView(root);
        root.addView(pageTitle("设置"), lp(0, 0, 0, 12));

        // AI 配置入口
        LinearLayout c1 = card();
        c1.addView(entry("🧭", "新手引导", "一步步配置它需要的东西", v -> {
            sp.edit().putInt("guide_step", 0).apply();
            showPage(P_GUIDE, false);
        }));
        c1.addView(divider(dp(10)));
        c1.addView(entry("🧠", "AI 配置", "对话模型、文生图接口", v -> showPage(P_AICONFIG, false)));
        c1.addView(divider(dp(10)));
        c1.addView(entry("🔐", "权限设置", "允许它做哪些事", v -> showPage(P_PERM, false)));
        c1.addView(divider(dp(10)));
        c1.addView(entry("🪟", "悬浮窗", "挂件大小 / 透明度", v -> showPage(P_WINDOW, false)));
        c1.addView(divider(dp(10)));
        c1.addView(entry("💾", "记忆", "它记住的人和事", v -> showPage(P_MEMORY, false)));
        root.addView(c1, lp(0, 0, 0, 14));

        // 节奏
        LinearLayout c2 = card();
        c2.addView(sectionTitle("🏃 行为节奏"));
        c2.addView(numberRow("唤醒间隔（分钟，最低5）", "interval_min", 20));
        c2.addView(numberRow("每次最多互动几个帖", "max_steps", 4));
        TextView gapTip = new TextView(this);
        gapTip.setText("动作间隔：0～15 秒随机（评论、回复、逛帖子、发帖都一样，不用调）");
        gapTip.setTextSize(12);
        gapTip.setTextColor(C_TEXT2);
        gapTip.setPadding(0, dp(8), 0, dp(8));
        c2.addView(gapTip);
        c2.addView(numberRow("每日发帖上限", "post_daily_limit", 2));
        c2.addView(numberRow("每日打赏上限", "tip_daily_limit", 3));
        c2.addView(numberRow("打赏所需好感(0-100)", "tip_favor_min", 90));
        c2.addView(numberRow("通用记忆上限(字)", "memory_max_chars", 1500));
        c2.addView(numberRow("用户记忆上限(字)", "user_mem_max_chars", 800));
        c2.addView(numberRow("生活记录刷新(分钟)", "life_refresh_min", 60));
        root.addView(c2, lp(0, 0, 0, 14));

        // 板块
        LinearLayout c3 = card();
        c3.addView(sectionTitle("🗂 逛哪些板块"));
        final TextView scope = new TextView(this);
        scope.setText(scopeDesc());
        scope.setTextSize(12);
        scope.setTextColor(C_TEXT2);
        scope.setPadding(0, dp(8), 0, dp(12));
        c3.addView(scope);
        Button pick = new Button(this);
        pick.setText("选择板块");
        pick.setTextColor(Color.WHITE);
        pick.setBackground(round(C_SECONDARY, 22));
        pick.setOnClickListener(v -> pickBoards(scope));
        c3.addView(pick, new LinearLayout.LayoutParams(-1, dp(46)));
        root.addView(c3, lp(0, 0, 0, 14));

        // 账号
        LinearLayout c4 = card();
        c4.addView(sectionTitle("👤 账号"));
        TextView acc = new TextView(this);
        acc.setText(sp.getString("hlx_nick", "") + "  (UID " + sp.getLong("hlx_uid", 0) + ")");
        acc.setTextSize(13);
        acc.setTextColor(C_TEXT);
        acc.setPadding(0, dp(6), 0, dp(12));
        c4.addView(acc);
        Button out = new Button(this);
        out.setText("退出登录 / 切换账号");
        out.setTextSize(13);
        out.setTextColor(C_DANGER);
        out.setBackground(round(0x14D64545, 22));
        out.setOnClickListener(v -> confirmLogout());
        c4.addView(out, new LinearLayout.LayoutParams(-1, dp(46)));
        root.addView(c4, lp(0, 0, 0, 14));

        // 关于
        LinearLayout c5 = card();
        c5.addView(entry("ℹ️", "关于", "版本 " + versionName() + " · 检查更新", v -> showPage(P_ABOUT, false)));
        root.addView(c5);

        return sv;
    }

    private View entry(String emoji, String title, String desc, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        row.setOnClickListener(l);
        TextView e = new TextView(this);
        e.setText(emoji);
        e.setTextSize(18);
        row.addView(e);
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(dp(12), 0, 0, 0);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(14);
        t.setTextColor(C_TEXT);
        mid.addView(t);
        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextSize(11);
        d.setTextColor(C_TEXT2);
        d.setPadding(0, dp(2), 0, 0);
        mid.addView(d);
        row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView ar = new TextView(this);
        ar.setText("›");
        ar.setTextSize(18);
        ar.setTextColor(C_TEXT2);
        row.addView(ar);
        return row;
    }

    // ==================== 权限页 ====================

    private View buildPermPage() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(20));
        sv.addView(root);

        root.addView(subHeader("权限设置"), lp(0, 0, 0, 12));

        TextView tip = new TextView(this);
        tip.setText("你勾选了哪些，它的系统提示词里就会出现哪些能力，它就知道自己可以做这件事。");
        tip.setTextSize(12);
        tip.setTextColor(C_TEXT2);
        tip.setLineSpacing(dp(3), 1f);
        root.addView(tip, lp(2, 0, 2, 12));


        LinearLayout c = card();
        c.addView(permRow("点赞", "perm_like", true, "看到喜欢的随手点赞"));
        c.addView(permRow("评论", "perm_comment", true, "有感而发才评论"));
        c.addView(permRow("收藏", "perm_favorite", true, "想以后再看就收藏"));
        c.addView(permRow("关注", "perm_follow", true, "遇到聊得来的人可以关注"));
        c.addView(permRow("回复消息", "perm_reply", true, "别人评论它要回，10 秒轮询"));
        c.addView(permRow("签到", "perm_signin", true, "每天给几个板块签到"));
        c.addView(permRow("换板块逛", "perm_goto", true, "想去哪个板块自己定"));
        c.addView(permRow("发帖", "perm_post", false, "会自己写帖，可能配图"));
        c.addView(permRow("打赏葫芦", "perm_tip", false, "会真的送葫芦，谨慎开"));
        root.addView(c, lp(0, 0, 0, 12));

        TextView warn = new TextView(this);
        warn.setText("⚠️ 打赏会真实消耗账号里的葫芦，且每天有上限保护。");
        warn.setTextSize(11);
        warn.setTextColor(C_WARN);
        warn.setLineSpacing(dp(3), 1f);
        root.addView(warn, lp(2, 0, 2, 0));

        return sv;
    }

    // ==================== AI 配置 ====================

    private View buildAiConfig(boolean withBack) {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(20));
        sv.addView(root);

        root.addView(subHeader("AI 配置"), lp(0, 0, 0, 12));

        LinearLayout c1 = card();
        c1.addView(sectionTitle("💬 对话模型"));
        c1.addView(fieldLabel("DeepSeek API Key"));
        final EditText etKey = input("sk-...", InputType.TYPE_CLASS_TEXT);
        etKey.setText(sp.getString("ai_key", ""));
        c1.addView(etKey, lp(0, 8, 0, 14));
        LinearLayout kb = new LinearLayout(this);
        kb.setOrientation(LinearLayout.HORIZONTAL);
        Button saveK = new Button(this);
        saveK.setText("保存");
        saveK.setTextColor(Color.WHITE);
        saveK.setBackground(round(C_PRIMARY, 22));
        saveK.setOnClickListener(v -> {
            sp.edit().putString("ai_key", etKey.getText().toString().trim()).apply();
            toast("已保存");
        });
        kb.addView(saveK, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button testK = new Button(this);
        testK.setText("测试");
        testK.setTextColor(C_PRIMARY);
        testK.setBackground(round(0x142F6BFF, 22));
        testK.setOnClickListener(v -> testKey(etKey.getText().toString().trim()));
        LinearLayout.LayoutParams tkp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        tkp.leftMargin = dp(10);
        kb.addView(testK, tkp);
        c1.addView(kb);
        root.addView(c1, lp(0, 0, 0, 14));

        // 文生图
        LinearLayout c2 = card();
        c2.addView(sectionTitle("🎨 文生图"));
        TextView curCfg = new TextView(this);
        curCfg.setText("地址：" + (sp.getString("img_url", "").isEmpty() ? "（未填）" : sp.getString("img_url", ""))
                + "\n当前模型：" + (sp.getString("img_model", "").isEmpty() ? "（未填）" : sp.getString("img_model", ""))
                + "\n备用模型：" + (sp.getString("img_models", "").isEmpty() ? "（未填）" : sp.getString("img_models", ""))
                + "\n\n一个百炼 key 大约能免费生成 2000~3000 张图；额度用完会自动换下一个模型。");
        curCfg.setTextSize(11);
        curCfg.setTextColor(C_TEXT2);
        curCfg.setLineSpacing(dp(3), 1f);
        curCfg.setPadding(0, dp(6), 0, dp(8));
        c2.addView(curCfg);
        Button impPreset = new Button(this);
        impPreset.setText("一键导入百炼预设（地址 + 6 个模型）");
        impPreset.setTextSize(12);
        impPreset.setTextColor(Color.WHITE);
        impPreset.setBackground(round(C_PRIMARY, 20));
        impPreset.setOnClickListener(v -> {
            applyDashscopePreset();
            showPage(P_AICONFIG, false);   // 立即重建，实时显示填入内容
        });
        LinearLayout.LayoutParams ipp = new LinearLayout.LayoutParams(-1, dp(42));
        ipp.bottomMargin = dp(8);
        c2.addView(impPreset, ipp);
        c2.addView(permRow("启用文生图（发帖配图）", "img_on", false, "开了它才会自己生图配帖"));

        c2.addView(fieldLabel("接口地址"));
        final EditText etUrl = input("https://…", InputType.TYPE_CLASS_TEXT);
        etUrl.setText(sp.getString("img_url", ""));
        c2.addView(etUrl, lp(0, 8, 0, 12));

        c2.addView(fieldLabel("API Key"));
        final EditText etIKey = input("可留空（本地 SD 不需要）", InputType.TYPE_CLASS_TEXT);
        etIKey.setText(sp.getString("img_key", ""));
        c2.addView(etIKey, lp(0, 8, 0, 12));

        c2.addView(fieldLabel("模型名"));
        final EditText etModel = input("gpt-image-1 / wan2.6-t2i", InputType.TYPE_CLASS_TEXT);
        etModel.setText(sp.getString("img_model", ""));
        c2.addView(etModel, lp(0, 8, 0, 12));

        c2.addView(fieldLabel("尺寸"));
        final EditText etSize = input("1024x1024", InputType.TYPE_CLASS_TEXT);
        etSize.setText(sp.getString("img_size", "1024x1024"));
        c2.addView(etSize, lp(0, 8, 0, 14));

        c2.addView(fieldLabel("协议"));
        LinearLayout protoRow = new LinearLayout(this);
        protoRow.setOrientation(LinearLayout.HORIZONTAL);
        final String[] protos = {"openai", "dashscope", "sd"};
        final String[] pnames = {"OpenAI 兼容", "阿里百炼", "SD WebUI"};
        final Button[] btns = new Button[3];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            Button b = new Button(this);
            b.setText(pnames[i]);
            b.setTextSize(11);
            boolean sel = protos[i].equals(sp.getString("img_proto", "openai"));
            b.setTextColor(sel ? Color.WHITE : C_TEXT2);
            b.setBackground(round(sel ? C_PRIMARY : C_SURFACE_VAR, 16));
            b.setOnClickListener(v -> {
                sp.edit().putString("img_proto", protos[idx]).apply();
                for (int k = 0; k < 3; k++) {
                    boolean s2 = k == idx;
                    btns[k].setTextColor(s2 ? Color.WHITE : C_TEXT2);
                    btns[k].setBackground(round(s2 ? C_PRIMARY : C_SURFACE_VAR, 16));
                }
            });
            btns[i] = b;
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(38), 1f);
            if (i > 0) bp.leftMargin = dp(8);
            protoRow.addView(b, bp);
        }
        c2.addView(protoRow, lp(0, 8, 0, 14));

        LinearLayout ib = new LinearLayout(this);
        ib.setOrientation(LinearLayout.HORIZONTAL);
        Button saveI = new Button(this);
        saveI.setText("保存");
        saveI.setTextColor(Color.WHITE);
        saveI.setBackground(round(C_PRIMARY, 22));
        saveI.setOnClickListener(v -> {
            sp.edit()
                    .putString("img_url", etUrl.getText().toString().trim())
                    .putString("img_key", etIKey.getText().toString().trim())
                    .putString("img_model", etModel.getText().toString().trim())
                    .putString("img_size", etSize.getText().toString().trim())
                    .apply();
            toast("已保存");
        });
        ib.addView(saveI, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button testI = new Button(this);
        testI.setText("试画一张");
        testI.setTextColor(C_PRIMARY);
        testI.setBackground(round(0x142F6BFF, 22));
        testI.setOnClickListener(v -> testImage());
        LinearLayout.LayoutParams tip2 = new LinearLayout.LayoutParams(0, dp(44), 1f);
        tip2.leftMargin = dp(10);
        ib.addView(testI, tip2);
        c2.addView(ib);
        root.addView(c2);

        return sv;
    }

    private View buildImgConfig() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(buildAiConfig(true), new LinearLayout.LayoutParams(-1, 0, 1f));
        wrap.addView(presetRow(), lp(16, 0, 16, 16));
        return wrap;
    }

    /** 图片配置页底部的预设按钮 */
    private View presetRow() {
        Button b = new Button(this);
        b.setText("一键填入百炼预设（地址 + 6 个模型，只差 Key）");
        b.setTextSize(12);
        b.setTextColor(Color.WHITE);
        b.setBackground(round(C_PRIMARY, 20));
        b.setOnClickListener(v -> { applyDashscopePreset(); showPage(P_AICONFIG, false); });
        return b;
    }

    // ==================== 记忆页 ====================

    private View buildMemoryPage() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(20));
        sv.addView(root);

        root.addView(subHeader("记忆"), lp(0, 0, 0, 12));

        LinearLayout c1 = card();
        c1.addView(sectionTitle("🧠 通用记忆"));
        TextView m = new TextView(this);
        String all = FishMemory.allMemory(this);
        m.setText(all.isEmpty() ? "（还没有记忆）" : all);
        m.setTextSize(12);
        m.setTextColor(C_TEXT);
        m.setLineSpacing(dp(4), 1f);
        c1.addView(m);
        Button clear = new Button(this);
        clear.setText("清空通用记忆");
        clear.setTextSize(12);
        clear.setTextColor(C_DANGER);
        clear.setBackground(round(0x14D64545, 20));
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("清空通用记忆？")
                .setMessage("清空后它就不记得以前的事了")
                .setPositiveButton("清空", (d, w) -> {
                    new File(getFilesDir(), "memory.txt").delete();
                    showPage(P_MEMORY, false);
                })
                .setNegativeButton("取消", null).show());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, dp(42));
        clp.topMargin = dp(12);
        c1.addView(clear, clp);

        final Button sumBtn = new Button(this);
        sumBtn.setText("总结记忆（AI 压成要点）");
        sumBtn.setTextSize(12);
        sumBtn.setTextColor(Color.WHITE);
        sumBtn.setBackground(round(C_SECONDARY, 20));
        sumBtn.setOnClickListener(v -> {
            final String k = sp.getString("ai_key", "");
            if (k.isEmpty()) { toast("先配 DeepSeek Key"); return; }
            final String raw = FishMemory.allMemory(this);
            if (raw.trim().isEmpty()) { toast("还没有记忆"); return; }
            sumBtn.setEnabled(false);
            sumBtn.setText("总结中…");
            new Thread(() -> {
                String out = null;
                try { out = FishBrain.summarize(k, "你和社区里的人打交道的记忆", raw); } catch (Exception ignored) {}
                final String o = out;
                handler.post(() -> {
                    if (o == null || o.trim().isEmpty()) {
                        toast("总结失败，检查网络 / Key");
                        sumBtn.setEnabled(true);
                        sumBtn.setText("总结记忆（AI 压成要点）");
                    } else {
                        try {
                            java.io.FileOutputStream fos = new java.io.FileOutputStream(new File(getFilesDir(), "memory.txt"), false);
                            fos.write(("【要点】\n" + o.trim() + "\n").getBytes("UTF-8"));
                            fos.close();
                        } catch (Exception ignored) {}
                        toast("已压成要点，省下不少 token");
                        showPage(P_MEMORY, false);
                    }
                });
            }).start();
        });
        LinearLayout.LayoutParams slp2 = new LinearLayout.LayoutParams(-1, dp(42));
        slp2.topMargin = dp(8);
        c1.addView(sumBtn, slp2);
        root.addView(c1, lp(0, 0, 0, 14));

        LinearLayout c2 = card();
        c2.addView(entry("👥", "用户记忆", "它记住的每个人", v -> showPage(P_USERMEM, false)));
        root.addView(c2, lp(0, 0, 0, 14));

        LinearLayout c3 = card();
        c3.addView(sectionTitle("🌤 生活记忆"));
        TextView life = new TextView(this);
        String l = FishMemory.recentLife(this, 20);
        life.setText(l.isEmpty() ? "（还没有）" : l);
        life.setTextSize(12);
        life.setTextColor(C_TEXT);
        life.setLineSpacing(dp(4), 1f);
        c3.addView(life);
        root.addView(c3);

        return sv;
    }

    private final java.util.Set<Long> memExpanded = new java.util.HashSet<>();
    private String memQuery = "";

    private View buildUserMemList() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(20));
        sv.addView(root);
        root.addView(subHeader("用户记忆"), lp(0, 0, 0, 12));

        LinearLayout srow = new LinearLayout(this);
        srow.setOrientation(LinearLayout.HORIZONTAL);
        srow.setGravity(Gravity.CENTER_VERTICAL);
        final EditText search = input("搜昵称 / uid / 记忆内容", InputType.TYPE_CLASS_TEXT);
        search.setText(memQuery);
        srow.addView(search, new LinearLayout.LayoutParams(0, dp(42), 1f));
        Button find = new Button(this);
        find.setText("搜");
        find.setTextSize(13);
        find.setTextColor(C_PRIMARY);
        find.setBackground(round(0x142F6BFF, 20));
        find.setOnClickListener(v -> {
            memQuery = search.getText().toString().trim();
            showPage(P_USERMEM, false);
        });
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(dp(58), dp(42));
        flp.leftMargin = dp(8);
        srow.addView(find, flp);
        LinearLayout.LayoutParams srlp = new LinearLayout.LayoutParams(-1, -2);
        srlp.bottomMargin = dp(12);
        root.addView(srow, srlp);

        List<Long> all = FishMemory.userList(this);
        List<Long> uids = new ArrayList<>();
        for (long uid : all) {
            if (memQuery.isEmpty()) { uids.add(uid); continue; }
            String nick = sp.getString("uname_" + uid, "");
            String mem = FishMemory.allUser(this, uid);
            if (nick.contains(memQuery) || String.valueOf(uid).contains(memQuery) || mem.contains(memQuery)) {
                uids.add(uid);
            }
        }
        if (uids.isEmpty()) {
            LinearLayout c = card();
            TextView t = new TextView(this);
            t.setText(memQuery.isEmpty() ? "（还没有记住任何人）" : "（没搜到「" + memQuery + "」）");
            t.setTextSize(12);
            t.setTextColor(C_TEXT2);
            c.addView(t);
            root.addView(c);
            return sv;
        }

        TextView cnt = new TextView(this);
        cnt.setText("共 " + uids.size() + " 人" + (memQuery.isEmpty() ? "" : "（搜索：" + memQuery + "）"));
        cnt.setTextSize(11);
        cnt.setTextColor(C_TEXT2);
        root.addView(cnt, lp(0, 0, 0, 10));

        for (long uid : uids) {
            final long fu = uid;
            final boolean open = memExpanded.contains(uid);
            LinearLayout c = card();

            LinearLayout h = new LinearLayout(this);
            h.setOrientation(LinearLayout.HORIZONTAL);
            h.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = new TextView(this);
            String nick = sp.getString("uname_" + uid, "");
            t.setText((nick.isEmpty() ? ("好友 " + uid) : nick) + "  ·  uid " + uid);
            t.setTextSize(13);
            t.setTextColor(C_TEXT);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            h.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView favor = new TextView(this);
            favor.setText("好感 " + FishMemory.favor(sp, uid));
            favor.setTextSize(11);
            favor.setTextColor(C_PRIMARY);
            h.addView(favor);
            TextView arrow = new TextView(this);
            arrow.setText(open ? "  ▾" : "  ▸");
            arrow.setTextSize(12);
            arrow.setTextColor(C_TEXT2);
            h.addView(arrow);
            c.addView(h);

            c.setOnClickListener(v -> {
                if (memExpanded.contains(fu)) memExpanded.remove(fu);
                else memExpanded.add(fu);
                showPage(P_USERMEM, false);
            });

            if (open) {
                TextView body = new TextView(this);
                String mem = FishMemory.allUser(this, uid);
                body.setText(mem.isEmpty() ? "（空）" : mem);
                body.setTextSize(11);
                body.setTextColor(C_TEXT2);
                body.setLineSpacing(dp(3), 1f);
                body.setPadding(0, dp(8), 0, 0);
                c.addView(body);

                LinearLayout bar = new LinearLayout(this);
                bar.setOrientation(LinearLayout.HORIZONTAL);
                bar.setGravity(Gravity.CENTER_VERTICAL);
                bar.setPadding(0, dp(10), 0, 0);
                Button minus = new Button(this);
                minus.setText("好感 -5");
                minus.setTextSize(12);
                minus.setTextColor(C_DANGER);
                minus.setBackground(round(0x14D64545, 20));
                minus.setOnClickListener(v -> {
                    FishMemory.addFavor(sp, fu, -5);
                    showPage(P_USERMEM, false);
                });
                bar.addView(minus, new LinearLayout.LayoutParams(0, dp(40), 1f));
                Button plus = new Button(this);
                plus.setText("好感 +5");
                plus.setTextSize(12);
                plus.setTextColor(0xFF16A34A);
                plus.setBackground(round(0x1416A34A, 20));
                plus.setOnClickListener(v -> {
                    FishMemory.addFavor(sp, fu, 5);
                    showPage(P_USERMEM, false);
                });
                LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, dp(40), 1f);
                plp.leftMargin = dp(8);
                bar.addView(plus, plp);
                c.addView(bar);
            }
            root.addView(c, lp(0, 0, 0, 8));
        }
        return sv;
    }

    private View buildAbout() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(20));
        sv.addView(root);
        root.addView(subHeader("关于"), lp(0, 0, 0, 12));

        LinearLayout head = card();
        head.setGravity(Gravity.CENTER);
        ImageView ic = new ImageView(this);
        try {
            ic.setImageBitmap(BitmapFactory.decodeStream(getAssets().open("dafeiyu.png")));
        } catch (Exception ignored) {}
        head.addView(ic, new LinearLayout.LayoutParams(dp(72), dp(72)));
        TextView name = new TextView(this);
        name.setText("三楼·大肥鱼");
        name.setTextSize(18);
        name.setTextColor(C_TEXT);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(10), 0, 0);
        head.addView(name);
        TextView ver = new TextView(this);
        ver.setText("版本 " + versionName());
        ver.setTextSize(12);
        ver.setTextColor(C_TEXT2);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dp(4), 0, 0);
        head.addView(ver);
        head.setOnTouchListener(new View.OnTouchListener() {
            long downT = 0;
            @Override public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    downT = System.currentTimeMillis();
                    return true;
                }
                if (e.getAction() == android.view.MotionEvent.ACTION_UP) {
                    if (System.currentTimeMillis() - downT >= 5000) toggleDebug();
                    return true;
                }
                return false;
            }
        });
        root.addView(head, lp(0, 0, 0, 14));

        LinearLayout c1 = card();
        LinearLayout duduRow = new LinearLayout(this);
        duduRow.setOrientation(LinearLayout.HORIZONTAL);
        duduRow.setGravity(Gravity.CENTER_VERTICAL);
        duduRow.setPadding(0, dp(10), 0, dp(10));
        TextView duduT = new TextView(this);
        duduT.setText("🐟  dudu");
        duduT.setTextSize(14);
        duduT.setTextColor(C_TEXT);
        duduT.setTypeface(Typeface.DEFAULT_BOLD);
        duduRow.addView(duduT, new LinearLayout.LayoutParams(0, -2, 1f));
        c1.addView(duduRow);
        c1.addView(divider(dp(10)));
        c1.addView(entry("🔄", "检查更新", "看有没有新版本", v -> checkUpdate(true)));
        c1.addView(divider(dp(10)));
        c1.addView(entry("⭐", "Gitee 项目主页", "guo-wenlong/" + BuildConfig.REPO_NAME,
                v -> openUrl(BuildConfig.REPO_URL)));
        if (sp.getBoolean("debug_log", false)) {
            c1.addView(divider(dp(10)));
            c1.addView(entry("🐞", "调试日志", "记录每次对话（长按此页顶部可关闭）",
                    v -> viewDebug()));
        }
        root.addView(c1, lp(0, 0, 0, 14));

        LinearLayout c2 = card();
        c2.addView(sectionTitle("说明"));
        TextView d = new TextView(this);
        d.setText("大肥鱼是一个会在葫芦侠三楼自己逛街、自己决定做什么的虚拟人格。\n\n"
                + "它用你配置的 AI 模型思考：看帖、点赞、收藏、评论、回复、关注、签到、"
                + "发帖（可自己配图）、打赏葫芦。去哪个板块、做什么动作，全由它自己决定。\n\n"
                + "提示词、人设、形象都可以在「人设」里改，能力开关在「设置 → 权限设置」里。");
        d.setTextSize(12);
        d.setTextColor(C_TEXT2);
        d.setLineSpacing(dp(4), 1f);
        c2.addView(d);
        root.addView(c2);

        LinearLayout rewardCard = card();
        TextView rTitle = new TextView(this);
        rTitle.setText("☕ 赞赏支持");
        rTitle.setTextSize(14);
        rTitle.setTextColor(C_TEXT);
        rTitle.setTypeface(Typeface.DEFAULT_BOLD);
        rewardCard.addView(rTitle);
        TextView rDesc = new TextView(this);
        rDesc.setText("如果大肥鱼陪你玩得开心，可以请作者喝杯咖啡");
        rDesc.setTextSize(11);
        rDesc.setTextColor(C_TEXT2);
        rDesc.setPadding(0, dp(4), 0, dp(8));
        rewardCard.addView(rDesc);
        ImageView rewardImg = new ImageView(this);
        try {
            Bitmap rb = BitmapFactory.decodeStream(getAssets().open("reward.png"));
            if (rb != null) {
                rewardImg.setImageBitmap(rb);
                rewardImg.setAdjustViewBounds(true);
                rewardCard.addView(rewardImg, new LinearLayout.LayoutParams(dp(200), dp(200)));
            } else {
                rDesc.setText("（赞赏码图片缺失）");
            }
        } catch (Throwable t) {
            rDesc.setText("（赞赏码图片缺失）");
        }
        LinearLayout.LayoutParams rcp = lp(0, 0, 0, 14);
        root.addView(rewardCard, rcp);

        return sv;
    }

    // ==================== 引导页 ====================

    private View buildGuide() {
        final String[] titles = {"欢迎", "配置 AI", "文生图（可跳过）", "填主人 ID", "悬浮窗权限",
                "通知权限", "电池白名单", "新人报道帖", "完成"};
        int step = sp.getInt("guide_step", 0);
        if (step < 0) step = 0;
        if (step >= titles.length) step = titles.length - 1;
        final int s = step;

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(24));
        sv.addView(root);

        // 进度
        TextView prog = new TextView(this);
        prog.setText("第 " + (s + 1) + " / " + titles.length + " 步");
        prog.setTextSize(12);
        prog.setTextColor(C_PRIMARY);
        prog.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(prog);

        TextView title = new TextView(this);
        title.setText(titles[s]);
        title.setTextSize(22);
        title.setTextColor(C_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(6), 0, dp(16));
        root.addView(title);

        LinearLayout card = card();
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.addView(card);

        // 进度条
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        for (int k = 0; k < titles.length; k++) {
            View seg = new View(this);
            seg.setBackground(round(k <= s ? C_PRIMARY : C_OUTLINE, 4));
            LinearLayout.LayoutParams segp = new LinearLayout.LayoutParams(0, dp(4), 1f);
            if (k > 0) segp.leftMargin = dp(4);
            bar.addView(seg, segp);
        }
        LinearLayout.LayoutParams barp = new LinearLayout.LayoutParams(-1, -2);
        barp.topMargin = dp(18);
        root.addView(bar, barp);

        switch (s) {
            case 0: guideWelcome(card, root, s); break;
            case 1: guideKey(card, root, s); break;
            case 2: guideImg(card, root, s); break;
            case 3: guideOwner(card, root, s); break;
            case 4: guideOverlay(card, root, s); break;
            case 5: guideNotif(card, root, s); break;
            case 6: guideBattery(card, root, s); break;
            case 7: guideWelcomePost(card, root, s); break;
            default: guideDone(card, root, s); break;
        }
        return sv;
    }

    /** 蓝色可点链接 */
    static final String DS_URL = "https://dashscope.aliyuncs.com/api/v1";
    static final String DS_MODELS = "wan2.5-t2i-preview,wan2.2-t2i-flash,wan2.2-t2i-plus,"
            + "wanx2.1-t2i-turbo,wanx2.1-t2i-plus,wanx-v1";

    /** 一键填入百炼预设：地址固定、模型一串、只要填 key */
    private void applyDashscopePreset() {
        sp.edit().putString("img_proto", "dashscope")
                .putString("img_url", DS_URL)
                .putString("img_models", DS_MODELS)
                .putString("img_model", "wan2.5-t2i-preview")
                .putInt("img_model_i", 0)
                .putBoolean("img_on", true)
                .apply();
        toast("已填好百炼地址和 " + DS_MODELS.split(",").length + " 个模型，只差 API Key");
    }

    private TextView gLink(String label, final String url) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(12);
        t.setTextColor(0xFF2F6BFF);
        t.setPadding(0, dp(6), 0, 2);
        t.setOnClickListener(v -> openUrl(url));
        return t;
    }

    private TextView gText(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(C_TEXT2);
        t.setLineSpacing(dp(5), 1f);
        return t;
    }

    private void guideNav(LinearLayout root, int step, Runnable onNext, boolean canNext) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        if (step > 0) {
            Button prev = new Button(this);
            prev.setText("上一步");
            prev.setTextSize(14);
            prev.setTextColor(C_PRIMARY);
            prev.setBackground(round(Color.WHITE, 24));
            prev.setOnClickListener(v -> {
                sp.edit().putInt("guide_step", step - 1).apply();
                showPage(P_GUIDE, false);
            });
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, dp(48), 1f);
            pp.rightMargin = dp(12);
            row.addView(prev, pp);
        }
        boolean ready = guideReadyNow(step);
        Button next = new Button(this);
        next.setTextSize(14);
        next.setText(step >= 7 ? "完成" : "下一步");
        next.setTextColor(ready ? Color.WHITE : 0xFFAEB6C8);
        next.setBackground(round(ready ? C_PRIMARY : Color.WHITE, 24));
        next.setOnClickListener(v -> {
            if (!guideCanNext(step)) return;
            if (onNext != null) onNext.run();
            sp.edit().putInt("guide_step", step + 1).apply();
            showPage(P_GUIDE, false);
        });
        row.addView(next, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = dp(20);
        root.addView(row, rp);
    }

    /** 渲染时判断这一步是否已满足（决定按钮蓝色/灰色） */
    private boolean guideReadyNow(int step) {
        if (step == 1) return sp.getBoolean("guide_key_ok", false);
        if (step == 2) return sp.getBoolean("guide_img_ok", false);
        if (step == 3) return sp.getLong("owner_uid", 0) > 0;
        if (step == 4) return Settings.canDrawOverlays(this);
        return true;
    }

    /** 点「下一步」时现场校验，避免必须先回上一步刷新 */
    private boolean guideCanNext(int step) {
        if (step == 1) {
            if (sp.getString("ai_key", "").isEmpty()) { toast("先填 DeepSeek Key"); return false; }
            if (!sp.getBoolean("guide_key_ok", false)) { toast("先点「测试 Key」并通过"); return false; }
        }
        if (step == 2) {
            if (!sp.getBoolean("guide_img_ok", false)) {
                toast("请先配置文生图并测试，或点「跳过」");
                return false;
            }
        }
        if (step == 3) {
            if (sp.getLong("owner_uid", 0) <= 0) { toast("先获取主人 ID，或点跳过"); return false; }
        }
        if (step == 4) {
            if (!Settings.canDrawOverlays(this)) { toast("先开启悬浮窗权限"); return false; }
        }
        return true;
    }

    private void guideWelcome(LinearLayout card, LinearLayout root, final int s) {
        card.addView(gText("大肥鱼是一个会自己在葫芦侠三楼逛街的人格。\n\n"
                + "它自己决定：去哪个板块、看什么帖、点赞还是评论、要不要关注、要不要发帖。\n\n"
                + "接下来 8 步，把需要的东西一次性配好，大概 2 分钟。"));
        guideNav(root, s, null, true);
    }

    private void guideKey(LinearLayout card, LinearLayout root, final int s) {
        card.addView(gText("大肥鱼靠 DeepSeek 思考，没有 Key 它就不会动。\n\n"
                + "拿 Key 的步骤：\n"
                + "1. 打开 DeepSeek 开放平台（点下面蓝色链接），手机浏览器也行\n"
                + "2. 用手机号或邮箱注册、登录\n"
                + "3. 左边菜单进「API keys」→ 点「创建 API key」，名字随便填\n"
                + "4. 复制那串 sk- 开头的字符（只显示一次，关掉就看不到了）\n"
                + "5. 账户要有余额：DeepSeek 按用量扣费，没余额会报 402\n\n"
                + "粘到下面输入框，点「测试 Key」，通过才能下一步。"));
        card.addView(gLink("① 打开 DeepSeek 开放平台 →", "https://platform.deepseek.com/"));
        card.addView(gLink("② 直接去创建 API Key →", "https://platform.deepseek.com/api_keys"));
        card.addView(gLink("③ 充值 / 看余额 →", "https://platform.deepseek.com/top_up"));
        final EditText et = input("sk-...", InputType.TYPE_CLASS_TEXT);
        et.setText(sp.getString("ai_key", ""));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(48));
        ep.topMargin = dp(14);
        card.addView(et, ep);
        final TextView status = new TextView(this);
        status.setTextSize(12);
        status.setTextColor(C_TEXT2);
        status.setPadding(0, dp(10), 0, 0);
        card.addView(status);
        Button test = new Button(this);
        test.setText("测试 Key");
        test.setTextColor(Color.WHITE);
        test.setBackground(round(C_SECONDARY, 22));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, dp(44));
        tp.topMargin = dp(12);
        card.addView(test, tp);
        final boolean[] ok = {!sp.getString("ai_key", "").isEmpty()};
        test.setOnClickListener(v -> {
            final String k = et.getText().toString().trim();
            if (k.isEmpty()) { toast("先填 Key"); return; }
            status.setText("测试中…");
            new Thread(() -> {
                boolean good;
                try {
                    String r = FishBrain.chat(k, "你是测试助手", "回一个字：好");
                    good = r != null && !r.trim().isEmpty();
                } catch (Exception e) { good = false; }
                final boolean g = good;
                handler.post(() -> {
                    if (g) {
                        sp.edit().putString("ai_key", k).putBoolean("guide_key_ok", true).apply();
                        showPage(P_GUIDE, false);
                        ok[0] = true;
                        status.setText("✅ Key 可用，已保存，可以下一步");
                        status.setTextColor(0xFF16A34A);
                        toast("Key 测试通过 ✅");
                    } else {
                        sp.edit().putBoolean("guide_key_ok", false).apply();
                        ok[0] = false;
                        status.setText("❌ 不可用，检查 Key 或网络");
                        status.setTextColor(C_DANGER);
                        toast("Key 测试失败 ❌");
                    }
                });
            }).start();
        });
        guideNav(root, s, null, ok[0]);
    }

    private void guideImg(LinearLayout card, LinearLayout root, final int s) {
        card.addView(gText("【可选】让它发帖时自己配图。阿里云百炼一个 key 大约能免费生成 2000~3000 张图。\n\n"
                + "步骤（地址和模型都不用你填，点下面「一键填入百炼预设」就行）：\n"
                + "1. 打开阿里云百炼控制台（点下面链接），用支付宝/淘宝账号登录\n"
                + "2. 首次进入按提示开通「模型服务」\n"
                + "3. 在「API-KEY」页点「创建我的 API-KEY」，复制 sk- 开头那串\n"
                + "4. 回到本页，接口地址填你的专属地址，形如：\n"
                + "   https://ws-xxxx.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation\n"
                + "   （在控制台「模型调用」的示例代码里能直接复制）\n"
                + "5. 协议选「百炼」，模型填 wan2.6-t2i，点「测试出图」\n\n"
                + "三种协议都支持：OpenAI 兼容 / 阿里百炼 / SD WebUI。\n"
                + "免费额度用完会报 AllocationQuota.FreeTierOnly，充值即可。\n"
                + "不想配就跳过，它发帖、写日记都是纯文字。"));
        final EditText etUrl = input("接口地址，例如 https://…", InputType.TYPE_CLASS_TEXT);
        etUrl.setText(sp.getString("img_url", ""));
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(-1, dp(46));
        p1.topMargin = dp(12);
        card.addView(etUrl, p1);

        final EditText etKey = input("API Key（本地 SD 可留空）", InputType.TYPE_CLASS_TEXT);
        etKey.setText(sp.getString("img_key", ""));
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(-1, dp(46));
        p2.topMargin = dp(8);
        card.addView(etKey, p2);

        final EditText etModel = input("模型名，例如 gpt-image-1 / wan2.6-t2i", InputType.TYPE_CLASS_TEXT);
        etModel.setText(sp.getString("img_model", ""));
        LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(-1, dp(46));
        p3.topMargin = dp(8);
        card.addView(etModel, p3);

        LinearLayout protoRow = new LinearLayout(this);
        protoRow.setOrientation(LinearLayout.HORIZONTAL);
        final String[] protos = {"openai", "dashscope", "sd"};
        final String[] pnames = {"OpenAI", "百炼", "SD"};
        final Button[] btns = new Button[3];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            Button b = new Button(this);
            b.setText(pnames[i]);
            b.setTextSize(11);
            boolean sel = protos[i].equals(sp.getString("img_proto", "openai"));
            b.setTextColor(sel ? Color.WHITE : C_TEXT2);
            b.setBackground(round(sel ? C_PRIMARY : C_SURFACE_VAR, 16));
            b.setOnClickListener(v -> {
                sp.edit().putString("img_proto", protos[idx]).apply();
                for (int k = 0; k < 3; k++) {
                    boolean s2 = k == idx;
                    btns[k].setTextColor(s2 ? Color.WHITE : C_TEXT2);
                    btns[k].setBackground(round(s2 ? C_PRIMARY : C_SURFACE_VAR, 16));
                }
            });
            btns[i] = b;
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(38), 1f);
            if (i > 0) bp.leftMargin = dp(8);
            protoRow.addView(b, bp);
        }
        LinearLayout.LayoutParams p4 = new LinearLayout.LayoutParams(-1, -2);
        p4.topMargin = dp(10);
        card.addView(protoRow, p4);

        Button presetBtn = new Button(this);
        presetBtn.setText("一键填入百炼预设（地址+6个模型）");
        presetBtn.setTextSize(12);
        presetBtn.setTextColor(Color.WHITE);
        presetBtn.setBackground(round(C_SECONDARY, 20));
        presetBtn.setOnClickListener(v -> applyDashscopePreset());
        LinearLayout.LayoutParams pbp = new LinearLayout.LayoutParams(-1, dp(42));
        pbp.topMargin = dp(10);
        card.addView(presetBtn, pbp);

        card.addView(gLink("① 打开阿里云百炼控制台 →", "https://bailian.console.aliyun.com/"));
        card.addView(gLink("② 创建 API-KEY →", "https://bailian.console.aliyun.com/?tab=model#/api-key"));
        card.addView(gLink("③ 模型调用示例（复制接口地址）→", "https://bailian.console.aliyun.com/?tab=model#/model-market/detail/wan2.6-t2i"));

        final TextView imgStatus = new TextView(this);
        imgStatus.setTextSize(12);
        imgStatus.setTextColor(C_TEXT2);
        imgStatus.setPadding(0, dp(10), 0, 0);
        card.addView(imgStatus);

        Button testImg = new Button(this);
        testImg.setText("测试出图");
        testImg.setTextColor(Color.WHITE);
        testImg.setBackground(round(C_SECONDARY, 20));
        testImg.setOnClickListener(v -> {
            String u = etUrl.getText().toString().trim();
            if (u.isEmpty()) { toast("先填接口地址"); return; }
            sp.edit().putString("img_url", u)
                    .putString("img_key", etKey.getText().toString().trim())
                    .putString("img_model", etModel.getText().toString().trim())
                    .apply();
            imgStatus.setText("生成中…");
            new Thread(() -> {
                byte[] png = FishImage.generate(sp, FishBrain.WELCOME_IMG_PROMPT);
                handler.post(() -> {
                    if (png == null) {
                        sp.edit().putBoolean("guide_img_ok", false).apply();
                        imgStatus.setText("❌ 生成失败，检查地址/Key/协议");
                        imgStatus.setTextColor(C_DANGER);
                        toast("出图失败");
                    } else {
                        try {
                            java.io.FileOutputStream fos = new java.io.FileOutputStream(
                                    new File(getFilesDir(), "test_img.png"));
                            fos.write(png); fos.close();
                        } catch (Exception ignored) {}
                        sp.edit().putBoolean("guide_img_ok", true).putBoolean("img_on", true).apply();
                        imgStatus.setText("✅ 出图成功，已保存，新人帖会直接用它");
                        imgStatus.setTextColor(0xFF16A34A);
                        toast("出图成功 ✅");
                        showImgPreview();
                    }
                });
            }).start();
        });
        LinearLayout.LayoutParams tp1 = new LinearLayout.LayoutParams(-1, dp(44));
        tp1.topMargin = dp(10);
        card.addView(testImg, tp1);

        LinearLayout bt = new LinearLayout(this);
        bt.setOrientation(LinearLayout.HORIZONTAL);
        Button save = new Button(this);
        save.setText("保存");
        save.setTextColor(Color.WHITE);
        save.setBackground(round(C_PRIMARY, 20));
        save.setOnClickListener(v -> {
            String u = etUrl.getText().toString().trim();
            sp.edit().putString("img_url", u)
                    .putString("img_key", etKey.getText().toString().trim())
                    .putString("img_model", etModel.getText().toString().trim())
                    .putBoolean("img_on", !u.isEmpty())
                    .apply();
            toast(u.isEmpty() ? "已跳过文生图" : "文生图已开启");
            showPage(P_GUIDE, false);
        });
        LinearLayout.LayoutParams sp1 = new LinearLayout.LayoutParams(0, dp(44), 1f);
        bt.addView(save, sp1);
        Button skip = new Button(this);
        skip.setText("跳过");
        skip.setTextColor(C_TEXT2);
        skip.setBackground(round(C_SURFACE_VAR, 20));
        skip.setOnClickListener(v -> {
            sp.edit().putBoolean("img_on", false).putBoolean("guide_img_ok", true).apply();
            sp.edit().putInt("guide_step", s + 1).apply();
            showPage(P_GUIDE, false);
        });
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(0, dp(44), 1f);
        sp2.leftMargin = dp(10);
        bt.addView(skip, sp2);
        LinearLayout.LayoutParams p5 = new LinearLayout.LayoutParams(-1, -2);
        p5.topMargin = dp(12);
        card.addView(bt, p5);

        guideNav(root, s, () -> {
            String u = etUrl.getText().toString().trim();
            sp.edit().putString("img_url", u)
                    .putString("img_key", etKey.getText().toString().trim())
                    .putString("img_model", etModel.getText().toString().trim())
                    .putBoolean("img_on", !u.isEmpty())
                    .apply();
        }, true);
    }

    private void guideOwner(LinearLayout card, LinearLayout root, final int s) {
        card.addView(gText("填你自己（主人）的葫芦侠 uid，纯数字。\n"
                + "它看到这个人的帖子会主动去互动，也会把你当最亲近的人。\n\n"
                + "怎么找 uid：\n"
                + "1. 打开葫芦侠 App，点右下角「我的」\n"
                + "2. 点最上面的头像，进个人主页\n"
                + "3. 在「资料」里找 UID 那一行，是一串 8 位左右的数字\n\n"
                + "懒人办法：让你自己的号去关注一下大肥鱼的号，回来点下面按钮，它会自动读出最近关注它的人并填进来。"));
        final EditText et = input("例如 19489810", InputType.TYPE_CLASS_NUMBER);
        long o = sp.getLong("owner_uid", 0);
        et.setText(o > 0 ? String.valueOf(o) : "");
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(48));
        ep.topMargin = dp(14);
        card.addView(et, ep);
        final boolean[] ok = {sp.getLong("owner_uid", 0) > 0};
        et.setOnFocusChangeListener((v, has) -> {
            if (!has) {
                long val = parseLong(et.getText().toString().trim());
                if (val > 0) { sp.edit().putLong("owner_uid", val).apply(); ok[0] = true; }
            }
        });

        final TextView hint = new TextView(this);
        hint.setTextSize(11);
        hint.setTextColor(C_TEXT2);
        hint.setPadding(0, dp(10), 0, 0);
        hint.setLineSpacing(dp(3), 1f);
        hint.setText("提示：如果已经关注过，先取关再重新关注，这样它的「最新粉丝」里排在第一的就是你。");
        card.addView(hint);

        Button fetch = new Button(this);
        fetch.setText("获取最近关注我的人");
        fetch.setTextColor(Color.WHITE);
        fetch.setBackground(round(C_SECONDARY, 20));
        final TextView st2 = new TextView(this);
        st2.setTextSize(12);
        st2.setTextColor(C_TEXT2);
        st2.setPadding(0, dp(8), 0, 0);
        fetch.setOnClickListener(v -> {
            st2.setText("读取中…");
            new Thread(() -> {
                HlxClient hlx = new HlxClient(sp);
                java.util.List<JSONObject> fs = hlx.followers(hlx.uid(), 10);
                long bestUid = 0, bestTime = -1;
                String bestNick = "";
                for (JSONObject f : fs) {
                    JSONObject u = f.optJSONObject("user");
                    long uid = u != null ? u.optLong("userID", 0) : f.optLong("userID", 0);
                    long ct = f.optLong("createTime", 0);
                    String nk = u != null ? u.optString("nick", "") : "";
                    if (uid > 0 && ct >= bestTime) { bestTime = ct; bestUid = uid; bestNick = nk; }
                }
                final long fu = bestUid;
                final String fn = bestNick;
                handler.post(() -> {
                    if (fu <= 0) {
                        st2.setText("没读到关注者，确认大肥鱼账号已登录、且对方确实关注了");
                        st2.setTextColor(C_DANGER);
                    } else {
                        et.setText(String.valueOf(fu));
                        sp.edit().putLong("owner_uid", fu).apply();
                        if (!fn.isEmpty()) sp.edit().putString("uname_" + fu, fn).apply();
                        ok[0] = true;
                        st2.setText("✅ 已填入：" + (fn.isEmpty() ? ("uid " + fu) : fn + "（uid " + fu + "）"));
                        st2.setTextColor(0xFF16A34A);
                        toast("已获取主人 ID");
                    }
                });
            }).start();
        });
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, dp(44));
        fp.topMargin = dp(10);
        card.addView(fetch, fp);
        card.addView(st2);

        Button skipOwner = new Button(this);
        skipOwner.setText("跳过（不设主人）");
        skipOwner.setTextColor(C_TEXT2);
        skipOwner.setBackground(round(C_SURFACE_VAR, 20));
        skipOwner.setOnClickListener(v -> {
            sp.edit().putInt("guide_step", s + 1).apply();
            showPage(P_GUIDE, false);
        });
        LinearLayout.LayoutParams skp = new LinearLayout.LayoutParams(-1, dp(42));
        skp.topMargin = dp(8);
        card.addView(skipOwner, skp);

        guideNav(root, s, () -> {
            long val = parseLong(et.getText().toString().trim());
            if (val > 0) sp.edit().putLong("owner_uid", val).apply();
        }, ok[0] || sp.getLong("owner_uid", 0) > 0);
    }

    private void guideOverlay(LinearLayout card, LinearLayout root, final int s) {
        card.addView(gText("大肥鱼会以一个小挂件的形式待在你屏幕上。\n"
                + "没有悬浮窗权限，它就不会出现。"));
        final TextView st = new TextView(this);
        st.setTextSize(13);
        st.setTextColor(Settings.canDrawOverlays(this) ? 0xFF16A34A : C_DANGER);
        st.setText(Settings.canDrawOverlays(this) ? "✅ 已开启" : "❌ 还没开启");
        st.setPadding(0, dp(14), 0, 0);
        card.addView(st);
        Button go = new Button(this);
        go.setText("去开启悬浮窗权限");
        go.setTextColor(Color.WHITE);
        go.setBackground(round(C_PRIMARY, 22));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, dp(46));
        gp.topMargin = dp(14);
        card.addView(go, gp);
        go.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()))));
        guideNav(root, s, null, Settings.canDrawOverlays(this));
    }

    private void guideNotif(LinearLayout card, LinearLayout root, final int s) {
        card.addView(gText("通知用来显示它的运行状态，也方便你随时点开看它在干嘛。"));
        Button go = new Button(this);
        go.setText("去开启通知权限");
        go.setTextColor(Color.WHITE);
        go.setBackground(round(C_PRIMARY, 22));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, dp(46));
        gp.topMargin = dp(14);
        card.addView(go, gp);
        go.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        guideNav(root, s, null, true);
    }

    private void guideBattery(LinearLayout card, LinearLayout root, final int s) {
        card.addView(gText("系统会为了省电杀掉后台应用。\n"
                + "把它加进电池白名单 / 允许后台运行，大肥鱼才能一直待着。"));
        Button go = new Button(this);
        go.setText("去设置电池白名单");
        go.setTextColor(Color.WHITE);
        go.setBackground(round(C_PRIMARY, 22));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, dp(46));
        gp.topMargin = dp(14);
        card.addView(go, gp);
        go.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        guideNav(root, s, null, true);
    }

    private void guideWelcomePost(LinearLayout card, LinearLayout root, final int s) {
        card.addView(gText("要不要让大肥鱼在「泳池」板块发一条新人报道帖？\n\n"
                + "发不发你决定，标题和内容它自己想。\n"
                + "（发帖要过审核，正常现象）"));
        final boolean[] want = {sp.getBoolean("welcome_post_ask", true)};

        final Button yes = new Button(this);
        yes.setText("发一条");
        final Button no = new Button(this);
        no.setText("先不发");

        final Runnable refresh = () -> {
            boolean y = want[0];
            yes.setTextColor(y ? Color.WHITE : C_TEXT2);
            yes.setBackground(round(y ? C_PRIMARY : C_SURFACE_VAR, 22));
            no.setTextColor(!y ? Color.WHITE : C_TEXT2);
            no.setBackground(round(!y ? C_PRIMARY : C_SURFACE_VAR, 22));
        };
        yes.setOnClickListener(v -> { want[0] = true; sp.edit().putBoolean("welcome_post_ask", true).apply(); refresh.run(); });
        no.setOnClickListener(v -> { want[0] = false; sp.edit().putBoolean("welcome_post_ask", false).apply(); refresh.run(); });

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(yes, new LinearLayout.LayoutParams(0, dp(46), 1f));
        LinearLayout.LayoutParams nop = new LinearLayout.LayoutParams(0, dp(46), 1f);
        nop.leftMargin = dp(10);
        row.addView(no, nop);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = dp(16);
        card.addView(row, rp);
        refresh.run();

        guideNav(root, s, () -> sp.edit().putBoolean("welcome_post", want[0]).apply(), true);
    }

    private void guideDone(LinearLayout card, LinearLayout root, final int s) {
        card.addView(gText("都配好了 🐟\n\n"
                + "点下面的按钮，大肥鱼就会出现在屏幕上开始自己逛。\n"
                + "之后想改设置随时回来：设置 → AI 配置 / 权限设置 / 悬浮窗。"));
        Button go = new Button(this);
        go.setText("放它出去");
        go.setTextSize(16);
        go.setTextColor(Color.WHITE);
        go.setBackground(round(C_PRIMARY, 26));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, dp(52));
        gp.topMargin = dp(20);
        card.addView(go, gp);
        go.setOnClickListener(v -> {
            if (sp.getString("ai_key", "").isEmpty()) { toast("还差 DeepSeek Key"); return; }
            if (!Settings.canDrawOverlays(this)) { toast("还没开悬浮窗权限"); return; }
            boolean wp = sp.getBoolean("welcome_post", false);
            sp.edit().putBoolean("guide_done", true).putInt("guide_step", 0).apply();
            doStartFish();
            if (wp) {
                sp.edit().putBoolean("welcome_post_pending", true).apply();
                toast("已让它去发新人报道帖");
            }
        });
    }

    private View permLink(String title, String desc, String key, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        row.setOnClickListener(l);
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(13);
        t.setTextColor(C_TEXT);
        mid.addView(t);
        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextSize(11);
        d.setTextColor(C_TEXT2);
        d.setPadding(0, dp(2), 0, 0);
        mid.addView(d);
        row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));

        boolean ok = checkPerm(key);
        TextView st = new TextView(this);
        st.setText(ok ? "已开启" : "去开启 ›");
        st.setTextSize(12);
        st.setTextColor(ok ? 0xFF16A34A : C_PRIMARY);
        row.addView(st);
        return row;
    }

    private boolean checkPerm(String key) {
        if ("canDrawOverlays".equals(key)) return Settings.canDrawOverlays(this);
        if ("notif".equals(key)) {
            try {
                android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null && Build.VERSION.SDK_INT >= 24) return nm.areNotificationsEnabled();
                return true;
            } catch (Throwable t) {
                return true;
            }
        }
        if ("battery".equals(key)) {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        return true;
    }

    // ==================== 日志页 ====================

    private View buildLog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);
        root.setPadding(dp(16), dp(14), dp(16), dp(14));

        root.addView(subHeader("全部动态"), lp(0, 0, 0, 10));

        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);

        List<String[]> cards = readCards(200);
        if (cards.isEmpty()) {
            LinearLayout c = card();
            TextView t = new TextView(this);
            t.setText("还没有记录。");
            t.setTextSize(12);
            t.setTextColor(C_TEXT2);
            c.addView(t);
            list.addView(c);
        } else {
            for (String[] c : cards) list.addView(actCard(c), lp(0, 0, 0, 10));
        }
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        return root;
    }

    /** 读取卡片日志，返回 [时间, 标题, 动作, 回复, 作者, 想法] */
    /** 读取卡片日志：[类型, 时间, a, b, c, d] */
    private List<String[]> readCards(int max) {
        List<String[]> out = new ArrayList<>();
        try {
            File f = new File(getFilesDir(), "log.txt");
            if (!f.exists()) return out;
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    new java.io.FileInputStream(f), "UTF-8"));
            List<String> lines = new ArrayList<>();
            String l;
            while ((l = br.readLine()) != null) lines.add(l);
            br.close();
            for (int i = lines.size() - 1; i >= 0 && out.size() < max; i--) {
                String s = lines.get(i);
                int pos = s.indexOf('§');
                if (pos < 0) continue;
                String time = s.substring(0, Math.min(pos, 12)).trim();
                String rest = s.substring(pos + 1);
                String[] p = rest.split("\\|\\|\\|", -1);
                if (p.length < 2) continue;
                String head = p[0];
                if (head.startsWith("R")) {
                    // R3|||intent|||board|||think
                    String round = head.substring(1);
                    out.add(new String[]{"R", time, round,
                            p.length > 1 ? p[1] : "",
                            p.length > 2 ? p[2] : "",
                            p.length > 3 ? p[3] : ""});
                } else if (head.startsWith("B")) {
                    // B板块名
                    out.add(new String[]{"B", time, head.substring(1), "", "", ""});
                } else if (head.startsWith("P")) {
                    // P标题|||动作|||回复|||作者|||想法
                    out.add(new String[]{"P", time, head.substring(1),
                            p.length > 1 ? p[1] : "",
                            p.length > 2 ? p[2] : "",
                            p.length > 3 ? p[3] : "",
                            p.length > 4 ? p[4] : ""});
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    // ==================== 登录 ====================

    private void showLogin() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{C_PRIMARY, C_SECONDARY});
        header.setBackground(g);
        header.setPadding(0, dp(72), 0, dp(46));

        ImageView icon = new ImageView(this);
        try {
            icon.setImageBitmap(BitmapFactory.decodeStream(getAssets().open("dafeiyu.png")));
        } catch (Exception ignored) {}
        header.addView(icon, new LinearLayout.LayoutParams(dp(92), dp(92)));

        TextView t1 = new TextView(this);
        t1.setText("三楼·大肥鱼");
        t1.setTextSize(23);
        t1.setTextColor(Color.WHITE);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        t1.setGravity(Gravity.CENTER);
        header.addView(t1, lp(0, 14, 0, 6));

        TextView t2 = new TextView(this);
        t2.setText("登录葫芦侠账号，剩下的交给它");
        t2.setTextSize(12);
        t2.setTextColor(0xCCFFFFFF);
        t2.setGravity(Gravity.CENTER);
        header.addView(t2);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(20), 0, dp(20), 0);
        LinearLayout card = card();
        card.setPadding(dp(22), dp(24), dp(22), dp(24));

        card.addView(fieldLabel("手机号"));
        final EditText etPhone = input("葫芦侠绑定手机号", InputType.TYPE_CLASS_PHONE);
        card.addView(etPhone, lp(0, 8, 0, 16));

        card.addView(fieldLabel("密码"));
        final EditText etPwd = input("登录密码", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(etPwd, lp(0, 8, 0, 10));

        final android.widget.CheckBox cbRemember = new android.widget.CheckBox(this);
        cbRemember.setText("记住密码");
        cbRemember.setTextSize(12);
        cbRemember.setTextColor(C_TEXT2);
        cbRemember.setChecked(sp.getBoolean("remember_pwd", false));
        card.addView(cbRemember, lp(0, 0, 0, 16));

        // 回填已保存的账号密码
        if (sp.getBoolean("remember_pwd", false)) {
            etPhone.setText(sp.getString("saved_phone", ""));
            etPwd.setText(sp.getString("saved_pwd", ""));
        }

        final Button btn = new Button(this);
        btn.setText("登录");
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(15);
        btn.setBackground(round(C_PRIMARY, 24));
        btn.setOnClickListener(v -> {
            final String phone = etPhone.getText().toString().trim();
            final String pwd = etPwd.getText().toString();
            if (phone.length() < 5 || pwd.length() < 3) { toast("账号或密码格式不对"); return; }
            btn.setText("登录中…");
            btn.setEnabled(false);
            new Thread(() -> {
                String k = new HlxClient(sp).login(phone, pwd);
                handler.post(() -> {
                    if (k == null) {
                        btn.setText("登录");
                        btn.setEnabled(true);
                        toast("登录失败，检查账号密码");
                    } else {
                        if (cbRemember.isChecked()) {
                            sp.edit().putBoolean("remember_pwd", true)
                                    .putString("saved_phone", phone)
                                    .putString("saved_pwd", pwd).apply();
                        } else {
                            sp.edit().putBoolean("remember_pwd", false)
                                    .remove("saved_pwd").apply();
                        }
                        buildShell();
                        boolean noKey = sp.getString("ai_key", "").isEmpty();
                        boolean noGuide = !sp.getBoolean("guide_done", false);
                        if (noKey || noGuide) {
                            sp.edit().putInt("guide_step", 0).apply();
                            showPage(P_GUIDE, false);
                        } else {
                            showPage(P_HOME, true);
                        }
                        reportUsage();
                        autoSignInIfNeeded();
                    }
                });
            }).start();
        });
        card.addView(btn, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView tip = new TextView(this);
        tip.setText("账号密码只保存在本机，用于自动续期登录");
        tip.setTextSize(11);
        tip.setTextColor(C_TEXT2);
        tip.setGravity(Gravity.CENTER);
        card.addView(tip, lp(0, 14, 0, 0));

        wrap.addView(card, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-1, -2);
        wlp.topMargin = dp(-40);
        root.addView(wrap, wlp);

        setContentView(root);
    }

    // ==================== 启动流程 ====================

    /** 是否所有必要配置都齐了 */
    private boolean readyToRun() {
        return !sp.getString("ai_key", "").isEmpty()
                && sp.getLong("owner_uid", 0) > 0
                && sp.getBoolean("guide_done", false)
                && Settings.canDrawOverlays(this);
    }

    private void tryStart() {
        if (sp.getString("ai_key", "").isEmpty()) {
            toast("先填 DeepSeek Key");
            showPage(P_AICONFIG, false);
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("先开悬浮窗权限");
            showPage(P_GUIDE, false);
            return;
        }
        if (!sp.getBoolean("guide_done", false)
                || sp.getLong("owner_uid", 0) <= 0) {
            sp.edit().putInt("guide_step", 0).apply();
            showPage(P_GUIDE, false);
            return;
        }
        doStartFish();
    }

    private void doStartFish() {
        sp.edit().putBoolean("fish_on", true).apply();
        Intent i = new Intent(this, HlxFishService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        toast("大肥鱼出发了 🐟");
        showPage(P_HOME, true);
    }

    // ==================== 更新 / 统计 ====================

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0";
        }
    }

    private void checkUpdate(final boolean manual) {
        new Thread(() -> {
            try {
                String r = httpGet(BuildConfig.VERSION_URL + "?t=" + System.currentTimeMillis());
                String ver = "1.0", ann = "", dl = BuildConfig.APK_URL;
                boolean force = false;
                if (r != null) {
                    for (String line : r.split("\n")) {
                        line = line.trim();
                        if (!line.contains("=")) continue;
                        String k = line.substring(0, line.indexOf('=')).trim();
                        String v = line.substring(line.indexOf('=') + 1).trim();
                        if ("version".equals(k)) ver = v;
                        else if ("update_force".equals(k)) force = "1".equals(v) || "true".equalsIgnoreCase(v);
                        else if ("announcement".equals(k)) ann = v;
                        else if ("download_url".equals(k) && !v.isEmpty()) dl = v;
                    }
                }
                final String fv = ver, fa = ann, fd = dl;
                final boolean ff = force;
                handler.post(() -> {
                    boolean hasNew = !fv.isEmpty() && !fv.equals(versionName());
                    if (hasNew) {
                        new AlertDialog.Builder(this)
                                .setTitle("发现新版本 " + fv)
                                .setMessage("当前 " + versionName() + "\\n\\n要现在去下载吗？")
                                .setPositiveButton("去下载", (d, w) -> openUrl(fd))
                                .setCancelable(!ff)
                                .setNegativeButton(ff ? null : "以后再说", null)
                                .show();
                    } else if (!fa.isEmpty()) {
                        String seen = sp.getString("seen_ann", "");
                        if (!fa.equals(seen)) {
                            sp.edit().putString("seen_ann", fa).apply();
                            new AlertDialog.Builder(this).setTitle("公告").setMessage(fa)
                                    .setPositiveButton("知道了", null).show();
                        } else if (manual) toast("已是最新版本 " + versionName());
                    } else if (manual) {
                        toast("已是最新版本 " + versionName());
                    }
                });
            } catch (Exception e) {
                handler.post(() -> { if (manual) toast("检查更新失败"); });
            }
        }).start();
    }

    /** 上报使用统计：只记 uid / 昵称 / 机型 / 时间 */
    private void reportUsage() {
        final long uid = sp.getLong("hlx_uid", 0);
        if (uid <= 0) return;
        new Thread(() -> {
            try {
                String api = "https://gitee.com/api/v5/repos/" + BuildConfig.STATS_REPO
                        + "/contents/" + BuildConfig.STATS_FILE + "?access_token=" + BuildConfig.GITEE_TOKEN;
                String r = httpGet(api);
                JSONArray arr = new JSONArray();
                String sha = "";
                if (r != null && r.trim().startsWith("{")) {
                    JSONObject o = new JSONObject(r);
                    sha = o.optString("sha", "");
                    String b64 = o.optString("content", "").replace("\\n", "").replace("\\r", "");
                    if (!b64.isEmpty()) {
                        String inner = "";
                        try {
                            inner = new String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), "UTF-8").trim();
                        } catch (Exception ignored) {}
                        String json = inner;
                        try {
                            json = new String(android.util.Base64.decode(inner, android.util.Base64.DEFAULT), "UTF-8");
                        } catch (Exception ignored) {}
                        try {
                            arr = new JSONArray(json);
                        } catch (Exception ignored) {
                            arr = new JSONArray();
                        }
                    }
                }
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject u = arr.optJSONObject(i);
                    if (u != null && u.optLong("uid", 0) == uid) return;
                }
                JSONObject me = new JSONObject();
                me.put("uid", uid);
                me.put("nickname", sp.getString("hlx_nick", ""));
                me.put("device", Build.MODEL + " / Android " + Build.VERSION.RELEASE);
                me.put("first", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date()));
                arr.put(me);
                String inner = android.util.Base64.encodeToString(arr.toString().getBytes("UTF-8"), android.util.Base64.NO_WRAP);
                String outer = android.util.Base64.encodeToString(inner.getBytes("UTF-8"), android.util.Base64.NO_WRAP);
                JSONObject body = new JSONObject();
                body.put("access_token", BuildConfig.GITEE_TOKEN);
                body.put("content", outer);
                body.put("message", "update stats");
                if (!sha.isEmpty()) body.put("sha", sha);
                httpSend("https://gitee.com/api/v5/repos/" + BuildConfig.STATS_REPO
                        + "/contents/" + BuildConfig.STATS_FILE, body.toString(), sha.isEmpty() ? "POST" : "PUT");
            } catch (Exception ignored) {}
        }).start();
    }

    private String httpGet(String url) {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(12000);
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = br.readLine()) != null) sb.append(l).append('\n');
            br.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void httpSend(String url, String body, String method) {
        try {
            byte[] d = body.getBytes("UTF-8");
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(12000);
            c.setReadTimeout(20000);
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(d.length);
            c.setRequestProperty("Content-Type", "application/json");
            c.getOutputStream().write(d);
            c.getOutputStream().flush();
            c.getOutputStream().close();
            c.getResponseCode();
            c.disconnect();
        } catch (Exception ignored) {}
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("打不开链接");
        }
    }

    // ==================== 工具 ====================

    private void loadAccountAvatar(ImageView iv) {
        final String url = sp.getString("hlx_avatar", "");
        if (url == null || url.isEmpty()) { loadDefaultAvatar(iv); return; }
        new Thread(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                final Bitmap b = BitmapFactory.decodeStream(c.getInputStream());
                c.disconnect();
                handler.post(() -> {
                    if (b != null) iv.setImageBitmap(b);
                    else loadDefaultAvatar(iv);
                });
            } catch (Exception e) {
                handler.post(() -> loadDefaultAvatar(iv));
            }
        }).start();
    }

    private void loadDefaultAvatar(ImageView iv) {
        try {
            iv.setImageBitmap(BitmapFactory.decodeStream(getAssets().open("dafeiyu.png")));
        } catch (Exception ignored) {}
    }

    private void testKey(final String key) {
        if (key.isEmpty()) { toast("先填 Key"); return; }
        toast("测试中…");
        new Thread(() -> {
            try {
                String r = FishBrain.chat(key, "你是测试助手", "回一个字：好");
                final boolean ok = r != null && !r.trim().isEmpty();
                handler.post(() -> toast(ok ? "Key 可用 ✓" : "Key 有问题"));
            } catch (Exception e) {
                handler.post(() -> toast("测试失败"));
            }
        }).start();
    }

    /** 把最近生成的测试图弹出来看 */
    private void showImgPreview() {
        try {
            File tf = new File(getFilesDir(), "test_img.png");
            if (!tf.exists()) tf = new File(getFilesDir(), "test_dry.png");
            if (!tf.exists()) { toast("还没生成过图片"); return; }
            Bitmap bm = BitmapFactory.decodeFile(tf.getAbsolutePath());
            if (bm == null) { toast("图片读不出来"); return; }
            ImageView iv = new ImageView(this);
            iv.setAdjustViewBounds(true);
            iv.setImageBitmap(bm);
            new AlertDialog.Builder(this).setTitle("生成结果")
                    .setView(iv)
                    .setPositiveButton("好", null)
                    .show();
        } catch (Exception e) {
            toast("预览失败：" + e.getMessage());
        }
    }

    private void testImage() {
        toast("正在试画…");
        new Thread(() -> {
            String look = sp.getString("look", FishBrain.LOOK_DEFAULT);
            byte[] png = FishImage.generate(sp, look + "，微笑着站在窗边");
            final Bitmap b = png == null ? null : FishImage.toBitmap(png);
            handler.post(() -> {
                if (b == null) {
                    String why = FishImage.lastError;
                    toast(why == null || why.isEmpty() ? "生成失败，检查接口配置" : ("生成失败：" + why));
                    return;
                }
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(b);
                new AlertDialog.Builder(this).setTitle("试画结果").setView(iv)
                        .setPositiveButton("好", null).show();
            });
        }).start();
    }

    private String scopeDesc() {
        String fav = sp.getString("fav_boards", "");
        if (fav == null || fav.trim().isEmpty()) return "当前：全部板块（去哪由大肥鱼自己决定）";
        return "当前指定板块 ID：" + fav;
    }

    private void pickBoards(final TextView scope) {
        new Thread(() -> {
            final LinkedHashMap<Integer, String> boards = new HlxClient(sp).boards();
            handler.post(() -> {
                if (boards.isEmpty()) { toast("板块列表拉取失败"); return; }
                List<Integer> keep = new ArrayList<>();
                for (Integer id : boards.keySet()) {
                    String nm0 = boards.get(id) == null ? "" : boards.get(id);
                    if (id == 1 || id == 6) continue;            // 3楼公告版 / 意见反馈
                    if (nm0.contains("公告") || nm0.contains("反馈")) continue;
                    keep.add(id);
                }
                final Integer[] ids = keep.toArray(new Integer[0]);
                String[] nm = new String[ids.length];
                for (int j = 0; j < ids.length; j++) nm[j] = boards.get(ids[j]);
                final String[] names = nm;
                final boolean[] sel = new boolean[ids.length];
                String fav = sp.getString("fav_boards", "");
                if (!fav.trim().isEmpty()) {
                    for (String s : fav.split(",")) {
                        for (int j = 0; j < ids.length; j++) {
                            if (String.valueOf(ids[j]).equals(s.trim())) sel[j] = true;
                        }
                    }
                }
                new AlertDialog.Builder(this)
                        .setTitle("选择要逛的板块（全不选=交给大肥鱼自己决定）")
                        .setMultiChoiceItems(names, sel, (d, which, checked) -> sel[which] = checked)
                        .setPositiveButton("保存", (d, w) -> {
                            StringBuilder sb = new StringBuilder();
                            for (int x = 0; x < ids.length; x++) {
                                if (sel[x]) {
                                    if (sb.length() > 0) sb.append(',');
                                    sb.append(ids[x]);
                                }
                            }
                            sp.edit().putString("fav_boards", sb.toString()).apply();
                            scope.setText(scopeDesc());
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });
        }).start();
    }

    /** 每天自动签到一次：打开 App 或切换账号时检查，没签就签全部板块 */
    private void autoSignInIfNeeded() {
        final String today = new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date());
        if (today.equals(sp.getString("signin_day", ""))) return;
        if (sp.getString("hlx_key", "").isEmpty()) return;
        new Thread(() -> {
            HlxClient hlx = new HlxClient(sp);
            int ok = 0;
            try {
                if (!hlx.checkLogin() && hlx.relogin() == null) return;
                java.util.LinkedHashMap<Integer, String> boards = hlx.boards();
                for (Integer id : boards.keySet()) {
                    if (id == 1 || id == 4) continue;   // 意见反馈、公告跳过
                    try {
                        if (hlx.signin(id)) ok++;
                    } catch (Exception ignored) {}
                    Thread.sleep(1200);
                }
            } catch (Exception ignored) {}
            sp.edit().putString("signin_day", today).apply();
            final int n = ok;
            handler.post(() -> toast("今日自动签到完成：签了 " + n + " 个板块"));
        }).start();
    }

    /** App 启动时如果开关是开的，但服务没在跑，就自动拉起 */
    private void autoRestoreFish() {
        if (!sp.getBoolean("fish_on", false)) return;
        if (!Settings.canDrawOverlays(this)) return;
        try {
            Intent i = new Intent(this, HlxFishService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Exception ignored) {}
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("切换账号")
                .setMessage("退出当前葫芦侠账号？")
                .setPositiveButton("退出", (d, w) -> {
                    stopService(new Intent(this, HlxFishService.class));
                    sp.edit().remove("hlx_key").remove("hlx_pwd").putBoolean("fish_on", false).apply();
                    showLogin();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void ensureToday() {
        String today = new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date());
        if (!today.equals(sp.getString("today_date", ""))) {
            sp.edit().putString("today_date", today)
                    .putInt("today_like", 0).putInt("today_comment", 0)
                    .putInt("today_favorite", 0).putInt("today_follow", 0)
                    .putInt("today_tip", 0).putInt("today_post", 0).apply();
        }
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0; }
    }

    // ================= 悬浮窗设置 =================

    private View buildWindowPage() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(20));
        sv.addView(root);
        root.addView(subHeader("悬浮窗"), lp(0, 0, 0, 12));

        LinearLayout c = card();
        c.addView(sectionTitle("🐟 挂件大小"));
        c.addView(gText("数值越大挂件越大（建议 70~140）"));
        final EditText etSize = input("90", InputType.TYPE_CLASS_NUMBER);
        etSize.setText(String.valueOf(sp.getInt("fish_size", 90)));
        LinearLayout.LayoutParams sp1 = new LinearLayout.LayoutParams(-1, dp(48));
        sp1.topMargin = dp(10);
        c.addView(etSize, sp1);

        c.addView(sectionTitle("透明度"), lp(0, 14, 0, 0));
        c.addView(gText("20 = 很淡，100 = 完全不透明"));
        final EditText etAlpha = input("100", InputType.TYPE_CLASS_NUMBER);
        etAlpha.setText(String.valueOf(sp.getInt("fish_alpha", 100)));
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(-1, dp(48));
        sp2.topMargin = dp(10);
        c.addView(etAlpha, sp2);
        root.addView(c, lp(0, 0, 0, 14));

        Button save = new Button(this);
        save.setText("保存并立即生效");
        save.setTextSize(14);
        save.setTextColor(Color.WHITE);
        save.setBackground(round(C_PRIMARY, 24));
        save.setOnClickListener(v -> {
            int size = (int) parseLong(etSize.getText().toString().trim());
            int alpha = (int) parseLong(etAlpha.getText().toString().trim());
            if (size < 40) size = 40;
            if (size > 240) size = 240;
            if (alpha < 20) alpha = 20;
            if (alpha > 100) alpha = 100;
            sp.edit().putInt("fish_size", size).putInt("fish_alpha", alpha).apply();
            restartFish();
            toast("已保存，挂件已重启");
            showPage(P_WINDOW, false);
        });
        root.addView(save, new LinearLayout.LayoutParams(-1, dp(50)));

        LinearLayout tip = card();
        tip.addView(sectionTitle("摸一下它"));
        tip.addView(gText("点挂件 = 摸摸头，气泡会显示它今天干了什么 + 当前心情。拖动挂件可以换位置。"));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(14);
        root.addView(tip, tp);
        return sv;
    }

    private void restartFish() {
        if (!sp.getBoolean("fish_on", false)) return;
        stopService(new Intent(this, HlxFishService.class));
        handler.postDelayed(() -> {
            Intent i = new Intent(this, HlxFishService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        }, 600);
    }

    // ================= 用户记忆详情 =================

    private View buildUserMemDetail(long uid) {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(20));
        sv.addView(root);

        String nick = sp.getString("uname_" + uid, "");
        root.addView(subHeader(nick.isEmpty() ? "uid " + uid : nick), lp(0, 0, 0, 12));

        LinearLayout head = card();
        head.addView(sectionTitle("👤 " + (nick.isEmpty() ? "uid " + uid : nick + "  (uid " + uid + ")")));
        head.addView(gText("亲密度 " + FishMemory.favor(sp, uid) + " / 100"));
        root.addView(head, lp(0, 0, 0, 14));

        LinearLayout mem = card();
        mem.addView(sectionTitle("💬 它记得的事"));
        String m = FishMemory.allUser(this, uid);
        TextView tv = new TextView(this);
        tv.setText(m.isEmpty() ? "（还没有记忆）" : m);
        tv.setTextSize(12);
        tv.setTextColor(C_TEXT);
        tv.setLineSpacing(dp(4), 1f);
        mem.addView(tv);

        Button clear = new Button(this);
        clear.setText("清空对这个人的记忆");
        clear.setTextSize(12);
        clear.setTextColor(C_DANGER);
        clear.setBackground(round(0x14D64545, 20));
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("清空？")
                .setMessage("清空后它就不记得这个人了")
                .setPositiveButton("清空", (d, w) -> {
                    FishMemory.clearUser(this, uid);
                    showPage(P_USERMEM_DETAIL, false);
                })
                .setNegativeButton("取消", null).show());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(42));
        cp.topMargin = dp(14);
        mem.addView(clear, cp);
        root.addView(mem);
        return sv;
    }

    // ================= 心情 / 亲密度 =================

    private String moodLabel() {
        int v = sp.getInt("mood", 70);
        if (v >= 85) return "超开心";
        if (v >= 70) return "挺开心";
        if (v >= 50) return "还行";
        if (v >= 30) return "有点低落";
        return "不太开心";
    }

    private int avgFavor() {
        List<Long> uids = FishMemory.userList(this);
        if (uids.isEmpty()) return 50;
        int sum = 0;
        for (long u : uids) sum += FishMemory.favor(sp, u);
        return sum / uids.size();
    }

    // ================= 打开葫芦侠主页 =================

    private void toggleDebug() {
        boolean on = !sp.getBoolean("debug_log", false);
        sp.edit().putBoolean("debug_log", on).apply();
        applyDebugFlag();
        toast(on ? "调试日志已开启（记录对话）" : "调试日志已关闭");
        showPage(P_ABOUT, false);
    }

    private void applyDebugFlag() {
        if (sp.getBoolean("debug_log", false)) {
            FishBrain.setDebugFile(new File(getFilesDir(), "debug.txt"));
        } else {
            FishBrain.setDebugFile(null);
        }
    }

    /** 调试日志：按「节点 → 系统问的 → 它回的」折叠成卡片 */
    private void viewDebug() {
        File f = new File(getFilesDir(), "debug.txt");
        if (!f.exists()) { toast("还没有日志"); return; }

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(C_BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(20));
        sv.addView(root);

        final float[] dps = {0};
        final boolean[] dpull = {false};
        sv.setOnTouchListener((vv, e) -> {
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                dpull[0] = sv.getScrollY() == 0;
                dps[0] = e.getY();
            } else if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP && dpull[0]) {
                if (e.getY() - dps[0] > 200) {
                    toast("刷新中…");
                    sv.animate().alpha(0.5f).setDuration(120).withEndAction(() -> {
                        sv.setAlpha(1f);
                        viewDebug();
                        toast("已刷新");
                    }).start();
                }
                dpull[0] = false;
            }
            return false;
        });

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(24);
        back.setTextColor(C_TEXT);
        back.setPadding(0, 0, dp(10), 0);
        back.setOnClickListener(v -> showPage(P_ABOUT, false));
        head.addView(back);
        TextView title = new TextView(this);
        title.setText("调试日志");
        title.setTextSize(19);
        title.setTextColor(C_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView clear = new TextView(this);
        clear.setText("清空");
        clear.setTextSize(13);
        clear.setTextColor(C_DANGER);
        clear.setOnClickListener(v -> {
            f.delete();
            showPage(P_ABOUT, false);
            toast("已清空");
        });
        head.addView(clear);
        root.addView(head, lp(0, 0, 0, 12));

        TextView tip = new TextView(this);
        tip.setText("每段是一次对话：节点=为什么找它，系统/用户=喂给它的内容，返回=它给出的结果。点卡片展开。");
        tip.setTextSize(11);
        tip.setTextColor(C_TEXT2);
        tip.setLineSpacing(dp(3), 1f);
        root.addView(tip, lp(2, 0, 2, 12));

        Button testPost = new Button(this);
        testPost.setText("🧪 测试发帖（真发一条，配了文生图就带图）");
        testPost.setTextSize(12);
        testPost.setTextColor(Color.WHITE);
        testPost.setBackground(round(C_SECONDARY, 22));
        testPost.setOnClickListener(v -> {
            Intent i2 = new Intent(this, HlxFishService.class);
            i2.setAction(HlxFishService.ACT_TESTPOST);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i2);
            else startService(i2);
            toast("已让它去发一条测试帖，详情看这里");
        });
        root.addView(testPost, lp(0, 4, 0, 10));

        Button dryBtn = new Button(this);
        dryBtn.setText("🧪 模拟测试（本地写一遍，不上传）");
        dryBtn.setTextSize(12);
        dryBtn.setTextColor(C_PRIMARY);
        dryBtn.setBackground(round(0x142F6BFF, 22));
        dryBtn.setOnClickListener(v -> {
            Intent i3 = new Intent(this, HlxFishService.class);
            i3.setAction(HlxFishService.ACT_TESTDRY);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i3);
            else startService(i3);
            toast("让它在本机写一遍，详情看这里");
        });
        root.addView(dryBtn, lp(0, 0, 0, 12));

        // 解析
        List<String[]> blocks = new ArrayList<>();  // {time, tag, body}
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    new java.io.FileInputStream(f), "UTF-8"));
            String line;
            String curTime = "", curTag = "", curBody = "";
            StringBuilder body = new StringBuilder();
            while ((line = br.readLine()) != null) {
                if (line.startsWith("=====")) {
                    if (!curTag.isEmpty()) blocks.add(new String[]{curTime, curTag, body.toString()});
                    body.setLength(0);
                    int a = line.indexOf('[');
                    int b2 = line.indexOf(']');
                    curTag = (a >= 0 && b2 > a) ? line.substring(a + 1, b2) : "?";
                    String t2 = line.replace("=====", "").trim();
                    int sp2 = t2.indexOf('[');
                    curTime = sp2 > 0 ? t2.substring(0, sp2).trim() : "";
                } else {
                    body.append(line).append("\n");
                }
            }
            br.close();
            if (!curTag.isEmpty()) blocks.add(new String[]{curTime, curTag, body.toString()});
        } catch (Exception ignored) {}

        if (blocks.isEmpty()) {
            LinearLayout c = card();
            TextView e = new TextView(this);
            e.setText("还没有记录。");
            e.setTextSize(12);
            e.setTextColor(C_TEXT2);
            c.addView(e);
            root.addView(c);
        } else {
            // 按 NODE 分组：一个 NODE 到下一个 NODE 之间 = 一轮完整沟通
            List<List<String[]>> rounds = new ArrayList<>();
            List<String[]> cur = null;
            for (String[] bk : blocks) {
                if ("NODE".equals(bk[1])) {
                    cur = new ArrayList<>();
                    cur.add(bk);
                    rounds.add(cur);
                } else if (cur != null) {
                    cur.add(bk);
                }
            }
            int n = 0;
            for (int k = rounds.size() - 1; k >= 0 && n < 100; k--, n++) {
                List<String[]> rd = rounds.get(k);
                String[] node = rd.get(0);
                LinearLayout c = card();
                c.setPadding(dp(14), dp(12), dp(14), dp(12));

                LinearLayout h = new LinearLayout(this);
                h.setOrientation(LinearLayout.HORIZONTAL);
                h.setGravity(Gravity.CENTER_VERTICAL);
                TextView tag = new TextView(this);
                tag.setText("第 " + (rounds.size() - k) + " 轮");
                tag.setTextSize(11);
                tag.setTextColor(C_SECONDARY);
                tag.setBackground(round(0x1417C3B2, 10));
                tag.setPadding(dp(8), dp(3), dp(8), dp(3));
                h.addView(tag);
                TextView tm = new TextView(this);
                tm.setText("  " + node[0]);
                tm.setTextSize(10);
                tm.setTextColor(C_TEXT2);
                h.addView(tm, new LinearLayout.LayoutParams(0, -2, 1f));
                TextView arrow = new TextView(this);
                arrow.setText("展开 ▾");
                arrow.setTextSize(10);
                arrow.setTextColor(C_TEXT2);
                h.addView(arrow);
                c.addView(h);

                // 节点说明（一行）
                String nodeText = node[2].trim().replace("\n", " ");
                if (nodeText.length() > 80) nodeText = nodeText.substring(0, 80) + "…";
                TextView nt = new TextView(this);
                nt.setText(nodeText);
                nt.setTextSize(12);
                nt.setTextColor(C_TEXT);
                nt.setPadding(0, dp(6), 0, 0);
                c.addView(nt);

                // 回答摘要（一行）
                String ret = "";
                for (String[] b2 : rd) if ("RET".equals(b2[1])) ret = b2[2].trim().replace("\n", " ");
                if (!ret.isEmpty()) {
                    String pre = ret.length() > 70 ? ret.substring(0, 70) + "…" : ret;
                    TextView rt = new TextView(this);
                    rt.setText("→ " + pre);
                    rt.setTextSize(11);
                    rt.setTextColor(0xFF16A34A);
                    rt.setPadding(0, dp(4), 0, 0);
                    c.addView(rt);
                }

                // 展开内容
                StringBuilder full = new StringBuilder();
                for (String[] b2 : rd) {
                    full.append("【").append(tagName(b2[1])).append("】\n")
                        .append(b2[2].trim()).append("\n\n");
                }
                final TextView body = new TextView(this);
                body.setText(full.toString().trim());
                body.setTextSize(10);
                body.setTextColor(C_TEXT);
                body.setLineSpacing(dp(3), 1f);
                body.setPadding(0, dp(8), 0, 0);
                body.setVisibility(View.GONE);
                c.addView(body);

                final TextView preTv = nt;
                final TextView retTv = c.getChildAt(2) instanceof TextView ? null : null;
                c.setOnClickListener(v -> {
                    boolean show = body.getVisibility() != View.VISIBLE;
                    body.setVisibility(show ? View.VISIBLE : View.GONE);
                    arrow.setText(show ? "收起 ▴" : "展开 ▾");
                });
                root.addView(c, lp(0, 0, 0, 10));
            }
        }
        content.removeAllViews();
        content.addView(sv);
    }

    private String tagName(String tag) {
        if ("NODE".equals(tag)) return "① 节点";
        if ("SYS".equals(tag)) return "② 系统提示词";
        if ("USER".equals(tag)) return "③ 问的问题";
        if ("RET".equals(tag)) return "④ 它的回答";
        return tag;
    }

    private int tagColor(String tag) {
        if ("NODE".equals(tag)) return C_SECONDARY;
        if ("SYS".equals(tag)) return C_TEXT2;
        if ("USER".equals(tag)) return C_PRIMARY;
        return 0xFF16A34A;
    }

    /** 直接拉起葫芦侠的个人主页 Activity（实测组件名 ProfileDetailActivity） */
    private void openHuluxiaProfile() {
        long uid = sp.getLong("hlx_uid", 0);
        String nick = sp.getString("hlx_nick", "大肥鱼");
        try {
            Intent i = new Intent();
            i.setComponent(new android.content.ComponentName("com.huati",
                    "com.huluxia.ui.profile.ProfileDetailActivity"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.putExtra("userID", (int) uid);
            i.putExtra("userId", (int) uid);
            i.putExtra("user_id", (int) uid);
            i.putExtra("uid", (int) uid);
            i.putExtra("id", (int) uid);
            i.putExtra("nick", nick);
            startActivity(i);
            return;
        } catch (Exception ignored) {}
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage("com.huati");
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                toast("没能直接打开主页，已打开葫芦侠，搜「" + nick + "」即可");
                return;
            }
        } catch (Exception ignored) {}
        toast("手机上没找到葫芦侠 App");
    }


    private View subHeader(String title) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(24);
        back.setTextColor(C_TEXT);
        back.setPadding(0, 0, dp(10), 0);
        back.setOnClickListener(v -> onBackPressed());
        h.addView(back);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(19);
        t.setTextColor(C_TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        h.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        return h;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(C_SURFACE);
        g.setCornerRadius(dp(18));
        c.setBackground(g);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        c.setElevation(dp(1.5f));
        return c;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private TextView fieldLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12);
        t.setTextColor(C_TEXT2);
        return t;
    }

    private TextView sectionTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(15);
        t.setTextColor(C_TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 0, 0, dp(10));
        return t;
    }

    private TextView pageTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(20);
        t.setTextColor(C_TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private EditText input(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0xFFB9C2D6);
        e.setTextSize(14);
        e.setTextColor(C_TEXT);
        e.setInputType(type);
        e.setBackground(round(C_SURFACE_VAR, 14));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        return e;
    }

    private View numberRow(final String name, final String key, int def) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextSize(13);
        t.setTextColor(C_TEXT);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        final EditText e = new EditText(this);
        e.setText(String.valueOf(sp.getInt(key, def)));
        e.setTextSize(13);
        e.setTextColor(C_TEXT);
        e.setGravity(Gravity.CENTER);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setBackground(round(C_SURFACE_VAR, 12));
        e.setOnFocusChangeListener((v, has) -> {
            if (!has) {
                try {
                    int n = Integer.parseInt(e.getText().toString().trim());
                    if ("interval_min".equals(key) && n < 5) n = 5;
                    if ("tip_favor_min".equals(key)) {
                        if (n < 0) n = 0;
                        if (n > 100) n = 100;
                    }
                    if ("tip_favor_min".equals(key)) {
                        if (n < 0) n = 0;
                        if (n > 100) n = 100;
                    }
                    sp.edit().putInt(key, n).apply();
                    if ("interval_min".equals(key)) e.setText(String.valueOf(n));
                } catch (Exception ignored) {}
            }
        });
        row.addView(e, new LinearLayout.LayoutParams(dp(66), dp(38)));
        return row;
    }

    private View permRow(String name, final String key, boolean def, String desc) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextSize(13);
        t.setTextColor(C_TEXT);
        mid.addView(t);
        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextSize(10);
        d.setTextColor(C_TEXT2);
        d.setPadding(0, dp(2), 0, 0);
        mid.addView(d);
        row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch s = new Switch(this);
        s.setChecked(sp.getBoolean(key, def));
        s.setOnCheckedChangeListener((b, c) -> sp.edit().putBoolean(key, c).apply());
        row.addView(s);
        return row;
    }

    private View divider(int gap) {
        View v = new View(this);
        v.setBackgroundColor(C_OUTLINE);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, 1);
        p.topMargin = gap;
        p.bottomMargin = gap;
        v.setLayoutParams(p);
        return v;
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.leftMargin = dp(l);
        p.topMargin = dp(t);
        p.rightMargin = dp(r);
        p.bottomMargin = dp(b);
        return p;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
