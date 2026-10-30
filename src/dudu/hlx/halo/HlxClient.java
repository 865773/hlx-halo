package dudu.hlx.halo;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 葫芦侠三楼 API 客户端
 * 所有签名、端点、参数都封装在这里，供大肥鱼调用
 */
public class HlxClient {

    public static final String BASE = "http://floor.huluxia.com";
    public static final String UPLOAD_HOST = "http://upload.huluxia.com";
    public static final String SECRET = "fa1c28a5b62e79c3e63d9030b6142e4b";
    public static final String APP_VER = "4.4.0.6";
    public static final String VER_CODE = "20141528";
    public static final String MARKET = "floor_web";
    public static final String UA = "okhttp/3.8.1";
    public static final String DEV_RAW = "[d]5125c3c6-f456-4c6b-81cf-9bc467522d61";
    public static final String DEV_ENC = "%5Bd%5D5125c3c6-f456-4c6b-81cf-9bc467522d61";

    private String lastErr = "";
    public String lastError() { return lastErr; }
    private int lastCode = 0;
    private String lastMsg = "";
    public int lastCode() { return lastCode; }
    public String lastMsg() { return lastMsg; }

    private final SharedPreferences sp;

    public HlxClient(SharedPreferences sp) {
        this.sp = sp;
    }

    // ================= 基础工具 =================

