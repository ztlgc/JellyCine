# Emby「家庭视频和照片」媒体库支持探索

此文保留早期探索与实施历史。TV 和 iOS 专用代码现已移除，下文的旧平台路径和早期计划仅为历史记录；当前仅维护 Android 手机端。

## 结论与范围

基于仓库静态检查和 Emby 官方 API 文档，可以在现有架构上支持该类型，无需新增服务端插件。2026-10-11 探索时客户端没有完整支持：库入口可能显示，但进入后使用影视查询，照片、普通视频和目录无法正常形成浏览流程。首批实现及验证结果见文末。

2026-10-11 的探索阶段仅静态检查。2026-10-12 已实施手机和 TV 的共享浏览流程；真实服务器的视频播放、照片格式兼容和具体返回类型仍需设备验证。iOS 应另行检查实现完整性。

## 现有代码证据

以下路径相对仓库根目录；行号对应探索时版本。

| 位置 | 当前行为 | 支持所需变化 |
| --- | --- | --- |
| `phone/.../dashboard/media/MyMedia.kt:113,325` | 保留有效 CollectionFolder/Folder；movies、tvshows 以外均进入 ContentType.ALL | 识别 homevideos，转入家庭媒体浏览入口，补充名称和图标 |
| `tv/.../dashboard/media/MyMedia.kt:74,301` | 同类库过滤和通用入口映射 | 同步 TV 入口 |
| `phone/.../dashboard/media/ViewAllViewModel.kt:198` | ALL 使用 IncludeItemTypes=Movie,Series，Recursive=true | 家庭媒体不能沿用此查询 |
| `tv/.../dashboard/media/ViewAllViewModel.kt:149` | ALL 同样只查询 Movie,Series | 同上 |
| `data/src/androidMain/com/jellycine/data/repository/MediaRepository.kt:985` | 首页库分区只接受 movies、tvshows、boxsets 或 null | 若首页展示家庭媒体，须加入类型并调整最新内容查询 |
| 同文件 `:1017` | 最新内容查询分支缺少 homevideos | 为该库选择照片/视频条目，避免影视默认过滤 |
| `phone/.../dashboard/home/Dashboard.kt:1881,2998` 和 TV 对应位置 | 首页库入口也按 collectionType 映射影视内容类型 | 必须同步路由，避免从首页进入旧的 ALL 查询 |
| `phone/.../navigation/AppNavigation.kt:448` | ViewAll 条目点击统一进入 detail/{id} | 改为目录、照片、视频分别处理 |
| `phone/.../dashboard/media/ViewAllScreen.kt:690` 和 TV `:581` | 默认卡片使用 2:3 海报比例 | 家庭媒体采用适合照片的缩略图网格，显示目录和视频标识 |

表中 `phone/...`、`tv/...` 均省略 `src/main/java/com/jellycine/app/ui/screens/`；导航文件省略部分为 `src/main/java/com/jellycine/app/ui/`。

可复用能力：

- `data/src/commonMain/com/jellycine/data/model/BaseItemDto.kt` 已有 collectionType、isFolder、parentId、type、mediaType、primaryImageAspectRatio、width、height、cameraMake、cameraModel、imageOrientation 等字段。
- `MediaRepository.getUserItems`（约 682 行）及 `MediaServerApiClient.getUserItems` 已透传 parentId、recursive、includeItemTypes、分页、排序和 fields，无需先扩展基础列表协议。
- `MediaRepository.getImageUrlString`（约 1082 行）已有 Primary 图片地址、尺寸、质量、缓存 tag 和增强器参数；照片查看仍需专门的请求策略。
- `phone/.../player/PlayerViewModel.kt:320` 已使用 PlaybackInfo 和 MediaSources，可作为普通家庭视频的播放接入点。是否成功播放须用真实条目验证。
- 未在已检查的手机/TV 导航和媒体浏览代码中发现 Photo/PhotoAlbum 专用路由或完整照片查看器。

## API 与条目分发

