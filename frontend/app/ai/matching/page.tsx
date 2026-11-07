'use client';
import styles from './page.module.css';
import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useNavigation as useNavigate } from '@/lib/navigation';
import { Plus, Send, Sparkles, Trash2 } from 'lucide-react';
import { useHome } from '@/lib/state/homeStore';
import { useAuth } from '@/lib/state/authStore';
import { ChatConditions } from '@/app/ai/matching/_components/ChatConditions';
import { SavedRecommendations } from './_components/SavedRecommendations';
import type { AiRecommendation } from '@/types/aiRecommendation';
import { ChatResults } from '@/app/ai/matching/_components/ChatResults';
import { Button } from '@/components/common/Button';
import { Textarea } from '@/components/common/Input';
import { Modal } from '@/components/common/Modal';
import { chatWelcome } from '@/mocks/matchingChat';
export default function MatchingPage() {
    const { user } = useAuth();
    const { conversations, conversation, newChat, selectChat, deleteAiChat, sendChat, editChatMessage, loadAiChats, recommendChat } = useHome();
    const navigate = useNavigate();
    const text = conversation?.draft || '';
    const [error, setError] = useState('');
    const [busyKey, setBusyKey] = useState<string | null>(null);
    const [result, setResult] = useState<{ chatKey: string; value: AiRecommendation }>();
    const [deleting, setDeleting] = useState<string | null>(null);
    const [historyOpen, setHistoryOpen] = useState(false);
    const bottom = useRef<HTMLDivElement>(null);
    // 입력문은 대화별 로컬 초안에 보존하고 계정 전환 시 오류/삭제 창만 닫는다.
    const chatKey = `${conversation?.id}|${user?.email}`;
    const currentChatKey = useRef(chatKey);
    useEffect(() => { currentChatKey.current = chatKey; }, [chatKey]);
    const busy = busyKey === chatKey;
    const [shownChat, setShownChat] = useState(chatKey);
    if (shownChat !== chatKey) {
        setShownChat(chatKey);
        setError('');
        setDeleting(null);
    }
    useEffect(() => {
        if (bottom.current) bottom.current.scrollTop = bottom.current.scrollHeight;
    }, [conversation?.id, conversation?.messages.length]);
    const send = () => {
        const result = sendChat(text);
        setError(result.error || '');
    };
    useEffect(() => {
        if (!user) navigate('/login?next=%2Fai%2Fmatching', { replace: true });
    }, [user, navigate]);
    useEffect(() => {
        if (!user) return;
        let active = true;
        void loadAiChats().catch(e => { if (active) setError(e instanceof Error ? e.message : '대화 기록 조회에 실패했습니다.'); });
        return () => { active = false; };
    }, [user?.email, loadAiChats, user]);
    const recommend = async () => {
        if (busy) return;
        const key = chatKey;
        setBusyKey(key);
        setError('');
        try {
            const value = await recommendChat();
            if (currentChatKey.current === key) setResult({ chatKey: key, value });
        } catch (e) {
            if (currentChatKey.current === key) setError(e instanceof Error ? e.message : '추천 실행에 실패했습니다. 초안은 유지됩니다.');
        } finally { setBusyKey(null); }
    };
    if (!user) return <div className="container"><p>AI 추천 대화는 로그인 후 이용할 수 있습니다.</p><Link href="/login?next=%2Fai%2Fmatching">로그인하기</Link></div>;
    return (
        <div className={[styles.scope, 'container ai-chat-page'].join(' ')}>
            <div className={[styles.scope, 'ai-chat-title'].join(' ')}>
                <div>
                    <div className={[styles.scope, 'eyebrow'].join(' ')}>YOUR NEXT TEAM, ONE CONVERSATION AWAY</div>
                    <h1>
                        <Sparkles size={25} /> AI 팀원 추천
                    </h1>
                </div>
                <span className={[styles.scope, 'outline-badge'].join(' ')}>조건 기반 추천</span>
            </div>
            <div className={[styles.scope, 'ai-chat-layout'].join(' ')}>
                <aside
                    className={[styles.scope, 'ai-chat-sidebar'].join(' ')}
                    aria-label="추천 대화 기록"
                    data-history-open={historyOpen}
                >
                    <Button disabled={busy} onClick={() => newChat()}>
                        <Plus size={16} />새 대화 시작
                    </Button>
                    <button
                        className={[styles.scope, 'ai-history-toggle'].join(' ')}
                        type="button"
                        aria-expanded={historyOpen}
                        aria-controls="matching-history"
                        onClick={() => setHistoryOpen(v => !v)}
                    >
                        대화 기록 {historyOpen ? '접기 −' : '펼치기 +'}
                    </button>
                    <div id="matching-history" className={[styles.scope, 'ai-history-block'].join(' ')}>
                        <h2>이전 대화 기록</h2>
                        <p className={[styles.scope, 'auth-hint'].join(' ')}>
                            현재 계정의 초안과 저장된 대화를 표시합니다.
                        </p>
                        <div className={[styles.scope, 'ai-conversation-list'].join(' ')}>
                            {conversations.map(c => (
                                <div
                                    key={c.id}
                                    className={[styles.scope, c.id === conversation?.id ? 'active' : ''].join(' ')}
                                >
                                    <button
                                        disabled={busy}
                                        className={[styles.scope, 'ai-history-open'].join(' ')}
                                        onClick={() => {
                                            selectChat(c.id);
                                            setHistoryOpen(false);
                                        }}
                                        aria-current={c.id === conversation?.id ? 'true' : undefined}
                                    >
                                        <strong>{c.title}</strong>
                                        <time dateTime={c.createdAt}>
                                            {new Date(c.createdAt).toLocaleDateString('ko-KR', {
                                                timeZone: 'Asia/Seoul',
                                            })}
                                        </time>
                                    </button>
                                    <button
                                        className={[styles.scope, 'icon-button'].join(' ')}
                                        disabled={busy}
                                        aria-label={`${c.title} 대화 삭제`}
                                        onClick={() => setDeleting(c.id)}
                                    >
                                        <Trash2 size={14} />
                                    </button>
                                </div>
                            ))}
                        </div>
                        {!conversations.length && (
                            <p className={[styles.scope, 'auth-hint'].join(' ')}>첫 아이디어로 대화를 시작해 보세요.</p>
                        )}
                    </div>
                    <div className={[styles.scope, 'ai-usage'].join(' ')}>
                        <strong>대화·조건 임시 보관</strong>
                        <p>메시지와 조건을 기록해도 추천 횟수는 차감되지 않습니다.</p>
                        <small>추천 버튼을 누르면 대화와 조건이 서버에 저장됩니다.</small>
                    </div>
                </aside>
                <section className={[styles.scope, 'ai-chat-main'].join(' ')} aria-label="AI 추천 대화" data-saved={!!conversation?.serverId}>
                    <header className={[styles.scope, 'ai-conversation-title'].join(' ')}>
                        <strong>{conversation?.title || '아이디어에서 시작하는 새로운 팀'}</strong>
                        <span>{conversation?.serverId ? '저장된 대화 · 변경은 다음 추천 시 반영' : '메시지·조건 로컬 저장'}</span>
                    </header>
                    <div
                        ref={bottom}
                        className={[styles.scope, 'ai-chat-messages'].join(' ')}
                        role="log"
                        aria-label="추천 메시지"
                    >
                        <article className={[styles.scope, 'ai-message assistant'].join(' ')}>
                            <span className={[styles.scope, 'ai-message-label'].join(' ')}>
                                <Sparkles size={15} /> DevMozip
                            </span>
                            <p>{chatWelcome}</p>
                        </article>
                        {conversation?.messages.map(m => (
                            <article key={m.id} className={[styles.scope, `ai-message ${m.role}`].join(' ')}>
                                <span className={[styles.scope, 'ai-message-label'].join(' ')}>
                                    {m.role === 'user' ? '나' : m.result ? 'DevMozip · Mock' : 'DevMozip'}
                                </span>
                                <p>{m.text}</p>
                                {m.role === 'user' && m.requirements && (
                                    <div className={[styles.scope, 'ai-sent-conditions'].join(' ')}>
                                        {[
                                            ...m.requirements.fields,
                                            ...m.requirements.interests,
                                            ...m.requirements.stacks,
                                            ...m.requirements.roles.map(r => r.name + ' ' + r.count + '명'),
                                            m.requirements.career,
                                            m.requirements.duration,
                                        ]
                                            .filter(Boolean)
                                            .map((v, i) => (
                                                <span key={i}>{v}</span>
                                            ))}
                                    </div>
                                )}
                                {m.result && <ChatResults result={m.result} />}
                            </article>
                        ))}
                        <SavedRecommendations key={`${chatKey}:${result?.chatKey === chatKey ? result.value.requestId : ""}`} serverId={conversation?.serverId} result={result?.chatKey === chatKey ? result.value : undefined} />
                    </div>
                    <fieldset disabled={busy} className={[styles.scope, 'ai-chat-controls'].join(' ')}>
                        <ChatConditions busy={busy} retry={!!conversation?.pendingRecommendation} key={`${user?.email || 'guest'}:${conversation?.id || 'new'}`} onRecommend={recommend} />
                        {conversation?.pendingRecommendation && !busy && <p role="status">이전 추천의 완료 여부를 확인해 주세요. 변경한 조건은 다음 신규 추천에 반영됩니다.</p>}
                        <form
                            className={[styles.scope, 'ai-chat-composer'].join(' ')}
                            onSubmit={e => {
                                e.preventDefault();
                                send();
                            }}
                        >
                            <Textarea
                                aria-label="AI에게 메시지"
                                rows={2}
                                maxLength={1500}
                                value={text}
                                onChange={e => editChatMessage(e.target.value)}
                                placeholder="예: 웹과 AI 분야, 프론트엔드 2명과 백엔드 1명, 경력 무관, 3개월을 생각해요."
                                onKeyDown={e => {
                                    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
                                        e.preventDefault();
                                        if (text.trim()) send();
                                    }
                                }}
                            />
                            <Button type="submit" disabled={!text.trim()} aria-label="메시지 전송">
                                <Send size={18} />
                            </Button>
                        </form>
                        {error && (
                            <p role="alert" className={[styles.scope, 'form-error'].join(' ')}>
                                {error}
                            </p>
                        )}
                        <small className={[styles.scope, 'ai-disclaimer'].join(' ')}>
                            자연어 자동 분석 없이 직접 선택한 조건으로 추천합니다. Enter 전송 / Shift+Enter 줄바꿈
                        </small>
                    </fieldset>
                </section>
            </div>
            {deleting && (
                <Modal title="대화를 삭제할까요?" onClose={() => setDeleting(null)}>
                    <p>대화와 메시지·조건 및 서버에 저장된 추천 이력이 함께 삭제됩니다.</p>
                    <div className={[styles.scope, 'modal-actions'].join(' ')}>
                        <Button variant="secondary" onClick={() => setDeleting(null)}>
                            취소
                        </Button>
                        <Button
                            onClick={async () => {
                                const key = chatKey;
                                setBusyKey(key);
                                try { await deleteAiChat(deleting); setDeleting(null); }
                                catch (e) { if (currentChatKey.current === key) setError(e instanceof Error ? e.message : '삭제에 실패했습니다.'); setDeleting(null); }
                                finally { setBusyKey(null); }
                            }}
                            disabled={busy}
                        >
                            대화 삭제
                        </Button>
                    </div>
                </Modal>
            )}
        </div>
    );
}
