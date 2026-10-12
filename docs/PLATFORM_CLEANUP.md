# TV / iOS 专用代码清理

## 移除内容

- `tv/`、`iosApp/`、`data/src/iosMain/` 从活动源码树移除。
- `settings.gradle` 移除 TV 模块；当前应用模块仅 `phone`、`data`、`shared`、`core`。
- `data`、`shared` 移除 iOS targets/source sets，删除 XCFramework 和复制 iOS framework 的任务及依赖。
- 删除未使用的 Compose Multiplatform 插件声明、Kotlin/Native 平台跳过配置；README 和仓库开发指引同步更新。
- 保留 Android 所需的共同模型、资源、组件、播放器、网络和存储代码及 Android 构建配置。不将媒体类型“电视节目”的功能与 TV 客户端混同。

本地备份为 `artifacts/android/platform-cleanup/removed-platform-sources.zip`，包含移除前的专用源码与资源。一个 PNG 及空目录另行移入同一备份目录，活动源码树已无旧平台目录。

## 验证

为避免其他 Gradle 进程覆盖结果，清理前后分别使用独立的构建输出目录和项目缓存，执行全量 Android 手机构建及测试。

- 清理前、清理后的独立构建均成功；各 19 项测试通过（首页分类 3、媒体库行为 11、家庭媒体分类 5）。
- `core`、`data`、`shared` 编译包的 81 / 764 / 121 项内容，清理前后字节一致。
- APK 的 1,334 项资源、assets、Manifest、原生库条目，清理前后内容一致；ARM64 MPV / FFmpeg 原生库保留。
- APK ZIP 完整性、debug v2 签名及包信息校验通过。
- 另一个会话在验证期间持续修改 Android 首页、连接设置等文件；这些修改没有被回退。用户选择继续并行并仅验收当前构建快照，所以不声称此次 APK 与源码树的后续变化一致，也不声称前后整包字节相同。
- 尚未进行真机安装或真实 Emby 播放验收。

## 产物

`artifacts/android/jellycine-phone-debug-1.3.6-arm64-v8a-android-only.apk`，约 70.6 MiB，版本 1.3.6 / code 26，最低 Android 8.1（API 27）。Discord 集成沿用该快照的关闭状态。

SHA-256：`3ded72f97f3465dab5df1fb6ca106fa4779261571964f12866ddde22b13738ac`。

同目录有 `.apk.sha256`；完整验证数据位于 `artifacts/android/platform-cleanup/validation.json`。
