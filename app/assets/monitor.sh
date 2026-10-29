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
case "${1:-loop}" in
 loop) exec "$BB" flock -n "$BASE/loop.lock" /system/bin/sh "$BASE/monitor.sh" --loop;;
 once) exec "$BB" flock -n "$BASE/check.lock" "$BB" timeout 40 /system/bin/sh "$BASE/monitor.sh" --check;;
 --loop|--check) ;;
 *) exit 2;;
esac
enabled() { [ -f "$BASE/enabled" ]; }
wanted() { grep -q '<boolean name="enabled" value="true"' "$PREF" 2>/dev/null; }
ready() { [ "$(getprop sys.boot_completed)" = 1 ] && [ "$(cmd activity get-started-user-state 0 2>/dev/null | tr -d '\r')" = RUNNING_UNLOCKED ]; }
foreground() { dumpsys activity services "$SELF" 2>/dev/null | grep -q 'isForeground=true'; }
report() { printf '%s\n%s\n' "$(date +%s)" "$1" > "$BASE/status.new"; mv -f "$BASE/status.new" "$BASE/status"; }
healthy() {
 prefix=$(sed -n 's/.*<string name="prefix">\([a-f0-9]*\)<\/string>.*/\1/p' "$PREF")
 [ "${#prefix}" = 32 ] || return 1
 "$BB" timeout 4 "$BB" wget -q -T 3 -O - "http://127.0.0.1:18080/$prefix/ping" 2>/dev/null | grep -q '"message":"pong"'
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
 if foreground && healthy; then BSTATUS='Bark 服务正常'; return; fi
 sleep 1
 enabled && wanted || { BSTATUS='Bark 恢复已取消'; return; }
 if foreground && healthy; then BSTATUS='Bark 服务正常'; return; fi
 if ! healthy; then am stopservice -n "$SELF/.BarkService" >/dev/null 2>&1; sleep 1; fi
 enabled && wanted || { BSTATUS='Bark 恢复已取消'; return; }
 am start-foreground-service -n "$SELF/.BarkService" --ez monitor_recovery true >/dev/null 2>&1
 sleep 3
 if foreground && healthy; then BSTATUS='Bark 已自动恢复'; else BSTATUS='Bark 恢复未成功，请检查'; fi
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
  am start-foreground-service -n "$SMS/.service.ForegroundService" -a START >/dev/null 2>&1
  fixed=1
  sleep 2
 fi
 ops=$(cmd appops get "$SMS" 2>/dev/null)
 package=$(dumpsys package "$SMS" 2>/dev/null)
 if ! sms_foreground || printf '%s' "$ops" | grep -Eq '(READ_SMS|RECEIVE_SMS|MIUIOP\(10018\)):[[:space:]]*(ignore|deny|errored|ask)' || ! printf '%s' "$package" | grep -q 'android.permission.RECEIVE_SMS: granted=true'; then
  SSTATUS='短信转发器需要手动检查权限或后台设置'
 elif [ "$fixed" = 1 ]; then SSTATUS='短信转发器已自动恢复'; else SSTATUS='短信转发器服务与接收权限正常'; fi
}
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
 pm path "$SELF" >/dev/null 2>&1 || break
 /system/bin/sh "$BASE/monitor.sh" once
 MINUTES=$(cat "$BASE/minutes" 2>/dev/null)
 case "$MINUTES" in ''|*[!0-9]*|?????*) MINUTES=5;; esac
 [ "$MINUTES" -ge 1 ] && [ "$MINUTES" -le 1440 ] || MINUTES=5
 LEFT=$((MINUTES * 60))
 while [ "$LEFT" -gt 0 ] && enabled; do
  [ -f "$BASE/now" ] && { rm -f "$BASE/now"; break; }
  sleep 5
  LEFT=$((LEFT - 5))
  if [ $((LEFT % 10)) -eq 0 ] && ready; then
   if wanted && ! foreground; then break; fi
   if sms_wanted && ! sms_foreground; then break; fi
  fi
 done
done
