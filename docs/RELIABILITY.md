# Reliability changes in 1.3.1 / 1.3.1 可靠性更新

- The boot hook now supervises the checker and restarts it after exit. Duplicate hooks share a file lock. An upgrade allows a previous bounded check time to release its lock.
- Framework queries have timeouts; repeated unsuccessful service recovery backs off for 60, 120, 240, then 480 seconds. A successful health check clears the backoff. Boot identity and uptime avoid wall-clock changes affecting retry times.
- Explicit stop remains authoritative. A sticky Android restart or delayed recovery request cannot start the Bark engine before checking the saved desired state.
- Existing configuration, pairing, notification routing, monitor interval and wake-lock policy are retained. No SMS is sent by these tests.

开机入口持续监督检测进程；检测退出后重新拉起。恢复失败后采用递增冷却时间，避免每十秒反复启动故障服务。手动停止、首次解锁等待和原有唤醒策略均保留。没有把配对、密码或其他私密数据搬到解锁前存储区。

## 1.3.2 recovery speed / 恢复速度

- A stable checker that exits is retried after one second; repeated short failures back off up to 30 seconds.
- Cached application PIDs are checked once per second using a shell builtin. The ten-second foreground-service check and the configured full-check interval remain separate. PID reuse, suspend, and initialization can make actual recovery take longer.
- The native Bark engine retries its first crash after one second, with bounded backoff for repeated crashes.
- Both parent and loop locks are owned by BusyBox ash. Long-lived children close the parent lock descriptor, allowing a new supervisor to recover ownership after its predecessor exits.
- Upgrade and stop operations recognize exact owned worker paths and complete `--ash-worker`, `--worker`, and `--loop` arguments. Manual stop and unsuccessful-service recovery cooldowns remain effective.

## 1.3.3 first-failure latency / 首次恢复等待

- A stable checker and the Bark engine are rebuilt immediately after their first exit. Repeated short failures still back off; no faster periodic polling or new wake lock is introduced.
- A cached `cn.ppps.forwarder` main-process exit now requests its wanted foreground service before unrelated Bark and permission checks. This path retains the shared check lock, first-unlock check, SMS enabled/client-mode preferences, foreground-service recheck, recovery cooldown and a final desired-state check. The normal full check confirms readiness and repairs permissions afterward.
- The new offline script fixture covers immediate first restart, bounded repeated-failure backoff, stability reset, the SMS fast path, manual disable, client mode, locked boot, a stop during the check, a service already running, cooldown and lock contention. Commands are mocked; no phone, SMS or notification is used.

这里缩短的是本机服务恢复准备，不是短信投递证明。恢复仅请求原前台服务启动，不创建短信或测试通知；官方转发器启动时会恢复其已有 WorkManager/定时任务，已有用户任务仍可能继续发送。前台服务、短信接收器及权限就绪和 Bark 本机 ping 应分别记录；不能把 Bark 就绪当作转发器已验证。

## 1.3.3 check-lock lifetime / 检查锁生命周期

- The total deadline starts before the checker opens its lock. BusyBox ash owns the check lock explicitly on descriptor 8 until the whole check exits. Framework/HTTP timeout helpers and sleeps close inherited lock descriptors, so a surviving timer cannot delay the next check. Concurrent full and fast checks still share one lock.
- Offline fixtures verify that the checker opens its lock after the deadline is established and each timeout helper receives a closed descriptor. The Android isolation fixture also checks immediate lock release after completion, exclusion during a held mock framework query and the query child's descriptor closure. Device recovery timing remains separate from these assertions.

完整检查与快速恢复仍互斥；只是避免检查结束后遗留计时子进程继续占锁。没有降低首次解锁、手动停止、前台服务复查或失败退避条件，不能仅凭此修正认定此前所有延迟都由锁引起。

### 2026-10-31 Magisk isolation verification / 真机隔离验证

在 Redmi Note 10 Pro / MIUI 14 / Magisk 设备上运行 `app/tests/verify-supervisor.py`，全部隔离用例通过：锁定启动等待、连续退出退避、重复入口互斥、父进程被杀后重新获取锁、检查工作进程被杀恢复、手动停用，以及完整检查结束后立即释放锁、检查进行时拒绝并发快速恢复、框架查询子进程不继承检查锁。真实 BusyBox ash 工作进程首次恢复的本次样本为 **0.3 秒**。

All isolated cases passed on the Redmi Note 10 Pro / MIUI 14 / Magisk device, including immediate completed-check lock release, exclusion during a held mock query and closure of the query child's lock descriptor. The real BusyBox ash checker recovered in **0.3 seconds** in this isolated sample. These assertions use mock services and do not establish the recovery time of the real SmsForwarder or Bark service, message delivery, or a worst-case deadline.

对应同版本 1.3.3 / code 7 安装包已通过原签名核验并覆盖安装，SHA-256：`9662ad322dfda03759bea3ced46a7541991d312a2e77774fe1bc59477d0ae942`。实际业务恢复时间另行记录；隔离测试没有启动真实短信服务或发送短信、推送。