Emby 官方浏览文档列出 CollectionType=homevideos，并支持通过用户视图和 ParentId 通用浏览。CollectionType 是库分类，Type 是具体条目类型，MediaType 是媒体类别，三者不能混用。

推荐按以下顺序分发条目，在家庭媒体路由内使用大小写不敏感比较：

1. isFolder=true，或已知目录类型 Folder/PhotoAlbum：进入子目录，保存返回栈。
2. type=Photo 或 mediaType=Photo：进入照片查看器。
3. type=Video 或 mediaType=Video：进入现有视频播放流程，允许服务端返回 Movie 等视频子类型。
4. 未识别条目：显示明确的不可打开状态，保留返回与刷新操作。

PhotoAlbum、Folder、Video、Photo 是实施时需要覆盖的候选返回类型；实际组合须以目标服务器响应为准。目录判断优先，避免有封面的相册被误当作照片。isFolder 缺失时仅对已知目录类型兜底，不按名称或是否存在封面猜测。

### 目录模式（建议作为默认模式）

```text
GET /Users/{userId}/Items
  ?ParentId={libraryOrFolderId}
  &Recursive=false
  &StartIndex=0
  &Limit=60
  &SortBy=SortName
  &SortOrder=Ascending
  &Fields=ParentId,DateCreated,PrimaryImageAspectRatio
```

省略 IncludeItemTypes，保留当前目录中的子目录、照片和视频；不要把递归展开的文件混进层级浏览。按 TotalRecordCount 分页，切换目录时重置分页、取消旧请求或校验请求身份；返回时恢复该层排序与滚动位置。

### 可选的全库照片/视频模式

使用库 ID 作为 ParentId，Recursive=true；照片可用 IncludeItemTypes=Photo。视频首版可覆盖已确认的 Video 类型；若需覆盖所有视频子类型，可增加 MediaTypes=Video 的 API 透传（当前 getUserItems 未暴露该参数），并以服务器响应验证。该模式与目录模式分别保存查询状态。

DateCreated 表示条目创建时间，不能直接标成「拍摄时间」。EXIF 字段是否返回、支持的排序字段及相册条目类型须在目标服务器核验。

### 照片请求与查看

缩略图和全屏使用 `/Items/{id}/Images/Primary`，沿用现有认证机制。网格请求小尺寸图；全屏请求屏幕适配大图并保持宽高比，关闭海报增强器，处理方向、加载失败和超大图片内存。

官方图片 API 提供 Format、AutoOrient、MaxWidth/MaxHeight、Tag 等参数。当前 URL helper 未暴露 Format/AutoOrient，同时传入宽高时使用 fillWidth/fillHeight；照片专用请求应核验这些参数在 Emby 的表现，避免裁切。Format=original 表示输出格式选择，不作为「返回源文件原始字节」的承诺。

手机查看器至少提供全屏、左右切换、缩放和退出；TV 提供遥控器左右切换、返回及清晰焦点。图片查看不得调用视频 PlaybackInfo 或上报视频播放进度。下载原图和自动幻灯片可作为后续范围。

## 建议实施顺序

1. 新增共享的家庭媒体分类与条目分发规则、独立的目录状态和浏览查询；覆盖手机/TV 我的媒体与首页入口。
2. 完成手机目录网格与照片查看器，家庭视频接入现有播放器；优先验证完整的「库 → 子目录 → 照片/视频 → 返回」链路。
3. 实现 TV 网格焦点、照片切换和返回恢复。通用分类/查询放在共享层，手机与 TV 分别处理交互。
4. 可选增加首页最新家庭媒体、全库分类浏览、照片幻灯片及下载；首页分区缓存须检查旧快照刷新和服务器切换。

不要单独给 ALL 的 IncludeItemTypes 追加 Photo/Video：这只能改善取数，仍缺少目录导航、照片查看和适合照片的展示，并会扩大原有影视列表的语义。

## 验证标准

