# 프로필 영역별 수정 및 중복 정보 정리

- 상단 프로필 수정: 직군, 경력, 연락 가능 시간, 협업 상태만 편집한다.
- 소개 탭: 상세 소개, 기술 스택, 관심 분야, 외부 링크를 각 위치의 수정 버튼에서 편집한다. HTML 소개 편집도 유지한다.
- 한 줄 소개는 검색·추천 카드에서 사용하는 선택 정보로 소개 편집창에 명확히 표시한다. 상단 프로필에서는 소개 문구와 외부 링크를 반복 표시하지 않고, 링크는 소개 탭에서만 표시한다. 저장된 기존 내용은 삭제하지 않는다.
- 포트폴리오 및 내 글: 기존 항목별 수정·삭제를 유지한다.
- 활동: 프로젝트 관리 화면에서 연결된 정보를 수정한다. 받은 리뷰는 조회 전용을 유지한다.
- 본인만 수정 버튼과 편집창을 이용하며 기존 상태 및 localStorage 저장 구조는 유지한다.

주요 변경 파일: ProfileScreen.tsx, ProfileHeader.tsx, ProfileEditor.tsx, AboutTab.tsx 및 AboutTab.module.css. 모두 frontend/app/users/[userId]/_components에 위치한다. 브라우저 검증 스크립트는 scripts/verify-profile-sections.mjs.

검증: npm test 27개 파일·376개 테스트 통과, npm run build(TypeScript 검사 포함) 성공. Chromium에서 기본 정보/소개/기술/관심/링크 분리 편집과 저장·새로고침 복원, 상단 중복 정보 제거, 5개 탭 전환, 다른 사용자 수정 버튼 숨김 확인. pageerror 0건. 기존 포트폴리오·게시글 CRUD 전체 흐름은 이번 브라우저 검증에서 재실행하지 않았다.

## 추가: 기본 한 줄 소개 및 사용자 정의 항목

- 한 줄 소개는 기본 정보 수정창으로 이동했다. 상단 프로필에 표시하며 상세 소개 입력창과 분리한다. 기존 한 줄 소개 데이터와 검색·추천 카드 연동은 유지한다.
- 기술 스택/관심 분야에 직접 입력과 선택 태그 삭제를 제공한다. 각 항목 1~30자, 최대 30개이며 대소문자 무시 중복 입력을 차단한다. 기존 선택 목록도 유지한다.
- GitHub/블로그 외에 링크 이름과 URL을 직접 입력하는 추가 링크를 최대 10개 제공한다. 이름 40자, URL 500자 이내이며 http/https만 허용한다. 추가·삭제가 가능하다.
- Profile.externalLinks는 선택 필드로 추가되어 기존 저장 데이터도 그대로 복원된다. 태그는 기존 stacks/interests 배열을 활용한다. 저장 검증과 복원 구조 검증을 함께 추가했다.
- 추가/변경 파일: ProfileEditor.tsx, ProfileTagEditor.tsx, ProfileHeader.tsx, AboutTab.tsx, types/profile.ts, lib/profiles/profileService.ts, profileCustomFields.test.ts, scripts/verify-profile-custom-fields.mjs.
- 최종 검증: 28개 파일·379개 테스트 통과, npm run build(TypeScript 검사 포함) 성공. Chromium에서 한 줄 소개 수정, 직접 입력 태그 추가·중복 차단, 링크 추가/삭제, 새로고침 복원 확인.

## 추가 입력 UI 정리

긴 추가 버튼 대신 오른쪽 끝의 40px + 아이콘 버튼을 사용한다. 기술/관심은 입력칸 옆, 외부 링크는 섹션 제목 오른쪽에 배치한다. 삭제는 × 아이콘으로 표시하고 접근성 이름은 유지한다. 링크 이름과 URL은 PC에서 가로, 좁은 화면에서는 세로로 배치한다. ProfileTagEditor.tsx 및 CSS Module, ProfileEditor.tsx 및 CSS Module 변경.

검증: 379개 테스트·빌드(TypeScript 포함) 통과. Chromium에서 기존 추가/삭제/저장 시나리오 통과, 1440px/390px + 아이콘 크기와 모달 가로 넘침 확인. PC 외부 링크 모달 캡처 직접 확인.

## 닉네임 및 @태그 편집

