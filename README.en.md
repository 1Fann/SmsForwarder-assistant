# SmsForwarder Assistant · Bark-Android / 短信推送助手

[中文](README.md) | **English** · [v1.3 Release](https://github.com/1Fann/SmsForwarder-assistant/releases/tag/v1.3) · [MIT](LICENSE)

**A Bark server for Android, with optional SMS forwarding to iPhone.** Deploy the Bark server on an ARM64 Android phone and use it as a standalone push server, or pair it with [SmsForwarder](https://github.com/pppscn/SmsForwarder) to forward incoming SMS notifications to your iPhone. Current version: **1.3**, previously named “Bark Local Server”.

**You can use this project solely to host a Bark server on Android. Installing or enabling SmsForwarder is not required.** SMS forwarding is an optional use case, not a prerequisite for running Bark.

| What you need | How to use the assistant |
| --- | --- |
| Host a Bark server on Android | Start the local Bark server, register your iPhone, and provide a push endpoint for applications or scripts that can reach it |
| Forward Android SMS to iPhone | Enable SmsForwarder alongside the local Bark server and configure SMS rules, permission repairs, and retries |

[Read the v1.3 release notes](https://github.com/1Fann/SmsForwarder-assistant/releases/tag/v1.3). This release provides source code only; no APK is attached.

This is an independent community assistant, not an official SmsForwarder or Bark client. The assistant is licensed under [MIT](LICENSE). Third-party components retain their original licenses; see [NOTICE.md](NOTICE.md).

## Features

- Run a standalone Bark server on Android: start, stop, display registration addresses, and send a test notification.
- Generate iPhone registration information on the current device, without preconfigured personal domains, fixed IP addresses, or device registration keys.
- The built APK bundles the official SmsForwarder 3.5.0 ARM64 installer, verified with SHA-256. Installing and enabling SMS forwarding remains optional.
- Create or update the current iPhone's sender channel and SMS rules for all SIMs, and configure failed-delivery retries.
- Check and repair SMS reception, SMS reading, Xiaomi notification-SMS access, autostart, and background execution permissions.
- An independent root monitor performs full checks every 1–1440 minutes, with a default of 5 minutes, and lightweight checks for either service exiting approximately every 10 seconds. Immediate checks and the last result are available in the app.
- Check the local Bark endpoint, the SmsForwarder foreground service, and SMS permissions; attempt recovery while respecting services manually switched off in the apps.
- Resume monitoring after app data becomes accessible following boot and unlock. Preserve registration during upgrades. Includes an adaptive speech-bubble and bell icon.

## Getting started

Requires Android 8.0+, ARM64, and Bark on iPhone. Automatic SMS configuration, permission repairs, and the independent scheduled monitor require root. The standalone Bark startup code does not invoke root, but operation on unrooted devices has not been tested on hardware. The tested environment is Android 13 / Redmi / Magisk; other root managers and ROMs still need verification.

### Option 1: Run only the Bark server on Android

1. Open the assistant and tap “启动服务” (Start service) on the home screen. You do not need to open SMS setup or install SmsForwarder.
2. Tap “刷新状态 / 配对地址” (Refresh status / Registration addresses). For initial registration, connect Android and iPhone to the same Wi-Fi and add the displayed LAN server address in Bark's server settings on iPhone.
3. Tap “发送测试提醒” (Send test notification) and confirm that the iPhone receives it.
4. Allow the assistant to keep running in the background. Other applications or scripts can send notifications through a reachable Bark endpoint. Callers on the Android phone can use `127.0.0.1`; callers on other devices need the phone's reachable network address.

The Android phone must remain online with Bark running. The iPhone receives notifications through Apple APNs and does not need to stay on the same Wi-Fi; callers sending requests to Bark must still be able to reach its endpoint. On rooted devices, you can optionally enable the independent monitor. Without SmsForwarder installed, its status may say “短信转发器未初始化” (SmsForwarder has not been initialized); this does not prevent Bark's own checks and recovery.

### Option 2: Forward Android SMS to iPhone

This use case requires root. Automatic configuration currently supports SmsForwarder 3.5.0 with database version 21 only.

1. Open “首次配置 / 一键启用 / 权限修复” (Initial setup / Enable / Repair permissions) and grant Magisk root access.
2. Tap “安装 / 打开内置短信转发器” (Install / Open bundled SmsForwarder) and complete the official app's first-use consent.
3. Tap “启动 Bark，查看 iPhone 配对地址” (Start Bark and view iPhone registration addresses). Connect both phones to the same Wi-Fi and add the displayed server address in Bark on iPhone.
4. Return to the assistant and tap “一键启用全部短信转发” (Enable forwarding for all SMS). Existing unrelated channels and rules are preserved and may continue sending messages as well.
5. Review the permission results. Where necessary, confirm “Always allow”, autostart, and unrestricted battery usage in Xiaomi system settings. Send a real SMS to verify delivery.
6. From the home screen, open “自动检查与恢复” (Automatic checks and recovery), enable monitoring, and save the interval. Before uninstalling, disable monitoring and then stop the services.

Delivery path: **SmsForwarder → local Bark at 127.0.0.1 → Apple APNs → iPhone**. After initial registration, both phones only need their own internet connections for routine SMS delivery; they do not need to remain on the same Wi-Fi. Reinstalling Bark on iPhone or changes to its push token may require registration again.

## Operation and data

The local server listens on IPv4 port 18080 with a randomized URL path. LAN registration uses HTTP; do not expose this plaintext port directly to the internet. Messages are ultimately delivered through Apple APNs, so hosting Bark yourself does not remove the dependency on Apple's push service.

Registration and configuration are stored in app-private directories. The independent monitor lives at `/data/adb/sms-push-assistant`, with a boot entry at `/data/adb/service.d/97-sms-push-assistant.sh`. A backup is saved before modifying SmsForwarder configuration. Bark access logs omit request bodies, URLs, and device keys; SmsForwarder's own history may retain SMS content.

The default retry settings are up to 5 retries, a 5-second increasing interval, and a 20-second request timeout. Network loss, shutdown, deep sleep, and vendor restrictions can delay or prevent delivery. Healthy local checks do not guarantee notification delivery or indefinite replay of failed messages. The assistant adds no cloud synchronization or telemetry that uploads personal SMS content.

## Build from source

This repository currently provides source and build scripts, with no prebuilt APK. The preparation script downloads the official SmsForwarder installer and Bark push component from their upstream projects and verifies pinned SHA-256 hashes. Downloaded components are not committed to this repository.

Build environment: Windows, PowerShell 7, JDK 21, Node.js 20+, Go 1.27.3 (upstream requires at least Go 1.26), Android SDK platform 35, Build Tools 35.0.0, and NDK 27.0.12077973.

```powershell
$env:ANDROID_SDK_ROOT = 'C:/Android/Sdk'
$env:JAVA_HOME = 'C:/Java/jdk-21'
node ./prepare-dependencies.mjs
./build-engine.ps1
./test.ps1
./app/build.ps1
```

Output: `app/out/SmsForwarder-Assistant-arm64.apk`. Go dependencies are verified against `go.sum`; configure `GOPROXY` if needed for your network.

By default, the build creates a local test signing key. To use a maintainer signing key, pass `-Keystore` and `-KeyAlias`, and provide the passwords through the `SMS_STORE_PASS` and `SMS_KEY_PASS` environment variables. Signing keys are not included in the repository. An APK signed with a different key cannot replace an installed maintainer-signed version directly.

The adapted Bark source is in `upstream/bark-server-2.3.9`; assistant code and runtime scripts are in `app`. The build does not depend on the maintainer's computer paths, phone database, or registration files. The Android app's current interface is Chinese; this English document provides translations of the relevant controls.

## Validation

Configuration tests cover preserving unrelated fields, updating retries, repeated execution, first-use consent, and rejection of malformed XML. Monitor interval tests cover bounds and invalid inputs. Hardware checks verified recovery after force-stopping either service, restoration of Xiaomi notification-SMS permission, manual-stop protection, the icon, and the local health endpoint. The boot script was installed and syntax-checked, but a full device reboot was not performed for this validation. The entire first-install flow has not been tested on a second, fresh phone.
