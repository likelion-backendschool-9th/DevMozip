'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import Image from 'next/image';
import { PortfolioDeleteButton } from '@/app/portfolios/_components/PortfolioDeleteButton';
import { Plus } from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { Card } from '@/components/common/Card';
import { Button } from '@/components/common/Button';
import { fetchPortfolioPage, PortfolioRequestError } from '@/lib/portfolios/portfolioApi';
import type { PortfolioPage } from '@/types/portfolioApi';
import styles from './PortfolioTab.module.css';
export type PortfolioListState = { status: 'loading' } | { status: 'error'; code: number; message: string } | { status: 'ready'; value: PortfolioPage };
export function PortfolioListResult({ result, onRetry, onPage, own = false, onDeleted }: { own?: boolean; onDeleted?: () => void; result: PortfolioListState; onRetry: () => void; onPage: (page: number) => void }) {
    if (result.status === 'loading') return <p role="status">포트폴리오를 불러오는 중…</p>;
    if (result.status === 'error') return <section role="alert"><p>{result.message}</p>{result.code === 401
        ? <Link href="/login?next=/profile/me?tab=portfolio" className="button secondary">로그인하기</Link>
        : <Button variant="secondary" onClick={onRetry}>다시 시도</Button>}</section>;
    const { items, page, totalElements, totalPages } = result.value;
    return <>
        <p className="text-muted">포트폴리오 {totalElements}개</p>
        {!items.length && <div className="empty-state">{totalElements === 0 ? '아직 등록된 포트폴리오가 없습니다.' : '이 페이지에는 포트폴리오가 없습니다.'}
            {totalElements > 0 && <Button variant="secondary" onClick={() => onPage(0)}>첫 페이지 보기</Button>}</div>}
        <div className={styles.grid}>{items.map(item => <Card key={item.id} className={styles.summaryCard}>
            <Link className={styles.openCard} href={`/portfolios/${item.id}`} aria-label={`${item.title || '제목 미입력'} 상세 보기`}>
                <Image unoptimized width={960} height={540} className={styles.cover} src={item.thumbnailUrl || '/portfolio-cover.svg'} alt={`${item.title || '포트폴리오'} 대표 이미지`} />
                <div className={styles.summary}><span className={styles.field}>{item.topic || '분야 미등록'}{item.visibility === 'PRIVATE' ? ' · 비공개' : ''}</span>
                    <h3>{item.title || '제목 미입력'}</h3><p>{item.summary || '한 줄 소개 미입력'}</p>
                    <div className="profile-tags">{item.techStack.map(stack => <span key={stack}>{stack}</span>)}</div>
                </div>
            </Link>
            {own && <div className={styles.ownerActions}><Link href={`/portfolios/${item.id}/edit`}>수정</Link><PortfolioDeleteButton id={item.id} title={item.title || '포트폴리오'} onDeleted={onDeleted} /></div>}
        </Card>)}</div>
        {totalPages > 0 && <nav aria-label="포트폴리오 페이지" className={styles.ownerActions}>
            <Button variant="secondary" disabled={page === 0} onClick={() => onPage(page - 1)}>이전</Button>
            <span>{page + 1} / {totalPages}</span><Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>다음</Button>
        </nav>}
    </>;
}
export function ApiPortfolioTab({ slug, own }: { slug?: string; own: boolean }) {
    const { user } = useAuth();
    const target = own ? 'me' : slug;
    const [page, setPage] = useState(0), [attempt, setAttempt] = useState(0);
    const requestKey = `${user?.email || ''}:${target}:${page}:${attempt}`;
    return <div>
        <div className="tab-section-title"><div><h2>경험으로 쌓아온 포트폴리오</h2><p>이미지와 요약으로 살펴보고, 상세 페이지에서 더 알아보세요.</p></div>
            {own && <Link href="/portfolios/new" className="button"><Plus size={15} />포트폴리오 등록</Link>}</div>
        {!target ? <p role="alert">프로필 태그를 확인할 수 없어요.</p>
            : <PortfolioListLoader key={requestKey} target={target} page={page} own={own} onRetry={() => setAttempt(v => v + 1)} onPage={setPage} />}
    </div>;
}

function PortfolioListLoader({ target, page, own, onRetry, onPage }: { target: string; page: number; own: boolean; onRetry: () => void; onPage: (page: number) => void }) {
    const [result, setResult] = useState<PortfolioListState>({ status: 'loading' });
    useEffect(() => {
        const controller = new AbortController();
        void fetchPortfolioPage(target, page, controller.signal).then(value => {
            if (!controller.signal.aborted) setResult({ status: 'ready', value });
        }).catch(error => {
            if (!controller.signal.aborted) setResult({ status: 'error', code: error instanceof PortfolioRequestError ? error.status : 502,
                message: error instanceof PortfolioRequestError ? error.message : '포트폴리오 목록을 확인할 수 없어요.' });
        });
        return () => controller.abort();
    }, [target, page]);
    return <PortfolioListResult result={result} own={own} onDeleted={onRetry} onRetry={onRetry} onPage={onPage} />;
}
