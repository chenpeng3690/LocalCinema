# 本地影院 LocalCinema

Android 本地视频播放器。纯离线、无网络权限、数据只存在本机。

- 包名：`com.localcinema.app`
- 技术栈：Kotlin 2.0.20 · Jetpack Compose · Hilt · Room · DataStore · Media3
- 最低版本：Android 7.0（minSdk 24），targetSdk 34

---

## 一、怎么跑起来

### 方式 A：覆盖到 Android Studio 新建的项目（推荐）

1. AS → **New Project** → **Empty Activity**（Compose 模板）
2. Name `LocalCinema`，Package `com.localcinema.app`，Minimum SDK `API 24`
3. 等 Gradle Sync 完成（这一步会生成 `gradle-wrapper.jar`、`gradlew`、`local.properties`）
4. 用本仓库的这些文件**覆盖**过去：

```
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/...        （整个 java 目录）
app/src/main/res/values/     （strings / themes / colors）
build.gradle.kts
settings.gradle.kts
gradle.properties
app/proguard-rules.pro
```

**不要覆盖** `local.properties`、`gradlew`、`gradlew.bat`、`gradle/wrapper/` —— 这些和你的机器绑定。

5. 再 Sync → Run。

### 方式 B：直接打开仓库目录

仓库不含 `gradle-wrapper.jar`（二进制文件未提交）。若报 `Could not find or load main class org.gradle.wrapper.GradleWrapperMain`，在本机执行一次：

```powershell
gradle wrapper --gradle-version 8.7
```

### 环境要求

| 项 | 版本 |
|---|---|
| Android Studio | Ladybug (2024.2)+ |
| JDK | 17（用 AS 自带 JBR，不要 JDK 21+） |
| Gradle | 8.7 |
| AGP / Kotlin | 8.5.2 / 2.0.20 |

---

## 二、已完成的功能

### 媒体库
- **目录建专辑**：SAF 选文件夹 → 递归扫描 → 自动建专辑；也支持一次把子目录各建一个
- **扫描性能**：`DocumentsContract` 批量查询（一次拿整个目录），不是逐文件查
- **增量同步**：以 `(uri, size, mtime)` 判断增删改；未扫到的旧记录标 `missing` 而非删除
- **自然排序**：`第2集` 排在 `第10集` 前，不是字典序
- **缩略图**：取第 120 秒的帧（跳过片头黑屏），失败回退 1/3 处、再回退首帧，落盘缓存
- **专辑管理**：长按卡片 → 重新扫描 / 重命名 / 删除（只删记录，不动视频文件）

### 播放
- **内核**：ExoPlayer（Media3），运行在 `PlaybackService` 里，与界面解耦
- **进度记忆**：每视频独立记录，5 秒心跳 + 暂停 + 退后台 + 播完四个时机落库
- **续播规则**：从上次位置 **-5 秒** 开始；距结尾不足 10 秒视为看完，从头播
- **手势**：左半屏上下滑调亮度、右半屏上下滑调音量、水平滑拖进度、双击快退/快进
- **锁屏**：一键锁定，控件全隐、手势失效，只留解锁按钮
- **画面比例**：适应 / 铺满 / 裁剪 三态循环

### 字幕
- **外挂自动配对**：扫描时把同目录同名 `srt / ass / ssa / vtt / sub / smi` 与视频绑定
- **自解析渲染**：自己解析字幕文件后按播放位置筛选，因此支持 **±60 秒延迟微调**
- **样式**：字号 10–30、四色、底部位置可调
- **内嵌轨**：列出文件自带字幕轨，可切换可关闭
- **编码兼容**：BOM 检测 + UTF-8/GBK 自动回退

### 音频与倍速
- **音轨切换**：多语言/多声道列表，显示声道数
- **倍速 0.5x–5.0x**：内部用百分数整数存储，`±` 每次 0.1x，长按连续步进，9 档快捷位，变速不变调，速度持久化

### 悬浮按钮（核心差异化）
- 左右**完全独立**：启用、秒数 1–120、直径 36–96dp、透明度 10–100%、位置、常驻、长按连续
- 位置按**屏幕比例**存储，换设备不会跑出屏幕
- **常驻显示**：开启后控件自动隐藏时按钮仍保留；锁定态一律隐藏
- 设置页带实时预览 + 九宫格预设 + 水平/垂直滑块
- 设置根列表每行带开关，可随手关闭遮挡画面的按钮

### 上次状态恢复
- 进入专辑记录 `lastAlbumId`，开始播放记录 `lastVideoId`
- 冷启动按开关直达上次专辑（专辑已删则回退首页）
- 专辑列表用浅橙底 + 1.5dp 橙边 + 「上次播放」角标标记上次那集，并自动滚动到该位置

### 后台与通知
- `MediaSessionService` 承载播放器，退后台/锁屏继续播放
- 通知栏自动生成播放控制与进度，锁屏可控
- 设置项「后台播放」控制退到后台是否暂停

---

## 三、待办与暂缓

| 项 | 状态 | 说明 |
|---|---|---|
| **VLC / libVLC 兜底内核** | **暂缓** | 见下方说明 |
| 搜索（专辑内 / 全局） | 未做 | 首页搜索框目前是静态占位 |
| 播放列表模式 | 未做 | 顺序 / 随机 / 单集循环 / 播完关闭 |
| 画中画（PiP） | 未做 | 需要 Service 侧配合 |
| 浅色主题 | 未做 | 目前仅深色 |
| 字幕样式持久化 | 未做 | 字号/颜色/延迟仅本次播放有效 |
| 专辑封面自定义 | 未做 | 目前自动抽帧 |
| 长按 2 倍速加速 | 未做 | 手势里预留了位置 |

