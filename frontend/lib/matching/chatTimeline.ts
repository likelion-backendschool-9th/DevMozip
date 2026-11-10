import type { AiRecommendation } from '@/types/aiRecommendation';
import type { MatchingMessage } from '@/types/matchingChat';

type Event = { kind: 'message'; message: MatchingMessage } | { kind: 'recommendation'; result: AiRecommendation };

export function chatTimeline(messages: MatchingMessage[], results: AiRecommendation[]): Event[] {
    const events = messages.map((message, index) => ({ position: index * 2, event: { kind: 'message', message } as Event }));
    const unique = [...new Map(results.map(result => [result.requestId, result])).values()]
        .sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.requestId - b.requestId);
    for (const result of unique) {
        let index = result.messageId == null ? -1 : messages.findIndex(m => m.serverId === result.messageId);
        // 과거 NULL 연결 결과는 같은 서버 시계의 저장 메시지와만 비교한다. 로컬 미전송 메시지는 항상 뒤에 둔다.
        if (index < 0) messages.forEach((m, i) => {
            if (m.serverId && Date.parse(m.createdAt) <= Date.parse(result.createdAt)) index = i;
        });
        events.push({ position: index * 2 + 1, event: { kind: 'recommendation', result } });
    }
    return events.sort((a, b) => a.position - b.position).map(v => v.event);
}
