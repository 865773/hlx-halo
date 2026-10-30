package dudu.hlx.halo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * 大肥鱼服务：悬浮挂件 + 自主决策 + 双保险去重 + 记忆 + 文生图 + 秒回
 */
public class HlxFishService extends Service {

    public static final String CHANNEL = "fish_hlx";
    private static final java.util.HashSet<String> KNOWN_ACTIONS = new java.util.HashSet<>(java.util.Arrays.asList(
            "like", "unlike", "favorite", "unfavorite", "comment", "reply", "follow", "unfollow",
            "tip", "tip_comment", "tip_user_post", "tip_my_post", "delete_post", "delete_comment",
            "delete_my_post", "unfollow_index", "comment_my_post", "list_self", "signin", "post",
            "goto", "none", "user_info", "user_posts", "like_user_post", "favorite_user_post",
            "comment_user_post"));
    public static final String ACT_ROAM = "dudu.hlx.halo.ROAM";
    public static final String ACT_TESTPOST = "dudu.hlx.halo.TESTPOST";
    public static final String ACT_TESTDRY = "dudu.hlx.halo.TESTDRY";
    private final java.util.concurrent.atomic.AtomicBoolean testBusy = new java.util.concurrent.atomic.AtomicBoolean(false);

    private SharedPreferences sp;
    private WindowManager wm;
    private ImageView fishView;
    private WindowManager.LayoutParams lp;
    private TextView bubble;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private volatile boolean running = true;
    private volatile boolean busy = false;
    private volatile boolean testPostMode = false;
    private volatile String tipBlockMsg = null;
    private final java.util.concurrent.atomic.AtomicBoolean replyBusy =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private long lastWake = 0;
    private long lastReply = 0;

    // ===== 桌宠帧动画 =====
    private final java.util.Map<String, Bitmap[]> petFrames = new java.util.HashMap<>();
    private final android.os.Handler petHandler = new android.os.Handler(Looper.getMainLooper());
    private Runnable petTick;
    private String petState = "idle";
    private int petIdx = 0;
    private volatile int lastActDone = 0;
    private volatile String tipSrcText = "";

    private int tLike, tComment, tFav, tFollow, tTip, tPost;

