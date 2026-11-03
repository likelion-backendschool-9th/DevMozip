'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Flag, MessageSquare } from 'lucide-react';
import { messengerStyles as styles } from '@/app/messages/_components/MessengerLayout';
import { ChatWindow } from '@/app/messages/_components/ChatWindow';
import { UserReportDialog } from '@/app/messages/_components/UserReportDialog';
import { useNavigation as useNavigate } from '@/lib/navigation';
import { useAuth } from '@/lib/state/authStore';
import { useMessaging } from '@/lib/state/messagingStore';
import { useProfiles } from '@/lib/state/profileStore';
import { ProfileAvatar } from '@/components/profile/ProfileAvatar';
import { Button } from '@/components/common/Button';
import { useActiveConversation } from './_hooks/useActiveConversation';

// /messages/chats와 /messages/chats/{대화 ID}가 함께 쓰는 대화 영역. 사이드바는 layout.tsx에 있다.
export default function MessagesPage() {
    const { user } = useAuth();
    const { state: profiles } = useProfiles();
    const { state, act } = useMessaging();
    const navigate = useNavigate();
    const { active, requested, selected } = useActiveConversation();
    const [report, setReport] = useState(false);
    const peerId = active?.participants.find(id => id !== user?.id);
    const peer = peerId ? profiles.profiles[peerId] : null;
    const unread =
        active?.messages.filter(m => m.senderId !== user?.id && !m.readBy.includes(user?.id || '')).length || 0;

    useEffect(() => {
        if (active && unread && state.conversations.some(c => c.id === active!.id))
            act({ type: 'read-direct', conversationId: active.id });
    }, [active?.id, user?.id, unread]);

    return (
        <>
            <section className={[styles.scope, 'conversation-pane'].join(' ')}>
                {active && peerId && peer ? (
                    <>
                        <header className={[styles.scope, 'conversation-header'].join(' ')}>
                            <Link href={`/users/${peerId}`}>
                                <ProfileAvatar name={peer.nickname} color={peer.color} small />
                                <div>
                                    <strong>{peer.nickname}</strong>
                                    <span>
                                        {peer.job} · {peer.available ? '협업 가능' : '협업 쉬는 중'}
                                    </span>
                                </div>
                            </Link>
                            <Button variant="secondary" onClick={() => setReport(true)}>
                                <Flag size={13} />
                                사용자 신고
                            </Button>
                        </header>
                        <ChatWindow
                            channelKey={active.id}
                            messages={active.messages}
                            members={active.participants}
                            onSend={body => {
                                const result = act({ type: 'send-direct', targetId: peerId, body, kind: 'text' });
                                if (result.ok && result.conversationId)
                                    navigate(`/messages/chats/${encodeURIComponent(result.conversationId)}`, {
                                        replace: true,
                                    });
                                return result;
                            }}
                        />
                    </>
                ) : (
                    <div className={[styles.scope, 'chat-empty'].join(' ')}>
                        <MessageSquare size={35} />
                        <h2>{requested || selected ? '대화를 열 수 없습니다.' : '대화를 선택해 주세요.'}</h2>
                        <p>참여 중인 대화를 선택하거나 개발자 프로필에서 시작해 보세요.</p>
                    </div>
                )}
            </section>
            {report && peerId && <UserReportDialog targetId={peerId} onClose={() => setReport(false)} />}
        </>
    );
}
