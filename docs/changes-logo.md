# 로고 적용

- 사용자 제공 SVG를 `frontend/src/assets/devmozip-logo.svg`에 저장했습니다.
- `AppLayout.tsx`: 헤더와 푸터의 기존 임시 로고를 SVG 이미지로 교체했습니다.
- `global.css`: 원본 3:2 비율을 유지하여 헤더 96×64px, 푸터 84×56px로 표시합니다. 원본 색상을 유지합니다.
- 첫 빌드 명령은 루트에서 실행해 package.json 부재로 실패했습니다. frontend 디렉터리에서 다시 실행했습니다.
- SVG XML 파싱 정상. 실제 브라우저 시각 검증은 수행하지 않았습니다.
