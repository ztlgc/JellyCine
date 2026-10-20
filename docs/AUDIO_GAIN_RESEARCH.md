# Android 播放音量增益研究（2026-10-15）

## 2026-10-18：再增加三档

新增 +3000%、+6000%、+12000%，分别为 31、61、121 倍幅度；两引擎共用最高档位上限，避免新档位仍被旧 16 倍限制。均衡额外补偿仍最多 4 倍，并与手动增益共用 121 倍总上限。浮点增益之后的最终峰值限幅保留。

低电平 PCM 测试覆盖全部档位、最高增益的峰值限幅和 EOS 样本保留；桌面 FFmpeg 7.1.1 的同参数滤镜输出测得新三档分别约 31.000000、61.000000、120.999999 倍，峰值输入限制到 0.96，均保留 48,000 样本。证据为 `artifacts/server-rating-20261018/host-higher-gain.json`。桌面滤镜测试、Android PCM 处理器单测和 MPV 滤镜配置测试各有边界，不能替代 Android MPV 真机链路、外放/耳机听感和设备安装验收。

## 2026-10-17：音量基线、PCM 测量与限幅

本轮按用户反馈“400% 有增益但仍明显弱于 Yamby”推进，先排除不同系统音量造成的比较偏差，再改进音频处理链。Yamby 的公开公告没有给出档位换算或 DSP 算法，不能把听感差异归结为已证实的算法差异。

- 默认跟随设备当前媒体音量，进入/退出播放器均不写系统音量。显式选择记住播放器音量的用户保留该行为；未保存音量时改用设备当前值作为起点。每次新音量手势从系统实际音量读取，写入后读回实际值，避免实体键调整后的旧状态覆盖。
- 保留关闭、30%、80%、150%、200%、400%，增加 800%、1500%。百分比仍表示额外采样幅度，最大为 16 倍，即约 +24.08 dB；界面同时显示 dB，旧保存值继续兼容。
- ExoPlayer 使用浮点中间计算、5 ms 前瞻、声道联动的包络限幅和 40 ms 档位渐变，输出仍为 PCM16。关闭增强时样本完全透传；前瞻缓冲在 EOS 排空，seek/flush 清空旧内容。
- 可选音量均衡默认关闭。对低于 -50 dBFS 的片段停止向上补偿，额外补偿最多约 +12 dB，手动与自动总补偿不超过 16 倍，最终限幅控制峰值。
- 输入/输出电平必须来自实际经过 DSP 的 PCM。ExoPlayer 使用已输出帧对应的输入样本测量，按 0.5 秒窗口更新 RMS/peak；切档后清空旧窗口，不把历史平均值当作当前效果。媒体信息面板和 `AudioGain` 日志显示测量结果；没有有效样本时保持等待状态。
- MPV 将增益移入浮点滤镜链并把后置 `volume-gain` 清零，避免限幅后再次放大。需要匹配现有依赖的原生滤镜库和命令返回码桥接，不能只写入原库不具备的滤镜名。

PCM 测量点位于 Android 系统媒体音量、厂商音效及扬声器之前；它验证数字处理，不等于真实扬声器响度，也不证明与 Yamby 的听感已经一致。设备验收需固定同一视频、同一音轨、同一输出设备与系统音量，分别测量/试听关闭、400% 与更高档位，并验证切轨、拖动、耳机/蓝牙切换。

MPV 使用同版 FFmpeg 9.0.1 补充库，保留原解码器与四种 Android ABI。编译来源、锁定的 AAR/源代码校验和、各 ABI 库校验和和重建命令见 `phone/native/mpv-audio-dsp/README.md` 与 `manifest.json`。Gradle 在生成本地增强 AAR 时检查这些校验和，不修改全局依赖缓存。

两侧 MPV astats 是独立的累计电平测量；若缓冲使输入与输出帧数不一致，界面只显示输出峰值，不把不同时间区间的 RMS 差当作增益。ExoPlayer 支持 40 ms 切档渐变，MPV 切档重建滤镜；两引擎的可选均衡算法不同，MPV 使用约 250 ms 分析窗。

本轮构建与测试证据目录为 `artifacts/audio-gain-20261017/`，数字滤镜样本测试记录为 `artifacts/mpv-audio-dsp-native/host-audio-validation.json`（桌面 FFmpeg 7.1.1，不执行 Android ELF）。正式 ARM64 APK 的签名、包信息、打包原生库哈希与校验和由同目录 `verify_apk.py` 检查，并写入 `validation.json`。下文为此前研究与实施的历史记录。

## 后续实施

