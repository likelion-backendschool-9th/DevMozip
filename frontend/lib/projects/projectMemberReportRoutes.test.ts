import {beforeEach,expect,it,vi} from 'vitest';
const m=vi.hoisted(()=>({guard:vi.fn(),proxy:vi.fn()}));
vi.mock('@/lib/projects/projectProxy',()=>({projectGuard:m.guard,proxyProject:m.proxy,projectError:(status:number,message:string)=>Response.json({message},{status}),readProjectBody:(r:Request)=>r.json()}));
import {POST} from '@/app/api/projects/[projectId]/members/[memberId]/reports/route';
// 관리자 신고 처리 BFF(/api/admin/project-reports)는 통합 신고 BFF(/api/admin/reports)로 옮겼다. 그 시험은 app/api/admin/reports/route.test.ts에 있다 (docs/admin-plan.md D5).
const context={params:Promise.resolve({projectId:'7',memberId:'8'})};
const req=(body:unknown)=>new Request('http://localhost/api/test',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
beforeEach(()=>{vi.clearAllMocks();m.guard.mockResolvedValue(null);m.proxy.mockResolvedValue(Response.json({id:1}));});
it('신고자와 관리자 필드를 제거하고 프로젝트·팀원 경로에 제출한다',async()=>{await POST(req({reason:'기타',detail:'설명',reporterId:999,status:'RESOLVED'}),context);expect(m.guard).toHaveBeenCalledWith(expect.any(Request));const [url,init]=m.proxy.mock.calls[0];expect(url).toBe('/api/v1/projects/7/members/8/reports');expect(JSON.parse(init.body)).toEqual({reason:'기타',detail:'설명'});});
it.each([{reason:'unknown'},{reason:'기타',detail:3},{reason:'기타',detail:'x'.repeat(1001)}])('유효하지 않은 내용을 전달하지 않는다',async body=>{expect((await POST(req(body),context)).status).toBe(400);expect(m.proxy).not.toHaveBeenCalled();});
it('잘못된 팀원 ID를 거절한다',async()=>{expect((await POST(req({reason:'기타'}),{params:Promise.resolve({projectId:'7',memberId:'../8'})})).status).toBe(400);});
