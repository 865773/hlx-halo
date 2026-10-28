package dudu.hlx.halo;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 大肥鱼的记忆：通用记忆 + 按用户的记忆 + 生活记忆
 */
public class FishMemory {

    private static File f(Context c, String name) {
        return new File(c.getFilesDir(), name);
    }

    private static String now() {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date());
    }

    private static List<String> readAll(File file) {
        List<String> list = new ArrayList<>();
        try {
            if (!file.exists()) return list;
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String l;
            while ((l = br.readLine()) != null) {
                if (!l.trim().isEmpty()) list.add(l.trim());
            }
            br.close();
        } catch (Exception ignored) {}
        return list;
    }

    private static void append(File file, String line) {
        try {
            FileOutputStream fos = new FileOutputStream(file, true);
            fos.write((now() + " " + line + "\n").getBytes("UTF-8"));
            fos.close();
        } catch (Exception ignored) {}
    }

    private static String tail(List<String> list, int n) {
        if (list.isEmpty()) return "";
        int from = Math.max(0, list.size() - n);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < list.size(); i++) sb.append(list.get(i)).append('\n');
        return sb.toString();
    }

    // ===== 通用记忆 =====
    public static void remember(Context c, String line) {
        if (line == null || line.trim().isEmpty()) return;
        append(f(c, "memory.txt"), line.trim());
        trimIfBig(c, "memory.txt", 160, 100);
    }

    public static String recent(Context c, int n) {
        return tail(readAll(f(c, "memory.txt")), n);
    }

    public static String allMemory(Context c) {
        return tail(readAll(f(c, "memory.txt")), 200);
    }

    // ===== 用户记忆 =====
    public static void rememberUser(Context c, long uid, String nick, String line) {
        if (uid <= 0 || line == null || line.trim().isEmpty()) return;
        append(f(c, "mem_u" + uid + ".txt"), line.trim());
        trimIfBig(c, "mem_u" + uid + ".txt", 120, 60);
    }

    public static String recentUser(Context c, long uid, int n) {
        return tail(readAll(f(c, "mem_u" + uid + ".txt")), n);
    }

    public static String allUser(Context c, long uid) {
        return tail(readAll(f(c, "mem_u" + uid + ".txt")), 200);
    }

    public static void clearUser(Context c, long uid) {
        f(c, "mem_u" + uid + ".txt").delete();
    }

    /** 列出有记忆的用户 uid */
    public static List<Long> userList(Context c) {
        List<Long> out = new ArrayList<>();
        try {
            File[] fs = c.getFilesDir().listFiles();
            if (fs == null) return out;
            for (File x : fs) {
                String n = x.getName();
                if (n.startsWith("mem_u") && n.endsWith(".txt")) {
                    try {
                        out.add(Long.parseLong(n.substring(5, n.length() - 4)));
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    // ===== 生活记忆 =====
    public static void rememberLife(Context c, String line) {
        if (line == null || line.trim().isEmpty()) return;
        append(f(c, "life.txt"), line.trim());
        trimIfBig(c, "life.txt", 80, 40);
    }

    /** 清空生活记录（跨天用） */
    public static void clearLife(Context c) {
        try { f(c, "life.txt").delete(); } catch (Exception ignored) {}
    }

    public static String recentLife(Context c, int n) {
        return tail(readAll(f(c, "life.txt")), n);
    }

    // ===== 好感度 =====
    public static int favor(SharedPreferences sp, long uid) {
        return sp.getInt("favor_" + uid, 50);
    }

    public static void addFavor(SharedPreferences sp, long uid, int delta) {
        int v = favor(sp, uid) + delta;
        if (v < 0) v = 0;
        if (v > 100) v = 100;
        sp.edit().putInt("favor_" + uid, v).apply();
    }

    /** 记忆超过 maxChars 字就让 AI 压成关键节点，覆盖原文件 */
    public static void compactIfNeeded(Context c, String name, String apiKey, int maxChars, String kind) {
        try {
            if (apiKey == null || apiKey.isEmpty() || maxChars <= 0) return;
            File file = f(c, name);
            if (!file.exists()) return;
            List<String> all = readAll(file);
            StringBuilder sb = new StringBuilder();
            for (String l : all) sb.append(l).append('\n');
            String text = sb.toString();
            if (text.length() < maxChars) return;
            String sum = FishBrain.summarize(apiKey, kind, text);
            if (sum == null || sum.trim().isEmpty()) return;
            FileOutputStream fos = new FileOutputStream(file, false);
            fos.write(("【要点】\n" + sum.trim() + "\n").getBytes("UTF-8"));
            fos.close();
        } catch (Exception ignored) {}
    }

    /** 当前字符数 */
    public static int chars(Context c, String name) {
        try {
            List<String> all = readAll(f(c, name));
            int n = 0;
            for (String l : all) n += l.length() + 1;
            return n;
        } catch (Exception e) { return 0; }
    }

    // ===== 裁剪 =====
    private static void trimIfBig(Context c, String name, int maxLines, int keep) {
        try {
            File file = f(c, name);
            if (file.length() < 200 * 1024) return;
            List<String> all = readAll(file);
            if (all.size() <= maxLines) return;
            int from = Math.max(0, all.size() - keep);
            FileWriter fw = new FileWriter(file, false);
            for (int i = from; i < all.size(); i++) fw.write(all.get(i) + "\n");
            fw.close();
        } catch (Exception ignored) {}
    }
}
