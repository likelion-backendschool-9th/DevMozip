'use client';
import styles from './NotificationList.module.css';
import { useState } from 'react';
import Link from 'next/link';
import { Bell, CheckCheck } from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { useMessaging } from '@/lib/state/messagingStore';
import { Button } from '@/components/common/Button';

/**
 * Mock 토글 ON의 통합 알림 목록. 기존 /notifications 화면을 공통 알림 모달과 페이지가 함께 쓰도록 옮긴 것이며 동작은 바꾸지 않았다.
 * 모달(variant="modal")에서는 큰 제목만 숨기고, 링크를 누르면 onNavigate로 모달을 닫는다.
 */
export function MockNotificationList({ variant = 'page', onNavigate }: { variant?: 'page' | 'modal'; onNavigate?: () => void }) {
    const { user } = useAuth();
    const { state, notifications, unreadCount, act } = useMessaging();
    const [filter, setFilter] = useState('all');
    if (!user)
        return (
            <div className={[styles.scope, 'container planned'].join(' ')}>
                <section className={[styles.scope, 'planned-card'].join(' ')}>
                    <h1>로그인 후 알림을 확인해 주세요.</h1>
                    <Link className={[styles.scope, 'button'].join(' ')} href="/login" onClick={onNavigate}>
                        로그인
                    </Link>
                </section>
            </div>
        );
    const readIds = state.notificationReads[user.id] || [];
    const items = notifications.filter(
        n =>
            filter === 'all' ||
            (filter === 'unread' ? !n.resolved && !readIds.includes(n.id) : n.resolved || readIds.includes(n.id)),
    );
    const readAll = (
        <Button variant="secondary" disabled={!unreadCount} onClick={() => act({ type: 'notices-read-all' })}>
            <CheckCheck size={15} />
            모두 읽음
        </Button>
    );
    const content = (
        <>
            {variant === 'page' ? (
                <header className={[styles.scope, 'section-heading'].join(' ')}>
                    <div>
                        <div className={[styles.scope, 'eyebrow'].join(' ')}>YOUR UPDATES</div>
                        <h1>새로운 소식을 확인하세요.</h1>
                        <p role="status">
                            안 읽은 알림 <b>{unreadCount}</b>개
                        </p>
                    </div>
                    {readAll}
                </header>
            ) : (
                <div className={[styles.scope, 'notification-summary'].join(' ')}>
                    <p role="status">
                        안 읽은 알림 <b>{unreadCount}</b>개
                    </p>
                    {readAll}
                </div>
            )}
            <div className={[styles.scope, 'tabs notification-tabs'].join(' ')}>
                {[
                    { value: 'all', label: '전체' },
                    { value: 'unread', label: '안 읽음' },
                    { value: 'read', label: '읽음' },
                ].map(t => (
                    <button
                        key={t.value}
                        className={[styles.scope, filter === t.value ? 'active' : ''].join(' ')}
                        aria-pressed={filter === t.value}
                        onClick={() => setFilter(t.value)}
                    >
                        {t.label}
                    </button>
                ))}
            </div>
            <div className={[styles.scope, 'notification-list'].join(' ')}>
                {items.map(n => {
                    const read = !!n.resolved || readIds.includes(n.id);
                    return (
                        <article
                            key={n.id}
                            className={[styles.scope, `notification-item ${read ? 'read' : ''}`].join(' ')}
                        >
                            <span className={[styles.scope, 'notice-icon'].join(' ')}>
                                <Bell size={18} />
                            </span>
                            <div>
                                <header>
                                    <strong>
                                        {n.title}
                                        {n.resolved && (
                                            <span className={[styles.scope, 'notice-resolved-label'].join(' ')}>
                                                처리 완료
                                            </span>
                                        )}
                                    </strong>
                                    <time>
                                        {new Date(n.createdAt).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })}
                                    </time>
                                </header>
                                <p>{n.body}</p>
                                <div className={[styles.scope, 'notification-item-actions'].join(' ')}>
                                    <Link
                                        href={n.href}
                                        onClick={() => {
                                            act({ type: 'notice-read', id: n.id, read: true });
                                            onNavigate?.();
                                        }}
                                    >
                                        내용 확인 →
                                    </Link>
                                    {!n.resolved && (
                                        <button onClick={() => act({ type: 'notice-read', id: n.id, read: !read })}>
                                            {read ? '안 읽음으로 변경' : '읽음 처리'}
                                        </button>
                                    )}
                                    {!n.resolved &&
                                        n.actions?.map(action => (
                                            <Link
                                                key={action.label}
                                                className={[styles.scope, 'button secondary small'].join(' ')}
                                                href={action.href}
                                                onClick={() => {
                                                    act({ type: 'notice-read', id: n.id, read: true });
                                                    onNavigate?.();
                                                }}
                                            >
                                                {action.label}
                                            </Link>
                                        ))}
                                </div>
                            </div>
                            {!read && (
                                <span className={[styles.scope, 'notice-unread-dot'].join(' ')} aria-label="안 읽음" />
                            )}
                        </article>
                    );
                })}
                {!items.length && (
                    <div className={[styles.scope, 'empty-state'].join(' ')}>표시할 알림이 없습니다.</div>
                )}
            </div>
            <p className={[styles.scope, 'auth-hint'].join(' ')}>
                팔로우·SNS 반응·프로젝트·선택한 팔로잉 소식을 통합한 Mock 알림입니다. 실제 푸시·메일은 전송하지
                않습니다.
            </p>
        </>
    );
    return variant === 'page'
        ? <div className={[styles.scope, 'container notifications-page'].join(' ')}>{content}</div>
        : <div className={styles.scope}>{content}</div>;
}