用户确认采用关闭、30%、80%、150%、200%、400% 档位后，已按下面的研究方案修改：MPV 使用内置 `volume-gain`、设置 14 dB 增益上限并读回确认参数；ExoPlayer 使用同一幅度倍率。百分比仍表示额外增益，因此 400% 为 5 倍幅度。视频详情页总时长与剩余时长增加秒显示。

实施验证：`:core:testDebugUnitTest :phone:testDebugUnitTest :phone:compileDebugKotlin --offline` 成功，core 8 项、phone 47 项测试全部通过。直接调用已编译的时长格式化函数，45 秒显示 `0:45`、3723 秒显示 `1:02:03`，原分钟格式保持不变。记录见 `artifacts/audio-gain-presets-20261015/validation.json`；未生成新 APK，也未进行真机播放验收。

以下章节保留研究时的缺陷、依据与验收边界。原生库仍缺少 `volume` 滤镜，新代码已避开该依赖；真机实际输出仍需验收。

## 结论

可以在视频播放时放大原本音量较小的音轨：音频先解码为 PCM，再对采样幅度施加软件增益，最后送入 Android 音频输出。只调系统媒体音量或 ExoPlayer 的普通 `volume`，不能代替超过原始满幅的增益。

当前默认 MPV 路径存在明确的依赖能力不匹配：代码把 MPV 内置 `volume-gain` 设为 0，随后尝试添加 FFmpeg `volume` 滤镜；实际 Android 依赖编译时使用了 `--disable-filters`，没有该滤镜。这可以解释当前实现为什么无法产生预期放大。检查覆盖缓存 AAR 的四种 ABI，以及三个已有 APK 的 ARM64 原生库。

建议先改用当前 MPV 自带的 `volume-gain`，保留 ExoPlayer 已有的 PCM 处理器，并补上失败反馈及实际输出验证。需要自动限制峰值、改善较大增益的音质时，再为 MPV 重编包含所需滤镜的原生库。

本次完成研究和验证，未修改播放器逻辑、未生成新 APK。

## 实际检查结果

### 1. 点击路径已经接通，失效发生在更下游

- 默认引擎是 MPV：`core/src/main/java/com/jellycine/player/preferences/PlayerPreferences.kt:135`。
- 选择档位后，`PlayerViewModel.setAudioGainPercent()` 会保存偏好并调用引擎实现：`phone/src/main/java/com/jellycine/app/ui/screens/player/PlayerViewModel.kt:1048`。
- MPV 实现在 `phone/src/main/java/com/jellycine/app/player/mpv/MpvPlayerController.kt:213`。它先执行 `volume-gain=0`，非零档位再执行 `af add @jellycine_gain:lavfi=[volume=倍率]`。
- ExoPlayer 实现直接乘 PCM 采样，位于 `core/src/main/java/com/jellycine/player/audio/AudioGainProcessor.kt:28`；`HardwareAcceleration.kt:24` 注入该处理器、关闭 float 旁路，并拒绝压缩格式直通和 offload。

因此，在默认 MPV 下，仅修正 ExoPlayer 的 PCM 处理器不会解决问题。

### 2. 当前 Android 原生库没有所调用的滤镜

实际依赖是 `org.jellycine.mpv:libmpv:1.0.4`，内含 MPV 版本字符串 `mpv v0.41.0`。对 AAR 中 ARM64、ARMv7、x86、x86_64 的 `libavfilter.so` 逐一检查：

- 内嵌 FFmpeg 编译参数都包含 `--disable-filters`，未发现启用 `volume` 的参数。
- 都没有 `volume`、`alimiter`、`acompressor`、`dynaudnorm` 的精确名称字符串，也没有 `ff_af_volume`、`ff_af_alimiter` 符号标记。
- 能找到 `abuffer`、`abuffersink`、`aresample`，说明存在 `libavfilter.so` 并不代表包含完整滤镜集合。
- ARM64 `libavfilter.so` 仅 138,048 字节，SHA-256 为 `30db27351cf28c2f2961446017eaaa806f24e8fbab6b33fc3c0a66424c2977a5`。

三个现有 APK 的 ARM64 `libavfilter.so` 与该依赖哈希完全相同：

| APK（位于 artifacts） | DEX 中的增益实现标记 | 原生 volume 滤镜 |
| --- | --- | --- |
| `JellyCine_1.3.6_arm64_release.apk` | 未检出 `AudioGainProcessor` 或命名增益滤镜标记；release 可能经过混淆，不能据此断定功能是否存在 | 缺失 |
| `JellyCine_1.3.6_arm64_playback-ui_debug.apk` | `LoudnessEnhancer`，未检出命名增益滤镜 | 缺失 |
| `JellyCine_1.3.6_arm64_video-actions_debug.apk` | `AudioGainProcessor` 与 `jellycine_gain` | 缺失 |

