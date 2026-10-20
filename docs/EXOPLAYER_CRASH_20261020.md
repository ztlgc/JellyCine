# ExoPlayer 点击播放后闪退（2026-10-20）

## 证据与原因

真实手机导出的 `jellycine_log_20261020_073637.txt` 包含四次相同崩溃，线程均为
`ExoPlayer:Playback`，异常均为 `java.lang.StackOverflowError`。设备报告为 HONOR
PTP-AN70、Android 17 / API 37，应用版本为 1.3.6 / 26。

日志中的 R8 map ID 与修复前 `phone/build/outputs/mapping/release/mapping.txt` 一致：
`e82b9ba18d44ebf9c6c45c343ed32d46aac2e338c522f28c76b3525da33736a3`。
重复帧 `nq.y` 对应 `AudioSink.configure` 的两个重载。原映射和崩溃节选保存在
`artifacts/exoplayer-crash-20261020/`。

`HardwareAcceleration.buildAudioSink()` 原先通过 Kotlin `AudioSink by sink` 包装
`DefaultAudioSink`，以禁止压缩音频直通和 offload，确保 PCM 音量增益生效。
这种委托没有为 Java 默认方法生成转发。Media3 1.11.1 的
`configure(AudioSinkConfig)` 与旧版 `configure(Format, int, int[])` 默认实现会相互调用；
包装类没有实现任何一个重载，开始配置音轨时便无限递归。

## 修复

将包装层提取为 `PcmOnlyAudioSink`，继承 Media3 的 `ForwardingAudioSink`。
配置及其他输出方法由该基类转发到实际的 `DefaultAudioSink`，保留 PCM 格式限制、
音频处理器和禁用 offload 的行为。

参考：[AudioSink](https://developer.android.com/reference/androidx/media3/exoplayer/audio/AudioSink)、
[ForwardingAudioSink](https://developer.android.com/reference/androidx/media3/exoplayer/audio/ForwardingAudioSink)。

## 验证

`PcmOnlyAudioSinkTest` 通过代理底层 AudioSink 调用实际 Media3 接口，检查新旧配置入口、
PCM 支持及禁止 offload。修复前四项测试中两项产生 `StackOverflowError`，对应真实日志的
递归调用链；原始失败报告保存在 `regression-before-fix.xml`。

修复后 core 35 项、phone 68 项测试全部通过，包含上述 4 项回归测试。
ARM64 正式包构建成功，记录为 `build-and-tests.log`，最终包检查结果写入 `validation.json`。
包信息为 `com.jellycine.app`、1.3.6 / 26、最低 API 27、target API 36；APK v2 签名通过，
证书与此前正式包一致，ZIP 完整性、16 KiB ZIP 对齐及 ARM64 原生库检查通过。

修复包：`artifacts/android/JellyCine_1.3.6_arm64_exoplayer-fix_20261020.apk`，
42,383,105 字节；SHA-256 为
`3a56f4306a9cf47dd17f28587674908c9b230fdaa1cb57b430cdb482c6052bb2`。
同目录提供 `.apk.sha256` 和 `.apk.validation.json`。

本轮不启动模拟器，未进行真实手机安装、实际输出及服务器播放验收。
此修复针对日志证实的闪退；此前播放中短暂停顿仍需独立记录缓冲和解码状态。
