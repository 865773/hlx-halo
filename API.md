# 葫芦侠三楼 API 全接口测试报告

> 测试时间：2026-10-28
> 测试账号：`18057887037`（uid `19190925`，昵称「吊打三楼圈钱狗」，Lv.5）
> 测试方式：纯 HTTP 直连 `floor.huluxia.com`，全部真实请求真实返回
> 图例：✅ 实测通过　⚠️ 可用但有条件　❌ 未通过/不存在

---

## 零、通用规则

```
BASE      = http://floor.huluxia.com
UPLOAD    = http://upload.huluxia.com
SECRET    = fa1c28a5b62e79c3e63d9030b6142e4b
APP_VER   = 4.4.0.6
VER_CODE  = 20141528
MARKET_ID = floor_web
PLATFORM  = 2
GKEY      = 000000
DEVICE_CODE = [d]5125c3c6-f456-4c6b-81cf-9bc467522d61
UA = okhttp/3.8.1
```

**通用参数**（几乎每个接口都要）：
```
platform=2&gkey=000000&app_version=4.4.0.6&versioncode=20141528&market_id=floor_web
&_key=<KEY>&device_code=%5Bd%5D<uuid>&phone_brand_type=MI
```

**签名算法**（普通接口）：
```
raw  = 按 tags 顺序把 "参数名+参数值" 直接拼接
sign = md5(raw + SECRET)        # 小写
```
⚠️ 签名用的 `device_code` 是**原始值 `[d]xxx`**，URL 里才用编码值 `%5Bd%5Dxxx`。
⚠️ 值本身也要参与签名（例如 `images` 传 `[]`，签名里就是 `images[]`）。

---

## 一、登录 ✅

```
GET /account/login/ANDROID/4.1.8
```

| 参数 | 值 | 说明 |
|---|---|---|
| `account` | 手机号 | |
| `password` | **md5(明文) 小写** | 传明文会报「账号或密码错误」 |
| `login_type` | `2` | 传 1 报「参数不能为空」 |
| `voice_code` | 空 | 但必须参与签名 |
| `device_code` | URL 编码值 | 签名内用原始值 |
| `sign` | md5(...) | 见下 |

签名：
```
raw  = "account" + 手机号 + "device_code" + [d]xxx + "password" + md5密码 + "voice_code"
sign = md5(raw + SECRET)
```

**成功返回**（每次登录 `_key` 都会变）：
```json
{"_key":"A89938452F62F3D2...","user":{"userID":19190925,"nick":"吊打三楼圈钱狗","level":5,...},"status":1}
```

**失败对照表**

| 现象 | 原因 |
|---|---|
| `code 109` + qqinfo | 用了 iOS 接口 `/account/login/IOS/1.0` |
| `code 104 登录失败,请重新登录` | 用了 `/ANDROID/4.2.6` 或 sign 里 device_code 写成编码值 |
| `code 101 参数不能为空` | `login_type=1` |
| `code 1102 账号或密码错误` | password 传了明文 |

---

## 二、账号信息 ✅

| 接口 | 结果 | 返回要点 |
|---|---|---|
| `GET /account/security/info/ANDROID/4.2.6` | ✅ status=1 | `hasPassword` `hasBindPhone` `phone`(脱敏) `hasThirdBind` |
| `GET /user/status/ANDROID/2.1` | ✅ | `{"state":4,"status":1}` |
| `GET /user/info/ANDROID/4.1.8?user_id=<uid>` | ✅ | 自己：`nick=吊打三楼圈钱狗` `level=5` |
| `GET /user/info/ANDROID/4.1.8?user_id=14395308` | ✅ | 他人：`nick=晚安｡` |

---

## 三、板块 ✅

| 接口 | 结果 | 说明 |
|---|---|---|
| `GET /category/list/ANDROID/2.0` | ✅ 35 个板块 | 返回 categories[]，含 categoryID/title/postCount |
| `GET /category/forum/list/ANDROID/2.0` | ✅ | 返回 categoryforum[]（含图标、描述） |
| `GET /category/forum/list/all/ANDROID/2.0?fum_id=1` | ⚠️ | 返回空数组，需正确 fum_id |
| `GET /category/detail/ANDROID/2.0?cat_id=43` | ✅ | **参数名是 `cat_id`**；用 `category_id` 会返回 code 100「出错了」 |

实测板块：`2`=泳池，`43`=实用软件，`94`=三楼活动，`119`=爱国爱党，`81`=手机美化，`16`=玩机广场

---

## 四、帖子 ✅（读）

