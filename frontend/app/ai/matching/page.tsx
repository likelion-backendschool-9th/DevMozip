'use client';
import styles from './page.module.css';
import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useNavigation as useNavigate } from '@/lib/navigation';
import { Plus, Send, Sparkles, Trash2 } from 'lucide-react';
import { useHome } from '@/lib/state/homeStore';
import { useAuth } from '@/lib/state/authStore';
import { ChatConditions } from '@/app/ai/matching/_components/ChatConditions';
import { ChatResults } from '@/app/ai/matching/_components/ChatResults';
import { Button } from '@/components/common/Button';
import { Textarea } from '@/components/common/Input';
import { Modal } from '@/components/common/Modal';
import { chatWelcome } from '@/mocks/matchingChat';
export default function MatchingPage() {
    const { user } = useAuth();
    const { conversations, conversation, newChat, selectChat, deleteChat, sendChat, editChatMessage, guestDraft, resumeGuestChat, prepareChatLogin } = useHome();
    const navigate = useNavigate();
    const text = conversation?.draft || '';
    const [error, setError] = useState('');
    const [deleting, setDeleting] = useState<string | null>(null);
    const [historyOpen, setHistoryOpen] = useState(false);
    const bottom = useRef<HTMLDivElement>(null);
    // 입력문은 대화별 로컬 초안에 보존하고 계정 전환 시 오류/삭제 창만 닫는다.
    const chatKey = `${conversation?.id}|${user?.email}`;
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
    const recommend = () => {
        if (!user) {
            const saved = prepareChatLogin();
            if (!saved.ok) { setError(saved.error || ''); return; }
            navigate('/login?next=%2Fai%2Fmatching');
        } else setError('추천 실행은 아직 준비 중입니다. 대화와 조건은 이 브라우저에 보관됩니다.');
    };
    return (
        <div className={[styles.scope, 'container ai-chat-page'].join(' ')}>
            <div className={[styles.scope, 'ai-chat-title'].join(' ')}>
                <div>
                    <div className={[styles.scope, 'eyebrow'].join(' ')}>YOUR NEXT TEAM, ONE CONVERSATION AWAY</div>
                    <h1>
                        <Sparkles size={25} /> AI 팀원 추천
                    </h1>
                </div>
                <span className={[styles.scope, 'outline-badge'].join(' ')}>로컬 초안 · 추천 준비 중</span>
            </div>
            <div className={[styles.scope, 'ai-chat-layout'].join(' ')}>
                <aside
                    className={[styles.scope, 'ai-chat-sidebar'].join(' ')}
                    aria-label="추천 대화 기록"
                    data-history-open={historyOpen}
                >
                    <Button onClick={() => newChat()}>
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
                            {user ? '현재 계정의 로컬 대화만 표시합니다.' : '비로그인 대화는 이 브라우저에 보관됩니다.'}
                        </p>
                        <div className={[styles.scope, 'ai-conversation-list'].join(' ')}>
                            {conversations.map(c => (
                                <div
                                    key={c.id}
                                    className={[styles.scope, c.id === conversation?.id ? 'active' : ''].join(' ')}
                                >
                                    <button
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
                        {!user && <Link href="/login?next=%2Fai%2Fmatching" onClick={event => { event.preventDefault(); recommend(); }}>로그인하고 초안 이어가기</Link>}
                        <small>추천 실행은 준비 중입니다.</small>
                    </div>
                </aside>
                <section className={[styles.scope, 'ai-chat-main'].join(' ')} aria-label="AI 추천 대화">
                    {user && guestDraft && (
                        <div className={[styles.scope, 'auth-hint'].join(' ')}>
                            <p>로그인 전 초안 “{guestDraft.title}”을 {user.email} 계정으로 옮겨 이어갈 수 있습니다.</p>
                            <Button variant="secondary" onClick={() => { const result = resumeGuestChat(); setError(result.error || ''); }}>
                                로그인 전 초안 이어받기
                            </Button>
                        </div>
                    )}
                    <header className={[styles.scope, 'ai-conversation-title'].join(' ')}>
                        <strong>{conversation?.title || '아이디어에서 시작하는 새로운 팀'}</strong>
                        <span>메시지·조건 로컬 저장</span>
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
                                    {m.role === 'user' ? '나' : 'DevMozip · Mock'}
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
                    </div>
                    <div className={[styles.scope, 'ai-chat-controls'].join(' ')}>
                        <ChatConditions key={`${user?.email || 'guest'}:${conversation?.id || 'new'}`} onRecommend={recommend} />
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
                            자동 답변·추천 없이 기록만 보관합니다. Enter 전송 / Shift+Enter 줄바꿈
                        </small>
                    </div>
                </section>
            </div>
            {deleting && (
                <Modal title="대화를 삭제할까요?" onClose={() => setDeleting(null)}>
                    <p>이 브라우저에 보관된 대화와 메시지·조건이 삭제됩니다.</p>
                    <div className={[styles.scope, 'modal-actions'].join(' ')}>
                        <Button variant="secondary" onClick={() => setDeleting(null)}>
                            취소
                        </Button>
                        <Button
                            onClick={() => {
                                deleteChat(deleting);
                                setDeleting(null);
                            }}
                        >
                            대화 삭제
                        </Button>
                    </div>
                </Modal>
            )}
        </div>
    );
}
