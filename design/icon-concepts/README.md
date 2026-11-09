# JellyCine 图标候选稿

五款原创矢量设计，均可在应用「设置 → 界面 → 应用图标」中切换；默认使用 **02「胶片字母 J」**。Android 资源由 `export_android_icons.py` 导出到 `phone/src/main/res/`。

| 编号 | 名称 | 构思 |
| --- | --- | --- |
| 01 | 灵动水母 | 将原有水母与电影概念简化为水母轮廓和播放负形 |
| 02 | 胶片字母 J | 以 J 为主轮廓，顶部胶片孔建立电影关联 |
| 03 | 光影放映 | 字母 C、播放键与投影光束组合 |
| 04 | 果冻播放 | 不规则柔软三角轮廓，呼应 Jelly 与播放 |
| 05 | 私人影院 | 金色电影票与播放符号，强调私人影院氛围 |

打开 `JellyCine-five-icon-concepts.png` 查看对比图，或打开 `index.html` 查看响应式展示。`app-icon-switcher-preview.png` 为设置页图标选择区的界面示意（静态稿，非真机截图）。每款提供 512 viewBox 的 SVG 和 1024×1024 RGBA PNG。SVG 为可编辑源文件；PNG 通过 Pillow 超采样渲染。

## Android 集成

- 每款导出 `ic_app_icon_0X.xml`（设置页预览）、`ic_launcher_0X_background/foreground/monochrome.xml` 与 `mipmap-anydpi/ic_launcher_0X.xml`。
- `phone/src/main/AndroidManifest.xml` 为每款声明一个 `activity-alias`，默认仅启用 `JellyCineLauncher02`；`AppIconSwitcher` 在切换时只启用选中的别名，并持久化选择。
- `jellycine_logo.xml`、`ic_splash_logo.xml`、`splash_icon.xml` 使用 02 作为应用内默认标识。
- 单色层为 Android 13+ 主题图标准备；05 与 02 使用 evenOdd 复合路径保留镂空。

重新生成：`python -I design/icon-concepts/export_android_icons.py`，需要 Python 标准库。对比图重新生成仍使用 `generate_icons.py`（需要 Pillow 和 Windows 微软雅黑字体）。
