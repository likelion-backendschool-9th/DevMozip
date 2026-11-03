# LAN 개발 서버 초기화 멈춤 수정

증상: localhost에서는 정상 표시되지만 WSL/LAN IP에서 ‘DevMozip 데모 데이터를 준비하고 있어요…’만 표시됐다. 브라우저에서는 개발용 HMR WebSocket 연결이 잘못된 HTTP 응답으로 실패했다.

변경 파일: frontend/next.config.ts. 설치된 Next.js의 allowedDevOrigins 설정에 현재 Windows LAN IP 192.168.219.101 및 WSL IP 172.28.186.83을 명시했다. 서버의 변경 감지 후 재시작으로 반영했다. 사용자 저장 데이터는 초기화하지 않았다.

검증: Chromium에서 localhost:5173, 172.28.186.83:5173, 192.168.219.101:5173 모두 준비 화면 이후 메인 콘텐츠 표시 확인. 페이지 오류/콘솔 오류 및 HTTP 400 이상 응답 없음. npm test 34개 파일·404개 테스트 통과, npm run build(TypeScript 포함) 성공.

다른 LAN 기기에서의 실제 클릭은 직접 검증하지 않았고, 개발 환경에서 Windows LAN 주소 경유로 브라우저 검증했다. Windows 포트 전달/방화벽은 별도 설정이며 이번 변경에서 수정하지 않았다. IP가 변경되면 allowedDevOrigins 및 포트 전달 주소도 함께 갱신해야 한다.

접속: 개발 컴퓨터는 http://localhost:5173, 같은 Wi-Fi의 다른 기기는 http://192.168.219.101:5173. 이전에 멈췄던 탭은 Ctrl+Shift+R로 새로고침한다.
