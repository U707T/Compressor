<div align="center">
  <img width="96" alt="ic_launcher-playstore" src="assets/logo.png?v=2" />

# Compressor（个人 fork）

Lightning fast, ad free, native video compressor for Android — 本仓库是
[JoshAtticus/Compressor](https://github.com/JoshAtticus/Compressor) 的个人 fork，
在上游 1.7.0 的基础上加入了**批量压缩**、**硬件性能适配**、**内置文件浏览器**等能力。

![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84.svg?style=for-the-badge&logo=android&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue.svg?style=for-the-badge)

**当前版本：1.8.0**（`versionCode 33`）· `minSdk 24` · `targetSdk 37` · Media3 1.11.1

[下载 APK](https://github.com/U707T/Compressor/releases) · [更新日志](CHANGELOG.md) · [审计报告](docs/AUDIT-1.8.0.md)

<img src="assets/select.jpg" alt="Screenshot 3" width="24%"/> <img src="assets/settings.jpg" alt="Screenshot 1" width="24%"/> <img src="assets/compressing.jpg" alt="Screenshot 2" width="24%"> <img src="assets/done.jpg" alt="Screenshot 4" width="24%"/>
</div>

---

## 和上游的差异

| 能力 | 上游 1.7.0 | 本 fork 1.8.0 |
|---|---|---|
| 批量压缩 | ❌ | ✅ 最多 50 条队列、单条进度/错误、汇总页、取消与超时看门狗 |
| 后台压缩 | 仅前台运行 | ✅ `dataSync` 前台服务 + 进度通知 + 批量时 wake lock（澎湃 OS 后台冻结对策） |
| 文件选择 | 系统照片选择器 / SAF | ✅ 内置文件浏览器（全盘权限，绕开澎湃「安全访问」）+ 照片选择器 + 第三方应用选择器 |
| 硬件性能适配 | ❌ | ✅ SoC/GPU/核心/内存识别、三档性能模式、MediaTek 编码器策略、Media3 CodecDbLite |
| HDR → SDR | 仅 Pixel 10 | ✅ 所有设备在 H.264 目标下走 GPU tone map |
| 可变帧率（VFR）视频 | 崩溃（`Unexpected runtime error`） | ✅ Media3 1.11.1 + 帧率归一化 + 兼容模式重试 |
| 保存失败处理 | 与导出失败混用，返回即删成品 | ✅ 独立 `saveError` 通道，成品保留、可重试 |
| 签名与发布 | Play / IzzyOnDroid | ✅ GitHub Actions 签名发布（keystore 走 secrets） |

详细对照与可引进项见 [`docs/COMPRESSOREDGE-COMPARISON.md`](docs/COMPRESSOREDGE-COMPARISON.md)。

---

## 功能

**压缩**

- 目标大小 / 质量预设（高 / 中 / 低，可自定义并保存为默认）
- H.264 / H.265 / AV1（按设备硬件编码器能力显示，可解锁全部编码器）
- 分辨率、帧率、音频码率、音量、移除音轨
- 目标大小护栏（不会把文件压得比原文件更大，可开启「额外大小缓冲」）
- HDR 视频自动 tone map 到 SDR（GPU 路径）
- 删除原视频、分享、保存到相册 / 自定义 SAF 目录 / 系统文件选择器
- 文件名模板（原名 / 日期 / 时间 / 分辨率 / 帧率 / 码率 / 编码 / 预设 / 随机串）

**批量**

- 从内置浏览器多选、照片选择器多选、第三方应用多选，或直接多选分享到 Compressor
- 顺序执行（一次只开一路硬件编码器，避免联发科第二路 4K 会话失败）
- 每条独立状态、失败不中断队列、保存失败保留临时文件
- 结束给出总节省与失败清单

**性能**

- 自动识别 SoC / GPU / 核心数 / 最高频 / 内存 / HyperOS
- 性能模式：自动（旗舰与高端才激进）/ 最大 / 均衡
- 联发科：强制 VBR、不请求 realtime priority、编码器拒绝提示时无提示重试
- 编码器提示：`operating-rate`、`priority`、CodecDbLite、下一条预探测

---

## 权限说明

| 权限 | 用途 | 可以拒绝吗 |
|---|---|---|
| `MANAGE_EXTERNAL_STORAGE` | 内置文件浏览器直接读文件夹，绕开系统「安全访问」弹窗 | ✅ 拒绝后自动改用照片选择器 / 第三方选择器 |
| `READ_EXTERNAL_STORAGE`（≤ Android 12） | 旧系统上的同一能力 | ✅ |
| `READ_MEDIA_VIDEO` / `READ_MEDIA_VISUAL_USER_SELECTED` | 照片选择器模式下读取视频信息、删除原视频 | ✅ |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | 批量压缩时保活并显示进度通知 | ❌（不授予则长批量可能被系统中断） |
| `POST_NOTIFICATIONS` | 显示批量进度通知 | ✅ 拒绝只是看不到通知 |
| `WAKE_LOCK` | 批量时保持 CPU 唤醒 | ✅ |

> 没有任何联网权限（`ACCESS_NETWORK_STATE` 在清单里被显式移除），全部处理都在本机完成。

---

## 安装

1. 从 [Releases](https://github.com/U707T/Compressor/releases) 下载 `Compressor-<版本>.apk`（附 `SHA256SUMS.txt`）。
2. 本 fork 使用**独立签名**，无法覆盖安装 Google Play 版；两者共存需要改 `applicationId` 自行构建。
3. 首次选择视频时会跳到「所有文件访问权限」页面，授予一次即可；之后选视频就是普通的文件夹浏览。

### 澎湃 OS / 小米额外设置（建议）

- 设置 → 应用管理 → Compressor → 权限 → **所有文件访问**：允许
- 应用信息 → **自启动**：允许
- 应用信息 → 省电策略 → **无限制**
- 最近任务里给 Compressor **加锁**（避免一键清理）

> 以上是澎湃 OS 的后台管理项，不做也能用，但长批量更容易被系统冻结。

---

## 构建

环境：JDK 21、Android SDK（platform 37 / build-tools 37.0.0）、Gradle 由 wrapper 提供（9.9.1）。

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew testDebugUnitTest     # 单元测试（70 个）
./gradlew assembleDebug         # 调试包
./gradlew assembleRelease       # 发布包（需要签名配置，见下）
```

### 签名

本地发布签名放在仓库外的 `keystore.properties`（已被 `.gitignore` 忽略）：

```properties
storeFile=/absolute/path/to/release.jks
storePassword=******
keyAlias=compressor
keyPassword=******
```

也支持环境变量：`KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`。
两者都没有时 `assembleRelease` 会产出未签名包（不会失败），`assembleDebug` 不受影响。

### CI 发布

`.github/workflows/release.yml`：推送 `v*` tag（或手动触发）后依次执行

1. 接受 SDK 许可并安装 platform 37 / build-tools 37
2. 从 `KEYSTORE_BASE64` 解码 keystore（缺失则**硬失败**，绝不发未签名包）
3. `./gradlew testDebugUnitTest` —— 测试不过不发版
4. `./gradlew assembleRelease`
5. `apksigner verify --print-certs` 校验签名
6. 创建 GitHub Release 并上传 APK + `SHA256SUMS.txt`

需要的 secrets：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。

---

## 架构

```text
app/src/main/java/compress/joshattic/us/
├── MainActivity.kt                 入口、分享/多选分享接收
├── codec/CodecCapabilities.kt      MediaCodec 能力查询（硬件过滤、尺寸/帧率、MTK 判定）
├── storage/MediaSaver.kt           保存到 SAF 目录 / MediaStore / Uri（统一返回错误串）
├── service/
│   └── CompressionForegroundService.kt   dataSync 前台服务 + 进度通知 + onTimeout
├── model/                          UiState、预设、批量模型、搜索索引
├── utils/                          HardwareUtils、PerformanceTuning、StorageAccess、FileUtils…
├── viewmodel/CompressorViewModel.kt 状态编排、导出管线、批量引擎
└── ui/                             Compose 屏幕（Config/Batch/FileBrowser/Result/Settings…）
```

数据流：

```text
选择视频 → loadSelection(探测/尺寸/帧率) → buildCompressionPlan(编码器能力 + 回退)
        → Transformer 导出(性能调优 + HDR 处理) → 保存(SAF/相册) → 结果页
批量：prepareBatch → runBatch(逐条：loadSelection → 导出 → 保存) → 汇总
```

文档：

| 文档 | 内容 |
|---|---|
| [`docs/AUDIT-1.8.0.md`](docs/AUDIT-1.8.0.md) | 全量代码审计 + Android 16 / HyperOS 适配审计 |
| [`docs/AUDIT-1.7.1.md`](docs/AUDIT-1.7.1.md) | 首次全量审计（问题基线） |
| [`docs/PERFORMANCE.md`](docs/PERFORMANCE.md) | 批量引擎与性能调优实现说明、真机验证清单 |
| [`docs/COMPRESSOREDGE-COMPARISON.md`](docs/COMPRESSOREDGE-COMPARISON.md) | 与 CompressorEdge 的功能对照与引进路线 |
| [`CHANGELOG.md`](CHANGELOG.md) | 版本变更 |

---

## 已知限制

- 批量是**顺序**执行，不做多路并发编码（硬件编码器实例有限，联发科尤甚）。
- 单条导出 6 小时看门狗；前台服务在 Android 15+ 有 6 小时上限，超时会停止前台状态（导出继续，但可能被系统回收）。
- `Android/data`、`Android/obb` 即使有全盘权限也无法列出（Android 11+ 限制）。
- 未在 Android 17 真机验证；`targetSdk 37` 跟随上游。
- 本 fork 未上架 Google Play（`MANAGE_EXTERNAL_STORAGE` 受限）。

## 测试

```bash
./gradlew testDebugUnitTest
```

70 个单元测试：UI 状态与目标大小换算、文件名模板、音频处理器、硬件识别、性能调优、批量模型。批量引擎与导出管线目前依赖真机验证（见审计报告）。

---

## Credits

上游作者 [@JoshAtticus](https://github.com/JoshAtticus) 及 Compressor 的贡献者：
[@rA9stuff](https://github.com/rA9stuff)、[@tgranz](https://github.com/tgranz)、
[@sirtoaks](https://github.com/sirtoaks)、[@3r1s-s](https://github.com/3r1s-s)、
[@AhmedRX20](https://github.com/AhmedRX20)。

本 fork 的增量改动（批量、性能、文件浏览器、审计与发版流程）由 U707T 维护。

## License

MIT，见 [LICENSE](LICENSE)。Copyright (c) 2026 JoshAtticus。
