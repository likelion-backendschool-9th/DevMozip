'use client';
import { useState } from 'react';
import { MessageSquare } from 'lucide-react';
import { useNavigation as useNavigate } from '@/lib/navigation';
import { useAuth } from '@/lib/state/authStore';
import { startChatWithMember } from '@/lib/state/directChatStore';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';
import { usePrototype } from '@/lib/state/prototypeStore';

/**
 * 1:1 대화 시작(WBS 11.5). 프로필 상단과 개발자 탐색 카드(6.7)가 함께 쓴다.
 * 실제 회원끼리의 대화라서 Mock 토글 OFF에서만 표시한다. 본인 프로필·카드에는 호출하는 쪽에서 넣지 않는다.
 */
export function StartChatButton({ slug }: { slug: string }) {
    const { enabled } = useMockVisibility();
    const { user } = useAuth();
    const { notify } = usePrototype();
    const navigate = useNavigate();
    const [pending, setPending] = useState(false);
    if (enabled) return null;
    return <button className="button" type="button" disabled={pending} onClick={async () => {
        setPending(true);
        const result = await startChatWithMember(slug, !!user, navigate);
        setPending(false);
        if (!result.ok) notify(result.error || '대화를 시작하지 못했어요.');
    }}><MessageSquare size={14} />메시지 보내기</button>;
}
