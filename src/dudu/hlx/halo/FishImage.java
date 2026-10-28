package dudu.hlx.halo;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 文生图：支持 openai / dashscope / sd 三种协议（和灵芥助手一致）
 */
public class FishImage {

    /** 最近一次失败原因（给日志用） */
    public static String lastError = "";

    /** 生成图片，返回 PNG 字节；失败返回 null */
    public static byte[] generate(SharedPreferences sp, String prompt) {
        String proto = sp.getString("img_proto", "openai");
        String url = sp.getString("img_url", "");
        String key = sp.getString("img_key", "");
        String model = sp.getString("img_model", "");
        String size = sp.getString("img_size", "1024x1024");
        if (url.isEmpty()) return null;
        try {
            if ("dashscope".equals(proto)) {
                String base = url.trim();
                while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
                int at = base.indexOf("/services/aigc/");
                if (at > 0) base = base.substring(0, at);
                // 候选模型：先用当前模型，再按预设备份列表轮换；一个额度用完自动换下一个
                java.util.List<String> cand = new java.util.ArrayList<>();
                String cur = model == null ? "" : model.trim();
                if (!cur.isEmpty()) cand.add(cur);
                String preset = sp.getString("img_models", "");
                if (preset != null && !preset.trim().isEmpty()) {
                    for (String x : preset.split(",")) {
                        String y = x.trim();
                        if (!y.isEmpty() && !cand.contains(y)) cand.add(y);
                    }
                }
                if (cand.isEmpty()) cand.add("wan2.5-t2i-preview");
                int start = sp.getInt("img_model_i", 0);
                if (start < 0 || start >= cand.size()) start = 0;
                String lastMsg = "";
                for (int k = 0; k < cand.size(); k++) {
                    int idx = (start + k) % cand.size();
                    String mdl = cand.get(idx);
                    byte[] png;
                    if (base.endsWith("/api/v1")) png = dashscopeAsync(base, key, mdl, size, prompt);
                    else png = dashscope(base, key, mdl, size, prompt);
                    if (png != null) {
                        sp.edit().putString("img_model", mdl).putInt("img_model_i", idx).apply();
                        if (k > 0) FishBrain.dbg("IMG", "换到可用模型：" + mdl);
                        return png;
                    }
                    lastMsg = mdl + "：" + lastError;
                    FishBrain.dbg("IMG", "模型不可用 → " + lastMsg);
                    if (idx == cand.size() - 1) start = 0;   // 转一圈
                }
                lastError = "全部模型都不可用（" + lastMsg + "）";
                return null;
            }
            if ("sd".equals(proto)) return sd(url, prompt);
            return openai(url, key, model.isEmpty() ? "gpt-image-1" : model, size, prompt);
        } catch (Exception e) {
            lastError = "异常：" + e;
            FishBrain.dbg("IMG", lastError);
            return null;
        }
    }

