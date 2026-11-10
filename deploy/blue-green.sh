#!/usr/bin/env bash
# 블루그린 배포 스크립트. 서버의 프로젝트 폴더(compose.prod.yaml 이 있는 곳)에서 실행한다. 어디서 실행해도 그 폴더로 이동한다.
#
#   bash deploy/blue-green.sh deploy      # 쉬는 색에 새 이미지 → 헬스체크 → nginx 전환 → 이전 색 정지
#   bash deploy/blue-green.sh rollback    # 정지해 둔 이전 색을 다시 켜서 되돌린다 (헬스체크 뒤 전환)
#   bash deploy/blue-green.sh status      # 활성 색과 컨테이너 상태
#   IMAGE_TAG=sha-abc1234 bash deploy/blue-green.sh deploy    # 특정 태그를 배포 (롤백의 다른 길)
#   SKIP_PULL=1 bash deploy/blue-green.sh deploy               # 로컬에서 시험할 때: GHCR 에서 받지 않고 로컬 이미지 사용
#
# 상태는 nginx/upstream/active.conf 한 파일이다. 팀 nginx 가 이 파일을 include 하고,
# 여기 적힌 색(frontend-blue 또는 frontend-green)이 트래픽을 받는다. git 에는 들어가지 않는다.
# 메신저 WebSocket(/ws/messages)은 같은 파일의 backend_upstream 으로 활성 색의 backend 에 바로 간다.
#
# 종료 코드 3: 배포는 끝났지만 WebSocket 경로 확인이 실패했다. 화면은 폴링으로 동작한다.
set -euo pipefail
cd "$(dirname "$0")/.."

COMPOSE="docker compose -f compose.prod.yaml"
ACTIVE_FILE=nginx/upstream/active.conf
mkdir -p nginx/upstream
# 동시 배포/롤백이 같은 upstream 파일을 덮어쓰지 않도록 한다.
exec 9>nginx/upstream/.deploy.lock
flock -n 9 || { echo "다른 배포 작업이 진행 중입니다"; exit 1; }
HEALTH_RETRIES=${HEALTH_RETRIES:-30}        # 시도 사이 2초 대기. 각 wget 타임아웃은 5초

log() { printf '[blue-green] %s\n' "$*"; }

# active.conf 에서 지금 트래픽을 받는 색을 읽는다. 파일이 없으면(첫 배포) 빈 문자열
active_color() {
  [ -f "$ACTIVE_FILE" ] && sed -n 's/.*frontend-\(blue\|green\):3000.*/\1/p' "$ACTIVE_FILE" || true
}
other() { [ "$1" = blue ] && echo green || echo blue; }

# upstream 파일을 쓴다. resolve 를 붙여 컨테이너가 재시작해 IP 가 바뀌어도 nginx 가 5초 안에 따라가게 한다.
# server.conf 가 backend_upstream 을 쓰므로 두 upstream 을 항상 함께 쓴다
write_active() {
  cat > "$ACTIVE_FILE" <<CONF
# deploy/blue-green.sh 가 쓴다. 손으로 고치지 않는다
upstream frontend_upstream {
    zone frontend_upstream 64k;
    resolver 127.0.0.11 valid=5s;
    server frontend-$1:3000 resolve;
}
# 메신저 WebSocket(/ws/messages). 브라우저가 BFF 를 거치지 않고 활성 색의 backend 에 연결한다
upstream backend_upstream {
    zone backend_upstream 64k;
    resolver 127.0.0.11 valid=5s;
    server backend-$1:8080 resolve;
}
CONF
}

