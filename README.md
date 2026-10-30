# 三楼·大肥鱼 (hlx-halo)

## 相关项目 / 下载

| 项目 | 项目地址 | 下载 |
|---|---|---|
| HaloDesk（Windows） | https://gitee.com/guo-wenlong/halo-desk | [HaloDesk.exe](https://gitee.com/guo-wenlong/halo-desk/releases/download/v10.18/HaloDesk.exe) |
| 葫芦侠大肥鱼（本项目，Android） | https://gitee.com/guo-wenlong/hlx-halo | [HlxHalo.apk](https://gitee.com/guo-wenlong/hlx-halo/raw/master/release/HlxHalo.apk) |
| 灵芥助手（Android） | https://gitee.com/guo-wenlong/lingjie-halo | [LingjieHelper.apk](https://gitee.com/guo-wenlong/lingjie-halo/raw/master/release/LingjieHelper.apk) |


葫芦侠三楼社区 AI 宠物：桌面悬浮挂件 + 后台自主逛社区 + 秒回评论 + 打赏/关注/发帖，全部走葫芦侠官方 HTTP 接口，不需要 Root。

---

## 一、项目简介

大肥鱼是一个运行在 Android 系统上的 AI 宠物应用，以悬浮小鱼的形态出现在桌面，能够自主在葫芦侠社区中互动、回复消息、发帖打赏。它利用 AI 大模型进行决策，通过葫芦侠官方接口完成各种操作。

### 核心特性

- **悬浮挂件**：桌面常驻可拖动的小鱼，点击互动
- **自主逛社区**：每隔一段时间自动浏览板块、点赞、评论、收藏、关注
- **消息秒回**：10 秒轮询通知，自动回复 @ 你的消息
- **18 种互动动作**：点赞、收藏、评论、回复、关注、打赏、发帖等
- **记忆系统**：通用记忆、用户记忆、生活记录、情绪好感度
- **文生图**：支持 OpenAI 兼容 API、阿里百炼、SD WebUI

---

## 二、功能详细

### 1. 悬浮挂件

- 桌面显示一条动态小鱼，可拖动位置
- 点击摸头会回复一句随机短话
- 前台服务常驻，通知栏显示运行状态

### 2. 自主逛社区

- 每 20 分钟唤醒一次（可配置间隔）
- 自动选择板块浏览，每条互动换一个板块
- 自动跳过已评论、已看过的帖子
- 公告版（id 1）、意见反馈（id 6）全局拉黑
- 支持指定板块白名单

### 3. 互动动作（模型可返回 18 种）

| 动作 | 说明 |
|---|---|
| like / unlike | 点赞 / 取消点赞 |
| favorite / unfavorite | 收藏 / 取消收藏 |
| comment | 评论帖子 |
| reply | 回复评论 |
| follow / unfollow | 关注 / 取关 |
| tip | 送葫芦给评论 |
| tip_user_post / tip_my_post | 送葫芦给帖子 |
| delete_my_post / delete_post | 删帖 |
| delete_comment | 删评论 |
| list_self / user_posts / user_info | 查看信息 |
| goto | 换板块 |
| post | 发帖（可带图） |
| signin | 签到 |

### 4. 消息秒回

- 10 秒轮询通知接口
- 只回复 1 天内、未回复过的消息
- 先认领再发送，避免重复回复
- 支持错误重试，锁帖/禁言时自动放弃

### 5. @ 提及

- 使用不可见控制符实现 @ 效果
- 只能 @ 互关好友
- 支持「@主人」「@大大王」自动替换

### 6. 送葫芦（打赏）

- 主人可随时打赏
- 非主人需好感度 ≥ 90（可配置）
- 每日打赏次数有限制

### 7. 发帖与日记

- 支持带图发帖
- 每日发帖上限默认 2 条
- 每天自动发布一篇日记（不占发帖上限）

### 8. 记忆系统

- 通用记忆（memory.txt）
- 用户记忆（mem_u<uid>.txt）
- 生活记录（life.txt，每天重置）
- 超长自动 AI 摘要
- 情绪值与好感度系统

### 9. 文生图

支持三种协议：
- OpenAI 兼容 API
- 阿里百炼（推荐，额度自动轮换）
- SD WebUI

### 10. 统计

登录成功后自动上报 uid / 昵称 / 机型到统计仓库。

---

## 三、设置项

| 设置项 | 键名 | 默认值 |
|---|---|---|
| 唤醒间隔（分钟） | `interval_min` | 20 |
| 每次最多互动帖子数 | `max_steps` | 4 |
| 每日发帖上限 | `post_daily_limit` | 2 |
| 每日打赏上限 | `tip_daily_limit` | 3 |
| 打赏所需好感度 | `tip_favor_min` | 90 |
| 通用记忆上限（字） | `memory_max_chars` | 1500 |
| 用户记忆上限（字） | `user_mem_max_chars` | 800 |
| 生活记录刷新（分钟） | `life_refresh_min` | 60 |

所有操作间隔随机 0～15 秒。

---

## 四、权限要求

### App 内能力开关（perm_*）

`perm_like` `perm_comment` `perm_favorite` `perm_follow` `perm_reply` `perm_signin` `perm_goto` `perm_post` `perm_tip`

### Android 系统权限

- `INTERNET`：访问网络
- `SYSTEM_ALERT_WINDOW`：悬浮窗
- `FOREGROUND_SERVICE`：前台服务
- `POST_NOTIFICATIONS`：通知
- `RECEIVE_BOOT_COMPLETED`：开机启动
- `WAKE_LOCK`：唤醒锁
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`：电池优化

---

## 五、构建与安装

### 构建环境

需要以下工具：
- JDK 17
- Android SDK（android.jar）
- R8（r8.jar）
- AAPT
- Zipalign
- 签名文件（key.jks，别名 ljs，密码 123456）

### 构建命令

```bash
sh build_hlx.sh
```

构建产物位于 `out/final.apk`。

### 安装

```bash
pm install -r out/final.apk
```

---

## 六、本地文件结构

| 文件 | 用途 |
|---|---|
| `files/memory.txt` | 通用记忆 |
| `files/mem_u<uid>.txt` | 用户专属记忆 |
| `files/life.txt` | 当天生活记录 |
| `files/log.txt` | 动态日志 |
| `files/debug.txt` | 调试日志 |
| `shared_prefs/fish.xml` | 全部配置、好感、心情等 |

---

## 七、注意事项

1. **行为克制**：葫芦侠对自动化敏感，建议保持默认间隔（0～15 秒随机，20 分钟唤醒）
2. **重复检测**：完全相同的正文会被拒绝
3. **锁定处理**：被锁帖子会自动放弃操作
4. **打赏上限**：服务端有日限额，超限会次日再试

---

## 八、API 文档

葫芦侠接口详细文档见 [API.md](./API.md)。

---

## 九、相关链接

- 源码：https://gitee.com/guo-wenlong/hlx-halo
- 统计上报：https://gitee.com/guo-wenlong/lingjie-misc
