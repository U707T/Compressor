# 全量代码审计 · Compressor fork 1.7.1

```text
项目：/workspace/Compressor（JoshAtticus/Compressor fork）  生命周期：长期（上游活跃 + 本 fork 持续演进）
规模：46 个 Kotlin 文件 / 13,640 行 main + 1,221 行 test / 约 7 个模块
批量策略：逐模块分批（总行数 > 3000）
```

## 分诊表

| 模块 | 等级 | 依据（文件 / 行数 / 结构特征） | 重点 |
|------|------|------------------------------|------|
| `viewmodel/` | 🔴 | `CompressorViewModel.kt` 2,583 行，承担 UI 状态、轨道探测、编解码能力查询、预设持久化、文件名模板、导出管线、保存、批量调度共 8 类职责 | 功能 Bug + 结构 |
| `ui/CompressorApp.kt` | 🟡 | 588 行，导航状态、6 个 ActivityResult launcher、权限请求、分享、批量入口混在一个 composable | 常规五轴 |
| `ui/screens/settings/` | 🟡 | `DisplaySettingsScreen.kt` 1,072 行、`DisplaySettingsScreen()` 单函数约 960 行；`PresetsSettingsScreen` 756 行 | 结构 |
| `ui/screens/`（其余） | 🟡 | `BatchScreen` 515 行（新）、`ResultScreen`/`ConfigScreen` 未深读 | 常规五轴 |
| `model/` | 🟡 | `CompressorUiState.autoAdjust()` 约 200 行 + 20 次迭代循环，隐含不变量多 | 边界情况 |
| `utils/` | 🟢 | `HardwareUtils` / `PerformanceTuning` / `FileUtils` 职责单一，新代码纯函数化并有测试 | 保持 |
| `service/` | 🟢 | `CompressionForegroundService` 151 行，单一职责，异常全部兜底 | 保持 |
| build / CI | 🟢 | 签名走 secrets，测试门禁 + 签名校验后才发版 | 保持 |

**覆盖说明（不假装全量已读）**：完整读过 `CompressorViewModel`、`CompressorUiState`、`CompressorApp`、`HardwareUtils`、`PerformanceTuning`、`BatchModels`、`BatchScreen`、`PerformanceScreen`、`CompressionForegroundService`、`MainActivity`、`EmptyScreen`、`WhatsNewDialog`、`FileUtils`（约 60%）、`SettingsScreen`/`SearchableSetting`（约 50%）、清单与构建脚本、全部 workflow。
**未深读**：`ConfigScreen`、`ui/tabs/*`、`DisplaySettingsScreen`、`PresetsSettingsScreen`、`Video/AudioSettingsScreen`、`AboutScreen`、`LicensesScreen`、`ResultScreen`、`CompressionFailedScreen`、`ui/components/*`、`theme/*`、`VolumeAudioProcessor`、`TargetSizePreset`。下一批建议从这些开始。

---

## 🔴 阻断性问题

### 1. `CompressorViewModel.kt` 是 2,583 行的多职责巨型文件

- **位置**：`app/src/main/java/compress/joshattic/us/viewmodel/CompressorViewModel.kt:1-2583`
- **问题**：同一个类里同时存在 8 类变更理由：① `MutableStateFlow` UI 状态与偏好读写；② `MediaExtractor`/`MediaMetadataRetriever` 探测；③ `MediaCodecList` 能力查询（`hasEncoder`/`isCodecConfigurationSupported`/`isBitrateModeSupported`）；④ 预设与目标大小的 JSON 持久化（约 400 行）；⑤ 文件名模板序列化；⑥ `Transformer` 导出管线；⑦ 保存到 SAF / MediaStore / Uri；⑧ 新增的批量调度。
- **为什么是问题**：任何一处改动（例如新增一种编码器回退）都要在 2.5k 行里定位；批量引擎与导出管线共享可变字段（`activeTransformer`、`pendingCompletion`），无法单独测试，也无法单独演进。
- **修复建议**：按变更理由拆成 5 个文件，`CompressorViewModel` 只保留编排（目标 ≤ 600 行）：
  - `codec/CodecCapabilities.kt`：③ 全部查询 + `MediaCodecInfo` 缓存；
  - `codec/CompressionPlanner.kt`：`TrackProbe` → `CompressionPlan`（含 H.264/分辨率回退），纯函数，可单测；
  - `export/ExportRunner.kt`：⑥ 的 `Transformer` 构建与回调，暴露 `suspend fun run(plan, settings): ExportResult`；
  - `storage/MediaSaver.kt`：⑦ 三个保存目标（已在 1.7.1 合并为 `copyToUri/copyToTree/copyToGallery`，只需搬走）；
  - `settings/SettingsStore.kt`：④⑤ 的 JSON 读写。
  批量引擎留在 ViewModel 或独立 `batch/BatchController.kt`，只依赖上面四个接口。