# nginx/server.conf 는 파일 하나만 마운트돼 있어 git pull 로 바뀐 내용을 reload 로는 읽지 못한다.
# 실행 중인 nginx 가 읽은 내용과 다르면 다시 시작한다. 다시 시작하는 동안 전체 트래픽이 잠깐 끊긴다
restart_nginx_if_config_changed() {
  local expected running i
  expected=$(sha256sum nginx/server.conf | cut -d' ' -f1)
  running=$($COMPOSE exec -T nginx sha256sum /etc/nginx/conf.d/default.conf 2>/dev/null | cut -d' ' -f1 || true)
  [ "$expected" = "$running" ] && return 0
  log "nginx/server.conf 가 바뀌어 nginx 를 다시 시작한다"
  $COMPOSE restart nginx
  for ((i = 1; i <= 15; i++)); do
    if $COMPOSE exec -T nginx nginx -t > /dev/null 2>&1; then return 0; fi
    sleep 1
  done
  log "nginx 가 새 설정으로 시작하지 못했다. 원인은:  $COMPOSE logs nginx"
  return 1
}

# 업로드 이미지 폴더(public/images)를 만들고 backend 가 쓸 수 있게 한다 (docs/feed-plan.md 3절 "운영 서버").
# 폴더가 없으면 Docker 가 root 소유로 만들고, backend 이미지는 root 가 아닌 app 사용자로 실행되어 쓸 수 없다.
# 새 색의 backend 이미지를 root 로 한 번 실행해, 이미지 안의 app 사용자로 소유자를 바꾼다(UID 를 따로 알 필요가 없다)
prepare_image_storage() {
  mkdir -p public/images
  $COMPOSE run --rm --no-deps -T --user root --entrypoint chown "backend-$1" app:app /public/images
}

env_value() {
  [ -f .env ] && sed -n "s/^$1=//p" .env | tail -n 1 | tr -d "\"'" || true
}

# 메신저 WebSocket 경로를 팀 nginx 로 확인한다. 쿠키 없이 요청하므로 경로와 Origin 이 맞으면 백엔드가 401 로 거절한다.
# 실시간이 끊겨도 화면은 폴링으로 동작해 장애가 드러나지 않으므로 배포 때 확인한다
check_websocket() {
  local origin port code
  origin=${DOMAINNAME:-$(env_value DOMAINNAME)}
  port=${TEAM_PORT:-$(env_value TEAM_PORT)}
  code=$(curl -s -o /dev/null -m 10 -w '%{http_code}' \
    -H 'Connection: Upgrade' -H 'Upgrade: websocket' -H 'Sec-WebSocket-Version: 13' \
    -H 'Sec-WebSocket-Key: ZGV2bW96aXAtY2hlY2s9PQ==' -H "Origin: $origin" \
    "http://127.0.0.1:$port/ws/messages" || true)
  case "$code" in
    401) log "WebSocket 경로 확인: 401 (정상. 쿠키 없는 요청을 backend 가 거절했다)"; return 0 ;;
    403) log "WebSocket 경로 확인 실패: 403. .env 의 DOMAINNAME($origin)이 사이트 주소와 같은지 확인한다" ;;
    404) log "WebSocket 경로 확인 실패: 404. nginx/server.conf 의 /ws/messages 경로가 적용되지 않았다" ;;
    *) log "WebSocket 경로 확인 실패: ${code:-응답 없음}. 활성 색의 backend 와 nginx 를 확인한다" ;;
  esac
  return 1
}

# 새 색의 frontend 가 BFF 로 backend 까지 응답하는지 본다. 컨테이너 안에서 자기 자신을 부른다.
# localhost 가 아니라 127.0.0.1 인 이유: alpine 에서 localhost 는 ::1 로 풀리고 Next.js 는 IPv4 만 듣는다
health() {
  local color=$1 i
  for ((i = 1; i <= HEALTH_RETRIES; i++)); do
    if $COMPOSE exec -T "frontend-$color" wget -T 5 -qO /dev/null http://127.0.0.1:3000/api/health 2>/dev/null; then
      log "헬스체크 통과: frontend-$color → backend-$color (${i}번째 시도)"
      return 0
    fi
    sleep 2
  done
  log "헬스체크 실패: frontend-$color 가 ${HEALTH_RETRIES}회 확인 동안 정상 응답하지 않았다"
  return 1
}