### 关于 VLC（暂缓原因）

VLC 的解码核心 libVLC 可以播 ExoPlayer 解不了的东西：**RMVB/RealVideo、部分 WMV/AVI、DTS 音轨、ASS 特效字幕**。

暂缓的原因：当前片源以 MP4/MKV 为主，用不上；而代价不小 ——
- 体积：全 ABI 约 100MB+，做 ABI 分包后 APK 仍会从十几 MB 涨到 **40MB 左右**
- 许可证：LGPL 2.1，商用需动态链接并在「关于」页列明
- 复杂度：双内核要同步播放位置、切换时不能黑屏，约 300–400 行

**什么时候该加**：手上出现 RMVB 老剧、WMV 录屏、或带 DTS 音轨的蓝光压制片，ExoPlayer 黑屏/无声时。届时按「ExoPlayer 主 + libVLC 兜底，解码异常自动切换」实现。

---

## 四、架构与目录

```
app/src/main/java/com/localcinema/app/
├── LocalCinemaApp.kt            @HiltAndroidApp
├── MainActivity.kt              单 Activity + 边到边
├── playback/
│   ├── PlaybackService.kt       MediaSessionService，持有 ExoPlayer
│   └── PlayerConnection.kt      MediaController 异步连接
├── data/
│   ├── db/                      Room：albums / videos / playback_state
│   ├── prefs/                   DataStore：设置、按钮配置、倍速
│   ├── scanner/                 SAF 扫描、自然排序、格式判定
│   ├── subtitle/                SRT/VTT/ASS 解析 + 编码探测
│   ├── thumb/                   抽帧与磁盘缓存
│   └── repo/                    LibraryRepository / PlaybackRepository
├── ui/
│   ├── nav/AppRoot.kt           四 Tab + 路由 + 冷启动直达
│   ├── home/                    首页：专辑网格、扫描进度
│   ├── album/                   专辑详情：Hero、列表、上次播放高亮
│   ├── player/                  播放页、ViewModel、面板（倍速/字幕/音轨）
│   ├── settings/                设置根页、按钮配置页
│   ├── common/                  自绘开关、占位页
│   └── theme/                   Color / Type / Dimens / Theme（token）
└── util/VideoId.kt              sha1(uri+size+mtime)
```

**数据流**：`PlaybackService`（持有 player）→ `MediaController` → `PlayerViewModel` → Compose UI。界面销毁不影响播放。

**设计约束**：全工程禁止硬编码色值，除 `ui/theme/Color.kt` 外不应出现 `Color(0xFF...)`。尺寸走 `Dimens`，字号走 `PlayerType`。

---

## 五、踩过的坑（改代码前先看这里）

**1. `@Insert(REPLACE)` + 外键 CASCADE = 更新即删除子记录**

SQLite 的 REPLACE 是 `DELETE + INSERT`，DELETE 会触发 `ON DELETE CASCADE`。
- 更新专辑封面（`albumDao.upsert`）→ 删 albums 行 → **videos 全部级联删除 → 列表清空**
- 重扫视频（`videoDao.upsertAll`）→ 删 videos 行 → **playback_state 级联删除 → 进度全丢**

已全部改为 `@Upsert`（冲突走 UPDATE，不产生 DELETE）。新增需要"存在则更新"的表时务必注意。

**2. Media3 的 `Cue` 没有起止时间**

`Cue` 只描述"此刻显示什么"，时间信息在 `CueGroup.presentationTimeUs` 上，且只是一个时间点。
所以**用 ExoPlayer 的字幕轨无法实现延迟**。外挂字幕因此改为自解析（`SubtitleRepository`），按位置筛选渲染。

**3. SAF 的 `size` 可能是 0**

部分 provider 不返回 `COLUMN_SIZE`。原逻辑 `size in 0 until 1MB` 会把整个目录的文件判定为"过小"全部过滤掉。现在改为 `size > 0 && size < 128KB`。

**4. 扫描结果为 0 时不能继续往下跑**

后续的 `markMissing` 会把所有已有记录标记失效，等于清空专辑。现在扫到 0 条直接报错返回。

**5. `LaunchedEffect(Unit)` 首次执行时 `currentRoute` 为 null**

冷启动导航不能用 `currentRoute == 首页` 做判断，否则永远不跳转。改用 `rememberSaveable` 标记只执行一次。

**6. 悬浮按钮层必须在控件层之上**

控件层有一层全屏黑色渐变（保证文字可读），按钮画在下面会被压暗，看起来像"设了 60% 透明度实际只有 40%"。

**7. 沙箱到 GitHub 的链路不稳**

`api.github.com` 稳定，但 `github.com:443`（git 传输）会间歇性 TLS 中断。已把 git 切到 HTTP/1.1 + 短超时重试，成功率从 1/5 提升到约 4/6。推送失败属网络问题，重试即可。

---

## 六、已知限制

- 没有 launcher 图标（manifest 未声明 `android:icon`），装出来是系统默认图标
- ASS 只渲染文本，字体/位置/动画等特效不支持（自解析的边界）
- 字幕延迟仅对外挂字幕生效，内嵌轨不支持（面板已注明）
- 首次扫描较慢：每个视频都要读一次时长/分辨率，100 个约 10–20 秒
- 缩略图首次进专辑逐条生成，滚到哪生成到哪
- `fallbackToDestructiveMigration()` 仍在，上线前需改为正式 Migration
- 仅深色主题
