'use client';
import styles from './ChatWindow.module.css';
import { useLayoutEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import Link from 'next/link';
import { Send } from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { useProfiles } from '@/lib/state/profileStore';
import { Button } from '@/components/common/Button';
import { Textarea } from '@/components/common/Input';
import type { ChatMessage } from '@/types/messaging';

export type ChatSendResult = { ok: boolean; error?: string };

/** 서버 대화에서만 넘긴다. 없으면 Mock 대화처럼 메시지가 바뀔 때마다 맨 아래로 이동한다. */
export interface ChatHistoryControls {
    hasOlder: boolean;
    loadingOlder: boolean;
    onLoadOlder: () => void;
    onBottomChange: (atBottom: boolean) => void;
}

const BOTTOM_MARGIN = 40;
const MOCK_FOOTER = 'Mock 대화 · 실제 서버 전송 없음';

/** 서버 대화에서만 넘긴다. 없으면 입력 중 표시 영역을 그리지 않아 Mock 대화의 화면이 바뀌지 않는다. */
export interface ChatTypingControls {
    /** 입력 중인 사람의 안내 문구. 없으면 null이다. bubble 표시에서는 화면 읽기 프로그램만 읽는다. */
    text: string | null;
    onTyping: () => void;
    /**
     * text(기본): 대화 기록 밖의 높이 고정 영역에 문구를 쓴다. 누가 입력 중인지 보여야 하는 프로젝트 채팅이 쓴다.
     * bubble: 대화 기록 맨 아래에 상대 말풍선을 띄우고 그 안에서 점 세 개가 움직인다. 1:1 대화가 쓴다.
     */
    display?: 'text' | 'bubble';
}

export function ChatWindow({ channelKey, messages, members, disabled = false, historyContent, historyVersion, onSend, author, footerText = MOCK_FOOTER, history, typing }: {
    channelKey: string;
    messages: ChatMessage[];
    members: string[];
    disabled?: boolean;
    historyContent?: ReactNode;
    historyVersion?: number;
    onSend: (body: string) => ChatSendResult | Promise<ChatSendResult>;
    /** 작성자 이름과 링크. href가 null이면 링크 없이 이름만 표시한다. 없으면 Mock 프로필에서 찾는다. */
    author?: (senderId: string) => { name: string; href: string | null };
    footerText?: string;
    history?: ChatHistoryControls;
    typing?: ChatTypingControls;
}) {
    const { user } = useAuth();
    const { state } = useProfiles();
    const [body, setBody] = useState('');
    const [error, setError] = useState('');
    const [sending, setSending] = useState(false);
    const [shownChannel, setShownChannel] = useState(channelKey);
    const sendingNow = useRef(false);
    const currentChannel = useRef(channelKey);
    const log = useRef<HTMLDivElement>(null);
    const bottom = useRef<HTMLDivElement>(null);
    const atBottom = useRef(true);
    const firstId = messages[0]?.id;
    const lastId = messages.at(-1)?.id;
    const lastSender = messages.at(-1)?.senderId;
    const seen = useRef<{ channelKey: string; firstId?: string; lastId?: string } | null>(null);
    const olderBase = useRef<{ height: number; top: number } | null>(null);
    const typingBubble = typing?.display === 'bubble' && typing.text !== null;

    useLayoutEffect(() => {
        currentChannel.current = channelKey;
        if (!history) {
            bottom.current?.scrollIntoView({ block: 'nearest' });
            return;
        }
        const box = log.current;
        // 폴링으로 메시지를 받으므로 페이지 전체가 움직이지 않게 대화 영역 안에서만 내린다.
        const toBottom = () => { if (box) box.scrollTop = box.scrollHeight; };
        const before = seen.current;
        seen.current = { channelKey, firstId, lastId };
        if (!before || before.channelKey !== channelKey) {
            atBottom.current = true;
            toBottom();
        } else if (before.lastId === lastId && before.firstId !== firstId) {
            // 이전 기록을 불러와도 보고 있던 메시지가 제자리에 있게 한다.
            const base = olderBase.current;
            olderBase.current = null;
            if (box && base) box.scrollTop = base.top + box.scrollHeight - base.height;
        } else if (before.lastId !== lastId && (atBottom.current || lastSender === user?.id)) {
            // 위로 올려 읽는 중에는 상대 메시지가 와도 움직이지 않는다. 내가 보낸 메시지는 따라간다.
            toBottom();
        }
    }, [channelKey, messages.length, historyVersion, firstId, lastId]);

    useLayoutEffect(() => {
        // 입력 중 말풍선이 생기거나 사라질 때 맨 아래를 보고 있었다면 말풍선이 보이게 따라간다. 위로 올려 읽는 중에는 움직이지 않는다.
        const box = log.current;
        if (history && box && atBottom.current) box.scrollTop = box.scrollHeight;
    }, [typingBubble]);

    if (shownChannel !== channelKey) {
        setShownChannel(channelKey);
        setBody('');
        setError('');
    }

    const onScroll = history ? () => {
        const box = log.current;
        if (!box) return;
        const next = box.scrollHeight - box.scrollTop - box.clientHeight < BOTTOM_MARGIN;
        atBottom.current = next;
        history.onBottomChange(next);
    } : undefined;

    // 누르는 순간의 높이로 계산해야 글꼴 적용이나 창 크기 변경으로 높이가 달라져도 어긋나지 않는다.
    const loadOlder = () => {
        const box = log.current;
        if (box) olderBase.current = { height: box.scrollHeight, top: box.scrollTop };
        history?.onLoadOlder();
    };

    async function submit(event: FormEvent) {
        event.preventDefault();
        if (sendingNow.current) return;
        sendingNow.current = true;
        setSending(true);
        setError('');
        const channel = channelKey;
        try {
            const result = await onSend(body);
            if (currentChannel.current !== channel) return;
            if (result.ok) setBody('');
            else setError(result.error || '메시지를 보내지 못했습니다.');
        } catch {
            setError('메시지를 보내지 못했습니다.');
        } finally {
            sendingNow.current = false;
            setSending(false);
        }
    }

    return <div className={[styles.scope, "chat-window"].join(" ")}>
        <div className={[styles.scope, "message-history"].join(" ")} role="log" aria-label="메시지 대화" aria-live="polite" ref={log} onScroll={onScroll}>
            {history?.hasOlder && <div className={[styles.scope, "older-messages"].join(" ")}>
                <Button type="button" variant="secondary" disabled={history.loadingOlder} onClick={loadOlder}>
                    {history.loadingOlder ? '불러오는 중…' : '이전 메시지 더 보기'}
                </Button>
            </div>}
            {!messages.length && <div className={[styles.scope, "chat-empty"].join(" ")}>첫 메시지로 대화를 시작해 보세요.</div>}
            {messages.map(m => {
                if (m.kind === 'system') return <p className={[styles.scope, "system-message"].join(" ")} key={m.id}>{m.body}</p>;
                const mine = m.senderId === user?.id;
                const others = members.filter(id => id !== m.senderId);
                const read = others.filter(id => m.readBy.includes(id)).length;
                const writer = author ? author(m.senderId) : { name: state.profiles[m.senderId]?.nickname || '사용자', href: `/users/${m.senderId}` };
                return <article className={[styles.scope, `chat-message ${mine ? 'mine' : ''}`].join(" ")} key={m.id}>
                    {writer.href
                        ? <Link className={[styles.scope, "message-author"].join(" ")} href={writer.href}>{writer.name}</Link>
                        : <span className={[styles.scope, "message-author"].join(" ")}>{writer.name}</span>}
                    <div className={[styles.scope, "message-bubble"].join(" ")}>
                        {m.kind === 'proposal' && <div className={[styles.scope, "proposal-message-title"].join(" ")}><strong>{m.proposalTitle}</strong></div>}
                        <p className={[styles.scope, "preserve-text"].join(" ")}>{m.body}</p>
                    </div>
                    <div className={[styles.scope, "message-meta"].join(" ")}>
                        <time>{new Date(m.createdAt).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}</time>
                        {mine && <span>{others.length === 1 ? (read ? '읽음' : '안 읽음') : others.length ? `읽음 ${read}/${others.length}` : '전송됨'}</span>}
                    </div>
                </article>;
            })}
            {historyContent}
            {/* 보이는 말풍선은 대화 기록이 소리 내어 읽지 않게 숨기고, 안내는 아래의 상태 영역이 맡는다. */}
            {typingBubble && <article className={[styles.scope, "chat-message typing-message"].join(" ")} aria-hidden="true">
                <div className={[styles.scope, "message-bubble typing-bubble"].join(" ")}>
                    {/* 메시지 글자와 같은 문단 규칙을 써서 한 줄 메시지와 높이가 같다. */}
                    <p className={[styles.scope, "preserve-text typing-dots"].join(" ")}>
                        <span className={[styles.scope, "typing-dot"].join(" ")}/>
                        <span className={[styles.scope, "typing-dot"].join(" ")}/>
                        <span className={[styles.scope, "typing-dot"].join(" ")}/>
                    </p>
                </div>
            </article>}
            <div ref={bottom}/>
        </div>
        {/* 대화 기록(role="log") 밖에 두어 스크린 리더가 기록 전체를 다시 읽지 않게 하고, 높이를 고정해 나타날 때 화면이 움직이지 않게 한다. */}
        {typing && (typing.display === 'bubble'
            ? <p className={[styles.scope, "typing-status"].join(" ")} role="status">{typing.text ?? ''}</p>
            : <p className={[styles.scope, "typing-indicator"].join(" ")}>{typing.text ?? ''}</p>)}
        <form className={[styles.scope, "message-compose"].join(" ")} onSubmit={submit}>
            <Textarea aria-label="메시지 입력" rows={3} value={body} maxLength={2000} onChange={e => {
                setBody(e.target.value);
                if (e.target.value.trim()) typing?.onTyping();
            }} placeholder={disabled ? '메시지를 작성할 수 없는 채팅방입니다.' : '동료에게 메시지를 남겨보세요.'} disabled={disabled}/>
            <div className={[styles.scope, "message-compose-bottom"].join(" ")}>
                <span>{footerText}</span>
                <Button disabled={disabled || sending} type="submit"><Send size={14}/>메시지 보내기</Button>
            </div>
            {error && <p className={[styles.scope, "form-error"].join(" ")} role="alert">{error}</p>}
        </form>
    </div>;
}
