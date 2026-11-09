import { describe, expect, it } from 'vitest';
import { passwordNotice, readResetToken } from './passwordReset';

describe('재설정 링크의 토큰', () => {
    it('fragment에서 토큰을 꺼낸다', () => {
        expect(readResetToken('#token=abc-DEF_123')).toBe('abc-DEF_123');
    });
    it.each(['', '#', '#token=', '#other=abc'])('토큰이 없는 fragment %s는 null이다', hash => {
        expect(readResetToken(hash)).toBeNull();
    });
});

describe('로그인 화면의 비밀번호 결과 안내', () => {
    it('결과 코드에 맞는 문구를 돌려준다', () => {
        expect(passwordNotice('reset')).toContain('재설정했습니다');
        expect(passwordNotice('changed')).toContain('변경했습니다');
    });
    it.each([null, '', 'unknown', 'constructor', '<b>가짜 안내</b>'])('목록에 없는 코드 %s는 표시하지 않는다', value => {
        expect(passwordNotice(value)).toBeNull();
    });
});
