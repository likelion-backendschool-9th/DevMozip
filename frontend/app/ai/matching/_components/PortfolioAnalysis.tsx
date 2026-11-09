'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import Image from 'next/image';
import { useAuth } from '@/lib/state/authStore';
import { runPortfolioAnalysis } from '@/lib/matching/portfolioAnalysis';
import { AiApiError } from '@/lib/matching/aiApi';
import type { AiPortfolioAnalysis } from '@/types/aiPortfolioAnalysis';
import { PortfolioAnalysisContent } from './PortfolioAnalysisContent';
import styles from './PortfolioAnalysis.module.css';

export function PortfolioAnalysis({ recommendationId, nickname }: { recommendationId: number; nickname?: string | null }) {
    const { user } = useAuth();
    const premium = user?.role === 'premium' || user?.role === 'admin';
    const scope = `${user?.id}:${user?.role}:${recommendationId}`;
    const [action, setAction] = useState<{ scope: string; version: number }>();
    const [state, setState] = useState<{ scope: string; busy: boolean; data?: AiPortfolioAnalysis; error?: string; subscriptionRequired?: boolean }>();
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
                if (!controller.signal.aborted) setState({ scope, busy: false,
                    error: failure instanceof Error ? failure.message : '상세 분석을 불러올 수 없습니다.',
                    subscriptionRequired: failure instanceof AiApiError && failure.status === 403 });
            }
        };
        void load();
        return () => controller.abort();
    }, [action, opened, premium, scope, recommendationId]);
    const visible = state?.scope === scope ? state : undefined;
    return <section className={styles.analysis} aria-label="포트폴리오 상세 분석">
        <div className={styles.actions}>
            <button className={styles.reasonButton} type="button" disabled={opened && visible?.busy} onClick={() => setAction({ scope, version: (action?.version || 0) + 1 })}>
                <Image src="/icons/premium-star.svg" alt="" width={28} height={28} className={styles.star} />
                <span>{opened && visible?.busy ? '포트폴리오 분석 중' : opened && premium ? '분석 다시 확인하기' : '포트폴리오 추천 이유 보기'}</span>
            </button>
            {opened && <button type="button" onClick={() => setAction(undefined)}>닫기</button>}
        </div>
        {opened && (!premium || visible?.subscriptionRequired) && <div className={styles.premiumNotice} role="status">
            <p>{premium ? '유효한 프리미엄 구독이 필요합니다. 구독 상태를 확인해 주세요.' : '이 기능은 프리미엄 전용입니다.'}</p>
            <Link href="/subscription">프리미엄 페이지로 이동 →</Link>
        </div>}
        {opened && premium && visible?.error && !visible.subscriptionRequired && <p role="alert">{visible.error}</p>}
        {opened && premium && visible?.data && <div aria-live="polite">
            {['STALE', 'FAILED', 'NOT_GENERATED'].includes(visible.data.status) && <p>분석을 다시 요청해 주세요.</p>}
            <PortfolioAnalysisContent data={visible.data} nickname={nickname} />
        </div>}
    </section>;
}
