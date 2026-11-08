'use client';
import { useEffect, useState } from 'react';
import { CheckCheck, FolderKanban, RefreshCw, UsersRound } from 'lucide-react';
import { getAiHistory, getAiRecommendation } from '@/lib/matching/aiApi';
import { RecommendationCards } from './RecommendationCards';
import type { AiRecommendation } from '@/types/aiRecommendation';
import { useHome } from '@/lib/state/homeStore';
import { chatTimeline } from '@/lib/matching/chatTimeline';
import type { MatchingMessage } from '@/types/matchingChat';
import { ChatResults } from './ChatResults';
import chatStyles from '../page.module.css';
import styles from './SavedRecommendations.module.css';
export function SavedRecommendations({ serverId, result, messages = [], onLoaded }: { serverId?: number; result?: AiRecommendation; messages?: MatchingMessage[]; onLoaded?: () => void }) {
    const { rememberAiRecommendation } = useHome();
    const [loaded, setLoaded] = useState<AiRecommendation[]>([]);
    const [refresh, setRefresh] = useState(0);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    useEffect(() => {
        if (!serverId) return;
        let active = true;
        async function load(id: number) {
            setLoading(true);
            try {
                const history = await getAiHistory(id);
                const fetched = await Promise.allSettled(history.map(item => refresh === 0 && result?.requestId === item.requestId && result.status !== 'PENDING'
                    ? Promise.resolve(result) : getAiRecommendation(id, item.requestId)));
                if (!active) return;
                const success = fetched.flatMap(v => v.status === 'fulfilled' && v.value.status === 'SUCCESS' ? [v.value] : []).sort((a, b) => b.requestId - a.requestId)[0];
                if (success) rememberAiRecommendation(id, success);
                setLoaded(previous => [...new Map([...previous, ...fetched.flatMap(v => v.status === 'fulfilled' ? [v.value] : [])].map(v => [v.requestId, v])).values()]);
                setError(fetched.some(v => v.status === 'rejected') ? '일부 추천 기록을 불러오지 못했습니다.' : '');
                requestAnimationFrame(() => { if (active) onLoaded?.(); });
            } catch (e) { if (active) setError(e instanceof Error ? e.message : '추천 기록 조회에 실패했습니다.'); }
            finally { if (active) setLoading(false); }
        }
        void load(serverId);
        return () => { active = false; };
    }, [serverId, result, refresh, onLoaded, rememberAiRecommendation]);
    const results = result && (!loaded.some(v => v.requestId === result.requestId) || loaded.some(v => v.requestId === result.requestId && v.status === 'PENDING') && result.status !== 'PENDING')
        ? [...loaded.filter(v => v.requestId !== result.requestId), result] : loaded;
    const reload = () => setRefresh(v => v + 1);
    return <>
        {chatTimeline(messages, results).map(event => event.kind === 'recommendation'
            ? <RecommendationBlock key={`recommendation:${event.result.requestId}`} current={event.result} loading={loading} onRefresh={reload} />
            : <article key={`message:${event.message.id}`} className={event.message.role === 'user' ? chatStyles.userMessage : chatStyles.aiMessage}>
                <div className={chatStyles.messageLabel}>{event.message.role === 'user' ? '나' : event.message.result ? 'DevMozip · 이전 Mock 기록' : 'DevMozip'}</div><p>{event.message.text}</p>
                {event.message.result && <ChatResults result={event.message.result} />}
            </article>)}
        {loading && !results.length && <p role="status" className={styles.note}>저장된 추천을 불러오고 있어요…</p>}
        {error && <p role="alert" className={styles.error}>{error} <button type="button" disabled={loading} onClick={reload}>다시 불러오기</button></p>}
    </>;
}
function RecommendationBlock({ current, loading, onRefresh }: { current: AiRecommendation; loading: boolean; onRefresh: () => void }) {
    const [tab, setTab] = useState<'developers' | 'projects'>('developers');
    return <section className={styles.results} aria-label="저장된 추천 결과">
        <div className={styles.top}><span><CheckCheck size={15} />DevMozip · 추천 결과</span><div className={styles.history}><button type="button" disabled={loading} aria-label="결과 새로고침" onClick={onRefresh}><RefreshCw size={13} /></button></div></div>
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
                <p className={styles.caption}>적합도는 추천 당시 기준이며, 공개 프로필·프로젝트 정보는 현재 기준입니다.</p>
            </>}
    </section>;
}
