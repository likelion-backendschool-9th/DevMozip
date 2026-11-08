import { describe, expect, it } from 'vitest';
import { developerReason } from './recommendationPresentation';

describe('추천 개발자 이유 표시', () => {
    it('기존 직군·온도·개수 표현을 정리하고 현재 프로필 기술임을 명시한다', () => {
        const original = '모집 직군과 일치합니다.\n요청 기술 3개 중 2개가 일치합니다.\n신뢰 온도가 기본 36.5도보다 높습니다.\n협업 가능한 상태이며 요구 경력 필터를 통과했습니다.';
        const display = developerReason(original, ['React', 'TypeScript']);
        expect(display).toContain('현재 프로필에 React, TypeScript 기술 스택이 등록되어 있습니다.');
        expect(display).not.toMatch(/모집 직군|3개 중 2개|신뢰 온도/);
        expect(display).toContain('요구 경력 필터');
        expect(original).toContain('3개 중 2개');
    });
    it('새 추천의 당시 기술명은 현재 프로필이 바뀌어도 그대로 유지한다', () => {
        const reason = 'Java, Spring Boot 기술 스택을 활용할 수 있습니다.';
        expect(developerReason(reason, ['React'])).toBe(reason);
    });
    it('현재 프로필을 확인할 수 없으면 기술을 만들지 않는다', () => {
        expect(developerReason('요청 기술 3개 중 2개가 일치합니다.')).toBe('저장된 추천 이유를 확인할 수 없습니다.');
    });
});
