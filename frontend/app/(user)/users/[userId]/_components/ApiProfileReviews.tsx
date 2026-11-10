'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Star } from 'lucide-react';
import { Card } from '@/components/common/Card';
import { Button } from '@/components/common/Button';
import { fetchProfileReviews } from '@/lib/profiles/profileReviewsApi';
import { useAuth } from '@/lib/state/authStore';
import type { ProfileReviewsPage } from '@/types/profileReviews';
import styles from './ProfileReviews.module.css';
export type ProfileReviewsState = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; value: ProfileReviewsPage };
export function ProfileReviewsResult({ result, trustScore, onRetry, onPage }: { result: ProfileReviewsState; trustScore: number | null; onRetry: () => void; onPage: (page: number) => void }) {
    const scope = (name: string) => `${styles.scope} ${name}`;
    if (result.status === 'loading') return <p role="status">받은 리뷰를 불러오는 중…</p>;
    if (result.status === 'error') return <div role="alert"><p>{result.message}</p><Button variant="secondary" onClick={onRetry}>다시 시도</Button></div>;
    const { count, average, keywords, distribution, items, page, totalPages } = result.value;
    const selected = keywords.reduce((sum, k) => sum + k.count, 0);
    return <>
        <div className={scope('review-overview')}>
            <Card className={scope('profile-section trust-card')}><span>신뢰 온도</span>
                <div><Star size={30} /><strong>{trustScore === null ? '—' : trustScore.toFixed(1)}{trustScore !== null && <small>°C</small>}</strong></div>
                <p>프로필에 저장된 신뢰 온도입니다.</p>
                <div className={scope('review-metrics')}><span>평균 별점 <b>{count ? average.toFixed(1) : '—'}</b></span><span>협업 리뷰 <b>{count}개</b></span></div>
                <ul aria-label="별점 분포">{distribution.map((value, index) => <li key={index}>{index + 1}점 · {value}개</li>)}</ul>
            </Card>
            <Card className={scope('profile-section keyword-chart')}><h3>동료들이 발견한 강점</h3><p>전체 선택 키워드 중 해당 키워드의 비중입니다.</p>
                <div className={scope('keyword-bubbles')} role="list" aria-label="리뷰 키워드 버블 차트">{keywords.map((k, index) => {
                    const percent = selected ? Math.round(k.count / selected * 100) : 0;
                    const diameter = 72 + Math.sqrt(percent / 100) * 66;
                    return <div role="listitem" className={scope(`keyword-bubble bubble-${index % 5}`)} key={k.name} style={{ width: diameter, height: diameter }}><span>{k.name}</span><strong>{k.count}회</strong><small>{percent}%</small></div>;
                })}</div>{!keywords.length && <p>아직 선택된 키워드가 없습니다.</p>}
            </Card>
        </div>
        <p className={scope('profile-policy-note')}>리뷰는 종료된 프로젝트의 리뷰 요청에서 작성합니다. 이 탭은 받은 리뷰 조회 전용입니다.</p>
        <div className={scope('review-records')}>{items.map(r => <Card className={scope('profile-section')} key={r.id}>
            <div className={scope('review-record-head')}>
                {r.authorTag && r.authorTag.toLowerCase() !== 'me' ? <Link href={`/users/${encodeURIComponent(r.authorTag)}`}>{r.authorNickname}</Link> : <strong>{r.authorNickname}</strong>}
                <span className={scope('review-stars')} aria-label={`별점 ${r.rating}점`}>{'★'.repeat(r.rating)}{'☆'.repeat(5 - r.rating)}</span>
                {r.submittedAt && <time dateTime={r.submittedAt}>{new Date(r.submittedAt).toLocaleDateString('ko-KR', { timeZone: 'Asia/Seoul' })}</time>}
            </div><p>{r.projectId === null ? r.projectTitle : <Link href={`/projects/${r.projectId}`}>{r.projectTitle}</Link>}</p>
            <div className={scope('tags')}>{r.keywords.map(k => <span key={k}>{k}</span>)}{!r.keywords.length && <span>별점만 작성한 리뷰</span>}</div>
        </Card>)}</div>
        {!items.length && <div className="empty-state">{count === 0 ? '아직 받은 리뷰가 없습니다.' : '이 페이지에는 리뷰가 없습니다.'}
            {count > 0 && <Button variant="secondary" onClick={() => onPage(0)}>첫 페이지 보기</Button>}</div>}
        {totalPages > 0 && <nav aria-label="리뷰 페이지"><Button variant="secondary" disabled={page === 0} onClick={() => onPage(page - 1)}>이전</Button>
            <span> {page + 1} / {totalPages} </span><Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>다음</Button></nav>}
    </>;
}
function ReviewsLoader({ target, page, trustScore, onPage, onRetry }: { target: string; page: number; trustScore: number | null; onPage: (page: number) => void; onRetry: () => void }) {
    const [result, setResult] = useState<ProfileReviewsState>({ status: 'loading' });
    useEffect(() => {
        const controller = new AbortController();
        void fetchProfileReviews(target, page, controller.signal).then(value => {
            if (!controller.signal.aborted) setResult({ status: 'ready', value });
        }).catch(error => {
            if (!controller.signal.aborted) setResult({ status: 'error', message: error instanceof Error ? error.message : '리뷰를 불러오지 못했어요.' });
        });
        return () => controller.abort();
    }, [target, page]);
    return <ProfileReviewsResult result={result} trustScore={trustScore} onRetry={onRetry} onPage={onPage} />;
}
export function ApiProfileReviews({ own, slug, trustScore = null }: { own: boolean; slug?: string; trustScore?: number | null }) {
    const { user } = useAuth();
    const [page, setPage] = useState(0), [attempt, setAttempt] = useState(0);
    const target = own ? 'me' : slug;
    return <div className={`${styles.scope} reviews-tab`}><div className={scopeTitle}><h2>함께한 동료들의 리뷰</h2></div>
        {target ? <ReviewsLoader key={`${user?.email || ''}:${target}:${page}:${attempt}`} target={target} page={page} trustScore={trustScore} onPage={setPage} onRetry={() => setAttempt(n => n + 1)} />
            : <p>프로필 주소를 확인할 수 없어요.</p>}
    </div>;
}
const scopeTitle = `${styles.scope} tab-section-title`;
