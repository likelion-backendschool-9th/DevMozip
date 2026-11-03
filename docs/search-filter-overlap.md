# 개발자 탐색 필터 겹침 수정

상세 필터 패널의 position:sticky가 스크롤 도중 뒤에 있는 신뢰 온도 고급 검색 영역을 덮고 있었다. 개별 패널 고정을 제거해 두 영역이 문서 흐름을 따라 이동하도록 수정했다.

변경 파일: frontend/app/search/_components/SearchFilters.module.css

검증: npm test 37개 파일·422개 통과, npm run build 및 TypeScript 검사 성공. Chromium에서 수정 전 1440px 스크롤 400/800px의 겹침을 재현했다. 수정 후 1920/1440/1280/1024/768/390px × 스크롤 0/400/800px 총 18건에서 겹침과 문서 가로 넘침이 없었다. 프리미엄 신뢰 온도 선택·정렬 체크 동작도 확인했다. 실제 모바일 기기 및 전체 검색 시나리오는 재검증하지 않았다. 커밋·푸시는 하지 않았다.

[검증 결과](artifacts/search-filter-overlap/report.json) · [PC](artifacts/search-filter-overlap/1440.png) · [모바일](artifacts/search-filter-overlap/390.png)
