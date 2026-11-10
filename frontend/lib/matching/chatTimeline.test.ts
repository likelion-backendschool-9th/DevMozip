import { describe, expect, it } from 'vitest';
import { chatTimeline } from './chatTimeline';
import type { AiRecommendation } from '@/types/aiRecommendation';
import type { MatchingMessage } from '@/types/matchingChat';

const messages: MatchingMessage[] = [
    { id: 'first', serverId: 8, role: 'user', text: '첫 설명', createdAt: '2026-11-08T18:00:00' },
    { id: 'second', serverId: 9, role: 'user', text: '추가 설명', createdAt: '2026-11-08T18:02:00' },
    { id: 'draft', role: 'user', text: '미전송 설명', createdAt: '2026-11-08T09:04:00Z' },
];
const result = (requestId: number, messageId: number | null, createdAt: string): AiRecommendation => ({
    requestId, messageId, createdAt, conversationId: 7, status: 'SUCCESS', completedAt: createdAt,
    conditions: { interests: ['웹'], career: '경력 무관', durationMonths: 3 }, developers: [], projects: [],
});
const names = (items: ReturnType<typeof chatTimeline>) => items.map(v => v.kind === 'message' ? v.message.id : v.result.requestId);

describe('대화와 추천 결과의 시간순 복원', () => {
    it('분석 답변을 먼저 표시한 뒤 연결된 추천을 배치하며 재조회해도 유지한다', () => {
        const thread: MatchingMessage[] = [messages[0],
            { id: 'analysis', serverId: 10, role: 'assistant', text: '채팅 경험 선호를 저장했어요', createdAt: '2026-11-08T18:00:30' },
            messages[1], messages[2]];
        const recommendation = result(11, 8, '2026-11-08T18:01:00');
        expect(names(chatTimeline(thread, [recommendation]))).toEqual(['first', 'analysis', 11, 'second', 'draft']);
        expect(names(chatTimeline(JSON.parse(JSON.stringify(thread)), [recommendation]))).toEqual(['first', 'analysis', 11, 'second', 'draft']);
    });
    it('추천 이후 생성된 답변을 과거 추천 앞으로 옮기지 않는다', () => {
        const later: MatchingMessage = { id: 'later-analysis', serverId: 10, role: 'assistant', text: '나중 답변', createdAt: '2026-11-08T18:02:00' };
        expect(names(chatTimeline([messages[0], later], [result(11, 8, '2026-11-08T18:01:00')]))).toEqual(['first', 11, 'later-analysis']);
    });
    it('다음 USER의 분석 답변을 이전 추천 앞으로 옮기지 않는다', () => {
        const later: MatchingMessage = { id: 'next-analysis', serverId: 10, role: 'assistant', text: '추가 설명 답변', createdAt: '2026-11-08T18:02:30' };
        expect(names(chatTimeline([messages[0], messages[1], later], [result(11, 8, '2026-11-08T18:03:00')]))).toEqual(['first', 11, 'second', 'next-analysis']);
    });
    it('조회 순서와 관계없이 각 결과를 반영한 메시지 뒤에 두고 중복을 제거한다', () => {
        const first = result(11, 8, '2026-11-08T18:01:00');
        const second = result(12, 9, '2026-11-08T18:03:00');
        expect(names(chatTimeline(messages, [second, first, first]))).toEqual(['first', 11, 'second', 12, 'draft']);
        expect(names(chatTimeline(messages, JSON.parse(JSON.stringify([first, second]))))).toEqual(['first', 11, 'second', 12, 'draft']);
    });
    it('과거 NULL 연결은 서버 저장 시각으로 배치하고 UTC 로컬 메시지는 뒤에 둔다', () => {
        expect(names(chatTimeline(messages, [result(11, null, '2026-11-08T18:01:00')]))).toEqual(['first', 11, 'second', 'draft']);
    });
    it('같은 메시지에 여러 번 추천해도 각각 유지하며 빈 대화 추천도 복원한다', () => {
        const a = result(11, 8, '2026-11-08T18:01:00');
        const b = result(12, 8, '2026-11-08T18:01:00');
        expect(names(chatTimeline(messages, [b, a]))).toEqual(['first', 11, 12, 'second', 'draft']);
        expect(names(chatTimeline([], [result(13, null, '2026-11-08T18:01:00')]))).toEqual([13]);
    });
});
