# 短信推送助手 / SmsForwarder Assistant

在已 Root 的 ARM64 安卓手机上部署本机 Bark 服务，并帮助配置 [SmsForwarder](https://github.com/pppscn/SmsForwarder)，将短信通知推送到 iPhone。当前版本 **1.3**，原名“Bark 本机服务器”。

本项目是独立的社区助手，不是 SmsForwarder 或 Bark 官方客户端。助手代码采用 [MIT](LICENSE)，第三方组件保留原许可证，见 [NOTICE.md](NOTICE.md)。

## 功能

- 内置经 SHA-256 校验的官方 SmsForwarder 3.5.0 ARM64 安装包。
- 本机运行 Bark 服务，iPhone 配对信息由当前设备生成，无预置域名、固定 IP 或密钥。
- 一键创建/更新当前 iPhone 的发送通道、全部 SIM 的短信规则，并配置失败重试。
- 检查与修复短信接收、读取、小米通知类短信、自启动和后台运行权限。
- 独立 Root 检测任务：完整检查间隔 1–1440 分钟（默认 5），约每 10 秒检查两个后台服务是否退出，支持立即检查与上次结果。
- 检测 Bark 本机接口、SmsForwarder 前台服务及短信权限，异常时尝试恢复；尊重应用内手动关闭。
- 开机应用数据解锁后恢复监测；升级保留配对。短信气泡与铃铛自适应图标。

## 首次使用

需要 Android 8.0+、ARM64、Root、iPhone Bark。自动配置当前只适配 SmsForwarder 3.5.0 数据库版本 21；实测环境为 Android 13 / Redmi / Magisk。其它 Root 管理器与 ROM 仍需验证。

1. 打开“首次配置 / 一键启用 / 权限修复”，允许 Magisk Root 授权。
2. 点击“安装 / 打开内置短信转发器”，在官方应用中完成首次使用确认。
3. 点击“启动 Bark，查看 iPhone 配对地址”。两台手机连接同一 Wi-Fi，在 iPhone Bark 添加显示的服务器地址。
4. 返回助手，点击“一键启用全部短信转发”。现有其它通道、规则会保留，可能仍同时发送。
5. 检查权限结果，按需在小米系统设置中确认“始终允许”、自启动与省电无限制，再发送真实短信验证。
6. 首页打开“自动检查与恢复”，开启并保存间隔。卸载前先关闭监测，再停止服务。

发送路径为 **短信转发器 → 127.0.0.1 本机 Bark → Apple APNs → iPhone**。首次注册后，两台手机各自联网即可，日常推送不要求处于同一 Wi-Fi。重新安装 iPhone Bark 或推送标识变化时可能需要重新注册。

## 运行与数据

本机服务监听 IPv4 18080，使用随机 URL 路径。局域网配对采用 HTTP；请勿直接映射明文端口到公网。短信最终经 Apple APNs 投递，自建 Bark 并不消除对苹果推送服务的依赖。

配对和配置位于应用私有目录；独立监测位于 `/data/adb/sms-push-assistant`，开机入口为 `/data/adb/service.d/97-sms-push-assistant.sh`。修改 SmsForwarder 配置前保存备份。Bark 访问日志不记录请求正文、URL 或设备密钥；SmsForwarder 自身的历史记录可能保存短信内容。

重试默认最多 5 次、递增间隔 5 秒、单次超时 20 秒。断网、关机、深度休眠、厂商限制可能导致延迟或漏推；检测正常只表示本机服务可用，不保证通知必达，也不保证无限期补发。未添加云端同步或上传个人短信的遥测功能。

## 自行构建

当前仓库提供源码与构建脚本，暂未提供预编译 APK。准备脚本从官方上游下载 SmsForwarder 安装包及 Bark 推送组件，并校验固定 SHA-256；这些下载内容不提交到本仓库。

Windows + PowerShell 7、JDK 21、Node.js 20+、Go 1.27.3（上游至少要求 Go 1.26）、Android SDK platform 35、Build Tools 35.0.0、NDK 27.0.12077973。

```powershell
$env:ANDROID_SDK_ROOT = 'C:/Android/Sdk'
$env:JAVA_HOME = 'C:/Java/jdk-21'
node ./prepare-dependencies.mjs
./build-engine.ps1
./test.ps1
./app/build.ps1
```

输出：`app/out/SmsForwarder-Assistant-arm64.apk`。Go 依赖会正常校验 go.sum；网络不通时可自行配置 GOPROXY。

默认构建生成本机测试签名。使用维护者签名时传 `-Keystore`、`-KeyAlias`，并通过环境变量 `SMS_STORE_PASS` / `SMS_KEY_PASS` 提供密码。签名密钥不在仓库中，第三方自行签名不能覆盖安装维护者版本。

上游 Bark 适配源码位于 `upstream/bark-server-2.3.9`；助手与运行脚本在 `app`。构建过程不依赖维护者电脑目录、手机数据库或配对文件。

## 验证

配置测试覆盖保留无关字段、重试值更新、重复执行、首次使用确认及异常 XML 拒绝；检测间隔测试覆盖范围与非法输入。实机已验证两个服务被强制结束后的恢复、通知类短信权限恢复、手动停止保护、图标及本机健康接口。开机脚本已安装并完成语法检查，未为此重启手机做整机验证；没有在另一台全新手机上测试完整首次安装流程。
