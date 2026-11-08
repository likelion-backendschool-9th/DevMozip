import { describe, expect, it } from 'vitest';
import { developerReason } from './recommendationPresentation';

describe('추천 개발자 이유 표시', () => {
    it('요청 조건에 포함되고 현재 프로필에도 있는 기술만 안내한다', () => {
        const original = '모집 직군과 일치합니다.\n요청 기술 3개 중 2개가 일치합니다.\n신뢰 온도가 기본 36.5도보다 높습니다.\n협업 가능한 상태이며 요구 경력 필터를 통과했습니다.';
        const display = developerReason(original, ['React', 'Java', 'Figma'], ['React', 'Java', 'Python']);
        expect(display).toContain('제시하신 조건 중 React, Java 기술 스택이 현재 프로필에 등록되어 있습니다.');
        expect(display).not.toMatch(/Figma|Python|모집 직군|3개 중 2개|신뢰 온도/);
        expect(display).toContain('요구 경력 필터');
        expect(original).toContain('3개 중 2개');
    });
    it('새 형식의 저장 이유도 해당 요청 조건과 현재 프로필로 표시한다', () => {
        const reason = 'Java, Spring Boot 기술 스택을 활용할 수 있습니다.';
        expect(developerReason(reason, ['React', 'Java'], ['Java', 'Spring Boot'])).toBe('제시하신 조건 중 Java 기술 스택이 현재 프로필에 등록되어 있습니다.');
    });
    it('기술 별칭과 대소문자는 서버와 동일하게 대조한다', () => {
        expect(developerReason('요청 기술 3개 중 2개가 일치합니다.', ['React.js', 'Spring Boot'], ['react', 'springboot'])).toContain('React.js, Spring Boot 기술 스택');
    });
    it('프로필이 변경되었거나 조회 불가하면 일치 기술을 만들어내지 않는다', () => {
        const reason = 'Java 기술 스택을 활용할 수 있습니다.';
        expect(developerReason(reason, ['React'], ['Java'])).toBe('현재 프로필에서 제시하신 기술 조건에 해당하는 스택을 확인할 수 없습니다.');
        expect(developerReason(reason, [], ['Java'])).not.toContain('Java 기술 스택이');
        expect(developerReason(reason, ['Java'])).toBe('저장된 추천 이유를 확인할 수 없습니다.');
    });
});
