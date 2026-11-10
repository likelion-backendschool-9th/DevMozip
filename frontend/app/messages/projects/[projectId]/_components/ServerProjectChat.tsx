'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { MessageSquare, Siren, UserRoundMinus } from 'lucide-react';
import { MessengerLayout, messengerStyles as styles } from '@/app/messages/_components/MessengerLayout';
import { ConversationSidebar } from '@/app/messages/_components/ConversationSidebar';
import { ChatWindow } from '@/app/messages/_components/ChatWindow';
import { UserReportDialog } from '@/app/messages/_components/UserReportDialog';
import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { ProfileAvatar } from '@/components/profile/ProfileAvatar';
import { validChatId } from '@/lib/messaging/directChatApi';
import { profileHref } from '@/lib/messaging/directChatView';
import { projectChatStatusLabel } from '@/lib/messaging/projectChatApi';
import { currentMemberKeys, projectMemberKey, projectMemberName, projectTypingText, toProjectChatMessages } from '@/lib/messaging/projectChatView';
import { profileImageUrl } from '@/lib/profiles/profileDetail';
import { useAuth } from '@/lib/state/authStore';
import {submitProjectMemberReport} from '@/lib/projects/projectMemberReport';
import {
    loadOlderProjectMessages, notifyProjectChatTyping, openProjectChat, sendOpenProjectMessage, setProjectChatAtBottom, useProjectChats,
} from '@/lib/state/projectChatStore';
import type { ProjectChatMember } from '@/types/messaging';
import {ApiKickVotePanel,ApiKickVoteDialog} from './ApiKickVotePanel';
import memberStyles from '../page.module.css';

function Refused({ title, reason, href, label }: { title: string; reason: string; href: string; label: string }) {
    return (
        <div className={[styles.scope, 'container planned'].join(' ')}>
            <section className={[styles.scope, 'planned-card'].join(' ')}>
                <h1>{title}</h1>
                <p>{reason}</p>
                <Link className={[styles.scope, 'button'].join(' ')} href={href}>{label}</Link>
            </section>
        </div>
    );
}

/**
 * Mock 토글 OFF의 프로젝트 단톡방(7.18~7.23). 멤버·메시지·읽음·입력 중 표시를 서버 데이터로 그린다.
 * 강퇴 투표는 실제 프로젝트 참여 상태와 채팅 멤버에 함께 반영한다(#49). 채팅방 삭제는 프로젝트 관리 화면이 맡는다.
 */
