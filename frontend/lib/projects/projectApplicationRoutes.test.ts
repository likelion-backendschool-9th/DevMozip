import {beforeEach,describe,expect,it,vi} from 'vitest';
const mocked=vi.hoisted(()=>({guard:vi.fn(),proxy:vi.fn()}));
vi.mock('@/lib/projects/projectProxy',()=>({projectGuard:mocked.guard,proxyProject:mocked.proxy,projectError:(status:number,message:string)=>Response.json({message},{status}),readProjectBody:(request:Request)=>request.json()}));
import {POST,GET} from '@/app/api/projects/[projectId]/applications/route';
import {PATCH} from '@/app/api/projects/[projectId]/applications/[applicationId]/route';
import {GET as snapshot} from '@/app/api/projects/[projectId]/applications/[applicationId]/portfolios/[snapshotId]/route';
const context={params:Promise.resolve({projectId:'7'})};
function request(body:unknown){return new Request('http://localhost/api/projects/7/applications',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});}
beforeEach(()=>{vi.clearAllMocks();mocked.guard.mockResolvedValue(null);mocked.proxy.mockResolvedValue(Response.json({id:8}));});
describe('프로젝트 지원 BFF',()=>{
 it('신원·승인 상태를 제출 본문에서 제거한다',async()=>{await POST(request({jobRole:'SRE',portfolioIds:[1,2],introduction:'지원합니다.',applicantMemberId:999,status:'APPROVED'}),context);const [path,init]=mocked.proxy.mock.calls[0];expect(path).toBe('/api/v1/projects/7/applications');expect(JSON.parse(init.body)).toEqual({jobRole:'SRE',portfolioIds:[1,2],introduction:'지원합니다.'});});
 it('보안 검사 실패 시 서버로 전달하지 않는다',async()=>{mocked.guard.mockResolvedValue(Response.json({message:'forbidden'},{status:403}));expect((await POST(request({}),context)).status).toBe(403);expect(mocked.proxy).not.toHaveBeenCalled();});
 it('잘못된 프로젝트 ID는 거절한다',async()=>{expect((await GET(new Request('http://localhost'),{params:Promise.resolve({projectId:'-1'})})).status).toBe(400);expect(mocked.proxy).not.toHaveBeenCalled();});
 it('지원자 목록에는 상태 필터만 전달한다',async()=>{await GET(new Request('http://localhost?status=PENDING&userId=999'),context);expect(mocked.proxy).toHaveBeenCalledWith('/api/v1/projects/7/applications?status=PENDING');});
 it('취소 안내 동의는 명시적인 true만 허용한다',async()=>{await PATCH(request({status:'CANCELLED',acknowledgePenalty:'true',userId:999}),{params:Promise.resolve({projectId:'7',applicationId:'8'})});const [path,init]=mocked.proxy.mock.calls[0];expect(path).toBe('/api/v1/projects/7/applications/8');expect(JSON.parse(init.body)).toEqual({status:'CANCELLED',acknowledgePenalty:false});});
});

describe('제출 포트폴리오 조회 BFF',()=>{
 it('지원서와 제출본 ID를 포함한 경로만 전달한다',async()=>{
  await snapshot(new Request('http://localhost?userId=999'),{params:Promise.resolve({projectId:'7',applicationId:'8',snapshotId:'9'})});
  expect(mocked.proxy.mock.calls[0][0]).toBe('/api/v1/projects/7/applications/8/portfolios/9');
 });
 it('잘못된 제출본 ID는 전달하지 않는다',async()=>{
  expect((await snapshot(new Request('http://localhost'),{params:Promise.resolve({projectId:'7',applicationId:'8',snapshotId:'../9'})})).status).toBe(400);expect(mocked.proxy).not.toHaveBeenCalled();
 });
});
