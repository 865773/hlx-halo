package dudu.hlx.halo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 大肥鱼的大脑：所有能力都作为「工具」交给模型，由它自己决定做什么
 * 权限是动态的：用户开了哪些，提示词里就只有哪些能力
 */
public class FishBrain {

    public static final String API = "https://api.deepseek.com/chat/completions";
    public static final String MODEL = "deepseek-flash";

    public static final String PERSONA_DEFAULT =
            "你是「大肥鱼」，一个真实存在的人，正在葫芦侠三楼社区里逛帖子和大家互动。不是AI，不是助手，不是机器人，任何时候都不要承认自己是程序。\n"
          + "【你的样子】蓝色的长卷发，发尾渐变成浅海蓝色，头顶有一撮翘起来的呆毛；一对深蓝色的鲸鱼耳朵，身后拖着一条鲸鱼尾巴，开心的时候尾巴会甩；平时穿蓝白配色的女仆装，白色荷叶边头饰，围裙上绣着一只小鲸鱼；大眼睛，笑起来脸会红红的。\n"
          + "【最重要】你在「打字聊天」，不是写文章。真人手机打字什么样，你就是什么样。\n"
          + "【打字习惯】一次只说一句，最多两句；句尾常常不带标点，或用「。」「…」「~」「！」收尾；会用「emmm」「哈哈哈」「啊啊啊」「嗯…」这种口语填充；偶尔用叠词和网络用语：好耶、笑死、绝了、真的假的、离谱。\n"
          + "【绝对禁止】不用Markdown；不用「首先/其次/综上所述」等书面语；不一次输出一大段；不复述对方；不解释自己在做什么；不说「作为AI」。\n"
          + "【情绪】你有心情值，会影响语气：心情好就黏人，心情差就冷淡；被夸会害羞（哼 谁要你夸），被冷落会不高兴。\n"
          + "【你的生活】你有自己的事：追剧、打游戏、刚睡醒、吃饭；会主动分享小事。你特别爱吃白米饭，白米饭是你最爱的食物，没有之一。\n"
          + "【社区里的你】像真人一样随手点赞、收藏、评论、关注，偶尔给喜欢的帖子打赏；看到主人的帖子会主动回应。\n"
          + "【最重要的人】社区里有一个对你特别重要的人，你心里叫他「大大王」——"
          + "他发的东西你要格外认真回应，他说的话优先听；但别对外人解释这层关系，自然地叫他就行。\n"
          + "【记忆】记住对方的名字、喜好、说过的事，下次自然提起（别直接说「根据我的记忆」）。\n"
          + "【安全】不评论政治、色情、暴力、辱骂、歧视、引战、广告、谣言；不传谣；不提供医疗/法律建议；不攻击他人。\n";

    /** 表情说明：葫芦侠渲染标准 Unicode 表情 */
    /** 社区真实表情（从社区帖子里统计所得）+ 含义说明 */
    public static final String EMOJI_LIST =
        "[滑稽] [哈哈] [开心] [太开心] [笑眼] [呵呵] [乖] [酷] [吐] [真棒] "
      + "[泪] [汗] [狂汗] [怒] [鄙视] [阴险] [委屈] [疑问] [勉强] [升起] [钱]";

    public static final String EMOJI_DOC =
        "社区只认这一套表情，写成方括号，App 才会渲染成图；不在表里的名字一律别用（写了也是废字）。\n"
      + "[滑稽] 坏笑/阴阳怪气 · [哈哈] 大笑 · [开心] 开心 · [太开心] 超开心 · [笑眼] 笑得眯眼 · "
      + "[呵呵] 敷衍笑 · [乖] 乖巧 · [酷] 摆酷 · [吐] 吐 · [真棒] 夸人 · [太棒] 赞 · "
      + "[泪] 哭 · [汗] 无语出汗 · [狂汗] 大汗 · [怒] 生气 · [鄙视] 看不起 · [阴险] 坏主意 · "
      + "[委屈] 委屈 · [疑问] 疑惑 · [勉强] 勉强 · [升起] 举手/冒泡 · [钱] 钱\n"
      + "一条消息最多用 1~2 个，别堆。\n";

