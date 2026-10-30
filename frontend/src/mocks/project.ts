import { projects } from './home';
import type { CreateProjectInput, RecruitmentProject, RecruitmentState } from '../types/project';
export const recruitmentRoles=['프론트엔드','백엔드','AI 엔지니어','풀스택','디자이너'];
export const projectFields=['생산성','핀테크','교육','커뮤니티','AI'];
export const reportReasons=['허위 모집 정보','스팸 또는 광고','부적절한 내용','기타'];
export const cancellationPolicy={days:3,examplePenalty:0.5};
const hosts:Record<string,string>={flowmate:'yuna',pocket:'demo-premium',learnloop:'seoyeon',devgarden:'demo-premium',devlog:'yuna',studybridge:'seoyeon',codeatlas:'minjun',skillnest:'seoyeon'};
const periods:Record<string,[string,string,string]>={flowmate:['2026-10-25','2027-01-25','2026-10-20'],pocket:['2026-11-03','2027-03-03','2026-10-15'],learnloop:['2026-11-15','2027-02-15','2026-10-25'],devgarden:['2026-11-10','2027-01-10','2026-10-20'],devlog:['2026-08-01','2026-10-20','2026-07-01'],studybridge:['2026-03-01','2026-05-30','2026-02-01'],codeatlas:['2026-10-01','2027-02-01','2026-09-01'],skillnest:['2026-10-10','2027-01-10','2026-09-25']};
export const recruitmentProjects:RecruitmentProject[]=projects.map(p=>{const dates=periods[p.id];return {...p,hostId:hosts[p.id],description:`${p.summary}\n\n작은 팀에서 문제를 정의하고, 아이디어를 실제 사용자 경험으로 연결합니다. 매주 진행 상황을 공유하고 코드 리뷰와 회고로 함께 성장하는 팀을 지향합니다.`,goal:'핵심 사용자 흐름을 갖춘 MVP를 완성하고, 팀 테스트와 피드백으로 사용성을 개선합니다.',developmentStart:dates[0],developmentEnd:dates[1],recruitmentStart:dates[2],slots:p.roles.map((role,i)=>({role,count:Math.floor(p.capacity/p.roles.length)+(i<p.capacity%p.roles.length?1:0),filled:Math.floor(p.members/p.roles.length)+(i<p.members%p.roles.length?1:0),minYears:p.id==='pocket'?1:0}))};});
export const initialRecruitmentState:RecruitmentState={version:1,kickVotes:[],chatExclusions:{},reviewRequests:[],created:[],applications:[],bookmarks:{},reports:[],overrides:{},deletedIds:[],notifications:[],chatMembers:{},endedAt:{},deletedChatIds:[]};
export const emptyProjectInput:CreateProjectInput={title:'',summary:'',description:'',goal:'',field:'생산성',stacks:[],developmentStart:'',developmentEnd:'',recruitmentStart:'',deadline:'',slots:[{role:'프론트엔드',count:1,minYears:0}]};

export const seedProjectMembers:Record<string,{userId:string;role:string}[]>={
 flowmate:[{userId:'yuna',role:'프론트엔드 / 주최자'},{userId:'demo-user',role:'프론트엔드'},{userId:'minjun',role:'백엔드'}],
 pocket:[{userId:'demo-premium',role:'백엔드 / 주최자'},{userId:'yuna',role:'디자이너'}],
 learnloop:[{userId:'seoyeon',role:'AI 엔지니어 / 주최자'},{userId:'minjun',role:'AI 엔지니어'},{userId:'jiho',role:'프론트엔드'},{userId:'demo-user',role:'프론트엔드'}],
 devgarden:[{userId:'demo-premium',role:'백엔드 / 주최자'},{userId:'jiho',role:'프론트엔드'}],
};
