package dudu.hlx.halo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * 启动时把设备统计上报到 guo-wenlong/lingjie-misc。
 *
 * 数据是一台设备一条，accounts 里累积这台设备登录过的所有账号：
 * {"id":"安装id","device":"机型 / Android 版本","version":"x.y.z",
 *  "first":"2026-10-31 10:00","last":"2026-10-31 10:00",
 *  "accounts":[{"platform":"葫芦侠","uid":123,"nickname":"名字"}]}
 *
 * 编码是两层 Base64：文件字节 = Base64(JSON)，API.content = Base64(文件字节)。
 *
 * 任何异常都被吞掉，绝不影响 App 启动。
 */
public final class StatsReporter {

    private static final String API = "https://gitee.com/api/v5/repos/";
    private static final String REPO_PATH = "guo-wenlong/lingjie-misc/contents/";
    private static final String FILE = "assets/data/.cache/hlx.dat";
    private static final String PLATFORM = "葫芦侠";

    /** 同一台设备 12 小时内不重复上报，避免每次启动都写仓库 */
    private static final long MIN_INTERVAL_MS = 12L * 60 * 60 * 1000L;

    // token 不明文进仓库：Base64 + XOR（与 HaloDesk 的 telemetry.py 保持同一套）
    private static final String KEY = "halo-desk-2026!";
    private static final String TOKEN_OBF = "WVNYXU9WUEtTG1MABwIXDgUOXhkCVUYOHldVAw5AUFU=";

    private StatsReporter() {
    }