export function ServerProjectChat({ projectId }: { projectId: string }) {
    const { user } = useAuth();
    const { open, sessionExpired } = useProjectChats();
    const [kickTarget,setKickTarget]=useState<ProjectChatMember|null>(null);
    const [voteVersion,setVoteVersion]=useState(0);
    const [showMembers, setShowMembers] = useState(false);
    const [reportTarget, setReportTarget] = useState<ProjectChatMember | null>(null);
    const id = validChatId(projectId) ? Number(projectId) : null;

    useEffect(() => {
        if (id !== null && user) return openProjectChat(id);
    }, [id, !!user]);

    if (!user || sessionExpired) {
        const next = encodeURIComponent(`/messages/projects/${projectId}`);
        return <Refused title={user ? '로그인이 만료됐어요. 다시 로그인해 주세요.' : '로그인하고 팀 채팅에 참여하세요.'}
            reason="프로젝트 채팅은 승인된 팀원만 볼 수 있어요." href={`/login?next=${next}`} label="로그인" />;
    }
    const chat = id !== null && open?.projectId === id ? open : null;
    if (id === null || chat?.status === 'missing') {
        return <Refused title="채팅방에 입장할 수 없어요."
            reason="승인된 팀원만 들어올 수 있어요. 모집 시작 전이거나, 삭제됐거나, 참여가 끝났거나 강퇴된 채팅방일 수 있어요."
            href="/messages/chats" label="메신저로" />;
    }

    const ready = chat?.status === 'ready' ? chat : null;
    const current = ready ? ready.members.filter(member => member.leftAt === null) : [];
    const memberByKey = new Map((ready?.members ?? []).map(member => [member.me ? user.id : projectMemberKey(member.id), member]));
    const completed = ready?.chatStatus === 'COMPLETED';

    return (
        <div className={[styles.scope, 'container messenger-page project-chat-page'].join(' ')}>
            <Link className={[styles.scope, 'text-link'].join(' ')} href="/messages/chats">← 메신저로</Link>
            <header className={[styles.scope, 'section-heading'].join(' ')}>
                <div>
                    <div className={[styles.scope, 'eyebrow'].join(' ')}>PROJECT TEAM CHAT</div>
                    <h1>{ready?.title || '프로젝트 채팅'}</h1>
                    <p>{completed ? '종료된 프로젝트라 대화는 읽기만 할 수 있어요.' : '승인된 팀원끼리 나누는 대화예요.'}</p>
                </div>
            </header>
            <MessengerLayout>
                <ConversationSidebar key={user.id} kind="project" activeId={projectId} />
                <section className={[styles.scope, 'conversation-pane'].join(' ')}>
                    {ready ? (
                        <>
                            <header className={[styles.scope, 'conversation-header'].join(' ')}>
                                <div>
                                    <strong>{ready.title}</strong>
                                    <span>{ready.chatStatus ? projectChatStatusLabel[ready.chatStatus] : ''}</span>
                                </div>
                                <Button variant="secondary" onClick={() => setShowMembers(true)}>멤버 {current.length}명</Button>
                            </header>
                            <ApiKickVotePanel projectId={id} version={voteVersion}>{(items,error)=><ChatWindow
                                historyItems={items}
                                historyContent={error?<p role="alert">{error}</p>:undefined}
                                historyVersion={voteVersion}
                                channelKey={projectId}
                                messages={toProjectChatMessages(ready.members, ready.messages, ready.nextBefore !== null, user.id)}
                                members={currentMemberKeys(ready.members, user.id)}
                                disabled={!ready.canSend}
                                onSend={sendOpenProjectMessage}
                                author={key => {
                                    const member = memberByKey.get(key);
                                    return { name: member ? projectMemberName(member) : '팀원', href: profileHref(member?.tag ?? null) };
                                }}
                                footerText="2,000자까지 보낼 수 있어요."
                                history={{
                                    hasOlder: ready.nextBefore !== null,
                                    loadingOlder: ready.loadingOlder,
                                    onLoadOlder: () => void loadOlderProjectMessages(),
                                    onBottomChange: setProjectChatAtBottom,
                                }}
                                typing={{ text: projectTypingText(ready.typing), onTyping: () => notifyProjectChatTyping() }}
                            />}</ApiKickVotePanel>
                        </>
                    ) : (
                        <div className={[styles.scope, 'chat-empty'].join(' ')}>
                            <MessageSquare size={35} />
                            <h2>{chat?.status === 'error' ? '프로젝트 채팅을 불러오지 못했어요. 잠시 후 다시 시도합니다.' : '프로젝트 채팅을 불러오고 있어요…'}</h2>
                        </div>
                    )}
                </section>
            </MessengerLayout>
            {showMembers && ready && (
                <Modal title="채팅방 멤버" onClose={() => setShowMembers(false)}>
                    <aside className={[memberStyles.scope, memberStyles.memberList, 'chat-member-panel'].join(' ')}>
                        <p className={memberStyles.memberSummary}>총 {current.length}명이 참여하고 있어요.</p>
                        {current.map(member => {
                            const name = projectMemberName(member);
                            const href = profileHref(member.tag);
                            const label = <>
                                <ProfileAvatar name={name} color="lavender" small image={profileImageUrl(member.profileImage)} />
                                <span>
                                    <strong>{name}{member.me ? ' (나)' : ''}</strong>
                                    <small>{member.role === 'HOST' ? '주최자' : '팀원'}</small>
                                </span>
                            </>;
                            return (
                                <div className={memberStyles.memberRow} key={member.id}>
                                    {href ? <Link className={memberStyles.memberIdentity} href={href}>{label}</Link> : <div className={memberStyles.memberIdentity}>{label}</div>}
                                    <div className={memberStyles.memberActions}>
                                    {!member.me && !completed && ready.canSend && member.role!=='HOST' && member.projectMemberId && <button type="button" className={memberStyles.kickIcon} aria-label={`${name} 강퇴 투표 요청`} title="강퇴 투표 요청" onClick={()=>{setShowMembers(false);setKickTarget(member);}}><UserRoundMinus size={17}/></button>}
                                    {!member.me && member.projectMemberId && (
                                        <button type="button" className={memberStyles.reportIcon} aria-label={`${name} 신고`} title="사용자 신고"
                                            onClick={() => { setShowMembers(false); setReportTarget(member); }}>
                                            <Siren size={16} aria-hidden="true" />
                                        </button>
                                    )}
                                    </div>
                                </div>
                            );
                        })}
                        <p className={[memberStyles.scope, 'auth-hint'].join(' ')}>
                            지원이 승인된 팀원은 채팅방을 열 때 들어오고, 참여를 취소하면 나가요.
                        </p>
                    </aside>
                </Modal>
            )}
            {kickTarget?.projectMemberId && <ApiKickVoteDialog projectId={id} targetMemberId={kickTarget.projectMemberId} name={projectMemberName(kickTarget)} onClose={()=>setKickTarget(null)} onCreated={()=>setVoteVersion(v=>v+1)}/>}
            {reportTarget?.projectMemberId && (
                <UserReportDialog
                    key={reportTarget.id}
                    server={{projectContext:true, name: projectMemberName(reportTarget), onSubmit:(reason,detail)=>submitProjectMemberReport(id,reportTarget.projectMemberId!,reason,detail)}}
                    onClose={() => setReportTarget(null)}
                />
            )}
        </div>
    );
}
