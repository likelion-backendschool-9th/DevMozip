import type { AuthUser, SignupInput } from '../types/auth';
export const demoPassword = 'Devmozip123!';
export const demoAccounts: AuthUser[] = [
 {id:'demo-user',email:'member@devmozip.demo',nickname:'김하늘',role:'free',job:'프론트엔드 개발자',career:'3년 차',stacks:['React','TypeScript'],interests:['생산성'],available:true},
 {id:'demo-premium',email:'premium@devmozip.demo',nickname:'이서준',role:'premium',job:'백엔드 개발자',career:'4년 차',stacks:['Java','Spring Boot'],interests:['핀테크'],available:true},
 {id:'demo-admin',email:'admin@devmozip.demo',nickname:'데브모집 관리자',role:'admin',job:'풀스택 개발자',career:'5년 이상',stacks:['React','Java'],interests:['커뮤니티'],available:false},
];
export const roleLabels = {free:'일반 회원',premium:'프리미엄 회원',admin:'관리자'};
export const jobOptions=['프론트엔드 개발자','백엔드 개발자','풀스택 개발자','모바일 개발자','AI 엔지니어','DevOps 엔지니어','디자이너'];
export const careerOptions=['신입 / 학습 중','1년 미만','1년 차','2년 차','3년 차','4년 차','5년 이상'];
export const emptySignup: SignupInput={email:'',password:'',confirmPassword:'',nickname:'',job:'',career:'',stacks:[],interests:[],available:true};
