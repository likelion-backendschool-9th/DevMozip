"""Docker를 실행하지 않고 배포 스크립트의 전환/실패/롤백 순서를 검증한다."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = "https://devmozip.example.test"


class BlueGreenTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="devmozip-deploy-test-")
        self.root = Path(self.directory.name)
        (self.root / "deploy").mkdir()
        (self.root / "bin").mkdir()
        (self.root / "nginx/upstream").mkdir(parents=True)
        shutil.copy(ROOT / "deploy/blue-green.sh", self.root / "deploy/blue-green.sh")
        shutil.copy(ROOT / "nginx/server.conf", self.root / "nginx/server.conf")
        (self.root / ".env").write_text(f"TEAM_PORT=3002\nDOMAINNAME='{DOMAIN}'\n")
        # 실행 중인 nginx 의 설정 해시. 기본값은 저장소의 server.conf 와 같아 재시작하지 않는다
        conf_hash = hashlib.sha256((self.root / "nginx/server.conf").read_bytes()).hexdigest()
        docker = self.root / "bin/docker"
        docker.write_text('''#!/usr/bin/env python3
import os, sys
args = sys.argv[1:]
with open(os.environ['DOCKER_LOG'], 'a') as log:
    log.write(' '.join(args) + '\\n')
if 'wget' in args and os.environ.get('HEALTH_FAIL') == '1': sys.exit(1)
if args[-2:] == ['nginx', '-t'] and os.environ.get('RELOAD_FAIL') == '1': sys.exit(1)
if 'sha256sum' in args: print(os.environ['CONTAINER_CONF_HASH'] + '  /etc/nginx/conf.d/default.conf')
''')
        docker.chmod(0o755)
        curl = self.root / "bin/curl"
        curl.write_text('''#!/usr/bin/env python3
import os, sys
with open(os.environ['CURL_LOG'], 'a') as log:
    log.write(' '.join(sys.argv[1:]) + '\\n')
print(os.environ.get('WS_STATUS', '401'), end='')
''')
        curl.chmod(0o755)
        self.env = dict(os.environ, PATH=f"{self.root / 'bin'}:{os.environ['PATH']}",
                        DOCKER_LOG=str(self.root / "docker.log"), CURL_LOG=str(self.root / "curl.log"),
                        CONTAINER_CONF_HASH=conf_hash, HEALTH_RETRIES="1")
        self.env.pop("DOMAINNAME", None)
        self.env.pop("TEAM_PORT", None)

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

    def log_lines(self):
        return (self.root / "docker.log").read_text().splitlines()

    def test_websocket_goes_to_the_active_backend(self):
        self.assertEqual(self.run_script().returncode, 0)
        self.assertIn("upstream backend_upstream", self.active())
        self.assertIn("server backend-blue:8080 resolve;", self.active())
        self.assertEqual(self.run_script().returncode, 0)
        self.assertIn("server backend-green:8080 resolve;", self.active())
        self.assertEqual(self.run_script("rollback").returncode, 0)
        self.assertIn("server backend-blue:8080 resolve;", self.active())
        self.assertIn("location = /ws/messages", (ROOT / "nginx/server.conf").read_text())

    def test_old_active_conf_is_upgraded_before_a_failing_deploy(self):
        # 이전 스크립트가 쓴 active.conf(백엔드 업스트림 없음)에서 git pull 뒤 헬스체크가 실패한 경우.
        # 새 server.conf 와 짝이 맞아야 다음 nginx 재시작 때 사이트가 멈추지 않는다.
        (self.root / "nginx/upstream/active.conf").write_text(
            "upstream frontend_upstream {\n    server frontend-blue:3000 resolve;\n}\n")
        self.assertNotEqual(self.run_script(HEALTH_FAIL="1").returncode, 0)
        self.assertIn("frontend-blue:3000", self.active())
        self.assertIn("server backend-blue:8080 resolve;", self.active())

    def test_rollback_upgrades_old_active_conf_first(self):
        self.assertEqual(self.run_script().returncode, 0)
        self.assertEqual(self.run_script().returncode, 0)
        (self.root / "nginx/upstream/active.conf").write_text(
            "upstream frontend_upstream {\n    server frontend-green:3000 resolve;\n}\n")
        self.assertNotEqual(self.run_script("rollback", HEALTH_FAIL="1").returncode, 0)
        self.assertIn("server backend-green:8080 resolve;", self.active())

    def test_changed_server_conf_restarts_nginx_before_switching(self):
        self.assertEqual(self.run_script().returncode, 0)
        (self.root / "docker.log").write_text("")
        self.assertEqual(self.run_script(CONTAINER_CONF_HASH="old").returncode, 0)
        log = self.log_lines()
        restart = log.index("compose -f compose.prod.yaml restart nginx")
        reload = log.index("compose -f compose.prod.yaml exec -T nginx nginx -s reload")
        self.assertLess(restart, reload)
        self.assertIn("frontend-green:3000", self.active())

    def test_unchanged_server_conf_does_not_restart_nginx(self):
        self.assertEqual(self.run_script().returncode, 0)
        self.assertEqual(self.run_script().returncode, 0)
        self.assertEqual(self.run_script("rollback").returncode, 0)
        self.assertNotIn("compose -f compose.prod.yaml restart nginx", self.log_lines())

    def test_websocket_check_uses_the_site_origin_through_the_team_nginx(self):
        self.assertEqual(self.run_script().returncode, 0)
        request = (self.root / "curl.log").read_text()
        self.assertIn(f"Origin: {DOMAIN}", request)
        self.assertIn("Upgrade: websocket", request)
        self.assertIn("http://127.0.0.1:3002/ws/messages", request)

    def test_failed_websocket_check_keeps_the_deploy_and_exits_3(self):
        self.assertEqual(self.run_script().returncode, 0)
        for status, hint in [("404", "/ws/messages 경로가 적용되지 않았다"), ("403", "DOMAINNAME"), ("000", "000")]:
            result = self.run_script(WS_STATUS=status)
            self.assertEqual(result.returncode, 3, result.stdout)
            self.assertIn(hint, result.stdout)
        # 실패해도 배포는 되돌리지 않는다. 세 번 배포해 green 이 활성이다.
        self.assertIn("frontend-green:3000", self.active())

    def test_rollback_does_not_check_websocket(self):
        self.assertEqual(self.run_script().returncode, 0)
        self.assertEqual(self.run_script().returncode, 0)
        self.assertEqual(self.run_script("rollback", WS_STATUS="404").returncode, 0)


if __name__ == "__main__":
    unittest.main()
