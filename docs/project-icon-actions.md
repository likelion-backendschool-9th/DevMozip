# 프로젝트 좋아요·공유·북마크

- 프로젝트 상세에서 좋아요·공유·북마크를 한 아이콘 그룹으로 표시한다. 아이콘은 21px, 상세 버튼은 40px, 카드 북마크 버튼은 36px로 확대했다. 접근 가능한 이름·선택 상태·키보드 포커스를 제공한다.
- 공유는 공통 URL 복사 모달을 사용한다. 클립보드 접근이 불가능한 HTTP 환경에서는 주소 입력창을 선택해 직접 복사할 수 있다.
- Mock 켜기는 기존 모집 상태에 계정별 likes를 추가해 localStorage에 보존한다. 이전 저장값은 그대로 읽는다. Mock 끄기는 북마크와 마찬가지로 인증된 회원의 실제 API·DB를 사용한다. DB 실패를 로컬 저장 성공으로 대체하지 않는다.
- 사용자 승인으로 2026-11-09 팀 DB에 [likes DDL](sql/project-likes.sql)을 실행했다. bookmarks와 같은 6개 컬럼과 FK·중복·대상 검증 제약을 확인했다. 기존 회원·프로젝트 데이터는 변경하지 않았다.

| 메서드 | 백엔드 경로 | 용도 |
| --- | --- | --- |
| POST | /api/v1/projects/{id}/like | 좋아요 저장(중복 안전) |
| DELETE | /api/v1/projects/{id}/like | 본인 좋아요 취소(반복 안전) |
| GET | /api/v1/projects/likes | 본인이 좋아요한 열람 가능한 프로젝트 |

프론트 BFF는 `/api/projects` 아래 같은 경로를 사용한다. 비공개/삭제 공고 및 인증·사용자별 분리는 북마크와 동일한 정책이다. 게시글 실제 좋아요와 post_id FK는 구현하지 않았다.

변경 파일: `Like`, `LikeRepository`, `ProjectLikeResponse`, `ProjectLikeService`, `ProjectLikeServiceImpl`, `ProjectLikeController`, `ProjectLikeIntegrationTest`, 공통 `ProjectIconActions`·`ProjectLikeButton`·`ProjectShareButton`·`ProjectBookmarkButton`, `ProjectActions`, `ApiProjectDetail`, `projectLikeStore`, Mock `projectService`·타입과 관련 테스트. 실행 소스와 `feat/56-project-bookmarks` 작업 폴더에 함께 반영했다. 검증 결과는 `ai-progress.md`를 참조한다.

## 배치 보완 — 2026-11-09

카드 북마크를 대표 이미지 오른쪽 위로 이동했다. 제목 20px/750, 소개 14px로 구분했다. 상세 세 아이콘은 제목 헤더 오른쪽 끝에 정렬한다. 기능·저장 방식은 그대로 유지한다. 빌드·TypeScript 및 관련 테스트 40개 통과. 1440px 빌드 프리뷰에서 Mock/API 목록·상세 배치를 측정하고 확인했다. 개발 서버 브라우저는 초기화 대기여서 별도로 기록했다.
