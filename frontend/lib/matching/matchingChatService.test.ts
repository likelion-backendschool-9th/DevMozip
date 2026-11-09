import { describe, expect, it, vi } from 'vitest';
import { addChatStack, buildChatResult, inferChatRequirements, missingChatRequirements, newMatchingConversation, parseMatchingChat, sendMatchingMessage } from '@/lib/matching/matchingChatService';
import { emptyChatRequirements } from '@/mocks/matchingChat';
import { initialMatchingState } from '@/mocks/matching';
import { developers, projects } from '@/mocks/home';
import { demoAccounts } from '@/mocks/auth';
import { consumeMatching } from '@/lib/matching/matchingService';
import { freshHomeState, parseHomeState } from '@/lib/matching/homeService';
import type { ChatRequirements } from '@/types/matchingChat';

const now = new Date('2026-10-30T12:00:00+09:00');
const ready = (): ChatRequirements => ({ description: '팀의 일정을 관리하는 웹 프로젝트입니다.', fields: [], interests: ['생산성', '웹'], stacks: ['React', 'TypeScript'], roles: [{ name: '프론트엔드', count: 2 }, { name: '백엔드', count: 1 }], career: '경력 무관', duration: '3개월' });

describe('로컬 사용자 메시지 저장', () => {
    it.each(['안녕하세요', '설정한 조건으로 추천해 주세요.', 'React 백엔드 3명, 6개월로 변경'])('내용과 관계없이 USER만 기록한다: %s', text => {
        const original = newMatchingConversation(now);
        original.draft = text;
        const requirements = ready();
        const next = sendMatchingMessage(original, text, requirements, now);
        expect(next.messages).toHaveLength(1);
        expect(next.messages[0]).toMatchObject({ role: 'user', text, requirements });
        expect(next.messages[0].result).toBeUndefined();
        expect(next.requirements).toEqual(requirements);
        expect(next.draft).toBe('');
        expect(original.messages).toEqual([]);
        expect(original.draft).toBe(text);
    });

    it('빈 조건이어도 저장하며 자연어 조건 추출·추가 질문을 실행하지 않는다', () => {
        const next = sendMatchingMessage(newMatchingConversation(now), 'React 웹과 백엔드 2명, 3개월 프로젝트', emptyChatRequirements, now);
        expect(next.requirements).toEqual(emptyChatRequirements);
        expect(next.messages.map(m => m.role)).toEqual(['user']);
        expect(next.title).toBe('React 웹과 백엔드 2명, 3개월 프로젝트');
    });

    it('분야·경력·기간만 필수이며 설명·기술·직군은 미선택을 허용한다', () => {
        expect(missingChatRequirements(emptyChatRequirements)).toEqual(['interests', 'career', 'duration']);
        const required = { ...emptyChatRequirements, interests: ['웹'], career: '경력 무관', duration: '3개월' };
        expect(missingChatRequirements(required)).toEqual([]);
        expect(sendMatchingMessage(newMatchingConversation(now), '조건 기록', required, now).requirements).toEqual(required);
        expect(missingChatRequirements({ ...required, interests: [] })).toEqual(['interests']);
        expect(missingChatRequirements({ ...required, career: '' })).toEqual(['career']);
        expect(missingChatRequirements({ ...required, duration: '' })).toEqual(['duration']);
    });

    it('후속 메시지를 입력 순서로 추가하고 선택한 조건 스냅샷을 보존한다', () => {
        const first = sendMatchingMessage(newMatchingConversation(now), '첫 메시지', ready(), now);
        const changed = { ...ready(), stacks: ['Java'] };
        const second = sendMatchingMessage(first, '두 번째 메시지', changed, new Date(now.getTime() + 1000));
        changed.stacks.push('Python');
        expect(second.messages.map(m => m.text)).toEqual(['첫 메시지', '두 번째 메시지']);
        expect(second.messages[0].requirements?.stacks).toEqual(['React', 'TypeScript']);
        expect(second.messages[1].requirements?.stacks).toEqual(['Java']);
        expect(second.title).toBe('첫 메시지');
    });

    it.each(['', '   ', 'x'.repeat(1501)])('잘못된 메시지는 초안 원본을 바꾸지 않는다', text => {
        const original = { ...newMatchingConversation(now), draft: text };
        const snapshot = structuredClone(original);
        expect(() => sendMatchingMessage(original, text, emptyChatRequirements, now)).toThrow('메시지');
        expect(original).toEqual(snapshot);
    });

    it('조건 입력 오류를 처리하고 기존 저장 검증을 보존한다', () => {
        expect(() => sendMatchingMessage(newMatchingConversation(now), '메시지', { ...ready(), stacks: [' '] }, now)).toThrow('선택 조건');
    });

    it('기술 직접 입력의 표기·중복·길이·개수 제한을 적용한다', () => {
        expect(addChatStack([], ' java ')).toEqual(['Java']);
        expect(addChatStack(['Java'], ' Vue ')).toEqual(['Java', 'Vue']);
        expect(() => addChatStack(['Java'], 'java')).toThrow('이미 추가');
        expect(() => addChatStack(['Vue'], ' Vue ')).toThrow('이미 추가');
        for (const value of ['', ' ', 'x'.repeat(51)]) expect(() => addChatStack([], value)).toThrow('1~50자');
        const full = Array.from({ length: 30 }, (_, i) => `기술${i}`);
        expect(() => addChatStack(full, 'Docker')).toThrow('최대 30개');
        expect(addChatStack(full.slice(0, 29), 'x'.repeat(50))).toHaveLength(30);
    });

    it('직접 입력한 기술을 메시지 저장과 재복원에서 보존하고 잘못된 값은 거절한다', () => {
        const requirements = { ...ready(), stacks: addChatStack(['Java'], 'Vue') };
        const conversation = sendMatchingMessage(newMatchingConversation(now), '직접 입력 기술', requirements, now);
        const restored = parseMatchingChat(JSON.parse(JSON.stringify({ conversations: { member: [conversation] }, active: { member: conversation.id } })));
        expect(restored.conversations.member[0].requirements.stacks).toEqual(['Java', 'Vue']);
        expect(restored.conversations.member[0].messages[0].requirements?.stacks).toEqual(['Java', 'Vue']);
        for (const stacks of [['x'.repeat(51)], Array.from({ length: 31 }, (_, i) => `기술${i}`)]) {
            expect(() => sendMatchingMessage(conversation, '잘못된 기술', { ...requirements, stacks }, now)).toThrow('선택 조건');
        }
    });
});

