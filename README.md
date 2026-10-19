# 本地影院 LocalCinema · 阶段二（目录扫描与专辑）

Android 本地视频播放器。纯离线、无网络权限、不联网可用。

- 包名：`com.localcinema.app`
- 语言/UI：Kotlin + Jetpack Compose（Material 3，深色主题）
- 最低支持：Android 7.0（minSdk 24），targetSdk 34
- 架构：MVVM + Hilt + Room + DataStore

---

## 一、怎么把这个工程跑起来（两种方式，二选一）

### 方式 A：覆盖到 Android Studio 新建的项目里（推荐，最不容易踩坑）

1. 打开 Android Studio → **New Project** → **Empty Activity**（Compose 模板）
2. 填写：
   - Name：`LocalCinema`
   - Package name：`com.localcinema.app`
   - Language：Kotlin
   - Minimum SDK：`API 24`
3. 等 Gradle Sync 完成（这一步会生成 `gradle-wrapper.jar`、`gradlew`、`local.properties`）
4. 把本压缩包里的这些**覆盖**进新项目同名位置：

```
app/build.gradle.kts          ← 覆盖（依赖清单）
app/src/main/AndroidManifest.xml
app/src/main/java/...         ← 整个 java 目录覆盖
app/src/main/res/values/      ← 覆盖 strings / themes / colors
build.gradle.kts              ← 覆盖（根插件版本）
settings.gradle.kts           ← 覆盖（模块名）
gradle.properties             ← 覆盖
app/proguard-rules.pro        ← 覆盖
```

**不要覆盖**（这些是本机相关，保留 AS 生成的）：
```
local.properties              ← 含你电脑的 SDK 路径
gradlew / gradlew.bat
gradle/wrapper/               ← wrapper 的 jar 与 properties
```

5. 再次 **Sync Now** → 运行 `app`。

### 方式 B：直接打开本目录

本压缩包**不含 `gradle-wrapper.jar`**（二进制文件无法在此环境生成）。直接打开会报：

```
Could not find or load main class org.gradle.wrapper.GradleWrapperMain
```

如果你本机装了 Gradle，在本目录执行一次即可补上：

```powershell
gradle wrapper --gradle-version 8.7
```

然后 Android Studio 里 **File → Open** 选择本目录。

> 更省事的做法还是走方式 A。

### 环境要求

| 项 | 版本 |
|---|---|
| Android Studio | Ladybug (2024.2) 或更新 |
| JDK | 17（用 AS 自带的 JBR 即可，不要用 JDK 21+） |
| Gradle | 8.7（wrapper 自动下载） |
| AGP / Kotlin | 8.5.2 / 2.0.20 |

---

## 二、阶段二新增（本轮）

现在可以**真正添加专辑了**：

1. 首页点「添加专辑」（空态按钮或右下 FAB）
2. 系统弹出目录选择器 → 选一个存放视频的文件夹 → 允许
3. 自动递归扫描（批量查询，不卡 UI），顶部显示「已查看 N 个文件 · 找到 M 个视频」
4. 扫描完成后专辑出现，封面取第一个视频第 120 秒的帧
5. 点专辑进入详情，视频按**自然排序**（第 2 集在第 10 集前），缩略图按需生成

新增文件：

```
data/
├── scanner/
│   ├── SafScanner.kt       SAF 递归扫描（DocumentsContract 批量查询 + 元数据提取）
│   ├── NaturalOrder.kt     自然排序比较器
│   └── VideoFormats.kt     扩展名白名单、时长/分辨率格式化
├── thumb/ThumbnailStore.kt 抽帧 + 磁盘缓存（120s → 1/3 处 → 首帧，三级回退）
└── repo/LibraryRepository.kt  专辑创建、扫描编排、增量同步、封面生成
ui/
├── album/AlbumDetailScreen.kt 详情页：Hero + 列表 + 缩略图懒生成
└── home/HomeScreen.kt         改：SAF 选择器、扫描进度条、卡片跳转
ui/nav/AppRoot.kt              改：album/{albumId} 路由，详情页隐藏底栏
```

