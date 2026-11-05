# JellyCine 图标候选稿

五款原创矢量设计，用于选择 Android 手机端应用图标方向。用户已选定 **05「私人影院」**；对应 Android 资源已放入 `phone/src/main/res/`，定稿与适配预览见 `../private-cinema/`。

| 编号 | 名称 | 构思 |
| --- | --- | --- |
| 01 | 灵动水母 | 将原有水母与电影概念简化为水母轮廓和播放负形 |
| 02 | 胶片字母 J | 以 J 为主轮廓，顶部胶片孔建立电影关联 |
| 03 | 光影放映 | 字母 C、播放键与投影光束组合 |
| 04 | 果冻播放 | 不规则柔软三角轮廓，呼应 Jelly 与播放 |
| 05 | 私人影院 | 金色电影票与播放符号，强调私人影院氛围 |

打开 `JellyCine-five-icon-concepts.png` 查看对比图，或打开 `index.html` 查看响应式展示。每款提供 512 viewBox 的 SVG 和 1024×1024 RGBA PNG。SVG 为可编辑源文件；PNG 通过 Pillow 超采样渲染。

对比图展示 64px、48px、32px 缩略图和圆形裁切示意。这些是候选稿，并非已经完成设备验收的 Android adaptive-icon 资源。选定后需导出前景、背景、单色层，并核对安全区域与真实启动器效果。

重新生成：`python -I design/icon-concepts/generate_icons.py`（需要 Pillow 和 Windows 微软雅黑字体）。
