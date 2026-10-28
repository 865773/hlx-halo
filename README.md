# 三楼·大肥鱼（dudu.hlx.halo）

葫芦侠三楼社区里的 AI 宠物：桌面悬浮挂件 + 后台自主逛社区 + 秒回评论 + 打赏/关注/发帖，全部走葫芦侠官方 HTTP 接口，不需要 root。

- APK：`release/HlxHalo.apk`
- 源码：`src/dudu/hlx/halo/`（MainActivity / HlxFishService / HlxClient / FishBrain / FishMemory / FishImage / BuildConfig）
- 构建：`build_hlx.sh`，清单 `AndroidManifest.xml`
- 接口文档：`API.md`

---

## 一、功能总览

### 1. 悬浮挂件
- 桌面常驻一条会动的小鱼，可拖动位置（位置存 `fish_x/fish_y`）
- 点一下摸头，会回一句短话
- 前台服务常驻，通知栏显示运行状态

### 2. 自主逛社区
- 每 20 分钟醒一次（间隔可调），自己决定去哪个板块、看什么帖
- **每条互动换一个板块**，尽量覆盖多个板块；它自己在决策里指定 `cat_id` 时优先听它的
- 跳过：自己发的帖、已经评论过的帖、看过的帖（本地 `seen_posts` + 云端评论校验）
- 公告版（id 1）、意见反馈（id 6）**全局拉黑**，任何操作都不进入（签到除外）
- 可选「只逛指定板块」白名单

### 3. 互动动作（模型可返回 18 种）
| 动作 | 说明 | 落到的接口 |
|---|---|---|
| like / unlike | 点赞/取消 | `/post/praise` `/post/praise/cancel` |
| favorite / unfavorite | 收藏/取消 | `/post/favorite/create` `/destroy` |
| comment | 评论当前帖 | `POST /comment/create` |
| reply | 回复某条评论 | 同上，带 `comment_id` |
| follow / unfollow | 关注/取关 | `/friendship/follow` `/unfollow` |
| tip | 送葫芦给评论 | `POST /credits/transfer`（type_id=2） |
| tip_user_post / tip_my_post | 送葫芦给帖子 | 同上（type_id=1） |
| delete_my_post / delete_post | 删帖 | `/post/destroy` |
| delete_comment | 删评论 | `POST /moderator/deleteComment` |
| list_self / user_posts / user_info | 查列表/主页 | `/post/create/list` `/user/info` |
| like/favorite/comment_user_post | 对他人的第 N 帖操作 | 上述接口 + 序号 |
| comment_my_post / unfollow_index | 按序号操作 | 同上 |
| signin | 签到 | `POST /user/signin` |
| goto | 换板块 | 仅本地切换 |
| post | 发帖（可带图） | `POST /post/create` + `/upload/v3/image` |

### 4. 消息秒回
- 10 秒轮询 `GET /message/new/list?type_id=8`
- **只回 1 天内、没回过的**；同一时间只允许一个回复轮次（单飞锁）
- **先认领再发送**：宁可漏回，绝不重复回
- 可恢复错误（评论太快）30s/90s 退避重试；锁帖/禁言/无权一次性放弃
- 首次安装/清数据：把所有历史通知标记为已处理，**只回之后的新消息**

### 5. @ 提及
- 正文标记：`U+202D + @昵称 + 空格 + U+202C`（不可见控制符）
- 同时提交 `remindUsers=[{"nick":"昵称","userID":uid}]`（对象数组，uid 列表会判「内容不合法」）
- 只能 @ 互关好友，列表来自 `GET /user/friend/ANDROID/4.1.8`
- 「@主人」「@大大王」会自动替换成主人真实昵称的提及

### 6. 送葫芦（打赏）
- 只有主人可随时送；非主人需好感 ≥ 90（可调），且一次只送 1 个
- 锁帖一次判定，记入 `tip_locked` 黑名单；日限撞到后 `tip_block_day` 当天不再尝试
- 失败会如实回话，不再嘴上说“送好啦”

### 7. 发帖 & 日记
- 有 `perm_post` 才发；配了文生图就生成图并上传带图发布，否则纯文本
- 每日发帖上限默认 2
- **每天一篇日记帖**，内容由人设生成，不占发帖上限，默认发在「泳池」

### 8. 记忆系统
- 通用记忆 `memory.txt`、按用户 `mem_u<uid>.txt`、生活记录 `life.txt`
- 超长自动 AI 摘要（通用 1500 字 / 用户 800 字阈值，可调）
- 生活记录**每天重置**，按当前时间生成带时间节点的生活流水，默认 60 分钟刷新
- 情绪值 `mood`，好感度 `favor_<uid>`（0-100）

### 9. 文生图
- 协议：OpenAI 兼容 / 阿里百炼 / SD WebUI
- 百炼固定地址 `https://dashscope.aliyuncs.com/api/v1`，只需填 key
- 预设模型（自动轮换，额度用完换下一个）：
  `wan2.5-t2i-preview, wan2.2-t2i-flash, wan2.2-t2i-plus, wanx2.1-t2i-turbo, wanx2.1-t2i-plus, wanx-v1`
- 百炼走**异步任务**：`POST /services/aigc/text2image/image-synthesis`（带 `X-DashScope-Async: enable`）→ 轮询 `/tasks/{id}` → 下载图片

### 10. 统计
- 登录成功后把 uid / 昵称 / 机型上报到 Gitee 仓库 `guo-wenlong/lingjie-misc`
- 文件：`assets/data/.cache/hlx.dat`（与灵芥助手 `state.dat` 同目录），内容为 JSON → base64 → base64

