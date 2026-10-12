# Android 手机端开发与打包

本仓库只开发 Android 手机端，TV 和 iOS 专用项目、源码及对应打包配置已移除。Android 手机端保留 `phone`、`data`、`shared`、`core` 四个模块。

平台移除后的独立构建、19 项测试及 Android 产物对比结果见 [平台清理记录](PLATFORM_CLEANUP.md)。

## 构建

在仓库根目录运行 PowerShell：

```powershell
.\gradlew.bat :phone:assembleDebug :data:testAndroidHostTest -PandroidAbis=arm64-v8a --console=plain
```

产物位于 `phone/build/outputs/apk/debug/jellycine-phone-debug-1.3.6-arm64-v8a.apk`。
这是 ARM64 Android 手机的 debug 签名测试包，包含家庭视频和照片媒体库支持。

`androidAbis` 可以指定以逗号分隔的 ABI；省略参数时保留上游原有的全部 ABI 配置。
构建使用仓库指定的 Gradle、JDK 17 或更高版本及 Android SDK 37。

## 可选 Discord SDK

`core/libs/discord_partner_sdk.aar` 是可选的本地依赖：

- 文件存在：编译官方 SDK 适配器及 C++ 桥接，启用 Discord 连接功能；需要匹配的 SDK、NDK/CMake。
- 文件不存在：不构建 Discord 原生桥接，关闭该构建中的连接入口与播放状态同步。媒体库浏览、图片和视频播放代码正常打包。

`core` 的 `DISCORD_RPC_AVAILABLE` BuildConfig 字段反映当前构建是否包含该 SDK。
两个适配器分别放在 `src/discordEnabled/java` 与 `src/discordDisabled/java`，每次构建只包含一个。
已验证的打包路径是缺少 SDK 时的构建；包含 SDK 的原生集成需另行验证。

## 产物检查

交付前检查 APK 签名、包名、版本、最低系统版本、ABI、ZIP 内容和 SHA-256。
完整构建与单元测试通过不代表真实 Emby 连接、安装或设备播放验收通过。

## 本次产物（2026-10-12）

- 文件：`artifacts/android/jellycine-phone-debug-1.3.6-arm64-v8a.apk`。
- 大小约 70.5 MiB；包名 `com.jellycine.app`，版本 `1.3.6` / code 26，最低 Android 8.1（API 27），目标 API 36。
- 手机端完整 assembleDebug 构建成功，5 项分类测试通过；APK v2 debug 签名验证通过。
- ZIP 完整性、仅 ARM64 原生库、MPV 库及家庭媒体浏览类均已检查。
- SHA-256：`e1cff3ea1065c82fa447c9f11d5b9470b0099ddc37293bb47b645cca50892fea`。
- 同目录提供 `.apk.sha256` 和 `validation.json`；构建目录与交付副本的哈希一致。
- 此包不包含 Discord SDK；没有连接 Android 设备或真实 Emby 服务器验收。TV 和 iOS 未构建。

## “其他视频”栏目产物（2026-10-12）

左上角分类菜单新增“其他视频”。选择后使用紧凑标题栏，仅列出 CollectionType=homevideos 的媒体库；点击卡片进入家庭媒体目录浏览。没有匹配库时显示空状态，关闭轮播后仍保留分类菜单。

- 新包：`artifacts/android/jellycine-phone-debug-1.3.6-arm64-v8a-other-videos.apk`（约 73.3 MiB）。
- 执行 `:phone:assembleDebug :phone:testDebugUnitTest :data:testAndroidHostTest -PandroidAbis=arm64-v8a` 成功；3 项栏目过滤测试和 5 项条目分类测试通过。
- 签名、ZIP 完整性、ARM64 ABI、分类标识及家庭媒体类均已检查；未真机或真实服务器验收。
- SHA-256：`bb1d55a6e721fb310cc5972467a02d51cb479cee5b47b073dac9559fc084f7d8`。
- 同目录包含该包的 `.sha256` 与 `.validation.json`，此前交付的 APK 保留。
