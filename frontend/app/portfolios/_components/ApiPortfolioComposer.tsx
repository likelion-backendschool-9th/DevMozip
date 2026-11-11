'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useAuth } from '@/lib/state/authStore';
import { PortfolioComposer, PortfolioEditorForm } from './PortfolioComposer';
import { fetchPortfolioDetail, PortfolioRequestError, validPortfolioId } from '@/lib/portfolios/portfolioApi';
import { apiPortfolioDraftKey, loadApiPortfolioDraft, portfolioInputFromDetail, saveApiPortfolioDraft } from '@/lib/portfolios/portfolioApiDraft';
import { savePortfolio } from '@/lib/portfolios/portfolioMutations';
import { clearPortfolioDraft } from '@/lib/profiles/portfolioDraft';
import { getPortfolioBlocks } from '@/lib/profiles/portfolioBlocks';
import { emptyPortfolio } from '@/mocks/profile';
import type { PortfolioDetail } from '@/types/portfolioApi';
import type { PortfolioInput } from '@/types/profile';
import { PortfolioDetailResult } from './ApiPortfolioDetail';
export function ApiPortfolioComposer({ id }: { id?: string }) {
    const { user } = useAuth(); const [attempt, setAttempt] = useState(0);
    if (!user) return <section className="container planned"><h1>로그인하고 포트폴리오를 작성하세요.</h1><Link className="button" href="/login">로그인</Link></section>;
    return <EditorLoader key={`${user.email}:${id || 'new'}:${attempt}`} email={user.email} id={id} reload={() => setAttempt(v => v + 1)} />;
}
function EditorLoader({ email, id, reload }: { email: string; id?: string; reload: () => void }) {
    const [result, setResult] = useState<{ status: 'loading' } | { status: 'ready'; value: PortfolioDetail } | { status: 'error'; code: number; message: string }>({ status: 'loading' });
    useEffect(() => {
        if (!id || !validPortfolioId(id)) return;
        const controller = new AbortController();
        void fetchPortfolioDetail(id, controller.signal).then(value => { if (!controller.signal.aborted) setResult(value.canEdit ? { status: 'ready', value } : { status: 'error', code: 403, message: '본인 포트폴리오만 수정할 수 있어요.' }); })
            .catch(error => { if (!controller.signal.aborted) setResult({ status: 'error', code: error instanceof PortfolioRequestError ? error.status : 502, message: '편집할 포트폴리오를 불러오지 못했어요.' }); });
        return () => controller.abort();
    }, [id]);
    if (!id) return <EditorSession email={email} reload={reload} />;
    if (!validPortfolioId(id)) return <p role="alert">포트폴리오 ID를 확인해 주세요.</p>;
    if (result.status !== 'ready') return <PortfolioDetailResult result={result} onRetry={reload} />;
    return <EditorSession email={email} existing={result.value} reload={reload} />;
}
function EditorSession({ email, existing, reload }: { email: string; existing?: PortfolioDetail; reload: () => void }) {
    const key = apiPortfolioDraftKey(email, existing ? String(existing.portfolio.id) : undefined);
    const [initial] = useState(() => {
        const draft = loadApiPortfolioDraft(key);
        const base: PortfolioInput = existing ? portfolioInputFromDetail(existing) : { ...structuredClone(emptyPortfolio), fields: [], blocks: getPortfolioBlocks(emptyPortfolio) };
        return draft ? { ...draft, restoredDraft: true } : { restoredDraft: false, input: base, visibility: existing?.portfolio.visibility || 'PRIVATE' as const, baseUpdatedAt: existing?.portfolio.updatedAt || null };
    });
    const existingForSave = existing ? { ...existing, portfolio: { ...existing.portfolio, updatedAt: initial.baseUpdatedAt } } : undefined;
    return <PortfolioEditorForm userId={email} item={existing ? { ...initial.input, id: String(existing.portfolio.id), ownerId: email } : undefined} api={{
        initial: initial.input, draftKey: key, visibility: initial.visibility,
        canChangeVisibility: true, backHref: existing ? `/portfolios/${existing.portfolio.id}` : '/profile/me?tab=portfolio',
        restoredDraft: initial.restoredDraft,
        staleDraft: !!existing && initial.baseUpdatedAt !== existing.portfolio.updatedAt,
        reload: existing ? () => { clearPortfolioDraft(key); reload(); } : undefined,
        saveDraft: (input, visibility) => saveApiPortfolioDraft(key, { input, visibility, baseUpdatedAt: initial.baseUpdatedAt }),
        save: async (input, visibility, signal) => `/portfolios/${(await savePortfolio(input, visibility, existingForSave, signal)).portfolio.id}`,
    }} />;
}

export function PortfolioEditRoute() {
    const { portfolioId } = useParams<{ portfolioId: string }>();
    return /^\d+$/.test(portfolioId) ? <ApiPortfolioComposer id={portfolioId} /> : <PortfolioComposer editing />;
}
