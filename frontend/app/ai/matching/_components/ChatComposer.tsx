'use client';
import { ArrowUp, CornerDownLeft } from 'lucide-react';
import { useEffect, useRef } from 'react';
import { CHAT_MESSAGE_LIMIT, CHAT_TOTAL_LIMIT } from '@/lib/matching/matchingChatService';
import styles from './ChatComposer.module.css';
export function ChatComposer({ value, busy, signedIn, used = 0, onChange, onSend }: {
    used?: number; value: string; busy: boolean; signedIn: boolean; onChange: (text: string) => void; onSend: () => void;
}) {
    const input = useRef<HTMLTextAreaElement>(null);
    useEffect(() => {
        if (!input.current) return;
        input.current.style.height = 'auto';
        input.current.style.height = `${Math.min(input.current.scrollHeight, 144)}px`;
    }, [value]);
    const total = used + value.trim().length + (used && value.trim() ? 2 : 0);
    const overLimit = value.length > CHAT_MESSAGE_LIMIT || total > CHAT_TOTAL_LIMIT;
    return <div className={styles.container}>
        <form className={styles.composer} onSubmit={e => { e.preventDefault(); if (value.trim() && !busy && !overLimit) onSend(); }}>
            <textarea ref={input} aria-label="AI에게 메시지" rows={1} maxLength={CHAT_MESSAGE_LIMIT} aria-describedby="matching-message-length" aria-invalid={overLimit} disabled={busy} value={value}
                onChange={e => onChange(e.target.value)} placeholder="프로젝트 아이디어나 찾고 있는 동료를 이야기해 주세요."
                onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) { e.preventDefault(); if (value.trim() && !busy && !overLimit) onSend(); } }} />
            <div className={styles.bottom}><span><CornerDownLeft size={12} />Enter 전송 · Shift+Enter 줄바꿈</span><button type="submit" disabled={busy || !value.trim() || overLimit} aria-label={signedIn ? '메시지 전송' : '로그인하고 메시지 이어가기'}><ArrowUp size={19} /></button></div>
        </form>
        <p id="matching-message-length" role={overLimit ? 'alert' : undefined}>{value.length.toLocaleString()} / 1,500자 · 대화 {total.toLocaleString()} / 10,000자 (메시지 구분 줄바꿈 포함) · 남은 용량 {Math.max(0, CHAT_TOTAL_LIMIT - total).toLocaleString()}자{overLimit && ' — 전송 한도를 초과했습니다. 입력을 줄이거나 새 대화를 시작해 주세요.'}</p>
        <p>{signedIn ? '메시지는 자동 분석되지 않습니다. 오른쪽에서 조건을 선택해 추천을 시작하세요.' : '입력은 서버에 저장되지 않으며, 로그인 후 메시지로 이어집니다.'}</p>
    </div>;
}
