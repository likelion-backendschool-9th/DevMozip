'use client';
import styles from './page.module.css';
import { ReviewRequestStatus } from '@/components/projects/ReviewRequestStatus';
import { ProjectDateSimulator } from '@/components/projects/ProjectDateSimulator';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useNavigation as useNavigate, useQueryState as useSearchParams } from '@/lib/navigation';
import { Pencil, Trash2, Users, FileCheck2, CalendarPlus, Settings2 } from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { useProjects } from '@/lib/state/projectStore';
import { useProfiles } from '@/lib/state/profileStore';
import { teamFor } from '@/lib/projects/projectSelectors';
import { lifecycleOptions } from '@/lib/projects/projectManagementService';
import { projectStatus } from '@/lib/projects/projectService';
import { ProfileAvatar } from '@/components/profile/ProfileAvatar';
import { CreateProjectForm } from '@/app/projects/_components/CreateProjectForm';
import { ProjectNotices } from '@/components/projects/ProjectNotices';
import { Card } from '@/components/common/Card';
import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { Input } from '@/components/common/Input';
function ManageContent({ projectId }: { projectId: string }) {
    const { user } = useAuth();
    const { state, projects, act, now } = useProjects();
    const { state: profiles } = useProfiles();
    const navigate = useNavigate();
    const p = projects.find(p => p.id === projectId);
    const [params, setParams] = useSearchParams();
    const tab = ['applicants', 'team', 'settings'].includes(params.get('tab') || '')
        ? params.get('tab')!
        : 'applicants';
    const [filter, setFilter] = useState('전체');
    const [modal, setModal] = useState<'edit' | 'delete' | 'extend' | 'status' | null>(null);
    const [status, setStatus] = useState<string>(p?.status || '모집 중');
    const [deadline, setDeadline] = useState(p?.deadline || '');
    const [end, setEnd] = useState(p?.developmentEnd || '');
    const [deleteChat, setDeleteChat] = useState(false);
    const [error, setError] = useState('');
    // 알림 링크의 ?action=extend|end|edit로 들어오면 해당 모달을 연다(렌더 중 한 번만 처리).
    const action = params.get('action');
    const actionKey =
        p && p.hostId === user?.id && p.status !== '프로젝트 종료' && ['extend', 'end', 'edit'].includes(action || '')
            ? `${p.id}|${user.id}|${action}`
            : null;
    const [handledAction, setHandledAction] = useState<string | null>(null);
    if (handledAction !== actionKey) {
        setHandledAction(actionKey);
        if (p && actionKey) {
            setError('');
            setDeleteChat(false);
            setDeadline(p.deadline);
            setEnd(p.developmentEnd);
            setStatus(action === 'end' ? '프로젝트 종료' : p.status || '모집 중');
            setModal(action === 'end' ? 'status' : action === 'extend' ? 'extend' : 'edit');
        }
    }
    // 처리한 action은 주소에서 지운다.
    useEffect(() => {
        if (!actionKey) return;
        const next = new URLSearchParams(params);
        next.delete('action');
        setParams(next, { replace: true });
    }, [actionKey]);
    if (!p)
        return (
            <div className={[styles.scope, 'container planned'].join(' ')}>
                <section className={[styles.scope, 'planned-card'].join(' ')}>
                    <h1>관리할 프로젝트가 없습니다.</h1>
                    <Link href="/projects" className={[styles.scope, 'button'].join(' ')}>
                        목록으로
                    </Link>
                </section>
            </div>
        );
    if (!user || p.hostId !== user.id)
        return (
            <div className={[styles.scope, 'container planned'].join(' ')}>
                <section className={[styles.scope, 'planned-card'].join(' ')}>
                    <h1>주최자만 프로젝트를 관리할 수 있습니다.</h1>
                    <p>해당 공고를 작성한 계정으로 전환해 주세요.</p>
                    <Link className={[styles.scope, 'button'].join(' ')} href={user ? `/projects/${p.id}` : '/login'}>
                        {user ? '상세로 돌아가기' : '로그인'}
                    </Link>
                </section>
            </div>
        );
    const ended = p.status === '프로젝트 종료';
    const applications = state.applications.filter(a => a.projectId === p.id);
    const pending = applications.filter(a => a.status === '지원 완료');
    const team = teamFor(state, p.id);
    const open = (value: typeof modal) => {
        setError('');
        setDeleteChat(false);
        setModal(value);
        setStatus(p.status || '모집 중');
        setDeadline(p.deadline);
        setEnd(p.developmentEnd);
    };
    const own = projects.filter(v => v.hostId === user.id);
    return (
        <div className={[styles.scope, 'container project-manage-page'].join(' ')}>
            <Link className={[styles.scope, 'text-link'].join(' ')} href="/my-projects">
                ← 내 프로젝트
            </Link>
            <div className={[styles.scope, 'manage-breadcrumb'].join(' ')}>
                <Link href={`/projects/${p.id}`}>← 프로젝트 상세</Link>
                <select
                    aria-label="내 프로젝트 선택"
                    value={p.id}
                    onChange={e => navigate(`/projects/${e.target.value}/manage`)}
                >
                    {own.map(p => (
                        <option key={p.id} value={p.id}>
                            {p.title}
                        </option>
                    ))}
                </select>
            </div>
            <header className={[styles.scope, 'manage-heading'].join(' ')}>
                <div>
                    <div className={[styles.scope, 'eyebrow'].join(' ')}>PROJECT WORKSPACE</div>
                    <h1>{p.title}</h1>
                    <p>
                        주최자 관리 · <strong>{p.status || '모집 중'}</strong>
                        {projectStatus(p, now) === '모집 마감' ? ' · 모집 기한 경과' : ''}
                    </p>
                </div>
                <div className={[styles.scope, 'manage-heading-actions'].join(' ')}>
                    <Button variant="secondary" disabled={ended} onClick={() => open('edit')}>
                        <Pencil size={14} />
                        공고 수정
                    </Button>
                    <Button variant="secondary" onClick={() => open('delete')}>
                        <Trash2 size={14} />
                        삭제
                    </Button>
                </div>
            </header>
            <ProjectDateSimulator />
            {user.role !== 'premium' && (
                <p className={[styles.scope, 'auth-info'].join(' ')}>
                    임시 정책: 구독 해지 후 기존 주최자의 프로젝트 관리는 유지합니다. 신규 공고 생성과 지원자 포트폴리오
                    상세 열람은 프리미엄 전용입니다.
                </p>
            )}
            <section className={[styles.scope, 'workspace-panel'].join(' ')}>
                <h2>프로젝트 기본 정보</h2>
                <p>{p.summary}</p>
                <p>목표 · {p.goal}</p>
                <p>
                    모집 {p.recruitmentStart} — {p.deadline} · 개발 {p.developmentStart} — {p.developmentEnd}
                </p>
                <Link className={[styles.scope, 'text-link'].join(' ')} href={`/projects/${p.id}`}>
                    공개 상세 확인 →
                </Link>
            </section>
            <div className={[styles.scope, 'manage-metrics'].join(' ')}>
                <Card>
                    <span>검토 대기</span>
                    <strong>
                        {pending.length}
                        <small>명</small>
                    </strong>
                </Card>
                <Card>
                    <span>등록된 팀원</span>
                    <strong>
                        {team.length}
                        <small>명</small>
                    </strong>
                </Card>
                <Card>
                    <span>채팅방 참여</span>
                    <strong>
                        {(state.chatMembers[p.id] || []).length}
                        <small>명</small>
                    </strong>
                </Card>
                <Card>
                    <span>모집 마감일</span>
                    <strong className={[styles.scope, 'date-metric'].join(' ')}>{p.deadline}</strong>
                </Card>
            </div>
            <div className={[styles.scope, 'manage-tabs tabs'].join(' ')}>
                {[
                    { id: 'applicants', label: '지원자', icon: FileCheck2 },
                    { id: 'team', label: '팀원', icon: Users },
                    { id: 'settings', label: '운영 및 알림', icon: Settings2 },
                ].map(t => (
                    <button
                        key={t.id}
                        aria-pressed={tab === t.id}
                        className={[styles.scope, tab === t.id ? 'active' : ''].join(' ')}
                        onClick={() => setParams({ tab: t.id })}
                    >
                        <t.icon size={14} />
                        {t.label}
                    </button>
                ))}
            </div>
            {tab === 'applicants' && (
                <section className={[styles.scope, 'manage-section'].join(' ')}>
                    <div className={[styles.scope, 'tab-section-title'].join(' ')}>
                        <div>
                            <h2>지원자 목록</h2>
                            <p>지원서의 포트폴리오와 멘트를 확인해 주세요.</p>
                            {!ended && (
                                <Button
                                    variant="secondary"
                                    onClick={() => act({ type: 'demo-applicants', projectId: p.id })}
                                >
                                    지원자 데모 추가
                                </Button>
                            )}
                        </div>
                        <select
                            className={[styles.scope, 'form-input application-filter'].join(' ')}
                            aria-label="지원 상태 필터"
                            value={filter}
                            onChange={e => setFilter(e.target.value)}
                        >
                            {['전체', '지원 완료', '지원 승인', '지원 거절', '지원 취소'].map(v => (
                                <option key={v}>{v}</option>
                            ))}
                        </select>
                    </div>
                    {applications.filter(a => filter === '전체' || a.status === filter).length === 0 && (
                        <div className={[styles.scope, 'empty-state'].join(' ')}>해당 상태의 지원자가 없습니다.</div>
                    )}
                    <div className={[styles.scope, 'applicant-list'].join(' ')}>
                        {applications
                            .filter(a => filter === '전체' || a.status === filter)
                            .map(a => {
                                const profile = profiles.profiles[a.userId];
                                return (
                                    <Card
                                        key={a.id}
                                        className={[styles.scope, 'profile-section applicant-card'].join(' ')}
                                    >
                                        <div className={[styles.scope, 'applicant-head'].join(' ')}>
                                            {profile && (
                                                <ProfileAvatar name={profile.nickname} color={profile.color} small />
                                            )}
                                            <div>
                                                <Link href={`/users/${a.userId}`}>
                                                    <strong>{profile?.nickname || '지원자'}</strong>
                                                </Link>
                                                <small>
                                                    {a.role} · {profile?.career || '경력 미등록'}
                                                </small>
                                            </div>
                                            <span
                                                className={[
                                                    styles.scope,
                                                    `application-pill ${a.status === '지원 승인' ? 'approved' : ''}`,
                                                ].join(' ')}
                                            >
                                                {a.status}
                                            </span>
                                        </div>
                                        <p className={[styles.scope, 'preserve-text'].join(' ')}>{a.message}</p>
                                        <div className={[styles.scope, 'applicant-footer'].join(' ')}>
                                            <Link
                                                className={[styles.scope, 'text-link'].join(' ')}
                                                href={`/users/${a.userId}?tab=portfolio`}
                                            >
                                                포트폴리오 · {a.portfolioTitle} →
                                            </Link>
                                            <span>
                                                {new Date(a.createdAt).toLocaleDateString('ko-KR', {
                                                    timeZone: 'Asia/Seoul',
                                                })}
                                            </span>
                                        </div>
                                        {a.status === '지원 완료' && !ended && (
                                            <div className={[styles.scope, 'applicant-decisions'].join(' ')}>
                                                <Button
                                                    variant="secondary"
                                                    onClick={() =>
                                                        act({
                                                            type: 'decide',
                                                            projectId: p.id,
                                                            applicationId: a.id,
                                                            decision: '지원 거절',
                                                        })
                                                    }
                                                >
                                                    거절
                                                </Button>
                                                <Button
                                                    onClick={() =>
                                                        act({
                                                            type: 'decide',
                                                            projectId: p.id,
                                                            applicationId: a.id,
                                                            decision: '지원 승인',
                                                        })
                                                    }
                                                >
                                                    지원 승인
                                                </Button>
                                            </div>
                                        )}
                                    </Card>
                                );
                            })}
                    </div>
                </section>
            )}
            {tab === 'team' && (
                <section className={[styles.scope, 'manage-section'].join(' ')}>
                    <div className={[styles.scope, 'tab-section-title'].join(' ')}>
                        <div>
                            <h2>함께하는 팀원</h2>
                            <p>승인과 지원 취소가 팀원·채팅방 멤버에 같이 반영됩니다.</p>
                        </div>
                    </div>
                    {!state.chatExclusions[p.id]?.includes(user.id) ? (
                        <Link
                            className={[styles.scope, 'button secondary manage-chat-link'].join(' ')}
                            href={`/messages/projects/${p.id}`}
                        >
                            프로젝트 채팅방 열기 →
                        </Link>
                    ) : (
                        <p className={[styles.scope, 'form-error'].join(' ')}>
                            강퇴 투표로 채팅방 접근이 제한됩니다. 주최자 관리 권한은 임시 정책에 따라 유지됩니다.
                        </p>
                    )}
                    <div className={[styles.scope, 'manage-team-grid'].join(' ')}>
                        {team.map(m => {
                            const profile = profiles.profiles[m.userId];
                            return (
                                <Card
                                    className={[styles.scope, 'profile-section team-member-card'].join(' ')}
                                    key={m.userId}
                                >
                                    {profile && <ProfileAvatar name={profile.nickname} color={profile.color} />}
                                    <h3>
                                        {profile?.nickname || m.userId}
                                        {m.userId === p.hostId && <small>주최자</small>}
                                    </h3>
                                    <p>{m.role}</p>
                                    <span className={[styles.scope, 'chat-ready'].join(' ')}>
                                        {state.deletedChatIds.includes(p.id)
                                            ? '종료 시 채팅방 삭제됨'
                                            : state.chatExclusions[p.id]?.includes(m.userId)
                                              ? '채팅방 강퇴 · 참여 이력 유지'
                                              : state.chatMembers[p.id]?.includes(m.userId)
                                                ? '채팅 멤버 등록됨'
                                                : '초대 준비 중'}
                                    </span>
                                    <Link className={[styles.scope, 'text-link'].join(' ')} href={`/users/${m.userId}`}>
                                        프로필 보기 →
                                    </Link>
                                </Card>
                            );
                        })}
                    </div>
                    <p className={[styles.scope, 'auth-hint'].join(' ')}>
                        채팅방의 멤버와 메시지는 Mock 상태로 유지합니다. 실제 WebSocket은 사용하지 않습니다.
                    </p>
                </section>
            )}
            {tab === 'settings' && (
                <section className={[styles.scope, 'manage-section'].join(' ')}>
                    <Card className={[styles.scope, 'profile-section manage-operations'].join(' ')}>
                        <h2>프로젝트 운영</h2>
                        <p>
                            현재 상태: <b>{p.status || '모집 중'}</b>
                        </p>
                        <div className={[styles.scope, 'manage-operation-buttons'].join(' ')}>
                            <Button
                                variant="secondary"
                                disabled={p.status !== '모집 완료'}
                                onClick={() => {
                                    open('status');
                                    setStatus('프로젝트 진행 중');
                                }}
                            >
                                프로젝트 시작
                            </Button>
                            <Button
                                variant="secondary"
                                disabled={ended}
                                onClick={() => {
                                    open('status');
                                    setStatus('프로젝트 종료');
                                }}
                            >
                                프로젝트 종료
                            </Button>
                            <Button disabled={ended} onClick={() => open('status')}>
                                <Settings2 size={14} />
                                상태 변경 / 프로젝트 종료
                            </Button>
                            <Button disabled={ended} variant="secondary" onClick={() => open('extend')}>
                                <CalendarPlus size={14} />
                                프로젝트 기간 연장
                            </Button>
                        </div>
                        {ended && (
                            <p className={[styles.scope, 'auth-info'].join(' ')}>
                                프로젝트가 종료되었습니다. 종료 당시 팀원끼리 종료 시각부터 30일 동안 리뷰를 작성할 수
                                있습니다.
                            </p>
                        )}
                        <p className={[styles.scope, 'auth-hint'].join(' ')}>
                            모집 중 → 모집 완료 → 프로젝트 진행 중 → 프로젝트 종료. 모집 완료에서 재모집할 수 있으며
                            종료는 되돌릴 수 없습니다. 진행·종료 전 대기 지원서를 처리해 주세요.
                        </p>
                    </Card>
                    <ProjectNotices projectId={p.id} />
                </section>
            )}
            {ended && <ReviewRequestStatus projectId={p.id} />}
            {modal === 'edit' && (
                <Modal
                    className={[styles.scope, 'profile-dialog'].join(' ')}
                    title="공고 수정"
                    onClose={() => setModal(null)}
                >
                    <CreateProjectForm project={p} onSaved={() => setModal(null)} onCancel={() => setModal(null)} />
                </Modal>
            )}
            {modal === 'delete' && (
                <Modal title="공고를 삭제할까요?" onClose={() => setModal(null)}>
                    <p>
                        남아 있는 대기 지원자나 참여 팀원이 있으면 삭제할 수 없습니다. 삭제 시 관련 사용자에게 Mock
                        알림이 남습니다.
                    </p>
                    {error && (
                        <p role="alert" className={[styles.scope, 'form-error'].join(' ')}>
                            {error}
                        </p>
                    )}
                    <div className={[styles.scope, 'modal-actions'].join(' ')}>
                        <Button variant="secondary" onClick={() => setModal(null)}>
                            유지
                        </Button>
                        <Button
                            onClick={() => {
                                const result = act({ type: 'delete', projectId: p.id });
                                if (result.ok) navigate('/projects');
                                else setError(result.error || '삭제하지 못했습니다.');
                            }}
                        >
                            삭제
                        </Button>
                    </div>
                </Modal>
            )}
            {modal === 'status' && (
                <Modal title="프로젝트 상태 변경" onClose={() => setModal(null)}>
                    <form
                        className={[styles.scope, 'profile-form'].join(' ')}
                        onSubmit={e => {
                            e.preventDefault();
                            const result = act({
                                type: 'status',
                                projectId: p.id,
                                status: status as (typeof lifecycleOptions)[number],
                                deleteChatOnEnd: deleteChat,
                            });
                            if (result.ok) setModal(null);
                            else setError(result.error || '상태를 변경하지 못했습니다.');
                        }}
                    >
                        <label>
                            새 상태
                            <select
                                className={[styles.scope, 'form-input'].join(' ')}
                                value={status}
                                onChange={e => {
                                    setStatus(e.target.value);
                                    setError('');
                                }}
                            >
                                {lifecycleOptions.map(s => (
                                    <option key={s}>{s}</option>
                                ))}
                            </select>
                        </label>
                        {status === '프로젝트 종료' && (
                            <p className={[styles.scope, 'cancellation-warning'].join(' ')}>
                                종료는 되돌릴 수 없습니다. 등록된 팀원에게 종료 및 리뷰 작성 알림이 생성됩니다.
                            </p>
                        )}
                        {status === '프로젝트 종료' && (
                            <label className={[styles.scope, 'checkbox-label'].join(' ')}>
                                <input
                                    type="checkbox"
                                    checked={deleteChat}
                                    onChange={e => setDeleteChat(e.target.checked)}
                                />
                                종료 시 채팅방과 메시지 삭제 (미선택 시 읽기 전용 보관)
                            </label>
                        )}
                        {error && (
                            <p role="alert" className={[styles.scope, 'form-error'].join(' ')}>
                                {error}
                            </p>
                        )}
                        <div className={[styles.scope, 'modal-actions'].join(' ')}>
                            <Button type="button" variant="secondary" onClick={() => setModal(null)}>
                                취소
                            </Button>
                            <Button type="submit">변경 확인</Button>
                        </div>
                    </form>
                </Modal>
            )}
            {modal === 'extend' && (
                <Modal title="프로젝트 기간 연장" onClose={() => setModal(null)}>
                    <form
                        className={[styles.scope, 'profile-form'].join(' ')}
                        onSubmit={e => {
                            e.preventDefault();
                            const result = act({ type: 'extend', projectId: p.id, deadline, developmentEnd: end });
                            if (result.ok) setModal(null);
                            else setError(result.error || '기간을 확인해 주세요.');
                        }}
                    >
                        <label>
                            모집 마감일
                            <Input
                                type="date"
                                min={p.deadline}
                                value={deadline}
                                required
                                onChange={e => setDeadline(e.target.value)}
                            />
                        </label>
                        <label>
                            개발 종료일
                            <Input
                                type="date"
                                min={p.developmentEnd}
                                value={end}
                                required
                                onChange={e => setEnd(e.target.value)}
                            />
                        </label>
                        <p className={[styles.scope, 'auth-hint'].join(' ')}>
                            기존보다 긴 기간만 허용하며 지원자·팀원에게 알림을 생성합니다.
                        </p>
                        {error && (
                            <p role="alert" className={[styles.scope, 'form-error'].join(' ')}>
                                {error}
                            </p>
                        )}
                        <div className={[styles.scope, 'modal-actions'].join(' ')}>
                            <Button type="button" variant="secondary" onClick={() => setModal(null)}>
                                취소
                            </Button>
                            <Button type="submit">연장하기</Button>
                        </div>
                    </form>
                </Modal>
            )}
        </div>
    );
}
export default function ProjectManagePage() {
    const { projectId = '' } = useParams<{
        projectId: string;
        postId: string;
        userId: string;
    }>();
    return <ManageContent key={projectId} projectId={projectId} />;
}