# upstream 파일을 바꾸고 nginx 를 reload 한다. 열려 있던 연결은 끝까지 처리되고 새 연결부터 새 색으로 간다
switch_to() {
  local previous
  previous=$(active_color)
  write_active "$1"
  if ! $COMPOSE exec -T nginx nginx -t || ! $COMPOSE exec -T nginx nginx -s reload; then
    [ -z "$previous" ] || write_active "$previous"
    log "nginx 검증/전환 실패. 이전 upstream 설정을 복원했다"
    return 1
  fi
  log "nginx 전환 → frontend-$1"
}

cmd_status() {
  local cur; cur=$(active_color)
  log "활성 색: ${cur:-없음}   (IMAGE_TAG=${IMAGE_TAG:-latest})"
  $COMPOSE --profile blue --profile green ps -a --format 'table {{.Service}}\t{{.Image}}\t{{.Status}}'
}

cmd_deploy() {
  local cur new
  cur=$(active_color)
  if [ -z "$cur" ]; then
    new=blue
    log "첫 배포: blue 로 시작한다"
  else
    new=$(other "$cur")
    log "활성 색은 $cur. 새 버전(${IMAGE_TAG:-latest})을 $new 에 띄운다"
    # git pull 로 server.conf 가 먼저 바뀌어 있을 수 있다. 아래에서 실패해 끝나도 디스크의 두 설정이 맞도록 먼저 쓴다.
    # 실행 중인 nginx 에는 영향이 없다(이전 server.conf 와 새 active.conf 의 조합도 유효하다)
    write_active "$cur"
  fi

  if [ "${SKIP_PULL:-0}" = 1 ]; then
    log "SKIP_PULL=1: 이미지를 받지 않고 로컬에 있는 것을 쓴다 (로컬 시험용)"
  else
    $COMPOSE pull --quiet "backend-$new" "frontend-$new"
  fi
  $COMPOSE up -d --no-build postgres redis            # 공유 서비스. 이미 떠 있으면 그대로
  prepare_image_storage "$new"                         # 두 색이 같이 쓰는 업로드 이미지 폴더
  $COMPOSE up -d --no-build "backend-$new" "frontend-$new"  # 쉬는 색을 새 이미지로 교체. 활성 색은 건드리지 않는다

  if ! health "$new"; then
    log "전환하지 않는다. ${cur:+$cur 가 그대로 트래픽을 받는다. }원인은:  $COMPOSE logs backend-$new frontend-$new"
    $COMPOSE stop "frontend-$new" "backend-$new"
    exit 1
  fi

  if [ -z "$cur" ]; then write_active "$new"; fi
  $COMPOSE up -d --no-build nginx
  restart_nginx_if_config_changed
  switch_to "$new"
  if [ -n "$cur" ]; then
    $COMPOSE stop "frontend-$cur" "backend-$cur"            # 지우지 않고 멈춘다. rollback 이 다시 켠다
    log "$cur 정지. 되돌리려면:  bash deploy/blue-green.sh rollback"
  fi
  cmd_status
  if ! check_websocket; then
    log "배포는 끝났지만 메신저 실시간 연결이 동작하지 않는다. 화면은 폴링으로 동작한다"
    exit 3
  fi
}

cmd_rollback() {
  local cur prev
  cur=$(active_color)
  [ -n "$cur" ] || { log "활성 색이 없다. 먼저 deploy 한다"; exit 1; }
  prev=$(other "$cur")
  log "활성 색은 $cur. 멈춰 둔 $prev 를 다시 켠다"
  write_active "$cur"                                        # deploy 와 같은 이유로 디스크의 두 설정을 먼저 맞춘다
  $COMPOSE start "backend-$prev" "frontend-$prev"           # up 이 아니라 start: 이미지를 바꾸지 않고 그대로 켠다
  if ! health "$prev"; then
    log "$prev 도 응답하지 않는다. 전환하지 않는다"
    exit 1
  fi
  restart_nginx_if_config_changed
  switch_to "$prev"
  $COMPOSE stop "frontend-$cur" "backend-$cur"
  cmd_status
}

case "${1:-}" in
  deploy)   cmd_deploy ;;
  rollback) cmd_rollback ;;
  status)   cmd_status ;;
  *) echo "사용법: bash deploy/blue-green.sh deploy | rollback | status"; exit 2 ;;
esac