**增量扫描**：重复扫描同一目录不会重复插入；本次没扫到的旧记录标记 `missing=1`（灰显）而不是物理删除——**播放进度不会因此丢失**。

---

## 三、阶段一交付了什么

```
com.localcinema.app
├── LocalCinemaApp.kt          @HiltAndroidApp
├── MainActivity.kt            单 Activity + 边到边全屏 + Compose 入口
├── di/AppModule.kt            Room 与 DAO 的 Hilt 提供
├── util/VideoId.kt            sha1(uri+size+mtime) 生成视频唯一 ID
├── data/
│   ├── db/
│   │   ├── Entities.kt        albums / videos / playback_state 三张表
│   │   ├── Daos.kt            三个 DAO（含 Flow 查询）
│   │   └── AppDatabase.kt     Room v1
│   └── prefs/SettingsStore.kt DataStore：上次专辑 / 上次视频 / 续播参数
└── ui/
    ├── theme/
    │   ├── Color.kt           PlayerColors（全站唯一色板，已清零硬编码）
    │   ├── Type.kt            字号阶梯 25/16/13/12.5/12/10.5/9
    │   ├── Dimens.kt          边距 18dp、圆角 13/15/11/9、进度条 3dp
    │   └── Theme.kt           深色 MaterialTheme
    ├── nav/AppRoot.kt         四 Tab 底部导航 + NavHost
    ├── home/HomeScreen.kt     大标题 + 搜索行 + 专辑网格 / 空态
    ├── settings/SettingsScreen.kt  分组列表，两个开关真实生效
    └── common/
        ├── PlayerSwitch.kt    自绘 38×22 开关（不用 Material 默认）
        └── PlaceholderScreen.kt
```

**能跑起来看到什么**：深色首页（空态引导）、四个 Tab 可切换、设置页开关能点且能持久化（杀进程重开仍保留）。

**这一轮刻意没做**：SAF 目录扫描、播放器、专辑详情、悬浮按钮、倍速面板、上次播放高亮。

---

## 四、阶段路线（后续每一轮都在此工程上增量）

| 阶段 | 内容 | 产出 |
|---|---|---|
| ✅ 1 | 骨架 + Room + DataStore + 主题 token + 四 Tab | 能编译、能打开 |
| ✅ 2 | SAF 授权、目录扫描、缩略图、自然排序 | 能添加专辑并看到列表 |
| 3 | 播放页 + ExoPlayer（手势/字幕/音轨/比例） | 能播放 |
| 4 | VLC 兜底内核 + 无感切换 | 能播 RMVB/DTS |
| 5 | 倍速 0.5–5.0（整数 tick） | 变速不变调 |
| 6 | 快退/快进悬浮按钮 + 常驻 + 配置页 | 核心差异化功能 |
| 7 | 进度记忆 + 续播 + 上次专辑恢复 | 闭环 |

---

## 五、已知事项

- **没有 launcher 图标**：manifest 未声明 `android:icon`，装出来是系统默认图标。阶段二补自适应图标。
- **硬编码颜色已清零**：除了 `ui/theme/Color.kt`，工程里没有任何 `Color(0xFF...)`。新增界面请只用 token，否则后续换肤会失控。
- **`fallbackToDestructiveMigration()`**：开发期用，上线前必须改成正式 Migration。
- **首次扫描会慢**：每个视频要读一次时长/分辨率（MediaMetadataRetriever），100 个视频约 10–20 秒。这是必要开销，列表要显示时长就必须读。
- **缩略图首次进入专辑时逐条生成**：每条约 100–300ms，滚到哪生成到哪，第二次进入直接读缓存。
- **SAF 权限可能被系统回收**：换机恢复、清理数据后目录会失效，此时扫描会报「目录未授权」，需要重新选择（阶段七会做引导重授权）。
