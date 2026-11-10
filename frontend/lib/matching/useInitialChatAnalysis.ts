import { useEffect, useRef } from 'react';
import type { MatchingConversation } from '@/types/matchingChat';

export function useInitialChatAnalysis(conversation: MatchingConversation | null, ready: boolean, analyze: (id: string) => Promise<void>) {
    const attempts = useRef(new Set<string>());
    useEffect(() => {
        const firstMessage = conversation?.messages[0];
        if (!ready || !conversation || conversation.serverId || conversation.pendingAnalysisKey
            || conversation.messages.length !== 1 || firstMessage?.role !== 'user' || firstMessage.serverId
            || attempts.current.has(conversation.id)) return;
        // 메인 제출·로그인 복원으로 저장된 첫 메시지도 전송 버튼과 같은 분석 흐름으로 처리한다.
        attempts.current.add(conversation.id);
        void analyze(conversation.id);
    }, [conversation, ready, analyze]);
}
