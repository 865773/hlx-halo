package dudu.hlx.halo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Base64;

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
        if (context == null) return;
        final Context app = context.getApplicationContext();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    report(app);
                } catch (Throwable ignored) {
                    // 静默失败：统计不影响主流程
                }
            }
        }, "halo-stats");
        thread.setDaemon(true);
        thread.start();
    }

    private static void report(Context ctx) throws Exception {
        SharedPreferences sp = ctx.getSharedPreferences("fish", Context.MODE_PRIVATE);
        long uid = sp.getLong("hlx_uid", 0L);
        String nickname = sp.getString("hlx_nick", "");
        if (uid <= 0L) return; // 未登录不上报

        String tk = token();
        if (tk.length() == 0) return;

        SharedPreferences meta = ctx.getSharedPreferences("halo_stats", Context.MODE_PRIVATE);
        long lastAt = meta.getLong("last_at", 0L);
        if (lastAt > 0L && System.currentTimeMillis() - lastAt < MIN_INTERVAL_MS) {
            return;
        }

        String installId = meta.getString("install_id", "");
        if (installId == null || installId.length() == 0) {
            installId = UUID.randomUUID().toString().replace("-", "");
            meta.edit().putString("install_id", installId).apply();
        }

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
                String json = new String(
                        Base64.decode(inner.trim(), Base64.DEFAULT), "UTF-8");
                arr = new JSONArray(json);
            }
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
                found = true;
                break;
            }
        }
        if (!found) {
            JSONObject a = new JSONObject();
            a.put("platform", PLATFORM);
            a.put("uid", uid);
            a.put("nickname", nickname);
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
            meta.edit().putLong("last_at", System.currentTimeMillis()).apply();
        }
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