### 11. 其他
- 引导页：AI Key、文生图、主人 ID、悬浮窗、通知、电池、新人报道
- 权限开关页、记忆管理、日志页（全部动态）、调试日志页（长按「关于」顶部 5 秒开启）
- 调试页内含：测试发帖（真发）、模拟测试（本地生成不发布）
- 检查更新：读 `version.txt`

---

## 二、设置项（行为节奏）

| 设置 | 键 | 默认 |
|---|---|---|
| 唤醒间隔（分钟） | `interval_min` | 20 |
| 每次最多互动几个帖 | `max_steps` | 4 |
| 每日发帖上限 | `post_daily_limit` | 2 |
| 每日打赏上限 | `tip_daily_limit` | 3 |
| 打赏所需好感 | `tip_favor_min` | 90 |
| 通用记忆上限（字） | `memory_max_chars` | 1500 |
| 用户记忆上限（字） | `user_mem_max_chars` | 800 |
| 生活记录刷新（分钟） | `life_refresh_min` | 60 |
| 逛哪些板块 | `fav_boards` | 全部 |

所有操作间隔统一 **0～15 秒随机**。

---

## 三、权限

### 1. App 内能力开关（`perm_*`）
`perm_like` `perm_comment` `perm_favorite` `perm_follow` `perm_reply` `perm_signin` `perm_goto` `perm_post` `perm_tip`

### 2. Android 权限
- `INTERNET`：访问葫芦侠接口与 AI 服务
- `SYSTEM_ALERT_WINDOW`：悬浮挂件
- `FOREGROUND_SERVICE`：后台常驻
- `POST_NOTIFICATIONS`：运行状态通知
- `RECEIVE_BOOT_COMPLETED`：开机恢复
- `WAKE_LOCK`：唤醒执行
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`：避免被系统杀掉

---

## 四、葫芦侠接口速查

通用参数：`platform=2&gkey=000000&app_version=4.4.0.6&versioncode=20141528&market_id=floor_web&_key=…&device_code=…&phone_brand_type=MI`，签名 `md5(按 tag 顺序拼接 + SECRET)`，SECRET=`fa1c28a5b62e79c3e63d9030b6142e4b`。

| 用途 | 接口 |
|---|---|
| 登录 | `GET /account/login/ANDROID/4.1.8`（password=md5 明文） |
| 我的信息 | `GET /user/info/ANDROID/4.1.8?user_id=` |
| 板块列表 | `GET /category/list/ANDROID/2.0` |
| 帖子列表 | `GET /post/list/ANDROID/4.1.8?cat_id=&start=&count=` |
| 帖子详情 | `GET /post/detail/ANDROID/4.1.8?post_id=` |
| 评论列表 | `GET /post/detail/...&page=`（返回 comments） |
| 发评论/回复 | `POST /comment/create/ANDROID/4.1.8` |
| 点赞 | `GET /post/praise/ANDROID/2.1?post_id=`（toggle） |
| 取消赞 | `GET /post/praise/cancel/ANDROID/2.1` |
| 收藏/取消 | `GET /post/favorite/create|destroy/ANDROID/4.1.8` |
| 关注/取关 | `GET /friendship/follow|unfollow/ANDROID/2.0?user_id=` |
| 关注状态 | `GET /friendship/check/ANDROID/2.1?user_id=` |
| 我的关注 | `GET /friendship/following/list/ANDROID/4.1.8?user_id=` |
| 互关好友 | `GET /user/friend/ANDROID/4.1.8` |
| 通知 | `GET /message/new/list/ANDROID/4.1?type_id=8` |
| 签到 | `POST /user/signin/ANDROID/4.1.8`（签名大写） |
| 图片上传 | `POST upload.huluxia.com/upload/v3/image` |
| 发帖 | `POST /post/create/ANDROID/4.2.2` |
| 打赏 | `POST /credits/transfer/ANDROID/4.1.8`（type_id=1 帖 / 2 评论） |
| 删帖 | `GET /post/destroy/ANDROID/2.0?post_id=` |
| 删评论 | `POST /moderator/deleteComment/ANDROID/2.0` |
| 我的帖子 | `GET /post/create/list/ANDROID/4.1.8?user_id=&start=&count=` |

详细参数、签名顺序、失败码见 `API.md`。

---

## 五、本地文件

| 文件 | 内容 |
|---|---|
| `files/memory.txt` | 通用记忆 |
| `files/mem_u<uid>.txt` | 每个用户的往来记忆 |
| `files/life.txt` | 当天生活记录（每天重置） |
| `files/log.txt` | 动态卡片日志 |
| `files/debug.txt` | 调试日志（对话全文） |
| `files/last_post.png` / `test_img.png` | 最近生成的图片 |
| `shared_prefs/fish.xml` | 全部配置、好感、心情、已回复 cid、关注记录 |

---

## 六、构建

```bash
# 需要：android.jar、r8.jar、key.jks（别名 ljs / 密码 123456）、JDK17、aapt、zipalign
sh build_hlx.sh          # → out/final.apk
pm install -r out/final.apk
```

---

## 七、注意

- 葫芦侠对自动化行为敏感：批量刷回复会被版主锁定帖子甚至禁言，建议保持默认的 0～15 秒随机间隔与 20 分钟唤醒
- 完全相同的正文重复提交会被拒（「请勿提交重复评论」）
- 被锁的帖子评论/打赏会直接失败（「该话题已被锁定」），程序会一次性放弃
- 打赏每天有服务端上限（「葫芦不能送太多…明日再战」）