| 接口 | 结果 | 参数 / 返回 |
|---|---|---|
| `GET /post/list/ANDROID/4.1.8` | ✅ | `cat_id` `tag_id`(0=全部) `sort_by`(0默认/1热门) `start` `count`；返回 posts[] |
| `GET /post/detail/ANDROID/4.1.8?post_id=` | ✅ | `post.detail` 是 `<text>…</text>` 富文本；含 identityTitle/tagid/hit/commentCount |
| `GET /post/search/ANDROID/2.1` | ✅ | `keyword` `start` `count`；2.0、4.1.8 也可用，**2.2 返回 404** |
| `GET /post/create/list/ANDROID/4.1.8?user_id=` | ✅ | 自己 9 帖 / 指定 uid 20 帖；**无 status 字段属正常结构** |

帖子字段里 `tagid` 是真实 tag（如 4301、4305），`tag_id=0` 表示全部。

---

## 五、评论 ✅

| 接口 | 结果 |
|---|---|
| `GET /comment/create/list/ANDROID/4.1.8?post_id=&page=&pageSize=` | ✅ 评论列表 |
| `POST /comment/create/ANDROID/4.1.8` | ✅ **`{"msg":"发布成功","commentID":0,"code":200,"status":1}`** |

评论签名 tags 顺序：`_key, comment_id, device_code, images, post_id, text`
```
raw  = "_key"+KEY+"comment_id"+cid+"device_code"+DEV_RAW+"images"+IMGS+"post_id"+pid+"text"+TEXT
sign = md5(raw + SECRET)   # 小写
```
Body：`post_id` `comment_id`（回复填对方 cid，0=新评论）`text` `patcha` `images` `remindUsers` `sign`

---

## 六、点赞 ✅（toggle）

| 接口 | 结果 |
|---|---|
| `GET /post/praise/ANDROID/2.1?post_id=` | ✅ 返回 `praise` 0/1（当前赞状态），再调用一次即取消 |
| `GET /post/praise/check/ANDROID/2.1?post_id=` | ✅ 返回 `isPraise` 0/1 |

---

## 七、收藏 ✅

| 接口 | 结果 | 备注 |
|---|---|---|
| `GET /post/favorite/create/ANDROID/4.1.8?post_id=` | ✅ status=1 | |
| `GET /post/favorite/destroy/ANDROID/4.1.8?post_id=` | ✅ status=1 | |
| `GET /post/favorite/check/ANDROID/2.0?post_id=` | ✅ | `{"isFavorite":1,"status":1}` |
| `GET /post/favorite/list/ANDROID/4.1.8` | ⚠️ | **必须带 `user_id`**，否则永远返回空数组 |
| `GET /favorite/group/list/ANDROID/4.1.8?user_id=` | ✅ | 用户不存在？→ 就是因为缺 `user_id`；带后返回默认收藏夹 count=12 |

---

## 八、关注 / 粉丝 ✅

| 接口 | 结果 | 备注 |
|---|---|---|
| `GET /friendship/follow/ANDROID/2.0?user_id=` | ✅ status=1 | **toggle**：重复调会取关 |
| `GET /friendship/unfollow/ANDROID/2.0?user_id=` | ✅ status=1 | 明确取关 |
| `GET /friendship/check/ANDROID/2.1?user_id=` | ✅ | `friendship`：**0=未关注 1=已关注 3=互相关注** |
| `GET /friendship/following/list/ANDROID/4.1.8?user_id=&start=&count=` | ✅ | 关注列表（自己/他人均可） |
| `GET /friendship/follower/list/ANDROID/4.1.8?user_id=&start=&count=` | ✅ | 粉丝列表（自己/他人均可） |

实测闭环：`friendship=1` → unfollow → `3` → follow → `1`。

---

## 九、消息通知 ✅

| 接口 | 结果 |
|---|---|
| `GET /message/new/list/ANDROID/4.1?type_id=8&start=&count=` | ✅ 评论通知，返回 datas[].content.{commentID,text,postID,userID} |
| `GET /message/new/list/ANDROID/4.1?type_id=0` | ✅ 空列表 |

`type_id=8` = 评论/回复通知（大肥鱼回复要用这个）。

---

## 十、签到 ✅

**执行签到**
```
POST /user/signin/ANDROID/4.1.8?<通用参数>&cat_id=<板块>&time=<秒级时间戳>
body: sign = md5("cat_id"+cid+"time"+ts+SECRET)   # ⚠️ 大写
```
实测成功：
```json
{"continueDays":1,"experienceVal":5,"signin":1,"isFirstSignToday":1,"status":1}
```

