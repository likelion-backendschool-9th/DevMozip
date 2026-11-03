active.conf는 deploy/blue-green.sh가 쓰는 상태 파일입니다. 팀 nginx가 include하여 현재 활성 frontend 색을 결정합니다.

호스트마다 상태가 다르므로 커밋하지 않습니다. 첫 배포 때 스크립트가 생성합니다.
