import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
vi.mock('@/lib/auth/session', () => ({ getSession: vi.fn() }));
import { backendFetch } from '@/lib/auth/backend';
import { getSession } from '@/lib/auth/session';
import { POST } from './route';
const request = (body: unknown = {}, origin = 'http://localhost') => new Request('http://localhost/api/projects', { method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
beforeEach(() => {
 vi.stubEnv('PROJECT_CREATION_API_ENABLED', 'true'); vi.mocked(backendFetch).mockReset();
 vi.mocked(getSession).mockReset(); vi.mocked(getSession).mockResolvedValue({ username: 'premium', role: 'PREMIUM', isAdmin: false, token: 'not-exposed' });
});
afterEach(() => vi.unstubAllEnvs());
describe('프로젝트 생성 BFF', () => {
 it('준비 전에는 백엔드 호출을 차단한다', async () => {
  vi.stubEnv('PROJECT_CREATION_API_ENABLED', 'false'); expect((await POST(request())).status).toBe(503); expect(backendFetch).not.toHaveBeenCalled();
 });
 it('외부 출처와 잘못된 본문을 차단한다', async () => {
  expect((await POST(request({}, 'https://external.test'))).status).toBe(403);
  expect((await POST(request([]))).status).toBe(400); expect(backendFetch).not.toHaveBeenCalled();
 });
 it('비로그인 또는 실제 무료 회원을 차단한다', async () => {
  vi.mocked(getSession).mockResolvedValue(null); expect((await POST(request())).status).toBe(401);
  vi.mocked(getSession).mockResolvedValue({ username: 'free', role: 'USER', isAdmin: false, token: 'token' });
  expect((await POST(request())).status).toBe(403); expect(backendFetch).not.toHaveBeenCalled();
 });
 it('신원과 상태 필드를 제거하고 생성 API로 전달한다', async () => {
  vi.mocked(backendFetch).mockResolvedValue(Response.json({ id: 9, title: '제목', hostMemberId: 1 }, { status: 201 }));
  const res = await POST(request({ title: '제목', hostMemberId: 999, status: 'COMPLETED', role: 'ADMIN' }));
  expect(res.status).toBe(201); expect(await res.json()).toEqual({ id: 9, title: '제목' });
  const [path, init] = vi.mocked(backendFetch).mock.calls[0]; expect(path).toBe('/api/v1/projects');
  expect(JSON.parse(String(init?.body))).toEqual({ title: '제목' }); expect(res.headers.get('cache-control')).toBe('no-store');
 });
 it.each([400, 401, 403, 409])('오류 %s를 유지한다', async status => {
  vi.mocked(backendFetch).mockResolvedValue(new Response('private internal error', { status }));
  const res = await POST(request()); expect(res.status).toBe(status); expect(await res.text()).not.toContain('private');
 });
 it('연결 실패와 잘못된 성공 응답을 오류로 표시한다', async () => {
  vi.mocked(backendFetch).mockRejectedValue(new Error('secret')); expect((await POST(request())).status).toBe(502);
  vi.mocked(backendFetch).mockResolvedValue(Response.json({ id: -1 }, { status: 201 })); expect((await POST(request())).status).toBe(502);
 });
});
