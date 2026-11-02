'use client';
import styles from './ReviewRequestStatus.module.css';
import Link from 'next/link';
import { useAuth } from '@/lib/state/AuthProvider';
import { useProjects } from '@/lib/state/ProjectProvider';
import { useProfiles } from '@/lib/state/ProfileProvider';
import { pendingReviewTargets, requestStatus, reviewLink } from '@/lib/projects/projectReviewService';
export function ReviewRequestStatus({ projectId }: {
    projectId: string;
}) { const { user } = useAuth(); const { state, now } = useProjects(); const { state: profiles } = useProfiles(); const items = state.reviewRequests.filter(r => r.projectId === projectId); return <section className={[styles.scope, "workspace-panel"].join(" ")}><h2>리뷰 요청 현황</h2><p>팀원별 제출 상태와 임시 링크 만료 시간을 확인합니다.</p>{!items.length && <div className={[styles.scope, "empty-state"].join(" ")}>아직 리뷰 요청이 없습니다. 프로젝트 종료 시 평가할 팀원이 있으면 생성됩니다.</div>}{items.map(r => <article className={[styles.scope, "review-request-row"].join(" ")} key={r.id}><div><strong>{profiles.profiles[r.recipientId]?.nickname || r.recipientId}</strong><small>제출 {r.targetIds.length - pendingReviewTargets(r, profiles).length}/{r.targetIds.length}명 · 만료 {new Date(r.expiresAt).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })}</small></div><span>{requestStatus(r, now)}</span>{r.recipientId === user?.id && <Link href={reviewLink(r)} className={[styles.scope, "button secondary small"].join(" ")}>{r.usedAt ? '제출 결과 확인' : '팀원 리뷰 작성'}</Link>}</article>)}</section>; }
