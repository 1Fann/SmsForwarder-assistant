# 短信推送助手

中文 | [English](README.en.md)

在安卓手机上自建 Bark 推送服务，也可配合 SmsForwarder 将短信推送到 iPhone。

[查看源码发布](https://github.com/1Fann/SmsForwarder-assistant/releases/latest) · [更新说明](https://github.com/1Fann/SmsForwarder-assistant/releases)

## 功能

- 独立启动和管理 Bark 服务，不强制安装短信转发器。
- 生成 iPhone 配对信息并发送测试提醒。
- 可选安装 SmsForwarder，配置短信规则和发送通道。
- 提供通知失败重试、短信对账及可选后台恢复。

## 使用

需要 Android 8+、ARM64；iPhone 接收推送需安装 Bark。短信自动配置、权限修复和独立守护需要 Root。

Releases 提供源码 ZIP／tar.gz，不是可安装 APK。请按项目中的构建说明生成安装包，再完成授权与账号配置。同包名、同签名的版本才可保留数据覆盖安装。

## 文档

- [可靠性与验证](docs/RELIABILITY.md)
- [第三方说明](NOTICE.md)

## 许可证

助手采用 [MIT](LICENSE)，Bark、SmsForwarder 等第三方组件保留原许可证。
