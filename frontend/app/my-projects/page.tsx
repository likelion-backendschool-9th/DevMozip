'use client';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';
import { ApiProjectList } from '@/app/projects/_components/api/ApiProjectList';
import styles from './page.module.css';
import { ProjectCreateButton } from '@/components/projects/ProjectCreateButton';
import Link from 'next/link';
import { useQueryState as useSearchParams } from '@/lib/navigation';
import { useAuth } from '@/lib/state/authStore';
import { useProfiles } from '@/lib/state/profileStore';
import { useProjects } from '@/lib/state/projectStore';
import { myProjects } from '@/lib/projects/myProjectsService';
import { myProjectTabs } from '@/mocks/projectWorkspace';
import { ProjectDateSimulator } from '@/components/projects/ProjectDateSimulator';
import { RecruitmentCard } from '@/components/projects/RecruitmentCard';
function MockMyProjectsPage() {
    const { user } = useAuth();
    const { state, now } = useProjects();
    const { state: profiles } = useProfiles();
    const [params, setParams] = useSearchParams();
    const tab = myProjectTabs.some(t => t.id === params.get('tab')) ? params.get('tab')! : 'all';
    if (!user)
        return (
            <div className={[styles.scope, 'container planned'].join(' ')}>
                <section className={[styles.scope, 'planned-card'].join(' ')}>
                    <h1>로그인하고 내 프로젝트를 확인하세요.</h1>
                    <Link className={[styles.scope, 'button'].join(' ')} href="/login">
                        로그인하기
                    </Link>
                </section>
            </div>
        );
    const entries = myProjects(state, user.id, tab);
    return (
        <div className={[styles.scope, 'container my-projects-page'].join(' ')}>
            <header className={[styles.scope, 'section-heading'].join(' ')}>
                <div>
                    <span className={[styles.scope, 'eyebrow'].join(' ')}>MY PROJECT WORKSPACE</span>
                    <h1>함께 만드는 나의 프로젝트</h1>
                    <p>모집부터 참여, 종료와 동료 리뷰까지 한곳에서 관리하세요.</p>
                </div>
                <ProjectCreateButton>새 프로젝트 모집</ProjectCreateButton>
            </header>
            <ProjectDateSimulator />
            <div className={[styles.scope, 'tabs my-project-tabs'].join(' ')}>
                {myProjectTabs.map(t => (
                    <button
                        key={t.id}
                        className={[styles.scope, t.id === tab ? 'active' : ''].join(' ')}
                        aria-pressed={t.id === tab}
                        onClick={() => setParams({ tab: t.id })}
                    >
                        {t.label}
                        <small>{myProjects(state, user.id, t.id).length}</small>
                    </button>
                ))}
            </div>
            <div className={[styles.scope, 'my-project-grid'].join(' ')}>
                {entries.map(({ project: p, host, member, application }) => {
                    const joined = !!member;
                    return (
                        <RecruitmentCard key={p.id} project={p} returnTo={'/my-projects'+(params.size?'?'+params.toString():'')}>
                            <dl className={styles.projectFacts}>
                                <div><dt>주최자</dt><dd>{profiles.profiles[p.hostId]?.nickname || p.hostId}</dd></div>
                                <div><dt>내 역할</dt><dd>{host ? '주최자 · 모집 운영' : `${member?.role || application?.role || '참여자'} · ${joined ? (p.status === '프로젝트 종료' ? '참여 종료' : '참여 중') : application?.status}`}</dd></div>
                                <div><dt>모집 기간</dt><dd>{p.recruitmentStart} — {p.deadline}</dd></div>
                                <div><dt>현재 참여</dt><dd>{p.members}/{p.capacity}명</dd></div>
                            </dl>
                            {p.status !== '프로젝트 종료' && new Date(`${p.developmentEnd}T23:59:59.999+09:00`).getTime() < now.getTime() && <p className="form-error">예정 종료일이 지났습니다.</p>}
                            <div className={styles.cardActions}>

                                {joined && !state.deletedChatIds.includes(p.id) && !state.chatExclusions[p.id]?.includes(user.id) && <Link className="text-link" href={`/messages/projects/${p.id}`}>단체 채팅방 →</Link>}
                            </div>
                            {state.chatExclusions[p.id]?.includes(user.id) && <p className="form-error">채팅방 강퇴 · 프로젝트 참여 이력 유지</p>}
                        </RecruitmentCard>
                    );
                })}
            </div>
            {!entries.length && (
                <div className={[styles.scope, 'empty-state'].join(' ')}>
                    <h2>해당하는 프로젝트가 없습니다.</h2>
                    <Link href="/projects" className={[styles.scope, 'button secondary'].join(' ')}>
                        모집 프로젝트 탐색
                    </Link>
                </div>
            )}
        </div>
    );
}

export default function MyProjectsPage(){const {enabled}=useMockVisibility();return enabled?<MockMyProjectsPage/>:<ApiProjectList mine/>;}
