import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { matchingLoginPath, readLoginPrompt, clearLoginPrompt } from './loginPrompt';
const now = 1000000;
let data: Map<string, string>;
beforeEach(() => { data = new Map(); vi.stubGlobal('sessionStorage', { setItem: (k: string, v: string) => data.set(k, v), getItem: (k: string) => data.get(k) || null, removeItem: (k: string) => data.delete(k) }); });
afterEach(() => vi.unstubAllGlobals());
describe('명시적 로그인 프롬프트 인계', () => {
    it('URL에 입력을 노출하지 않고 같은 탭의 일회성 토큰으로 이어간다', () => {
        const target = new URL(matchingLoginPath('비공개 프로젝트 아이디어', now), 'http://localhost').searchParams.get('next')!;
        const token = new URL(target, 'http://localhost').searchParams.get('resume')!;
        expect(target).not.toContain('아이디어');
        expect(readLoginPrompt(token, now + 1)).toBe('비공개 프로젝트 아이디어');
        expect(readLoginPrompt('other', now + 1)).toBeNull();
        clearLoginPrompt(); expect(readLoginPrompt(token, now + 1)).toBeNull();
    });
    it('만료·미래 시각·깨진 데이터는 인계하지 않는다', () => {
        const next = new URL(matchingLoginPath('아이디어', now), 'http://localhost').searchParams.get('next')!;
        const token = new URL(next, 'http://localhost').searchParams.get('resume')!;
        expect(readLoginPrompt(token, now + 600001)).toBeNull();
        expect(readLoginPrompt(token, now - 1)).toBeNull();
        data.set('devmozip:matching-login-prompt:v1', '{'); expect(readLoginPrompt(token, now)).toBeNull();
    });
    it('빈 입력은 저장하지 않으며 저장 실패를 알린다', () => {
        expect(matchingLoginPath('  ', now)).toBe('/login?next=%2Fai%2Fmatching'); expect(data.size).toBe(0);
        vi.stubGlobal('sessionStorage', { setItem: () => { throw new Error('blocked'); } });
        expect(() => matchingLoginPath('유지할 입력', now)).toThrow('입력은 그대로 유지');
        expect(() => matchingLoginPath('a'.repeat(1501), now)).toThrow('1,500');
    });
});
