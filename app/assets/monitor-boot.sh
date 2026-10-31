#!/system/bin/sh
# sms-push-assistant-owned
BASE=/data/adb/sms-push-assistant
BB=
for b in /data/adb/magisk/busybox /data/adb/ksu/bin/busybox /data/adb/ap/bin/busybox; do
 [ -x "$b" ] && { BB=$b; break; }
done
[ -n "$BB" ] && [ -f "$BASE/.owned" ] && [ -f "$BASE/enabled" ] || exit 0
case "${1:-}" in
 --acquire|--worker) exec "$BB" ash "$0" --ash-worker;;
 --ash-worker) ;;
 *) "$BB" setsid /system/bin/sh "$0" --acquire </dev/null >/dev/null 2>&1 3>&- 4>&- 5>&- 6>&- 7>&- 8>&- 9>&- &
    exit 0;;
esac
# Own the parent lock in ash, not in a flock wrapper whose descriptor can be
# inherited by the long-lived monitor. mksh cannot pass numbered FDs to flock.
exec 8>"$BASE/boot.lock"
attempt=0
acquired=
# Keep the upgrade-acquisition window while old bounded checks finish.
while [ -f "$BASE/enabled" ] && [ -f "$BASE/.owned" ] && [ "$attempt" -lt 6 ]; do
 if "$BB" flock -n 8; then acquired=yes; break; fi
 attempt=$((attempt + 1))
 sleep 10 8>&-
done
[ "$acquired" = yes ] || exit 0
# Supervise the checker itself. Magisk runs the hook once; it does not restart it.
printf '%s\n' "$$" > "$BASE/boot.pid"
trap 'rm -f "$BASE/boot.pid"' EXIT
trap 'exit 0' TERM INT
delay=0
while [ -f "$BASE/enabled" ] && [ -f "$BASE/.owned" ]; do
 if [ "$(getprop sys.boot_completed 8>&-)" = 1 ] && [ "$(exec 8>&-; "$BB" timeout 5 cmd activity get-started-user-state 0 2>/dev/null | tr -d '\r')" = RUNNING_UNLOCKED ]; then
  started=$(cut -d. -f1 /proc/uptime 8>&-)
  "$BB" timeout 5 pm path com.mcyao.barkserver >/dev/null 2>&1 8>&- && /system/bin/sh "$BASE/monitor.sh" loop 8>&-
  ended=$(cut -d. -f1 /proc/uptime 8>&-)
   [ $((ended - started)) -ge 30 ] && delay=0
   # Rebuild a stable worker immediately; only repeated short failures wait.
   [ "$delay" -eq 0 ] || sleep "$delay" 8>&-
   if [ "$delay" -eq 0 ]; then delay=1; else delay=$((delay * 2)); fi
   [ "$delay" -le 30 ] || delay=30
 else
  sleep 1 8>&-
 fi
done
