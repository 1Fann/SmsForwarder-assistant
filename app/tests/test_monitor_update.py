"""Isolated production updater control flow; no Android or real service access."""
from pathlib import Path
import os, subprocess, tempfile, unittest, sys, shutil

SHELL=os.environ.get('TEST_SHELL',shutil.which('sh') or str(Path.home()/'.cache/codex-runtimes/codex-primary-runtime/dependencies/native/git/usr/bin/sh.exe'))
SOURCE=Path(__file__).resolve().parents[1]/'assets/monitor-update.sh'

@unittest.skipUnless(Path(SHELL).is_file(),'POSIX shell unavailable')
class MonitorUpdateTests(unittest.TestCase):
 def run_case(self,fail=False,interrupted=False,kill_during_replace=False,recovery_kill=None):
  with tempfile.TemporaryDirectory(prefix='bark-update-') as tmp:
   root=Path(tmp);stage=root/'input';stage.mkdir();(root/'.owned').touch();(root/'enabled').touch();(root/'minutes').write_text('5\n')
   old='#!/bin/sh\n# sms-push-assistant-owned\necho old >> "$FIXTURE/launched"\n'
   new='#!/bin/sh\n# sms-push-assistant-owned\necho new >> "$FIXTURE/launched"\n'
   if kill_during_replace:new='#!/bin/sh\n# sms-push-assistant-owned\nsh "$FIXTURE/update.sh" recover\necho new >> "$FIXTURE/launched"\n'
   for name in ['monitor.sh','monitor-boot.sh']:(root/name).write_text(old);(stage/name).write_text(new)
   (root/'hook.sh').write_text(old)
   (root/'busybox').write_text('#!/bin/sh\ncase "$1" in flock) exit 0;; ash) shift; exec sh "$@";; esac\n')
   script=SOURCE.read_text();start=script.index('BB=\n');end=script.index('[ -n "$BB" ]',start)
   script=script[:start]+'BB="$FIXTURE/busybox"\n'+script[end:]
   script=script.replace('BASE=/data/adb/sms-push-assistant','BASE="$FIXTURE"').replace('HOOK=/data/adb/service.d/97-sms-push-assistant.sh','HOOK="$FIXTURE/hook.sh"').replace('mkdir -p /data/adb/service.d','mkdir -p "$FIXTURE"').replace('/system/bin/sh','sh')
   if fail:script=script.replace('mkdir -p "$FIXTURE"','exit 9 # injected copy/commit failure')
   if kill_during_replace:script=script.replace('mv -f "$BASE/$name.staged" "$BASE/$name"; done','mv -f "$BASE/$name.staged" "$BASE/$name"; kill -9 $$; done')
   if interrupted:
    backup=root/'update.rollback';backup.mkdir()
    for name in ['monitor.sh','monitor-boot.sh']:(backup/name).write_text(old);(root/name).write_text(new)
    (backup/'boot').write_text(old);(backup/'enabled').touch();(backup/'minutes').write_text('5\n');(backup/'ready').touch()
    (root/'enabled').unlink();(root/'hook.sh').write_text(new)
   if recovery_kill:
    (root/'inject-once').touch()
    injection='\n if [ -f "$FIXTURE/inject-once" ]; then rm -f "$FIXTURE/inject-once"; kill -9 $$; fi\n'
    points={
     'runtime':'mv -f "$BASE/$name.recover" "$BASE/$name";',
     'settings':'[ ! -f "$BACKUP/minutes" ] || cp "$BACKUP/minutes" "$BASE/minutes"',
     'boot':'mv -f "$HOOK.recover" "$HOOK"; else rm -f "$HOOK"; fi',
     'commit':'rm -f "$BACKUP/ready"',
     'cleanup':'rm -f "$BACKUP/monitor.sh" "$BACKUP/monitor-boot.sh" "$BACKUP/boot" "$BACKUP/enabled" "$BACKUP/minutes"'}
    point=points[recovery_kill];self.assertIn(point,script);script=script.replace(point,point+injection,1)
   target=root/'update.sh';target.write_text(script,newline='\n')
   # This bundled Windows shell has no coreutils. Use real Python filesystem
   # operations for the fixture; production branching remains unchanged.
   helper=root/'fs.py';helper.write_text('''import sys,pathlib,shutil,re
cmd=sys.argv[1];args=sys.argv[2:];p=[a for a in args if not a.startswith('-')]
if cmd=='cp':shutil.copyfile(p[0],p[1])
elif cmd=='mv':pathlib.Path(p[0]).replace(p[1])
elif cmd=='rm':
 for x in p:pathlib.Path(x).unlink(missing_ok=True)
elif cmd=='mkdir':
 for x in p:pathlib.Path(x).mkdir(parents='-p' in args,exist_ok='-p' in args)
elif cmd=='rmdir':pathlib.Path(p[0]).rmdir()
elif cmd=='chmod':pass
elif cmd=='cat':sys.stdout.write(pathlib.Path(p[0]).read_text())
elif cmd=='head':sys.stdout.write(''.join(pathlib.Path(p[-1]).read_text().splitlines(True)[:3]))
elif cmd=='grep':sys.exit(0 if re.search(p[0],sys.stdin.read()) else 1)
''')
   functions='\n'.join(n+'() { "$TASK_PY" "$FIXTURE/fs.py" '+n+' "$@"; }' for n in ['cp','mv','rm','mkdir','rmdir','chmod','cat','head','grep'])
   script=script.replace('set -eu','set -eu\n'+functions)
   target.write_text(script,newline='\n')
   env={**os.environ,'FIXTURE':root.as_posix(),'TASK_PY':Path(sys.executable).as_posix(),'PATH':str(Path(SHELL).parent)+os.pathsep+os.environ.get('PATH','')}
   args=['recover'] if interrupted else [stage.as_posix(),'1','6']
   result=subprocess.run([SHELL,str(target),*args],env=env,capture_output=True)
   if recovery_kill:
    self.assertNotEqual(result.returncode,0)
    result=subprocess.run([SHELL,str(target),'recover'],env=env,capture_output=True)
   if kill_during_replace:
    self.assertNotEqual(result.returncode,0)
    # Windows locks an open script against rename; Linux/Android permits the
    # running boot file to be replaced. Read it first to retain that behavior.
    subprocess.run([SHELL,'-c',(root/'hook.sh').read_text()],env=env,check=True,capture_output=True)
   else:self.assertEqual(result.returncode,9 if fail else 0,result.stderr.decode())
   self.assertTrue((root/'enabled').exists())
   expected=old if fail or interrupted or kill_during_replace else new
   for name in ['monitor.sh','monitor-boot.sh','hook.sh']:self.assertEqual((root/name).read_text(),expected,name)
   self.assertFalse((root/'update.rollback').exists())
 def test_success(self):self.run_case()
 def test_partial_replace_failure_rolls_back(self):self.run_case(fail=True)
 def test_interrupted_transaction_recovers(self):self.run_case(interrupted=True)
 def test_legacy_hook_upgrade_recovers_after_kill_between_replacements(self):self.run_case(kill_during_replace=True)
 def test_recovery_is_idempotent_at_every_boundary(self):
  for point in ['runtime','settings','boot','commit','cleanup']:
   with self.subTest(point=point):self.run_case(interrupted=True,recovery_kill=point)

if __name__=='__main__':unittest.main()