### 2. 保存失败会走进"压缩失败"界面，返回即删除成品（数据丢失）

- **位置**：`CompressorViewModel.runSave()`（`CompressorViewModel.kt:2564`）设置 `error`；`CompressorApp.kt:537` 的 `state.error != null -> CompressionFailedScreen`（入口判定在 `:475`）；`CompressionFailedScreen` 的返回按钮 `onBack = { viewModel.reset() }`；`reset()` → `clearCache()` 删除 `cacheDir/compressed_videos/*`。
- **问题**：`error` 一个字段同时表示"导出失败"和"保存失败"。压缩成功但写入相册/SAF 失败时，UI 会切到失败页；用户点返回 → `reset()` → 成品被删掉，既没保存也没法重试。
- **为什么是问题**：真实数据丢失路径，且触发条件（相册写入失败、SAF 权限被回收、存储满）在 HyperOS 上并不罕见。
- **修复建议**：把"保存错误"从 `error` 拆出来（`saveError: String?`），失败页只在 `exportError != null` 时出现；保存失败时停留在 `ResultScreen`，提供"重试保存 / 换个位置 / 分享"三个动作，并且 `reset()` 在 `compressedUri != null && !saveSuccess` 时不要 `clearCache()`。

---

## 🟡 建议修复

### 3. 批量引擎完全没有测试覆盖

- **位置**：`CompressorViewModel.runBatch/startBatchCompression/cancelBatch/finishBatch`（`CompressorViewModel.kt:1567-1780`）。
- **问题**：新增约 280 行状态机（PENDING→PREPARING→COMPRESSING→SAVING→DONE/FAILED/CANCELLED + 取消 + 超时 + 保存回退），只有 `BatchModels`（纯数据）有 5 个测试，引擎本身 0 覆盖。
- **为什么是问题**：这是 1.7.1 最复杂的新逻辑，也是最容易在真机上出现"卡住/重复压缩/丢文件"的地方；一旦回归只能靠用户反馈发现。
- **修复建议**：把队列推进逻辑抽成不依赖 Android 的 `BatchQueue`（输入 `List<BatchItem>` + `suspend (BatchItem) -> BatchResult`），用假 runner 覆盖：单条失败继续下一条、取消后剩余项标 CANCELLED、保存失败保留 temp、超时触发取消。

### 4. `DisplaySettingsScreen` 单函数约 960 行

- **位置**：`app/src/main/java/compress/joshattic/us/ui/screens/settings/DisplaySettingsScreen.kt:96-1059`。
- **问题**：一个 `@Composable` 里塞了显示选项、输出位置、文件名模板编辑器、拖拽排序、预览等全部内容；`findClosestSegmentIndex` 这类纯逻辑也被埋在文件末尾。
- **为什么是问题**：改任意一个设置项都要在千行函数里滚动；Compose 的重组范围也难以判断。
- **修复建议**：按区块拆成 `OutputLocationSection`、`FilenamePatternEditor`、`FilenameTokenChips`、`DisplayTogglesSection`，纯计算（最近插入点、token 命中）移到 `FilenamePatternState` 并加单测。

### 5. 批量期间持有 Activity Context

- **位置**：`CompressorApp.kt` 传入 `LocalContext.current`（Activity）→ `prepareBatch/startBatchCompression/loadSelection/runBatch/persistBatchOutput`；`CompressionForegroundService.start(context)` 同样用 Activity context。
- **问题**：一次批量最长可达数小时，协程闭包持有 Activity 引用；`singleTask` + 竖屏锁降低了风险，但 ViewModel 生命周期长于 Activity，属于不该保留的引用。
- **为什么是问题**：配置变更或 Activity 重建时可能泄漏；Service 也不需要 Activity context。
- **修复建议**：ViewModel 内部统一用 `getApplication<Application>()`（`ContentResolver`、`getExternalFilesDir` 都是进程级的），对外 API 去掉 `Context` 参数；Service 用 `applicationContext`。

### 6. 探测/读取失败被静默吞掉，用户看到的是"默认 10MB 目标"

- **位置**：`CompressorViewModel.loadSelection()` 的 `catch (e: Exception) { e.printStackTrace() }`；失败后 `size = 0` → `defaultTargetMb = 10f`。
- **问题**：14 处 `printStackTrace()`（全项目），其中探测与保存路径上的吞异常会让 UI 显示看似正常但语义错误的数据（目标大小、时长、码率全为默认值）。
- **为什么是问题**：用户无法区分"视频只有这么点信息"和"读取失败"，批量时更难发现。
- **修复建议**：探测失败时设置一个 `probeFailed` 标记并在配置页提示"无法读取视频信息，将使用默认参数"；`printStackTrace` 换成带上下文的日志入口，批量失败项已经能显示错误，单条路径也应如此。

