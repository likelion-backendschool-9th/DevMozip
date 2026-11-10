'use client';
import { ApiProjectDetail } from '@/app/projects/_components/api/ApiProjectDetail';
import styles from './page.module.css';
import { useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { ArrowLeft, CalendarDays, MessageSquare, Users } from 'lucide-react';
import { useProjects } from '@/lib/state/projectStore';
import { useProfiles } from '@/lib/state/profileStore';
import { useAuth } from '@/lib/state/authStore';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';
import { applicationFor, applicationRestriction, projectStatus } from '@/lib/projects/projectService';
import { Card } from '@/components/common/Card';
import { Button } from '@/components/common/Button';
import { ApplicationModal, CancelApplicationModal } from '@/app/projects/[projectId]/_components/ApplicationModal';
import { ProjectActions } from '@/app/projects/[projectId]/_components/ProjectActions';
import { ProfileAvatar } from '@/components/profile/ProfileAvatar';
function ProjectDetail({ projectId }: { projectId: string }) {
    const { projects, state, now } = useProjects();
    const { state: profiles } = useProfiles();
    const { user } = useAuth();
    const { enabled: mockEnabled } = useMockVisibility();
    const [modal, setModal] = useState<'apply' | 'cancel' | null>(null);
    const p = projects.find(p => p.id === projectId);
    if (!p)
        return (
            <div className={[styles.scope, 'container planned'].join(' ')}>
                <section className={[styles.scope, 'planned-card'].join(' ')}>
                    <h1>프로젝트를 찾을 수 없어요.</h1>
                    <p>주소가 잘못되었거나 초기화된 Mock 공고입니다.</p>
                    <Link className={[styles.scope, 'button'].join(' ')} href="/projects">
                        모집 목록으로
                    </Link>
                </section>
            </div>
        );
    const host = profiles.profiles[p.hostId];
    const application = applicationFor(state, p.id, user?.id);
    const restriction = applicationRestriction(p, user, now, state);
    const owner = p.hostId === user?.id;
    return (
        <div className={[styles.scope, 'container project-detail-page'].join(' ')}>
            <Link href="/projects" className={[styles.scope, 'text-link'].join(' ')}>
                <ArrowLeft size={14} />
                프로젝트 모집
            </Link>
            <header className={[styles.scope,styles.heading,'project-detail-heading'].join(' ')}>
                <div className={[styles.scope, 'project-detail-labels'].join(' ')}>
                    <span className={[styles.scope, 'outline-badge'].join(' ')}>{p.field}</span>
                    <span className={[styles.scope, 'availability-badge'].join(' ')}>{projectStatus(p, now)}</span>
                </div>
                <h1>{p.title}</h1>
                <p>{p.summary}</p>
                <div className={styles.headerActions}><ProjectActions project={p} /></div>
            </header>
            <div className={[styles.scope, 'project-detail-layout'].join(' ')}>
                <div className={[styles.scope, 'project-detail-main'].join(' ')}>
                    <Card className={[styles.scope, 'profile-section'].join(' ')}>
                        <h2>프로젝트 소개</h2>
                        <p className={[styles.scope, 'preserve-text'].join(' ')}>{p.description}</p>
                        <h2 className={[styles.scope, 'sub-heading'].join(' ')}>함께 이루고 싶은 목표</h2>
                        <p className={[styles.scope, 'preserve-text'].join(' ')}>{p.goal}</p>
                    </Card>
                    <Card className={[styles.scope, 'profile-section'].join(' ')}>
                        <h2>기술 스택</h2>
                        <div className={[styles.scope, 'profile-tags'].join(' ')}>
                            {p.stacks.map(s => (
                                <span key={s}>{s}</span>
                            ))}
                        </div>
                        <h2 className={[styles.scope, 'sub-heading'].join(' ')}>모집 직군과 인원</h2>
                        <div className={[styles.scope, 'role-requirements'].join(' ')}>
                            <div className={[styles.scope, 'role-row role-table-heading'].join(' ')}>
                                <span>모집 직군</span>
                                <span>참여 / 정원</span>
                                <span>최소 경력</span>
                            </div>
                            {p.slots.map(s => (
                                <div className={[styles.scope, 'role-row'].join(' ')} key={s.role}>
                                    <strong>{s.role}</strong>
                                    <span>
                                        {s.filled} / {s.count}명
                                    </span>
                                    <span>{s.minYears ? `${s.minYears}년 이상` : '경력 무관'}</span>
                                </div>
                            ))}
                        </div>
                        <p className={[styles.scope, 'auth-hint'].join(' ')}>
                            지원서는 참여 인원에 포함되지 않으며 승인되면 참여 인원에 반영됩니다.
                        </p>
                    </Card>
                    <Card className={[styles.scope, 'profile-section'].join(' ')}>
                        <h2>진행 일정</h2>
                        <dl className={[styles.scope, 'project-schedule'].join(' ')}>
                            <div>
                                <dt>개발 기간</dt>
                                <dd>
                                    {p.developmentStart} — {p.developmentEnd}
                                </dd>
                            </div>
                            <div>
                                <dt>모집 기간</dt>
                                <dd>
                                    {p.recruitmentStart} — {p.deadline} (한국 시각)
                                </dd>
                            </div>
                        </dl>
                    </Card>
                </div>
                <aside className={[styles.scope, 'project-detail-sidebar'].join(' ')}>
                    <Card className={[styles.scope, 'profile-section apply-summary'].join(' ')}>
                        <span className={[styles.scope, 'eyebrow'].join(' ')}>YOUR NEXT CHALLENGE</span>
                        <h2>함께 만들어볼까요?</h2>
                        <p>
                            <Users size={14} />
                            현재 {p.members} / {p.capacity}명
                        </p>
                        <p>
                            <CalendarDays size={14} />
                            모집 마감 {p.deadline}
                        </p>
                        {application && (
                            <div className={[styles.scope, 'application-status'].join(' ')} role="status">
                                <strong>{application.status}</strong>
                                {['지원 완료', '지원 승인'].includes(application.status) ? (
                                    <>
                                        <p>
                                            {application.role} ·{' '}
                                            {application.status === '지원 승인' ? '팀원 등록 완료' : '주최자 검토 대기'}
                                        </p>
                                        <small>제출 포트폴리오: {application.portfolioTitle}</small>
                                        <p className={[styles.scope, 'preserve-text'].join(' ')}>
                                            {application.message}
                                        </p>
                                    </>
                                ) : (
                                    <p>
                                        {application.status === '지원 거절'
                                            ? '지원이 거절되었습니다. 모집 기간 내 재지원할 수 있습니다.'
                                            : '모집 기간 내에 다시 지원할 수 있습니다.'}
                                    </p>
                                )}
                            </div>
                        )}
                        {application && ['지원 완료', '지원 승인'].includes(application.status) ? (
                            <Button
                                variant="secondary"
                                disabled={p.status === '프로젝트 종료'}
                                onClick={() => setModal('cancel')}
                            >
                                {p.status === '프로젝트 종료' ? '프로젝트 종료 · 참여 기록 유지' : '지원 취소'}
                            </Button>
                        ) : !user ? (
                            <Link className={[styles.scope, 'button'].join(' ')} href="/login">
                                로그인하고 지원하기
                            </Link>
                        ) : owner ? (
                            <Link className={[styles.scope, 'button'].join(' ')} href={`/projects/${p.id}/manage`}>
                                주최자 관리
                            </Link>
                        ) : (
                            <>
                                <Button disabled={!!restriction} onClick={() => setModal('apply')}>
                                    {application && ['지원 취소', '지원 거절'].includes(application.status)
                                        ? '다시 지원하기'
                                        : '프로젝트 지원하기'}
                                </Button>
                                {restriction && (
                                    <p className={[styles.scope, 'application-restriction'].join(' ')}>{restriction}</p>
                                )}
                            </>
                        )}
                        {user && state.chatMembers[p.id]?.includes(user.id) && !state.deletedChatIds.includes(p.id) && (
                            <Link
                                className={[styles.scope, 'button secondary'].join(' ')}
                                href={`/messages/projects/${p.id}`}
                            >
                                프로젝트 채팅방 입장
                            </Link>
                        )}
                        <p className={[styles.scope, 'auth-hint'].join(' ')}>
                            무료 회원도 지원할 수 있습니다. 실제 서버 전송 없이 브라우저에 보관됩니다.
                        </p>
                    </Card>
                    <Card className={[styles.scope, 'profile-section project-host'].join(' ')}>
                        <h3>프로젝트 주최자</h3>
                        {host ? (
                            <Link href={`/users/${host.id}`}>
                                <ProfileAvatar name={host.nickname} color={host.color} small />
                                <div>
                                    <strong>{host.nickname}</strong>
                                    <small>
                                        {host.job} · {host.career}
                                    </small>
                                </div>
                            </Link>
                        ) : (
                            <p>주최자 프로필을 찾을 수 없습니다.</p>
                        )}
                        <Link
                            className={[styles.scope, 'button secondary'].join(' ')}
                            href={user ? (owner || !mockEnabled ? '/messages/chats' : `/messages/chats?userId=${p.hostId}`) : '/login'}
                        >
                            <MessageSquare size={14} />
                            {owner ? '내 메신저' : mockEnabled ? '주최자에게 쪽지' : '메신저로 이동'}
                        </Link>
                        <p className={[styles.scope, 'auth-hint'].join(' ')}>
                            {mockEnabled ? '주최자와 Mock 1:1 대화를 시작할 수 있습니다.' : '메신저에서 내 대화 목록을 확인할 수 있습니다.'}
                        </p>
                    </Card>
                </aside>
            </div>
            {modal === 'apply' && <ApplicationModal project={p} onClose={() => setModal(null)} />}{' '}
            {modal === 'cancel' && <CancelApplicationModal project={p} onClose={() => setModal(null)} />}
        </div>
    );
}
function MockProjectDetailPage() {
    const { projectId = '' } = useParams<{
        projectId: string;
        postId: string;
        userId: string;
    }>();
    return <ProjectDetail key={projectId} projectId={projectId} />;
}

export default function ProjectDetailPage(){const {enabled}=useMockVisibility();return enabled?<MockProjectDetailPage/>:<ApiProjectDetail/>;}
