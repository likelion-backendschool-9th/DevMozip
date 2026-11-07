'use client';
import { useEffect, useState } from 'react';
import { getAiHistory, getAiRecommendation } from '@/lib/matching/aiApi';
import type { AiRecommendation, AiRequestSummary } from '@/types/aiRecommendation';
import styles from './SavedRecommendations.module.css';

export function SavedRecommendations({ serverId, result }: { serverId?: number; result?: AiRecommendation }) {
    const [history, setHistory] = useState<AiRequestSummary[]>([]);
    const [loaded, setLoaded] = useState<{ selection: string; value?: AiRecommendation }>();
    const [refresh, setRefresh] = useState(0);
    const [error, setError] = useState('');
    const [selection, setSelection] = useState('');
    useEffect(() => {
        if (!serverId) return;
        let active = true;
        async function load(id: number) {
            try {
                const items = await getAiHistory(id);
                const value = selection ? await getAiRecommendation(id, Number(selection))
                    : (refresh === 0 ? result : undefined) || (items.length ? await getAiRecommendation(id) : undefined);
                if (active) { setHistory(items); setLoaded({ selection, value }); setError(''); }
            } catch (e) { if (active) setError(e instanceof Error ? e.message : '추천 기록 조회에 실패했습니다.'); }
        }
        void load(serverId);
        return () => { active = false; };
    }, [serverId, result, selection, refresh]);
    let current = result || loaded?.value;
    if (selection) current = loaded?.selection === selection ? loaded.value : undefined;
    else if (refresh > 0) current = loaded?.value;
    if (!serverId) return null;
    return <section className={styles.results} aria-label="저장된 추천 결과">
        <h2>추천 결과</h2>
        <label>추천 이력 <select aria-label="추천 이력" value={selection} onChange={e => setSelection(e.target.value)}>
            <option value="">최신 결과</option>
            {history.map(item => <option key={item.requestId} value={item.requestId}>요청 #{item.requestId} · {item.createdAt.replace('T', ' ')} · {item.status}</option>)}
        </select></label>
        <button type="button" onClick={() => setRefresh(value => value + 1)}>결과 새로고침</button>
        {error && <p role="alert">{error}</p>}
        {!current && !error && <p>저장된 추천 결과가 없습니다.</p>}
        {current && <>
            <p>요청 #{current.requestId} · {current.createdAt.replace('T', ' ')}</p>
            <p>실행 조건: {current.conditions.interests.join(', ')} · {current.conditions.career} · {current.conditions.durationMonths}개월</p>
            {current.status === 'PENDING' && <p role="status">처리 중인 요청입니다. 잠시 후 결과 새로고침으로 상태를 확인해 주세요.</p>}
            {current.status === 'FAILED' && <p role="alert">추천에 실패했습니다. 조건과 대화는 보존됩니다. 추천 버튼으로 다시 실행할 수 있습니다.</p>}
            {current.status === 'SUCCESS' && <>
                <p>실제 DB 후보를 바탕으로 계산해 저장한 점수와 기본 추천 이유입니다.</p>
                {(['developers', 'projects'] as const).map(kind => <div key={kind}>
                    <h3>{kind === 'developers' ? '추천 개발자' : '유사 프로젝트'}</h3>
                    {!current[kind].length && <p>조건에 맞는 {kind === 'developers' ? '개발자가' : '프로젝트가'} 없습니다.</p>}
                    <div className={styles.grid}>{current[kind].map(item => <article key={item.id}>
                        <strong>{item.rank}위 · {kind === 'developers' ? `개발자 #${item.recommendedUserId}` : `프로젝트 #${item.recommendedProjectId}`}</strong>
                        <b>{item.matchScore.toFixed(2)}점</b>
                        <p>{item.basicReason}</p>
                    </article>)}</div>
                </div>)}
            </>}
        </>}
    </section>;
}
