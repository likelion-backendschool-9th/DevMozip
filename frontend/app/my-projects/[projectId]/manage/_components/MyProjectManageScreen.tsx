'use client';
import styles from './MyProjectManageScreen.module.css';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useAuth } from '@/lib/state/AuthProvider';
import { useProjects } from '@/lib/state/ProjectProvider';
import { useProfiles } from '@/lib/state/ProfileProvider';
import { teamFor } from '@/lib/projects/projectSelectors';
import { ProjectManagePage } from '@/app/projects/[projectId]/manage/_components/ProjectManageScreen';
import { ReviewRequestStatus } from '@/components/projects/ReviewRequestStatus';
import { ProjectDateSimulator } from '@/components/projects/ProjectDateSimulator';
import { ProjectNotices } from '@/components/projects/ProjectNotices';
export function MyProjectManagePage() { const { projectId = '' } = useParams<{
    projectId: string;
    postId: string;
    userId: string;
}>(); const { user } = useAuth(); const { projects, state } = useProjects(); const { state: profiles } = useProfiles(); const p = projects.find(p => p.id === projectId); if (user && p?.hostId === user.id)
    return <ProjectManagePage />; const member = user ? teamFor(state, projectId).find(m => m.userId === user.id) : null; const application = state.applications.find(a => a.projectId === projectId && a.userId === user?.id && a.status === '지원 완료'); if (!user || !p || !member && !application)
    return <div className={[styles.scope, "container planned"].join(" ")}><section className={[styles.scope, "planned-card"].join(" ")}><h1>{!user ? '로그인하고 프로젝트를 확인하세요.' : '이 프로젝트에 대한 관리 권한이 없습니다.'}</h1><Link href={user ? '/my-projects' : '/login'} className={[styles.scope, "button"].join(" ")}>{user ? '내 프로젝트로' : '로그인하기'}</Link></section></div>; return <div className={[styles.scope, "container my-projects-page"].join(" ")}><Link className={[styles.scope, "text-link"].join(" ")} href="/my-projects">← 내 프로젝트</Link><header className={[styles.scope, "section-heading"].join(" ")}><div><span className={[styles.scope, "eyebrow"].join(" ")}>PROJECT MEMBER WORKSPACE</span><h1>{p.title}</h1><p>참여자 화면 · {p.status || '모집 중'}</p></div><Link className={[styles.scope, "button secondary"].join(" ")} href={`/projects/${p.id}`}>프로젝트 상세</Link></header><ProjectDateSimulator /><section className={[styles.scope, "workspace-panel"].join(" ")}><h2>내 참여 정보</h2><dl className={[styles.scope, "workspace-details"].join(" ")}><div><dt>주최자</dt><dd>{profiles.profiles[p.hostId]?.nickname || p.hostId}</dd></div><div><dt>담당 역할</dt><dd>{member?.role || application?.role}</dd></div><div><dt>참여 상태</dt><dd>{p.status === '프로젝트 종료' ? '프로젝트 종료' : member ? '팀원 등록 완료' : '지원 검토 중'}</dd></div><div><dt>진행 기간</dt><dd>{p.developmentStart} — {p.developmentEnd}</dd></div><div><dt>모집 기간</dt><dd>{p.recruitmentStart} — {p.deadline}</dd></div><div><dt>현재 인원</dt><dd>{p.members}/{p.capacity}명</dd></div></dl>{member && !state.deletedChatIds.includes(p.id) && !state.chatExclusions[p.id]?.includes(user.id) && <Link className={[styles.scope, "button"].join(" ")} href={`/chats/${p.id}`}>단체 채팅방 이동</Link>}{state.chatExclusions[p.id]?.includes(user.id) && <p className={[styles.scope, "form-error"].join(" ")}>강퇴 투표로 채팅방 접근이 제한되었습니다. 프로젝트 참여 이력은 유지됩니다.</p>}{state.deletedChatIds.includes(p.id) && <p>종료 시 채팅방이 삭제되었습니다.</p>}{!member && <p>지원 승인 후 단체 채팅방에 참여할 수 있습니다.</p>}</section>{p.status === '프로젝트 종료' && <ReviewRequestStatus projectId={p.id}/>}<ProjectNotices projectId={p.id}/></div>; }