describe('버전 1 로컬 데이터 호환', () => {
    it('전송 전 입력문·선택 조건·메시지를 새로고침 후 복원한다', () => {
        const home = freshHomeState();
        const conversation = sendMatchingMessage(newMatchingConversation(now), '기록할 메시지', ready(), now);
        conversation.draft = '아직 전송하지 않은 입력';
        home.chat = { conversations: { guest: [conversation] }, active: { guest: conversation.id } };
        const restored = parseHomeState(JSON.stringify(home));
        expect(restored.chat).toEqual(home.chat);
        expect(restored.matching).toEqual(initialMatchingState);
    });

    it('이전 분야 선택을 관심 분야로 한 번만 옮기고 과거 메시지·직접 입력은 보존한다', () => {
        const conversation = sendMatchingMessage(newMatchingConversation(now), '기존 메시지', ready(), now);
        conversation.requirements = { ...ready(), fields: ['웹', '모바일', '해양 탐사'], interests: ['생산성', '웹'] };
        const previous = structuredClone(conversation);
        const restored = parseMatchingChat({ conversations: { member: [conversation] }, active: { member: conversation.id } });
        expect(restored.conversations.member[0].requirements).toMatchObject({ fields: ['해양 탐사'], interests: ['생산성', '웹', '모바일'] });
        expect(restored.conversations.member[0].messages).toEqual(previous.messages);
        expect(conversation).toEqual(previous);
        restored.conversations.member[0].requirements.interests = ['생산성'];
        expect(parseMatchingChat(restored).conversations.member[0].requirements.interests).toEqual(['생산성']);
    });

    it('draft 필드가 없는 이전 대화를 복원하고 손상 대화만 제거한다', () => {
        const valid = newMatchingConversation(now);
        const invalid = { ...valid, id: 'invalid', draft: 123 };
        const parsed = parseMatchingChat({ conversations: { guest: [valid, invalid] }, active: { guest: valid.id } });
        expect(parsed.conversations.guest).toEqual([valid]);
        expect(parsed.active.guest).toBe(valid.id);
    });

    it('이전 저장된 Mock 결과를 조회용으로 복원하며 삭제된 대화를 부활시키지 않는다', () => {
        const member = demoAccounts[0];
        const input = { description: 'React 일정 프론트엔드 웹 서비스를 만들어요', stacks: ['React'], interest: '생산성', role: '프론트엔드 개발자' };
        const old = { version: 1, input, matching: consumeMatching(initialMatchingState, member, input, developers, projects, now).state };
        const migrated = parseHomeState(JSON.stringify(old));
        expect(migrated.chat.conversations[member.id][0].messages[1].result).toBeDefined();
        migrated.chat = { conversations: {}, active: {} };
        expect(parseHomeState(JSON.stringify(migrated)).chat.conversations).toEqual({});
    });
});

// 기존 Mock 계산 유틸리티의 회귀 확인. 현재 메시지 저장 흐름에서는 호출하지 않는다.
describe('기존 Mock 유틸리티 보존', () => {
    it('기존 조건 추출과 필수 항목 검증을 보존한다', () => {
        const requirements = inferChatRequirements(emptyChatRequirements, 'React 웹과 모바일 일정 앱, 프론트엔드 2명과 백엔드 1명, 경력 무관, 3개월을 생각합니다.');
        expect(requirements.fields).toEqual(['웹', '모바일']);
        expect(requirements.roles).toEqual([{ name: '프론트엔드', count: 2 }, { name: '백엔드', count: 1 }]);
        expect(missingChatRequirements(requirements)).toEqual([]);
    });

    it('이전 저장 결과 fixture용 Mock 점수 계산을 보존한다', () => {
        const web = buildChatResult({ ...ready(), roles: [{ name: '프론트엔드', count: 2 }] }, developers, projects);
        const ai = buildChatResult({ ...ready(), fields: ['AI / 머신러닝'], interests: ['AI'], stacks: ['Python'], roles: [{ name: 'AI 엔지니어', count: 1 }] }, developers, projects);
        expect(web.developers[0].id).toBe('yuna');
        expect(ai.developers[0].id).toBe('seoyeon');
    });

    it('crypto.randomUUID가 없어도 로컬 대화·메시지 ID를 만든다', () => {
        vi.stubGlobal('crypto', undefined);
        try {
            const conversation = sendMatchingMessage(newMatchingConversation(now), '메시지', emptyChatRequirements, now);
            expect(conversation.id).toMatch(/^mock-/);
            expect(conversation.messages[0].id).not.toBe(conversation.id);
        } finally { vi.unstubAllGlobals(); }
    });
});