### 7. 分辨率换算逻辑重复两份

- **位置**：`CompressorViewModel.updateSelectedUri()` 内 `fun getTargetHeight()`（`:472`）与 `applyPreset()` 内同名同实现（`:546`）。
- **问题**：竖屏/横屏短边换算复制了两份，规则一旦调整（例如新增 1440p）要改两处。
- **修复建议**：移到 `CompressorUiState.shortSideToHeight()` / `heightToShortSide()`，两处调用同一实现，并补 1:1、9:16、21:9 的测试。

---

## 🔵 可选优化

### 8. `autoAdjust()` 的 20 次迭代循环

- **位置**：`CompressorUiState.autoAdjust()`（`CompressorUiState.kt:265-400`）。
- **问题**：靠"改一点 → 重新算 minimumSizeMb → 再判断"的循环逼近，`maxAttempts = 20` 是经验值；已有 19 个测试，但都是场景级断言。
- **建议**：保留行为，补一个性质测试（任意 target ≥ 最小可行值时，结果 `minimumSizeMb <= target`；任意 target 时循环必然终止），把不变量钉死。

### 9. `shareVideo` 的 `!!`

- **位置**：`CompressorApp.kt:280` `File(uri.path!!)`。
- **问题**：当前只对缓存里的 `file://` 调用，实际不会为空；但这是 UI 层唯一的非空断言，一旦未来支持 content:// 分享就会崩。
- **建议**：`uri.path ?: return`，并给用户一个 Toast。

### 10. `pendingCompletion` 完成后未清空

- **位置**：`CompressorViewModel.startCompression()` 的 `pendingCompletion = done`。
- **问题**：导出结束后仍持有已完成的 `CompletableDeferred`（无大害，但与 `activeTransformer` 的清理不对称）。
- **建议**：`onCompleted/onError` 里 `if (pendingCompletion === done) pendingCompletion = null`。

### 11. 三处 `startCompression(context)` 调用完全一致

- **位置**：`ConfigScreen.kt:178 / 298 / 417`（手机/平板/紧凑布局三个分支）。
- **问题**：三份相同的调用 + 相同的错误处理入口，改一处容易漏。
- **建议**：提到 `ConfigScreen` 顶部一个 `onStart` lambda，三个布局共用。

---

## ✅ 做得好的地方

- **`PerformanceTuning` 是纯函数**：`resolve()` / `shouldToneMap()` 不碰 Android API，7 个单测覆盖 MTK/高通/低端/边界 fps；这是本次改动里最容易腐烂的部分，却最稳。
- **批量限制集中管理**：`BatchLimits`（50 条、6h/条、wake lock 上限）只有一个来源，UI 与引擎引用同一常量。
- **保存管线统一**：`copyToUri/copyToTree/copyToGallery` 三个实现替代了原先三份几乎相同的代码，且 MediaStore 写入失败会删除 pending 行。
- **导出入口有兜底**：`transformer.start()` 包了 try/catch（原先会直接崩），编码器拒绝 `operating-rate/priority` 时会先降级重试再走 VBR。
- **签名与密钥纪律**：keystore 在 `/workspace/Compressor-keys`（仓库外），`.gitignore` 覆盖 `*.jks/*.keystore/keystore.properties`，仓库内无明文凭据；CI 缺 secret 时硬失败而不是发未签名包。
- **发版链路有门禁**：release workflow 先跑 `testDebugUnitTest`，再用 `apksigner verify --print-certs` 校验签名，最后才上传产物。

---

## 跨模块观察

1. **"是不是联发科"有三套实现**：`HardwareUtils.getDeviceWorkarounds()`、`HardwareUtils.detectDeviceProfile()`（新增）、`CompressorViewModel.isMediaTekDeviceOrEncoder()`。三者判定口径不完全一致（第三个还会看编码器名字）。建议统一到 `DeviceProfile.isMediaTek`，`isMediaTekDeviceOrEncoder` 只作为"编码器名兜底"的补充。
2. **错误通道有四个**：`error`、`errorLog`、`deleteOriginalError`、`BatchItem.error`，语义与生命周期各不相同（见 🔴 2）。建议引入 `sealed class UiError { Export, Save, Probe, Delete }`，UI 按类型决定展示位置。
3. **两套"压缩计划"类型**：ViewModel 私有的 `CompressionPlan`（编码格式/分辨率/帧率/警告）与 `utils.EncoderTuning`（性能提示）。二者是不同维度，可以保留，但 `CompressionPlan` 目前是私有嵌套类，无法单测——与 🟡 3 的拆分一起解决。

## 下一批建议

`ui/screens/settings/`（`DisplaySettingsScreen` 优先）→ `ui/tabs/*` + `ConfigScreen` → `ui/screens/ResultScreen` + `CompressionFailedScreen`（与 🔴 2 的修复一起做）。