## Verification / 验证

Run the Java configuration tests with `test.ps1`. On an explicitly selected Android test device, run:

```text
python app/tests/verify-supervisor.py ADB_PATH DEVICE_SERIAL
```

The integration test uses mock workers in a unique `/data/local/tmp/bark-supervisor-test-*` directory. It covers locked boot, repeated worker crashes, duplicate hook launches, SIGKILL recovery and manual disable. It does not touch production app data, start real services or send push notifications.

深度休眠会影响普通 shell 轮询；本次未添加无条件全局唤醒。首次解锁前仍等待应用私有数据可用。本机健康接口正常不能证明 Apple APNs 已投递，整机重启及长期息屏需要独立实测。

## 2026-10-31 Redmi / MIUI 14 device verification

Installed same-signature updates on one Redmi Note 10 Pro running Android 13, MIUI 14 and Magisk. Existing application data and monitor settings were retained. The device currently has no secure lock-screen credential; no lock-screen setting was changed.

真实息屏测试中，分别结束五项套件的检测工作进程，均自动恢复：Voice SIP 15.3 秒、Asterisk 6.8 秒、Bark 11.2 秒、短信互联 11.1 秒、DDNS 28.6 秒。这里只结束检测工作进程，没有用结束真实通话或发送短信来测试。

整机重启一次，全程没有手动解锁或打开应用；约 67 秒后，所需前台服务和重启后的新检测记录齐备。随后确认网关 SIP 注册、Shizuku 连接、音频就绪、本机短信 HTTPS、Bark ping 及 DDNS 本机健康正常。DDNS 配置和原偏好摘要保持一致。

随后强制进入深度 Doze 75 秒，Voice SIP、Asterisk、短信互联和 Bark 的检测记录均在此期间更新，电话检查状态正常。已退出强制 Doze，恢复正常电源策略。

These are service-readiness tests, not new end-to-end cellular calls, actual SMS delivery, APNs delivery, or an overnight battery-powered suspend test. No automatic real calls or messages were sent. Unattended boot on this device does not establish Direct Boot support before first credential unlock on a password-protected device. Existing wake-lock preferences were preserved; no global battery policy was disabled.

## 2026-10-31 recovery-speed follow-up

The second pass removes fixed first-failure waits, adds inexpensive cached-process checks, and keeps backoff for repeated failures. Full network checks retain their configured cadence. These measurements replace neither the original baseline above nor its stated limitations.

| Component | Checker replacement (s) | Actual local service ready after process kill (s) |
|---|---:|---:|
| voice | 0.3 | 13.16 |
| asterisk | 0.2 | 11.77 |
| sms | 1.9 | 2.69 |
| bark | 1.4 | 1.48 |
| ddns | 2.1 | 1.41 |

Phone readiness required SIP registration plus the gateway’s connected Shizuku and ready audio flags; PBX readiness required the engine and gateway registration. SMS, Bark and DDNS required successful local HTTP(S) responses, with the configured Host header retained for SMS. No real call, SMS or push notification was generated. Measurements include polling overhead and are not upper bounds for network faults or repeated failures.

In the final unattended reboot, Android reported boot completion at 36.8 seconds; all five service checks were simultaneously ready by 69.39 seconds. These timings include the operating-system restart and polling overhead; the initial 67-second baseline above used foreground-service presence and fresh monitor records, so it is not the same measurement.

The unattended reboot phase measurements and final installed artifact hashes are recorded in `D:/CodeX/HELP/reliability-20261031/speed-verification.json`. No unlock input or app-launch request was sent by that test. The device had no secure screen-lock credential before testing; no lock-screen setting was changed.

<!-- recovery-target-20261031:start -->
## 2026-10-31 恢复目标实机复测

红米 Note 10 Pro / MIUI 14，接电、息屏、ADB 连接，沿用现有唤醒设置。每次终止单个进程前均确认蜂窝与 PBX 空闲。

- 守护工作进程（Bark）：0.55, 0.61 s（范围 0.55–0.61 s）。
- Bark业务进程退出至本地就绪：2.06, 1.84 s（范围 1.84–2.06 s）。整机重启后该项首次观测就绪：44.11 s。
- 短信转发器业务进程退出至本地就绪：3.20, 3.34 s（范围 3.20–3.34 s）。整机重启后该项首次观测就绪：44.74 s。
- 短信转发器使用 Bark 助手的同一守护，表中不重复计为独立守护。

重启各时间均从同一次重启请求起算。全部六项首次齐备 61.97 s，稳定确认 66.12 s；无安全锁屏凭据，观察者未输入解锁、未主动启动界面或服务。强制深度休眠 77.66 s，4 次抽查六项服务与五个工作进程均正常，结束后已解除强制状态。

数值为本机含探测开销的首次观测，不是普适上限。守护与业务分别连续抽样确认至少 1 秒和 3 秒。电话就绪检查含网关状态及匹配的 PBX 注册记录；本次未拨打真实电话、发送短信或通知，也未验证外网送达或双向通话音频。
<!-- recovery-target-20261031:end -->
