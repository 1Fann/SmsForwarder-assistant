"""Run isolated boot/worker failure tests on an authorized Android test device.

Usage: python verify-supervisor.py ADB_PATH SERIAL
Only mocks in a unique /data/local/tmp directory are executed; no real app starts.
"""
from pathlib import Path
import subprocess, shlex, sys, tempfile, time, uuid

adb_path, serial = sys.argv[1:]
base = '/data/local/tmp/bark-supervisor-test-' + uuid.uuid4().hex[:10]
def adb(*args):
    p = subprocess.run([adb_path, '-s', serial, *args], capture_output=True, timeout=30)
    if p.returncode: raise RuntimeError('ADB test command failed')
    return p.stdout.decode(errors='replace').strip()
def root(s): return adb('shell', '-T', 'su -M -c ' + shlex.quote(s))
def wait_for(check, label):
    until = time.monotonic() + 15
    while time.monotonic() < until:
        if check(): return
        time.sleep(.3)
    raise AssertionError(label)
def count(): return int(root('cat ' + base + '/count 2>/dev/null || echo 0'))
source = Path(__file__).resolve().parents[1] / 'assets' / 'monitor-boot.sh'
boot = source.read_text(encoding='utf-8').replace('/data/adb/sms-push-assistant', base).replace('sleep 10', 'sleep 1')
worker = '''#!/system/bin/sh
BASE=@BASE@
[ "$1" = loop ] || exit 2
exec /data/adb/magisk/busybox flock -n "$BASE/loop.lock" /system/bin/sh "$BASE/stub.sh"
'''.replace('@BASE@', base)
stub = '''#!/system/bin/sh
BASE=@BASE@
n=$(cat "$BASE/count" 2>/dev/null || echo 0)
n=$((n+1)); echo "$n" > "$BASE/count"
[ "$n" -gt 2 ] || exit 7
echo $$ > "$BASE/worker.pid"
trap 'rm -f "$BASE/worker.pid"' EXIT
while [ -f "$BASE/enabled" ]; do sleep 1; done
'''.replace('@BASE@', base)
try:
    adb('shell', 'mkdir -p ' + base + '/bin')
    root('touch ' + base + '/.owned ' + base + '/enabled')
    with tempfile.TemporaryDirectory() as td:
        files = {'boot.sh':boot, 'monitor.sh':worker, 'stub.sh':stub,
                 'bin/getprop':'#!/system/bin/sh\necho 1\n',
                 'bin/cmd':'#!/system/bin/sh\n[ -f ' + base + '/unlocked ] && echo RUNNING_UNLOCKED || echo RUNNING_LOCKED\n',
                 'bin/pm':'#!/system/bin/sh\necho package:mock\n'}
        for name, text in files.items():
            local = Path(td) / name.replace('/', '-')
            local.write_text(text, encoding='utf-8', newline='\n')
            adb('push', str(local), base + '/' + name)
    root('chmod 700 ' + base + '/*.sh ' + base + '/bin/*')
    launch = 'PATH=' + base + '/bin:/system/bin /system/bin/sh ' + base + '/boot.sh'
    root(launch)
    time.sleep(2)
    assert count() == 0, 'Must not start app-dependent worker before first unlock'
    root('touch ' + base + '/unlocked')
    wait_for(lambda: count() >= 3, 'Worker crash recovery')
    assert count() == 3
    root(launch)
    time.sleep(2)
    assert count() == 3, 'Duplicate hook must not start another worker'
    # Killing only the parent must release its own lock even while the old
    # monitor child is alive. Otherwise --acquire can give up forever.
    parent = root('cat ' + base + '/boot.pid')
    assert parent.isdigit()
    assert base in root('tr "\\000" " " < /proc/' + parent + '/cmdline')
    pid = root('cat ' + base + '/worker.pid')
    assert pid.isdigit()
    assert root('test ! -e /proc/' + pid + '/fd/8 && echo closed || true') == 'closed', 'Child must not inherit boot lock'
    assert root('/data/adb/magisk/busybox flock -n ' + base + '/boot.lock true; echo $?') == '1'
    root('kill -KILL ' + parent)
    assert root('kill -0 ' + pid + ' 2>/dev/null && echo alive || true') == 'alive'
    wait_for(lambda: root('/data/adb/magisk/busybox flock -n ' + base + '/boot.lock true; echo $?') == '0', 'Parent lock releases while child survives')
    root(launch)
    def parent_replaced():
        current = root('cat ' + base + '/boot.pid 2>/dev/null || true')
        return current.isdigit() and current != parent
    wait_for(parent_replaced, 'New hook must reacquire parent lock')
    assert count() == 3, 'Replaced parent must not duplicate live child'
    pid = root('cat ' + base + '/worker.pid')
    assert pid.isdigit()
    assert base in root('tr "\\000" " " < /proc/' + pid + '/cmdline')
    root('kill -KILL ' + pid)
    wait_for(lambda: count() == 4, 'Killed worker must recover')
    root('rm -f ' + base + '/enabled')
    wait_for(lambda: root('test ! -f ' + base + '/boot.pid && echo stopped || true') == 'stopped', 'Disable exits supervisor')
    before = count()
    root(launch)
    time.sleep(2)
    assert count() == before, 'Disabled monitor must not resurrect'
    print('PASS: locked boot, crash retry, single instance, parent-lock reacquisition with live child, killed-worker recovery, explicit disable',flush=True)
    # Exercise the REAL worker's ash/flock behavior, not just the supervisor mock.
    actual=(source.parent/'monitor.sh').read_text(encoding='utf-8').replace('/data/adb/sms-push-assistant',base)
    actual=actual.replace('SELF=com.mcyao.barkserver','SELF=test.bark.worker').replace('SMS=cn.ppps.forwarder','SMS=test.sms.worker')
    actual=actual.replace('PREF=/data/user/0/$SELF/shared_prefs/server.xml','PREF='+base+'/no-server.xml').replace('SMSPREF=/data/user_de/0/$SMS/shared_prefs/$SMS.xml','SMSPREF='+base+'/no-sms.xml')
    for command in ['cmd','dumpsys','pm','am']:
        actual=actual.replace('/system/bin/'+command,base+'/bin/'+command)
    with tempfile.TemporaryDirectory() as td:
        check_cmd='''#!/system/bin/sh
if [ -f @BASE@/hold-check ]; then
 echo $$ > @BASE@/query.pid
 touch @BASE@/query-entered
 while [ -f @BASE@/hold-check ]; do /data/adb/magisk/busybox sleep 0.1; done
fi
echo RUNNING_UNLOCKED
'''.replace('@BASE@',base)
        for name,text in {'monitor.sh':actual,'bin/pm':'#!/system/bin/sh\necho package:'+base+'/mock.apk\n','bin/dumpsys':'#!/system/bin/sh\nexit 0\n','bin/am':'#!/system/bin/sh\nexit 0\n','bin/cmd':check_cmd}.items():
            local=Path(td)/name.replace('/','-');local.write_text(text,encoding='utf-8',newline='\n')
            # Previously installed files are root-owned after chmod; stage via shell tmp.
            adb('push',str(local),base+'/incoming')
            root('cp '+base+'/incoming '+base+'/'+name+'; chmod 700 '+base+'/'+name)
    root('touch '+base+'/enabled '+base+'/mock.apk')
    check='/system/bin/sh '+base+'/monitor.sh once'
    # Real BusyBox timers may outlive the command: the outer timer is created
    # before fd 8, and framework timers/commands must not inherit that fd.
    root('PATH='+base+'/bin:/system/bin '+check)
    assert root('/data/adb/magisk/busybox flock -n '+base+'/check.lock true; echo $?')=='0', 'Finished check must release its lock before the outer 40-second timer expires'
    root('touch '+base+'/hold-check; rm -f '+base+'/query-entered '+base+'/check.done')
    background=check+'; echo $? > '+base+'/check.done'
    root('PATH='+base+'/bin:/system/bin /data/adb/magisk/busybox setsid /system/bin/sh -c '+shlex.quote(background)+' </dev/null >/dev/null 2>&1 &')
    wait_for(lambda:root('test -f '+base+'/query-entered && echo entered || true')=='entered','Check enters held framework query')
    assert root('/data/adb/magisk/busybox flock -n '+base+'/check.lock true; echo $?')=='1', 'Owner must retain the lock throughout its query'
    query=root('cat '+base+'/query.pid');assert query.isdigit()
    assert root('test ! -e /proc/'+query+'/fd/8 && echo closed || true')=='closed', 'Framework command must not inherit check fd'
    assert root('PATH='+base+'/bin:/system/bin /system/bin/sh '+base+'/monitor.sh fast-sms; echo $?')=='1', 'Concurrent fast check must remain excluded'
    root('rm -f '+base+'/hold-check')
    wait_for(lambda:root('cat '+base+'/check.done 2>/dev/null || true')=='0','Held check finishes')
    assert root('/data/adb/magisk/busybox flock -n '+base+'/check.lock true; echo $?')=='0', 'Released query/timers must not pin completed check lock'
    print('PASS: completed-check lock release, live-check exclusion, framework child fd closure',flush=True)
    root(launch)
    def loop_pid(): return root('cat '+base+'/loop.pid 2>/dev/null || true')
    wait_for(lambda:loop_pid().isdigit(),'Real worker starts under ash')
    old=loop_pid()
    assert root('/data/adb/magisk/busybox flock -n '+base+'/loop.lock true; echo $?')=='1'
    started=time.monotonic();root('kill -KILL '+old)
    wait_for(lambda:loop_pid().isdigit() and loop_pid()!=old,'Real worker lock releases without sleeper delay')
    elapsed=time.monotonic()-started
    assert elapsed<4, 'First real worker recovery was delayed: '+str(elapsed)
    root('rm -f '+base+'/enabled')
    wait_for(lambda:root('test ! -f '+base+'/boot.pid && echo stopped || true')=='stopped','Real worker disable')
    print('PASS: real ash worker lock, singleton, first recovery %.1fs, disable'%elapsed,flush=True)
finally:
    # Unique verified test-only root, never a production configuration path.
    assert base.startswith('/data/local/tmp/bark-supervisor-test-')
    root('rm -f ' + base + '/enabled')
    time.sleep(2)
    root('rm -rf ' + shlex.quote(base))