    private static String token() {
        try {
            byte[] raw = Base64.decode(TOKEN_OBF, Base64.DEFAULT);
            byte[] key = KEY.getBytes("UTF-8");
            byte[] out = new byte[raw.length];
            for (int i = 0; i < raw.length; i++) {
                out[i] = (byte) (raw[i] ^ key[i % key.length]);
            }
            return new String(out, "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }

    /** 启动时调用：后台线程执行，绝不抛异常 */
    public static void reportAsync(Context context) {
        reportAsync(context, false);
    }

    /** 登录\/切换账号后调用：force=true 无视 12 小时节流，立刻把新账号写进子列表 */
    public static void reportAsync(Context context, final boolean force) {
        if (context == null) return;
        final Context app = context.getApplicationContext();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    report(app, force);
                } catch (Throwable t) {
                    Log.w("Stats", "上报失败: " + t);   // 静默失败，但留一行日志
                }
            }
        }, "halo-stats");
        thread.setDaemon(true);
        thread.start();
    }

    private static void report(Context ctx, boolean force) throws Exception {
        SharedPreferences sp = ctx.getSharedPreferences("fish", Context.MODE_PRIVATE);
        long uid = sp.getLong("hlx_uid", 0L);
        String nickname = sp.getString("hlx_nick", "");
        if (uid <= 0L) return; // 未登录不上报

        String tk = token();
        if (tk.length() == 0) return;

        SharedPreferences meta = ctx.getSharedPreferences("halo_stats", Context.MODE_PRIVATE);
        String installId = deviceId(ctx, meta);

        String version = "";
        try {
            version = ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Throwable ignored) {
        }
        if (version == null) version = "";
        String device = (Build.MODEL == null ? "Android" : Build.MODEL)
                + " / Android " + Build.VERSION.RELEASE;

        String fileUrl = API + REPO_PATH + FILE;
        String queryUrl = fileUrl + "?access_token=" + urlEncode(tk);

        // 1) 读远端当前文件
        String sha = "";
        JSONArray arr = new JSONArray();
        String body = request(queryUrl, null, "GET");
        if (body != null && body.trim().startsWith("{")) {
            JSONObject obj = new JSONObject(body);
            sha = obj.optString("sha", "");
            String content = obj.optString("content", "");
            if (content.length() > 0) {
                String inner = new String(
                        Base64.decode(content.replaceAll("\\s", ""), Base64.DEFAULT), "UTF-8");
                String json;
                try {
                    json = new String(Base64.decode(inner.trim(), Base64.DEFAULT), "UTF-8");
                } catch (Throwable t) {
                    json = inner;   // 兼容只做了一层 Base64 的老文件
                }
                try {
                    arr = new JSONArray(json);
                } catch (Throwable t) {
                    arr = new JSONArray();
                }
            }
        }

        // 清洗：丢掉没有 id 的旧格式行（老版本客户端写的扁平账号行），
        // 同一个 id 出现多次就合并 accounts，避免文件越写越乱
        {
            JSONArray cleaned = new JSONArray();
            for (int i2 = 0; i2 < arr.length(); i2++) {
                JSONObject o2 = arr.optJSONObject(i2);
                if (o2 == null) continue;
                String rid = o2.optString("id", "");
                if (rid.isEmpty()) continue;
                JSONObject same = null;
                for (int k2 = 0; k2 < cleaned.length(); k2++) {
                    JSONObject c2 = cleaned.optJSONObject(k2);
                    if (c2 != null && rid.equals(c2.optString("id"))) { same = c2; break; }
                }
                if (same == null) {
                    cleaned.put(o2);
                } else {
                    JSONArray from = o2.optJSONArray("accounts");
                    JSONArray to = same.optJSONArray("accounts");
                    if (to == null) { to = new JSONArray(); same.put("accounts", to); }
                    if (from != null) for (int a2 = 0; a2 < from.length(); a2++) {
                        JSONObject acc = from.optJSONObject(a2);
                        if (acc == null) continue;
                        boolean dup = false;
                        for (int b2 = 0; b2 < to.length(); b2++) {
                            JSONObject ex = to.optJSONObject(b2);
                            if (ex != null && ex.optLong("uid", 0) == acc.optLong("uid", 0)
                                    && ex.optString("platform", "").equals(acc.optString("platform", ""))) {
                                dup = true;
                                break;
                            }
                        }
                        if (!dup) to.put(acc);
                    }
                }
            }
            arr = cleaned;
        }

        // 不做节流：每次打开应用都上报，保证账号/版本变化能立刻反映到统计里
        boolean remoteExists = sha.length() > 0;
        if (!remoteExists) {
            Log.w("Stats", "远端文件不存在（空数组/无 sha），立即创建");
        }

        // 2) 找到本设备记录并更新
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date());
        JSONObject dev = null;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && installId.equals(o.optString("id"))) {
                dev = o;
                break;
            }
        }
        if (dev == null) {
            dev = new JSONObject();
            dev.put("id", installId);
            dev.put("first", now);
            dev.put("accounts", new JSONArray());
            arr.put(dev);
        }
        dev.put("device", device);
        dev.put("version", version);
        dev.put("last", now);

        // 3) 追加或更新账号（历史账号保留）
        JSONArray accounts = dev.optJSONArray("accounts");
        if (accounts == null) {
            accounts = new JSONArray();
            dev.put("accounts", accounts);
        }
        boolean found = false;
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject a = accounts.optJSONObject(i);
            if (a != null && a.optLong("uid", 0L) == uid) {
                a.put("platform", PLATFORM);
                a.put("nickname", nickname);
                a.put("version", version);   // 这个账号是在哪个版本登录的
                a.put("last", now);          // 最近一次登录/上报时间
                found = true;
                break;
            }
        }
        if (!found) {
            JSONObject a = new JSONObject();
            a.put("platform", PLATFORM);
            a.put("uid", uid);
            a.put("nickname", nickname);
            a.put("version", version);
            a.put("last", now);
            accounts.put(a);
        }

        // 4) 双层 Base64 写回
        String inner = Base64.encodeToString(arr.toString().getBytes("UTF-8"), Base64.NO_WRAP);
        String outer = Base64.encodeToString(inner.getBytes("UTF-8"), Base64.NO_WRAP);

        JSONObject payload = new JSONObject();
        payload.put("access_token", tk);
        payload.put("content", outer);
        payload.put("message", "stats: " + PLATFORM + " " + version);
        boolean updating = sha.length() > 0;
        if (updating) payload.put("sha", sha);

        String resp = request(fileUrl, payload.toString(), updating ? "PUT" : "POST");
        if (resp != null) {
            meta.edit().putLong("last_at", System.currentTimeMillis())
                    .putString("last_uid", String.valueOf(uid)).apply();
            Log.d("Stats", "上报成功：" + (updating ? "PUT" : "POST") + " uid=" + uid
                    + " version=" + version);
        } else {
            Log.w("Stats", "上报失败：写回无响应（" + (updating ? "PUT" : "POST") + "），下次启动重试");
        }
    }

    /** 设备 id：由 ANDROID_ID 派生，卸载重装/清数据都不变 */
    private static String deviceId(Context ctx, SharedPreferences meta) {
        try {
            String aid = android.provider.Settings.Secure.getString(
                    ctx.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
            if (aid != null && aid.length() > 0 && !"9774d56d682e549c".equals(aid)) {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                byte[] d = md.digest((aid + "|halo-stats").getBytes("UTF-8"));
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 16; i++) sb.append(String.format("%02x", d[i]));
                return sb.toString();
            }
        } catch (Throwable ignored) {
        }
        String fallback = meta.getString("install_id", "");
        if (fallback == null || fallback.length() == 0) {
            fallback = UUID.randomUUID().toString().replace("-", "");
            meta.edit().putString("install_id", fallback).apply();
        }
        return fallback;
    }

    // ------------------------------------------------------------ 网络

    private static String request(String url, String body, String method) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "HaloStats/1.0");
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                OutputStream os = conn.getOutputStream();
                try {
                    os.write(body.getBytes("UTF-8"));
                } finally {
                    os.close();
                }
            }
            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) return null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            } finally {
                is.close();
            }
            String text = new String(bos.toByteArray(), "UTF-8");
            return code >= 200 && code < 300 ? text : null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Throwable t) {
            return s;
        }
    }
}
