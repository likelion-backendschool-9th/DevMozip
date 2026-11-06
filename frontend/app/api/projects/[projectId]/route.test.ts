import {beforeEach,afterEach,it,expect,vi} from 'vitest';
vi.mock('@/lib/auth/backend',()=>({backendFetch:vi.fn()}));
vi.mock('@/lib/auth/session',()=>({getSession:vi.fn()}));
import {backendFetch} from '@/lib/auth/backend';
import {getSession} from '@/lib/auth/session';
import {GET,DELETE,PUT} from './route';
import {PATCH} from './update/route';
const context=(projectId='1')=>({params:Promise.resolve({projectId})});
const req=(method:string,body?:unknown,origin='http://localhost')=>new Request('http://localhost/api/projects/1',{method,headers:{Origin:origin,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});
beforeEach(()=>{vi.stubEnv('PROJECT_API_ENABLED','true');vi.mocked(backendFetch).mockReset();vi.mocked(getSession).mockResolvedValue({username:'member',role:'USER',isAdmin:false,token:'secret'});});
afterEach(()=>vi.unstubAllEnvs());
it('잘못된 ID를 백엔드로 전달하지 않는다',async()=>{expect((await GET(req('GET'),context('../2'))).status).toBe(400);expect(backendFetch).not.toHaveBeenCalled();});
it('구독을 해지한 기존 주최자의 수정 요청을 백엔드 인가로 전달한다',async()=>{vi.mocked(backendFetch).mockResolvedValue(new Response(null,{status:409}));expect((await PUT(req('PUT',{title:'수정',hostMemberId:999,status:'COMPLETED'}),context())).status).toBe(409);const [path,init]=vi.mocked(backendFetch).mock.calls[0];expect(path).toBe('/api/v1/projects/1');expect(JSON.parse(String(init?.body))).toEqual({title:'수정'});});
it('삭제는 동일 출처·로그인을 요구하고 204를 유지한다',async()=>{expect((await DELETE(req('DELETE',undefined,'https://other.test'),context())).status).toBe(403);vi.mocked(getSession).mockResolvedValueOnce(null);expect((await DELETE(req('DELETE'),context())).status).toBe(401);vi.mocked(backendFetch).mockResolvedValue(new Response(null,{status:204}));expect((await DELETE(req('DELETE'),context())).status).toBe(204);});
it('종료 요청에는 사용자 ID를 전달하지 않고 중복 오류를 유지한다',async()=>{vi.mocked(backendFetch).mockResolvedValue(new Response('sensitive',{status:409}));const res=await PATCH(req('PATCH',{action:'REQUEST_END',userId:999}),context());expect(res.status).toBe(409);expect(await res.text()).not.toContain('sensitive');const [path,init]=vi.mocked(backendFetch).mock.calls[0];expect(path).toBe('/api/v1/projects/1/update');expect(JSON.parse(String(init?.body))).toEqual({action:'REQUEST_END'});});
it('서버 연결 실패를 성공이나 Mock 데이터로 바꾸지 않는다',async()=>{vi.mocked(backendFetch).mockRejectedValue(new Error('private'));expect((await GET(req('GET'),context())).status).toBe(502);});

it('Next 내부 URL과 실제 요청 호스트가 달라도 동일 출처를 허용한다',async()=>{vi.mocked(backendFetch).mockResolvedValue(new Response(null,{status:204}));const request=new Request('http://localhost:3100/api/projects/1',{method:'DELETE',headers:{Host:'127.0.0.1:3100',Origin:'http://127.0.0.1:3100'}});expect((await DELETE(request,context())).status).toBe(204);});
