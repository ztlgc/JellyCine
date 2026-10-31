# ExoPlayer 偶发卡顿诊断（2026-10-24）

## 当前能确认的情况

现有“设置 → 日志”可导出应用/logcat、MPV 和崩溃记录，但缺少 ExoPlayer 的连续缓冲、
掉帧、音频断供和加载状态。没有本次卡顿的真实手机日志，不能确定根因。
此前 AudioSink 递归崩溃修复仍保留；崩溃与短暂停顿需要分别判断。

普通 ExoPlayer 播放在 `PlayerUtils.createLoadControl()` 使用 500 ms 起播、1500 ms
重新缓冲恢复门槛；省电模式分别为 1500/5000 ms。普通缓冲目标为 15–120 秒，
省电及部分低内存设备上限为 30 秒，用户设置可进一步限制。
这些是目标/门槛，不能当作播放器实际已有的缓冲量，也不能据此断定卡顿原因。
媒体 HTTP 连接/读取超时为 30/120 秒，最少重试次数为 6；网络慢读可能没有立即报错。

## 本次加入的证据

- 每个 ExoPlayer 实例分配 session，记录生效缓冲参数、播放方式、原始码率、用户硬解/异步/省电设置。
- 使用 Media3 `AnalyticsListener` 记录解码器实际名称与系统报告的软/硬解属性、音视频格式、Surface 尺寸、首帧、掉帧、音频 underrun、解码/音频输出错误及播放错误。
- 记录加载错误的异常类型链、HTTP 状态、请求范围、已载入字节、耗时、重试次数及任务编号；周期汇总活动加载数量、最长等待时间和已完成加载。
- 每 5 秒在有播放意图时记录实际缓冲、位置、状态、倍速、渲染/掉帧计数、帧处理偏移、带宽估计及其样本年龄、网络类型、温控/省电状态和 Java 堆占用。
- 使用 `PlaybackStatsListener(keepHistory=false)` 区分起播、拖动、暂停和再缓冲，并记录再缓冲次数/时长及退出汇总。原始 `bufferingWallMs` 可能包括拖动或暂停，不直接作为卡顿计数。
- 正常播放状态下采样间进度几乎不前进时标为 `progress_stall_suspected`；它只是线索。5 秒采样不能单独证明每一次瞬时卡顿，画面单独停顿还需结合掉帧/渲染计数。
- 新增记录直接进入 AppLogger，同时通过单独 IO 线程写入应用私有目录，最多 3 × 512 KiB 循环保留，重启后仍可导出。导出读取排在待写事件之后，不依赖 logcat 是否已被系统覆盖。
- 导出文件增加版本号、构建类型、Media3 版本、API 等信息。新增持久化记录不写请求头、URL 用户信息/查询参数、影片路径、文件名或原始异常消息。

仅采集本地诊断；没有调整播放、缓存、解码、增益、转码或重试行为。内嵌预告片、远程预告片和主题音乐用 source 字段区分。

## 下次卡顿后

1. 使用包含本次改动的 APK，选择 ExoPlayer，照常播放。
2. 卡顿后尽快返回“设置 → 日志”导出 txt 文件；无需开启调试开关。
3. 同时说明大约发生时间、影片进度，声音是否继续、是否出现转圈、能否自行恢复。
4. 持久记录有容量上限，长时间继续播放会轮换覆盖较早记录；强制结束进程可能丢失尚未写入的最后几条事件。

`bufferedMs` 接近 0、加载久未完成、HTTP/超时错误是数据供给方向的线索；
缓冲充足但持续掉帧/帧处理偏移为负是解码/渲染方向的线索；
音频 underrun 是音频供给/调度方向的线索。任何单一指标都不直接证明网络、服务器或设备的最终根因。
带宽是估计值，已完成加载的字节也可能来自磁盘缓存；网络 validated=false 不表示局域网服务器一定不可达。

参考：[Media3 Analytics](https://developer.android.com/media/media3/exoplayer/analytics)、
[AnalyticsListener](https://developer.android.com/reference/androidx/media3/exoplayer/analytics/AnalyticsListener)。

## 验证边界

本地回归检查日志轮换、重开读取、敏感字段过滤，以及 ExoPlayer 回调到导出文件的连接和释放后停止采样。
这些验证不等于真实手机的视频流畅性、真实 Emby 服务器或实际音频输出验收；卡顿根因仍需复现日志确认。

2026-10-24：core 39 项、phone 88 项测试全部通过，ARM64 release 构建成功。
诊断 APK 为 `artifacts/android/JellyCine_1.3.6_arm64_exoplayer-diagnostics_20261024.apk`，
包名 `com.jellycine.app`、版本 1.3.6 / 26、min SDK 27、target SDK 36。
签名校验、16 KiB ZIP 对齐及 ARM64 单架构检查通过；大小 42,684,159 字节，SHA-256：
`641fd26018eec62474bb24038e5a2fa3abda669191d5f933ab25a732f2cc5864`。
构建/签名/包信息/对齐记录及对应 R8 mapping 保存在 `artifacts/exoplayer-diagnostics-20261024/`，
APK 同目录附 `.sha256` 和 `.validation.json`。

构建保留现有 Kotlin 弃用/可空表达式、Gradle 配置/弃用提示，以及 R8 的
`META-INF/services/javax.script.ScriptEngineFactory` 缺失服务引用警告；本次构建未因此失败。
