# Android 手机端开发与打包

本仓库只开发 Android 手机端，TV 和 iOS 专用项目、源码及对应打包配置已移除。Android 手机端保留 `phone`、`data`、`shared`、`core` 四个模块。

## 更高增益与服务器评分（2026-10-18）

- 新增 +3000%、+6000%、+12000%，对应 31、61、121 倍幅度（约 +29.83、+35.71、+41.66 dB）。MPV / ExoPlayer 的总增益上限均跟随最高档位，最终峰值限幅保留；高峰值素材的实际倍率受限幅影响。
- 普通视频评分由本机 DataStore 改为服务器公共 `CommunityRating`（1–10 分），网页和读取该字段的其他客户端可共享。当前账号需要编辑媒体资料的权限，评分会影响所有用户。
- 按 Emby 完整单项 GET → 修改评分和该字段锁 → POST → GET 回读校验的流程，保留未知字段及其余元数据。不使用可能清空其他资料的局部 DTO 提交。旧本机分数保留但不再参与显示，不自动上传覆盖服务器。
- 详情重新进入/回到前台或打开评分框时读取服务器；已确认变化更新目录缓存、搜索已有分页和首页磁盘快照，保持缓存原刷新时间，避免全量目录请求。
- 本次正式 ARM64 APK：`artifacts/android/JellyCine_1.3.6_arm64_gain-rating_20261018.apk`。验证记录在 `artifacts/server-rating-20261018/`，本地测试与构建不代表真机或真实 Emby 验收。
- 接口依据：[Emby 官方完整资料更新说明](https://emby.media/community/topic/125696-update-an-item-with-api/)、[完整单项读取](https://dev.emby.media/reference/RestAPI/UserLibraryService/getUsersByUseridItemsById.html)、[评分字段锁](https://dev.emby.media/reference/pluginapi/MediaBrowser.Model.Entities.MetadataFields.html)。

## 音频增益与限幅（2026-10-17）

- 默认沿用系统当前媒体音量，不再在进入播放页时使用初始 50% 覆盖它；显式记忆播放器音量的设置保留。
- 原有增益档位保留，增加 +800%（9 倍）与 +1500%（16 倍，约 +24.08 dB），同时显示 dB。两引擎采用增益后最终限幅，并提供默认关闭的音量均衡。
- MPV 补充原生滤镜与返回真实错误码的 JNI 桥。原 AAR 与新增库均有校验和，保留四 ABI；原生构建与边界见 [重建说明](../phone/native/mpv-audio-dsp/README.md)。
- 家庭媒体目录、旧缓存和首页过滤照片，移除手机照片查看入口；文件夹中的视频仍可导航。
- 正式 ARM64 APK：`artifacts/android/JellyCine_1.3.6_arm64_audio-gain_20261017.apk`；包名、版本仍为 `com.jellycine.app`、`1.3.6` / code `26`，沿用现有正式签名。
- 构建、测试、签名、APK 内原生库与源码身份检查记录：`artifacts/audio-gain-20261017/validation.json`。交付目录同时提供 `.apk.sha256` 和 `.apk.validation.json`。
- PCM/桌面数字音频测试不等于真机响度、安装或真实 Emby 验收。当前无连接设备，需在固定视频、音轨、系统媒体音量和输出设备条件下试听比较。

## 长按 2 倍速正式包（2026-10-15）

- APK：`artifacts/android/JellyCine_1.3.6_arm64_hold-2x_release.apk`，42,233,958 字节。
- 播放中长按画面临时 2 倍速，松手恢复原倍速；取消、多指触摸、暂停、锁定及退出时结束临时加速。MPV 与 ExoPlayer 共用该手势，不保存临时倍速。
- 同时包含关闭、30%、80%、150%、200%、400% 增益档位、MPV 内置增益修复，以及详情页秒级时长。
- 包名 `com.jellycine.app`，版本 `1.3.6` / code `26`，最低 API `27`、target SDK `36`，仅 ARM64。
- release 构建成功；core 13 项、phone 47 项、data 26 项测试通过，包含 5 项长按倍速恢复测试。
- APK v2 签名通过，证书与已有 JellyCine 正式包一致；ZIP 完整性、16 KiB ZIP 对齐、包信息、ABI、功能映射及打包资源检查通过。
- SHA-256：`76b09e713cd601c19956985d6a82216596f9e55dc87e40330c6dbc8f167e2d3d`。同目录提供 `.apk.sha256` 与 `.apk.validation.json`。
- 构建与检查记录位于 `artifacts/hold-2x-release/`；构建期间 377 个源码/配置文件的哈希未变。当前没有连接 Android 设备，未进行安装、实际长按、播放响度或真实服务器验收。

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

## 正式版签名和打包

在根目录配置本机的 `keystore.properties`，四个必填字段为 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`。`storeFile` 的相对路径基于 `phone/`，例如 `jellycine-release.keystore` 对应 `phone/jellycine-release.keystore`。

也可使用环境变量 `JELLYCINE_STORE_FILE`、`JELLYCINE_STORE_PASSWORD`、`JELLYCINE_KEY_ALIAS`、`JELLYCINE_KEY_PASSWORD`。项目已有正式密钥时必须沿用原密钥。

```powershell
.\gradlew.bat :phone:assembleRelease -PandroidAbis=arm64-v8a --console=plain
```

正式 APK 输出到 `phone/build/outputs/apk/release/`。构建会在编译前检查签名配置是否完整、密钥文件是否存在。

密钥文件和 `keystore.properties` 已被 Git 忽略。需要单独备份密钥及密码，后续覆盖升级使用同一签名；新正式密钥签名的 APK 不能直接覆盖不同签名的旧包。

### 正式包验证（2026-10-13）

- ARM64 release APK：`artifacts/JellyCine_1.3.6_arm64_release.apk`。
- 包名 `com.jellycine.app`，版本 `1.3.6`，versionCode `26`，最低 Android API `27`，targetSdk `36`。
- APK v2 签名、证书与本地发布密钥匹配、ZIP 完整性和 ARM64 原生库检查通过。
- SHA-256：`906c0476765c24c5dbf1221df49cdbbc05540cd6c350ec2954dd7272f68647f3`。
- release 构建与现有 23 项手机端单元测试通过；签名配置缺失时的提前报错检查通过。
- 尚未进行设备安装、真实服务器及播放验收。编译中的弃用警告不代表构建失败。

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
