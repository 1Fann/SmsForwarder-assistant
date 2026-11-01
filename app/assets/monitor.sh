#!/system/bin/sh
# sms-push-assistant-owned: root supervision, no SMS or binding values logged.
BASE=/data/adb/sms-push-assistant
SELF=com.mcyao.barkserver
PREF=/data/user/0/$SELF/shared_prefs/server.xml
SMS=cn.ppps.forwarder
SMSPREF=/data/user_de/0/$SMS/shared_prefs/$SMS.xml
BB=
for b in /data/adb/magisk/busybox /data/adb/ksu/bin/busybox /data/adb/ap/bin/busybox; do
 [ -x "$b" ] && { BB=$b; break; }
done
[ -n "$BB" ] && [ -f "$BASE/.owned" ] || exit 20
umask 077
# Framework calls can hang independently of the app. Bound even the light checks.
cmd() { "$BB" timeout 5 /system/bin/cmd "$@" 8>&- 9>&-; }
dumpsys() { "$BB" timeout 5 /system/bin/dumpsys "$@" 8>&- 9>&-; }
pm() { "$BB" timeout 5 /system/bin/pm "$@" 8>&- 9>&-; }
am() { "$BB" timeout 8 /system/bin/am "$@" 8>&- 9>&-; }
case "${1:-loop}" in
 loop) exec "$BB" ash "$BASE/monitor.sh" --loop;;
 # Start the deadline before opening the check lock: BusyBox timeout's timer
 # can outlive its command and must never inherit the check's locked descriptor.
 once) exec "$BB" timeout 40 "$BB" ash "$BASE/monitor.sh" --acquire-check 8>&- 9>&-;;
 fast-sms) exec "$BB" timeout 20 "$BB" ash "$BASE/monitor.sh" --acquire-fast-sms 8>&- 9>&-;;
 --acquire-check|--acquire-fast-sms)
  exec 8>"$BASE/check.lock"
  "$BB" flock -n 8 || exit 1
  if [ "$1" = --acquire-check ]; then set -- --check; else set -- --fast-sms; fi;;
 --loop) ;;
 *) exit 2;;
esac
# The worker owns its lock in ash; bounded checks and sleepers cannot keep the
# lock after this process dies. Android mksh cannot pass numbered FDs to flock.
if [ "$1" = --loop ]; then
 exec 9>"$BASE/loop.lock"
 "$BB" flock -n 9 || exit 1
