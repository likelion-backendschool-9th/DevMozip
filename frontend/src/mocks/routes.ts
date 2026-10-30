import type { RouteDefinition } from '../types';
export const routes: RouteDefinition[] = [
 {path:'/my-projects',title:'내 프로젝트 관리',description:'모집·참여·지원·종료 프로젝트',stage:3,group:'프로젝트'},
 {path:'/my-projects/:projectId/manage',title:'내 프로젝트 워크스페이스',description:'주최자/참여자별 관리',stage:3,group:'프로젝트'},
 {path:'/projects/:projectId/reviews/write',title:'프로젝트 팀원 리뷰',description:'참여자별 임시 요청 링크',stage:3,group:'프로젝트'},
 {path:'/onboarding',title:'프로필 온보딩',description:'4단계 프로필 완성',stage:2,group:'계정'},
 {path:'/profile/me/likes',title:'좋아요한 글',description:'내가 좋아요한 SNS 게시글',stage:2,group:'계정'},
 {path:'/profile/me/bookmarks',title:'내 북마크함',description:'저장한 게시글과 프로젝트',stage:2,group:'계정'},
 {path:'/settings/account',title:'개인정보 및 계정 설정',description:'Mock 계정·연동·결제·보안',stage:2,group:'계정'},
 { path: '/', title: '홈', description: '새로운 동료와 프로젝트를 발견하는 공간', stage: 1, group: '시작' },
 { path: '/login', title: '로그인', description: '이메일 로그인과 데모 계정 전환', stage: 2, group: '계정' },
 { path: '/forgot-password', title: '비밀번호 찾기', description: 'Mock 비밀번호 복구 안내', stage: 2, group: '계정' },
 { path: '/signup', title: '회원가입', description: '개발 직군, 기술 스택과 관심 분야 등록', stage: 2, group: '계정' },
 { path: '/search', title: '통합 검색', description: '기술과 관심사가 맞는 개발자와 프로젝트 탐색', stage: 2, group: '탐색' },
 { path: '/users/:userId', title: '개발자 프로필', description: '소개 · 포트폴리오 · 내 글 · 활동 · 리뷰의 5개 가로 탭', stage: 2, group: '탐색' },
 { path: '/projects', title: '프로젝트 모집', description: '새로운 협업을 시작할 프로젝트 탐색', stage: 2, group: '프로젝트' },
 { path: '/projects/:projectId', title: '프로젝트 상세', description: '프로젝트 목표, 모집 직군과 요구 기술 확인', stage: 2, group: '프로젝트' },
 { path: '/matching', title: 'AI 팀원 추천', description: '프로젝트 설명과 기술 스택 기반 Mock 추천', stage: 3, group: '탐색' },
 { path: '/projects/new', title: '모집 공고 작성', description: '프리미엄 회원의 프로젝트 모집 공고 작성', stage: 3, group: '프로젝트' },
 { path: '/projects/:projectId/manage', title: '프로젝트 관리', description: '지원 승인, 팀원 관리와 프로젝트 상태 변경', stage: 3, group: '프로젝트' },
 { path: '/feed', title: 'SNS 피드', description: '개발 지식과 경험을 공유하는 세로형 피드', stage: 3, group: '커뮤니티' },
 { path: '/posts/new', title: '게시글 작성', description: '카테고리, 본문과 기술 태그로 경험 공유', stage: 3, group: '커뮤니티' },
 { path: '/posts/:postId/edit', title: '게시글 수정', description: '본인 게시글 전체 항목 수정', stage: 3, group: '커뮤니티' },
 { path: '/posts/:postId', title: '게시글 상세', description: '본문, 댓글, 좋아요와 퍼가기', stage: 3, group: '커뮤니티' },
 { path: '/following', title: '팔로잉', description: '함께하고 싶은 개발자와 연결', stage: 3, group: '커뮤니티' },
 { path: '/messages', title: '쪽지', description: '개발자와 1:1 대화 및 협업 제안', stage: 4, group: '소통' },
 { path: '/chats/:projectId', title: '프로젝트 채팅', description: '참여 상태와 연결되는 팀 단체 대화', stage: 4, group: '소통' },
 { path: '/notifications', title: '알림', description: '프로젝트와 개발자 활동 소식 확인', stage: 4, group: '소통' },
 { path: '/premium', title: '프리미엄', description: '요금제 비교와 데모 구독 상태 관리', stage: 4, group: '운영' },
 { path: '/admin', title: '관리자', description: '회원, 프로젝트, 게시글과 신고 관리', stage: 4, group: '운영' },
];
export const navigation = [{ label: '프로젝트 모집', to: '/projects' }, { label: 'SNS', to: '/feed' }, { label: '개발자 탐색', to: '/search?target=developer' }];
export const roadmap = [
 { number: '01', title: '기반을 단단하게', label: '현재 단계', text: '프로젝트 구조 · 라우팅 · 디자인 시스템' },
 { number: '02', title: '개발자를 만나는 화면', label: '다음 단계', text: '메인 · 계정 · 검색 · 프로필 · 프로젝트' },
 { number: '03', title: '협업으로 이어지는 경험', label: '예정', text: '추천 · 지원 · SNS · 팔로우 · 리뷰' },
 { number: '04', title: '함께 만드는 공간', label: '예정', text: '메시지 · 채팅 · 알림 · 프리미엄 · 관리자' },
 { number: '05', title: '발표 준비', label: '예정', text: '화면 통일 · 시나리오 검증 · 오류 수정' },
];
export const principles = [
 { title: '관심에서 연결로', text: '기술 스택과 관심 분야를 바탕으로 함께 성장할 개발자를 발견하세요.', icon: 'users' },
 { title: '아이디어에서 프로젝트로', text: '새로운 아이디어에 필요한 동료를 만나 협업을 시작하세요.', icon: 'code' },
 { title: '경험에서 신뢰로', text: '함께 만든 프로젝트와 협업 경험을 나만의 기록으로 쌓아가세요.', icon: 'star' },
];
