# 같은 네트워크에서 접속

현재 접속 주소: **http://192.168.219.101:5173**

- Vite는 `npm run dev`의 `--host 0.0.0.0` 설정으로 실행 중입니다.
- Windows Wi-Fi 주소의 TCP 5173만 WSL Ubuntu-26.04의 5173으로 전달했습니다.
- 방화벽 규칙 `DevMozip-Frontend-LAN-5173`은 같은 서브넷(LocalSubnet)에서만 허용합니다.
- 관리자 권한으로 스크립트를 적용한 후 Windows LAN 주소의 HTTP 200 응답을 확인했습니다. 다른 물리 컴퓨터에서의 접속은 직접 검증하지 않았습니다.
- 같은 Wi-Fi/LAN의 다른 컴퓨터 브라우저에서 위 주소로 접속하세요. 이 컴퓨터의 개발 서버가 실행 중이어야 합니다.

## 주소가 바뀌거나 WSL을 재시작한 경우

Windows PowerShell을 관리자 권한으로 열고 실행합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "\\wsl.localhost\Ubuntu-26.04\home\luvsuna\DevMozip\scripts\enable-lan.ps1"
```

현재 LAN/WSL IPv4를 다시 확인해 해당 5173 규칙을 갱신합니다. 결과는 `scripts/lan-setup.log`에 기록됩니다.

## 사용 범위

Mock 데이터와 로그인 상태는 각 컴퓨터의 브라우저 저장소에 별도로 유지됩니다. 다른 컴퓨터 사이에서 동기화되지 않습니다.
현재 회원가입은 브라우저 Web Crypto 사용으로 localhost 또는 HTTPS가 필요하므로 LAN HTTP에서는 데모 계정 전환으로 로그인할 수 있습니다. 이번 작업에서는 인증 기능 코드를 변경하지 않았습니다.

## 공유 해제 (Windows 관리자 PowerShell)

```powershell
netsh interface portproxy delete v4tov4 listenaddress=192.168.219.101 listenport=5173
Remove-NetFirewallRule -Name 'DevMozip-Frontend-LAN-5173'
```

설정된 다른 포트의 전달 규칙은 변경하지 않았습니다. 프론트엔드 소스/백엔드 변경이 없어 npm/Gradle 빌드는 이번에 재실행하지 않았습니다.
