# 手机端主页与播放交互调整

## 功能

- 视频卡片进入详情页，再通过播放或继续播放按钮启动播放器。随机播放仍直接播放队列；目录浏览与照片查看保留。
- 首页和“其他视频”依次展示媒体库、最近观看、各媒体库视频。最近观看合并未完成与已完成记录，按最近播放时间排序，以条目 ID 去重。
- “其他视频”的观看记录限定家庭视频媒体库；每库视频使用小批量查询，保留媒体库完整列表原有缓存与分页。
- 最近观看分别缓存首页与家庭视频分类数据，沿用按服务器和用户隔离的主页快照。旧快照仍可读取。
- 各媒体库分区提供“更多”入口，家庭视频使用横向封面。
- 底部导航栏居中，宽度约为原来一半，高度 60dp；保留至少 48dp 的按钮触控区域。
- 播放器采用半透明圆角控件：左上返回和标题、右上方向切换和画面比例及更多、中央播放与快进后退、左侧锁定、底部时间进度及音轨字幕。
- 横屏右侧显示倍速加减和当前档位；竖屏将其放入底部控件。支持 0.5、0.75、1、1.25、1.5、1.75、2 倍速。
- 音量增益支持关闭、10%、20%、30%、50%、70%，独立于普通音量。百分比表示额外音频幅度增益，70% 对应 1.7 倍幅度。
- ExoPlayer 直接处理解码后的 PCM 样本，并阻止直通和 offload 绕过增益处理链；MPV 使用独立命名的 lavfi volume 音频滤镜，各档不会重复叠加。
- 倍速和增益保存在播放器偏好中，重新初始化播放时恢复。横竖屏切换不重新初始化播放器，退出时恢复原方向。
- 锁定后仅保留解锁入口，打开选择菜单或拖动进度时暂停控制栏自动隐藏。
- 视频详情页的评分按钮为 32dp、播放按钮约 40dp、音轨和画质选项约 36dp，缩短组件间距与信息卡片的空白高度。
- 收藏仍由服务器保存；收藏查询和页面补上 Video 条目，操作期间防止重复请求，失败显示提示。
- 详情页删除图标替代更多菜单，并移除这页的投屏按钮。点击删除立即返回，由应用级任务在后台删除服务器条目及文件；绑定点击时的服务器和用户，不随页面退出取消。
- 删除成功后刷新列表并移除主页与媒体库缓存中的条目；失败提示原因，保留条目。缓存回写会过滤已删除视频。

## 验证范围

针对增益换算、倍速边界、观看记录排序去重和旧快照兼容性增加测试。构建日志与本次 APK 验证记录位于 `artifacts/`。

本次运行未连接 Android 设备，也没有已配置的模拟器。编译与单元测试不能替代真实 Emby/Jellyfin 服务器、设备横竖屏切换、实际音频增益和安装验收。

### 首次布局构建结果（2026-10-13）

- 独立输出目录、非增量完整构建成功，手机端 29 项、core 3 项、data 10 项测试全部通过；本次新增 7 项针对性测试。
- APK：`artifacts/JellyCine_1.3.6_arm64_playback-ui_debug.apk`，73,994,218 字节。
- 包名 `com.jellycine.app`，版本 `1.3.6` / code 26，最低 API 27，target SDK 36，仅 ARM64。
- Android Debug 证书的 APK v2 签名、ZIP 完整性及新增功能类检查通过。
- SHA-256：`c8554db8db485fe467fe71bb492ec223e8e30339fb31beb26d5dde2394f9c7b1`。
- 同目录提供 `.apk.sha256` 和 `.apk.validation.json`；构建日志为 `artifacts/playback-ui-isolated-build.log`。
- 构建期间源码快照未变动。此次调试包不替代设备安装与真实服务器验收。

实现依据：[Android LoudnessEnhancer](https://developer.android.com/reference/android/media/audiofx/LoudnessEnhancer)、[Media3 音频解码事件](https://developer.android.com/reference/androidx/media3/exoplayer/analytics/AnalyticsListener)、[MPV 音频与倍速控制](https://mpv.io/manual/stable/)。

### 增益与详情操作修复（2026-10-14）

上一版仅检查增益参数写入，未验证实际音频输出。此次改为直接处理音频，并增加 3 项 PCM 样本测试、2 项后台删除测试及 2 项删除缓存测试。

对 PCM 正弦波应用相同 FFmpeg volume 滤镜，10%、20%、30%、50%、70% 的 RMS 比值分别为 1.09999、1.2、1.3、1.50001、1.7；结果见 `artifacts/audio-gain-check/validation.json`。这些是音频算法验证，Android MPV 最终输出仍需设备验收。

相关接口依据：[Media3 PCM 处理链](https://developer.android.com/reference/androidx/media3/exoplayer/audio/DefaultAudioSink.Builder)、[MPV 命名音频滤镜](https://mpv.io/manual/stable/)、[Emby 删除条目接口](https://dev.emby.media/reference/RestAPI/LibraryService/deleteItems.html)。
