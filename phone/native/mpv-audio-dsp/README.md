# MPV 音频处理原生补充库

本目录只替换 `org.jellycine.mpv:libmpv:1.0.4` AAR 中的 `libavfilter.so`，保留原 AAR 的 MPV、解码器、libavutil、libswresample 和四种 Android ABI。原库 FFmpeg 是 **9.0.1**，但编译参数 `--disable-filters` 去掉了增益和限幅滤镜。

`jni/<ABI>/libavfilter.so` 从同版 [FFmpeg 9.0.1 源码](https://ffmpeg.org/releases/ffmpeg-9.0.1.tar.xz) 编译，补充 `aformat`、`volume`、`alimiter`、`dynaudnorm`、`astats`，保留 abuffer/abuffersink/aresample。没有改动 FFmpeg 的滤镜算法。与原依赖一致启用 GPL/version3；许可证见 `COPYING.GPLv3`。原源码和配置、完整日志缓存于仓库忽略的 `artifacts/mpv-audio-dsp-native/`。

`libjellycine_mpv_audio.so` 是项目自己的薄 JNI 桥，返回 `mpv_command` 的真实错误码并读取滤镜 PCM 统计。原包装器的 `nativeCommand` 返回 void，会吞掉错误。桥只适用于精确锁定的 AAR：构建脚本校验 AAR、FFmpeg 源码和 MPV 0.41.0 头文件的 SHA-256。MPVInstance 的首字段是 `mpv_handle*`，通过保留字段名的 `nativeInstance` 取得；升级 AAR 必须重新审查该结构，不能绕过校验。Controller 对设置、统计和释放使用同一监视器锁；释放后桥不再被调用。

## 处理与边界

- 输入 astats → 浮点格式 → 可选均衡 → 固定倍率增益 → 声道联动限幅 → 输出 astats。两个统计器是独立的 MPV 命名节点，可分别读取 `af-metadata/jellycine_gain_input` 和 `af-metadata/jellycine_gain_output`。
- 限幅阈值 0.96（约 -0.35 dBFS），lookahead 5 ms，release 150 ms，关闭自动输出补偿，并开启 FFmpeg 延迟补偿；MPV 后置 `volume-gain` 固定为 0，避免限幅后再次放大。关闭增益且关闭均衡时跳过限幅。
- 可选 dynaudnorm 采用 f=50 ms、g=5 的约 250 ms 窗，声道联动、目标 RMS 0.125、输入噪声门槛约 -50 dBFS，不启用压缩或 DC 修正。额外补偿最多 4 倍，并按手动倍率限制总补偿不超过最高档位的 121 倍（上限统一取自 PlaybackOptions）。这会增加缓冲并改变动态，默认关闭。它与 ExoPlayer 的流式 RMS 均衡器并非同一算法，不能承诺逐样本相同。
- `audio-spdif` 关闭，使音轨经过可处理的 PCM 路径。自定义 `af` 列表目前由这个功能独占；MPV 自动插入的重采样和速度处理位于输出链内部。
- 统计来自处理过的 PCM，单位 dBFS，未知时为 null；它不是设备扬声器响度、Android 系统音量或真机输出录音。配置成功与实际有处理帧分别判断。

## 重建

需要 Python 3、Git Bash、Android NDK r28b；脚本不修改系统安装或 Gradle 缓存。

```powershell
python phone/native/mpv-audio-dsp/build.py --ndk C:/Users/Heart/AppData/Local/Android/Sdk/ndk/28.2.13676358
```

脚本从 Gradle 缓存读取锁定的原 AAR，生成四 ABI 补充库及含各库校验和的 `manifest.json`。两种库均使用 16 KiB ELF 段对齐，并通过链接器检查原 avutil/swresample/mpv 依赖是否缺失符号。库文件保存在本目录供离线 Gradle 打包；完整源码可由固定 URL、校验和及脚本重现。
