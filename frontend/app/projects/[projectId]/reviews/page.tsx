'use client';
import styles from './page.module.css';
import {useMockVisibility} from '@/lib/state/mockVisibilityStore';
import {ApiProjectReviewForm} from './_components/ApiProjectReviewForm';
import { useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useQueryState as useSearchParams } from '@/lib/navigation';
import { useAuth } from '@/lib/state/authStore';
import { useProfiles } from '@/lib/state/profileStore';
import { useProjects } from '@/lib/state/projectStore';
import { teamFor } from '@/lib/projects/projectSelectors';
import { pendingReviewTargets, requestStatus } from '@/lib/projects/projectReviewService';
import { Button } from '@/components/common/Button';
import { ProjectDateSimulator } from '@/components/projects/ProjectDateSimulator';
import type { TeamReviewInput } from '@/types/projectReview';
function ReviewForm() {
    const { projectId = '' } = useParams<{
        projectId: string;
        postId: string;
        userId: string;
    }>();
    const [params] = useSearchParams();
    const { user, administration } = useAuth();
    const { state: profiles } = useProfiles();
    const { state, projects, now, submitReviews } = useProjects();
    const [draft, setDraft] = useState<
        Record<
            string,
            {
                rating: number;
                keywords: string[];
            }
        >
    >({});
    const [error, setError] = useState('');
    const request = state.reviewRequests.find(r => r.id === params.get('request') && r.projectId === projectId);
    const project = projects.find(p => p.id === projectId);
    const authorized =
        !!user &&
        !!project &&
        project.status === '프로젝트 종료' &&
        !!request &&
        request.recipientId === user.id &&
        request.token === params.get('token') &&
        teamFor(state, projectId).some(m => m.userId === user.id);
    if (!authorized)
        return (
            <div className={[styles.scope, 'container planned'].join(' ')}>
                <section className={[styles.scope, 'planned-card'].join(' ')}>
                    <h1>{user ? '리뷰 요청 링크를 확인해 주세요.' : '로그인 후 리뷰를 작성해 주세요.'}</h1>
                    <p>해당 프로젝트 참여자에게 발급된 본인 임시 링크로만 작성할 수 있습니다.</p>
                    <Link className={[styles.scope, 'button'].join(' ')} href={user ? '/my-projects' : '/login'}>
                        {user ? '내 프로젝트' : '로그인하기'}
                    </Link>
                </section>
            </div>
        );
    const status = requestStatus(request, now);
    const pending = pendingReviewTargets(request, profiles);
    const editable = status === '작성 대기';
    const edit = (id: string, patch: Partial<TeamReviewInput>) => {
        setDraft(prev => ({
            ...prev,
            [id]: { rating: prev[id]?.rating || 0, keywords: prev[id]?.keywords || [], ...patch },
        }));
        setError('');
    };
    return (
        <div className={[styles.scope, 'container project-review-write-page'].join(' ')}>
            <Link className={[styles.scope, 'text-link'].join(' ')} href={`/my-projects/${projectId}/manage`}>
                ← 프로젝트 참여 정보
            </Link>
            <header>
                <span className={[styles.scope, 'eyebrow'].join(' ')}>THANK YOUR TEAMMATES</span>
                <h1>함께한 팀원에게 리뷰를 남겨주세요.</h1>
                <h2>{project.title}</h2>
                <p>
                    종료 {new Date(request.issuedAt).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })} · 작성 만료{' '}
                    {new Date(request.expiresAt).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })}
                </p>
                <span className={[styles.scope, 'demo-pill'].join(' ')}>
                    {status} · 제출 {request.targetIds.length - pending.length}/{request.targetIds.length}명
                </span>
            </header>
            <ProjectDateSimulator />
            {status === '작성 완료' && (
                <p className={[styles.scope, 'review-page-status'].join(' ')} role="status">
                    리뷰를 모두 제출했습니다. 중복 제출할 수 없습니다.
                </p>
            )}
            {status === '기간 만료' && (
                <p className={[styles.scope, 'review-page-status'].join(' ')} role="status">
                    리뷰 작성 기간이 종료되었습니다. 재알림도 중단됩니다.
                </p>
            )}
            {status === '작성 시작 전' && (
                <p className={[styles.scope, 'review-page-status'].join(' ')} role="status">
                    현재 시뮬레이션 날짜에는 아직 리뷰 작성이 시작되지 않았습니다.
                </p>
            )}
            <form
                onSubmit={e => {
                    e.preventDefault();
                    const entries = pending.map(id => ({
                        targetId: id,
                        rating: draft[id]?.rating || 0,
                        keywords: (draft[id]?.keywords || []).filter(k => administration.keywords.includes(k)),
                    }));
                    const result = submitReviews(projectId, request.id, params.get('token') || '', entries);
                    if (!result.ok) setError(result.error || '평가 내용을 확인해 주세요.');
                }}
            >
                <div className={[styles.scope, 'team-review-list'].join(' ')}>
                    {request.targetIds.map(id => {
                        const profile = profiles.profiles[id];
                        const submitted = profiles.reviews.find(
                            r => r.projectId === projectId && r.authorId === user.id && r.targetId === id,
                        );
                        const selected = draft[id] || { rating: 0, keywords: [] };
                        return (
                            <section className={[styles.scope, 'workspace-panel team-review-card'].join(' ')} key={id}>
                                <header>
                                    <h2>{profile?.nickname || id}</h2>
                                    <span className={[styles.scope, 'demo-pill'].join(' ')}>
                                        {submitted ? '제출 완료' : '미제출'}
                                    </span>
                                </header>
                                {submitted ? (
                                    <div>
                                        <p aria-label={`${profile?.nickname || id} 제출 별점 ${submitted.rating}점`}>
                                            {'★'.repeat(submitted.rating)}
                                            {'☆'.repeat(5 - submitted.rating)}
                                        </p>
                                        <div className={[styles.scope, 'tags'].join(' ')}>
                                            {submitted.keywords.map(k => (
                                                <span key={k}>{k}</span>
                                            ))}
                                        </div>
                                        <Link
                                            className={[styles.scope, 'text-link'].join(' ')}
                                            href={`/users/${id}?tab=reviews`}
                                        >
                                            받은 리뷰 확인 →
                                        </Link>
                                    </div>
                                ) : (
                                    <>
                                        <fieldset disabled={!editable}>
                                            <legend>별점 · 필수</legend>
                                            <div className={[styles.scope, 'team-rating-input'].join(' ')}>
                                                {[1, 2, 3, 4, 5].map(n => (
                                                    <label key={n}>
                                                        <input
                                                            type="radio"
                                                            name={`rating-${id}`}
                                                            aria-label={`${profile?.nickname || id} ${n}점`}
                                                            checked={selected.rating === n}
                                                            onChange={() => edit(id, { rating: n })}
                                                        />
                                                        <span>
                                                            {'★'.repeat(n)} <small>{n}점</small>
                                                        </span>
                                                    </label>
                                                ))}
                                            </div>
                                        </fieldset>
                                        <fieldset disabled={!editable}>
                                            <legend>리뷰 키워드 · 선택사항 · {selected.keywords.length}/5개</legend>
                                            <div className={[styles.scope, 'chips'].join(' ')}>
                                                {administration.keywords.map(k => (
                                                    <button
                                                        type="button"
                                                        key={k}
                                                        aria-label={`${profile?.nickname || id} 키워드 ${k}`}
                                                        disabled={!selected.keywords.includes(k) && selected.keywords.length >= 5}
                                                        aria-pressed={selected.keywords.includes(k)}
                                                        className={[
                                                            styles.scope,
                                                            `chip ${selected.keywords.includes(k) ? 'selected' : ''}`,
                                                        ].join(' ')}
                                                        onClick={() =>
                                                            edit(id, {
                                                                keywords: selected.keywords.includes(k)
                                                                    ? selected.keywords.filter(v => v !== k)
                                                                    : [...selected.keywords, k],
                                                            })
                                                        }
                                                    >
                                                        {k}
                                                    </button>
                                                ))}
                                            </div>
                                        </fieldset>
                                    </>
                                )}
                            </section>
                        );
                    })}
                </div>
                {error && (
                    <p className={[styles.scope, 'form-error'].join(' ')} role="alert">
                        {error}
                    </p>
                )}
                <div className={[styles.scope, 'workspace-actions'].join(' ')}>
                    <p>본인 제외 모든 미제출 팀원의 별점이 필요합니다. 자유 서술형 리뷰는 제공하지 않습니다.</p>
                    <Button type="submit" disabled={!editable || !pending.length}>
                        전체 제출
                    </Button>
                </div>
            </form>
            <p className={[styles.scope, 'auth-hint'].join(' ')}>
                임시 링크와 만료/사용 여부는 브라우저 Mock 검증입니다. 실제 보안 검증은 향후 백엔드에서 구현합니다.
            </p>
        </div>
    );
}
export default function ProjectReviewWritePage() {
    const {enabled}=useMockVisibility();
    const { user } = useAuth();
    const { projectId = '' } = useParams<{
        projectId: string;
        postId: string;
        userId: string;
    }>();
    const [params] = useSearchParams();
    if(!enabled)return <ApiProjectReviewForm key={`${projectId}:${user?.id}`} projectId={projectId}/>;
    return <ReviewForm key={`${projectId}:${user?.id || 'guest'}:${params.get('request')}:${params.get('token')}`} />;
}