最新这份调试包包含新的滤镜调用代码，但没有包含它依赖的滤镜实现。此前桌面 FFmpeg 正弦波的 RMS 验证只证明了桌面滤镜的数学效果，遗漏了 Android 二进制依赖的能力检查。当前没有连接设备，无法确定用户实际安装的是哪份 APK。

### 3. 命令失败没有可靠返回到应用层

缓存 AAR 的 `MPVLib.command()`、`setPropertyDouble()` 都返回 `void`。对实际 ARM64 `libplayer.so` 中 `nativeCommand` 的反汇编确认：调用 `mpv_command` 后未检查返回寄存器中的错误码。对应上游 JNI 源码也直接丢弃 `mpv_command` 返回值。

因此，Kotlin 的 `runCatching` 无法依靠异常捕获这种命令失败。现有实现还有两点不足：

- 在调用前就更新 `appliedGainPercent`，失败后相同档位会被当作已经应用，阻碍重试。
- `audioGainAvailable` 在 MPV 侧依赖滤镜标签和输出格式，在 ExoPlayer 侧仅检查对象存在；这些都不是实际音频输出变大的证明。

是否显示不可用取决于运行时属性返回，静态检查不能替代设备日志。这里不能断言所有失败都会在界面上显示为成功。

### 4. 当前档位偏保守，而且硬截幅仍可能失真

现有百分比表示采样幅度额外增加，换算为 `20 × log10(倍率)`：

| 档位 | 幅度倍率 | 增益 |
| --- | --- | --- |
| +10% | 1.10 | +0.83 dB |
| +20% | 1.20 | +1.58 dB |
| +30% | 1.30 | +2.28 dB |
| +50% | 1.50 | +3.52 dB |
| +70% | 1.70 | +4.61 dB |

即使正确执行，+10% 的变化也较小，+70% 不表示主观听感增加 70%，也不足以补偿所有低音量素材。

当前 ExoPlayer 用 `coerceIn(Short.MIN_VALUE, Short.MAX_VALUE)` 防止整数溢出，但超过范围的峰值会被硬截断；这是削波保护边界，不是平滑限幅器。较大增益需要同时考虑峰值处理。

## 推荐实施方案

### 第一步：用现有依赖恢复可用的手动增益

1. MPV 移除失效的命名 `lavfi volume` 路径，使用内置 `volume-gain`，单位为 dB。当前库包含该选项字符串；对应 MPV v0.41.0 源码会把它换算成 `10^(dB/20)`，通过音频输出层对 PCM 采样执行软件乘法，不依赖 FFmpeg `volume` 滤镜。
2. 增益独立于普通音量，避免用 `setVolume()` 冒充增益；MPV v0.41.0 的普通 `volume` 使用立方映射，不能直接把 200 当成 2 倍采样幅度。只提高 `volume-max` 也不会主动放大。
3. ExoPlayer 保留 PCM 处理链，在同一 dB 档位下采用相同倍率。继续确认不会走 float、passthrough、offload 旁路。
4. 若保留现有百分比 UI，MPV 应写入 `20 * log10(1 + percent / 100.0)` dB；现有 `gainMillibels(percent) / 100.0` 也可换算，但会带少量整数舍入。
5. 更直观的备选档位为关闭、+3、+6、+9、+12 dB，默认关闭。+6 dB 约为 2 倍幅度，+12 dB 约为 4 倍幅度。需要统一调整状态、偏好、旧值迁移和界面；不能只替换档位列表，因为 `validGain()` 会把不认识的值还原为 0。
6. 读取属性确认设置被接受，在音频就绪/重建时恢复，但不能把读回成功当成响度验收。若调整原生包装层，应把 `mpv_command`/`mpv_set_property` 返回码传回应用；应用只在确认成功后缓存已应用值，并保留失败重试和明确反馈。
7. 对压缩音频直通应切回可处理的 PCM，或明确报告该输出路径不支持增益；不能显示已生效。

这是建议的最小修复路径。内置增益有软件实现依据，但在此项目实际 Android 输出上的效果仍需后述验证；单靠属性写入不能宣布问题解决。

### 第二步：需要较大增益与更好音质时加入限幅

原生 `volume-gain` 本身不是自动响度均衡器。若原始峰值已接近满幅，直接放大仍会削波。

- MPV 可重编匹配的 FFmpeg/libmpv，显式包含 `volume`、`alimiter` 及格式协商需要的滤镜，再做“增益 → 限幅”。例如 +6 dB 的候选链：`@jellycine_gain:lavfi=[volume=1.995262,alimiter=limit=0.95:level=false:latency=true]`。这是重编后的待验证候选，不适用于当前裁剪库；须确认版本支持相关选项并验证实际总增益、延迟与音画同步。
- `alimiter` 默认有自动补偿输出电平的行为，候选方案显式关闭 `level`，避免把限幅阈值又自动拉回。它不是严格的过采样真峰值保证。
- ExoPlayer 在 PCM 增益后实现带包络、声道联动的限幅器，避免只依靠逐采样硬截断；处理新增延迟，维持音画同步。
- “整段录音音量偏小”优先用固定增益；“对白小、爆炸声大”再考虑压缩或动态响度功能。后者会改变动态范围、可能放大底噪，应单独设计和验证。