fi
enabled() { [ -f "$BASE/enabled" ]; }
wanted() { grep -q '<boolean name="enabled" value="true"' "$PREF" 2>/dev/null; }
ready() { [ "$(getprop sys.boot_completed)" = 1 ] && [ "$(cmd activity get-started-user-state 0 2>/dev/null | tr -d '\r')" = RUNNING_UNLOCKED ]; }
foreground() { dumpsys activity services "$SELF" 2>/dev/null | grep -q 'isForeground=true'; }
report() { printf '%s\n%s\n' "$(date +%s)" "$1" > "$BASE/status.new"; mv -f "$BASE/status.new" "$BASE/status"; }
# Recovery backoff is based on uptime and boot identity, not the adjustable clock.
retry_ready() {
 target=$1
 read retry_boot retry_next retry_count < "$BASE/$target.retry" 2>/dev/null || return 0
 [ "$retry_boot" = "$(cat /proc/sys/kernel/random/boot_id)" ] || return 0
 case "$retry_next:$retry_count" in *[!0-9:]*|:*) return 0;; esac
 now=$(cut -d. -f1 /proc/uptime)
 [ "$retry_next" -le "$now" ] || [ "$retry_next" -gt $((now + 600)) ]
}
retry_attempt() {
 target=$1
 boot=$(cat /proc/sys/kernel/random/boot_id)
 read old_boot old_next retry_count < "$BASE/$target.retry" 2>/dev/null || retry_count=0
 [ "$old_boot" = "$boot" ] || retry_count=0
 case "$retry_count" in ''|*[!0-9]*|????*) retry_count=0;; esac
 [ "$retry_count" -lt 4 ] && retry_count=$((retry_count + 1))
 delay=$((60 << (retry_count - 1)))
 now=$(cut -d. -f1 /proc/uptime)
 printf '%s %s %s\n' "$boot" "$((now + delay))" "$retry_count" > "$BASE/$target.retry"
}
healthy() {
 prefix=$(sed -n 's/.*<string name="prefix">\([a-f0-9]*\)<\/string>.*/\1/p' "$PREF")
 [ "${#prefix}" = 32 ] || return 1
 "$BB" timeout 4 "$BB" wget -q -T 3 -O - "http://127.0.0.1:18080/$prefix/ping" 8>&- 9>&- 2>/dev/null | grep -q '"message":"pong"'
}
sms_wanted() { grep -q '<boolean name="enable_sms" value="true"' "$SMSPREF" 2>/dev/null && ! grep -q '<boolean name="enable_pure_client_mode" value="true"' "$SMSPREF" 2>/dev/null; }
sms_foreground() { dumpsys activity services "$SMS/.service.ForegroundService" 2>/dev/null | grep -q 'isForeground=true'; }
background() {
 ops=$(cmd appops get "$1" 2>/dev/null)
 if printf '%s' "$ops" | grep -Eq '(RUN_ANY_IN_BACKGROUND|RUN_IN_BACKGROUND|MIUIOP\(10008\)):[[:space:]]*(ignore|deny|errored|ask)'; then
  cmd appops set "$1" RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1
  cmd appops set "$1" RUN_IN_BACKGROUND allow >/dev/null 2>&1
  pm path com.miui.securitycenter >/dev/null 2>&1 && cmd appops set "$1" 10008 allow >/dev/null 2>&1
 fi
 dumpsys deviceidle whitelist +"$1" >/dev/null 2>&1
}
check_bark() {
 wanted || { BSTATUS='Bark 已手动停止'; return; }
 background "$SELF"
 if grep -q '<boolean name="enabled" value="true"' "/data/user/0/$SELF/shared_prefs/reliable_sms.xml" 2>/dev/null; then
  pm grant "$SELF" android.permission.READ_SMS >/dev/null 2>&1
  cmd appops set --uid "$SELF" READ_SMS allow >/dev/null 2>&1
  cmd appops set "$SELF" READ_SMS allow >/dev/null 2>&1
 fi
 if foreground && healthy; then rm -f "$BASE/bark.retry"; BSTATUS='Bark 服务正常'; return; fi
 retry_ready bark || { BSTATUS='Bark 恢复冷却中，将自动重试'; return; }
 sleep 1 8>&- 9>&-
 enabled && wanted || { BSTATUS='Bark 恢复已取消'; return; }
 if foreground && healthy; then BSTATUS='Bark 服务正常'; return; fi
 retry_attempt bark
 if ! healthy; then am stopservice -n "$SELF/.BarkService" >/dev/null 2>&1; sleep 1 8>&- 9>&-; fi
 enabled && wanted || { BSTATUS='Bark 恢复已取消'; return; }
 am start-foreground-service -n "$SELF/.BarkService" --ez monitor_recovery true >/dev/null 2>&1
 sleep 3 8>&- 9>&-
 if foreground && healthy; then rm -f "$BASE/bark.retry"; BSTATUS='Bark 已自动恢复'; else BSTATUS='Bark 恢复未成功，将退避重试'; fi
}
check_sms() {
 [ -f "$SMSPREF" ] || { SSTATUS='短信转发器未初始化'; return; }
 sms_wanted || { SSTATUS='短信转发已手动关闭或处于客户端模式'; return; }
 enabled || { SSTATUS='短信检查已取消'; return; }
 background "$SMS"
 package=$(dumpsys package "$SMS" 2>/dev/null)
 fixed=0
 for op in READ_SMS RECEIVE_SMS READ_PHONE_STATE; do
  if ! printf '%s' "$package" | grep -q "android.permission.$op: granted=true"; then
   pm grant "$SMS" "android.permission.$op" >/dev/null 2>&1
   fixed=1
  fi
 done
 ops=$(cmd appops get "$SMS" 2>/dev/null)
 for op in READ_SMS RECEIVE_SMS; do
  if printf '%s' "$ops" | grep -Eq "$op:[[:space:]]*(ignore|deny|errored|ask)"; then
   cmd appops set --uid "$SMS" "$op" allow >/dev/null 2>&1
   cmd appops set "$SMS" "$op" allow >/dev/null 2>&1
   fixed=1
  fi
 done
 if printf '%s' "$ops" | grep -Eq 'MIUIOP\(10018\):[[:space:]]*(ignore|deny|errored|ask)'; then
  cmd appops set "$SMS" 10018 allow >/dev/null 2>&1
  fixed=1
 fi
 enabled && sms_wanted || { SSTATUS='短信转发已手动关闭'; return; }
 if ! sms_foreground; then
  retry_ready sms || { SSTATUS='短信转发器恢复冷却中，将自动重试'; return; }
  retry_attempt sms
  am start-foreground-service -n "$SMS/.service.ForegroundService" -a START >/dev/null 2>&1
  fixed=1
  sleep 2 8>&- 9>&-
 fi
 ops=$(cmd appops get "$SMS" 2>/dev/null)
 package=$(dumpsys package "$SMS" 2>/dev/null)
 if ! sms_foreground || printf '%s' "$ops" | grep -Eq '(READ_SMS|RECEIVE_SMS|MIUIOP\(10018\)):[[:space:]]*(ignore|deny|errored|ask)' || ! printf '%s' "$package" | grep -q 'android.permission.RECEIVE_SMS: granted=true'; then
  SSTATUS='短信转发器需要手动检查权限或后台设置'
 elif [ "$fixed" = 1 ]; then rm -f "$BASE/sms.retry"; SSTATUS='短信转发器已自动恢复'; else rm -f "$BASE/sms.retry"; SSTATUS='短信转发器服务与接收权限正常'; fi
}
if [ "$1" = --fast-sms ]; then
 # Only a cached PID death enters here. Restore the wanted service before the
 # unrelated Bark/TLS and permission checks; the same exclusion/backoff applies.
 enabled && sms_wanted && ready || exit 0
 sms_foreground && exit 0
 retry_ready sms || exit 0
 enabled && sms_wanted || exit 0
 retry_attempt sms
 am start-foreground-service -n "$SMS/.service.ForegroundService" -a START >/dev/null 2>&1
 exit 0
