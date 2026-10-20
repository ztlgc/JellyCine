# 手机端播放页面设计 v2

按已确认的 `artifacts/design/playback-redesign-v2/jellycine-playback-redesign-v2.png` 实现 Android Compose 页面。适用普通 `Video` 详情页，以及手机端通用播放器。

## 页面与交互

- 详情页使用深色底、顶部宽幅封面、标题和年份/秒级时长。小型可编辑评分和清晰度/编码/帧率标签紧邻标题；继续播放和下载同排，下方显示播放进度及已播/剩余时间。
- 已看、收藏、删除置于右上角。移除详情页的版本、音轨、字幕卡片，简介和紧凑媒体信息直接接续进度区。
- 评分继续使用现有服务器 `CommunityRating` 保存与回读；收藏、已看、后台删除和下载沿用原回调。
- 评分弹窗按确认的 `artifacts/design/rating-slider-v4/rating-slider-design.png` 实现：选中分数放在标题右上角，移除中部大数字区以降低高度。常用 6.0–10.0 分、可展开的 1.0–5.9 分分别使用加粗滑条（12dp 轨道、28dp 滑块、56dp 触控区域），步进 0.1 分；当前低分自动展开。两段共享一个选中值，不添加微调或范围切换按钮。选分后明确保存，取消保留原评分；加载、权限不足、重试和保存失败反馈继续保留。
- 选择态使用整数十分位，避免拖动时积累浮点误差；服务器仍保存浮点 `CommunityRating`。提交前检查 1.0–10.0 与一位小数，并回读确认；允许浮点表示尾差，服务器若将 8.6 四舍五入成整数则不报告成功。原有完整元数据保留、字段锁和缓存局部更新机制不变。
- 播放器顶部保留返回、标题/格式、旋转、画面比例和更多。中央为后退/播放暂停/前进圆形按钮，左侧锁定，底部为细进度线与单行工具栏。
- 剧集的上一集/下一集在横屏中央保留；窄竖屏放入更多菜单，避免与锁定入口重叠。
- 倍速区使用带边框的减速/当前倍速/加速组合，横屏其余操作间用细分隔线；小屏竖屏工具栏可横向滚动。
- 音轨与增益入口打开同一个右侧声音抽屉：音轨选择、关闭/30/80/150/200 增益与更多档位、当前 dB、音量均衡。音轨入口直接展开音轨列表。
- 抽屉打开时暂停控制栏自动隐藏，隐藏中央与底部控件，保留画面和顶部标题。点击外部、关闭或系统返回退出抽屉。短屏内容可纵向滚动。
- 增益换算、DSP 和播放器引擎行为沿用现有实现；本次布局验收不构成实际音频输出验收。

## 原生预览

预览直接渲染实际 Compose 组件，覆盖 412×892、360×800（字体 115%）详情页、892×412、640×320、360×800 播放器，以及两种横屏声音抽屉。

声音抽屉的预览直接渲染同一套原生面板内容；运行时使用模态窗口并关闭系统默认遮罩，统一由页面绘制 25% 遮罩。窗口焦点、返回键和真实触控仍需设备验收。

预览源码：`phone/src/screenshotTest/kotlin/com/jellycine/app/ui/PlaybackDesignPreviews.kt`。截图工具仅在 `-PplaybackScreenshot` 时启用，示例照片同样只随此选项加入预览构建，普通 APK 使用服务器媒体图像。

示例照片来自 Unsplash / Luca Bravo：`photo-1433275328929-69941e862d61`（Pragser Wildsee）。照片用于预览媒体背景；页面文字与按钮由原生 Compose 绘制。

可复现命令（已配置 Android SDK、JDK 21）：

```powershell
.\gradlew.bat :phone:updateDebugScreenshotTest -PplaybackScreenshot '-Pandroid.experimental.enableScreenshotTest=true' '-PandroidAbis=arm64-v8a,x86_64' --console=plain --no-daemon --max-workers=2
```

## 验证边界

编译、单元测试、原生预览与 APK 静态检查分别记录在 `artifacts/design/playback-redesign-v2/`。当前环境没有连接 Android 设备，模拟器未能启动；尚未进行设备安装、真实服务器播放、触控、横竖屏切换和实际音频输出验收。

### 原生画面核对（2026-10-18）

- `:phone:updateDebugScreenshotTest` 成功，7 个尺寸/状态均已逐一目视核对；详情页 360dp 与 115% 字体仍完整显示主要操作和媒体信息，640×320 声音抽屉的音量均衡完整可见。
- 主预览：`artifacts/design/playback-redesign-v2/native/native-preview.png`；小屏核对：同目录 `compact-review.png`。原始全分辨率图片与 `render-manifest.json` 同目录保存。
- 预览合图可用 `python artifacts/design/playback-redesign-v2/make-preview-board.py` 重建；合图仅缩放原生截图，不改写页面内容。
- 构建缓存临时放在 `artifacts/build-cache/`，避免 C 盘空间不足。此目录属于生成缓存，未加入 APP 资源。
