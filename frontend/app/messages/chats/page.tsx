'use client';
import { useState } from 'react';
import Link from 'next/link';
import { Flag, MessageSquare } from 'lucide-react';
import { messengerStyles as styles } from '@/app/messages/_components/MessengerLayout';
import { ChatWindow } from '@/app/messages/_components/ChatWindow';
import { UserReportDialog } from '@/app/messages/_components/UserReportDialog';
import { ProfileAvatar } from '@/components/profile/ProfileAvatar';
import { Button } from '@/components/common/Button';
import { useConversationPane, type ConversationPane } from './_hooks/useConversationPane';

function PeerSummary({ peer }: { peer: NonNullable<ConversationPane['peer']> }) {
    const [imageFailed, setImageFailed] = useState(false);
    return <>
        <ProfileAvatar name={peer.name} color={peer.color} small image={imageFailed ? undefined : peer.image} onImageError={() => setImageFailed(true)} />
        <div>
            <strong>{peer.name}</strong>
            <span>{peer.subtitle}</span>
        </div>
    </>;
}

// /messages/chats와 /messages/chats/{대화 ID}가 함께 쓰는 대화 영역. 사이드바는 layout.tsx에 있다.
export default function MessagesPage() {
    const pane = useConversationPane();
    const [reporting, setReporting] = useState(false);
    const { conversation, peer, report } = pane;

    return (
        <>
            <section className={[styles.scope, 'conversation-pane'].join(' ')}>
                {conversation && peer ? (
                    <>
                        <header className={[styles.scope, 'conversation-header'].join(' ')}>
                            {peer.href ? (
                                <Link href={peer.href}><PeerSummary peer={peer} /></Link>
                            ) : (
                                <div className={[styles.scope, 'conversation-peer'].join(' ')}><PeerSummary peer={peer} /></div>
                            )}
                            {report && (
                                <Button variant="secondary" onClick={() => setReporting(true)}>
                                    <Flag size={13} />
                                    사용자 신고
                                </Button>
                            )}
                        </header>
                        <ChatWindow
                            channelKey={conversation.id}
                            messages={conversation.messages}
                            members={conversation.members}
                            disabled={!pane.canSend}
                            onSend={pane.send}
                            author={pane.author}
                            footerText={pane.footerText}
                            history={pane.history}
                            typing={pane.typing}
                        />
                    </>
                ) : (
                    <div className={[styles.scope, 'chat-empty'].join(' ')}>
                        <MessageSquare size={35} />
                        <h2>{pane.emptyTitle}</h2>
                        <p>참여 중인 대화를 선택하거나 개발자 프로필에서 시작해 보세요.</p>
                    </div>
                )}
            </section>
            {reporting && report && <UserReportDialog targetId={report.targetId} server={report.server} onClose={() => setReporting(false)} />}
        </>
    );
}
