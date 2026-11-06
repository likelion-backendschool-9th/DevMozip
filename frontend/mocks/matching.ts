import type { MatchingState } from '@/types/matching';
export const matchingLimits={free:5,premium:100,admin:100};
export const initialMatchingState:MatchingState={usage:{},last:{}};
export const matchingLatency=400;
export const stackAliases:Record<string,string[]>={React:['react','리액트'],TypeScript:['typescript','타입스크립트','ts'],Java:['java','자바'],'Spring Boot':['spring boot','springboot','스프링','스프링부트'],Python:['python','파이썬'],'Node.js':['node.js','nodejs','node js','노드'],Figma:['figma','피그마']};
export const interestAliases:Record<string,string[]>={'생산성':['생산성','일정','태스크','할 일','업무','투두'],'핀테크':['핀테크','금융','자산','가계부','결제'],'교육':['교육','학습','공부','스터디'],'커뮤니티':['커뮤니티','sns','소통','피드'],'AI':['ai','인공지능','챗봇','생성형']};
export const roleAliases:Record<string,string[]>={'프론트엔드 개발자':['프론트엔드','프론트','frontend','인터페이스'],'백엔드 개발자':['백엔드','backend','서버','api'],'AI 엔지니어':['ai 엔지니어','머신러닝','모델 학습'],'풀스택 개발자':['풀스택','fullstack'],'모바일 개발자':['모바일','앱 개발'],'디자이너':['디자이너','디자인'],'DevOps 엔지니어':['devops','배포 인프라']};
export const questionLabels={stacks:'어떤 기술을 사용할 계획인가요?',interest:'어떤 분야의 서비스인가요?',role:'어떤 직군의 동료가 필요한가요?'};
