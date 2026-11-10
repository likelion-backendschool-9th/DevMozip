'use client';
import { MessageSquare, Plus, Trash2, X } from 'lucide-react';
import type { MatchingConversation } from '@/types/matchingChat';
import styles from './ChatSidebar.module.css';
export function ChatSidebar({ conversations, activeId, busy, signedIn, onNew, onSelect, onDelete, onClose }: {
    conversations: MatchingConversation[]; activeId?: string; busy: boolean; signedIn: boolean;
    onNew: () => void; onSelect: (id: string) => void; onDelete: (id: string) => void; onClose: () => void;
}) {
    return <div className={styles.sidebar}>
        <div className={styles.heading}><span>대화 기록</span><button type="button" aria-label="대화 사이드바 접기" onClick={onClose}><X size={17} /></button></div>
        <button type="button" className={styles.newChat} onClick={onNew} disabled={busy}><Plus size={18} />새 대화 시작</button>
        <div className={styles.label}>최근 대화 <span>{conversations.length}</span></div>
        <div className={styles.list}>
            {conversations.map(c => <div key={c.id} className={`${styles.item} ${c.id === activeId ? styles.active : ''}`}>
                <button type="button" className={styles.open} onClick={() => onSelect(c.id)} disabled={busy} aria-current={c.id === activeId ? 'true' : undefined}>
                    <MessageSquare size={16} /><span><strong>{c.title}</strong><time dateTime={c.createdAt}>{new Date(c.createdAt).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}</time></span>
                </button>
                <button type="button" className={styles.delete} aria-label={`${c.title} 대화 삭제`} onClick={() => onDelete(c.id)} disabled={busy}><Trash2 size={14} /></button>
            </div>)}
            {!conversations.length && <div className={styles.empty}><MessageSquare size={24} /><p>{signedIn ? '아직 대화가 없어요' : '로그인하면 대화를 이어갈 수 있어요'}</p><small>아이디어를 적고 첫 대화를 시작해 보세요.</small></div>}
        </div>
        <div className={styles.tip}><span className={styles.dot} />나에게 맞는 팀, 한 걸음씩<p>아이디어를 기록하고 조건을 정리해 함께할 동료를 찾아보세요.</p></div>
    </div>;
}
