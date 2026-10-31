"""Offline real-script regression: mock Android commands, never contact a phone."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

SHELL = shutil.which("sh")
if "--shell" in sys.argv:
    index = sys.argv.index("--shell")
    SHELL = sys.argv[index + 1]
    del sys.argv[index:index + 2]
ASSETS = Path(__file__).resolve().parents[1] / "assets"


@unittest.skipUnless(SHELL, "POSIX shell required")
class RecoverySpeedTest(unittest.TestCase):
    def fixture(self, scenario, boot=False):
        with tempfile.TemporaryDirectory(prefix="bark-speed-") as temporary:
            base = Path(temporary)
            commands = base / "bin"
            commands.mkdir()
            (base / "enabled").touch()
            (base / ".owned").touch()
            if scenario == "disabled":
                (base / "enabled").unlink()
            (base / "sms.xml").write_text('<boolean name="enable_sms" value="%s" />\n' % ("false" if scenario == "off" else "true") + ('<boolean name="enable_pure_client_mode" value="true" />' if scenario == "client" else ""))
            if scenario == "cooldown":
                (base / "sms.retry").write_text("fixture-boot 160 1\n", newline="\n")
            monitor = (ASSETS / "monitor.sh").read_text(encoding="utf-8")
            monitor = monitor.replace("BASE=/data/adb/sms-push-assistant", 'BASE="$FIXTURE"')
            monitor = monitor.replace("SMSPREF=/data/user_de/0/$SMS/shared_prefs/$SMS.xml", 'SMSPREF="$FIXTURE/sms.xml"')
            begin = monitor.index("BB=\n")
            end = monitor.index('[ -n "$BB" ]', begin)
            monitor = monitor[:begin] + 'BB="$FIXTURE/bin/busybox"\n' + monitor[end:]
            for name in ["cmd", "dumpsys", "pm", "am"]:
                monitor = monitor.replace("/system/bin/" + name, '"$FIXTURE/bin/' + name + '"')
            monitor = monitor.replace("/system/bin/sh", '"$TEST_SHELL"')
            (base / "monitor.sh").write_text(monitor, encoding="utf-8", newline="\n")
            mocks = {
                "busybox": '''case "$1" in
                    ash) shift; exec "$TEST_SHELL" "$@";;
                    flock) echo flock >> "$FIXTURE/events"; [ "$SCENARIO" != locked ] || exit 1
                     [ "$3" = 8 ] || exit 81
                     (: >&8) 2>/dev/null || exit 82
                     echo owner-held >> "$FIXTURE/descriptors"
                     shift; [ "$1" != -n ] || shift; shift
                     [ "$#" -eq 0 ] && exit 0; exec "$@";;
                    timeout)
                     if (: >&8) 2>/dev/null; then echo leaked >> "$FIXTURE/descriptors"; exit 83; fi
                     echo timer-unlocked >> "$FIXTURE/descriptors"
                     shift 2; exec "$@";;
                    setsid) shift; exec "$@";;
                    *) exit 80;; esac''',
                "getprop": 'echo 1',
                "cmd": 'echo cmd >> "$FIXTURE/events"; [ "$SCENARIO" = bootlocked ] && echo RUNNING_LOCKED || echo RUNNING_UNLOCKED',
                "dumpsys": '''echo dumpsys >> "$FIXTURE/events"
                    [ "$SCENARIO" != alive ] || echo isForeground=true
                    if [ "$SCENARIO" = stopped_during_check ]; then printf '<boolean name="enable_sms" value="false" />' > "$FIXTURE/sms.xml"; fi''',
                "pm": 'echo forbidden-pm >> "$FIXTURE/events"; exit 90',
                "am": 'echo start >> "$FIXTURE/events"; exit 0',
                "sleep": 'echo "$1" >> "$FIXTURE/sleeps"',
                "cat": '''case "$1" in /proc/sys/kernel/random/boot_id) echo fixture-boot;; *) exec /usr/bin/cat "$@";; esac''',
                "cut": '''if [ "$3" = /proc/uptime ]; then
                    n=0; [ ! -f "$FIXTURE/ticks" ] || read n < "$FIXTURE/ticks"; n=$((n+1)); echo "$n" > "$FIXTURE/ticks"
                    if [ "$SCENARIO" = stable ] && [ "$n" -ge 4 ]; then echo 200; else echo 100; fi
                    else exec /usr/bin/cut "$@"; fi''',
            }
            if boot:
                mocks["pm"] = "echo package:fixture"
                supervisor = (ASSETS / "monitor-boot.sh").read_text(encoding="utf-8").replace("BASE=/data/adb/sms-push-assistant", 'BASE="$FIXTURE"')
                begin = supervisor.index("BB=\n")
                end = supervisor.index('[ -n "$BB" ]', begin)
                supervisor = supervisor[:begin] + 'BB="$FIXTURE/bin/busybox"\n' + supervisor[end:]
                supervisor = supervisor.replace("/system/bin/sh", '"$TEST_SHELL"')
                (base / "boot.sh").write_text(supervisor, encoding="utf-8", newline="\n")
                (base / "monitor.sh").write_text('''n=0
[ ! -f "$FIXTURE/count" ] || read n < "$FIXTURE/count"
n=$((n+1)); echo "$n" > "$FIXTURE/count"
[ "$n" -lt 9 ] || rm -f "$FIXTURE/enabled"
exit 7
''', encoding="utf-8", newline="\n")
            for name, source in mocks.items():
                target = commands / name
                target.write_text("#!/bin/sh\n" + source + "\n", encoding="utf-8", newline="\n")
                target.chmod(0o755)
            env = dict(os.environ, FIXTURE=base.as_posix(), TEST_SHELL=Path(SHELL).as_posix(), SCENARIO=scenario)
            env["PATH"] = str(commands) + os.pathsep + str(Path(SHELL).parent) + os.pathsep + env.get("PATH", "")
            result = subprocess.run([SHELL, str(base / ("boot.sh" if boot else "monitor.sh")), "--ash-worker" if boot else "fast-sms"], env=env, capture_output=True, text=True, timeout=10)
            self.assertEqual(1 if scenario == "locked" else 0, result.returncode, result.stderr)
            events = (base / "events").read_text().splitlines() if (base / "events").exists() else []
            sleeps = (base / "sleeps").read_text().splitlines() if (base / "sleeps").exists() else []
            descriptors = (base / "descriptors").read_text().splitlines() if (base / "descriptors").exists() else []
            self.assertNotIn("leaked", descriptors)
            if not boot and scenario != "locked":
                self.assertEqual(["timer-unlocked", "owner-held"], descriptors[:2])
            if not boot and scenario == "missing":
                self.assertEqual(4, descriptors.count("timer-unlocked"))
            return events, sleeps

    def test_sms_loss_requests_start_before_unrelated_permission_or_bark_checks(self):
        events, pauses = self.fixture("missing")
        self.assertEqual(["flock", "cmd", "dumpsys", "start"], events)
        self.assertEqual([], pauses)

    def test_fast_path_preserves_disabled_manual_stop_client_mode_and_unlock(self):
        for scenario in ["disabled", "off", "client", "bootlocked", "stopped_during_check"]:
            with self.subTest(scenario=scenario):
                self.assertNotIn("start", self.fixture(scenario)[0])

    def test_fast_path_preserves_existing_service_cooldown_and_exclusion(self):
        for scenario in ["alive", "cooldown", "locked"]:
            with self.subTest(scenario=scenario):
                self.assertNotIn("start", self.fixture(scenario)[0])

    def test_worker_first_exit_is_immediate_then_bounded_backoff(self):
        self.assertEqual(["1", "2", "4", "8", "16", "30", "30", "30"], self.fixture("crash", boot=True)[1])

    def test_stable_worker_resets_delay(self):
        # First two workers exit without delay; subsequent short failures back off again.
        self.assertEqual(["1", "2", "4", "8", "16", "30", "30"], self.fixture("stable", boot=True)[1])


if __name__ == "__main__":
    unittest.main()
