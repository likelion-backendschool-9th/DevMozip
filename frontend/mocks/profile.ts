import {portfolioShowcases} from './portfolioShowcases';
import { initialSocialState } from '@/mocks/social';
import { demoAccounts } from '@/mocks/auth';
import { developers, posts } from '@/mocks/home';
import type { AuthUser } from '@/types/auth';
import type { Collaboration, Profile, ProfileState, PortfolioInput } from '@/types/profile';
export const profileTabs=[{id:'about',label:'소개'},{id:'portfolio',label:'포트폴리오'},{id:'posts',label:'포스팅'},{id:'activity',label:'활동'},{id:'reviews',label:'리뷰'}] as const;
export const reviewKeywords=['소통이 원활해요','약속을 잘 지켜요','문제 해결이 뛰어나요','함께 성장해요','피드백이 구체적이에요','지식을 아낌없이 공유해요','책임감 있게 완수해요','일정을 체계적으로 관리해요','코드를 깔끔하게 작성해요','문서를 알기 쉽게 정리해요','새로운 아이디어를 제안해요','동료의 의견을 존중해요','꾸준히 학습하고 성장해요','어려운 상황에도 적극적이에요'];
export const emptyPortfolio:PortfolioInput={coverImage:'',field:'',title:'',summary:'',start:'',end:'',role:'',stacks:[],description:'',features:'',challenge:'',outcome:''};
export function profileFromAccount(user:AuthUser):Profile{return {id:user.id,nickname:user.nickname,handle:user.id.startsWith('demo-')?user.id:user.id.slice(0,8),job:user.job,career:user.career,bio:'함께 배우고 만드는 협업을 기다리고 있어요.',introduction:'안녕하세요! 작은 아이디어를 실제 경험으로 연결하고 싶은 개발자입니다.\n서로의 생각을 나누고 꾸준히 성장하는 팀을 만나고 싶어요.',joinedAt:'2026-10-30',github:'https://github.com',blog:'https://example.com',contact:'평일 19:00–22:00 · 주말 협의',available:user.available,stacks:[...user.stacks],interests:[...user.interests],color:'lavender'};}
const profiles:Record<string,Profile>=Object.fromEntries(demoAccounts.map(a=>[a.id,profileFromAccount(a)]));
for(const d of developers)profiles[d.id]={id:d.id,nickname:d.name,handle:d.id,job:d.role,career:d.career,bio:d.bio,introduction:'사용자에게 필요한 가치를 고민하며 서비스를 만들고 있습니다.\n좋은 코드를 넘어, 좋은 동료와 함께 성장하는 경험을 중요하게 생각해요.\n\n작은 실험과 꾸준한 회고를 통해 더 나은 제품을 만들어가고 싶습니다.',joinedAt:'2026-07-12',github:'https://github.com',blog:'https://example.com',contact:'평일 20:00–23:00 · 주말 오후',available:true,stacks:[...d.stacks],interests:[...d.interests],color:d.color};
export const collaborations:Collaboration[]=[
 {projectId:'devlog',start:'2026-08-01',end:'2026-10-20',status:'프로젝트 종료',members:[{userId:'demo-user',role:'프론트엔드'},{userId:'demo-premium',role:'백엔드'},{userId:'yuna',role:'프론트엔드 / 팀장'},{userId:'minjun',role:'백엔드'}]},
 {projectId:'studybridge',start:'2026-03-01',end:'2026-05-30',status:'프로젝트 종료',members:[{userId:'demo-user',role:'프론트엔드'},{userId:'seoyeon',role:'AI 엔지니어'}]},
 {projectId:'flowmate',start:'2026-10-25',end:'2027-01-25',status:'모집 중',members:[{userId:'yuna',role:'프론트엔드 / 주최자'},{userId:'demo-user',role:'프론트엔드'}]},
 {projectId:'codeatlas',start:'2026-10-01',end:'2027-02-01',status:'프로젝트 진행 중',members:[{userId:'minjun',role:'백엔드 / 주최자'},{userId:'demo-premium',role:'백엔드'}]},
 {projectId:'skillnest',start:'2026-10-10',end:'2027-01-10',status:'모집 완료',members:[{userId:'seoyeon',role:'AI 엔지니어'},{userId:'jiho',role:'풀스택'}]},
];
export const initialProfileState:ProfileState={version:1,socialNotices:[],followPreferences:{},followNotices:[],social:initialSocialState,profiles,portfolios:[
 {field:'개발 기록',...portfolioShowcases['pf-haneul'],id:'pf-haneul',ownerId:'demo-user',title:'DevLog · 개발자의 꾸준한 기록',summary:'개발 회고를 모아 함께 성장하는 기록 서비스',start:'2026-08',end:'2026-10',role:'프론트엔드 개발',stacks:['React','TypeScript']},
 {field:'디자인 시스템',...portfolioShowcases['pf-yuna'],id:'pf-yuna',ownerId:'yuna',title:'Flow UI · 팀의 디자인을 하나로',summary:'작은 팀을 위한 접근성 중심 UI 컴포넌트 모음',start:'2026-04',end:'2026-07',role:'프론트엔드 / UI 설계',stacks:['React','TypeScript','Figma']},
 {field:'핀테크',...portfolioShowcases['pf-minjun'],id:'pf-minjun',ownerId:'minjun',title:'Pocket API · 믿을 수 있는 데이터 흐름',summary:'개인 자산 데이터를 다루는 API 설계 포트폴리오',start:'2026-05',end:'2026-08',role:'백엔드 개발',stacks:['Java','Spring Boot']},
],posts:[...posts.map(p=>({...p,hidden:false})),{id:'haneul-retro',authorId:'demo-user',category:'자유',title:'첫 협업 프로젝트를 마치고 배운 것들',body:'DevLog를 마무리하며 함께한 동료들에게 감사 인사를 전합니다.\n작은 약속을 꾸준히 지키는 것이 가장 큰 힘이 되었어요.',tags:['회고','협업'],likes:12,comments:3,color:'lavender',hidden:false},{id:'seojun-design',authorId:'demo-premium',category:'정보',title:'팀 API 설계에서 먼저 합의한 세 가지',body:'이름 짓기, 오류 처리, 변경을 기록하는 방법.\n문서로 작은 기준을 맞춰두니 협업이 훨씬 편해졌습니다.',tags:['API','팀 문화'],likes:17,comments:4,color:'mint',hidden:false}],reposts:[{id:'rp-haneul',ownerId:'demo-user',originalId:'react-patterns',opinion:'협업 중에도 참고하기 좋은 기준이네요!',hidden:false},{id:'rp-yuna',ownerId:'yuna',originalId:'api-review',opinion:'서로의 관점을 나누는 리뷰를 응원합니다.',hidden:false}],reviews:[{id:'review-seed',projectId:'devlog',authorId:'yuna',targetId:'minjun',rating:5,keywords:['소통이 원활해요','문제 해결이 뛰어나요'],createdAt:'2026-10-23'}],following:{'demo-user':['yuna','minjun'],'demo-premium':['yuna'],yuna:['minjun','demo-user'],minjun:['yuna'],seoyeon:['jiho'],jiho:['seoyeon']}};

export const portfolioDetailFields=[{key:'description',label:'프로젝트 설명',eyebrow:'CONTEXT & GOAL'},{key:'contribution',label:'나의 기여',eyebrow:'MY CONTRIBUTION'},{key:'features',label:'주요 구현 기능',eyebrow:'ENGINEERING'},{key:'challenge',label:'문제 해결 경험',eyebrow:'TECHNICAL DECISIONS'},{key:'outcome',label:'개발 과정 및 결과물',eyebrow:'OUTCOME & LEARNING'}] as const;
