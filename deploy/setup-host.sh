#!/usr/bin/env bash
# 조별 서버에서 sudo bash deploy/setup-host.sh 실행. DevMozip 가상 호스트만 추가한다.
set -euo pipefail
DOMAIN=devmozip.newlecture.com
PORT=3002
SITE=/etc/nginx/sites-available/devmozip
LINK=/etc/nginx/sites-enabled/devmozip

[ "$(id -u)" = 0 ] || { echo 'sudo로 실행해 주세요.'; exit 1; }
command -v nginx >/dev/null
command -v certbot >/dev/null
curl --fail --silent --max-time 10 "http://127.0.0.1:$PORT/api/health" >/dev/null

# 기존 설정에 같은 도메인이 있거나 이름이 충돌하면 덮어쓰지 않는다.
python3 - "$SITE" "$LINK" "$DOMAIN" <<'PY'
from pathlib import Path
import sys
site, link, domain = map(Path, sys.argv[1:])
if site.exists() and '# DevMozip host proxy' not in site.read_text():
    sys.exit('기존 devmozip 설정이 있어 자동으로 덮어쓰지 않습니다.')
if link.is_symlink() and link.resolve() != site.resolve():
    sys.exit('devmozip 링크가 다른 설정을 가리킵니다.')
if link.exists() and not link.is_symlink():
    sys.exit('devmozip 활성 설정이 일반 파일입니다. 기존 파일을 보존합니다.')
for directory in [Path('/etc/nginx/sites-enabled'), Path('/etc/nginx/conf.d')]:
    for path in directory.glob('*'):
        if path.resolve() == site.resolve() or not path.is_file():
            continue
        if str(domain) in path.read_text():
            sys.exit(f'이미 도메인을 사용하는 설정이 있습니다: {path}')
PY

created_site=0
created_link=0
if [ ! -e "$SITE" ]; then
    cat > "$SITE" <<'NGINX'
# DevMozip host proxy
server {
    listen 80;
    server_name devmozip.newlecture.com;
    location / {
        proxy_pass http://127.0.0.1:3002;
        proxy_http_version 1.1;
        proxy_set_header Host $http_host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $http_host;
    }
}
NGINX
    created_site=1
fi
if [ ! -L "$LINK" ]; then
    ln -s "$SITE" "$LINK"
    created_link=1
fi
if ! nginx -t; then
    # 이번 실행이 만든 DevMozip 파일만 되돌린다. 다른 가상 호스트는 건드리지 않는다.
    [ "$created_link" = 0 ] || unlink "$LINK"
    [ "$created_site" = 0 ] || unlink "$SITE"
    exit 1
fi
systemctl reload nginx
# 기존 Certbot 계정을 사용한다. 계정/이메일이 없으면 오류를 보고하고 HTTP 설정은 유지한다.
certbot --nginx --non-interactive --agree-tos --keep-until-expiring --redirect -d "$DOMAIN"
nginx -t
systemctl reload nginx
curl --fail --silent --max-time 15 "https://$DOMAIN/api/health"
printf '\nDevMozip HTTPS 준비 완료: https://%s\n' "$DOMAIN"
