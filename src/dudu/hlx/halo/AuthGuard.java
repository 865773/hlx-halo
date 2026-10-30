package dudu.hlx.halo;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 统一登录异常检测（葫芦侠版）。
 *
 * 判定顺序：空响应/JSON 坏 → 只重试；网络类关键词 → 只重试；
 * 明确异常关键词 → 5 分钟内连续 2 次才退出登录；其余 → 清零计数。
 * 所有接口返回后都应调用 {@link #check(String)}。
 */
public final class AuthGuard {

    private static final String TAG = "AuthGuard";
    private static final String PLATFORM = "葫芦侠";
    private static final String PREFS = "fish";

    private static final String[] CLEAR_KEYS = {
            "hlx_key", "hlx_uid", "hlx_nick", "hlx_avatar", "hlx_level",
    };

    private static final String[] HARD_WORDS = {
            "存在风险", "紧急封禁", "封禁", "冻结", "修改密码", "账号异常", "异地登录",
            "禁止登录", "重新登录", "登录失效", "登录已过期", "未授权", "未登录",
            "token无效", "token 无效", "invalid token", "请先登录",
    };

    private static final String[] SOFT_WORDS = {
            "超时", "timeout", "timed out", "网络", "连接失败",
            "服务不可用", "系统繁忙", "请稍后",
    };

    private static final long WINDOW_MS = 5 * 60 * 1000L;
    private static final int CONFIRM_COUNT = 2;

    private static Context appCtx;
    private static LogoutHandler handler;

    private static int hits = 0;
    private static long firstHitAt = 0L;
    private static String lastWord = "";
    private static final List<String> recent = new ArrayList<String>();

    public interface LogoutHandler {
        void onLoggedOut(String reason);
    }

    private AuthGuard() {
    }

    public static void install(Context ctx, LogoutHandler h) {
        appCtx = ctx == null ? null : ctx.getApplicationContext();
        handler = h;
    }

    /** 最近的判定记录，方便排查（最多 50 条） */
    public static List<String> recentRecords() {
        synchronized (recent) {
            return new ArrayList<String>(recent);
        }
    }

    // ------------------------------------------------------------ 判定

    public static final int OK = 0;
    public static final int SOFT = 1;
    public static final int HARD = 2;

    public static int classify(String resp) {
        if (resp == null || resp.trim().isEmpty()) return SOFT;
        if (looksLikeBrokenJson(resp)) return SOFT;

        String lower = resp.toLowerCase(Locale.ROOT);
        for (String w : SOFT_WORDS) {
            if (lower.contains(w.toLowerCase(Locale.ROOT))) return SOFT;
        }
        for (String w : HARD_WORDS) {
            if (resp.contains(w)) {
                lastWord = w;
                return HARD;
            }
        }
        return OK;
    }

    private static boolean looksLikeBrokenJson(String resp) {
        String t = resp.trim();
        if (t.isEmpty()) return true;
        // 只做括号粗检，不依赖 org.json：避免被截断的响应被当成正常
        if (t.startsWith("{") && !t.endsWith("}")) return true;
        if (t.startsWith("[") && !t.endsWith("]")) return true;
        return false;
    }

    /** 所有接口返回后调用；返回 true 表示本次判定为登录失效并已退出 */
    public static boolean check(String resp) {
        int kind = classify(resp);
        if (kind == SOFT) {
            log(PLATFORM + " 网络/空响应，只重试：" + brief(resp));
            return false;
        }
        if (kind == OK) {
            if (hits > 0) {
                log(PLATFORM + " 恢复正常，计数清零（之前 " + hits + " 次）");
            }
            reset();
            return false;
        }
        return onHardHit(resp);
    }

    /** 状态接口专用（葫芦侠 /user/status）：HTTP 200 也要看 msg */
    public static boolean checkStatus(String resp) {
        return check(resp);
    }

    private static boolean onHardHit(String resp) {
        long now = System.currentTimeMillis();
        if (firstHitAt == 0L || now - firstHitAt > WINDOW_MS) {
            hits = 1;
            firstHitAt = now;
            log(PLATFORM + " 首次明确异常，等待确认 1/" + CONFIRM_COUNT
                    + " 命中词=" + lastWord + " msg=" + brief(resp));
            return false;
        }
        hits++;
        log(PLATFORM + " 明确异常累计 " + hits + "/" + CONFIRM_COUNT
                + " 命中词=" + lastWord + " msg=" + brief(resp));
        if (hits >= CONFIRM_COUNT) {
            String reason = extractReason(resp);
            reset();
            doLogout(reason);
            return true;
        }
        return false;
    }

    private static void reset() {
        hits = 0;
        firstHitAt = 0L;
        lastWord = "";
    }

    static String extractReason(String resp) {
        if (resp == null) return "登录状态异常";
        String msg = field(resp, "msg");
        if (msg.isEmpty()) msg = field(resp, "message");
        if (msg.isEmpty()) {
            String b = brief(resp);
            msg = b.isEmpty() ? "登录状态异常" : b;
        }
        return msg;
    }

    /** 轻量取 JSON 字符串字段，不依赖 org.json（便于单测） */
    static String field(String json, String key) {
        if (json == null) return "";
        String k = "\"" + key + "\"";
        int i = json.indexOf(k);
        if (i < 0) return "";
        int colon = json.indexOf(':', i + k.length());
        if (colon < 0) return "";
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) return "";
        StringBuilder sb = new StringBuilder();
        int p = q1 + 1;
        while (p < json.length()) {
            char ch = json.charAt(p);
            if (ch == '\\') {
                p += 2;
                continue;
            }
            if (ch == '"') break;
            sb.append(ch);
            p++;
        }
        return sb.toString().trim();
    }

    private static String brief(String s) {
        if (s == null) return "";
        String t = s.replace('\n', ' ').trim();
        return t.length() > 160 ? t.substring(0, 160) : t;
    }

    private static void log(String msg) {
        try {
            Log.w(TAG, msg);
        } catch (Throwable ignored) {
        }
        synchronized (recent) {
            recent.add(System.currentTimeMillis() + " " + msg);
            while (recent.size() > 50) recent.remove(0);
        }
    }

    // ------------------------------------------------------------ 退出登录

    private static void doLogout(String reason) {
        final Context ctx = appCtx;
        final String text = PLATFORM + "：" + reason;
        log(PLATFORM + " 判定登录失效，执行退出：" + text);

        if (ctx != null) {
            try {
                SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                SharedPreferences.Editor ed = sp.edit();
                for (String k : CLEAR_KEYS) ed.remove(k);
                ed.apply();
            } catch (Throwable e) {
                log(PLATFORM + " 清理登录字段失败：" + e);
            }
        }

        final LogoutHandler h = handler;
        if (h != null) {
            try {
                h.onLoggedOut(text);
                return;
            } catch (Throwable e) {
                log(PLATFORM + " onLoggedOut 异常：" + e);
            }
        }
        if (ctx != null) {
            try {
                Toast.makeText(ctx, text, Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) {
            }
        }
    }

    /** 单测用：清空状态 */
    static void resetForTest() {
        reset();
        handler = null;
        appCtx = null;
        synchronized (recent) {
            recent.clear();
        }
    }
}