**签到详情** `GET /user/signin/detail/ANDROID/4.0` ✅
返回 `lastYear/thisYear/continueDays/experienceVal/hasMiss` 以及 `thisDay[]`（当月每天签到状态数组）。

**签到记录**
- `GET /sgin/list/ANDROID/2.2` → ❌ 104「版块不存在!」
- `GET /user/signin/list/ANDROID/2.2` → ❌ 404
- `GET /user/signin/complete/ANDROID/4.0` → ⚠️ 需要 `year` 参数（code 101）

---

## 十一、图片上传 ✅（重要，**抓包实测版本**）

```
POST http://upload.huluxia.com/upload/v5/image     ← 实测是 v5，不是 v3
Content-Type: multipart/form-data
```

**抓包得到的真实参数顺序**（App 4.4.0.6 实际发的）：
```
?platform=2&gkey=000000&app_version=4.4.0.6&versioncode=20141528&market_id=floor_web
&_key=<KEY>&device_code=%5Bd%5D<uuid>&use_type=1&sign=<小写md5>&timestamp=<毫秒>&nonce_str=<32位随机>
```

⚠️ 三个和推论不同的点：
1. 路径是 **`/upload/v5/image`**（不是 v3）
2. `use_type=1`（不是 2）
3. **sign 是小写**（v3 那套 `secret=my_sign@huluxia.com` 大写的写法在 v5 上不适用）

实测返回：
```json
{"fid":"g4/M03/DE/48/rBAAdmrg_fOABviTAAPn9NF0zYk615.png","size":255988,
 "url":"http://cdn.u1.huluxia.com/g4/M03/DE/48/rBAAdmrg_fOABviTAAPn9NF0zYk615.png",
 "ts":1793129971440,"status":1}
```

**上传签名（与其他接口不同）**：
```
raw = "_key=..&app_version=..&device_code=<原始值>&gkey=000000&market_id=floor_web
       &nonce_str=..&platform=2&timestamp=<ms>&use_type=2&versioncode=..&secret=my_sign@huluxia.com"
sign = md5(raw)   # ⚠️ 大写，且用 k=v& 连接，不是 k+v
```

multipart 字段：`_key=key_10`（固定）、`file=@xxx.png;type=image/png`

实测成功返回：
```json
{"fid":"g4/M03/DE/48/rBAAdmrg_SGARf-2AAAAQ7-LnD4369.png",
 "url":"http://cdn.u1.huluxia.com/g4/M03/DE/48/rBAAdmrg_SGARf-2AAAAQ7-LnD4369.png",
 "size":67,"ts":1793129761830,"status":1}
```
拿到 `url` 后即可用于发帖/评论的 `images` 字段。

---

## 十二、发帖 ✅ 已完全打通（含带图）

### 12.1 最终可用方案（实测成功）

**关键：用 `/post/create/ANDROID/**4.2.2**`，这个版本的签名仍是简单 md5。**
`4.2.6`/`4.2.4` 走原生混淆签名（算不出来），但 `4.2.2` 完全够用。

```
POST http://floor.huluxia.com/post/create/ANDROID/4.2.2
  ?platform=2&gkey=000000&app_version=4.4.0.6&versioncode=20141528
  &market_id=floor_web&_key=<KEY>&device_code=%5Bd%5D<uuid>&phone_brand_type=MI

Content-Type: application/x-www-form-urlencoded

draft_id=0
&cat_id=<板块ID>
&tag_id=-1
&type=0
&title=<标题>
&detail=<text>正文内容</text>          ← 必须用 <text>…</text> 包裹
&patcha=
&voice=
&lng=0.0&lat=0.0
&images=<fid1>,<fid2>,                ← 图片 fid，逗号分隔、**末尾带逗号**；无图时留空
&user_ids=
&recommendTopics=
&remindTopics=
&sign=<见下>
&is_app_link=3
```

**签名（小写 md5）**
```
raw  = "_key"+KEY
     + "detail"+DETAIL
     + "device_code"+DEVICE_CODE原始值([d]xxx)
     + "images"+IMAGES
     + "title"+TITLE
     + "voice"+VOICE(空)
sign = md5(raw + "fa1c28a5b62e79c3e63d9030b6142e4b")
```

**成功返回**
```json
{"msg":"您刚发的帖子需要审核,审核成功后才会显示","postID":56005564,"code":201,"keepEditor":0,"status":1}
```
> `code 201` = 已提交、进审核队列。审核通过后才会公开显示（新号发帖基本都要过审）。