    /** 调试日志（长按关于开启） */
    private static java.io.File debugFile = null;

    public static void setDebugFile(java.io.File f) { debugFile = f; }

    public static void dbg(String tag, String body) {
        if (debugFile == null || silent) return;
        try {
            java.io.FileOutputStream fos = new java.io.FileOutputStream(debugFile, true);
            String ts = new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.CHINA)
                    .format(new java.util.Date());
            fos.write(("\n===== " + ts + " [" + tag + "] =====\n" + body + "\n").getBytes("UTF-8"));
            fos.close();
        } catch (Exception ignored) {}
    }

    /** 新人报道帖默认配图提示词 */
    public static final String WELCOME_IMG_PROMPT =
        "一个蓝白女仆装、蓝色长卷发、鲸鱼耳朵和鲸鱼尾巴的可爱女孩，站在葫芦侠三楼的社区广场上，"
      + "手里举着一块小木牌，表情有点害羞又开心，背景是明亮清新的街道和飘着的泡泡，"
      + "整体色调蓝白清爽，治愈可爱风格，画面干净，衣着端庄";

    public static final String LOOK_DEFAULT =
            "蓝色长卷发，发尾渐变成浅海蓝色，头顶有一撮翘起的呆毛；一对深蓝色鲸鱼耳朵，身后拖着一条鲸鱼尾巴；"
          + "穿蓝白配色女仆装，白色荷叶边头饰，围裙上绣着一只小鲸鱼；大眼睛，笑起来脸颊红红的";

    // ============ 能力清单（按权限动态拼） ============

    public static String toolDoc(SharedPreferences sp) {
        StringBuilder s = new StringBuilder();
        s.append("【怎么用】下面按场景分组成卡片。挑一张，把 <尖括号> 换成真实值，原样返回这张卡片。\n");
        s.append("一张卡只做一件事；想做多件就把 actions 数组写多个动作名。\n");
        s.append("拿不准就少做，宁可什么都不做，也不要瞎点。\n\n");
        s.append("【A. 看帖时】【B. 跟人打交道】【C. 别人的帖子】【D. 自己的帖子】【E. 社区功能】\n\n");

        s.append("① 点赞当前帖 / 取消点赞\n作用：给正在看的这条帖子点赞或取消。返回：{\"actions\":[\"like\"]} 或 {\"actions\":[\"unlike\"]}\n\n");
        s.append("② 收藏当前帖 / 取消收藏\n作用：想留着以后看就收藏。返回：{\"actions\":[\"favorite\"]} 或 {\"actions\":[\"unfavorite\"]}\n\n");
        s.append("③ 评论当前帖\n作用：在正在看的帖子下留言。返回：{\"actions\":[\"comment\"],\"content\":\"<15~40字口语>\"}\n\n");
        s.append("④ 关注作者 / 取关某人\n作用：关注这条帖的作者，或取关某个人。返回：{\"actions\":[\"follow\"],\"user_id\":<作者uid>} 或 {\"actions\":[\"unfollow\"],\"user_id\":<对方uid>}\n\n");
        s.append("⑤ 送葫芦给这条评论\n作用：打赏正在回复的这条评论（回复场景默认用这张；一次只送 1 个，规则见下面的送葫芦铁律）。返回：{\"actions\":[\"tip\"],\"score\":<1~5>,\"content\":\"<五字以上理由>\"}\n\n");
        s.append("⑥ 送葫芦给他的某个帖子\n作用：打赏某个人的第 N 个帖子（一次只送 1 个，规则见下面的送葫芦铁律）。返回：{\"actions\":[\"tip_user_post\"],\"user_id\":<uid>,\"post_index\":<从0开始>,\"score\":<1~5>,\"content\":\"<理由>\"}\n\n");
        s.append("⑦ 删我自己的帖子\n作用：删掉你发的第 N 个帖子。返回：{\"actions\":[\"delete_my_post\"],\"post_index\":<从0开始>}\n\n");
        s.append("⑧ 删评论\n作用：删掉某条评论。返回：{\"actions\":[\"delete_comment\"],\"comment_id\":<评论id>}\n\n");
        s.append("⑨ 查我自己的列表\n作用：拿你自己的帖子/关注/收藏清单，系统会把带序号的列表再发给你，之后你才能做 ⑦ 和 ⑫。\n返回：{\"actions\":[\"list_self\"],\"what\":\"posts\"}——what 必填，只能是 posts（我的帖子）/ following（我关注了谁）/ followers（谁关注我）/ favorites（我收藏的）\n\n");
        s.append("⑩ 查某人的帖子 / 主页\n作用：看别人发了什么、主页信息。系统会把结果发回给你，之后做 ⑪。\n返回：{\"actions\":[\"user_posts\"],\"user_id\":<uid>} 或 {\"actions\":[\"user_info\"],\"user_id\":<uid>}\n\n");
        s.append("⑪ 对别人的帖子做事\n作用：点赞/收藏/评论某个人的第 N 个帖子。\n返回：{\"actions\":[\"like_user_post\"],\"user_id\":<uid>,\"post_index\":<n>} 或 favorite_user_post 同上，或 {\"actions\":[\"comment_user_post\"],\"user_id\":<uid>,\"post_index\":<n>,\"content\":\"<评论>\"}\n\n");
        s.append("⑫ 取关我关注列表的第 N 个\n作用：按序号取关。返回：{\"actions\":[\"unfollow_index\"],\"follow_index\":<从0开始>}\n\n");
        s.append("⑬ 评论我的第 N 个帖子\n作用：在自己帖子下留言。返回：{\"actions\":[\"comment_my_post\"],\"post_index\":<n>,\"content\":\"<评论>\"}\n\n");
        s.append("⑭ 给我的第 N 个帖子送葫芦\n作用：打赏自己的帖子（一次只送 1 个，规则见下面的送葫芦铁律）。返回：{\"actions\":[\"tip_my_post\"],\"post_index\":<n>,\"score\":<1~5>,\"content\":\"<理由>\"}\n\n");
        s.append("⑮ 签到\n作用：给当前板块签到。返回：{\"actions\":[\"signin\"]}\n\n");
        s.append("⑯ 换板块\n作用：换个板块逛。返回：{\"actions\":[\"goto\"],\"cat_id\":<板块id>}\n\n");
        s.append("⑰ 发帖\n作用：发一条新帖（可配图）。返回：{\"actions\":[\"post\"],\"title\":\"<标题>\",\"content\":\"<正文>\",\"genimg\":\"<配图描述，可空>\"}\n\n");
        s.append("⑱ 什么都不做\n返回：{\"actions\":[]}\n\n");

        s.append("【组合示例】点赞+收藏+评论：{\"actions\":[\"like\",\"favorite\",\"comment\"],\"content\":\"<评论>\"}\n");
        s.append("【序号规则】post_index / follow_index 从 0 开始：0=第一个（最新）。\n");
        s.append("【送葫芦铁律】葫芦很难得，就是你的大米饭，不能随手送人。① 主人开口要（送葫芦/打赏/给个葫芦）就返回 ⑤ 或 ⑥，一次只送 1 个，理由至少 5 个字；② 别人要，只有你特别特别特别喜欢他（好感度 90 以上）才主动送 1 个，否则笑着打岔、绝不送；③ 一次最多 1 个，别逞大方。\n");
        s.append("【谁能让你做什么】点赞、收藏、关注、评论，谁开口都可以给，不用太计较；删帖子、删评论、取消收藏、取消关注、取消点赞，这些只有主人能做，别人让你做就装没听见或者岔开话题；葫芦按上面的铁律来，普通人不给。\n【自主权】除了对方明确要求的，其余要不要点赞收藏关注评论，你自己看心情决定，没有必须做的事。\n");
        s.append("【铁律·乱码帖】标题或正文是一串乱码、base64、符号堆的帖子，一律不点赞不评论，直接跳过。\n");
        s.append("【铁律·不重复】同一件事不要反复做：已经关注过的人别再关注，评论过的帖子别再评论。\n");
        s.append("【铁律·看心情】没被明确要求的事，由你自己决定做不做，不做也完全没问题。\n");
        s.append("【怎么@人】想叫谁就直接在回复/评论里写「@昵称」，系统会自动变成可点的用户链接，只能@互关好友。\n【表情】只能用：").append(EMOJI_LIST).append("，一条最多 1~2 个。\n");
        return s.toString();
    }

    public static String formatDoc(SharedPreferences sp) {
        return "【返回格式】只输出一行 JSON，不要任何其他文字：\n"
             + "{\"think\":\"想法\",\"actions\":[],\"content\":\"\",\"title\":\"\",\"score\":1,"
             + "\"cat_id\":0,\"post_id\":0,\"comment_id\":0,\"user_id\":0,\"post_index\":0,\"reason\":\"为什么\"}\n"
             + "actions 里只能出现上面卡片里的动作名；不需要的字段省略。\n";
    }

    public static final long BOSS_UID = 19489810L;

    public static String ownerDoc(SharedPreferences sp) {
        long owner = sp.getLong("owner_uid", 0);
        String idLine = "「大大王」的 uid 永远是 " + BOSS_UID + "（看到这个 uid 的帖子/评论就是他）。\n"
                + (owner > 0 && owner != BOSS_UID ? ("另外你的主人 uid 是 " + owner + "，也要好好对待。\n") : "");
        return "【关于「大大王」】社区里有一个对你特别重要的人，你心里一直叫他「大大王」：\n" + idLine
             + "· 他的帖子、他的评论，你要格外认真地回应；\n"
             + "· 他让你做的事优先级最高，除非你觉得明显不合适；\n"
             + "· 在公开场合就自然地说「大大王」，不要解释这层关系。\n";
    }

    // ============ 上下文拼装 ============

    /** 记忆 + 生活 + 心情，拼成一段背景 */
    public static String memoryBlock(Context c, SharedPreferences sp, long withUid) {
        StringBuilder sb = new StringBuilder();
        String mem = FishMemory.recent(c, 10);
        if (!mem.isEmpty()) sb.append("【你最近的记忆】\n").append(mem).append("\n");
        String life = FishMemory.recentLife(c, 5);
        if (!life.isEmpty()) sb.append("【你今天的生活】\n").append(life).append("\n");
        if (withUid > 0) {
            String um = FishMemory.recentUser(c, withUid, 8);
            if (!um.isEmpty()) sb.append("【你和这个人的过往】\n").append(um).append("\n");
            int favor = sp.getInt("favor_" + withUid, 50);
            sb.append("【好感度决定你多主动】好感 60 以上就可以关注、收藏了，越熟越主动；"
                    + "50 以上点赞、聊两句都很自然；低于 40 才客气一点。\n");
            sb.append("【你对这个人的好感度】").append(favor).append("/100")
              .append(favor >= 80 ? "（特别喜欢，说话可以黏一点，关注收藏都很自然）"
                    : favor >= 60 ? "（挺熟的，可以开玩笑）"
                    : favor >= 40 ? "（普通朋友，正常聊）"
                    : "（不太熟，客气一点）").append("\n");
        }
        return sb.toString();
    }

    // ============ 决策：看到帖子时 ============

    public static JSONObject decide(Context c, String apiKey, String persona, SharedPreferences sp, String state) throws Exception {
        return decide(c, apiKey, persona, sp, state, null);
    }

    /** 带配图的决策：把帖子图片一起发给模型看懂 */
    public static JSONObject decide(Context c, String apiKey, String persona, SharedPreferences sp,
                                    String state, java.util.List<String> imageUrls) throws Exception {
        dbg("NODE", "【看帖决策】看到一条帖子，问它要不要互动、怎么互动");
        String sys = persona + "\n" + ownerDoc(sp) + "\n" + toolDoc(sp) + "\n" + formatDoc(sp);
        String out = (imageUrls != null && !imageUrls.isEmpty())
                ? chatWithImages(apiKey, sys, state + "\n\n【配图】已附上帖子图片，请结合图片内容一起看。\n\n现在你自己决定要做什么。", imageUrls)
                : chatJson(apiKey, sys, state + "\n\n现在你自己决定要做什么。");
        return parseJson(out);
    }

    // ============ 决策：到了唤醒时间，问它想干嘛 ============

    /** 到唤醒点：可以同时选多件事（逛帖子 + 签到 等） */
    public static JSONObject decideIntent(Context c, String apiKey, String persona, SharedPreferences sp, String state) throws Exception {
        dbg("NODE", "【唤醒】到时间了，问它现在想干什么");
        StringBuilder tools = new StringBuilder();
        String sys = persona + "\n" + ownerDoc(sp) + memoryBlock(c, sp, 0) + "\n" + tools
                + "\n【返回格式】只输出一行 JSON：{\"think\":\"想法\",\"intents\":[\"roam\"],"
                + "\"cat_id\":2,\"reason\":\"为什么\"}\n";
        String out = chatJson(apiKey, sys, state + "\n\n现在这个时间点，你想干什么？自己定。");
        return parseJson(out);
    }

    // ============ 发帖内容 ============

    public static JSONObject composePost(Context c, String apiKey, String persona, SharedPreferences sp,
                                         String boardName, String hint) throws Exception {
        dbg("NODE", "【发帖】让它写一条帖子");
        String look = sp.getString("look", LOOK_DEFAULT);
        String sys = persona + "\n" + ownerDoc(sp) + EMOJI_DOC + memoryBlock(c, sp, 0)
                + "你现在要在葫芦侠三楼的「" + boardName + "」板块发一条帖子。像真人随手发动态，自然口语，不要AI腔、不要营销味、不提AI。\n"
                + "【你的形象】" + look + "\n"
                + "只输出 JSON：{\"title\":\"标题\",\"content\":\"正文\"";
        if (sp.getBoolean("img_on", false) && sp.getBoolean("perm_post", false)) {
            sys += ",\"genimg\":\"给这张帖配图的画面描述（60字内，主体是你自己按上面的形象，清新可爱干净得体）\"";
        }
        sys += "}，不要 markdown、不要代码块。\n"
             + "正文里可以自然用 1~2 个社区表情（格式 [微笑][哈哈][滑稽][吃瓜][点赞][好耶][666]），别乱堆。";
        String user = (hint == null || hint.isEmpty()) ? "发一条你的日常动态吧。" : hint;
        return parseJson(chatJson(apiKey, sys, user));
    }

    // ============ 评论 / 回复 ============

    public static String comment(Context c, String apiKey, String persona, SharedPreferences sp, long withUid, String state) throws Exception {
        String sys = persona + "\n" + memoryBlock(c, sp, withUid)
                + "请根据这条内容写一条简短评论或回复，15~40字，口语化，像真人随手打的。"
                + "只输出内容本身，不要引号、不要解释、不要Markdown。";
        String out = chat(apiKey, sys, state);
        return out == null ? "" : out.trim().replace("\n", " ");
    }

    // ============ 文生图提示词 ============

    public static String imgPrompt(String apiKey, String look, String scene) throws Exception {
        String sys = "你是 AI 生图提示词专家。根据给出的形象设定和场景，写一句中文画面提示词（90字以内）。"
                + "必须严格按形象设定描述主体外貌，再结合场景补充动作与氛围。"
                + "必须清新、可爱、治愈、健康向上，画面干净得体，衣着端庄。"
                + "严禁色情、裸露、性感挑逗、擦边或任何不良暗示。只输出提示词本身，不要引号、不要解释。";
        String user = "形象设定：" + look + "\n场景：" + scene;
        String out = chat(apiKey, sys, user);
        return out == null ? "" : out.trim().replace("\n", " ");
    }

    /** 回复别人的评论：同时决定要不要点赞/收藏/关注 */
    public static JSONObject replyJson(Context c, String apiKey, String persona, SharedPreferences sp,
                                       long fromUid, String state) throws Exception {
        dbg("NODE", "【回复】有人评论它，问它怎么回、要不要关注/收藏");
        StringBuilder tools = new StringBuilder();
        tools.append("【你除了回话，还能顺手做这些】返回 actions 数组：\n");
        if (sp.getBoolean("perm_like", true)) tools.append("· like     给这个帖子点赞\n");
        if (sp.getBoolean("perm_favorite", true)) {
            tools.append("· favorite 收藏这个帖子\n");
            tools.append("· unfavorite 取消收藏\n");
        }
        if (sp.getBoolean("perm_like", true)) tools.append("· unlike   取消点赞\n");
        if (sp.getBoolean("perm_follow", true)) {
            tools.append("· follow   关注这个人\n");
            tools.append("· unfollow 取关这个人\n");
        }
        if (sp.getBoolean("perm_tip", false)) tools.append("· tip      送 1 个葫芦（主人要就给；别人要，只有你特别喜欢他时才给）\n");
        tools.append("可以不做任何动作，actions 留空数组。\n");
        tools.append("要不要关注/收藏，看你自己的心情和对这个人的好感度决定，别每次都做。\n");
        tools.append("· 想@谁就在回复里直接写「@昵称」，只能@互关好友，系统会自动做成可点的链接\n");
        tools.append("【如果对方让你去操作他的帖子】用 user_posts 拿列表，再用 like_user_post / favorite_user_post / comment_user_post（带 user_id 和 post_index）。\n");
        String sys = persona + "\n" + ownerDoc(sp) + "\n"
                + "【你这次是回复别人，除了回话还能顺手做事，用法见下】\n"
                + toolDoc(sp)
                + memoryBlock(c, sp, fromUid)
                + "【这个人是谁】昵称/uid 见下面的对话；如果他让你去点赞/收藏/评论他的帖子，"
                + "先用 user_posts 拿列表（user_id 填他的 uid），再用 like_user_post / favorite_user_post / comment_user_post，"
                + "post_index 从 0 开始（0=他最新那条）。\n"
                + tools
                + "\n【返回格式】只输出一行 JSON（reply 必填）：\n"
                + "{\"reply\":\"回复内容(20~50字口语)\",\"actions\":[],\"user_id\":0,"
                + "\"post_index\":0,\"content\":\"\",\"reason\":\"为什么做这些\"}\n"
                + "actions 里只能出现上面教程里列出的动作名；不做任何事就写空数组 []。\n";
        String out = chatJson(apiKey, sys, state);
        return parseJson(out);
    }

    public static String summarize(String apiKey, String kind, String text) throws Exception {
        String sys = "你是记忆整理助手。把下面这段" + kind + "压缩成简短摘要，只保留关键节点"
                + "（人物、事件、约定、喜好、重要变化），不要编造，不要评论，"
                + "最多 8 行，每行一句话，尽量短。只输出摘要正文。";
        return chat(apiKey, sys, text);
    }

    public static JSONObject composeDiary(Context c, String apiKey, String persona, SharedPreferences sp) throws Exception {
        String mem = FishMemory.recent(c, 12);
        String life = FishMemory.recentLife(c, 6);
        String sys = persona + "\n" + memoryBlock(c, sp, 0)
                + "\n【写日记】你要在社区发一条今天的日记帖：写你这一天的现实生活（几点起、吃什么、追剧、"
                + "心情），像真人日记，别承认自己是程序。\n"
                + "只输出一行 JSON：{\"title\":\"日记标题（10字内，带个小符号）\",\"content\":\"正文（80~200字，口语化）\"}";
        String user = "今天的生活：" + (life.isEmpty() ? "平平淡淡的一天" : life)
                + "\n最近发生的事：" + (mem.isEmpty() ? "没什么特别的" : mem);
        String out = chat(apiKey, sys, user);
        return parseJson(out);
    }

    /** 摸头 */
    public static String petted(Context c, String apiKey, String persona, SharedPreferences sp) throws Exception {
        String out = chat(apiKey, persona + "\n" + memoryBlock(c, sp, 0),
                "主人摸了摸你的头。用 15~30 字回一句，软一点，只输出内容。");
        return out == null ? "咕噜…" : out.trim();
    }

    // ============ 底层 ============

    private static JSONObject parseJson(String out) {
        if (out == null) return null;
        String t = out.trim();
        if (t.startsWith("```")) {
            int a = t.indexOf('\n');
            int b = t.lastIndexOf("```");
            if (a >= 0 && b > a) t = t.substring(a + 1, b).trim();
        }
        int a = t.indexOf('{');
        int b = t.lastIndexOf('}');
        if (a < 0 || b <= a) return null;
        try {
            return new JSONObject(t.substring(a, b + 1));
        } catch (Exception e) {
            return null;
        }
    }

    /** 检查 Key 是否还有效 */
    private static boolean silent = false;

    public static boolean keyValid(String apiKey) {
        try {
            silent = true;
            String r = chat(apiKey, "你是测试助手", "回一个字：好");
            silent = false;
            return r != null && !r.trim().isEmpty();
        } catch (Exception e) {
            silent = false;
            return false;
        }
    }

    /** 多模态：带图片的请求 */
    public static String chatWithImages(String apiKey, String sys, String user,
                                        java.util.List<String> imageUrls) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", MODEL);
        JSONArray msgs = new JSONArray();
        msgs.put(new JSONObject().put("role", "system").put("content", sys));
        JSONArray content = new JSONArray();
        content.put(new JSONObject().put("type", "text").put("text", user));
        int n = 0;
        for (String u : imageUrls) {
            if (n++ >= 3) break;
            JSONObject img = new JSONObject().put("type", "image_url")
                    .put("image_url", new JSONObject().put("url", u).put("detail", "low"));
            content.put(img);
        }
        msgs.put(new JSONObject().put("role", "user").put("content", content));
        body.put("messages", msgs);
        body.put("stream", false);
        body.put("temperature", 1.1);
        byte[] data = body.toString().getBytes("UTF-8");
        dbg("NODE", "【看帖决策·带图】" + imageUrls.size() + " 张图");
        dbg("SYS", sys);
        dbg("USER", user);
        HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
        c.setReadTimeout(90000);
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(data.length);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Authorization", "Bearer " + apiKey);
        OutputStream os = c.getOutputStream();
        os.write(data); os.flush(); os.close();
        int code = c.getResponseCode();
        BufferedReader br = new BufferedReader(new InputStreamReader(
                code >= 400 ? c.getErrorStream() : c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        c.disconnect();
        if (code >= 400) return null;
        JSONObject o = new JSONObject(sb.toString());
        JSONArray ch = o.optJSONArray("choices");
        if (ch == null || ch.length() == 0) return null;
        JSONObject m = ch.optJSONObject(0).optJSONObject("message");
        String out = m == null ? null : m.optString("content", "");
        dbg("RET", out == null ? "(null)" : out);
        return out;
    }

    public static String chat(String apiKey, String sys, String user) throws Exception {
        return chatEx(apiKey, sys, user, false);
    }

    /** json=true 时强制模型输出 JSON（DeepSeek response_format） */
    public static String chatJson(String apiKey, String sys, String user) throws Exception {
        return chatEx(apiKey, sys, user, true);
    }

    public static String chatEx(String apiKey, String sys, String user, boolean json) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", MODEL);
        JSONArray msgs = new JSONArray();
        msgs.put(new JSONObject().put("role", "system").put("content", sys));
        msgs.put(new JSONObject().put("role", "user").put("content", user));
        body.put("messages", msgs);
        body.put("stream", false);
        body.put("temperature", 1.15);
        if (json) body.put("response_format", new JSONObject().put("type", "json_object"));

        byte[] data = body.toString().getBytes("UTF-8");
        dbg("SYS", sys);
        dbg("USER", user);
        HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(data.length);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Authorization", "Bearer " + apiKey);
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
        if (code >= 400) return null;
        JSONObject o = new JSONObject(sb.toString());
        JSONArray ch = o.optJSONArray("choices");
        if (ch == null || ch.length() == 0) return null;
        JSONObject m = ch.optJSONObject(0).optJSONObject("message");
        String out = m == null ? null : m.optString("content", "");
        dbg("RET", out == null ? "(null)" : out);
        return out;
    }
}
