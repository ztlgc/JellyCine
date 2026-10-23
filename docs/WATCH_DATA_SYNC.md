# 观看数据同步

Android 手机端先将普通 `Video` 的打开记录和真实播放片段写入 SQLite，再通过 Supabase Auth 和 PostgreSQL RPC 增量同步。统计页面读取本地数据；后台拉取完成后更新。暂停、缓冲、倍速和快进的计时口径沿用现有实现。

## 本机配置

将根目录 `supabase.local.properties.example` 复制为 `supabase.local.properties`，填写项目 HTTPS URL 和 `sb_publishable_` 开头的 Publishable Key。实际配置由 `.gitignore` 排除，示例中不填写真实值。没有配置时仍能编译，设置显示“未配置同步服务”。

Gradle 仅向 APK 注入公开连接参数，拒绝 secret、service_role 和旧式 JWT 密钥。用户密码只用于首次绑定，不写入文件。会话使用 Android Keystore AES-GCM 加密，且从系统备份排除。

## Supabase 配置

通过 Supabase 插件选择用户授权的项目，以 `apply_migration` 应用 `supabase/watch_sync.sql`。这份文件是部署 SQL 源码，不含连接凭据。随后通过插件运行真实查询和安全检查。

设置页首次开启时绑定邮箱密码同步账号，也可以在绑定窗口创建新账号。项目启用邮箱验证时，验证后再登录；其他手机登录相同账号即可恢复。托管项目的默认邮件服务限制收件人，个人使用可先用项目所有者邮箱，其他邮箱应在 Supabase 配置自定义 SMTP。Supabase 平台/控制台账号、数据库密码、媒体服务器账号与同步 Auth 账号相互独立。客户端不保存数据库密码，也不向 Supabase 发送媒体服务器令牌。

两张表启用 RLS，用户只能读写自身记录；客户端没有 DELETE 权限，所有函数为 SECURITY INVOKER。播放片段的身份和起点不可变，版本和结束时间只允许前进。

## 同步行为

- 开关位于设置的屏幕时间卡片内，默认关闭。关闭取消后台任务，不删除任一端记录；已经发送的请求可能仍在服务器完成。
- 首次开启迁移并上传既有明细，后续只传未确认的会话或片段版本。确认旧版本不影响上传期间延长的新版本。
- 同步账号、媒体服务器稳定 ID、媒体用户 ID 共同决定数据归属。不同地址连接同一服务器可归并；账号切换不展示其他同步账号的数据。
- 新设备先下载历史，验证当前媒体服务器身份后绑定到本地统计。未知服务器的数据仍保留在本地独立分区。
- 打开应用、媒体账号变化和本地记录产生后请求同步。打开统计页也会请求同步。WorkManager 提供网络恢复重试和 15 分钟周期兜底，实际后台执行时间由 Android 调度。
- 强制离线时暂停所有自动请求。网络失败指数退避；登录会话失效时提示重新绑定。
- 本地上传确认与远端下载游标均持久保存。下载记录及游标在同一 SQLite 事务内提交。
- PostgreSQL 写入和读取游标共用账号级事务锁，避免并发事务提交顺序导致漏拉。所有表写入都会经触发器分配版本游标。
- 关闭同步后仍会继续本地记录。重新开启后补传。卸载前尚未成功上传的记录无法从服务器恢复。

## 验证

运行 `:data:testAndroidHostTest :phone:testDebugUnitTest`，覆盖请求鉴权、重复重传、旧版本确认、增量分页、暂停、SQLite v1 升级、版本合并和账号隔离。数据库测试使用 Robolectric 的真实 SQLite 行为，无需连接个人媒体库。

界面预览使用 `:phone:updateDebugScreenshotTest --tests 'com.jellycine.app.ui.WatchSyncSettingsPreviewsKt*' -PplaybackScreenshot -Pandroid.experimental.enableScreenshotTest=true`。在 PowerShell 中应将两个 `-P...` 参数各自加引号。预览仅用于布局检查，不替代手机安装与实际 Supabase/媒体服务器端到端验收。
