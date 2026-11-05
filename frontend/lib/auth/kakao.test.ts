import { describe, expect, it } from 'vitest';
import { kakaoNotice, kakaoResultPath, parseKakaoFlow, serializeKakaoFlow } from './kakao';
import { needsFirstPassword, passwordChangeErrors, passwordSetErrors } from '@/lib/account/accountAreaService';
import { validateKakaoSignup, validateSignup } from '@/lib/account/authService';
import { emptySignup } from '@/mocks/auth';

describe('카카오 인증 흐름 값', () => {
    it('저장한 값을 그대로 읽는다', () => {
        const flow = { state: 'state', intent: 'link', next: '/settings/privacy' } as const;
        expect(parseKakaoFlow(serializeKakaoFlow(flow))).toEqual(flow);
    });
    it('손상되었거나 알 수 없는 의도면 읽지 않는다', () => {
        for (const value of [undefined, '', '{broken', 'null', '{"state":"s"}', '{"state":"","intent":"login"}', '{"state":"s","intent":"admin"}']) expect(parseKakaoFlow(value)).toBeNull();
    });
    it('외부 주소로 이동하지 않도록 next를 내부 경로로 고친다', () => {
        expect(parseKakaoFlow('{"state":"s","intent":"login","next":"//outside.test"}')?.next).toBe('/');
    });
    it('의도별로 결과를 보여 줄 화면이 정해져 있다', () => {
        expect(kakaoResultPath('login', 'failed')).toBe('/login?kakao=failed');
        expect(kakaoResultPath('signup', 'already-registered')).toBe('/signup?kakao=already-registered');
        expect(kakaoResultPath('link', 'linked')).toBe('/settings/privacy?kakao=linked');
        expect(kakaoResultPath('unlink', 'unlinked')).toBe('/settings/privacy?kakao=unlinked');
    });
});

describe('카카오 결과 안내', () => {
    it('이미 등록된 회원은 정해진 문구로 안내한다', () => {
        expect(kakaoNotice('already-registered')).toEqual({ kind: 'error', message: '이미 등록된 회원입니다.' });
    });
    it('성공 결과와 오류 결과를 구분한다', () => {
        for (const code of ['signup', 'linked', 'unlinked']) expect(kakaoNotice(code)?.kind).toBe('success');
        for (const code of ['cancelled', 'invalid-state', 'failed', 'suspended', 'unavailable', 'conflict', 'unlink-blocked']) expect(kakaoNotice(code)?.kind).toBe('error');
    });
    it('목록에 없는 값은 URL로 넣어도 표시하지 않는다', () => {
        for (const code of [null, '', 'unknown', '<script>alert(1)</script>', 'constructor', '__proto__', 'toString']) expect(kakaoNotice(code)).toBeNull();
    });
});

describe('비밀번호 없는 카카오 회원', () => {
    it('카카오를 연동했고 비밀번호가 없을 때만 첫 비밀번호를 설정한다', () => {
        expect(needsFirstPassword({ kakaoLinked: true, passwordSet: false })).toBe(true);
        expect(needsFirstPassword({ kakaoLinked: true, passwordSet: true })).toBe(false);
        expect(needsFirstPassword({ kakaoLinked: false, passwordSet: true })).toBe(false);
        expect(needsFirstPassword({ kakaoLinked: false, passwordSet: false })).toBe(false);
        expect(needsFirstPassword(null)).toBe(false);
    });
    it('첫 비밀번호는 현재 비밀번호 없이 새 비밀번호 규칙만 확인한다', () => {
        expect(passwordSetErrors({ newPassword: 'Devmozip123', confirmPassword: 'Devmozip123' })).toEqual([]);
        expect(passwordSetErrors({ newPassword: 'short', confirmPassword: 'other' })).toHaveLength(2);
    });
    it('비밀번호가 있는 회원의 입력 검사는 그대로다', () => {
        expect(passwordChangeErrors({ currentPassword: '', newPassword: 'short', confirmPassword: 'other' })).toEqual(['현재 비밀번호를 입력해 주세요.', '새 비밀번호는 영문·숫자를 포함해 8~64자로 입력해 주세요.', '새 비밀번호 확인이 일치하지 않습니다.']);
        expect(passwordChangeErrors({ currentPassword: 'Same1234', newPassword: 'Same1234', confirmPassword: 'Same1234' })).toEqual(['현재 비밀번호와 다른 값을 입력해 주세요.']);
    });
    it('카카오 가입은 비밀번호 오류만 빼고 같은 규칙으로 검사한다', () => {
        expect(validateSignup(emptySignup)).toHaveProperty('password');
        const errors = validateKakaoSignup(emptySignup);
        expect(errors).not.toHaveProperty('password');
        expect(errors).not.toHaveProperty('confirmPassword');
        for (const key of ['email', 'nickname', 'job', 'career', 'stacks', 'interests']) expect(errors).toHaveProperty(key);
        expect(validateKakaoSignup({ ...emptySignup, email: 'new@devmozip.test', nickname: '새회원', job: '백엔드 개발자', career: '3년 차', stacks: ['Java'], interests: ['AI'] })).toEqual({});
    });
});
