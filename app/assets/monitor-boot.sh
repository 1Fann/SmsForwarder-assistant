#!/system/bin/sh
# sms-push-assistant-owned
BASE=/data/adb/sms-push-assistant
BB=
for b in /data/adb/magisk/busybox /data/adb/ksu/bin/busybox /data/adb/ap/bin/busybox; do
 [ -x "$b" ] && { BB=$b; break; }
done
[ -n "$BB" ] && [ -f "$BASE/.owned" ] && [ -f "$BASE/enabled" ] || exit 0
if [ "${1:-}" != --worker ]; then
 "$BB" setsid "$BB" flock -n "$BASE/boot.lock" /system/bin/sh "$0" --worker </dev/null >/dev/null 2>&1 3>&- 4>&- 5>&- 6>&- 7>&- 8>&- 9>&- &
 exit 0
fi
while [ -f "$BASE/enabled" ]; do
 [ "$(getprop sys.boot_completed)" = 1 ] && [ "$(cmd activity get-started-user-state 0 2>/dev/null | tr -d '\r')" = RUNNING_UNLOCKED ] && break
 sleep 5
done
[ -f "$BASE/enabled" ] || exit 0
"$BB" setsid /system/bin/sh "$BASE/monitor.sh" loop </dev/null >/dev/null 2>&1 3>&- 4>&- 5>&- 6>&- 7>&- 8>&- 9>&- &
