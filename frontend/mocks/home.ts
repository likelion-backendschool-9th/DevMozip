import { initialMatchingChat } from '@/mocks/matchingChat';
import { initialMatchingState } from '@/mocks/matching';
import type { Developer, HomeState, Post, Project, ProjectSort } from '@/types/home';
export const stackOptions = ['React', 'TypeScript', 'Java', 'Spring Boot', 'Python', 'Node.js', 'Figma'];
export const interestOptions = ['전체 분야', '생산성', '핀테크', '교육', '커뮤니티', 'AI'];
export const categories = ['전체', '정보', '피드백', '홍보', '자유'] as const;
export const projectSortOptions: { value: ProjectSort; label: string }[] = [{value:'latest',label:'최신순'},{value:'popular',label:'인기순'},{value:'deadline',label:'마감 임박순'}];
export const defaultHomeState: HomeState = {version:1,chat:initialMatchingChat,matching:initialMatchingState,input:{description:'',stacks:[],interest:'전체 분야',role:'',skipStacks:false,skipInterest:false,skipRole:false}};
export const developers: Developer[] = [
{id:'yuna',name:'이유나',role:'프론트엔드 개발자',career:'3년 차',bio:'사용자가 좋아하는 인터페이스를 함께 만들어요.',stacks:['React','TypeScript','Figma'],interests:['생산성','커뮤니티'],color:'lavender',temperature:38.2},
{id:'minjun',name:'박민준',role:'백엔드 개발자',career:'4년 차',bio:'견고한 구조와 유연한 협업을 중요하게 생각합니다.',stacks:['Java','Spring Boot','Python'],interests:['핀테크','생산성'],color:'mint',temperature:39.1},
{id:'seoyeon',name:'최서연',role:'AI 엔지니어',career:'2년 차',bio:'일상에 도움이 되는 AI 서비스를 만들고 싶어요.',stacks:['Python','TypeScript'],interests:['AI','교육'],color:'peach',temperature:37.6},
{id:'jiho',name:'정지호',role:'풀스택 개발자',career:'3년 차',bio:'작은 아이디어를 빠르게 제품으로 만드는 개발자.',stacks:['Node.js','React','TypeScript'],interests:['커뮤니티','교육'],color:'blue',temperature:38.6},
];
export const projects: Project[] = [
{id:'codeatlas',title:'CodeAtlas — 팀의 지식 지도',summary:'함께 정리하고 탐색하는 팀 개발 지식 베이스',field:'생산성',roles:['백엔드'],stacks:['Java','Spring Boot'],members:2,capacity:2,duration:'2026.10–2027.02',deadline:'2026-09-28',createdAt:'2026-09-01',views:100,color:'mint',mark:'C',status:'프로젝트 진행 중'},
{id:'skillnest',title:'SkillNest — 매주 배우는 개발 루틴',summary:'AI 피드백과 스터디로 이어지는 팀 학습 루틴',field:'교육',roles:['AI 엔지니어','풀스택'],stacks:['Python','React'],members:2,capacity:2,duration:'2026.10–2027.01',deadline:'2026-10-09',createdAt:'2026-09-25',views:80,color:'peach',mark:'S',status:'모집 완료'},
{id:'devlog',title:'DevLog — 함께 남기는 개발 회고',summary:'DevMozip에서 만난 동료들과 만든 개발 기록 서비스',field:'커뮤니티',roles:['프론트엔드','백엔드'],stacks:['React','TypeScript','Spring Boot'],members:4,capacity:4,duration:'2026.08–2026.10',deadline:'2026-07-30',createdAt:'2026-07-01',views:110,color:'lavender',mark:'D',status:'프로젝트 종료'},
{id:'studybridge',title:'StudyBridge — 학습을 잇는 스터디',summary:'DevMozip 팀원들과 완성한 학습 도우미 프로젝트',field:'교육',roles:['프론트엔드','AI 엔지니어'],stacks:['React','Python'],members:2,capacity:2,duration:'2026.03–2026.05',deadline:'2026-02-28',createdAt:'2026-02-01',views:90,color:'peach',mark:'S',status:'프로젝트 종료'},
{id:'flowmate',title:'Flowmate — 우리의 집중을 연결하다',summary:'사이드 프로젝트 팀을 위한 가벼운 일정·태스크 관리 서비스',field:'생산성',roles:['프론트엔드','백엔드'],stacks:['React','TypeScript','Spring Boot'],members:3,capacity:5,duration:'3개월 · 온라인',deadline:'2026-11-05',createdAt:'2026-10-29',views:482,color:'lavender',mark:'F'},
{id:'pocket',title:'Pocket — 작게 시작하는 자산 관리',summary:'사회초년생이 쉽게 이해하고 실천하는 개인 금융 대시보드',field:'핀테크',roles:['백엔드','디자이너'],stacks:['Java','Spring Boot','Figma'],members:2,capacity:4,duration:'4개월 · 서울/온라인',deadline:'2026-11-02',createdAt:'2026-10-27',views:738,color:'mint',mark:'P'},
{id:'learnloop',title:'LearnLoop — 함께 배우는 작은 습관',summary:'AI 학습 피드백과 스터디 커뮤니티로 매일 조금씩 성장해요.',field:'교육',roles:['AI 엔지니어','프론트엔드'],stacks:['Python','React','TypeScript'],members:4,capacity:6,duration:'3개월 · 온라인',deadline:'2026-11-12',createdAt:'2026-10-30',views:361,color:'peach',mark:'L'},
{id:'devgarden',title:'DevGarden — 개발자의 성장 정원',summary:'코드 리뷰와 회고를 공유하는 개발자 커뮤니티',field:'커뮤니티',roles:['프론트엔드','백엔드'],stacks:['Node.js','React'],members:2,capacity:5,duration:'2개월 · 온라인',deadline:'2026-11-08',createdAt:'2026-10-26',views:254,color:'blue',mark:'D'},
];
export const posts: Post[] = [
{id:'react-patterns',authorId:'yuna',category:'정보',title:'React 컴포넌트, 어디까지 분리하시나요?',body:'최근 사이드 프로젝트를 진행하며 정리한 컴포넌트 설계 기준을 공유합니다. 재사용성과 가독성 사이에서 고민하고 있다면 함께 이야기해요.',tags:['React','컴포넌트 설계'],likes:42,comments:12,color:'lavender'},
{id:'api-review',authorId:'minjun',category:'피드백',title:'첫 API 설계 문서, 피드백 부탁드려요',body:'팀 프로젝트의 API 명세를 작성하고 있어요. 일관된 응답 구조와 에러 처리에 대한 경험을 듣고 싶습니다.',tags:['Spring Boot','API'],likes:28,comments:8,color:'mint'},
{id:'learning-launch',authorId:'seoyeon',category:'홍보',title:'매일 10분, AI와 함께 공부하는 루틴',body:'작은 학습 도우미의 프로토타입을 완성했어요. 직접 사용해 보시고 의견을 나눠주실 분을 찾습니다.',tags:['AI','사이드 프로젝트'],likes:35,comments:5,color:'peach'},
{id:'weekend-build',authorId:'jiho',category:'자유',title:'이번 주말엔 어떤 걸 만들고 계신가요?',body:'퇴근 후 조금씩 만들던 서비스를 드디어 친구들에게 보여줬어요. 여러분의 작은 성취도 궁금합니다!',tags:['개발 일상','회고'],likes:19,comments:14,color:'blue'},
{id:'type-tips',authorId:'yuna',category:'정보',title:'TypeScript로 폼 상태 안전하게 다루기',body:'복잡해지는 입력 폼에서 놓치기 쉬운 타입과 검증 패턴을 정리해봤습니다.',tags:['TypeScript','폼'],likes:56,comments:9,color:'blue'},
{id:'team-retro',authorId:'minjun',category:'자유',title:'좋은 협업은 작은 약속에서 시작하더라고요',body:'주간 회고 15분을 도입하고 달라진 팀의 이야기를 기록해요.',tags:['협업','팀 문화'],likes:31,comments:7,color:'lavender'},
];