    private static byte[] openai(String url, String key, String model, String size, String prompt) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("prompt", prompt);
        body.put("n", 1);
        body.put("size", size);
        String r = post(url, body.toString(), key);
        JSONObject o = new JSONObject(r);
        JSONArray arr = o.optJSONArray("data");
        if (arr == null || arr.length() == 0) return null;
        JSONObject d = arr.optJSONObject(0);
        String b64 = d.optString("b64_json", "");
        if (!b64.isEmpty()) return Base64.decode(b64, Base64.DEFAULT);
        String u = d.optString("url", "");
        if (!u.isEmpty()) return download(u);
        return null;
    }

    private static byte[] dashscope(String url, String key, String model, String size, String prompt) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        JSONObject input = new JSONObject();
        JSONArray msgs = new JSONArray();
        JSONObject m = new JSONObject();
        m.put("role", "user");
        JSONArray content = new JSONArray();
        content.put(new JSONObject().put("text", prompt));
        m.put("content", content);
        msgs.put(m);
        input.put("messages", msgs);
        body.put("input", input);
        JSONObject params = new JSONObject();
        params.put("size", size.replace("x", "*"));
        params.put("n", 1);
        body.put("parameters", params);
        String r = post(url, body.toString(), key);
        JSONObject o = new JSONObject(r);
        JSONObject out = o.optJSONObject("output");
        if (out == null) return null;
        JSONArray ch = out.optJSONArray("choices");
        if (ch == null || ch.length() == 0) return null;
        JSONObject msg = ch.optJSONObject(0).optJSONObject("message");
        if (msg == null) return null;
        JSONArray cts = msg.optJSONArray("content");
        if (cts == null) return null;
        for (int i = 0; i < cts.length(); i++) {
            JSONObject c = cts.optJSONObject(i);
            if (c == null) continue;
            String img = c.optString("image", "");
            if (!img.isEmpty()) return download(img);
        }
        return null;
    }

    private static byte[] sd(String url, String prompt) throws Exception {
        JSONObject body = new JSONObject();
        body.put("prompt", prompt);
        body.put("steps", 20);
        body.put("width", 512);
        body.put("height", 512);
        String r = post(url, body.toString(), "");
        JSONObject o = new JSONObject(r);
        JSONArray imgs = o.optJSONArray("images");
        if (imgs == null || imgs.length() == 0) return null;
        return Base64.decode(imgs.optString(0), Base64.DEFAULT);
    }

    /** 异步版 POST：带 X-DashScope-Async 头 */
    private static String postAsync(String url, String body, String key) throws Exception {
        byte[] data = body.getBytes("UTF-8");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(data.length);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("X-DashScope-Async", "enable");
        if (key != null && !key.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + key);
        OutputStream os = c.getOutputStream();
        os.write(data); os.flush(); os.close();
        int code = c.getResponseCode();
        BufferedReader br = new BufferedReader(new InputStreamReader(
                code >= 400 ? c.getErrorStream() : c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close(); c.disconnect();
        return sb.toString();
    }

    private static String httpGet(String url, String key) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        if (key != null && !key.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + key);
        BufferedReader br = new BufferedReader(new InputStreamReader(
                c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close(); c.disconnect();
        return sb.toString();
    }

    /** 专属部署（ws-xxx.../api/v1）的异步文生图：提交任务 → 轮询 → 下载 */
    private static byte[] dashscopeAsync(String base, String key, String model, String size, String prompt) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        JSONObject input = new JSONObject();
        input.put("prompt", prompt);
        body.put("input", input);
        JSONObject params = new JSONObject();
        params.put("size", size.replace("x", "*"));
        params.put("n", 1);
        body.put("parameters", params);
        String r = postAsync(base + "/services/aigc/text2image/image-synthesis", body.toString(), key);
        FishBrain.dbg("IMG-SUBMIT", r);
        JSONObject o = new JSONObject(r);
        JSONObject out = o.optJSONObject("output");
        if (out == null) return null;
        String tid = out.optString("task_id", "");
        if (tid.isEmpty()) { lastError = "没有 task_id：" + r; return null; }
        for (int i = 0; i < 40; i++) {
            Thread.sleep(3000);
            String rr = httpGet(base + "/tasks/" + tid, key);
            JSONObject oo = new JSONObject(rr);
            JSONObject o2 = oo.optJSONObject("output");
            if (o2 == null) continue;
            String st = o2.optString("task_status", "");
            if ("SUCCEEDED".equals(st)) {
                JSONArray res = o2.optJSONArray("results");
                if (res == null || res.length() == 0) { lastError = "结果为空：" + rr; return null; }
                String u = res.optJSONObject(0).optString("url", "");
                if (u.isEmpty()) { lastError = "没有图片地址：" + rr; return null; }
                byte[] png = download(u);
                if (png == null) lastError = "下载图片失败：" + u;
                return png;
            }
            if ("FAILED".equals(st)) { lastError = "任务失败：" + rr; FishBrain.dbg("IMG", lastError); return null; }
            if ("CANCELED".equals(st) || "UNKNOWN".equals(st)) { lastError = "任务状态 " + st; return null; }
        }
        return null;
    }

    private static String post(String url, String body, String key) throws Exception {
        byte[] data = body.getBytes("UTF-8");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
        c.setReadTimeout(120000);
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(data.length);
        c.setRequestProperty("Content-Type", "application/json");
        if (key != null && !key.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + key);
        OutputStream os = c.getOutputStream();
        os.write(data);
        os.flush();
        os.close();
        int code = c.getResponseCode();
        BufferedReader br = new BufferedReader(new InputStreamReader(
                code >= 400 ? c.getErrorStream() : c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        c.disconnect();
        return sb.toString();
    }

    public static byte[] download(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = c.getInputStream().read(buf)) > 0) bos.write(buf, 0, n);
            c.disconnect();
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** 字节转 Bitmap（用于预览） */
    public static Bitmap toBitmap(byte[] b) {
        try {
            return BitmapFactory.decodeByteArray(b, 0, b.length);
        } catch (Exception e) {
            return null;
        }
    }
}
