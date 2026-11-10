#!/usr/bin/env python3
"""Run the WSL backend or inspect its PostgreSQL using resolved Compose settings."""
import argparse
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]


def fail(message):
    raise SystemExit(message)


def compose_config():
    if not shutil.which('docker'):
        fail('docker CLI가 필요합니다. Docker Desktop의 WSL Integration을 확인하세요.')
    if not (ROOT / '.env').is_file():
        fail('프로젝트 루트의 .env 파일이 없습니다.')
    result = subprocess.run(['docker', 'compose', '--env-file', str(ROOT / '.env'),
                             'config', '--format', 'json'], cwd=ROOT, capture_output=True, text=True)
    if result.returncode:
        fail('Compose 설정을 읽지 못했습니다. 루트에서 docker compose config --quiet로 확인하세요.')
    # Never print resolved settings: this object contains passwords and signing keys.
    config = json.loads(result.stdout)
    settings = config['services']['backend'].get('environment', {})
    for key in ('DB_HOST', 'DB_PORT', 'DB_NAME', 'DB_USER', 'DB_PASSWORD'):
        if not settings.get(key):
            fail(f'{key} 설정이 비어 있습니다. 루트 .env와 Compose 설정을 확인하세요.')
    if settings['DB_HOST'] == 'postgres':
        fail('현재 DB_HOST가 컨테이너 이름 postgres입니다. 팀 개발 DB를 쓰려면 루트 .env의 DB_HOST를 설정하세요.')
    return config, settings


def redis_port(config):
    for binding in config['services']['redis'].get('ports', []):
        if int(binding.get('target', 0)) == 6379 and binding.get('published'):
            return str(binding['published'])
    fail('직접 bootRun에는 Redis 호스트 포트가 필요합니다. docs/postgresql-development.md를 확인하세요.')


def redis_ready(port):
    try:
        with socket.create_connection(('127.0.0.1', int(port)), timeout=1) as connection:
            connection.sendall(b'*1\r\n$4\r\nPING\r\n')
            return connection.recv(64).startswith(b'+PONG')
    except OSError:
        return False


def backend(config, settings):
    if not settings.get('JWT_SECRET'):
        fail('루트 .env의 JWT_SECRET이 필요합니다.')
    port = redis_port(config)
    if not redis_ready(port):
        print('로그인용 Redis를 시작합니다: docker compose up -d redis', flush=True)
        result = subprocess.run(['docker', 'compose', '--env-file', str(ROOT / '.env'),
                                 'up', '-d', 'redis'], cwd=ROOT)
        if result.returncode:
            fail('Redis를 시작하지 못했습니다. Windows Docker Desktop 실행과 WSL Integration을 확인하세요.')
        deadline = time.monotonic() + 30
        while not redis_ready(port):
            if time.monotonic() >= deadline:
                fail('Redis 응답을 기다리다 종료했습니다. docker compose logs redis로 확인하세요.')
            time.sleep(1)
    env = os.environ.copy()
    env.update({key: str(value) for key, value in settings.items() if value is not None})
    # A host JVM reaches the published Redis port, rather than Docker DNS name "redis".
    env.update(SPRING_PROFILES_ACTIVE='default', SPRING_JPA_HIBERNATE_DDL_AUTO='validate',
               SPRING_SQL_INIT_MODE='never', REDIS_HOST='127.0.0.1', REDIS_PORT=port,
               DEBUG='false', SPRING_DEVTOOLS_ADD_PROPERTIES='false', LOGGING_LEVEL_WEB='INFO',
               LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB='INFO')
    published = next((str(binding['published']) for binding in config['services']['backend'].get('ports', [])
                      if int(binding.get('target', 0)) == 8080 and binding.get('published')), '8080')
    env.setdefault('SERVER_PORT', published)
    print('루트 .env → Compose 설정으로 PostgreSQL에 연결합니다. 스키마 validate / SQL 초기화 never.', flush=True)
    print('백엔드 종료: Ctrl+C. 환경변수 값·비밀번호·토큰은 출력하지 않습니다.', flush=True)
    os.chdir(ROOT / 'backend')
    os.execvpe('sh', ['sh', './gradlew', 'bootRun', '--no-daemon'], env)


def database(settings, command=None, check=False):
    if not shutil.which('psql'):
        fail('psql이 필요합니다. Ubuntu/WSL에서 sudo apt install postgresql-client로 설치하세요.')
    env = os.environ.copy()
    env.update(PGPASSWORD=str(settings['DB_PASSWORD']), PGCONNECT_TIMEOUT='10',
               PGOPTIONS='-c default_transaction_read_only=on')
    args = ['psql', '-X', '--set=ON_ERROR_STOP=1', '-h', str(settings['DB_HOST']),
            '-p', str(settings['DB_PORT']), '-U', str(settings['DB_USER']), '-d', str(settings['DB_NAME'])]
    if check:
        command = """SELECT current_database(),current_schema(),current_setting('server_version');
SELECT 'ai_recommendation_requests' AS table_name,count(*) FROM ai_recommendation_requests
UNION ALL SELECT 'ai_recommendations',count(*) FROM ai_recommendations;
SELECT r.id,r.ai_conversation_id,r.ai_message_id,r.status,count(d.id) AS results
FROM ai_recommendation_requests r LEFT JOIN ai_recommendations d ON d.recommended_request_id=r.id
GROUP BY r.id ORDER BY r.id DESC LIMIT 10;"""
    if command:
        args += ['-P', 'pager=off', '-c', command]
    else:
        print('읽기 전용 PostgreSQL 터미널입니다. 종료는 \\q입니다.', flush=True)
    return subprocess.call(args, env=env)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=['backend', 'db', 'check'])
    parser.add_argument('--command', help='db 모드에서 실행할 읽기 전용 SQL')
    args = parser.parse_args()
    if args.command and args.mode != 'db':
        parser.error('--command는 db 모드에서만 사용합니다.')
    config, settings = compose_config()
    if args.mode == 'backend':
        backend(config, settings)
        return 0
    return database(settings, args.command, check=args.mode == 'check')


if __name__ == '__main__':
    sys.exit(main())
