import {beforeEach,describe,expect,it,vi} from 'vitest';
const mocked=vi.hoisted(()=>({guard:vi.fn(),proxy:vi.fn()}));
vi.mock('@/lib/projects/projectProxy',()=>({projectGuard:mocked.guard,proxyProject:mocked.proxy,projectError:(status:number,message:string)=>Response.json({message},{status}),readProjectBody:(r:Request)=>r.json()}));
import {GET,PUT} from '@/app/api/projects/[projectId]/reviews/me/route';
import {POST} from '@/app/api/projects/[projectId]/reviews/me/link/route';
const context={params:Promise.resolve({projectId:'7'})};
beforeEach(()=>{vi.clearAllMocks();mocked.guard.mockResolvedValue(null);mocked.proxy.mockResolvedValue(Response.json({}));});
describe('리뷰 BFF 보안 경계',()=>{
 it('제출자는 세션으로 식별하고 자유 서술·임의 상태를 전달하지 않는다',async()=>{const request=new Request('http://localhost/api/projects/7/reviews/me',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({token:'secret',reviewerMemberId:999,status:'SUBMITTED',entries:[{targetMemberId:8,rating:5,keywords:[],comment:'저장 금지'}]})});await PUT(request,context);expect(JSON.parse(mocked.proxy.mock.calls[0][1].body)).toEqual({token:'secret',entries:[{targetMemberId:8,rating:5,keywords:[]}]});});
 it('타 사이트 링크 발급 요청은 서버에 전달하지 않는다',async()=>{mocked.guard.mockResolvedValue(Response.json({}, {status:403}));expect((await POST(new Request('http://localhost',{method:'POST'}),context)).status).toBe(403);expect(mocked.proxy).not.toHaveBeenCalled();});
 it('잘못된 프로젝트 ID를 거절한다',async()=>{expect((await GET(new Request('http://localhost'),{params:Promise.resolve({projectId:'0'})})).status).toBe(400);expect(mocked.proxy).not.toHaveBeenCalled();});
 it('본인 리뷰 조회는 다른 사용자 쿼리를 전달하지 않는다',async()=>{await GET(new Request('http://localhost?userId=999'),context);expect(mocked.proxy).toHaveBeenCalledWith('/api/v1/projects/7/reviews/me');});
});
