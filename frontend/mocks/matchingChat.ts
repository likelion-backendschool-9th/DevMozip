import type { ChatRequirements,MatchingChatState } from '@/types/matchingChat';
export const chatFieldOptions=['웹','모바일','AI / 머신러닝','데이터','게임','보안','클라우드 / 인프라','기타'];
export const chatInterestOptions=['생산성','핀테크','교육','커뮤니티','AI',...chatFieldOptions.filter(value=>value!=='기타')];
export const chatRoleOptions=['프론트엔드','백엔드','풀스택','UI/UX','DevOps','데이터 엔지니어','AI 엔지니어','모바일','게임','기타'];
export const chatCareerOptions=['경력 무관','1년 이상','2년 이상','3년 이상','5년 이상'];
export const chatDurationOptions=['1개월','2개월','3개월','6개월','12개월'];
export const emptyChatRequirements:ChatRequirements={description:'',fields:[],interests:[],stacks:[],roles:[],career:'',duration:''};
export const initialMatchingChat:MatchingChatState={conversations:{},active:{}};
export const chatWelcome='만들고 싶은 프로젝트와 조건을 자유롭게 기록해 주세요. 추천 버튼을 누르면 대화와 조건을 저장하고, 직접 선택한 조건으로 개발자와 유사 프로젝트를 추천합니다.';
export const chatRequirementLabels:Record<keyof ChatRequirements,string>={description:'프로젝트 설명 (10자 이상)',fields:'프로젝트 분야',interests:'관심 분야',stacks:'기술 스택',roles:'모집 직군과 직군별 인원',career:'요구 경력',duration:'예상 프로젝트 기간'};
export const chatRoleAliases:Record<string,string[]>={'프론트엔드':['프론트엔드','프론트','frontend'],'백엔드':['백엔드','backend'],'풀스택':['풀스택','fullstack'],'UI/UX':['ui/ux','디자이너','디자인'],'DevOps':['devops'],'데이터 엔지니어':['데이터 엔지니어'],'AI 엔지니어':['ai 엔지니어'],'모바일':['모바일 개발자','모바일 개발'],'게임':['게임 개발자','게임 개발']};
export const chatDomainAliases:Record<string,string[]>={'웹':['웹','web'],'모바일':['모바일','앱'],'AI / 머신러닝':['머신러닝','인공지능','ai'],'데이터':['데이터'],'게임':['게임'],'보안':['보안'],'클라우드 / 인프라':['클라우드','인프라']};
export const chatDomainStacks:Record<string,string[]>={'웹':['React','TypeScript','Java','Spring Boot','Node.js','Figma'],'모바일':['React','TypeScript'],'AI / 머신러닝':['Python'],'데이터':['Python','Java'],'게임':[],'보안':[],'클라우드 / 인프라':['Node.js','Java','Spring Boot']};

// Suggestions are input examples, never simulated AI responses or inferred conditions.
export const chatPromptExamples = [
    { title: '새 프로젝트의 동료 찾기', text: 'React와 Spring Boot로 커뮤니티를 만들고 싶어요. 프론트엔드와 백엔드 동료를 찾고 있어요.' },
    { title: '아이디어와 비슷한 프로젝트', text: '개발자들이 함께 공부하는 서비스를 구상하고 있어요. 비슷한 프로젝트를 살펴보고 싶어요.' },
    { title: '작은 팀으로 시작하기', text: '3개월 동안 작은 웹 서비스를 완성하고 싶어요. 함께할 동료를 찾고 있어요.' },
];