### 为什么不优先回到 LoudnessEnhancer

Android `LoudnessEnhancer` 绑定特定 AudioTrack/MediaPlayer 的音频会话，使用 millibel 设置目标增益（100 mB = 1 dB），平台会对超出范围的信号压缩。它是一条可用路线，但正确绑定会话、会话重建以及具体音频输出路径均需要验证。

本项目有 MPV 与 ExoPlayer 两套引擎。当前 MPV 包装层没有直接暴露 AudioTrack 会话 ID，单独给 ExoPlayer 添加该效果不能覆盖默认播放路径。已有 PCM 增益和 MPV 内置软件增益更适合先把这项功能做实。

## 验证与验收

本次已执行：

- 静态检查实际 AAR 的四种 ABI，并对照三个已有 APK 的 ARM64 原生库哈希。
- 检查调用路径、档位换算、JNI 方法签名和 ARM64 命令桥接反汇编。
- 执行 `.\gradlew.bat :core:testDebugUnitTest --tests com.jellycine.player.audio.AudioGainProcessorTest --offline --console=plain`，3 项测试通过：不同档位实际改变采样、播放中切换/关闭、峰值不发生整数溢出。
- `adb devices` 为空，未进行 Android 实际输出、听感或真实服务器验收。

后续修复的验收应覆盖：

1. 使用同一段低电平测试音轨，在固定普通音量下比较关闭/+6/+12 dB；在不发生限幅时，数字采样 RMS 应接近 1:2:4。启用限幅或外放保护后，应按实际处理链解释偏差。
2. MPV 与 ExoPlayer 分别验证，采集尽可能靠近输出的数字音频。设备支持 AudioPlaybackCapture 时需确认捕获点和应用捕获权限；也可使用音频回环。外放录音若启用自动增益不适合当作精确倍率证据。
3. 真机实际播放较安静的视频，比较扬声器与耳机输出，确认可听到放大；同时检查高峰值素材的削波和失真。
4. 切音轨、拖动、暂停/恢复、重开视频、切换引擎后仍保持正确档位；关闭后恢复基线，不发生滤镜叠加或状态假成功。
5. 验证 PCM、直通/不支持路径以及命令失败后的反馈和重试。编译、算法测试、属性读回与真机输出分别记录。

研究证据位于 `artifacts/audio-gain-research-20261015/`：`dependency-audit.json`、可重复运行的 `inspect_audio_gain.py`、3 项测试 XML、`nativeCommand-disassembly.txt` 及查阅的上游源码快照。该目录已由现有 `.gitignore` 忽略，研究结论保存在本文件中。

## 依据

- [MPV 音量、volume-gain 与音频滤镜手册](https://mpv.io/manual/master/#audio)：明确内置增益使用 dB，并独立于普通音量。
- [MPV v0.41.0 player/audio.c](https://github.com/mpv-player/mpv/blob/v0.41.0/player/audio.c#L119) 与 [audio/out/ao.c](https://github.com/mpv-player/mpv/blob/v0.41.0/audio/out/ao.c#L613)：对应缓存库版本的软件增益计算与 PCM 乘法实现。
- [libmpv-android JNI 命令桥接](https://github.com/sureshfizzy/libmpv-android/blob/main/libmpv/src/main/cpp/main.cpp#L107)：读取的是上游 main，实际依赖另以缓存 AAR 签名及 ARM64 反汇编交叉确认。
- [Media3 DefaultAudioSink.Builder](https://developer.android.com/reference/androidx/media3/exoplayer/audio/DefaultAudioSink.Builder)：处理器面向 PCM，直通和 offload 不支持这条处理链。
- [Android LoudnessEnhancer](https://developer.android.com/reference/android/media/audiofx/LoudnessEnhancer)：音频会话、millibel 单位与压缩行为。
- [FFmpeg volume](https://ffmpeg.org/ffmpeg-filters.html#volume)、[alimiter](https://ffmpeg.org/ffmpeg-filters.html#alimiter)、[dynaudnorm](https://ffmpeg.org/ffmpeg-filters.html#dynaudnorm)：滤镜能力及电平/动态范围处理。

网页版手册用于核对公开机制；关于项目缺失滤镜的结论来自实际二进制检查，不是根据上游默认构建推断。
