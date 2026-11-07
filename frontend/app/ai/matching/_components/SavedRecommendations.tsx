'use client';
import { useEffect, useState } from 'react';
import { CheckCheck, FolderKanban, History, RefreshCw, UsersRound } from 'lucide-react';
import { getAiHistory, getAiRecommendation } from '@/lib/matching/aiApi';
import { RecommendationCards } from './RecommendationCards';
import type { AiRecommendation, AiRequestSummary } from '@/types/aiRecommendation';
import styles from './SavedRecommendations.module.css';
export function SavedRecommendations({ serverId, result, onLoaded }: { serverId?: number; result?: AiRecommendation; onLoaded?: () => void }) {
    const [history, setHistory] = useState<AiRequestSummary[]>([]);
    const [loaded, setLoaded] = useState<{ selection: string; value?: AiRecommendation }>();
    const [refresh, setRefresh] = useState(0);
    const [error, setError] = useState('');
    const [selected, setSelected] = useState<{ source?: number; value: string }>({ source: result?.requestId, value: '' });
    const selection = selected.source === result?.requestId ? selected.value : '';
    const [tab, setTab] = useState<'developers' | 'projects'>('developers');
    const [loading, setLoading] = useState(false);
    useEffect(() => {
        if (!serverId) return;
        let active = true;
        async function load(id: number) {
            setLoading(true);
            try {
                const items = await getAiHistory(id);
                const value = selection ? await getAiRecommendation(id, Number(selection)) : (refresh === 0 ? result : undefined) || (items.length ? await getAiRecommendation(id) : undefined);
                if (active) { setHistory(items); setLoaded({ selection, value }); setError(''); if (value) requestAnimationFrame(() => { if (active) onLoaded?.(); }); }
            } catch (e) { if (active) setError(e instanceof Error ? e.message : '추천 기록 조회에 실패했습니다.'); }
            finally { if (active) setLoading(false); }
        }
        void load(serverId);
        return () => { active = false; };
    }, [serverId, result, selection, refresh, onLoaded]);
    let current = result || loaded?.value;
    if (selection) current = loaded?.selection === selection ? loaded.value : undefined;
    else if (refresh > 0) current = loaded?.value;
    if (!serverId) return null;
    if (!current && !error && !loading) return null;
    return <section className={styles.results} aria-label="저장된 추천 결과">
        <div className={styles.top}><span><CheckCheck size={15} />추천 결과</span><div className={styles.history}><History size={13} /><select aria-label="추천 이력" value={selection} onChange={e => setSelected({ source: result?.requestId, value: e.target.value })}><option value="">최신 결과</option>{history.map(v => <option key={v.requestId} value={v.requestId}>#{v.requestId} · {v.createdAt.replace('T', ' ').slice(0, 16)}</option>)}</select><button type="button" disabled={loading} aria-label="결과 새로고침" onClick={() => setRefresh(v => v + 1)}><RefreshCw size={13} /></button></div></div>
        {error && <p role="alert" className={styles.error}>{error}</p>}
        {loading && !current && <p role="status" className={styles.note}>저장된 추천을 불러오고 있어요…</p>}
        {current && <>
            <h3>{current.status === 'SUCCESS' ? '이런 동료와 프로젝트는 어떠세요?' : '추천 요청 상태'}</h3>
            <p className={styles.note}>{current.conditions.interests.join(' · ')} · {current.conditions.career} · {current.conditions.durationMonths}개월 <span>요청 #{current.requestId}</span></p>
            {current.status === 'PENDING' && <p role="status" className={styles.note}>추천을 처리하고 있어요. 잠시 후 결과 새로고침으로 확인해 주세요.</p>}
            {current.status === 'FAILED' && <p role="alert" className={styles.error}>추천에 실패했어요. 대화와 조건은 보존됩니다. 추천 버튼으로 다시 시도해 주세요.</p>}
            {current.status === 'SUCCESS' && <>
                <div role="tablist" aria-label="추천 대상" className={styles.tabs} onKeyDown={e => {
                    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(e.key)) return;
                    e.preventDefault();
                    const index = e.key === 'Home' ? 0 : e.key === 'End' ? 1 : tab === 'developers' ? 1 : 0;
                    setTab(index === 0 ? 'developers' : 'projects');
                    e.currentTarget.querySelectorAll<HTMLButtonElement>('[role="tab"]')[index]?.focus();
                }}>
                    <button type="button" role="tab" id={`developers-${current.requestId}`} aria-controls={`panel-${current.requestId}`} aria-selected={tab === 'developers'} tabIndex={tab === 'developers' ? 0 : -1} onClick={() => setTab('developers')}><UsersRound size={14} />개발자 <span>{current.developers.length}</span></button>
                    <button type="button" role="tab" id={`projects-${current.requestId}`} aria-controls={`panel-${current.requestId}`} aria-selected={tab === 'projects'} tabIndex={tab === 'projects' ? 0 : -1} onClick={() => setTab('projects')}><FolderKanban size={14} />유사 프로젝트 <span>{current.projects.length}</span></button>
                </div>
                <div role="tabpanel" id={`panel-${current.requestId}`} aria-labelledby={`${tab}-${current.requestId}`}><RecommendationCards developers={current.developers} projects={current.projects} tab={tab} /></div>
                <p className={styles.caption}>추천 당시 저장된 적합도·기본 이유입니다. 공개 프로젝트 정보는 현재 기준입니다.</p>
            </>}
        </>}
    </section>;
}
