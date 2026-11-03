"""Docker를 실행하지 않고 배포 스크립트의 전환/실패/롤백 순서를 검증한다."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class BlueGreenTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="devmozip-deploy-test-")
        self.root = Path(self.directory.name)
        (self.root / "deploy").mkdir()
        (self.root / "bin").mkdir()
        (self.root / "nginx/upstream").mkdir(parents=True)
        shutil.copy(ROOT / "deploy/blue-green.sh", self.root / "deploy/blue-green.sh")
        docker = self.root / "bin/docker"
        docker.write_text('''#!/usr/bin/env python3
import os, sys
args = sys.argv[1:]
with open(os.environ['DOCKER_LOG'], 'a') as log:
    log.write(' '.join(args) + '\\n')
if 'wget' in args and os.environ.get('HEALTH_FAIL') == '1': sys.exit(1)
if args[-2:] == ['nginx', '-t'] and os.environ.get('RELOAD_FAIL') == '1': sys.exit(1)
''')
        docker.chmod(0o755)
        self.env = dict(os.environ, PATH=f"{self.root / 'bin'}:{os.environ['PATH']}",
                        DOCKER_LOG=str(self.root / "docker.log"), HEALTH_RETRIES="1")

    def tearDown(self):
        self.directory.cleanup()

    def run_script(self, command="deploy", **env):
        return subprocess.run(["bash", "deploy/blue-green.sh", command], cwd=self.root,
                              env=dict(self.env, **env), capture_output=True, text=True)

    def active(self):
        return (self.root / "nginx/upstream/active.conf").read_text()

    def test_first_deploy_switch_and_rollback(self):
        self.assertEqual(self.run_script().returncode, 0)
        self.assertIn("frontend-blue:3000", self.active())
        self.assertEqual(self.run_script().returncode, 0)
        self.assertIn("frontend-green:3000", self.active())
        self.assertEqual(self.run_script("rollback").returncode, 0)
        self.assertIn("frontend-blue:3000", self.active())
        self.assertEqual(self.run_script("status").returncode, 0)
        log = (self.root / "docker.log").read_text()
        self.assertIn("start backend-blue frontend-blue", log)
        self.assertIn("127.0.0.1:3000/api/health", log)
        self.assertNotIn("prune", log)

    def test_health_failure_keeps_current_color(self):
        self.assertEqual(self.run_script().returncode, 0)
        before = self.active()
        (self.root / "docker.log").write_text("")
        self.assertNotEqual(self.run_script(HEALTH_FAIL="1").returncode, 0)
        self.assertEqual(self.active(), before)
        log = (self.root / "docker.log").read_text()
        self.assertIn("stop frontend-green backend-green", log)
        self.assertNotIn("stop frontend-blue", log)
        self.assertNotIn("nginx -s reload", log)

    def test_nginx_failure_restores_state(self):
        self.assertEqual(self.run_script().returncode, 0)
        before = self.active()
        self.assertNotEqual(self.run_script(RELOAD_FAIL="1").returncode, 0)
        self.assertEqual(self.active(), before)

    def test_initial_failure_does_not_claim_active_color(self):
        self.assertNotEqual(self.run_script(HEALTH_FAIL="1").returncode, 0)
        self.assertFalse((self.root / "nginx/upstream/active.conf").exists())


if __name__ == "__main__":
    unittest.main()
