# 本地影院 LocalCinema

Android 本地视频播放器。纯离线、无网络权限、数据只存在本机。

- 包名：`com.localcinema.app`
- 技术栈：Kotlin 2.0.20 · Jetpack Compose · Hilt · Room · DataStore · Media3
- 最低版本：Android 7.0（minSdk 24），targetSdk 34
- 当前版本：0.3.0（versionCode 5）

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
app/src/main/res/            （values / drawable / mipmap）
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
gradle wrapper --gradle-version 8.9
```

### 环境要求

| 项 | 版本 |
|---|---|
| Android Studio | Ladybug (2024.2)+ |
| JDK | 17（用 AS 自带 JBR） |
| Gradle | 8.9 |
| AGP / Kotlin | 8.5.2 / 2.0.20 |

---

## 二、已完成的功能

### 媒体库
- **目录建专辑**：SAF 选文件夹 → 递归扫描 → 自动建专辑
- **扫描性能**：`DocumentsContract` 批量查询（一次拿整个目录），不是逐文件查
- **增量同步**：以 `(uri, size, mtime)` 判断增删改；未扫到的旧记录标 `missing` 而非删除
- **自然排序**：`第2集` 排在 `第10集` 前，不是字典序
- **排序切换**：名称 / 最近加入 / 修改时间 / 时长，按专辑独立记忆
- **缩略图**：取第 120 秒的帧（跳过片头黑屏），失败回退 1/3 处、再回退首帧，落盘缓存
- **专辑封面**：默认自动抽帧；Hero 卡右上角可自选图片（复制进私有目录，不受 SAF 授权失效影响）
- **专辑管理**：长按卡片 → 重新扫描 / 重命名 / 删除（只删记录，不动视频文件）
- **失效文件**：扫描发现文件已不在时标 `missing`，专辑页顶部提示，可一键清理记录
- **单集操作**：长按列表项 → 播放 / 标记已看完 / 清除观看进度 / 从专辑移除
- **批量操作**：顶栏「☑」进多选 → 勾选多项 → 标记已看 / 清进度 / 移除记录；支持全选（只作用于当前搜索结果）

### 首页「继续观看」
- 冷启动后**一次点击回到上次的剧**，不用先进专辑再翻集
- 数据来自 `playback_state` join `videos`，只看未看完、有进度、文件还在的记录
- **同一专辑只留最近 1 条**：横滑里最多 6 部不同的剧，而不是同一部剧占满整条
- 卡片显示缩略图、进度条、「已看 12:30 / 45:20 · 专辑名」
- 长按可「继续播放 / 打开所在专辑 / 从继续观看移除」
- 首页统计改为「N 个专辑 · M 个视频 · K 个未看完」

### 文件夹 Tab（已授权目录管理）
- 列出**系统里所有持久授权的目录**（`contentResolver.persistedUriPermissions`），不只是建过专辑的
- 三类状态一眼区分：正常（N 个视频）/ 未添加到专辑（点击即添加并扫描）/ 授权失效（橙字提示）
- 目录菜单：重新扫描、删除专辑
- 顶部「添加目录」直接授权新目录并扫描

### 最近播放 Tab
- 全部播放记录（含已看完），按「今天 / 昨天 / 本周 / 更早」分组
- 每项显示缩略图、进度、专辑名、相对时间（刚刚 / 12 分钟前 / 昨天 / 3 天前）
- 与首页「继续观看」的分工：这里是完整历史，那里是未看完的快捷入口
- 顶部「清空」二次确认，只清进度，不动视频与专辑

### 搜索
- **全局搜索**：首页搜索框实时匹配专辑名与视频名，结果分组，点视频直接起播
- **专辑内搜索**：详情页顶栏搜索图标，只过滤当前专辑

### 播放
- **内核**：ExoPlayer（Media3），运行在 `PlaybackService` 里，与界面解耦
- **进度记忆**：每视频独立记录，5 秒心跳 + 暂停 + 退后台 + 播完四个时机落库
- **续播规则**：从上次位置 **-5 秒** 开始；距结尾不足 10 秒视为看完，从头播
- **播放模式**：顺序 / 单集循环 / 随机 / 播完停止，四态在「更多」面板切换
- **下一集倒计时**：播完弹「N 秒后播放下一集」，可取消或立即播放；可在设置里关掉改成直接跳
- **手势**：左半屏上下滑调亮度、右半屏上下滑调音量、水平滑拖进度、双击快退/快进
- **长按加速**：按住画面临时切到 1.5x / 2.0x / 3.0x（可关），松开还原，不改用户设置的倍速
- **睡眠定时**：15 / 30 / 60 / 90 分钟，到点暂停播放，顶栏显示剩余时间
- **锁屏**：一键锁定，控件全隐、手势失效，只留解锁按钮
- **画面比例**：适应 / 铺满 / 裁剪
- **倍速 0.5x–5.0x**：内部用百分数整数存储，`±` 每次 0.1x，长按连续步进，9 档快捷位，变速不变调，速度持久化

### 外观
- **深色 / 浅色 / 跟随系统**三态，即时生效，系统栏图标明暗跟着切
- 播放页与封面覆盖层**不参与主题**：它们叠在视频/封面上，底色不可预测，固定深色遮罩 + 白字

### 字幕
- **外挂自动配对**：扫描时把同目录同名 `srt / ass / ssa / vtt / sub / smi` 与视频绑定
- **手动挂载 + 记忆**：手动选的字幕会被记住，下次进来自动加载，可一键恢复自动匹配
- **自解析渲染**：自己解析字幕文件后按播放位置筛选，因此支持 **±60 秒延迟微调**
- **样式持久化**：字号 10–30、四色、底部位置、延迟，退出重进仍然生效
- **内嵌轨**：列出文件自带字幕轨，可切换可关闭
- **编码兼容**：BOM 检测 + UTF-8/GBK 自动回退

### 悬浮按钮（核心差异化）
- 左右**完全独立**：启用、秒数 1–120、直径 36–96dp、透明度 10–100%、位置、常驻、长按连续
- 位置按**屏幕比例**存储，换设备不会跑出屏幕
- **常驻显示**：开启后控件自动隐藏时按钮仍保留；锁定态一律隐藏
- 设置页带实时预览 + 九宫格预设 + 水平/垂直滑块
- 设置根列表每行显示「秒数 · 是否常驻」，点进二级页调整；启用开关只在二级页（避免两处开关重复）
- **显示跳转提示**：快退/快进后在屏幕中央弹出的秒数提示可关闭，关闭后不再遮挡画面

### 上次状态恢复
- 进入专辑记录 `lastAlbumId`，开始播放记录 `lastVideoId`
- 冷启动按开关直达上次专辑（专辑已删则回退首页）
- 专辑列表用浅橙底 + 1.5dp 橙边 + 「上次播放」角标标记上次那集，并自动滚动到该位置
- 可选「自动续播上次视频」：进入专辑直接起播

### 后台与通知
- `MediaSessionService` 承载播放器，退后台/锁屏继续播放
- 通知栏自动生成播放控制与进度，锁屏可控
- 设置项「后台播放」控制退到后台是否暂停

---

## 三、待办与暂缓

| 项 | 状态 | 说明 |
|---|---|---|
| **VLC / libVLC 兜底内核** | **暂缓** | 见下方说明 |
| 画中画（PiP） | **不需要** | 明确不做 |
| 截图 | **不需要** | 明确不做 |

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
│   ├── db/                      Room v3：albums / videos / playback_state
│   ├── prefs/                   DataStore：设置、按钮配置、播放模式、字幕样式
│   ├── scanner/                 SAF 扫描、自然排序、格式判定
│   ├── subtitle/                SRT/VTT/ASS 解析 + 编码探测
│   ├── thumb/                   抽帧与磁盘缓存
│   └── repo/                    LibraryRepository / PlaybackRepository
├── ui/
│   ├── nav/AppRoot.kt           四 Tab + 路由 + 冷启动直达
│   ├── home/                    首页：搜索、继续观看、专辑网格、扫描进度
│   ├── folder/                  文件夹：已授权目录管理
│   ├── recent/                  最近播放：历史记录与时间分组
│   ├── album/                   专辑详情：Hero、排序、搜索、失效提示、单集菜单
│   ├── player/                  播放页、ViewModel、面板（倍速/字幕/音轨/更多）
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

**7. 多通道手势不能用多个 `pointerInput`**

Compose 的多个 `pointerInput` 会**并行**接收同一串事件。把「长按加速」「双击快进」「上下滑音量/亮度」「横滑进度」拆成四个 modifier，会互相抢事件（调音量顺带触发双击）。
播放页现在只有**一个** `pointerInput` + `awaitEachGesture` 手写状态机，按下先起长按计时，超过 touchSlop 就取消计时转拖动，抬起时按位移判定单击/双击。

**8. `WhileSubscribed` 的 StateFlow 没人订阅时 `.value` 永远是初始值**

`AlbumDetailViewModel.resolveAutoResume()` 一开始读 `autoResume.value`，但页面没 collect 这个流，`WhileSubscribed` 不会启动，值一直是 `false`，导致自动续播永远不触发。改成直接 `settingsStore.settings.first()`。

**9. `object` 常量做不了运行时主题切换**

`PlayerColors` 原本是 `object` + `Color(0xFF...)` 常量。这种写法编译期就定死了值，`CompositionLocal` 换了也没人读——界面不会重组。
现在拆成 `Palette` 数据类 + `DarkPalette/LightPalette` + `staticCompositionLocalOf`，`PlayerColors.Xxx` 改成 `@Composable @ReadOnlyComposable get()`。
副作用：这些 token 只能在 `@Composable` 上下文读取，普通函数里用（例如原来的 `DarkColors` 顶层 val）会编译失败。

**10. `persistedUriPermissions` 才是"我授权过哪些目录"的真相**

`albums` 表只记录建过专辑的目录。用户通过 SAF 授权过、但没建成专辑的目录，没有任何地方记录，所以在 App 里完全不可见。
`contentResolver.persistedUriPermissions` 能读到系统保存的全部持久授权，把它和 `albums` 做差集就是"未添加到专辑的目录"。文件夹页的核心逻辑就是这个差集。

**11. 沙箱到 GitHub 的链路不稳**

`api.github.com` 稳定，但 `github.com:443`（git 传输）会间歇性 TLS 中断。已把 git 切到 HTTP/1.1 + 短超时重试，成功率从 1/5 提升到约 4/6。推送失败属网络问题，重试即可。

---

## 六、已知限制

- ASS 只渲染文本，字体/位置/动画等特效不支持（自解析的边界）
- 字幕延迟仅对外挂字幕生效，内嵌轨不支持（面板已注明）
- 首次扫描较慢：每个视频都要读一次时长/分辨率，100 个约 10–20 秒
- 缩略图首次进专辑逐条生成，滚到哪生成到哪
- `fallbackToDestructiveMigration()` 仍在（作为迁移失败的最后兜底），上线前应移除
- 播放模式是全局设置，不按专辑记忆
- 深色主题下启动无闪屏；浅色主题冷启动会先看到一帧深色窗口背景（`themes.xml` 的 `windowBackground` 是静态资源，无法跟随应用内设置）
