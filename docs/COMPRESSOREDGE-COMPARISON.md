# CompressorEdge 对照：重叠与可引进项

对照对象：`JoshAtticus/CompressorEdge`（main，2026-08-22 最后提交，media3 1.11.0）。
本仓库：Compressor fork 1.7.3（media3 1.11.1）。

## 功能矩阵

| 能力 | 本仓库 1.7.3 | CompressorEdge | 结论 |
|---|---|---|---|
| 批量压缩 | ✅ 顺序队列、前台服务保活、下一条预探测、单条进度/错误、汇总页 | ✅ 队列 + 每条参数覆盖 | 各有取舍；**每条参数覆盖值得引进** |
| 后台压缩 | ⚠️ 仅前台服务保活，导出仍在 ViewModel | ✅ 导出跑在 Service 内（`BackgroundCompressionService` + `CompressionExecutor` + `BackgroundCompressionManager`） | **应当引进**（也是审计 🔴 #1 的解法） |
| 启用全部编解码器 | ✅（1.7.0 上游已有） | ✅ | 已对齐 |
| 音频编码选择 | ❌ 只有码率/音量/移除 | ✅ AAC / Opus（Opus 走 `DefaultMuxer`） | 暂不引进：MP4+Opus 在相册/第三方播放器兼容性差 |
| 帧率降低 | `EditedMediaItem.setFrameRate` | `FrameDropEffect`（GL 效果） | 不引进：会把所有缩放导出强制推进 GL 管线，增加失败面 |
| 音频处理链 | `VolumeAudioProcessor` | `VolumeAudioProcessor + SonicAudioProcessor` | 不引进：无已知缺陷需要 Sonic，且多一层重采样开销 |
| HDR → SDR | ✅ 所有设备 H.264 目标走 GPU tone map | 仅 Pixel 10 | 本仓库更宽 |
| 联发科码率模式 | ✅ VBR + 禁 realtime priority + 拒绝提示时无提示重试 | ✅ 仅 VBR | 本仓库更细 |
| 设备性能档位 | ✅ SoC/GPU/核心/内存识别 + 三档性能模式 | ❌ | 本仓库独有 |
| 选择器 | ✅ 内置文件浏览器（全盘权限）/照片选择器/第三方 | ❌ 仅系统选择器 | 本仓库独有 |
| Media3 版本 | **1.11.1**（含 VFR 修复） | 1.11.0（受 `frame-rate=0` 崩溃影响） | 本仓库已修 |

## 可实际引进的项（按优先级）

### P1 · 后台压缩服务化（建议 1.8.0）

Edge 的做法值得照搬：

- `CompressionExecutor`：把「一次导出」抽成无状态对象，入参 `Params`（输入/输出/编码/分辨率/帧率/码率/音频），出参 `suspend fun` 或回调。
- `BackgroundCompressionManager`：进程级 `StateFlow` 桥，Service 写、ViewModel/UI 读。
- `BackgroundCompressionService`：`dataSync` 前台服务里跑 `executeSuspend`，通知显示进度，`ProcessLifecycleOwner` 决定通知形态。

收益：Activity 被回收/切后台/熄屏都不影响导出；同时天然解决审计里的「2,583 行 ViewModel」拆分（导出管线搬出去后 ViewModel 只剩编排）。

注意：Edge 自己的注释写着 "Experimental: results may vary across devices/OEMs"，引进时要保留我们现有的：
- 每条视频的 `CompletableDeferred` 完成信号（防止队列卡死）；
- 6 小时单条看门狗 + 取消即 complete；
- 保存失败保留临时文件。

### P2 · 每条视频的参数覆盖

Edge 的 `QueueItem` 用一组 `xxxOverride: T?`（null = 跟随全局）实现「这条视频单独调分辨率/帧率/目标大小/音频」。我们的 `BatchItem` 目前只有状态，没有参数。

引进方式：`BatchItem` 增加 `override: BatchOverrides?`，`loadSelection()` 之后套用覆盖值再 `autoAdjust()`；UI 在队列项上长按弹出编辑。工作量中等，收益明确（一批视频里混着 4K 和 720p 时很有用）。

### P3 · 音频编码选择（AAC / Opus）

只有在确认目标播放器支持 MP4+Opus 时才值得做；否则只是给用户一个「压完放不出来」的开关。建议先不做，或在设置里标注实验性。

## 明确不引进

1. **`FrameDropEffect` 替代 `setFrameRate`**：`FrameDropEffect` 是 `GlEffect`，只要加上就会把导出推进 OpenGL 管线（额外的 surface/GL 往返），对中低端机和 HDR 源都是新增失败面。我们保留 `setFrameRate`。
2. **`SonicAudioProcessor`**：没有证据表明它修复了我们遇到的音频问题；多一层处理器意味着更多的 buffer 与时间戳处理。
3. **`DefaultMuxer.Factory()` 强制替换 muxer**：只在 Opus 场景需要，AAC 场景没必要。

## 结论

- 重叠部分主要是「批量压缩」：我们 1.7.1 已实现，且多了前台服务、预探测、汇总、单条看门狗。
- 真正值得引进的是 **后台服务化架构**（P1）和 **每条参数覆盖**（P2）。
- 我们已经在三件事上领先：Media3 1.11.1（VFR 崩溃修复）、HDR tone map 覆盖所有设备、联发科编码器策略。
