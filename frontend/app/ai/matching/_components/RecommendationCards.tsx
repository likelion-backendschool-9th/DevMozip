'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import Image from 'next/image';
import { ArrowUpRight, BriefcaseBusiness, FolderKanban, LockKeyhole, UserRound } from 'lucide-react';
import { developerReason } from '@/lib/matching/recommendationPresentation';
import { getAiPublicProject } from '@/lib/matching/aiApi';
import { collaborationLabel, formatCareer, profileImageUrl, validProfileSlug } from '@/lib/profiles/profileDetail';
import { useAuth } from '@/lib/state/authStore';
import { Modal } from '@/components/common/Modal';
import type { AiRecommendationItem, AiPublicProject } from '@/types/aiRecommendation';
import styles from './SavedRecommendations.module.css';
function PublicProjectCard({ item }: { item: AiRecommendationItem }) {
    const [project, setProject] = useState<AiPublicProject>();
    const [failed, setFailed] = useState(false);
    const [open, setOpen] = useState(false);
    useEffect(() => {
        const controller = new AbortController();
        if (item.recommendedProjectId) void getAiPublicProject(item.recommendedProjectId, controller.signal).then(p => {
            if (!controller.signal.aborted) setProject(p);
        }).catch(() => { if (!controller.signal.aborted) setFailed(true); });
        return () => controller.abort();
    }, [item.recommendedProjectId]);
    const image = profileImageUrl(project?.imageUrl || null);
    return <article className={styles.card}>
        <div className={styles.projectCover}>{image ? <Image src={image} alt="" fill unoptimized onError={e => { e.currentTarget.style.display = 'none'; }} /> : <FolderKanban size={26} />}<span>{item.rank}번째 추천</span></div>
        <div className={styles.cardBody}>
            <div className={styles.cardTitle}><h4>{project?.title || `프로젝트 #${item.recommendedProjectId}`}</h4><strong>{item.matchScore.toFixed(2)}<small>%</small></strong></div>
            <p className={styles.summary}>{project?.summary || (failed ? '공개 프로젝트 정보를 불러오지 못했어요.' : '프로젝트 정보를 불러오는 중…')}</p>
            {project && <><div className={styles.tags}>{project.stacks.map(v => <span key={v}>{v}</span>)}</div><p className={styles.roles}><BriefcaseBusiness size={12} />{project.roles.join(' · ') || '모집 직군 미등록'}</p></>}
            <div className={styles.reason}><span>추천 이유</span><p>{item.basicReason}</p></div>
            <button type="button" className={styles.detail} disabled={!project} onClick={() => setOpen(true)}>프로젝트 상세 보기<ArrowUpRight size={13} /></button>
        </div>
        {open && project && <Modal title={project.title} onClose={() => setOpen(false)}><p>{project.summary}</p><div className={styles.description}>{project.description || '추가 소개가 없습니다.'}</div><div className={styles.tags}>{project.stacks.map(v => <span key={v}>{v}</span>)}</div></Modal>}
    </article>;
}
function DeveloperCard({ item, premium, requestedStacks }: { item: AiRecommendationItem; premium: boolean; requestedStacks: string[] }) {
    const developer = item.developer;
    const image = profileImageUrl(developer?.profileImage || null);
    const trust = developer?.trustScore;
    const hasTrust = typeof trust === 'number' && Number.isFinite(trust);
    const href = developer?.tag && validProfileSlug(developer.tag) ? `/users/${encodeURIComponent(developer.tag)}` : undefined;
    return <article className={styles.card}>
        <div className={styles.cardBody}>
            <div className={styles.developerTop}><span className={styles.avatar}>{image ? <Image src={image} alt="" width={38} height={38} unoptimized onError={e => { e.currentTarget.style.display = 'none'; }} /> : <UserRound size={22} />}</span><span><small>추천 개발자 {item.rank}</small><h4>{developer?.nickname || '공개 프로필 확인 불가'}{developer && <span className={styles.trustTemperature} data-known={hasTrust} aria-label={hasTrust ? `신뢰 온도 ${trust}도` : '신뢰 온도 미등록'}>{hasTrust ? `${trust}°C` : '온도 미등록'}</span>}</h4></span><strong className={styles.score}>{item.matchScore.toFixed(2)}<small>%</small></strong></div>
            {developer ? <>
                <span className={styles.collaborationStatus} data-status={developer.collaborationStatus || 'UNKNOWN'} aria-label={`현재 협업 상태: ${collaborationLabel(developer.collaborationStatus || null)}`}><i aria-hidden="true" />{collaborationLabel(developer.collaborationStatus || null)}</span>
                <dl className={styles.profileFacts}>
                    <div><dt>직군</dt><dd>{developer.jobCategories.join(' · ') || '직군 미등록'}</dd></div>
                    <div><dt>경력</dt><dd>{formatCareer(developer.career || null)}</dd></div>
                </dl>
            </> : <p className={styles.summary}>현재 공개 프로필 정보를 확인할 수 없어요.</p>}
            {developer && <div className={styles.tags}>{developer.skills.length ? developer.skills.map(v => <span key={v}>{v}</span>) : <span>기술 스택 미등록</span>}</div>}
            <div className={styles.reason}><span>추천 이유</span><p>{developerReason(item.basicReason, developer?.skills, requestedStacks)}</p></div>
            {href ? <Link className={styles.detail} href={href}>프로필 상세 보기<ArrowUpRight size={13} /></Link> : <button type="button" disabled className={styles.detail}>프로필 상세 보기<ArrowUpRight size={13} /></button>}
        </div>
        <div className={styles.locked}><LockKeyhole size={12} />{premium ? '상세 분석·포트폴리오 연결 준비 중' : <Link href="/subscription">상세 분석·포트폴리오는 프리미엄에서</Link>}</div>
    </article>;
}
export function RecommendationCards({ developers, projects, tab, requestedStacks = [] }: { requestedStacks?: string[]; developers: AiRecommendationItem[]; projects: AiRecommendationItem[]; tab: 'developers' | 'projects' }) {
    const { user } = useAuth();
    const premium = user?.role === 'premium' || user?.role === 'admin';
    const items = tab === 'developers' ? developers : projects;
    if (!items.length) return <div className={styles.empty}><FolderKanban size={24} /><strong>조건에 맞는 {tab === 'developers' ? '개발자가' : '프로젝트가'} 아직 없어요</strong><p>기술 스택이나 모집 직군의 범위를 넓혀 다시 찾아보세요.</p></div>;
    return <div className={styles.grid}>{items.map(item => tab === 'projects' ? <PublicProjectCard key={item.id} item={item} /> : <DeveloperCard key={item.id} item={item} premium={premium} requestedStacks={requestedStacks} />)}</div>;
}