    public static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] b = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            String resp = sb.toString();
            AuthGuard.check(resp);
            return resp;
        } catch (Exception e) {
            return "";
        }
    }

    public static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    public String key() {
        return sp.getString("hlx_key", "");
    }

    public long uid() {
        return sp.getLong("hlx_uid", 0);
    }

    public String common() {
        return "platform=2&gkey=000000&app_version=" + APP_VER + "&versioncode=" + VER_CODE
                + "&market_id=" + MARKET;
    }

    public String auth() {
        return common() + "&_key=" + key() + "&device_code=" + DEV_ENC + "&phone_brand_type=MI";
    }

    private String httpGet(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(12000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", UA);
            int code = c.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    code >= 400 ? c.getErrorStream() : c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            String resp = sb.toString();
            AuthGuard.check(resp);
            return resp;
        } catch (Exception e) {
            return "";
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private String httpPost(String url, String body) {
        HttpURLConnection c = null;
        try {
            byte[] data = body.getBytes("UTF-8");
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(12000);
            c.setReadTimeout(20000);
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(data.length);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
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
            String resp = sb.toString();
            AuthGuard.check(resp);
            return resp;
        } catch (Exception e) {
            return "";
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static JSONObject json(String s) {
        try {
            if (s == null || s.trim().isEmpty()) return null;
            int a = s.indexOf('{');
            int b = s.lastIndexOf('}');
            if (a < 0 || b <= a) return null;
            return new JSONObject(s.substring(a, b + 1));
        } catch (Exception e) {
            return null;
        }
    }

    /** 是否登录态失效 */
    public static boolean isDead(String resp) {
        return AuthGuard.classify(resp) == AuthGuard.HARD;
    }

    // ================= 登录 =================

    /**
     * 手机号 + 密码登录，成功返回 _key，失败返回 null
     */
    public String login(String phone, String pwdPlain) {
        String pwd = md5(pwdPlain);
        String raw = "account" + phone + "device_code" + DEV_RAW + "password" + pwd + "voice_code";
        String sign = md5(raw + SECRET);
        String url = BASE + "/account/login/ANDROID/4.1.8?" + common()
                + "&device_code=" + DEV_ENC
                + "&account=" + enc(phone)
                + "&login_type=2"
                + "&password=" + pwd
                + "&voice_code=&sign=" + sign;
        JSONObject o = json(httpGet(url));
        if (o == null) return null;
        String k = o.optString("_key", "");
        if (k.isEmpty()) return null;
        JSONObject user = o.optJSONObject("user");
        long id = user == null ? 0 : user.optLong("userID", 0);
        String nick = user == null ? "" : user.optString("nick", "");
        String avatar = user == null ? "" : user.optString("avatar", "");
        int level = user == null ? 0 : user.optInt("level", 0);
        sp.edit()
                .putString("hlx_key", k)
                .putLong("hlx_uid", id)
                .putString("hlx_nick", nick)
                .putString("hlx_avatar", avatar)
                .putInt("hlx_level", level)
                .putString("hlx_phone", phone)
                .putString("hlx_pwd", pwdPlain)
                .apply();
        return k;
    }

    /** 用已保存的账号密码重新登录 */
    public String relogin() {
        String p = sp.getString("hlx_phone", "");
        String w = sp.getString("hlx_pwd", "");
        if (p.isEmpty() || w.isEmpty()) return null;
        return login(p, w);
    }

    public boolean checkLogin() {
        String r = httpGet(BASE + "/user/status/ANDROID/2.1?" + auth());
        JSONObject o = json(r);
        return o != null && o.optInt("status", 0) == 1;
    }

    // ================= 用户 =================

    public JSONObject myInfo() {
        return json(httpGet(BASE + "/user/info/ANDROID/4.1.8?" + auth() + "&user_id=" + uid()));
    }

    public JSONObject userInfo(long userId) {
        return json(httpGet(BASE + "/user/info/ANDROID/4.1.8?" + auth() + "&user_id=" + userId));
    }

    // ================= 板块 / 帖子 =================

    /** 板块列表：返回 fid -> name */
    public LinkedHashMap<Integer, String> boards() {
        LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
        JSONObject o = json(httpGet(BASE + "/category/list/ANDROID/2.0"));
        if (o == null) return map;
        JSONArray arr = o.optJSONArray("categories");
        if (arr == null) return map;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.optJSONObject(i);
            if (c == null) continue;
            int id = c.optInt("categoryID", 0);
            String t = c.optString("title", "");
            if (id > 0 && !t.isEmpty()) map.put(id, t);
        }
        return map;
    }

    /** 帖子列表 */
    public List<JSONObject> posts(int catId, int start, int count) {
        List<JSONObject> list = new ArrayList<>();
        String url = BASE + "/post/list/ANDROID/4.1.8?" + auth()
                + "&cat_id=" + catId + "&tag_id=0&sort_by=0&start=" + start + "&count=" + count;
        JSONObject o = json(httpGet(url));
        if (o == null) return list;
        JSONArray arr = o.optJSONArray("posts");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.optJSONObject(i);
                if (p != null) list.add(p);
            }
        }
        return list;
    }

    public JSONObject postDetail(long postId) {
        JSONObject o = json(httpGet(BASE + "/post/detail/ANDROID/4.1.8?" + auth()
                + "&post_id=" + postId + "&page=1"));
        return o == null ? null : o.optJSONObject("post");
    }

    /** 评论列表：走 post/detail 的 comments 字段（comment/create/list 会返回空） */
    public List<JSONObject> comments(long postId, int page) {
        List<JSONObject> list = new ArrayList<>();
        JSONObject o = json(httpGet(BASE + "/post/detail/ANDROID/4.1.8?" + auth()
                + "&post_id=" + postId + "&page=" + page));
        if (o == null) return list;
        JSONObject post = o.optJSONObject("post");
        JSONArray arr = null;
        if (post != null) arr = post.optJSONArray("comments");
        if (arr == null) arr = o.optJSONArray("comments");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject c = arr.optJSONObject(i);
                if (c != null) list.add(c);
            }
        }
        return list;
    }

    /** 我的粉丝列表（用来找哪个号关注了大肥鱼 → 设为主人） */
    public List<JSONObject> followers(long userId, int count) {
        List<JSONObject> list = new ArrayList<>();
        JSONObject o = json(httpGet(BASE + "/friendship/follower/list/ANDROID/4.1.8?" + auth()
                + "&user_id=" + userId + "&start=0&count=" + count));
        if (o == null) return list;
        JSONArray arr = o.optJSONArray("friendships");
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            JSONObject f = arr.optJSONObject(i);
            if (f != null) list.add(f);
        }
        return list;
    }

    /** 某个用户发过的帖子列表 */
    public List<JSONObject> userPosts(long userId, int count) {
        List<JSONObject> list = new ArrayList<>();
        JSONObject o = json(httpGet(BASE + "/post/create/list/ANDROID/4.1.8?" + auth()
                + "&phone_brand_type=MI&start=0&count=" + count + "&user_id=" + userId));
        if (o == null) return list;
        JSONArray arr = o.optJSONArray("posts");
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            JSONObject p = arr.optJSONObject(i);
            if (p != null) list.add(p);
        }
        return list;
    }

    /** 从帖子里提取图片 URL 列表（images 数组 + detail 里的 <image>） */
    public List<String> postImages(JSONObject post) {
        List<String> out = new ArrayList<>();
        if (post == null) return out;
        try {
            JSONArray arr = post.optJSONArray("images");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String u = arr.optString(i, "");
                    if (u.startsWith("http")) out.add(u);
                }
            }
            String d = post.optString("detail", "");
            java.util.regex.Matcher mt = java.util.regex.Pattern
                    .compile("<image>([^<]+)</image>").matcher(d);
            while (mt.find() && out.size() < 3) {
                String u = mt.group(1);
                if (u != null && u.startsWith("http") && !out.contains(u)) out.add(u);
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** 拉取社区表情列表（[{name:...}] 或 [{title:...}]） */
    public String emojiList() {
        String r = httpGet(BASE + "/emoji/list/ANDROID/2.0");
        if (r == null || r.isEmpty()) r = httpGet(BASE + "/emoji/list");
        return r;
    }

    public List<JSONObject> search(String keyword, int count) {
        List<JSONObject> list = new ArrayList<>();
        JSONObject o = json(httpGet(BASE + "/post/search/ANDROID/4.1.8?" + auth()
                + "&keyword=" + enc(keyword) + "&start=0&count=" + count));
        if (o == null) return list;
        JSONArray arr = o.optJSONArray("posts");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.optJSONObject(i);
                if (p != null) list.add(p);
            }
        }
        return list;
    }

    // ================= 互动 =================

    /** 点赞（toggle），返回是否成功 */
    public boolean praise(long postId) {
        JSONObject o = json(httpGet(BASE + "/post/praise/ANDROID/2.1?" + auth() + "&post_id=" + postId));
        return o != null && o.optInt("status", 0) == 1;
    }

    public boolean isPraised(long postId) {
        JSONObject o = json(httpGet(BASE + "/post/praise/check/ANDROID/2.1?" + auth() + "&post_id=" + postId));
        return o != null && o.optInt("isPraise", 0) == 1;
    }

    /** 删自己的帖子（实测：/post/destroy/ANDROID/2.0 返回 status=1 即成功） */
    public boolean deletePost(long postId) {
        String r = httpGet(BASE + "/post/destroy/ANDROID/2.0?" + auth() + "&post_id=" + postId);
        lastErr = r == null ? "无响应" : (r.length() > 160 ? r.substring(0, 160) : r);
        JSONObject o = json(r);
        if (o == null) return false;
        String msg = o.optString("msg", "");
        if (!msg.isEmpty() && !msg.contains("成功")) return false;   // 带非成功提示就是失败
        return o.optInt("status", 0) == 1;
    }

    /** 删评论 */
    public boolean deleteComment(long cid) {
        JSONObject o = json(httpPost(BASE + "/moderator/deleteComment/ANDROID/2.0?" + auth(),
                "cid=" + cid + "&reason=" + enc("删除评论")));
        return o != null && o.optInt("status", 0) == 1;
    }

    /** 给某条评论送葫芦（实测格式：post_id 必填、comment_id 不参与签名） */
    public boolean tipComment(long postId, long commentId, int score, String txt) {
        String raw = "_key" + key()
                + "device_code" + DEV_RAW
                + "post_id" + postId
                + "score" + score
                + "score_txt" + txt;
        String sign = md5(raw + SECRET);
        String body = "post_id=" + postId + "&comment_id=" + commentId
                + "&type_id=2&isadmin=0&score=" + score
                + "&score_txt=" + enc(txt) + "&sign=" + sign;
        String r = httpPost(BASE + "/credits/transfer/ANDROID/4.1.8?" + auth(), body);
        lastErr = r == null ? "无响应" : (r.length() > 160 ? r.substring(0, 160) : r);
        JSONObject o = json(r);
        return o != null && o.optInt("iscredits", 0) == 1;
    }

    /** 我的关注流（App 的「我的关注」板块，实测 cat_id=0 + 4.2.4） */
    public List<JSONObject> followFeed(int count) {
        List<JSONObject> list = new ArrayList<>();
        JSONObject o = json(httpGet(BASE + "/post/list/ANDROID/4.2.4?" + auth()
                + "&cat_id=0&tag_id=0&sort_by=0&start=0&count=" + count));
        if (o == null) return list;
        JSONArray arr = o.optJSONArray("posts");
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            JSONObject p = arr.optJSONObject(i);
            if (p != null) list.add(p);
        }
        return list;
    }

    /** 互关好友（App 里 @ 弹窗的那个列表） */
    public List<JSONObject> mutualFriends(int count) {
        List<JSONObject> list = new ArrayList<>();
        JSONObject o = json(httpGet(BASE + "/user/friend/ANDROID/4.1.8?" + auth()
                + "&start=0&count=" + count));
        if (o == null) return list;
        JSONArray arr = o.optJSONArray("friendships");
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            JSONObject f = arr.optJSONObject(i);
            if (f != null) list.add(f);
        }
        return list;
    }

    /** 上次 atify 生成的 remindUsers JSON（App 实测格式：[{"nick":..,"userID":..}]） */
    private String lastRemind = "[]";
    public String lastRemind() { return lastRemind; }

    /** 把 @昵称 换成葫芦侠的提及标记，并生成 remindUsers */
    public String atify(String text) {
        lastRemind = "[]";
        if (text == null || !text.contains("@")) return text;
        try {
            JSONArray arr = new JSONArray();
            java.util.Set<String> seen = new java.util.HashSet<>();
            List<JSONObject> fs = mutualFriends(30);
            for (JSONObject f : fs) {
                JSONObject u = f.optJSONObject("user");
                if (u == null) continue;
                String nick = u.optString("nick", "");
                long id = u.optLong("userID", 0);
                if (nick.isEmpty() || id <= 0) continue;
                String plain = "@" + nick;
                if (!text.contains(plain)) continue;
                if (!text.contains("\u202d" + plain)) {
                    text = text.replace(plain, "\u202d" + plain + " \u202c");
                }
                if (seen.add(String.valueOf(id))) {
                    arr.put(new JSONObject().put("nick", nick).put("userID", id));
                }
            }
            long owner = sp.getLong("owner_uid", 0);
            if (owner > 0 && !seen.contains(String.valueOf(owner))
                    && (text.contains("@主人") || text.contains("@大大王"))) {
                for (JSONObject f : fs) {
                    JSONObject u = f.optJSONObject("user");
                    if (u == null || u.optLong("userID", 0) != owner) continue;
                    String nick = u.optString("nick", "");
                    if (nick.isEmpty()) break;
                    String mark = "\u202d@" + nick + " \u202c";
                    text = text.replace("@主人", mark).replace("@大大王", mark);
                    arr.put(new JSONObject().put("nick", nick).put("userID", owner));
                    break;
                }
            }
            lastRemind = arr.toString();
        } catch (Exception ignored) {}
        return text;
    }

    /** 我的收藏列表 */
    public List<JSONObject> favorites(long userId, int count) {
        List<JSONObject> list = new ArrayList<>();
        JSONObject o = json(httpGet(BASE + "/post/favorite/list/ANDROID/4.1.8?" + auth()
                + "&user_id=" + userId + "&start=0&count=" + count));
        if (o == null) return list;
        JSONArray arr = o.optJSONArray("posts");
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            JSONObject x = arr.optJSONObject(i);
            if (x != null) list.add(x);
        }
        return list;
    }

    /** 我的关注列表 */
    public List<JSONObject> followingList(long userId, int count) {
        List<JSONObject> list = new ArrayList<>();
        JSONObject o = json(httpGet(BASE + "/friendship/following/list/ANDROID/4.1.8?" + auth()
                + "&user_id=" + userId + "&start=0&count=" + count));
        if (o == null) return list;
        JSONArray arr = o.optJSONArray("friendships");
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            JSONObject x = arr.optJSONObject(i);
            if (x != null) list.add(x);
        }
        return list;
    }

    public boolean unlike(long postId) {
        JSONObject o = json(httpGet(BASE + "/post/praise/cancel/ANDROID/2.1?" + auth() + "&post_id=" + postId));
        return o != null && o.optInt("status", 0) == 1;
    }

    public boolean unfavorite(long postId) {
        JSONObject o = json(httpGet(BASE + "/post/favorite/destroy/ANDROID/4.1.8?" + auth() + "&post_id=" + postId));
        return o != null && o.optInt("status", 0) == 1;
    }

    public boolean favorite(long postId) {
        JSONObject o = json(httpGet(BASE + "/post/favorite/create/ANDROID/4.1.8?" + auth() + "&post_id=" + postId));
        return o != null && o.optInt("status", 0) == 1;
    }

    public boolean isFavorited(long postId) {
        JSONObject o = json(httpGet(BASE + "/post/favorite/check/ANDROID/2.0?" + auth() + "&post_id=" + postId));
        return o != null && o.optInt("isFavorite", 0) == 1;
    }

    public boolean follow(long userId) {
        JSONObject o = json(httpGet(BASE + "/friendship/follow/ANDROID/2.0?" + auth() + "&user_id=" + userId));
        return o != null && o.optInt("status", 0) == 1;
    }

    public boolean unfollow(long userId) {
        JSONObject o = json(httpGet(BASE + "/friendship/unfollow/ANDROID/2.0?" + auth() + "&user_id=" + userId));
        return o != null && o.optInt("status", 0) == 1;
    }

    /** 关注关系：0未 1已 3互 */
    public int friendship(long userId) {
        JSONObject o = json(httpGet(BASE + "/friendship/check/ANDROID/2.1?" + auth() + "&user_id=" + userId));
        return o == null ? 0 : o.optInt("friendship", 0);
    }

    /** 安全关注（先查再关注，避免 toggle 误取关） */
    /** 我真的关注了他吗（以关注列表为准，不信 friendship 的状态码） */
    public boolean isFollowing(long userId) {
        try {
            for (JSONObject f : followingList(uid(), 100)) {
                JSONObject u = f.optJSONObject("user");
                if (u != null && u.optLong("userID", 0) == userId) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public boolean followSafe(long userId) {
        if (userId <= 0) return false;
        if (isFollowing(userId)) return true;      // 已经关注，直接算成功
        if (follow(userId)) return true;
        return isFollowing(userId);                // 再确认一次
    }

    /** 发评论 / 回复评论 */
    public boolean comment(long postId, long replyCid, String text) {
        text = atify(text);
        String remind = "[]".equals(lastRemind) ? "" : lastRemind;   // 没@人就留空，别发 []
        String raw = "_key" + key()
                + "comment_id" + replyCid
                + "device_code" + DEV_RAW
                + "images"
                + "post_id" + postId
                + "text" + text;
        String sign = md5(raw + SECRET);
        String body = "post_id=" + postId + "&comment_id=" + replyCid
                + "&text=" + enc(text) + "&patcha=&images=&remindUsers=" + enc(remind) + "&sign=" + sign;
        String raw2 = httpPost(BASE + "/comment/create/ANDROID/4.1.8?" + auth(), body);
        lastErr = raw2 == null ? "无响应" : (raw2.length() > 160 ? raw2.substring(0, 160) : raw2);
        JSONObject o = json(raw2);
        if (o != null) {
            lastCode = o.optInt("code", 0);
            lastMsg = o.optString("msg", "");
        } else {
            lastCode = 0; lastMsg = "无响应";
        }
        return o != null && (o.optInt("status", 0) == 1);
    }

    /** 打赏葫芦 */
    public boolean tip(long postId, int score, String txt) {
        String raw = "_key" + key()
                + "device_code" + DEV_RAW
                + "post_id" + postId
                + "score" + score
                + "score_txt" + txt;
        String sign = md5(raw + SECRET);
        String body = "post_id=" + postId + "&type_id=1&isadmin=0&score=" + score
                + "&score_txt=" + enc(txt) + "&sign=" + sign;
        JSONObject o = json(httpPost(BASE + "/credits/transfer/ANDROID/4.1.8?" + auth(), body));
        return o != null && o.optInt("iscredits", 0) == 1;
    }

    /** 发帖（可带图 fid，逗号分隔末尾带逗号） */
    public JSONObject createPost(int catId, String title, String content, String imageFids) {
        String detail = "<text>" + content + "</text>";
        String images = imageFids == null ? "" : imageFids;
        String raw = "_key" + key()
                + "detail" + detail
                + "device_code" + DEV_RAW
                + "images" + images
                + "title" + title
                + "voice";
        String sign = md5(raw + SECRET);
        String body = "draft_id=0&cat_id=" + catId + "&tag_id=-1&type=0"
                + "&title=" + enc(title)
                + "&detail=" + enc(detail)
                + "&patcha=&voice=&lng=0.0&lat=0.0"
                + "&images=" + enc(images)
                + "&user_ids=&recommendTopics=&remindTopics="
                + "&sign=" + sign + "&is_app_link=3";
        JSONObject o = json(httpPost(BASE + "/post/create/ANDROID/4.2.2?" + auth(), body));
        if (o != null) { lastCode = o.optInt("code", 0); lastMsg = o.optString("msg", ""); }
        return o;
    }

    /** 签到（单个板块） */
    public boolean signin(int catId) {
        long ts = System.currentTimeMillis() / 1000;
        String sign = md5("cat_id" + catId + "time" + ts + SECRET).toUpperCase();
        String body = "sign=" + sign;
        String url = BASE + "/user/signin/ANDROID/4.1.8?" + auth()
                + "&cat_id=" + catId + "&time=" + ts;
        JSONObject o = json(httpPost(url, body));
        return o != null && o.optInt("status", 0) == 1;
    }

    // ================= 列表（供 AI 参考） =================

    public List<JSONObject> following(long userId, int count) {
        List<JSONObject> list = new ArrayList<>();
        JSONObject o = json(httpGet(BASE + "/friendship/following/list/ANDROID/4.1.8?" + auth()
                + "&user_id=" + userId + "&start=0&count=" + count));
        if (o == null) return list;
        JSONArray arr = o.optJSONArray("friendships");
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            JSONObject f = arr.optJSONObject(i);
            if (f != null) list.add(f);
        }
        return list;
    }

    public List<JSONObject> notifications(int count) {
        List<JSONObject> list = new ArrayList<>();
        JSONObject o = json(httpGet(BASE + "/message/new/list/ANDROID/4.1?" + auth()
                + "&type_id=8&start=0&count=" + count));
        if (o == null) return list;
        JSONArray arr = o.optJSONArray("datas");
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            JSONObject d = arr.optJSONObject(i);
            if (d != null) list.add(d);
        }
        return list;
    }

    // ================= 图片上传 =================

    /**
     * 上传图片（multipart），成功返回 fid（形如 g4/M00/DE/49/xxx.png），失败返回 null
     */
    public String uploadImage(byte[] png) {
        String ts = String.valueOf(System.currentTimeMillis());
        String nonce = Long.toHexString(System.nanoTime()) + "abcdef";
        String raw = "_key=" + key()
                + "&app_version=" + APP_VER
                + "&device_code=" + DEV_RAW
                + "&gkey=000000"
                + "&market_id=" + MARKET
                + "&nonce_str=" + nonce
                + "&platform=2"
                + "&timestamp=" + ts
                + "&use_type=2"
                + "&versioncode=" + VER_CODE
                + "&secret=my_sign@huluxia.com";
        String sign = md5(raw).toUpperCase();
        String url = UPLOAD_HOST + "/upload/v3/image?_key=" + key()
                + "&app_version=" + APP_VER + "&device_code=" + DEV_ENC
                + "&gkey=000000&market_id=" + MARKET + "&nonce_str=" + nonce
                + "&platform=2&timestamp=" + ts + "&use_type=2"
                + "&versioncode=" + VER_CODE + "&sign=" + sign;
        String boundary = "----HlxBoundary" + System.currentTimeMillis();
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(15000);
            c.setReadTimeout(40000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            OutputStream os = c.getOutputStream();
            os.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"_key\"\r\n\r\nkey_10\r\n").getBytes("UTF-8"));
            os.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes("UTF-8"));
            os.write(png);
            os.write(("\r\n--" + boundary + "--\r\n").getBytes("UTF-8"));
            os.flush();
            os.close();
            int code = c.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    code >= 400 ? c.getErrorStream() : c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            JSONObject o = json(sb.toString());
            if (o == null) return null;
            return o.optString("fid", null);
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
