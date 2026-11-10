'use client';
import { MessengerLayout, messengerStyles as styles } from '@/app/messages/_components/MessengerLayout';
import memberStyles from './page.module.css';
import { ConversationSidebar } from '@/app/messages/_components/ConversationSidebar';
import { Siren } from 'lucide-react';
import { UserReportDialog } from '@/app/messages/_components/UserReportDialog';
import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { KickVoteDialog, KickVotePanel } from '@/app/messages/projects/[projectId]/_components/KickVotePanel';
import { ProjectDateSimulator } from '@/components/projects/ProjectDateSimulator';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useAuth } from '@/lib/state/authStore';
import { useProfiles } from '@/lib/state/profileStore';
import { useProjects } from '@/lib/state/projectStore';
import { useMessaging } from '@/lib/state/messagingStore';
import { roomAccess } from '@/lib/messaging/messagingService';
import { ChatWindow } from '@/app/messages/_components/ChatWindow';
import { ProfileAvatar } from '@/components/profile/ProfileAvatar';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';
import { ServerProjectChat } from './_components/ServerProjectChat';

/** Mock 토글 OFF는 서버 프로젝트 채팅, ON은 기존 Mock 단체 채팅을 그린다(D5). */
export default function ProjectChatPage() {
    const { projectId = '' } = useParams<{ projectId: string }>();
    const { enabled } = useMockVisibility();
    return enabled ? <MockProjectChat projectId={projectId} /> : <ServerProjectChat projectId={projectId} />;
}

function MockProjectChat({ projectId }: { projectId: string }) {
    const [reportTarget, setReportTarget] = useState<string | null>(null);
    const [showMembers, setShowMembers] = useState(false);
    const [target, setTarget] = useState<string | null>(null);
    const { user } = useAuth();
    const { state: profiles } = useProfiles();
    const { state: projects, projects: items, now } = useProjects();
    const { state, act } = useMessaging();
    const project = items.find(p => p.id === projectId);
    const access = roomAccess(projects, projectId, user?.id || null, now);
    const room = state.rooms[projectId];
    const unread =
        room?.messages.filter(m => m.senderId !== user?.id && !m.readBy.includes(user?.id || '')).length || 0;
    useEffect(() => {
        if (access.canRead && room && unread) act({ type: 'read-group', projectId });
    }, [user?.id, projectId, access.canRead, unread, !!room]);
    if (!access.canRead)
        return (
            <div className={[styles.scope, 'container planned'].join(' ')}>
                <section className={[styles.scope, 'planned-card'].join(' ')}>
                    <h1>채팅방에 입장할 수 없어요.</h1>
                    <p>{access.reason}</p>
                    <Link className={[styles.scope, 'button'].join(' ')} href={user ? '/messages/chats' : '/login'}>
                        {user ? '메신저로' : '로그인'}
                    </Link>
                </section>
            </div>
        );
    const members = projects.chatMembers[projectId] || [];
    return (
        <div className={[styles.scope, 'container messenger-page project-chat-page'].join(' ')}>
            <Link className={[styles.scope, 'text-link'].join(' ')} href="/messages/chats">
                ← 메신저로
            </Link>
            <header className={[styles.scope, 'section-heading'].join(' ')}>
                <div>
                    <div className={[styles.scope, 'eyebrow'].join(' ')}>PROJECT TEAM CHAT</div>
                    <h1>{project?.title}</h1>
                    <p>{access.reason || '프로젝트 참여 상태와 연결된 Mock 단체 채팅입니다.'}</p>
                </div>
            </header>
            <details className={[styles.scope, 'kick-date-details'].join(' ')}>
                <summary>투표 기한 시뮬레이션</summary>
                <ProjectDateSimulator />
            </details>
            <MessengerLayout>
                <ConversationSidebar key={user?.id} kind="project" activeId={projectId} />
                <section className={[styles.scope, 'conversation-pane'].join(' ')}>
                    <header className={[styles.scope, 'conversation-header'].join(' ')}>
                        <div>
                            <strong>{project?.title}</strong>
                            <span>{project?.status || '모집 중'}</span>
                        </div>
                        <Button variant="secondary" onClick={() => setShowMembers(true)}>
                            멤버 {members.length}명
                        </Button>
                    </header>
                    <ChatWindow
                        channelKey={projectId}
                        messages={room?.messages || []}
                        members={members}
                        disabled={!access.canWrite}
                        historyContent={
                            <KickVotePanel projectId={projectId} members={members} canWrite={access.canWrite} />
                        }
                        historyVersion={projects.kickVotes.filter(v => v.projectId === projectId).length}
                        onSend={body => act({ type: 'send-group', projectId, body })}
                    />
                </section>
            </MessengerLayout>
            {showMembers && (
                <Modal title="채팅방 멤버" onClose={() => setShowMembers(false)}>
                    <aside className={[memberStyles.scope, memberStyles.memberList, 'chat-member-panel'].join(' ')}>
                        <p className={memberStyles.memberSummary}>총 {members.length}명이 참여하고 있어요.</p>
                        {members.map(id => {
                            const p = profiles.profiles[id];
                            return (
                                <div className={memberStyles.memberRow} key={id}>
                                    <Link href={`/users/${id}`}>
                                        <ProfileAvatar
                                            name={p?.nickname || '사용자'}
                                            color={p?.color || 'lavender'}
                                            small
                                        />
                                        <span>
                                            <strong>{p?.nickname || id}</strong>
                                            <small>{id === project?.hostId ? '주최자' : '팀원'}</small>
                                        </span>
                                    </Link>
                                    {id !== user?.id && (
                                        <button
                                            className={[memberStyles.scope, 'kick-request-button'].join(' ')}
                                            disabled={
                                                !access.canWrite ||
                                                projects.kickVotes.some(
                                                    v => v.projectId === projectId && v.status === 'active',
                                                )
                                            }
                                            onClick={() => {
                                                setShowMembers(false);
                                                setTarget(id);
                                            }}
                                            aria-label={`${p?.nickname || id} 강퇴 투표 요청`}
                                        >
                                            강퇴 투표 요청
                                        </button>
                                    )}
                                    {id !== user?.id && (
                                        <button
                                            type="button"
                                            className={memberStyles.reportIcon}
                                            aria-label={`${p?.nickname || id} 신고`}
                                            title="사용자 신고"
                                            onClick={() => {
                                                setShowMembers(false);
                                                setReportTarget(id);
                                            }}
                                        >
                                            <Siren size={16} aria-hidden="true" />
                                        </button>
                                    )}
                                    {project?.status === '프로젝트 종료' && id !== user?.id && (
                                        <Link
                                            className={[memberStyles.scope, 'chat-review-link'].join(' ')}
                                            href={`/my-projects/${projectId}/manage`}
                                        >
                                            협업 리뷰 →
                                        </Link>
                                    )}
                                </div>
                            );
                        })}
                        <p className={[memberStyles.scope, 'auth-hint'].join(' ')}>
                            지원 승인 시 초대되고 취소 시 퇴장합니다. 실제 WebSocket은 사용하지 않습니다.
                        </p>
                    </aside>
                </Modal>
            )}
            {reportTarget && (
                <UserReportDialog
                    key={`${user?.id}:${reportTarget}`}
                    targetId={reportTarget}
                    onClose={() => setReportTarget(null)}
                />
            )}{' '}
            {target && (
                <KickVoteDialog
                    key={`${projectId}:${target}:${user?.id}`}
                    projectId={projectId}
                    targetId={target}
                    members={members}
                    onClose={() => setTarget(null)}
                />
            )}
        </div>
    );
}