### 12.2 配图流程

```
① POST http://upload.huluxia.com/upload/v3/image   （multipart，见第十一节）
   → 返回 {"fid":"g4/M00/DE/49/xxx.png","url":"http://cdn.u1.huluxia.com/...","status":1}
② 把 fid 填进发帖的 images 字段：images=g4/M00/DE/49/xxx.png,
③ 多图就逗号串起来，末尾保留逗号
```

### 12.3 实测记录

| 测试 | 结果 |
|---|---|
| 大号 `4.2.2` 纯文本 | ✅ `postID 56005557` |
| 小号 `4.2.2` 纯文本 | ✅ `postID 56005560` |
| **大号 `4.2.2` 带图** | ✅ **`postID 56005564`** |
| 大号 `4.2.6` / `4.2.4` | ❌ 参数异常（原生签名） |
| 大号 `4.1.8` | ⚠️ 通得过但报「请勿重复发布主题」（易触发去重） |
| 删帖 `/post/destroy/ANDROID/2.0?post_id=` | ✅ `{"msg":"","status":1}` |

> 测试帖均已删除，未留在账号里。

### 12.4 注意事项

1. **频率限制**：同一账号连续发帖会返回 `发贴太快了!喝杯茶休息休息`，冷却约几分钟；建议发帖间隔 ≥5 分钟。
2. **审核**：新号/低等级号发帖基本进审核（`code 201`），不是失败。
3. **标题去重**：相同标题+内容会报「请勿重复发布主题」，标题带上随机后缀可避免。
4. **versioncode 必须 20141528**，旧值会 104。
5. `4.2.6` 的 sign 由 `libcompressor.so` 原生生成（已逆不动），但**用 4.2.2 就完全不需要它**。

### 12.5 其它发帖相关接口
```
POST /post/create/ANDROID/4.2.2      发帖（推荐，简单签名）
POST /post/create/ANDROID/4.2.6      新版发帖（原生签名，需 hook）
GET  /post/destroy/ANDROID/2.0?post_id=   删帖
GET  /post/create/list/ANDROID/4.1.8?user_id=  我的帖子
POST /upload/v3/image                上传图片拿 fid
```

## 十三、其余接口状态

| 接口 | 状态 |
|---|---|
| `GET /topic/favorite/list/ANDROID/4.1` | ❌ 404 |
| `GET /user/pointLog/...` | ❌ 404 |
| `POST /post/destroy/ANDROID/2.0?post_id=` | ⚠️ 未测（账号无自己的帖子可删） |
| `/favorite/group/info`、`/favorite/group/del` | ⚠️ 未测 |
| `/qq/login/ANDROID/4.0` | ⚠️ 未测（需 QQ access_token+openid） |
| `/wx/login/ANDROID/4.0` | ⚠️ 未测 |
| `/oneclick/login/ANDROID/4.0` | ⚠️ 未测（运营商一键登录） |
| `/user/signin/ANDROID/4.2.6` | ⚠️ dex 里是新版签到路径，未测（4.1.8 已够用） |

---

## 十四、踩坑总表

1. **接口版本段**：`2.1`/`2.0` 的 `/post/list`、`/post/detail`、`/user/info` 已关闭（code 104），必须 `4.1.8`；但 `/user/signin`、`/post/praise`、`/friendship/*` 仍可用 2.0/2.1。
2. **登录**：必须 `login_type=2` + 密码 md5 小写 + `4.1.8` + 签名里 device_code 用原始值。
3. **签名大小写**：登录/评论/发帖小写；**签到大写**；**图片上传大写且用 `k=v&` 拼接 + 特殊 secret**。
4. **收藏列表**必带 `user_id`；**收藏分组**也必带 `user_id`。
5. **板块详情**参数是 `cat_id` 不是 `category_id`。
6. **搜索**用 2.1/2.0/4.1.8，不要用 2.2（404）。
7. **`/friendship/follow` 和 `/post/praise` 都是 toggle**，必须先 check 再操作，否则会误取关/误取消赞。
8. **帖子列表/用户帖子列表没有 `status` 字段**属正常结构，不要当成失败。
9. **发帖必须用 4.2.2 版本**（4.2.6 走原生签名算不出来）；`images` 是 fid 逗号串且末尾带逗号；`detail` 要用 `<text>…</text>` 包裹。

---

## 十二·补、赠送葫芦（打赏）✅ 已打通

### 接口（实测成功）

