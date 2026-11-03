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

# upstream 파일을 쓴다. resolve 를 붙여 컨테이너가 재시작해 IP 가 바뀌어도 nginx 가 5초 안에 따라가게 한다
write_active() {
  cat > "$ACTIVE_FILE" <<CONF
# deploy/blue-green.sh 가 쓴다. 손으로 고치지 않는다
upstream frontend_upstream {
    zone frontend_upstream 64k;
    resolver 127.0.0.11 valid=5s;
    server frontend-$1:3000 resolve;
}
CONF
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
  fi

  if [ "${SKIP_PULL:-0}" = 1 ]; then
    log "SKIP_PULL=1: 이미지를 받지 않고 로컬에 있는 것을 쓴다 (로컬 시험용)"
  else
    $COMPOSE pull --quiet "backend-$new" "frontend-$new"
  fi
  $COMPOSE up -d --no-build postgres redis            # 공유 서비스. 이미 떠 있으면 그대로
  $COMPOSE up -d --no-build "backend-$new" "frontend-$new"  # 쉬는 색을 새 이미지로 교체. 활성 색은 건드리지 않는다

  if ! health "$new"; then
    log "전환하지 않는다. ${cur:+$cur 가 그대로 트래픽을 받는다. }원인은:  $COMPOSE logs backend-$new frontend-$new"
    $COMPOSE stop "frontend-$new" "backend-$new"
    exit 1
  fi

  if [ -z "$cur" ]; then write_active "$new"; fi
  $COMPOSE up -d --no-build nginx
  switch_to "$new"
  if [ -n "$cur" ]; then
    $COMPOSE stop "frontend-$cur" "backend-$cur"            # 지우지 않고 멈춘다. rollback 이 다시 켠다
    log "$cur 정지. 되돌리려면:  bash deploy/blue-green.sh rollback"
  fi
  cmd_status
}

cmd_rollback() {
  local cur prev
  cur=$(active_color)
  [ -n "$cur" ] || { log "활성 색이 없다. 먼저 deploy 한다"; exit 1; }
  prev=$(other "$cur")
  log "활성 색은 $cur. 멈춰 둔 $prev 를 다시 켠다"
  $COMPOSE start "backend-$prev" "frontend-$prev"           # up 이 아니라 start: 이미지를 바꾸지 않고 그대로 켠다
  if ! health "$prev"; then
    log "$prev 도 응답하지 않는다. 전환하지 않는다"
    exit 1
  fi
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