fi
if [ "$1" = --check ]; then
 enabled || exit 0
 ready || { report '等待开机及首次解锁'; exit 0; }
 APK=$(pm path "$SELF" 2>/dev/null | sed -n 's/^package://p' | head -n 1)
 [ -n "$APK" ] && [ -f "$APK" ] || exit 0
 check_bark
 check_sms
 report "$BSTATUS；$SSTATUS"
 exit 0
fi
printf '%s\n' "$$" > "$BASE/loop.pid"
trap 'rm -f "$BASE/loop.pid"' EXIT
trap 'exit 0' TERM INT
while enabled; do
 pm path "$SELF" 9>&- >/dev/null 2>&1 || break
 /system/bin/sh "$BASE/monitor.sh" once 9>&-
 result=$?
 [ "$result" -eq 124 ] && report '系统检查超时，将自动重试'
 MINUTES=$(cat "$BASE/minutes" 2>/dev/null 9>&-)
 case "$MINUTES" in ''|*[!0-9]*|?????*) MINUTES=5;; esac
 [ "$MINUTES" -ge 1 ] && [ "$MINUTES" -le 1440 ] || MINUTES=5
 LEFT=$((MINUTES * 60))
 # Cache just the main process IDs. The one-second fast path is a shell builtin;
 # expensive Android service and HTTP checks retain their existing cadence.
 BARK_PID=$(pidof "$SELF" 9>&-); BARK_PID=${BARK_PID%% *}
 SMS_PID=$(pidof "$SMS" 9>&-); SMS_PID=${SMS_PID%% *}
 TICK=0
 while [ "$LEFT" -gt 0 ] && enabled; do
  [ -f "$BASE/now" ] && { rm -f "$BASE/now" 9>&-; break; }
  sleep 1 9>&-
  LEFT=$((LEFT - 1)); TICK=$((TICK + 1))
  if [ -n "$BARK_PID" ] && ! kill -0 "$BARK_PID" 2>/dev/null; then
   BARK_PID=
   wanted 9>&- && break
  fi
  if [ -n "$SMS_PID" ] && ! kill -0 "$SMS_PID" 2>/dev/null; then
   SMS_PID=
    if sms_wanted 9>&-; then
     /system/bin/sh "$BASE/monitor.sh" fast-sms 9>&-
     break
    fi
  fi
  if [ $((TICK % 10)) -eq 0 ] && ready 9>&-; then
   if wanted 9>&- && ! foreground 9>&-; then break; fi
   if sms_wanted 9>&- && ! sms_foreground 9>&-; then break; fi
   BARK_PID=$(pidof "$SELF" 9>&-); BARK_PID=${BARK_PID%% *}
   SMS_PID=$(pidof "$SMS" 9>&-); SMS_PID=${SMS_PID%% *}
  fi
 done
done
