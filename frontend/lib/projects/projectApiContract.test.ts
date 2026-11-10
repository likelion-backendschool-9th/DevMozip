import {describe,it,expect} from 'vitest';
import {pickProjectRequest,validProjectId,parseProjectPage,parseProjectDetail,parseProjectTeam} from './projectApiContract';
const project={id:1,hostMemberId:5,title:'실제 공고',description:'소개',summary:null,goal:null,imageUrl:'https://example.test/a.png',isPublic:true,status:'IN_PROGRESS',recruitmentSlots:[{roleCode:'BACKEND',capacity:2,minimumYears:0}],techStacks:['Java'],interests:['교육'],totalCapacity:3,viewCount:1,createdAt:'2026-11-06T10:00:00',recruitmentStart:'2026-11-06',recruitmentEnd:'2026-11-10',developmentStart:'2026-11-06',developmentEnd:'2026-12-06'};
describe('프로젝트 API 계약',()=>{
 it('클라이언트가 보낸 신원·상태를 제거한다',()=>expect(pickProjectRequest({title:'제목',hostMemberId:999,status:'COMPLETED',role:'ADMIN'})).toEqual(expect.objectContaining({title:'제목'})));
 it('프로젝트 ID 범위를 제한한다',()=>{for(const id of ['0','-1','mock-project','1/../2','9007199254740992'])expect(validProjectId(id)).toBe(false);expect(validProjectId('12')).toBe(true);});
 it('공개 상세와 참여자의 종료 동의 정보를 구분한다',()=>{const body={project,memberCount:2,recruitmentClosed:false,membership:null};expect(parseProjectDetail(body).membership).toBeNull();const membership={myRole:'PARTICIPANT',myEndRequested:true,endRequestCount:1,requiredEndRequestCount:2};expect(parseProjectDetail({...body,membership}).membership).toEqual(membership);expect(()=>parseProjectDetail({...body,membership:{...membership,myRole:'ADMIN'}})).toThrow();});
 it('목록의 잘못된 응답을 빈 결과로 덮어쓰지 않는다',()=>{expect(()=>parseProjectPage({items:[project],totalElements:1,page:0,size:12,totalPages:1})).toThrow();expect(parseProjectPage({items:[{...project,memberCount:2,recruitmentClosed:false,myRole:null}],totalElements:1,page:0,size:12,totalPages:1}).items[0].id).toBe(1);});
});

it('팀원 공개 필드만 전달하고 중복·잘못된 응답은 거부한다',()=>{
 const member={userId:7,tag:'developer',nickname:'개발자',role:'PARTICIPANT',jobRole:'BACKEND'};
 expect(parseProjectTeam([{...member,email:'private@example.test'}])).toEqual([member]);
 expect(()=>parseProjectTeam([member,member])).toThrow();
 expect(()=>parseProjectTeam([{...member,role:'ADMIN'}])).toThrow();
 expect(parseProjectDetail({project,memberCount:1,recruitmentClosed:false,membership:null,team:[member]}).team).toEqual([member]);
});
