#!/system/bin/sh
# sms-push-assistant-owned: reversible helper deployment; desired state persists.
set -eu
umask 077
BASE=/data/adb/sms-push-assistant
HOOK=/data/adb/service.d/97-sms-push-assistant.sh
BB=
for b in /data/adb/magisk/busybox /data/adb/ksu/bin/busybox /data/adb/ap/bin/busybox; do
 [ ! -x "$b" ] || { BB=$b; break; }
done
[ -n "$BB" ] && [ ! -L "$BASE" ] && [ -f "$BASE/.owned" ] || exit 20
# Android mksh marks numbered FDs close-on-exec. Acquire locks only in ash.
case "${1:-}" in --ash) shift;; *) exec "$BB" ash "$0" --ash "$@";; esac
exec 7>"$BASE/update.lock"
"$BB" flock -n 7 || exit 1
BACKUP="$BASE/update.rollback"
restore() {
 [ -d "$BACKUP" ] || return 0
 if [ -f "$BACKUP/ready" ]; then
  for name in monitor.sh monitor-boot.sh; do
   if [ -f "$BACKUP/$name" ]; then cp "$BACKUP/$name" "$BASE/$name.recover"; chmod 700 "$BASE/$name.recover"; mv -f "$BASE/$name.recover" "$BASE/$name"; else rm -f "$BASE/$name"; fi
  done
  if [ -f "$BACKUP/enabled" ]; then touch "$BASE/enabled"; else rm -f "$BASE/enabled"; fi
  [ ! -f "$BACKUP/minutes" ] || cp "$BACKUP/minutes" "$BASE/minutes"
  "$BB" sync
  # The old boot entry may not know how to recover. Restore it only after the
  # complete old runtime and desired state are durable.
  if [ -f "$BACKUP/boot" ]; then cp "$BACKUP/boot" "$HOOK.recover"; chmod 700 "$HOOK.recover"; mv -f "$HOOK.recover" "$HOOK"; else rm -f "$HOOK"; fi
  "$BB" sync
  # Commit recovery BEFORE deleting any backups. A second recovery must never
  # interpret an already-cleaned backup as a file that was originally absent.
  rm -f "$BACKUP/ready"
  "$BB" sync
 fi
 rm -f "$BACKUP/monitor.sh" "$BACKUP/monitor-boot.sh" "$BACKUP/boot" "$BACKUP/enabled" "$BACKUP/minutes"
 rmdir "$BACKUP"
}
restore
[ "${1:-}" != recover ] || exit 0
SOURCE=$1; ENABLED=$2; MINUTES=$3
case "$ENABLED" in 0|1) ;; *) exit 2;; esac
case "$MINUTES" in ''|*[!0-9]*|?????*) exit 2;; esac
[ "$MINUTES" -ge 1 ] && [ "$MINUTES" -le 1440 ] || exit 2
[ ! -e "$HOOK" ] || head -n 3 "$HOOK" | grep -q sms-push-assistant-owned
for name in monitor.sh monitor-boot.sh; do
 [ ! -L "$BASE/$name" ] && [ -f "$SOURCE/$name" ]
 "$BB" ash -n "$SOURCE/$name"
 cp "$SOURCE/$name" "$BASE/$name.staged"
 chmod 700 "$BASE/$name.staged"
done
mkdir "$BACKUP"
for name in monitor.sh monitor-boot.sh; do [ ! -f "$BASE/$name" ] || cp "$BASE/$name" "$BACKUP/$name"; done
[ ! -f "$HOOK" ] || cp "$HOOK" "$BACKUP/boot"
[ ! -f "$BASE/enabled" ] || touch "$BACKUP/enabled"
[ ! -f "$BASE/minutes" ] || cp "$BASE/minutes" "$BACKUP/minutes"
touch "$BACKUP/ready"
committed=0
finish() {
 code=$?;trap - EXIT TERM INT HUP
 if [ "$committed" = 0 ]; then restore || exit 1; fi
 exec 7>&-
 if [ -f "$BASE/enabled" ] && [ -f "$HOOK" ]; then /system/bin/sh "$HOOK" 7>&- || :; fi
 exit "$code"
}
trap finish EXIT
trap 'exit 1' TERM INT HUP
# Persist recovery evidence and install a recovery-aware boot entry BEFORE
# replacing either runtime file, including upgrades from pre-1.3.4 hooks.
"$BB" sync
mkdir -p /data/adb/service.d
cp "$BASE/monitor-boot.sh.staged" "$HOOK.new"
chmod 700 "$HOOK.new"
mv -f "$HOOK.new" "$HOOK"
"$BB" sync
for name in monitor.sh monitor-boot.sh; do mv -f "$BASE/$name.staged" "$BASE/$name"; done
printf '%s\n' "$MINUTES" > "$BASE/minutes"
if [ "$ENABLED" = 1 ]; then touch "$BASE/enabled"; else rm -f "$BASE/enabled"; fi
# Existing workers remain alive through every potentially failing copy above.
for slot in boot loop; do
 p=$(cat "$BASE/$slot.pid" 2>/dev/null || true)
 case "$p" in ''|*[!0-9]*|0|1) continue;; esac
 if [ -r "/proc/$p/cmdline" ] && tr '\000' ' ' <"/proc/$p/cmdline" | grep -Eq '(^| )/data/adb/(sms-push-assistant/monitor(-boot)?\.sh|service\.d/97-sms-push-assistant\.sh) +--(ash-worker|worker|loop)( |$)'; then kill -TERM "$p" 2>/dev/null || :; fi
done
# Removing the rollback marker is the commit. Partial cleanup is recovered as
# committed, because ready no longer exists; completed settings stay in effect.
"$BB" sync
rm -f "$BACKUP/ready"
"$BB" sync
committed=1
restore
