'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/lib/state/authStore';
import { runPortfolioAnalysis } from '@/lib/matching/portfolioAnalysis';
import type { AiPortfolioAnalysis } from '@/types/aiPortfolioAnalysis';
import { PortfolioAnalysisContent } from './PortfolioAnalysisContent';
import styles from './PortfolioAnalysis.module.css';

export function PortfolioAnalysis({ recommendationId, nickname }: { recommendationId: number; nickname?: string | null }) {
    const { user } = useAuth();
    const premium = user?.role === 'premium' || user?.role === 'admin';
    const scope = `${user?.id}:${user?.role}:${recommendationId}`;
    const [action, setAction] = useState<{ scope: string; version: number }>();
    const [state, setState] = useState<{ scope: string; busy: boolean; data?: AiPortfolioAnalysis; error?: string }>();
    const opened = action?.scope === scope;
    useEffect(() => {
        if (!opened || !premium) return;
        const controller = new AbortController();
        const load = async () => {
            setState({ scope, busy: true });
            try {
                await runPortfolioAnalysis(recommendationId, controller.signal, data => {
                    setState({ scope, busy: data.status === 'PENDING', data });
                });
            } catch (failure) {
                if (!controller.signal.aborted) setState({ scope, busy: false, error: failure instanceof Error ? failure.message : '상세 분석을 불러올 수 없습니다.' });
            }
        };
        void load();
        return () => controller.abort();
    }, [action, opened, premium, scope, recommendationId]);
    if (!premium) return <Link href="/subscription">상세 분석·포트폴리오는 프리미엄에서</Link>;
    const visible = state?.scope === scope ? state : undefined;
    return <section className={styles.analysis} aria-label="포트폴리오 상세 분석">
        <div className={styles.actions}>
            <button type="button" disabled={opened && visible?.busy} onClick={() => setAction({ scope, version: (action?.version || 0) + 1 })}>
                {opened && visible?.busy ? '포트폴리오 분석 중' : opened ? '분석 다시 확인하기' : '포트폴리오 추천 이유 보기'}
            </button>
            {opened && <button type="button" onClick={() => setAction(undefined)}>닫기</button>}
        </div>
        {opened && visible?.error && <p role="alert">{visible.error}</p>}
        {opened && visible?.data && <div aria-live="polite">
            {['STALE', 'FAILED', 'NOT_GENERATED'].includes(visible.data.status) && <p>분석을 다시 요청해 주세요.</p>}
            <PortfolioAnalysisContent data={visible.data} nickname={nickname} />
        </div>}
    </section>;
}