```
POST http://floor.huluxia.com/credits/transfer/ANDROID/4.1.8     ← 用 4.1.8，不是 4.2.6
  ?platform=2&gkey=000000&app_version=4.4.0.6&versioncode=20141528
  &market_id=floor_web&_key=<KEY>&device_code=%5Bd%5D<uuid>&phone_brand_type=MI

Content-Type: application/x-www-form-urlencoded

post_id=<帖子ID>
&type_id=1
&isadmin=0
&score=<葫芦数量>
&score_txt=<赠言>
&sign=<见下>
```

**成功返回**
```json
{"msg":"","iscredits":1,"status":1}
```

### 签名算法（已逆向，小写 md5）

```
raw = "_key"        + KEY
    + "device_code" + DEVICE_CODE原始值([d]xxx)
    + "post_id"     + POST_ID
    + "score"       + SCORE
    + "score_txt"   + 赠言原文

sign = md5(raw + "fa1c28a5b62e79c3e63d9030b6142e4b")
```

⚠️ 只签这 5 项：`_key`、`device_code`、`post_id`、`score`、`score_txt`。
`type_id` 和 `isadmin` **不参与签名**（但仍然要传）。

### 版本可用性（关键）

| 版本 | 结果 |
|---|---|
| `/credits/transfer/ANDROID/**4.1.8**` | ✅ **可用，简单 md5 签名** |
| `/credits/transfer/ANDROID/4.2.6` | ❌ 原生签名，算不出 |
| `4.2.2` / `4.2.0` / `4.1.0` | ❌ 404 不存在 |

> 这和发帖完全一样的套路：**4.2.6 是原生签名，退到低版本（发帖 4.2.2 / 打赏 4.1.8）就是简单 md5。**

### 必需参数（省略会返回 code 101 + arg 名）

`post_id`、`type_id`、`isadmin`、`score`、`score_txt`、`sign` 六个全必需。

### 实测记录

| 现象 | 含义 |
|---|---|
| `{"iscredits":1,"status":1}` | ✅ 赠送成功 |
| `{"code":104,"msg":"非法送葫芦!"}` | 签名通过了，但该帖不允许打赏 |
| `{"code":104,"msg":"送葫芦太快,先休息一下吧!"}` | 签名通过，被频率限制 |
| `{"code":104,"msg":"赠送葫芦失败,参数异常"}` | 签名错误 |

### 注意

1. 打赏会真实消耗葫芦，测试时用 `score=1`。
2. 有频率限制（「送葫芦太快」），建议间隔 ≥1 分钟。
3. `type_id=1` 表示对帖子打赏；`isadmin=0` 普通用户。

### 其它相关（易混淆）

- `GET /create/post/tip/ANDROID/4.1.7` → **发帖前版规提示**，不是打赏
- `GET /user/cash/ANDROID/4.4.4` → 兑换商城（需 `product_id`）
- `GET /user/cash/list/ANDROID/4.2` → 葫芦流水（可正常调用）
- `POST /comment/praise/ANDROID/4.2.6` → 评论点赞，不是打赏

---

## 十五、给大肥鱼接入的建议

| 功能 | 接口 | 可行性 |
|---|---|---|
| 登录 | `/account/login/ANDROID/4.1.8` | ✅ 可直接实现 |
| 逛板块 | `/category/list` + `/post/list` | ✅ |
| 看帖 | `/post/detail` | ✅ |
| 点赞 | `/post/praise`（先 check） | ✅ |
| 评论 | `/comment/create` | ✅ |
| 收藏 | `/post/favorite/create`（先 check） | ✅ |
| 关注 | `/friendship/follow`（先 check） | ✅ |
| 回复消息 | `/message/new/list?type_id=8` + `/comment/create` | ✅ |
| 签到 | `/user/signin/ANDROID/4.1.8`（sign 大写） | ✅ |
| 发图 | `/upload/v3/image` | ✅ |
| 发帖 | `/post/create/ANDROID/4.2.2` | ✅ 已打通（含带图） |
| 删帖 | `/post/destroy/ANDROID/2.0?post_id=` | ✅ 已验证 |

**落地要点**：
1. `_key` 每次登录都变，建议持久化 + 401/103 自动重登。
2. 关注/点赞/收藏前先 check，避免 toggle 误操作。
3. 请求间隔 ≥3 秒，避免「请勿重复发布主题」和风控。
4. 发帖用 4.2.2（简单签名），间隔 ≥5 分钟避开限流；返回 code 201 表示进审核，不是失败。


---

---

