'use client';
import { useCallback, useEffect, useRef, useState } from 'react';
import { PanelLeft, PanelRight, SlidersHorizontal, Sparkles, ArrowRight, LoaderCircle } from 'lucide-react';
import { useNavigation } from '@/lib/navigation';
import { useHome } from '@/lib/state/homeStore';
import { useAuth } from '@/lib/state/authStore';
import { matchingLoginPath } from '@/lib/matching/loginPrompt';
import { chatMessageLength, missingChatRequirements } from '@/lib/matching/matchingChatService';
import { chatPromptExamples, chatRequirementLabels, emptyChatRequirements } from '@/mocks/matchingChat';
import type { AiRecommendation } from '@/types/aiRecommendation';
import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { ChatSidebar } from './_components/ChatSidebar';
import { ChatComposer } from './_components/ChatComposer';
import { ChatConditions } from './_components/ChatConditions';
import { SavedRecommendations } from './_components/SavedRecommendations';
import styles from './page.module.css';

export default function MatchingPage() {
    const { user } = useAuth();
    return <MatchingWorkspace key={user?.email || 'guest'} />;
}
function MatchingWorkspace() {
    const { user } = useAuth();
    const { conversations, conversation, newChat, selectChat, deleteAiChat, sendChat, analyzeChat, editChatMessage, loadAiChats, recommendChat, recommendationUnchanged, resumeLoginPrompt } = useHome();
    const navigate = useNavigation();
    const [guestInput, setGuestInput] = useState('');
    const [error, setError] = useState('');
    const [busyKey, setBusyKey] = useState<string | null>(null);
    const [task, setTask] = useState<'analyze' | 'recommend' | null>(null);
    const [retryAnalysis, setRetryAnalysis] = useState(false);
    const [result, setResult] = useState<{ chatKey: string; value: AiRecommendation }>();
    const [deleting, setDeleting] = useState<string | null>(null);
    const [sidebar, setSidebar] = useState(true);
    const [conditions, setConditions] = useState(true);
    const [drawer, setDrawer] = useState<'history' | 'conditions' | null>(null);
    const [historyLoading, setHistoryLoading] = useState(!!user);
    const workspace = useRef<HTMLDivElement>(null);
    const thread = useRef<HTMLDivElement>(null);
    const chatKey = `${user?.email}:${conversation?.id || 'new'}`;
    const currentKey = useRef(chatKey);
    const busy = busyKey === chatKey;
    const text = user ? conversation?.draft || '' : guestInput;
    const requirements = conversation?.requirements || emptyChatRequirements;
    const missing = missingChatRequirements(requirements);
    const visibleError = error.endsWith('항목을 선택해 주세요.') ? (missing.length ? `${missing.map(k => chatRequirementLabels[k]).join('·')} 항목을 선택해 주세요.` : '') : error;
    const scrollBottom = useCallback(() => { thread.current?.scrollTo({ top: thread.current.scrollHeight, behavior: 'smooth' }); }, []);
    useEffect(() => { currentKey.current = chatKey; }, [chatKey]);
    useEffect(() => {
        if (conversation?.messages.length || busy || result?.chatKey === chatKey) scrollBottom();
        else if (thread.current) thread.current.scrollTop = 0;
    }, [conversation?.id, conversation?.messages.length, busy, result, chatKey, scrollBottom]);
    useEffect(() => {
        const measure = () => {
            if (window.innerWidth > 1100) setDrawer(null);
            const el = workspace.current;
            if (el) el.style.setProperty('--workspace-height', `${Math.max(340, window.innerHeight - el.getBoundingClientRect().top - 18)}px`);
        };
        measure(); window.addEventListener('resize', measure);
        const header = document.querySelector('header');
        const observer = new ResizeObserver(measure); if (header) observer.observe(header);
        return () => { window.removeEventListener('resize', measure); observer.disconnect(); };
    }, []);
    useEffect(() => {
        if (!user) return;
        let active = true;
        void loadAiChats().catch(e => { if (active) setError(e instanceof Error ? e.message : '대화 기록을 불러오지 못했습니다.'); }).finally(() => { if (active) setHistoryLoading(false); });
        return () => { active = false; };
    }, [user, loadAiChats]);
    useEffect(() => {
        if (!user) return;
        const token = new URLSearchParams(window.location.search).get('resume');
        if (!token) return;
        let active = true;
        void Promise.resolve().then(() => {
            if (!active) return;
            const resumed = resumeLoginPrompt(token);
            if (resumed.error) setError(resumed.error);
            else navigate('/ai/matching', { replace: true });
        });
        return () => { active = false; };
    }, [user, resumeLoginPrompt, navigate]);
    useEffect(() => {
        if (!drawer) return;
        const panel = document.getElementById(`matching-${drawer}`);
        const previous = document.activeElement as HTMLElement | null;
        const elements = () => Array.from(panel?.querySelectorAll<HTMLElement>('button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href]') || []).filter(el => el.offsetParent !== null);
        elements()[0]?.focus();
        const keydown = (e: KeyboardEvent) => {
            if (e.key === 'Escape') setDrawer(null);
            if (e.key !== 'Tab') return;
            const list = elements(); const first = list[0]; const last = list[list.length - 1];
            if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last?.focus(); }
            else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first?.focus(); }
        };
        document.addEventListener('keydown', keydown);
        return () => { document.removeEventListener('keydown', keydown); previous?.focus(); };
    }, [drawer]);
    const openPanel = (panel: 'history' | 'conditions') => {
        if (window.matchMedia('(max-width: 1100px)').matches) setDrawer(v => v === panel ? null : panel);
        else if (panel === 'history') setSidebar(v => !v);
        else setConditions(v => !v);
    };
    const showConditions = () => { if (window.matchMedia('(max-width: 1100px)').matches) setDrawer('conditions'); else setConditions(true); };
    const changeText = (value: string) => { if (user) editChatMessage(value); else setGuestInput(value); setError(''); };
    const login = () => { try { navigate(matchingLoginPath(text)); } catch (e) { setError(e instanceof Error ? e.message : '입력을 보관하지 못했습니다.'); } };
    const runAnalysis = async (conversationId = conversation?.id) => {
        const key = `${user?.email}:${conversationId || 'new'}`;
        currentKey.current = key;
        setBusyKey(key); setTask('analyze'); setRetryAnalysis(false); setError('');
        try { await analyzeChat(); }
        catch (e) { if (currentKey.current === key) { setRetryAnalysis(true); setError(e instanceof Error ? e.message : '메시지 분석을 완료하지 못했습니다. 다시 시도해 주세요.'); } }
        finally { setBusyKey(null); setTask(null); }
    };
    const send = () => {
        if (!user) { login(); return; }
        const sent = sendChat(text); setError(sent.error || '');
        if (sent.ok) void runAnalysis(sent.conversationId);
    };
    const recommend = async () => {
        if (!user) { login(); return; }
        if (busy || recommendationUnchanged) return;
        if (missing.length && !conversation?.pendingRecommendation) { setError(`${missing.map(k => chatRequirementLabels[k]).join('·')} 항목을 선택해 주세요.`); showConditions(); return; }
        const key = chatKey; setBusyKey(key); setTask('recommend'); setError('');
        try { const value = await recommendChat(); if (currentKey.current === key) setResult({ chatKey: key, value }); }
        catch (e) { if (currentKey.current === key) setError(e instanceof Error ? e.message : '추천에 실패했습니다. 다시 시도해 주세요.'); }
        finally { setBusyKey(null); setTask(null); }
    };
    return <div className={styles.page}>
        <div className={styles.pageHeading}><div className={styles.brandIcon}><Sparkles size={20} /></div><div><h1>AI 팀원 추천</h1><p>아이디어에서 시작하는, 나에게 맞는 팀</p></div><span className={styles.mode}><i />조건 기반 매칭</span></div>
        <div ref={workspace} className={styles.workspace} data-sidebar={sidebar} data-conditions={conditions}>
            {drawer && <button type="button" className={styles.backdrop} aria-label="열린 패널 닫기" onClick={() => setDrawer(null)} />}
            <aside id="matching-history" aria-label="추천 대화 기록" className={styles.sidebar} data-mobile-open={drawer === 'history'} role={drawer === 'history' ? 'dialog' : undefined} aria-modal={drawer === 'history' ? true : undefined}>
                <ChatSidebar conversations={conversations} activeId={conversation?.id} signedIn={!!user} busy={busy} onClose={() => { setSidebar(false); setDrawer(null); }} onNew={() => { if (user) newChat(); else setGuestInput(''); setError(''); setRetryAnalysis(false); setDrawer(null); }} onSelect={id => { selectChat(id); setError(''); setRetryAnalysis(false); setDrawer(null); }} onDelete={setDeleting} />
            </aside>
            <section className={styles.chat} aria-label="AI 추천 대화" aria-busy={busy}>
                <header className={styles.toolbar}><button type="button" aria-label="대화 사이드바 접기/펼치기" aria-controls="matching-history" onClick={() => openPanel('history')}><PanelLeft size={18} /></button><div><strong>{conversation?.title || '새로운 대화'}</strong><span>{historyLoading ? '대화 기록 불러오는 중' : user ? '대화하며 아이디어를 정리해 보세요' : '로그인하고 아이디어를 이어가세요'}</span></div><button type="button" className={styles.conditionToggle} aria-controls="matching-conditions" aria-label="추천 조건 패널 접기/펼치기" onClick={() => openPanel('conditions')}><PanelRight size={17} /><span>추천 조건</span></button></header>
                <div className={styles.thread} ref={thread} role="log" aria-label="추천 메시지" aria-live="polite">
                    {!conversation?.messages.length ? <div className={styles.welcome}><div className={styles.welcomeIcon}><Sparkles size={27} /></div><span>DEV‌MOZIP MATCHING</span><h2>어떤 프로젝트를<br />함께 만들어 볼까요?</h2><p>떠오른 아이디어나 찾고 있는 동료를 이야기해 주세요.<br />조건을 정리하고, 함께할 개발자와 비슷한 프로젝트를 찾아봐요.</p><div className={styles.examples}>{chatPromptExamples.map((v, i) => <button type="button" key={v.title} onClick={() => { changeText(v.text); workspace.current?.querySelector<HTMLTextAreaElement>('textarea')?.focus(); }}><span>{['01', '02', '03'][i]}</span><strong>{v.title}</strong><ArrowRight size={14} /></button>)}</div><small>메시지를 보내면 조건을 정리하고, 추천 결과에서 개발자와 유사 프로젝트를 보여 줍니다.</small></div> : <>
                        <div className={styles.conversationIntro}><Sparkles size={17} /><p>아이디어를 차근차근 정리해 보세요.<br /><span>선택한 조건을 바탕으로 동료와 유사 프로젝트를 추천합니다.</span></p></div>
                    </>}
                    {user && <SavedRecommendations key={chatKey} serverId={conversation?.serverId} result={result?.chatKey === chatKey ? result.value : undefined} messages={conversation?.messages} onLoaded={scrollBottom} />}
                    {visibleError.endsWith('항목을 선택해 주세요.') && !!missing.length && !busy && <div className={styles.conditionHint}><SlidersHorizontal size={16} /><div><strong>추천을 위해 조금만 더 알려주세요</strong><p>{missing.map(k => chatRequirementLabels[k]).join(' · ')}을 선택하면 추천을 시작할 수 있어요.</p><button type="button" onClick={showConditions}>조건 직접 설정<ArrowRight size={12} /></button></div></div>}
                    {busy && <div role="status" className={styles.loading}><LoaderCircle size={16} /><span>{task === 'analyze' ? '메시지를 분석하고 조건을 정리하고 있어요…' : '조건을 저장하고 추천 후보를 찾고 있어요…'}</span></div>}
                </div>
                <div className={styles.execution}><span><i />{conversation?.pendingRecommendation ? '이전 요청을 확인하면 같은 추천을 다시 만들지 않아요.' : recommendationUnchanged ? '같은 조건의 추천이 있어요. 추천 조건을 변경해 주세요.' : missing.length ? '조건을 설정하면 추천을 시작할 수 있어요.' : '조건이 준비됐어요. 새로운 동료를 찾아보세요.'}</span><button type="button" disabled={busy || recommendationUnchanged} onClick={recommend}><Sparkles size={13} />{conversation?.pendingRecommendation ? '이전 추천 확인/재시도' : '조건으로 추천 받기'}</button></div>
                {(visibleError || conversation?.pendingAnalysisKey && !busy) && <div role="alert" className={styles.error}>{visibleError}{(retryAnalysis || conversation?.pendingAnalysisKey) && <button type="button" onClick={() => void runAnalysis()}>분석 다시 시도</button>}</div>}
                <ChatComposer used={chatMessageLength(conversation?.messages || [])} value={text} busy={busy} signedIn={!!user} onChange={changeText} onSend={send} />
            </section>
            <aside id="matching-conditions" aria-label="추천 조건" className={styles.conditions} data-mobile-open={drawer === 'conditions'} role={drawer === 'conditions' ? 'dialog' : undefined} aria-modal={drawer === 'conditions' ? true : undefined}><ChatConditions key={chatKey} busy={busy} signedIn={!!user} retry={!!conversation?.pendingRecommendation} onRecommend={recommend} onClose={() => { setConditions(false); setDrawer(null); }} /></aside>
        </div>
        {deleting && <Modal title="대화를 삭제할까요?" onClose={() => { if (!busy) setDeleting(null); }}><p>대화와 메시지·조건 및 저장된 추천 이력이 함께 삭제됩니다.</p><div className={styles.modalActions}><Button variant="secondary" disabled={busy} onClick={() => setDeleting(null)}>취소</Button><Button disabled={busy} onClick={async () => { const key = chatKey; setBusyKey(key); try { await deleteAiChat(deleting); setDeleting(null); } catch (e) { if (currentKey.current === key) setError(e instanceof Error ? e.message : '삭제하지 못했습니다.'); setDeleting(null); } finally { setBusyKey(null); } }}>대화 삭제</Button></div></Modal>}
    </div>;
}
