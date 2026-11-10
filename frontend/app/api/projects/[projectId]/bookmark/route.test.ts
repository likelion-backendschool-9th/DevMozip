import {afterEach,beforeEach,expect,it,vi} from 'vitest';
vi.mock('@/lib/auth/backend',()=>({backendFetch:vi.fn()}));
vi.mock('@/lib/auth/session',()=>({getSession:vi.fn()}));
import {backendFetch} from '@/lib/auth/backend';
import {getSession} from '@/lib/auth/session';
import {POST,DELETE} from './route';
import {GET} from '../../bookmarks/route';
const context=(projectId='1')=>({params:Promise.resolve({projectId})});
const req=(method:string,origin='http://localhost')=>new Request('http://localhost/api/projects/1/bookmark',{method,headers:{Origin:origin}});
beforeEach(()=>{vi.stubEnv('PROJECT_API_ENABLED','true');vi.mocked(backendFetch).mockReset();vi.mocked(getSession).mockResolvedValue({username:'member',role:'USER',isAdmin:false,token:'secret'});});
afterEach(()=>vi.unstubAllEnvs());
it('저장·삭제는 사용자 ID 본문을 전달하지 않는다',async()=>{
 for(const [handler,method] of [[POST,'POST'],[DELETE,'DELETE']] as const){
  vi.mocked(backendFetch).mockResolvedValueOnce(Response.json({projectId:1,bookmarked:method==='POST'}));
  const request=new Request('http://localhost/api/projects/1/bookmark',{method,headers:{Origin:'http://localhost'},body:JSON.stringify({userId:999})});
  expect((await handler(request,context())).status).toBe(200);
  expect(backendFetch).toHaveBeenLastCalledWith('/api/v1/projects/1/bookmark',{method});
 }
});
it('교차 출처·비로그인·잘못된 ID를 차단한다',async()=>{
 expect((await POST(req('POST','https://other.test'),context())).status).toBe(403);
 vi.mocked(getSession).mockResolvedValueOnce(null);expect((await DELETE(req('DELETE'),context())).status).toBe(401);
 expect((await POST(req('POST'),context('../2'))).status).toBe(400);expect(backendFetch).not.toHaveBeenCalled();
});
it('개인 목록은 서버 세션을 사용하고 연결 실패를 안내한다',async()=>{
 vi.mocked(backendFetch).mockResolvedValueOnce(Response.json([]));expect((await GET()).status).toBe(200);
 expect(backendFetch).toHaveBeenLastCalledWith('/api/v1/projects/bookmarks',{});
 vi.mocked(backendFetch).mockRejectedValueOnce(new Error('private'));expect((await GET()).status).toBe(502);
});
