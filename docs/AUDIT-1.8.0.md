# 全量代码审计 + Android 16 / HyperOS 适配审计 · 1.8.0

```text
项目：/workspace/Compressor（JoshAtticus/Compressor fork）  生命周期：长期
规模：48 个 Kotlin 文件 / 约 12.8k 行 main + 1.2k 行 test / 8 个模块
批量策略：逐模块（总行数 > 3000）
基线：1.7.3（media3 1.11.1）
```

## 0. 结论摘要

- **Android 16 适配**：16KB page、edge-to-edge、预测性返回、前台服务类型/权限、通知权限、大屏方向都已就位；唯一的功能性缺口是 **Android 15+ `dataSync` 前台服务 6 小时上限**，1.8.0 已补 `onTimeout` 兜底。
- **代码审计**：本轮把 `CompressorViewModel` 从 2,583 行降到 2,383 行（抽出 `codec/CodecCapabilities`、`storage/MediaSaver`），修掉 1.7.1 报告里的 🔴「保存失败导致成品被删」和 4 个 🟡；剩余最大结构性问题是 ViewModel 仍然承担 6 类职责、批量状态机无测试。

---

## 1. Android 16+ / HyperOS 适配审计

| 项目 | 现状 | 证据 | 结论 |
|---|---|---|---|
| 16KB page size | 唯一的 `.so` 是 `libandroidx.graphics.path.so`，ELF `LOAD Align = 0x4000`；`zipalign -c -P 16 -v 4` 通过 | `readelf -lW`、`zipalign` | ✅ |
| edge-to-edge 强制 | `enableEdgeToEdge()` + 所有屏幕走 `Scaffold` / `innerPadding` | `MainActivity.kt`、`BatchScreen.kt`、`FileBrowserScreen.kt` | ✅ |
| 预测性返回 | `android:enableOnBackInvokedCallback="true"` + `PredictiveBackHandler` | `AndroidManifest.xml`、`CompressorApp.kt` | ✅ |
| 前台服务类型 | `android:foregroundServiceType="dataSync"` + `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | 清单 | ✅ |
| **FGS 6 小时上限（Android 15+）** | 1.7.3 之前未实现 `onTimeout`，超时会 ANR | — | **✅ 1.8.0 已补** |
| 通知权限（13+） | 批量开始时请求 `POST_NOTIFICATIONS`，拒绝也能跑 | `CompressorApp.kt` | ✅ |
| 大屏方向 | 手机强制竖屏，`sw600dp` 平板允许旋转；Android 16 在大屏上忽略方向限制，代码已按 `is_tablet` 分支 | `MainActivity.kt`、`values-sw600dp/bools.xml` | ✅ |
| 照片选择器 / SAF | 默认改为内置文件浏览器（`MANAGE_EXTERNAL_STORAGE`），照片选择器仍可选 | `FileBrowserScreen.kt` | ✅ |
| 全盘权限 | `MANAGE_EXTERNAL_STORAGE` 在 16 上行为不变；`Android/data`、`Android/obb` 仍不可列（已在浏览器里自然跳过） | `StorageAccess.kt` | ✅ |
| 后台启动 FGS | 只在用户点击「开始压缩」时启动，无后台拉起 | `startBatchCompression` | ✅ |
| MediaStore 写入 | `IS_PENDING=1` → 写完置 0，异常时删除 pending 行 | `MediaSaver.copyToGallery` | ✅ |
| 通知 Live Updates（16 新增 `ProgressStyle`） | 仍用普通进度通知 | `CompressionForegroundService` | 🔵 可选 |
| 屏幕常亮 | 全局 `FLAG_KEEP_SCREEN_ON` | `CompressorApp.kt` | ✅（但空闲时也常亮，🔵） |

### HyperOS（含 4.x / Android 16 底包）专项

| 问题 | 处理 |
|---|---|
| 「安全访问」文件选择器 | 已用内置文件浏览器 + 全盘权限绕开；MT 管理器「打开方式」也可用（file:// 全链路支持） |
| 后台冻结 / 杀进程 | `dataSync` 前台服务 + `PARTIAL_WAKE_LOCK`（批量时）+ 进度通知；README 里给出「自启动 / 省电策略无限制」设置路径 |
| 安装未知来源 | README 说明 |
| HyperOS 版本识别 | `ro.mi.os.version.name` 反射（失败回退 `Build.INCREMENTAL` 的 `V816`/`OSx.y` 匹配） |
| 联发科编码器 | VBR、禁 realtime priority、拒绝提示时无提示重试（`CodecCapabilities`） |
| 深色/主题 | 跟随系统（`values-night`），未使用 HyperOS 私有 API |

---

## 2. 全量代码审计

### 分诊表

| 模块 | 等级 | 依据 | 重点 |
|---|---|---|---|
| `viewmodel/CompressorViewModel.kt` | 🔴 | 2,383 行，仍承担状态、探测、预设持久化、文件名模板、导出编排、批量调度 6 类职责（codec/storage 已抽出） | 结构 + 功能 |
| `ui/screens/settings/DisplaySettingsScreen.kt` | 🟡 | 单函数 ~960 行（1,062 行文件） | 结构 |
| `ui/CompressorApp.kt` | 🟡 | 600 行，导航 + 8 个 launcher + 权限 + 分享 + 文件浏览器入口 | 常规 |
| `model/CompressorUiState.kt` | 🟡 | `autoAdjust()` ~190 行迭代；19 个测试覆盖 | 边界 |
| `codec/CodecCapabilities.kt` | 🟢 | 154 行，纯查询，无状态 | 保持 |
| `storage/MediaSaver.kt` | 🟢 | 140 行，三个目标统一返回 `String?` 错误 | 保持 |
| `service/` | 🟢 | 151 行 + `onTimeout`，异常全兜底 | 保持 |
| `utils/`（Hardware/Performance/File/Storage/Motion/Volume） | 🟢 | 纯函数为主，有测试 | 保持 |
| `ui/tabs/*`、`ui/components/*` | 🟡 | 直接持有 `viewModel`；个别硬编码英文 | 可读性/i18n |
| build / CI | 🟢 | 签名 secrets、测试门禁、签名校验 | 保持 |

### 🔴 阻断性问题

**1. `CompressorViewModel` 仍是多职责巨型文件（2,383 行）**

- 位置：`app/src/main/java/compress/joshattic/us/viewmodel/CompressorViewModel.kt`
- 现状：1.8.0 已抽出 `codec/CodecCapabilities.kt`（-154 行）与 `storage/MediaSaver.kt`（-140 行），但导出编排（`startCompression` 约 300 行）、预设/目标大小 JSON 持久化（约 400 行）、批量引擎（约 280 行）仍在同一个类里。
- 为什么是问题：`startCompression` 与批量引擎共享 `activeTransformer`/`pendingCompletion` 可变字段，任何一方改动都可能破坏另一方，且都无法单测。
- 修复方案（1.9.0）：按 1.7.1 报告继续拆 `export/ExportRunner`（导出管线 + 完成信号）与 `settings/SettingsStore`（JSON 持久化），批量引擎改为只依赖 `ExportRunner` 接口。**在改动导出逻辑前必须先做这一步。**

### 🟡 建议修复

**2. 批量状态机仍无测试**

- 位置：`runBatch / startBatchCompression / cancelBatch / finishBatch`
- 影响：取消、超时、保存失败三条路径只能靠真机验证。
- 方案：把队列推进抽成不依赖 Android 的 `BatchQueue`，用假 runner 覆盖这 3 条路径。

**3. `DisplaySettingsScreen` 单函数 ~960 行**

- 方案：拆 `OutputLocationSection` / `FilenamePatternEditor` / `DisplayTogglesSection`，纯计算移到 `FilenamePatternState`。

**4. 硬编码英文（i18n）**

- 位置（本轮已修一部分）：`InfoDialog`（"Are you sure?"/"Enable all codecs"/"Cancel"/"Compressor v…"）、`ResultScreen`（"N Warnings - Tap for Details"）、`PresetsSettingsScreen`（"$presetName Preset"、"Target Size Ratio"）。
- 剩余：`PresetsSettingsScreen` 其它对话框、`InfoCard` 少量拼接串。

**5. UI 层直接持有 ViewModel**

- 位置：`ConfigScreen(state, viewModel, context)`、`ui/tabs/*`
- 影响：Composable 与具体 ViewModel 强耦合，无法预览/测试；`viewModel.startCompression(context)` 还让 UI 决定导出时机。
- 方案：改为回调（`onStart: () -> Unit`、`onSetFps: (Int) -> Unit`），与 `BatchScreen`/`PerformanceScreen` 的写法保持一致。

### 🔵 可选优化

6. `CompressionForegroundService` 用普通进度通知；Android 16 可用 `NotificationCompat.ProgressStyle`（Live Updates）。
7. `FLAG_KEEP_SCREEN_ON` 全局常亮，建议只在压缩/批量进行时加。
8. `BatchLimits.WAKE_LOCK_TIMEOUT_MS` = 300 小时，可改为「按剩余条目数 × 6h」动态计算。
9. `CompressingScreen` 每次重组都读 `state.formattedCurrentOutputSize` 并对每个字符做 `AnimatedContent`，长文件名下略重（视觉需要，可保留）。

---

## 3. 1.8.0 已修复清单

| # | 问题 | 修复 |
|---|---|---|
| 1 | 保存失败走 `error` → 失败页 → 返回即 `reset()` 删成品（数据丢失） | 新增 `saveError` 独立通道；`ResultScreen` 显示「尚未保存：…」，保存按钮可重试；`reset()` 不再被保存失败触发 |
| 2 | 批量持有 Activity Context | `runBatch` 内部改用 `getApplication()`；前台服务也用 application context |
| 3 | `getTargetHeight` 两处重复实现 | 统一到 `CompressorUiState.heightForShortSide/shortSideForHeight`，`autoAdjust` 也复用 |
| 4 | 探测失败被静默吞掉，按 10MB 默认目标继续 | 无视频轨道时直接给出 `error_video_track_unreadable`，批量里标记该条失败 |
| 5 | `pendingCompletion` 完成后不清空 | `onCompleted`/`onError` 里条件清空 |
| 6 | `shareVideo` 的 `uri.path!!` | 改为 `?: return` |
| 7 | 三处重复的「开始压缩」按钮 | 抽出 `StartCompressionButton` |
| 8 | Android 15+ FGS 6 小时超时 | 实现 `onTimeout(startId, fgsType)`，停止前台并 `stopSelf` |
| 9 | 硬编码英文 | 新增 4 组字符串（中英），替换 InfoDialog / ResultScreen / PresetsSettingsScreen 的硬编码 |
| 10 | ViewModel 体积 | 抽出 `codec/CodecCapabilities`（154 行）与 `storage/MediaSaver`（140 行），2,583 → 2,383 行 |

## 4. 未决 / 下一批

1. `export/ExportRunner` + `settings/SettingsStore` 拆分（🔴 #1 的收尾）。
2. `BatchQueue` 单测（🟡 #2）。
3. `DisplaySettingsScreen` 拆分（🟡 #3）。
4. UI 去 ViewModel 化（🟡 #5）。
5. Android 16 `ProgressStyle` 通知（🔵）。
6. 真机验证清单见 `docs/PERFORMANCE.md` 末尾 + 本文 §1 中标注「待真机」的条目。
