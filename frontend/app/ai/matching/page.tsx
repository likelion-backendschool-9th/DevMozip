'use client';
import styles from './page.module.css';
import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useNavigation as useNavigate, useCurrentLocation as useLocation } from '@/lib/navigation';
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
    const { usage, conversations, conversation, newChat, selectChat, deleteChat, sendChat } = useHome();
    const location = useLocation();
    const navigate = useNavigate();
    const handoff = useRef<string | null>(null);
    const [text, setText] = useState('');
    const [error, setError] = useState('');
    const [deleting, setDeleting] = useState<string | null>(null);
    const [historyOpen, setHistoryOpen] = useState(false);
    const bottom = useRef<HTMLDivElement>(null);
    const premium = user?.role === 'premium' || user?.role === 'admin';
    // 대화나 계정이 바뀌면 입력과 오류를 비운다(렌더 중 이전 값과 비교).
    const chatKey = `${conversation?.id}|${user?.id}`;
    const [shownChat, setShownChat] = useState(chatKey);
    if (shownChat !== chatKey) {
        setShownChat(chatKey);
        setText('');
        setError('');
    }
    useEffect(() => {
        if (bottom.current) bottom.current.scrollTop = bottom.current.scrollHeight;
    }, [conversation?.id, conversation?.messages.length]);
    const send = () => {
        const result = sendChat(text);
        if (result.ok) setText('');
        setError(result.error || '');
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
                <span className={[styles.scope, 'outline-badge'].join(' ')}>Mock 추천 · API 연결 없음</span>
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
                            {user ? '현재 계정의 대화만 표시합니다.' : '비로그인 대화는 별도 데모 공간입니다.'}
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
                        <strong>
                            {usage.month} · {premium ? '프리미엄' : '무료'} 추천
                        </strong>
                        {user ? (
                            <>
                                <p>
                                    남은 추천 <b>{usage.remaining}</b> / {usage.limit}회
                                </p>
                                <small>{usage.used}회 사용 · 결과 생성만 차감</small>
                                <progress
                                    max={usage.limit}
                                    value={Math.min(usage.used, usage.limit)}
                                    aria-label="이번 달 추천 사용량"
                                />
                            </>
                        ) : (
                            <Link href="/login">로그인하고 추천 받기</Link>
                        )}
                        <small>한국 시각 매월 새 한도 적용</small>
                        {!premium && <Link href="/subscription">프리미엄 월 100회 안내 →</Link>}
                    </div>
                </aside>
                <section className={[styles.scope, 'ai-chat-main'].join(' ')} aria-label="AI 추천 대화">
                    <header className={[styles.scope, 'ai-conversation-title'].join(' ')}>
                        <strong>{conversation?.title || '아이디어에서 시작하는 새로운 팀'}</strong>
                        <span>일반 메시지·추가 질문 무료</span>
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
                        <ChatConditions key={`${user?.id || 'guest'}:${conversation?.id || 'new'}`} />
                        {user && usage.remaining === 0 && (
                            <div role="status" className={[styles.scope, 'matching-limit'].join(' ')}>
                                이번 달 추천 횟수를 모두 사용했습니다. 추가 질문은 계속 이용할 수 있어요.
                                {!premium && <Link href="/subscription">프리미엄 업그레이드 →</Link>}
                            </div>
                        )}
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
                                onChange={e => setText(e.target.value)}
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
                            Gemini 연결 없는 Mock 규칙 기반 추천 · 적합도는 데모 계산 점수 · Enter 전송 / Shift+Enter
                            줄바꿈
                        </small>
                    </div>
                </section>
            </div>
            {deleting && (
                <Modal title="대화를 삭제할까요?" onClose={() => setDeleting(null)}>
                    <p>메시지와 추천 결과가 삭제됩니다. 이미 사용한 추천 횟수는 복원되지 않습니다.</p>
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
