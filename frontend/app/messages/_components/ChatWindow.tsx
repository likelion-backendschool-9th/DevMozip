'use client';
import styles from './ChatWindow.module.css';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import Link from 'next/link';
import { Send } from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { useProfiles } from '@/lib/state/profileStore';
import { Button } from '@/components/common/Button';
import { Textarea } from '@/components/common/Input';
import type { ChatMessage } from '@/types/messaging';
export function ChatWindow({ channelKey, messages, members, disabled = false, historyContent, historyVersion, onSend }: {
    channelKey: string;
    messages: ChatMessage[];
    members: string[];
    disabled?: boolean;
    historyContent?: ReactNode;
    historyVersion?: number;
    onSend: (body: string) => {
        ok: boolean;
        error?: string;
    };
}) { const { user } = useAuth(); const { state } = useProfiles(); const [body, setBody] = useState(''); const [error, setError] = useState(''); const bottom = useRef<HTMLDivElement>(null); useEffect(() => { bottom.current?.scrollIntoView({ block: 'nearest' }); }, [channelKey, messages.length, historyVersion]); const [shownChannel, setShownChannel] = useState(channelKey); if (shownChannel !== channelKey) { setShownChannel(channelKey); setBody(''); setError(''); } return <div className={[styles.scope, "chat-window"].join(" ")}><div className={[styles.scope, "message-history"].join(" ")} role="log" aria-label="메시지 대화" aria-live="polite">{!messages.length && <div className={[styles.scope, "chat-empty"].join(" ")}>첫 메시지로 대화를 시작해 보세요.</div>}{messages.map(m => { if (m.kind === 'system')
    return <p className={[styles.scope, "system-message"].join(" ")} key={m.id}>{m.body}</p>; const mine = m.senderId === user?.id; const others = members.filter(id => id !== m.senderId); const read = others.filter(id => m.readBy.includes(id)).length; return <article className={[styles.scope, `chat-message ${mine ? 'mine' : ''}`].join(" ")} key={m.id}><Link className={[styles.scope, "message-author"].join(" ")} href={`/users/${m.senderId}`}>{state.profiles[m.senderId]?.nickname || '사용자'}</Link><div className={[styles.scope, "message-bubble"].join(" ")}>{m.kind === 'proposal' && <div className={[styles.scope, "proposal-message-title"].join(" ")}><strong>{m.proposalTitle}</strong></div>}<p className={[styles.scope, "preserve-text"].join(" ")}>{m.body}</p></div><div className={[styles.scope, "message-meta"].join(" ")}><time>{new Date(m.createdAt).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}</time>{mine && <span>{others.length === 1 ? (read ? '읽음' : '안 읽음') : others.length ? `읽음 ${read}/${others.length}` : '전송됨'}</span>}</div></article>; })}{historyContent}<div ref={bottom}/></div><form className={[styles.scope, "message-compose"].join(" ")} onSubmit={e => { e.preventDefault(); setError(''); const result = onSend(body); if (result.ok) {
    setBody('');
}
else
    setError(result.error || '메시지를 보내지 못했습니다.'); }}><Textarea aria-label="메시지 입력" rows={3} value={body} maxLength={2000} onChange={e => setBody(e.target.value)} placeholder={disabled ? '메시지를 작성할 수 없는 채팅방입니다.' : '동료에게 메시지를 남겨보세요.'} disabled={disabled}/><div className={[styles.scope, "message-compose-bottom"].join(" ")}><span>Mock 대화 · 실제 서버 전송 없음</span><Button disabled={disabled} type="submit"><Send size={14}/>메시지 보내기</Button></div>{error && <p className={[styles.scope, "form-error"].join(" ")} role="alert">{error}</p>}</form></div>; }
