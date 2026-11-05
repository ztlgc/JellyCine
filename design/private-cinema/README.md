# JellyCine · 私人影院

用户选定的 05 方案：葡萄紫 `#38233F`、香槟金 `#F0CE88`、票孔暗金 `#98744E`。

- `05-cinema-ticket.svg`：选定设计的可编辑源文件。
- `05-cinema-ticket-1024.png`：1024×1024 PNG。
- `private-cinema-android-preview.png`：选稿、自适应圆角/圆形、两种单色主题示意。
- `export_android.py`：从候选 SVG 导出手机端矢量资源和预览。运行 `python -I design/private-cinema/export_android.py`，需要 Pillow 及 Windows 微软雅黑字体。

## Android 资源

资源全部位于 `phone/src/main/res/`，利用应用资源优先级覆盖共享模块中的旧图标，无需修改现有界面源码或 Manifest。

- `mipmap-anydpi/ic_launcher.xml`、`ic_launcher_round.xml`：自适应图标，包含背景、前景和 monochrome 层。手机端最低 API 27，全部支持 adaptive-icon。
- `drawable/ic_private_cinema_background.xml`：铺满自适应画布的葡萄紫背景。
- `drawable/ic_private_cinema_foreground.xml`：香槟金电影票前景。
- `drawable/ic_private_cinema_monochrome.xml`：用 evenOdd 复合路径保留镜头、播放键和票孔的透明镂空，供 Android 13+ 主题图标着色。
- `drawable/jellycine_logo.xml`：完整选稿，用于现有启动动画、登录、首页、关于页面等应用内图标引用。
- `drawable/ic_splash_logo.xml`、`splash_icon.xml`：系统启动页使用相同电影票前景。

前景按中心缩放至 84%。以整个票形的外接矩形计算，最远点距中心不超过 31.86dp，位于 108dp 自适应画布中央直径 66dp 的安全圆内。预览使用中央 72dp 可见范围模拟常见启动器裁切；实际形状及主题色由启动器决定。

静态预览和资源编译不等同于真机验收。选稿与旧候选 ZIP 独立保存，原候选 ZIP 保持为当时的设计快照。