기본 정보 수정에 닉네임과 태그 입력을 추가했다. 닉네임은 기존 회원가입과 같은 2~20자 정책, 태그는 @ 없이 영문·숫자·밑줄·하이픈 2~30자다. 앞뒤 공백을 정리하고 다른 프로필과 대소문자 무시 중복 검사를 수행한다. 계정 ID와 프로필 URL은 유지하므로 프로젝트·게시글 등은 재연결할 필요가 없다.

ProfileEdit에는 선택적 nickname/handle 필드를 추가하여 기존 호출과 호환한다. 프로필 저장 후 AuthProvider의 표시 이름을 갱신하고, 새로고침 때는 기존 프로필 저장소에서 이름을 복원한다. 데모 계정 전환과 프로필 초기화에도 반영한다.

변경 파일: ProfileEditor.tsx, types/profile.ts, lib/profiles/profileService.ts, lib/state/ProfileProvider.tsx, lib/state/AuthProvider.tsx, profileIdentity.test.ts, scripts/verify-profile-identity.mjs.

검증: 29개 파일·382개 테스트 통과, npm run build(TypeScript 포함) 성공. 실제 Chromium에서 중복 닉네임 거절, 닉네임/태그 변경, 기존 ID/주소 유지, 헤더 즉시 갱신, 새로고침·계정 전환·변경한 태그로 검색 시 일관성 확인. pageerror 0건.

## 프로필·배너 이미지 변경

- 태그 입력 라벨에서 괄호 설명을 제거했다.
- 기본 정보 수정에 프로필 이미지/배너 이미지 파일 선택, 미리보기, 삭제 기능을 추가했다. 취소 시 저장하지 않고, 저장 시 프로필 화면 및 새로고침에 반영한다. 이미지를 삭제하면 기존 기본 아바타/배너 디자인으로 돌아간다.
- 기존 온보딩 이미지 정책과 읽기/검증 로직을 재사용한다. 각 이미지 PNG/JPEG/WebP 256KB 이하, 브라우저에서 실제 이미지 로드 확인 후 반영하며 처리 중 저장을 막는다. 실제 업로드 API는 사용하지 않는다.
- Profile.banner 및 ProfileEdit.avatar/banner는 선택 필드로, 기존 버전의 localStorage 데이터와 호환된다. 사진·배너 저장과 복원에 형식/크기 검증을 적용한다.
- 변경 파일: ProfileEditor.tsx, ProfileImageEditor.tsx 및 CSS Module, ProfileHeader.tsx 및 CSS Module, types/profile.ts, lib/profiles/profileService.ts, profileImages.test.ts, verify-profile-images.mjs, verify-profile-identity.mjs.
- 검증: 30개 파일·385개 테스트 통과, npm run build(TypeScript 포함) 성공. Chromium에서 파일 선택·미리보기·저장·새로고침 복원·취소·삭제·잘못된 형식 거절 확인. 1440px/390px 모달 가로 넘침 없음. pageerror 0건.

## 배너·프로필 겹침 편집 UI

기본 정보 이미지 영역을 배너와 하단에 겹치는 프로필 사진으로 구성했다. 파일 선택 입력은 숨기고 각 이미지의 + 아이콘을 누를 때만 파일 선택창을 연다. 이미지가 있으면 작은 × 삭제 아이콘도 표시한다. 기존 형식·크기·미리보기·저장·취소 정책은 유지한다.

변경: ProfileImageEditor.tsx 및 CSS Module, ProfileEditor.tsx 및 CSS Module. 검증: 385개 테스트·빌드 통과, Chromium에서 실제 + 클릭으로 filechooser 이벤트 발생과 입력 숨김, PC/모바일 겹침 배치·가로 넘침 없음 확인. 기존 파일 선택/저장/복원/삭제/취소 시나리오 통과. PC 편집창 캡처 직접 확인.

## PR 브랜치 검증

선행 PR #5 기준으로 구성한 이번 PR 브랜치에서도 npm ci 후 30개 파일·385개 테스트와 npm run build(TypeScript 포함)가 통과했다. 첫 실행에서는 임시 체크아웃에 sanitize-html이 설치되지 않아 실패했으며, lockfile에 맞춘 의존성 설치 후 재검증했다. 위 브라우저 검증은 다른 채팅·가독성 변경도 있는 작업 디렉터리에서 수행했다. 이번 PR에는 프로필 관련 스타일의 가독성 조정이 포함된다.