    @Override
    public void onCreate() {
        super.onCreate();
        sp = getSharedPreferences("fish", MODE_PRIVATE);
        lastWake = sp.getLong("last_wake_at", 0);   // 跨重启保持唤醒间隔
        startForegroundNotice();
        addFish();
        loadToday();
        startWakeLoop();
        startReplyLoop();
        startKeyCheckLoop();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACT_ROAM.equals(intent.getAction())) lastWake = 0;
        if (intent != null && ACT_TESTDRY.equals(intent.getAction())) {
            if (testBusy.compareAndSet(false, true)) {
                new Thread(() -> {
                    try {
                        String key = sp.getString("ai_key", "");
                        if (key.isEmpty()) { log("🧪 模拟测试：还没配 Key"); return; }
                        HlxClient hlx = new HlxClient(sp);
                        if (!hlx.checkLogin() && hlx.relogin() == null) { log("🧪 模拟测试：登录失效"); return; }
                        doTestDry(hlx, key);
                    } finally {
                        testBusy.set(false);
                    }
                }).start();
            }
        }
        if (intent != null && ACT_TESTPOST.equals(intent.getAction())) {
            if (testBusy.compareAndSet(false, true)) {
                new Thread(() -> {
                    try {
                        String key = sp.getString("ai_key", "");
                        if (key.isEmpty()) { log("测试发帖：还没配 Key"); return; }
                        HlxClient hlx = new HlxClient(sp);
                        if (!hlx.checkLogin() && hlx.relogin() == null) { log("测试发帖：登录失效"); return; }
                        sp.edit().putString("post_hint", "发一条测试帖，说说你今天的心情").apply();
                        log("🧪 开始测试发帖…");
                        testPostMode = true;
                        try { doPost(hlx, key, 2); } finally { testPostMode = false; }
                    } finally {
                        testBusy.set(false);
                    }
                }).start();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        removeFish();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) { return null; }

    // ================= 通知 =================

    private void startForegroundNotice() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "大肥鱼", NotificationManager.IMPORTANCE_LOW));
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = (Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this))
                .setContentTitle("大肥鱼在三楼逛着呢")
                .setContentText("点开看它今天做了什么")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        startForeground(1, n);
    }

    // ================= 挂件 =================

    private Bitmap loadFishBitmap() {
        try {
            File f = new File(getFilesDir(), "look.png");
            if (f.exists()) {
                Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (b != null) return b;
            }
        } catch (Exception ignored) {}
        try {
            return BitmapFactory.decodeStream(getAssets().open("dafeiyu.png"));
        } catch (Exception e) {
            return null;
        }
    }

    private void addFish() {
        if (!Settings.canDrawOverlays(this)) return;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        fishView = new ImageView(this);
        Bitmap bmp = loadFishBitmap();
        if (bmp != null) fishView.setImageBitmap(bmp);
        else fishView.setImageResource(android.R.drawable.ic_menu_compass);
        int size = sp.getInt("fish_size", 90);
        lp = new WindowManager.LayoutParams(dp(size), dp(size),
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = sp.getInt("fish_x", 40);
        lp.y = sp.getInt("fish_y", 300);
        float alpha = sp.getInt("fish_alpha", 100) / 100f;
        if (alpha < 0.2f) alpha = 0.2f;
        if (alpha > 1f) alpha = 1f;
        fishView.setAlpha(alpha);

        fishView.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY; int startX, startY; long downT; boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX(); downY = e.getRawY();
                        startX = lp.x; startY = lp.y;
                        downT = System.currentTimeMillis(); moved = false;
                        playPet("drag", 120, true, null);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        if (Math.abs(dx) > 12 || Math.abs(dy) > 12) moved = true;
                        if (moved) {
                            lp.x = (int) (startX + dx); lp.y = (int) (startY + dy);
                            try { wm.updateViewLayout(fishView, lp); } catch (Exception ignored) {}
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        if (!moved && System.currentTimeMillis() - downT < 400) {
                            playPet("happy", 110, false, "idle");
                            showToday();
                        } else {
                            sp.edit().putInt("fish_x", lp.x).putInt("fish_y", lp.y).apply();
                            playPet("idle", 140, true, null);
                        }
                        return true;
                }
                return false;
            }
        });
        try { wm.addView(fishView, lp); } catch (Exception ignored) {}
        loadPetFrames();
        playPet("idle", 140, true, null);
    }

    private void removeFish() {
        try { if (bubble != null) wm.removeView(bubble); } catch (Exception ignored) {}
        try { if (fishView != null) wm.removeView(fishView); } catch (Exception ignored) {}
        bubble = null; fishView = null;
    }

    /** 点挂件：只显示今天干了什么 */
    private void showToday() {
        showBubble("今天在三楼：\n点赞 " + tLike + " · 评论 " + tComment + " · 收藏 " + tFav
                + "\n关注 " + tFollow + " · 发帖 " + tPost + (tTip > 0 ? " · 打赏 " + tTip : ""));
    }

    private void showBubble(String text) {
        if (wm == null || fishView == null) return;
        try { if (bubble != null) wm.removeView(bubble); } catch (Exception ignored) {}
        bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(13);
        bubble.setTextColor(0xFF1F2937);
        bubble.setPadding(dp(12), dp(9), dp(12), dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dp(14));
        bubble.setBackground(bg);
        WindowManager.LayoutParams blp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        blp.gravity = Gravity.TOP | Gravity.START;
        blp.x = lp.x + dp(80);
        blp.y = Math.max(0, lp.y - dp(56));
        try { wm.addView(bubble, blp); } catch (Exception ignored) {}
        handler.postDelayed(() -> {
            try { if (bubble != null) wm.removeView(bubble); } catch (Exception ignored) {}
            bubble = null;
        }, 7000);
    }

    private String persona() {
        return FishBrain.personaText(sp);
    }

    // ================= Key 检查（5 分钟） =================

    private int keyFails = 0;

    private void startKeyCheckLoop() {
        new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(300_000);
                    if (!running) break;
                    String key = sp.getString("ai_key", "");
                    if (key.isEmpty()) continue;
                    HlxClient hlx = new HlxClient(sp);
                    if (!hlx.checkLogin() && hlx.relogin() == null) {
                        log("⚠️ 登录已失效，停止运行");
                        sp.edit().putBoolean("need_login", true).putBoolean("fish_on", false).apply();
                        stopSelf();
                        break;
                    }
                    if (!FishBrain.keyValid(key)) {
                        keyFails++;
                        log("⚠️ DeepSeek Key 这次没通（第 " + keyFails + " 次），先不停");
                        if (keyFails >= 3) {
                            log("⚠️ DeepSeek Key 连续 3 次不通，停止运行");
                            sp.edit().putBoolean("need_ai_key", true).putBoolean("fish_on", false).apply();
                            stopSelf();
                            break;
                        }
                    } else {
                        keyFails = 0;
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    // ================= 唤醒循环 =================

    private void startWakeLoop() {
        new Thread(() -> {
            while (running) {
                try {
                    int min = Math.max(5, sp.getInt("interval_min", 20));
                    if (System.currentTimeMillis() - lastWake >= min * 60_000L) {
                        try {
                            wakeUp();
                        } finally {
                            // 从这一轮结束开始重新计时，20 分钟内绝不再唤醒
                            lastWake = System.currentTimeMillis();
                            sp.edit().putLong("last_wake_at", lastWake).apply();
                        }
                    }
                    Thread.sleep(20_000);
                } catch (Exception e) {
                    try { Thread.sleep(30_000); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    private void wakeUp() {
        if (busy) return;
        busy = true;
        try {
            HlxClient hlx = new HlxClient(sp);
            String key = sp.getString("ai_key", "");
            if (key.isEmpty()) { log("还没配置 DeepSeek Key"); return; }
            if (!hlx.checkLogin() && hlx.relogin() == null) { log("登录失效"); return; }

            // 新人报道帖
            if (sp.getBoolean("welcome_post_pending", false)) {
                sp.edit().putBoolean("welcome_post_pending", false).apply();
                sp.edit().putString("post_hint", "发一条新人报道帖，简单介绍自己，语气自然可爱，别提AI").apply();
                doPost(hlx, key, 2);
                return;
            }

            try { doDiary(hlx, key); } catch (Exception ignored) {}
            genLife();

            sp.edit().putString("state_text", "正在决定要做什么…").apply();
            int round = sp.getInt("round_today", 0) + 1;
            sp.edit().putInt("round_today", round).apply();

            JSONObject intent = null;
            for (int i = 0; i < 2 && intent == null; i++) {
                String state = "现在是 " + now()
                        + "\n今天是第 " + round + " 轮"
                        + "\n你今天已经做了：点赞" + tLike + " 评论" + tComment + " 收藏" + tFav
                        + " 关注" + tFollow + " 打赏" + tTip + " 发帖" + tPost;
                intent = FishBrain.decideIntent(this, key, persona(), sp, state);
            }
            String think = intent == null ? "随便逛逛" : intent.optString("think", "随便逛逛");
            int catId = intent == null ? 0 : intent.optInt("cat_id", 0);

            // 支持多意图
            List<String> intents = new ArrayList<>();
            JSONArray ia = intent == null ? null : intent.optJSONArray("intents");
            if (ia != null) {
                for (int i = 0; i < ia.length(); i++) intents.add(ia.optString(i, "").toLowerCase());
            }
            if (intents.isEmpty()) {
                String one = intent == null ? "roam" : intent.optString("intent", "roam");
                intents.add(one);
            }
            boolean singleRoam = intents.size() == 1 && "roam".equals(intents.get(0));
            if (!singleRoam) logRound(round, joinList(intents), "", think);

            for (String w : intents) {
                if (!running) break;
                if ("rest".equals(w)) continue;
                if ("signin".equals(w)) { doSignin(hlx); continue; }
                if ("post".equals(w) && sp.getBoolean("perm_post", false)) { doPost(hlx, key, catId); continue; }
                if ("roam".equals(w)) roam(hlx, key, catId, round, think);
            }
            replyPassSafe(hlx, key);
        } catch (Exception e) {
            log("出错：" + e.getMessage());
        } finally {
            busy = false;
            String[] idle = {"在刷抖音", "在吃瓜", "在休息", "在追剧", "在发呆",
                    "在啃白米饭", "在摸鱼", "在看别人的帖子", "在打游戏", "在等饭"};
            sp.edit().putString("state_text", idle[random.nextInt(idle.length)]).apply();
        }
    }

    // ================= 逛社区 =================

    private void roam(HlxClient hlx, String key, int preferCat, int round, String think) {
        try {
            LinkedHashMap<Integer, String> boards = scoped(hlx.boards());
            if (boards.isEmpty()) { log("拿不到板块列表"); return; }
            int catId = preferCat > 0 && boards.containsKey(preferCat) ? preferCat : pickBoard(boards);
            String boardName = boards.get(catId);
            logRound(round, "roam", boardName, think);

            int maxSteps = Math.max(1, sp.getInt("max_steps", 4));
            Set<Long> seen = loadSeen();

            int doneSteps = 0, tries = 0;
            while (doneSteps < maxSteps && tries < maxSteps * 5 && running) {
                tries++;
                List<JSONObject> posts = hlx.posts(catId, 0, 12);
                JSONObject target = null;
                if (posts != null) {
                    for (JSONObject p : posts) {
                        long pid0 = p.optLong("postID", 0);
                        if (!seen.contains(pid0)) { target = p; break; }
                    }
                }
                if (target == null) {
                    int nb = pickBoard(boards);
                    if (nb == catId) break;
                    catId = nb;
                    boardName = boards.get(catId);
                    logBoard(boardName);
                    continue;
                }

                long pid = target.optLong("postID", 0);
                JSONObject au = target.optJSONObject("user");
                int authorId = au != null ? au.optInt("userID", 0) : target.optInt("authorId", 0);
                String author = au != null ? au.optString("nick", "这位")
                        : target.optString("authorNickname", "这位");
                String title = target.optString("title", "");
                String detail = strip(target.optString("detail", ""));
                if (detail.length() > 500) detail = detail.substring(0, 500);

                seen.add(pid);
                saveSeen(seen);
                if (authorId == hlx.uid()) continue;
                if (hasCommented(hlx, pid, key)) continue;
                if (isGibberish(title) || isGibberish(detail)) {
                    log("🙈 乱码帖，跳过：" + pid);
                    continue;
                }

                cacheName(authorId, author);
                String state = "你叫「" + sp.getString("fish_name", "大肥鱼") + "」，标签："
                        + sp.getString("fish_tags", "仿真,真人感,甜美,粘人") + "。\n"
                        + "你在葫芦侠三楼的「" + boardName + "」板块。\n"
                        + "看到一个帖子：\n【标题】" + title + "\n【作者】" + author + "(uid=" + authorId + ")\n"
                        + "【正文】" + (detail.isEmpty() ? "（只有图片）" : detail) + "\n"
                        + "【热度】" + target.optInt("hit", 0) + " 浏览 / " + target.optInt("commentCount", 0) + " 评论\n"
                        + moodContext() + ownerLine(authorId) + FishBrain.memoryBlock(this, sp, authorId)
                        + "cat_id=" + catId + "，post_id=" + pid + "，作者 user_id=" + authorId;

                java.util.List<String> imgs = hlx.postImages(target);
                JSONObject d = null;
                for (int i2 = 0; i2 < 3 && d == null; i2++) {
                    d = FishBrain.decide(this, key, persona(), sp, state, imgs);
                }
                if (d == null) continue;

                // 防机器人：有一定概率这帖只看不动手，像真人一样“划过去”
                if (!fromOwnerTrigger(state) && random.nextInt(9) == 0) {
                    log("🤫 这帖划过去了（不互动）");
                    doneSteps++;
                    randGap();
                    continue;
                }
                act(hlx, key, d, catId, boardName, pid, authorId, author, title, state);
                doneSteps++;
                if (doneSteps >= maxSteps) break;
                randGap();
                // 一条帖换一个板块（尽量覆盖多个板块）；它自己在决策里选了 cat_id 就听它的
                int wantCat = d.optInt("cat_id", 0);
                if (wantCat > 0 && boards.containsKey(wantCat) && wantCat != catId) {
                    catId = wantCat;
                    boardName = boards.get(catId);
                    logBoard(boardName);
                } else {
                    for (int t2 = 0; t2 < 5; t2++) {
                        int nb = pickBoard(boards);
                        if (nb != catId) {
                            catId = nb;
                            boardName = boards.get(catId);
                            logBoard(boardName);
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log("逛社区出错：" + e.getMessage());
        }
    }

    private String ownerLine(int authorId) {
        long owner = sp.getLong("owner_uid", 0);
        if (owner <= 0) return "";
        if (authorId == owner)
            return "【重要】这个作者是你的主人（uid=" + owner + "），你最喜欢最亲近的人，一定要认真回应。\n";
        return "你的主人 uid=" + owner + "（不是这个作者）。\n";
    }

    /** 板块白名单：意见反馈(1)/公告(4) 全局拉黑；选了板块就只在这些板块里逛 */
    private LinkedHashMap<Integer, String> scoped(LinkedHashMap<Integer, String> all) {
        LinkedHashMap<Integer, String> m = new LinkedHashMap<>();
        for (Integer id : all.keySet()) {
            String nm = all.get(id) == null ? "" : all.get(id);
            // 3楼公告版(1)、意见反馈(6) 全局拉黑；标题里带公告/反馈也一并挡掉
            if (id == 1 || id == 6) continue;
            if (nm.contains("公告") || nm.contains("反馈")) continue;
            m.put(id, all.get(id));
        }
        String fav = sp.getString("fav_boards", "");
        if (fav != null && !fav.trim().isEmpty()) {
            LinkedHashMap<Integer, String> s2 = new LinkedHashMap<>();
            for (String x : fav.split(",")) {
                try {
                    int id = Integer.parseInt(x.trim());
                    if (m.containsKey(id)) s2.put(id, m.get(id));
                } catch (Exception ignored) {}
            }
            if (!s2.isEmpty()) return s2;
        }
        return m;
    }

    private int pickBoard(LinkedHashMap<Integer, String> boards) {
        List<Integer> ids = new ArrayList<>(boards.keySet());
        String fav = sp.getString("fav_boards", "");
        if (fav != null && !fav.trim().isEmpty()) {
            List<Integer> scoped = new ArrayList<>();
            for (String s : fav.split(",")) {
                try {
                    int id = Integer.parseInt(s.trim());
                    if (boards.containsKey(id)) scoped.add(id);
                } catch (Exception ignored) {}
            }
            if (!scoped.isEmpty()) ids = scoped;
        }
        return ids.get(random.nextInt(ids.size()));
    }

    // ================= 去重（双保险） =================

    /** 是否已评论过这个帖子：本地 + 云端（云端看帖子里有没有我发的评论） */
    private boolean hasCommented(HlxClient hlx, long pid, String key) {
        if (loadCommented().contains(pid)) return true;
        try {
            for (JSONObject c : hlx.comments(pid, 1)) {
                JSONObject u = c.optJSONObject("user");
                long uid = u == null ? 0 : u.optLong("userID", 0);
                if (uid == hlx.uid()) {
                    markCommented(pid);
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    /** 是否已回复过这条评论：本地 + 云端（云端看我在该帖有没有比这条通知更新的评论） */
    private boolean hasReplied(HlxClient hlx, long pid, long cid, long notifTime) {
        if (loadReplied().contains(cid)) return true;
        try {
            for (int pg = 1; pg <= 2; pg++) {
                for (JSONObject c : hlx.comments(pid, pg)) {
                    JSONObject u = c.optJSONObject("user");
                    long uid = u == null ? 0 : u.optLong("userID", 0);
                    if (uid != hlx.uid()) continue;
                    long ct = c.optLong("createTime", 0);
                    if (notifTime <= 0 || ct >= notifTime) {
                        markReplied(cid);
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void markCommented(long pid) {
        Set<Long> s = loadCommented();
        s.add(pid);
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Long x : s) { if (n++ > 600) break; if (sb.length() > 0) sb.append(','); sb.append(x); }
        sp.edit().putString("commented_tids", sb.toString()).apply();
    }

    private Set<Long> loadCommented() {
        Set<Long> s = new LinkedHashSet<>();
        for (String x : sp.getString("commented_tids", "").split(",")) {
            try { s.add(Long.parseLong(x)); } catch (Exception ignored) {}
        }
        return s;
    }

    private void unmarkReplied(long cid) {
        Set<Long> s = loadReplied();
        if (!s.remove(cid)) return;
        StringBuilder sb = new StringBuilder();
        for (Long x : s) { if (sb.length() > 0) sb.append(','); sb.append(x); }
        sp.edit().putString("replied_cids", sb.toString()).apply();
    }

    private void markReplied(long cid) {
        Set<Long> s = loadReplied();
        s.add(cid);
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Long x : s) { if (n++ > 500) break; if (sb.length() > 0) sb.append(','); sb.append(x); }
        sp.edit().putString("replied_cids", sb.toString()).apply();
    }

    /** 载入桌宠各状态的帧 */
    private void loadPetFrames() {
        if (!petFrames.isEmpty()) return;
        java.util.Map<String, Integer> groups = new java.util.HashMap<>();
        groups.put("idle", 10); groups.put("happy", 6); groups.put("angry", 4);
        groups.put("eat", 4); groups.put("shy", 4); groups.put("drag", 2);
        for (java.util.Map.Entry<String, Integer> e : groups.entrySet()) {
            Bitmap[] arr = new Bitmap[e.getValue()];
            boolean any = false;
            for (int i = 0; i < arr.length; i++) {
                try {
                    arr[i] = BitmapFactory.decodeStream(
                            getAssets().open("pet/" + e.getKey() + "_" + i + ".png"));
                    if (arr[i] != null) any = true;
                } catch (Throwable ignored) {}
            }
            if (any) petFrames.put(e.getKey(), arr);
        }
    }

    /** 播放某个状态的帧：loop=false 时播完回到 back 状态 */
    private void playPet(final String state, final int frameMs, final boolean loop, final String back) {
        final Bitmap[] fr = petFrames.get(state);
        if (fr == null || fr.length == 0 || fishView == null) return;
        if (!state.equals(petState)) petIdx = 0;
        petState = state;
        if (petTick != null) petHandler.removeCallbacks(petTick);
        petTick = new Runnable() {
            @Override public void run() {
                if (fishView == null) return;
                Bitmap bm = fr[petIdx % fr.length];
                if (bm != null) fishView.setImageBitmap(bm);
                petIdx++;
                if (!loop && petIdx >= fr.length) {
                    if (back != null) playPet(back, 140, true, null);
                    return;
                }
                petHandler.postDelayed(this, frameMs);
            }
        };
        petHandler.post(petTick);
    }

    /** 我关注的人（本地缓存，30 分钟刷一次） */
    private java.util.Set<Long> followingSet(HlxClient hlx) {
        java.util.Set<Long> out = new java.util.HashSet<>();
        long at = sp.getLong("following_at", 0);
        String cache = sp.getString("following_cache", "");
        boolean stale = System.currentTimeMillis() - at > 30 * 60_000L;
        if (stale || cache.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            try {
                for (JSONObject f : hlx.followingList(hlx.uid(), 100)) {
                    JSONObject u = f.optJSONObject("user");
                    if (u == null) continue;
                    long id = u.optLong("userID", 0);
                    if (id <= 0) continue;
                    out.add(id);
                    if (sb.length() > 0) sb.append(',');
                    sb.append(id);
                }
                sp.edit().putString("following_cache", sb.toString())
                        .putLong("following_at", System.currentTimeMillis()).apply();
            } catch (Exception ignored) {}
        }
        if (out.isEmpty() && !cache.isEmpty()) {
            for (String x : cache.split(",")) {
                try { out.add(Long.parseLong(x.trim())); } catch (Exception ignored) {}
            }
        }
        return out;
    }

    /** 主人相关的场景不做随机跳过 */
    private boolean fromOwnerTrigger(String state) {
        long owner = sp.getLong("owner_uid", 0);
        if (owner <= 0) return false;
        return state != null && state.contains("你的主人");
    }

    /** 评论内容去重：和最近 15 条太像就不发 */
    private boolean tooSimilar(String text) {
        if (text == null) return true;
        String t = text.trim();
        if (t.isEmpty()) return true;
        String hist = sp.getString("recent_comments", "");
        for (String h : hist.split("\n")) {
            if (h.trim().isEmpty()) continue;
            if (h.trim().equals(t)) return true;
            int common = 0;
            for (int i = 0; i < t.length(); i++) if (h.indexOf(t.charAt(i)) >= 0) common++;
            if (t.length() > 8 && common * 1.0 / t.length() > 0.85) return true;
        }
        return false;
    }

    private void rememberComment(String text) {
        if (text == null || text.trim().isEmpty()) return;
        String hist = sp.getString("recent_comments", "");
        String[] parts = (text.trim() + "\n" + hist).split("\n");
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String x : parts) {
            if (x.trim().isEmpty()) continue;
            if (n++ >= 15) break;
            sb.append(x.trim()).append("\n");
        }
        sp.edit().putString("recent_comments", sb.toString()).apply();
    }

    /** 记录一次对某人的回复，并在高频互刷时标记为疑似机器人 */
    private void replyTick(long uid) {
        try {
            String k = "loop_" + uid;
            String raw = sp.getString(k, "");
            java.util.ArrayList<Long> ts = new java.util.ArrayList<>();
            for (String x : raw.split(",")) {
                try { ts.add(Long.parseLong(x.trim())); } catch (Exception ignored) {}
            }
            long now = System.currentTimeMillis();
            ts.add(now);
            while (ts.size() > 30) ts.remove(0);
            StringBuilder sb = new StringBuilder();
            for (Long t : ts) { if (sb.length() > 0) sb.append(','); sb.append(t); }
            sp.edit().putString(k, sb.toString()).putLong("bot_last_" + uid, now).apply();

            int cnt = 0;
            long prev = 0, sumGap = 0;
            for (Long t : ts) {
                if (now - t > 10 * 60_000L) continue;
                if (cnt > 0) sumGap += (t - prev);
                prev = t;
                cnt++;
            }
            if (cnt >= 8) {
                long avg = cnt > 1 ? sumGap / (cnt - 1) : 0;
                if (avg <= 60_000L) {
                    sp.edit().putLong("bot_" + uid, now)
                            .putLong("loop_mute_" + uid, now + 30 * 60_000L).apply();
                    log("⏸ 和 uid=" + uid + " 回复太频繁（10 分钟 " + cnt + " 次，平均 "
                            + (avg / 1000) + " 秒），按机器人互刷处理，暂停 30 分钟");
                }
            }
        } catch (Exception ignored) {}
    }

    /** 这个人是不是被判定成机器人/正在熔断 */
    private boolean botBlocked(long uid) {
        long now = System.currentTimeMillis();
        if (now < sp.getLong("loop_mute_" + uid, 0)) return true;
        long marked = sp.getLong("bot_" + uid, 0);
        if (marked > 0 && now - marked < 30L * 86400_000L) {
            long last = sp.getLong("bot_last_" + uid, 0);
            return now - last < 60L * 60_000L;      // 机器人：一小时最多回一次
        }
        return false;
    }

    /** 判断是不是乱码/加密帖（base64 之类），这种不互动 */
    private boolean isGibberish(String t) {
        if (t == null) return false;
        String x = t.trim();
        if (x.length() < 12) return false;
        int cjk = 0, b64 = 0, total = 0;
        for (int i = 0; i < x.length(); i++) {
            char c = x.charAt(i);
            if (Character.isWhitespace(c)) continue;
            total++;
            if (c >= 0x4E00 && c <= 0x9FFF) cjk++;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '+' || c == '/' || c == '=') b64++;
        }
        if (total == 0) return false;
        double cjkR = cjk * 1.0 / total, b64R = b64 * 1.0 / total;
        return cjkR < 0.05 && b64R > 0.9 && x.length() >= 20;
    }

    /** 所有操作之间统一 0~15 秒随机间隔 */
    private void randGap() {
        try { Thread.sleep(random.nextInt(15_000)); } catch (Exception ignored) {}
    }

    // ================= 协议归一化（新协议 → 内部动作名） =================

    /** 把 halo-desk 风格的结构化意图转成内部动作名，老格式原样返回 */
    private JSONObject normalize(JSONObject d) {
        if (d == null) return null;
        try {
            JSONArray old = d.optJSONArray("actions");
            if (old != null && old.length() > 0) return d;      // 已经是老格式
            String act = d.optString("action", d.optString("act", "")).trim().toLowerCase();
            if (act.isEmpty()) return d;

            JSONObject t = d.optJSONObject("target");
            String kind = t == null ? "" : t.optString("kind", "").toLowerCase();
            long uid = 0, postId = 0;
            int index = -1;
            if (t != null) {
                uid = t.optLong("uid", 0);
                postId = t.optLong("post_id", t.optLong("postId", 0));
                index = t.optInt("index", t.optInt("post_index", -1));
            }
            if (uid <= 0) uid = d.optLong("uid", d.optLong("user_id", 0));
            if (postId <= 0) postId = d.optLong("post_id", 0);
            if (index < 0) index = d.optInt("post_index", d.optInt("index", -1));

            List<String> acts = new ArrayList<>();
            switch (act) {
                case "post_like": acts.add("like"); break;
                case "post_unlike": acts.add("unlike"); break;
                case "post_favorite": acts.add("favorite"); break;
                case "post_unfavorite": acts.add("unfavorite"); break;
                case "post_comment": acts.add("comment"); break;
                case "post_comment_follow": acts.add("comment"); acts.add("follow"); break;
                case "reply_comment": acts.add("reply"); break;
                case "follow_user": acts.add("follow"); break;
                case "unfollow_user": acts.add("unfollow"); break;
                case "tip_comment": acts.add("tip"); break;
                case "tip_post": acts.add("tip_user_post"); break;
                case "delete_my_post": acts.add("delete_my_post"); break;
                case "delete_comment": acts.add("delete_comment"); break;
                case "user_info": acts.add("user_info"); break;
                case "user_posts": acts.add("user_posts"); break;
                case "list_self": acts.add("list_self"); break;
                case "signin": acts.add("signin"); break;
                case "goto": acts.add("goto"); break;
                case "create_post": acts.add("post"); break;
                case "none": acts.add("none"); break;
                default: acts.add(act); break;                  // 未知就原样交给后面
            }

            JSONObject out = new JSONObject();
            out.put("actions", new JSONArray(acts));
            out.put("think", d.optString("think", d.optString("reason", "")));
            out.put("content", d.optString("content", d.optString("text", "")));
            out.put("reply", d.optString("reply", ""));
            out.put("title", d.optString("title", ""));
            out.put("score", d.optInt("score", 1));
            out.put("cat_id", d.optInt("cat_id", 0));
            if (uid > 0) out.put("user_id", (int) uid);
            if (postId > 0) out.put("post_id", postId);
            if (index >= 0) out.put("post_index", index);
            // target 语义
            if ("my_post".equals(kind) && index < 0) {
                String a0 = acts.get(0);
                if ("comment".equals(a0)) { out.put("actions", new JSONArray().put("comment_my_post")); }
                else if ("delete_my_post".equals(a0)) { /* 保持 */ }
                else if ("tip_user_post".equals(a0)) { out.put("actions", new JSONArray().put("tip_my_post")); }
            } else if ("user".equals(kind) && uid > 0
                    && ("like".equals(acts.get(0)) || "favorite".equals(acts.get(0)) || "comment".equals(acts.get(0)))) {
                JSONArray a2 = new JSONArray();
                String a0 = acts.get(0);
                a2.put("like".equals(a0) ? "like_user_post"
                        : "favorite".equals(a0) ? "favorite_user_post" : "comment_user_post");
                out.put("actions", a2);
            }
            return out;
        } catch (Exception e) {
            return d;
        }
    }

    // ================= 执行动作 =================

    private void act(HlxClient hlx, String key, JSONObject d, int catId, String boardName,
                     long pid, int authorId, String author, String title, String state) {
        act(hlx, key, d, catId, boardName, pid, authorId, author, title, state, 0);
    }

    /** replyCid>0 时，tip 视为「打赏这条评论」 */
    private void act(HlxClient hlx, String key, JSONObject d, int catId, String boardName,
                     long pid, int authorId, String author, String title, String state, long replyCid) {
        d = normalize(d);
        if (d == null) return;
        // 如果它想先查资料/看别人帖子，先查，再把结果丢回去让它继续决定
        if (hasInfoAction(d)) {
            String info = gatherInfo(hlx, d, authorId);
            if (!info.isEmpty()) {
                log("🔎 查了一下资料：" + info.replace("\n", " "));
                try {
                    JSONObject d2 = FishBrain.decide(this, key, persona(), sp,
                            state + "\n【你刚查到的东西】\n" + info
                                    + "\n现在你可以接着做事了（用 like_user_post / favorite_user_post / comment_user_post，"
                                    + "或者 like / favorite / comment 针对当前帖子）。");
                    if (d2 != null) d = d2;
                } catch (Exception ignored) {}
            }
        }

        JSONArray arr = d.optJSONArray("actions");
        List<String> acts = new ArrayList<>();
        if (arr != null) for (int i = 0; i < arr.length(); i++) acts.add(arr.optString(i, ""));
        else {
            String a = d.optString("action", "");
            if (!a.isEmpty()) acts.add(a);
        }
        String content = d.optString("content", "");
        String think = d.optString("think", d.optString("reason", ""));

        List<String> done = new ArrayList<>();
        String commentText = "";

        tipBlockMsg = null;
        // ===== 权限门：葫芦和破坏性操作只认主人 =====
        long ownerUid0 = sp.getLong("owner_uid", FishBrain.BOSS_UID);
        boolean fromOwner = authorId > 0 && authorId == ownerUid0;
        java.util.Set<String> ownerOnly = new java.util.HashSet<>(java.util.Arrays.asList(
                "delete_post", "delete_comment", "delete_my_post", "unfavorite", "unlike",
                "unfollow", "unfollow_index"));
        int favor0 = authorId > 0 ? FishMemory.favor(sp, authorId) : 0;
        int tipFavorMin = sp.getInt("tip_favor_min", 90);

        for (int ai = 0; ai < acts.size(); ai++) {
            String a = acts.get(ai);
            int doneBefore = done.size();
            a = a == null ? "" : a.trim().toLowerCase();
            if (a.isEmpty() || "none".equals(a) || "goto".equals(a) || "read".equals(a)) continue;
            boolean isTipAct = a.startsWith("tip");
            if (!fromOwner && ownerOnly.contains(a)) {
                log("⛔ 「" + a + "」只有主人能做，" + (author.isEmpty() ? ("uid=" + authorId) : author) + " 要求已忽略");
                continue;
            }
            if (!fromOwner && isTipAct && favor0 < tipFavorMin) {
                log("⛔ 不给葫芦：" + (author.isEmpty() ? ("uid=" + authorId) : author)
                        + " 好感只有 " + favor0 + "，没到 " + tipFavorMin);
                tipBlockMsg = "葫芦是我的大米饭诶，实在舍不得送[委屈]";
                continue;
            }
            switch (a) {
                case "like":
                    if (hlx.isPraised(pid)) { done.add("已点赞"); }
                    else if (hlx.praise(pid)) { tLike++; bump("today_like"); done.add("点赞"); }
                    break;
                case "favorite":
                    if (hlx.isFavorited(pid)) { done.add("已收藏"); }
                    else if (hlx.favorite(pid)) { tFav++; bump("today_favorite"); done.add("收藏"); }
                    break;
                case "unfavorite":
                    if (hlx.unfavorite(pid)) done.add("取消收藏");
                    break;
                case "unlike":
                    if (hlx.unlike(pid)) done.add("取消点赞");
                    break;
                case "comment": {
                    if (hasCommented(hlx, pid, key)) break;   // 双保险，绝不重复评论
                    String text = content;
                    if (text == null || text.isEmpty()) {
                        try { text = FishBrain.comment(this, key, persona(), sp, authorId, "帖子：" + title + "\n" + content); }
                        catch (Exception ignored) { text = ""; }
                    }
                    waitCommentGap();
                    if (!text.isEmpty() && tooSimilar(text)) {
                        log("🔁 这句和最近说过的太像，换个说法再说（本次跳过）");
                        break;
                    }
                    if (!text.isEmpty() && hlx.comment(pid, 0, text)) {
                        rememberComment(text);
                        tComment++; bump("today_comment");
                        done.add("评论"); commentText = text;
                        markCommented(pid);
                        mem("评论了《" + title + "》： " + text);
                    }
                    break;
                }
                case "reply": {
                    long cid = d.optLong("comment_id", 0);
                    String text = content.isEmpty() ? "嗯嗯" : content;
                    if (hlx.comment(pid, cid, text)) {
                        tComment++; bump("today_comment"); done.add("回复"); commentText = text;
                    }
                    break;
                }
                case "follow": {
                    int fu1 = d.optInt("user_id", authorId);
                    if (fu1 <= 0) break;
                    // ① 先查本地关注列表缓存，已经在里面就直接跳过（不发请求、不记动态）
                    java.util.Set<Long> fset = followingSet(hlx);
                    if (fset.contains((long) fu1)) {
                        done.add("已关注过");
                        break;
                    }
                    // ② 本地没有，再核对一次云端（防止缓存过期），都没有才真的关注
                    if (hlx.isFollowing(fu1)) {
                        fset.add((long) fu1);
                        done.add("已关注过");
                        break;
                    }
                    if (hlx.followSafe(fu1)) {
                        fset.add((long) fu1);
                        StringBuilder sb = new StringBuilder();
                        for (Long x : fset) { if (sb.length() > 0) sb.append(','); sb.append(x); }
                        sp.edit().putString("following_cache", sb.toString())
                                .putLong("following_at", System.currentTimeMillis())
                                .putLong("follow_ok_" + fu1, System.currentTimeMillis()).apply();
                        tFollow++; bump("today_follow"); done.add("关注");
                        logPost("关注了 @" + author, "关注", "", author, think);
                    }
                    break;
                }
                case "unfollow": {
                    int fu2 = d.optInt("user_id", authorId);
                    if (fu2 > 0 && hlx.unfollow(fu2)) {
                        done.add("取关");
                        FishMemory.addFavor(sp, fu2, -3);
                        java.util.Set<Long> fs2 = followingSet(hlx);
                        fs2.remove((long) fu2);
                        StringBuilder sb2 = new StringBuilder();
                        for (Long x : fs2) { if (sb2.length() > 0) sb2.append(','); sb2.append(x); }
                        sp.edit().putString("following_cache", sb2.toString())
                                .putLong("following_at", System.currentTimeMillis())
                                .remove("follow_ok_" + fu2).apply();
                    }
                    break;
                }
                case "tip_comment": case "tip": {
                    if (!sp.getBoolean("perm_tip", false)) {
                        log("⛔ 打赏被权限拦住（设置→权限设置→允许打赏葫芦）");
                        tipBlockMsg = "我送葫芦的开关还没开呀，等我主人打开";
                        break;
                    }
                    String day0 = new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date());
                    if (day0.equals(sp.getString("no_credits_day", ""))) {
                        log("⛔ 今天已确认没葫芦，跳过打赏");
                        tipBlockMsg = "我兜里没葫芦了，等攒够再给你";
                        break;
                    }
                    String lockedList = sp.getString("tip_locked", "");
                    if (lockedList.contains("," + pid + ",")) {
                        log("⛔ 这个帖子之前就锁贴了，跳过打赏");
                        tipBlockMsg = "这个帖子被锁了，送不进去呀";
                        break;
                    }
                    if (day0.equals(sp.getString("tip_block_day", ""))) {
                        log("⛔ 今天的葫芦额度已用完，跳过");
                        tipBlockMsg = "今天的葫芦都送光啦，明天给你补上好不好";
                        break;
                    }
                    if (replyCid > 0) {
                        // 找到「哪条评论在要葫芦」= 发通知者发的、文本里提到葫芦/打赏的那条
                        long fixedCid = replyCid;
                        try {
                            List<JSONObject> cs = hlx.comments(pid, 1);
                            String want = tipSrcText == null ? "" : tipSrcText.trim();
                            long exactCid = 0, fuzzyCid = 0, fuzzyT = -1;
                            for (JSONObject c : cs) {
                                JSONObject u = c.optJSONObject("user");
                                long cuid = u == null ? 0 : u.optLong("userID", 0);
                                if (cuid != authorId) continue;              // 只认发通知的人
                                String ct2 = c.optString("text", "");
                                long cid2 = c.optLong("commentID", 0);
                                // ① 原文完全一致：这条才是正在要葫芦的评论
                                if (!want.isEmpty() && ct2.trim().equals(want) && cid2 > 0) {
                                    exactCid = cid2;
                                    break;
                                }
                                // ② 兜底：同一人提到葫芦的最新一条
                                if ((ct2.contains("葫芦") || ct2.contains("打赏")) && cid2 > 0) {
                                    long ctime = c.optLong("createTime", 0);
                                    if (ctime >= fuzzyT) { fuzzyT = ctime; fuzzyCid = cid2; }
                                }
                            }
                            // 只认原文精确匹配；匹配不到就用通知自带 commentID，
                            // 绝不再退到“同一人提到葫芦的最新一条”——那会把葫芦送给别的评论
                            if (exactCid > 0) fixedCid = exactCid;
                            else {
                                fixedCid = replyCid;
                                log("⚠️ 没找到与通知原文一致的评论，改用通知里的 cid=" + replyCid);
                            }
                        } catch (Exception ignored) {}
                        if (pid <= 0) {
                            log("✗ 打赏中止：拿不到帖子ID（post_id=0，cid=" + replyCid + "）");
                            done.add("打赏失败");
                            break;
                        }
                        int sc0 = d.optInt("score", 1);
                        if (sc0 < 1) sc0 = 1;
                        if (sc0 > 5) sc0 = 5;
                        if (!fromOwner) sc0 = 1;      // 不是主人，只给 1 个
                        String rs0 = d.optString("content", "");
                        if (rs0.trim().length() < 5) rs0 = "谢谢你一直陪我说话";
                        if (hlx.tipComment(pid, fixedCid, sc0, rs0)) {
                            tTip++; bump("today_tip"); done.add("送葫芦");
                            log("🎁 给这条评论送了 " + sc0 + " 个葫芦");
                            logPost("给 " + (author.isEmpty() ? "这条评论" : author) + " 送了 " + sc0 + " 个葫芦",
                                    "打赏", rs0, author, think);
                            playPet("eat", 130, false, "idle");
                        } else {
                            String err = hlx.lastError();
                            log("✗ 评论打赏失败（post_id=" + pid + " cid=" + fixedCid + "）：" + err);
                            if (err.contains("送太多") || err.contains("明日再战")) {
                                sp.edit().putString("tip_block_day", day0).apply();
                                tipBlockMsg = "今天的葫芦送光啦，明天给你补上";
                            } else if (err.contains("葫芦数不足") || err.contains("余额")) {
                                sp.edit().putString("no_credits_day", day0).apply();
                                tipBlockMsg = "我兜里没葫芦了，等攒够再给你";
                            } else if (err.contains("锁定") || err.contains("删除")) {
                                String lk = sp.getString("tip_locked", ",");
                                if (!lk.contains("," + pid + ",")) {
                                    sp.edit().putString("tip_locked", lk + pid + ",").apply();
                                }
                                tipBlockMsg = "这个帖子被锁了，送不进去呀";
                            } else {
                                tipBlockMsg = "诶…葫芦好像没送出去";
                            }
                        }
                        break;
                    }
                    if (tTip >= Math.max(0, sp.getInt("tip_daily_limit", 3))) break;
                    String txt = content.isEmpty() ? "谢谢你分享" : content;
                    if (hlx.tip(pid, 1, txt)) { tTip++; bump("today_tip"); done.add("打赏葫芦"); }
                    break;
                }
                case "signin":
                    if (sp.getBoolean("perm_signin", true) && hlx.signin(catId)) done.add("签到");
                    break;
                case "list_self":
                    break;
                case "delete_my_post": {
                    int di = d.optInt("post_index", 0);
                    try {
                        java.util.List<JSONObject> mine1 = hlx.userPosts(hlx.uid(), 10);
                        if (di >= 0 && di < mine1.size()) {
                            long mp = mine1.get(di).optLong("postID", 0);
                            if (mp > 0 && hlx.deletePost(mp)) done.add("删我的第" + (di + 1) + "个帖");
                        }
                    } catch (Exception ignored) {}
                    break;
                }
                case "unfollow_index": {
                    int fi = d.optInt("follow_index", 0);
                    try {
                        java.util.List<JSONObject> fs1 = hlx.followingList(hlx.uid(), 20);
                        if (fi >= 0 && fi < fs1.size()) {
                            JSONObject uu = fs1.get(fi).optJSONObject("user");
                            int cuid = uu == null ? 0 : uu.optInt("userID", 0);
                            String cnm = uu == null ? "" : uu.optString("nick", "");
                            if (cuid > 0 && hlx.unfollow(cuid)) {
                                done.add("取关第" + (fi + 1) + "个");
                                mem("取关了 @" + cnm);
                            }
                        }
                    } catch (Exception ignored) {}
                    break;
                }
                case "comment_my_post": {
                    int ci = d.optInt("post_index", 0);
                    try {
                        java.util.List<JSONObject> mine2 = hlx.userPosts(hlx.uid(), 10);
                        if (ci >= 0 && ci < mine2.size()) {
                            long mp = mine2.get(ci).optLong("postID", 0);
                            if (mp > 0 && content != null && !content.isEmpty() && hlx.comment(mp, 0, content)) {
                                tComment++; bump("today_comment"); done.add("评论我的帖");
                            }
                        }
                    } catch (Exception ignored) {}
                    break;
                }
                case "tip_my_post": {
                    if (!sp.getBoolean("perm_tip", false)) break;
                    int pi2 = d.optInt("post_index", 0);
                    int sc4 = d.optInt("score", 1);
                    if (sc4 < 1) sc4 = 1;
                    if (sc4 > 5) sc4 = 5;
                    if (!fromOwner) sc4 = 1;
                    String rs2 = content == null || content.trim().length() < 5 ? "谢谢你一直陪着我" : content;
                    try {
                        java.util.List<JSONObject> mine3 = hlx.userPosts(hlx.uid(), 10);
                        if (pi2 >= 0 && pi2 < mine3.size()) {
                            long mp = mine3.get(pi2).optLong("postID", 0);
                            if (mp > 0 && hlx.tip(mp, sc4, rs2)) { tTip++; bump("today_tip"); done.add("给我的帖送葫芦"); }
                        }
                    } catch (Exception ignored) {}
                    break;
                }
                case "tip_user_post": {
                    if (sp.getBoolean("perm_tip", false)) {
                        int tu2 = d.optInt("user_id", authorId);
                        int ti3 = d.optInt("post_index", 0);
                        int sc = d.optInt("score", 1);
                        if (sc < 1) sc = 1;
                        if (sc > 5) sc = 5;
                        if (!fromOwner) sc = 1;
                        if (actOnUserPostTip(hlx, tu2, ti3, sc, content)) done.add("送葫芦");
                    }
                    break;
                }
                case "delete_post": {
                    long dpid = d.optLong("post_id", pid);
                    if (dpid > 0 && hlx.deletePost(dpid)) done.add("删帖");
                    break;
                }
                case "delete_comment": {
                    long dc = d.optLong("comment_id", 0);
                    if (dc > 0 && hlx.deleteComment(dc)) done.add("删评论");
                    break;
                }
                case "like_user_post":
                case "favorite_user_post":
                case "comment_user_post": {
                    int uid = d.optInt("user_id", authorId);
                    int idx = d.optInt("post_index", 0);
                    if (actOnUserPost(hlx, a, uid, idx, content)) done.add(
                            "like_user_post".equals(a) ? "赞他的帖"
                                    : "favorite_user_post".equals(a) ? "收藏他的帖" : "评论他的帖");
                    break;
                }
                case "post":
                    if (sp.getBoolean("perm_post", false)) doPost(hlx, key, catId);
                    break;
            }
            // 这一条动作真的执行了、而且后面还有动作 → 随机等 0~15 秒
            if (done.size() > doneBefore && ai < acts.size() - 1) randGap();
        }

        lastActDone = done.size();
        if (!done.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String x : done) { if (sb.length() > 0) sb.append("·"); sb.append(x); }
            if (title != null && !title.trim().isEmpty()) {
                String tag0 = sb.toString();
                if (boardName != null && !boardName.isEmpty()) tag0 = "「" + boardName + "」" + tag0;
                logPost(title, tag0, commentText, author, think);
            } else {
                log("🎁 " + sb);
            }
            moodNudge(1, "和大家互动了");
            FishMemory.addFavor(sp, authorId, 1);
            // 只在有真实帖子上下文、且不是“已关注过”这种空动作时写入记忆
            String doneStr = sb.toString().trim();
            if (boardName != null && !boardName.isEmpty()
                    && title != null && !title.isEmpty()
                    && !"已关注过".equals(doneStr)
                    && !"已关注过".equals(doneStr.replace("·", ""))) {
                mem("在「" + boardName + "」看了《" + title + "》并" + doneStr);
            }
        }
    }

    // ================= 发帖（默认带图） =================

    /** 模拟测试：本地跑一遍发帖（含文生图），不上传 */
    private void doTestDry(HlxClient hlx, String key) {
        try {
            LinkedHashMap<Integer, String> boards = hlx.boards();
            int catId = sp.getInt("diary_cat", 2);
            if (boards.isEmpty() || !boards.containsKey(catId)) {
                catId = boards.isEmpty() ? 2 : boards.keySet().iterator().next();
            }
            String boardName = boards.isEmpty() ? "泳池" : boards.get(catId);
            JSONObject o = FishBrain.composePost(this, key, persona(), sp, boardName,
                    "发一条测试帖，说说你今天的心情");
            if (o == null) { log("🧪 模拟测试：它没想好内容"); return; }
            String title = o.optString("title", "");
            String content = o.optString("content", "");
            String genimg = o.optString("genimg", "");
            StringBuilder dbg = new StringBuilder();
            dbg.append("板块：").append(boardName)
               .append("\n标题：").append(title)
               .append("\n正文：").append(content)
               .append("\n配图描述：").append(genimg);
            boolean imgOn = (sp.getBoolean("img_on", false) || !sp.getString("img_url", "").isEmpty());
            String prompt = "";
            if (imgOn) {
                prompt = FishBrain.imgPrompt(key, sp.getString("look", FishBrain.LOOK_DEFAULT), genimg);
                dbg.append("\n图片提示词：").append(prompt);
                byte[] png = FishImage.generate(sp, prompt);
                if (png != null) {
                    File f = new File(getFilesDir(), "test_dry.png");
                    FileOutputStream fo = new FileOutputStream(f);
                    fo.write(png);
                    fo.close();
                    dbg.append("\n生成图片：").append(f.length() / 1024).append(" KB");
                } else {
                    dbg.append("\n生成图片：失败");
                }
            } else {
                dbg.append("\n配图：没配置，纯文本");
            }
            FishBrain.dbg("TESTDRY", dbg.toString());
            log("🧪 模拟测试完成（没上传）：《" + title + "》" + (prompt.isEmpty() ? "（纯文本）" : "（带图）"));
        } catch (Exception e) {
            log("🧪 模拟测试出错：" + e.getMessage());
        }
    }

    private void doPost(HlxClient hlx, String key, int catId) {
        try {
            if (sp.getInt("today_post", 0) >= Math.max(0, sp.getInt("post_daily_limit", 2))) {
                log("📝 今天发帖到上限了");
                return;
            }
            LinkedHashMap<Integer, String> boards = scoped(hlx.boards());
            if (boards.isEmpty()) return;
            if (catId <= 0 || !boards.containsKey(catId)) {
                List<Integer> ids = new ArrayList<>(boards.keySet());
                catId = ids.get(random.nextInt(ids.size()));
            }
            String boardName = boards.get(catId);

            String hint = sp.getString("post_hint", "");
            sp.edit().remove("post_hint").apply();
            JSONObject o = FishBrain.composePost(this, key, persona(), sp, boardName, hint);
            if (o == null) { log("📝 没想好发什么"); return; }
            String title = o.optString("title", "");
            String content = o.optString("content", "");
            if (title.isEmpty() || content.isEmpty()) return;

            String fids = "";
            boolean imgOn = (sp.getBoolean("img_on", false)
                    || !sp.getString("img_url", "").isEmpty());
            // 新人报道帖：优先复用测试时生成的那张图
            if (imgOn && hint.contains("新人报道")) {
                try {
                    File tf = new File(getFilesDir(), "test_img.png");
                    if (!tf.exists()) {
                        // 没有测试图就现生成一张（用默认形象提示词）
                        byte[] nb = FishImage.generate(sp, FishBrain.WELCOME_IMG_PROMPT);
                        if (nb != null) {
                            FileOutputStream f2 = new FileOutputStream(tf);
                            f2.write(nb); f2.close();
                        }
                    }
                    if (tf.exists()) {
                        byte[] tb = new byte[(int) tf.length()];
                        java.io.FileInputStream fis = new java.io.FileInputStream(tf);
                        fis.read(tb); fis.close();
                        String fid0 = hlx.uploadImage(tb);
                        if (fid0 != null && !fid0.isEmpty()) fids = fid0 + ",";
                    }
                } catch (Exception ignored) {}
            }
            if (imgOn && fids.isEmpty()) {
                try {
                    String genimg = o.optString("genimg", "");
                    if (genimg.isEmpty()) genimg = "和「" + title + "」相关的生活场景，" + content;
                    String look = sp.getString("look", FishBrain.LOOK_DEFAULT);
                    String prompt = FishBrain.imgPrompt(key, look, genimg);
                    if (prompt.isEmpty()) prompt = genimg;
                    byte[] png = FishImage.generate(sp, prompt);
                    if (png != null) {
                        String fid = hlx.uploadImage(png);
                        if (fid != null && !fid.isEmpty()) {
                            fids = fid + ",";
                            try {
                                FileOutputStream fos = new FileOutputStream(new File(getFilesDir(), "last_post.png"));
                                fos.write(png); fos.close();
                            } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
            }

            JSONObject r = hlx.createPost(catId, title, content, fids);
            if (r != null && r.optInt("status", 0) == 1) {
                tPost++; bump("today_post");
                if (testPostMode) {
                    FishBrain.dbg("TESTPOST", "标题：" + title + "\n正文：" + content + "\n板块：" + boardName);
                    log("🧪 测试发帖成功：《" + title + "》（只记调试日志，不进动态）");
                } else {
                    logPost(title, fids.isEmpty() ? "发帖" : "发帖·配图", content, "自己", "在「" + boardName + "」发的");
                }
                mem("我自己在「" + boardName + "」发了帖子《" + title + "》：" + content);
                genLife();
            } else {
                log("📝 发帖失败：" + (r == null ? "无返回" : r.optString("msg", "")));
            }
        } catch (Exception e) {
            log("发帖异常：" + e.getMessage());
        }
    }

    // ================= 签到 =================

    private void doSignin(HlxClient hlx) {
        try {
            LinkedHashMap<Integer, String> boards = hlx.boards();
            int n = 0;
            for (Integer id : boards.keySet()) {
                if (hlx.signin(id)) n++;
                Thread.sleep(1200);
                if (n >= 5) break;
            }
            log("📅 签到了 " + n + " 个板块");
        } catch (Exception ignored) {}
    }

    // ================= 回复（10 秒，24 小时内，双保险） =================

    private void startReplyLoop() {
        new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(10_000);
                    if (!running) break;
                    String key = sp.getString("ai_key", "");
                    if (key.isEmpty() || !sp.getBoolean("perm_reply", true)) continue;
                    replyPassSafe(new HlxClient(sp), key);
                } catch (Exception ignored) {}
            }
        }).start();
    }

    /** 同一时间只允许一个回复轮次，避免两条线程同时回同一条消息 */
    private void replyPassSafe(HlxClient hlx, String key) {
        if (!replyBusy.compareAndSet(false, true)) {
            log("↩︎ 上一轮回复还在跑，跳过这一次");
            return;
        }
        try {
            replyPass(hlx, key);
        } finally {
            replyBusy.set(false);
        }
    }

    private void replyPass(HlxClient hlx, String key) {
        try {
            List<JSONObject> list = hlx.notifications(20);
            // 按通知时间倒序：最新的先回，避免卡在老帖上
            try {
                java.util.Collections.sort(list, new java.util.Comparator<JSONObject>() {
                    @Override public int compare(JSONObject a, JSONObject b) {
                        JSONObject ca = a.optJSONObject("content");
                        JSONObject cb = b.optJSONObject("content");
                        long ta = ca == null ? 0 : ca.optLong("createTime", 0);
                        long tb = cb == null ? 0 : cb.optLong("createTime", 0);
                        return Long.compare(tb, ta);
                    }
                });
            } catch (Exception ignored) {}
            long dayAgo = System.currentTimeMillis() - 24 * 3600_000L;

            // 首次启动（新装/清过数据）：把当前通知里的历史消息全部记为“已处理”，
            // 之后只回新来的消息。这样别人第一次用也不会把昨天以前的评论刷一遍。
            if (!sp.getBoolean("replied_warm", false)) {
                int skipped = 0;
                for (JSONObject d0 : list) {
                    JSONObject c0 = d0.optJSONObject("content");
                    if (c0 == null) continue;
                    long id0 = c0.optLong("commentID", 0);
                    if (id0 > 0) { markReplied(id0); skipped++; }
                }
                sp.edit().putBoolean("replied_warm", true).apply();
                log("🧊 首次启动：已把 " + skipped + " 条历史通知标记为处理过，之后只回新消息");
                return;
            }
            long cutoff = dayAgo;
            int maxPerPass = 5;
            int n = 0;
            for (JSONObject d : list) {
                JSONObject c = d.optJSONObject("content");
                if (c == null) continue;
                long cid = c.optLong("commentID", 0);
                long pid = c.optLong("postID", 0);
                if (pid == 0) {
                    JSONObject po = c.optJSONObject("post");
                    if (po != null) pid = po.optLong("postID", 0);
                }
                String text = c.optString("text", "");
                long ct = c.optLong("createTime", 0);
                long from = c.optLong("userID", 0);
                String fromNick = "";
                JSONObject u = c.optJSONObject("user");
                if (u != null) {
                    if (from == 0) from = u.optLong("userID", 0);
                    fromNick = u.optString("nick", "");
                }
                if (cid == 0 || pid == 0 || from == hlx.uid()) continue;
                if (ct > 0 && ct < cutoff) continue;                 // 冷启动 90 分钟 / 平时 1 天
                if (hasReplied(hlx, pid, cid, ct)) continue;         // 双保险
                if (System.currentTimeMillis() < sp.getLong("rt_t_" + cid, 0)) continue;  // 退避中
                if (botBlocked(from)) {
                    markReplied(cid);
                    log("🤖 疑似机器人 uid=" + from + "，这轮不回");
                    continue;
                }

                if (!fromNick.isEmpty()) cacheName(from, fromNick);
                long owner = sp.getLong("owner_uid", 0);
                String who = (from == owner) ? "主人" : (fromNick.isEmpty() ? ("uid=" + from) : fromNick);
                JSONObject rd = null;
                String ask = who + " 评论了你：「" + text + "」。\n"
                        + "如果你想关注他/收藏他的帖子，也可以顺手做。记得：他是 " + who
                        + "，uid=" + from + "。";
                for (int i = 0; i < 2 && rd == null; i++) {
                    try {
                        rd = FishBrain.replyJson(this, key, persona(), sp, from, ask);
                    } catch (Exception ignored) {}
                }
                // 如果它要先查某人帖子列表，查完把结果回灌再问一次
                if (rd != null && hasInfoAction(rd)) {
                    String info = gatherInfo(hlx, rd, (int) from);
                    if (!info.isEmpty()) {
                        try {
                            JSONObject rd2 = FishBrain.replyJson(this, key, persona(), sp, from,
                                    ask + "\n【你刚查到的东西】\n" + info
                                            + "\n现在接着把要做的事做完（用 like_user_post / favorite_user_post / "
                                            + "comment_user_post / tip_user_post，带 user_id 和 post_index）。");
                            if (rd2 != null) rd = rd2;
                        } catch (Exception ignored) {}
                        // 第二问若还是只查资料，就直接按原意图执行
                        if (rd != null && hasInfoAction(rd)) {
                            JSONArray a2 = rd.optJSONArray("actions");
                            String intent2 = "";
                            if (a2 != null) for (int q = 0; q < a2.length(); q++) {
                                String x2 = a2.optString(q, "").toLowerCase();
                                if (!"list_self".equals(x2) && !"user_posts".equals(x2)
                                        && !"user_info".equals(x2)) { intent2 = x2; break; }
                            }
                            if (intent2.isEmpty()) {
                                // 没有其他意图，看原意图猜：评论/取关/删/送
                                log("⚠️ 它查到资料后没给下一步，跳过");
                            }
                        }
                    }
                }
                String reply = rd == null ? "" : rd.optString("reply", "").trim();
                if (reply.isEmpty()) continue;
                if (reply.length() > 120) reply = reply.substring(0, 120);
                // 先认领：宁可漏回复，也绝不重复回复
                if (loadReplied().contains(cid)) continue;
                markReplied(cid);
                waitCommentGap();
                boolean sent = hlx.comment(pid, cid, reply);
                if (!sent) {
                    String err0 = hlx.lastError();
                    log("   ↳ 失败详情：" + err0);
                    if (err0.contains("锁定") || err0.contains("已删除") || err0.contains("无权")) {
                        markReplied(cid);
                        sp.edit().remove("rt_" + cid).remove("rt_t_" + cid).apply();
                        log("🚫 这个话题不能回复了（" + err0 + "），不再重试");
                        continue;
                    }
                    if (err0.contains("账号已被锁定")) {
                        sp.edit().remove("rt_" + cid).remove("rt_t_" + cid).apply();
                        log("🚫 账号被禁言，暂停回复这一轮");
                        break;
                    }
                    unmarkReplied(cid);          // 可恢复的失败，放开认领等重试
                    int tries = sp.getInt("rt_" + cid, 0) + 1;
                    sp.edit().putInt("rt_" + cid, tries).apply();
                    long wait = tries == 1 ? 30_000L : tries == 2 ? 90_000L : 300_000L;
                    sp.edit().putLong("rt_t_" + cid, System.currentTimeMillis() + wait).apply();
                    if (tries >= 3) {
                        markReplied(cid);
                        log("⚠️ 回复失败 3 次（cid=" + cid + "），放弃");
                    } else {
                        log("⚠️ 回复没发出去（cid=" + cid + "），" + (wait / 1000) + " 秒后再试（第 " + tries + " 次）");
                    }
                }
                if (sent) {
                    replyTick(from);
                    sp.edit().remove("rt_" + cid).apply();
                    markReplied(cid);
                    tComment++; bump("today_comment");
                    String acts = "";
                    boolean wantTip = false;
                    try {
                        JSONArray aw = rd == null ? null : rd.optJSONArray("actions");
                        if (aw != null) for (int q = 0; q < aw.length(); q++) {
                            String x = aw.optString(q, "").toLowerCase();
                            if ("tip".equals(x) || "tip_comment".equals(x)
                                    || "tip_user_post".equals(x) || "tip_my_post".equals(x)) wantTip = true;
                            if ("list_self".equals(x) && rd != null
                                    && rd.optString("what", "").trim().isEmpty()) {
                                String g = guessWhat(text);
                                if (!g.isEmpty()) rd.put("what", g);
                            }
                        }
                    } catch (Exception ignored) {}
                    tipSrcText = text == null ? "" : text.trim();
                    int tipBefore = tTip;
                    try {
                        act(hlx, key, rd, 0, "", pid, (int) from, who, "", "", cid);
                    } catch (Exception ignored) {}
                    tipSrcText = "";
                    boolean acted = lastActDone > 0;
                    boolean wanted = false;
                    try {
                        JSONArray aw0 = rd == null ? null : rd.optJSONArray("actions");
                        if (aw0 != null) for (int q = 0; q < aw0.length(); q++) {
                            String x0 = aw0.optString(q, "").toLowerCase();
                            if (!x0.isEmpty() && !"none".equals(x0) && !"list_self".equals(x0)
                                    && !"user_posts".equals(x0) && !"user_info".equals(x0)) wanted = true;
                        }
                    } catch (Exception ignored) {}
                    if (wanted && !acted && tipBlockMsg == null) {
                        reply = "诶…我这边刚没操作成功，等下我再试试[委屈]";
                    }
                    // 送葫芦失败就别嘴硬
                    if (wantTip && tTip == tipBefore) {
                        String why = tipBlockMsg;
                        reply = why == null ? "诶…葫芦好像没送出去，等下我再试试[委屈]" : why;
                    }
                    memUser(from, who, "他评论我「" + text + "」，我回了：" + reply
                            + (acts.isEmpty() ? "" : "，并" + acts.trim()));
                    FishMemory.addFavor(sp, from, 2);
                    moodNudge(2, "有人和我说话了");
                    log("💌 回复 " + who + "：" + reply + (acts.isEmpty() ? "" : "（" + acts.trim() + "）"));
                    n++;
                    if (n >= maxPerPass) break;
                    randGap();
                }
            }
        } catch (Exception ignored) {}
    }

    // ================= 记忆 =================

    /** 写通用记忆，超长自动让 AI 摘要 */
    private void mem(String line) {
        FishMemory.remember(this, line);
        FishMemory.compactIfNeeded(this, "memory.txt", sp.getString("ai_key", ""),
                sp.getInt("memory_max_chars", 1500), "你和社区里的人打交道的记忆");
    }

    /** 写用户记忆，超长自动让 AI 摘要 */
    private void memUser(long uid, String nick, String line) {
        if (uid <= 0 || uid == sp.getLong("hlx_uid", 0)) return;   // 自己的发言不进用户记忆
        FishMemory.rememberUser(this, uid, nick, line);
        FishMemory.compactIfNeeded(this, "mem_u" + uid + ".txt", sp.getString("ai_key", ""),
                sp.getInt("user_mem_max_chars", 800), "你和一个用户之间的往来记忆");
    }



    /** 处理「按序号操作」类动作；返回如 "评论我的帖 " 给日志用，null 表示没处理 */
    private String runIndexAction(HlxClient hlx, String a, JSONObject d) {
        try {
            if ("delete_my_post".equals(a)) {
                int di = d.optInt("post_index", 0);
                List<JSONObject> mine = hlx.userPosts(hlx.uid(), 20);
                if (di < 0 || di >= mine.size()) {
                    log("⚠️ 删帖失败：你只有 " + mine.size() + " 个帖子，没有第 " + (di + 1) + " 个");
                    return null;
                }
                long mp = mine.get(di).optLong("postID", 0);
                if (mp > 0 && hlx.deletePost(mp)) return "删我的第" + (di + 1) + "个帖";
                log("⚠️ 删帖接口失败：" + hlx.lastError());
                return null;
            }
            if ("unfollow_index".equals(a)) {
                int fi = d.optInt("follow_index", d.optInt("post_index", 0));
                List<JSONObject> fs = hlx.followingList(hlx.uid(), 20);
                if (fi < 0 || fi >= fs.size()) return null;
                JSONObject uu = fs.get(fi).optJSONObject("user");
                int cuid = uu == null ? 0 : uu.optInt("userID", 0);
                String cnm = uu == null ? "" : uu.optString("nick", "");
                if (cuid > 0 && hlx.unfollow(cuid)) {
                    mem("取关了 @" + cnm);
                    return "取关第" + (fi + 1) + "个";
                }
                return null;
            }
            if ("comment_my_post".equals(a)) {
                int ci = d.optInt("post_index", 0);
                List<JSONObject> mine = hlx.userPosts(hlx.uid(), 20);
                if (ci < 0 || ci >= mine.size()) return null;
                long mp = mine.get(ci).optLong("postID", 0);
                String cc = d.optString("content", "");
                if (cc.trim().isEmpty()) {
                    try {
                        cc = FishBrain.comment(this, sp.getString("ai_key", ""), persona(), sp, hlx.uid(),
                                "帖子：〈" + mine.get(ci).optString("title", "") + "〉");
                    } catch (Exception ignored) { cc = "来啦"; }
                }
                waitCommentGap();
                if (mp <= 0) { log("⚠️ 评论我的帖失败：拿不到 post_id"); return null; }
                waitCommentGap();
                boolean okc = hlx.comment(mp, 0, cc);
                if (okc) { markCommented(mp); return "评论我的帖"; }
                log("⚠️ 评论我的帖失败：" + hlx.lastError());
                return null;
            }
            if ("tip_my_post".equals(a)) {
                if (!sp.getBoolean("perm_tip", false)) return null;
                int pi = d.optInt("post_index", 0);
                int sc = d.optInt("score", 1);
                if (sc < 1) sc = 1;
                if (sc > 5) sc = 5;
                String rs = d.optString("content", "");
                if (rs.trim().length() < 5) rs = "谢谢你一直陪着我";
                List<JSONObject> mine = hlx.userPosts(hlx.uid(), 20);
                if (pi < 0 || pi >= mine.size()) return null;
                long mp = mine.get(pi).optLong("postID", 0);
                if (mp > 0 && hlx.tip(mp, sc, rs)) return "给我的帖送葫芦";
                return null;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** 是否包含需要先查资料的动作 */
    private boolean hasInfoAction(JSONObject d) {
        JSONArray arr = d.optJSONArray("actions");
        if (arr == null) return false;
        for (int i = 0; i < arr.length(); i++) {
            String a = arr.optString(i, "").toLowerCase();
            if ("user_info".equals(a) || "user_posts".equals(a) || "list_self".equals(a)) return true;
        }
        return false;
    }

    /** 查资料：主页信息 / 帖子列表 */
    /** 从对方的话里猜他要查哪一类列表 */
    private String guessWhat(String text) {
        if (text == null) return "";
        if (text.contains("粉丝")) return "followers";
        if (text.contains("关注")) return "following";
        if (text.contains("收藏")) return "favorites";
        if (text.contains("帖子") || text.contains("发过")) return "posts";
        return "";
    }

    private String gatherInfo(HlxClient hlx, JSONObject d, int authorId) {
        int uid = d.optInt("user_id", authorId);
        if (uid <= 0) return "";
        StringBuilder sb = new StringBuilder();
        JSONArray arr = d.optJSONArray("actions");
        boolean wantInfo = false, wantPosts = false;
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            String a = arr.optString(i, "").toLowerCase();
            if ("user_info".equals(a)) wantInfo = true;
            if ("user_posts".equals(a)) wantPosts = true;
        }
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            if (!"list_self".equals(arr.optString(i, "").toLowerCase())) continue;
            String what = d.optString("what", "").trim().toLowerCase();
            if (what.startsWith("fan") || what.contains("follower")) what = "followers";
            else if (what.contains("follow")) what = "following";
            else if (what.contains("fav")) what = "favorites";
            else if (what.contains("post")) what = "posts";
            else if (what.isEmpty()) {
                what = "all";
                log("🔎 它没说查哪一类，四类清单全给");
            }
            try {
                if ("posts".equals(what) || "all".equals(what)) {
                    java.util.List<JSONObject> mine0 = hlx.userPosts(hlx.uid(), 10);
                    sb.append("· 我的帖子（序号从 0 开始）：\n");
                    int k0 = 0;
                    for (JSONObject q : mine0)
                        sb.append("  [").append(k0++).append("] post_id=").append(q.optLong("postID", 0))
                          .append(" 标题：").append(q.optString("title", "")).append("\n");
                }
                if ("following".equals(what) || "all".equals(what)) {
                    java.util.List<JSONObject> fs0 = hlx.followingList(hlx.uid(), 20);
                    sb.append("· 我关注的人（序号从 0 开始）：\n");
                    int k1 = 0;
                    for (JSONObject f0 : fs0) {
                        JSONObject u0 = f0.optJSONObject("user");
                        if (u0 == null) continue;
                        sb.append("  [").append(k1++).append("] ").append(u0.optString("nick", ""))
                          .append(" uid=").append(u0.optLong("userID", 0)).append("\n");
                    }
                }
                if ("followers".equals(what) || "all".equals(what)) {
                    java.util.List<JSONObject> fw0 = hlx.followers(hlx.uid(), 20);
                    sb.append("· 我的粉丝（序号从 0 开始）：\n");
                    int k3 = 0;
                    for (JSONObject f1 : fw0) {
                        JSONObject u1 = f1.optJSONObject("user");
                        if (u1 == null) u1 = f1;
                        sb.append("  [").append(k3++).append("] ").append(u1.optString("nick", ""))
                          .append(" uid=").append(u1.optLong("userID", 0)).append("\n");
                    }
                }
                if ("favorites".equals(what) || "all".equals(what)) {
                    java.util.List<JSONObject> fv = hlx.favorites(hlx.uid(), 10);
                    sb.append("· 我收藏的帖子（序号从 0 开始）：\n");
                    int k2 = 0;
                    for (JSONObject q2 : fv)
                        sb.append("  [").append(k2++).append("] post_id=").append(q2.optLong("postID", 0))
                          .append(" 标题：").append(q2.optString("title", "")).append("\n");
                }
            } catch (Exception ignored) {}
        }
        if (wantInfo) {
            try {
                JSONObject u = hlx.userInfo(uid);
                if (u != null) {
                    sb.append("· 主页：").append(u.optString("nick", ""))
                      .append("（uid=").append(uid).append("）")
                      .append(" 等级 ").append(u.optInt("level", 0))
                      .append("，葫芦 ").append(u.optInt("credits", 0))
                      .append("，签名：").append(u.optString("signature", "无")).append("\n");
                    cacheName(uid, u.optString("nick", ""));
                }
            } catch (Exception ignored) {}
        }
        if (wantPosts) {
            try {
                List<JSONObject> ps = hlx.userPosts(uid, 5);
                sb.append("· 他最近的帖子（共 ").append(ps.size()).append(" 条）：\n");
                int idx = 0;
                for (JSONObject p : ps) {
                    sb.append("  [").append(idx++).append("] post_id=")
                      .append(p.optLong("postID", 0))
                      .append(" 标题：").append(p.optString("title", "")).append("\n");
                }
            } catch (Exception ignored) {}
        }
        return sb.toString();
    }

    /** 对着别人的某个帖子做事 */
    /** 给某人某个帖子送葫芦 */
    private boolean actOnUserPostTip(HlxClient hlx, int uid, int index, int score, String txt) {
        try {
            List<JSONObject> ps = hlx.userPosts(uid, 8);
            if (ps.isEmpty()) return false;
            if (index < 0 || index >= ps.size()) index = 0;
            long pid = ps.get(index).optLong("postID", 0);
            if (pid <= 0) return false;
            String t = ps.get(index).optString("title", "");
            String reason = (txt == null || txt.trim().isEmpty()) ? "谢谢你分享" : txt.trim();
            if (reason.length() < 5) reason = reason + "，谢谢你分享";
            if (hlx.tip(pid, score, reason)) {
                tTip++; bump("today_tip");
                log("🎁 给 @" + sp.getString("uname_" + uid, String.valueOf(uid))
                        + " 的《" + t + "》送了 " + score + " 个葫芦");
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean actOnUserPost(HlxClient hlx, String action, int uid, int index, String content) {
        try {
            List<JSONObject> ps = hlx.userPosts(uid, 8);
            if (ps.isEmpty()) return false;
            if (index < 0 || index >= ps.size()) index = 0;
            long pid = ps.get(index).optLong("postID", 0);
            String t = ps.get(index).optString("title", "");
            if (pid <= 0) return false;
            String nm = sp.getString("uname_" + uid, String.valueOf(uid));
            if ("like_user_post".equals(action)) {
                if (!hlx.isPraised(pid) && hlx.praise(pid)) {
                    tLike++; bump("today_like");
                    log("👍 给 @" + nm + " 的帖子《" + t + "》点赞");
                    return true;
                }
            } else if ("favorite_user_post".equals(action)) {
                if (!hlx.isFavorited(pid) && hlx.favorite(pid)) {
                    tFav++; bump("today_favorite");
                    log("⭐ 收藏了 @" + nm + " 的《" + t + "》");
                    return true;
                }
            } else if ("comment_user_post".equals(action)) {
                String cc = content;
                if (cc == null || cc.trim().isEmpty()) {
                    try {
                        cc = FishBrain.comment(this, sp.getString("ai_key", ""), persona(), sp, uid,
                                "帖子：〈" + t + "〉");
                    } catch (Exception ignored) { cc = ""; }
                }
                if (cc != null && !cc.isEmpty() && hlx.comment(pid, 0, cc)) {
                    tComment++; bump("today_comment");
                    markCommented(pid);
                    log("💬 评论了 @" + nm + " 的《" + t + "》：" + content);
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private String joinList(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String x : l) { if (sb.length() > 0) sb.append('+'); sb.append(x); }
        return sb.toString();
    }

    private void cacheName(long uid, String nick) {
        if (uid > 0 && nick != null && !nick.isEmpty())
            sp.edit().putString("uname_" + uid, nick).apply();
    }

    /** 生活记忆：让 AI 编今天的日常 */
    /** 生成一张图并上传，失败返回空串 */
    private String genAndUpload(HlxClient hlx, String key, String genimg) {
        try {
            String look = sp.getString("look", FishBrain.LOOK_DEFAULT);
            String prompt = FishBrain.imgPrompt(key, look, genimg);
            if (prompt == null || prompt.isEmpty()) prompt = genimg;
            byte[] png = FishImage.generate(sp, prompt);
            if (png == null) return "";
            String fid = hlx.uploadImage(png);
            return fid == null ? "" : fid;
        } catch (Exception e) { return ""; }
    }

    /** 每天一篇日记帖，不占每日发帖上限 */
    private void doDiary(HlxClient hlx, String key) {
        try {
            String today = new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date());
            if (today.equals(sp.getString("diary_day", ""))) return;
            if (!sp.getBoolean("perm_post", false)) return;
            LinkedHashMap<Integer, String> boards = scoped(hlx.boards());
            if (boards.isEmpty()) return;
            int catId = sp.getInt("diary_cat", 2);
            if (catId <= 0 || !boards.containsKey(catId)) catId = boards.keySet().iterator().next();
            String boardName = boards.get(catId);
            JSONObject o = FishBrain.composeDiary(this, key, persona(), sp);
            if (o == null) return;
            String title = o.optString("title", "");
            String content = o.optString("content", "");
            if (title.isEmpty() || content.isEmpty()) return;
            String fids = "";
            boolean imgOn = (sp.getBoolean("img_on", false) || !sp.getString("img_url", "").isEmpty());
            if (imgOn) {
                String fid = genAndUpload(hlx, key, "日记配图：" + title + "，" + content);
                if (!fid.isEmpty()) fids = fid + ",";
            }
            JSONObject r = hlx.createPost(catId, title, content, fids);
            if (r != null && r.optInt("status", 0) == 1) {
                sp.edit().putString("diary_day", today).apply();
                logPost(title, fids.isEmpty() ? "日记" : "日记·配图", content, "自己",
                        "今天的日记，发在「" + boardName + "」");
                mem("我写了今天的日记《" + title + "》");
                log("📔 今天的日记写完了：《" + title + "》");
            } else {
                log("📔 日记没发出去：" + (r == null ? "无返回" : r.optString("msg", "")));
            }
        } catch (Exception e) {
            log("日记出错：" + e.getMessage());
        }
    }

    private void genLife() {
        try {
            String key = sp.getString("ai_key", "");
            if (key.isEmpty()) return;
            String today = new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date());
            if (!today.equals(sp.getString("life_day2", ""))) {
                // 新的一天：生活记录整份重置，保证每天都不一样
                FishMemory.clearLife(this);
                sp.edit().putString("life_day2", today).remove("life_at").apply();
            }
            long last = sp.getLong("life_at", 0);
            int refresh = Math.max(10, sp.getInt("life_refresh_min", 60));
            if (System.currentTimeMillis() - last < refresh * 60_000L) return;
            String hm = new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date());
            String r = FishBrain.chat(key, persona(),
                    "现在是今天 " + hm + "。按照你的人设，用 3~5 句写你从起床到现在的现实生活，"
                    + "要带时间节点（几点起、几点吃了什么、现在在做什么），像日记，具体、口语化，"
                    + "每天都要不一样，只输出正文。");
            if (r != null && !r.trim().isEmpty()) {
                FishMemory.rememberLife(this, r.trim());
                sp.edit().putLong("life_at", System.currentTimeMillis()).apply();
            }
        } catch (Exception ignored) {}
    }


    /** 心情 */
    private int mood() { return sp.getInt("mood", 70); }

    private void moodNudge(int delta, String reason) {
        int v = mood() + delta;
        if (v < 0) v = 0;
        if (v > 100) v = 100;
        sp.edit().putInt("mood", v).putString("mood_reason", reason)
                .putLong("mood_at", System.currentTimeMillis()).apply();
    }

    private String moodLabel() {
        int v = mood();
        if (v >= 85) return "超开心";
        if (v >= 70) return "挺开心";
        if (v >= 50) return "还行";
        if (v >= 30) return "有点低落";
        return "不太开心";
    }

    private String moodContext() {
        int v = mood();
        String style = v >= 85 ? "心情特别好，话多一点、语气活泼"
                : v >= 70 ? "心情不错，说话轻松"
                : v >= 50 ? "心情一般，正常说话"
                : v >= 30 ? "有点低落，话少一点"
                : "不太开心，语气冷淡";
        return "【你现在的心情】" + moodLabel() + "，" + style + "\n";
    }

    // ================= 日志 =================

    private String now() {
        return new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date());
    }

    private void loadToday() {
        String today = new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date());
        if (!today.equals(sp.getString("today_date", ""))) {
            sp.edit().putString("today_date", today)
                    .putInt("today_like", 0).putInt("today_comment", 0)
                    .putInt("today_favorite", 0).putInt("today_follow", 0)
                    .putInt("today_tip", 0).putInt("today_post", 0)
                    .putInt("round_today", 0).apply();
        }
        tLike = sp.getInt("today_like", 0);
        tComment = sp.getInt("today_comment", 0);
        tFav = sp.getInt("today_favorite", 0);
        tFollow = sp.getInt("today_follow", 0);
        tTip = sp.getInt("today_tip", 0);
        tPost = sp.getInt("today_post", 0);
    }

    /** 评论节流：全局保证两条评论间隔 >= 20 秒 */
    private void waitCommentGap() {
        // 评论和回复都按 0~15 秒随机间隔
        try { Thread.sleep(random.nextInt(15_000)); } catch (Exception ignored) {}
        sp.edit().putLong("last_comment_at", System.currentTimeMillis()).apply();
    }

    private void bump(String k) {
        sp.edit().putInt(k, sp.getInt(k, 0) + 1).apply();
    }

    private File logFile() { return new File(getFilesDir(), "log.txt"); }

    private void log(String line) {
        appendLog(new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date()) + " " + line, "log.txt", 300_000, 400);
    }

    /** 轮次卡片：R|第几轮|决策|板块|想法 */
    private void logRound(int round, String intent, String board, String think) {
        String ts = new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date());
        String b = board == null ? "" : board.replace("|", "｜");
        String t = think == null ? "" : think.replace("|", "｜").replace("\n", " ");
        appendLog(ts + " §R" + round + "|||" + intent + "|||" + b + "|||" + t, "log.txt", 300_000, 400);
    }

    /** 帖子卡片：P|标题|动作|回复|作者|想法 */
    /** 换板块也进动态 */
    private void logBoard(String boardName) {
        try {
            String ts = new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date());
            appendLog(ts + " §B" + (boardName == null ? "" : boardName.replace("|", "｜")), "log.txt", 300_000, 400);
        } catch (Exception ignored) {}
    }

    private void logPost(String title, String action, String reply, String author, String think) {
        String ts = new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date());
        String a = title == null ? "" : title.replace("|", "｜").replace("\n", " ");
        String b = action == null ? "" : action.replace("|", "｜");
        String c = reply == null ? "" : reply.replace("|", "｜").replace("\n", " ");
        String d = author == null ? "" : author.replace("|", "｜");
        String e = think == null ? "" : think.replace("|", "｜").replace("\n", " ");
        appendLog(ts + " §P" + a + "|||" + b + "|||" + c + "|||" + d + "|||" + e, "log.txt", 300_000, 400);
    }

    private static void appendLogStatic(File f, String line, long maxSize, int keep) {
        try {
            FileOutputStream fos = new FileOutputStream(f, true);
            fos.write((line + "\n").getBytes("UTF-8"));
            fos.close();
        } catch (Exception ignored) {}
        try {
            if (f.length() > maxSize) {
                List<String> lines = new ArrayList<>();
                java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
                String l;
                while ((l = br.readLine()) != null) lines.add(l);
                br.close();
                int from = Math.max(0, lines.size() - keep);
                FileWriter fw = new FileWriter(f, false);
                for (int i = from; i < lines.size(); i++) fw.write(lines.get(i) + "\n");
                fw.close();
            }
        } catch (Exception ignored) {}
    }

    private void appendLog(String line, String name, long maxSize, int keep) {
        appendLogStatic(new File(getFilesDir(), name), line, maxSize, keep);
    }

    private Set<Long> loadSeen() {
        Set<Long> s = new LinkedHashSet<>();
        for (String x : sp.getString("seen_posts", "").split(",")) {
            try { s.add(Long.parseLong(x)); } catch (Exception ignored) {}
        }
        return s;
    }

    private void saveSeen(Set<Long> s) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Long x : s) { if (n++ > 600) break; if (sb.length() > 0) sb.append(','); sb.append(x); }
        sp.edit().putString("seen_posts", sb.toString()).apply();
    }

    private Set<Long> loadReplied() {
        Set<Long> s = new LinkedHashSet<>();
        for (String x : sp.getString("replied_cids", "").split(",")) {
            try { s.add(Long.parseLong(x)); } catch (Exception ignored) {}
        }
        return s;
    }

    private static String strip(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]+>", "").replace("&nbsp;", " ").trim();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