- 分类与查询：homevideos 路由、目录优先分发、未知/空字段兜底、非递归目录请求、分页不丢项、不串目录。
- 流程：嵌套相册、照片与视频混合、空目录、无缩略图、返回恢复、名称含特殊字符，以及首页和我的媒体两个入口。
- 图片：横竖图、旋转 EXIF、大尺寸 JPEG/PNG，以及目标库实际使用的 HEIC/WebP/其他格式；分别验证服务端输出与客户端解码。
- 视频：至少一段普通 MP4 和一段需要转码的视频；验证权限、首帧、暂停/续播、进度回报和播放后返回原目录。
- 状态：断网、401/403、服务端错误、服务器/用户切换、旧请求迟到、库重新扫描后刷新。
- 构建：手机和 TV 对应模块构建；真机和遥控器验收单列，构建通过不代表交互或服务端兼容通过。

## 官方参考

- [Browsing the Library](https://dev.emby.media/doc/restapi/Browsing-the-Library.html)：homevideos、用户视图、ParentId、递归与字段选择。
- [Items API](https://dev.emby.media/reference/RestAPI/ItemsService/getItems.html)：分页、IncludeItemTypes、MediaTypes 和目录过滤参数。
- [Item Information](https://dev.emby.media/doc/restapi/Item-Information.html)：IsFolder、Type、MediaType、MediaSources 和图片比例。
- [Image API](https://dev.emby.media/reference/RestAPI/ImageService/getItemsByIdImagesByType.html)：图片格式、尺寸、方向和缓存参数。

检查日期：2026-10-11。官方文档和静态代码不能替代目标 Emby 版本的响应及设备验证。

## 首批实施（2026-10-12）

- 新增共享 HomeMediaKind 分类和 HomeMediaScreen，手机/TV 的我的媒体及首页库卡片进入家庭媒体浏览。
- 非递归目录查询、按服务端原始返回数量推进分页、目录缓存及返回栈、滚动位置保存；刷新、重试、空目录及未知条目处理。
- 照片全屏查看、左右切换、手机手势缩放/平移、TV 方向键切换；浏览到已加载照片末尾时继续查询目录后续分页。
- 图片保留宽高比、使用 Primary/tag、关闭增强器并请求 AutoOrient；普通家庭视频直接进入既有播放器。
- 新增条目分类单元测试。首页最新家庭媒体分区、全库照片/视频分类、幻灯片与原图下载仍属于后续范围。
- 构建、测试及设备验证结果以实际执行记录为准，未连接目标 Emby 服务器时不视为完成兼容性验收。

### 实际验证结果

- `./gradlew.bat :shared:compileAndroidMain :data:testAndroidHostTest --console=plain` 成功：Android 数据层和共享 Compose 界面编译通过，5 个分类测试通过。
- 中英文资源 XML、资源键唯一性及 `git diff --check` 通过。
- `:phone:compileDebugKotlin :tv:compileDebugKotlin` 在手机资源依赖解析阶段失败，原因是原仓库引用的 `core/libs/discord_partner_sdk.aar` 不存在；未验证完整手机/TV 应用编译，也未产出 APK。
- 尝试运行原有 desktop 测试目标时，其 commonMain 的 currentTimeMillis 缺少 JVM actual 实现，因此改为启用 Android host test 并运行上述测试；未改动 desktop 平台实现。
- `adb devices` 无设备；没有真实 Emby 连接、设备交互或播放验收结果。
- 首页库卡片已经路由到新界面，首页「最新内容」分区仍保持原有影视范围。

### 手机端产物后续验证（2026-10-12）

用户将后续范围限定为 Android 手机端。Discord SDK 已改为可选构建依赖，缺少 SDK 时关闭连接入口和状态同步。`phone:assembleDebug` 及数据层测试已成功，实际 ARM64 APK 的签名、ZIP、原生库和新增家庭媒体类均已核验。产物与校验和见 [Android 构建记录](ANDROID_BUILD.md)。仍未进行设备安装、真实 Emby 图片和视频播放验收。
