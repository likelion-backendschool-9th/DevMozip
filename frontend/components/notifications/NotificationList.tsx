'use client';
import styles from './NotificationList.module.css';
import { useEffect, type MouseEvent } from 'react';
import Link from 'next/link';
import { Bell, CheckCheck } from 'lucide-react';
import { Button } from '@/components/common/Button';
import { useNavigation } from '@/lib/navigation';
import { displayedAsRead, formatNotificationTime, reviewStatusLabel } from '@/lib/notifications/notificationApi';
import { useAuth } from '@/lib/state/authStore';
import {
    loadMoreNotifications, loadNotifications, openNotification, readAllNotifications, setNotificationRead, useNotifications,
    type NotificationListState,
} from '@/lib/state/notificationStore';
import type { ApiNotification, NotificationTab } from '@/types/notificationApi';

// Mock 토글 OFF의 서버 알림 목록(WBS 12.1·12.2·12.5). 공통 알림 모달과 /notifications 페이지가 함께 쓴다.
// 아이콘·시각·처리 완료 표시·안 읽음 점은 Mock 화면의 형식을 따른다.

const tabs: { value: NotificationTab; label: string }[] = [
    { value: 'all', label: '전체' },
    { value: 'unread', label: '안 읽음' },
    { value: 'read', label: '읽음' },
];
const cls = (...names: string[]) => [styles.scope, ...names].join(' ');

export interface NotificationListViewProps {
    variant: 'page' | 'modal';
    list: NotificationListState;
    unreadCount: number;
    onTab: (tab: NotificationTab) => void;
    onRetry: () => void;
    onMore: () => void;
    onToggleRead: (notification: ApiNotification) => void;
    onReadAll: () => void;
    onOpen: (notification: ApiNotification) => void;
}

/** 새 탭 열기 등 수정 키를 누른 클릭은 브라우저 기본 동작에 맡긴다. */
function plainClick(event: MouseEvent) {
    return event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey;
}

function NotificationItem({ notification: n, pending, onToggleRead, onOpen }: {
    notification: ApiNotification; pending: boolean;
    onToggleRead: (notification: ApiNotification) => void; onOpen: (notification: ApiNotification) => void;
}) {
    const read = displayedAsRead(n);
    const open = (event: MouseEvent) => {
        if (!plainClick(event)) return;
        event.preventDefault();
        onOpen(n);
    };
    // 리뷰 알림(12.5): 작성 대기는 만료 시각과 "팀원 리뷰 작성" 버튼, 작성 완료·기간 만료는 처리 완료와 상태를 보여 준다.
    const review = n.review;
    return (
        <article className={cls(`notification-item ${read ? 'read' : ''}`)}>
            <span className={cls('notice-icon')}>
                <Bell size={18} />
            </span>
            <div>
                <header>
                    <strong>
                        {n.title}
                        {n.resolved && <span className={cls('notice-resolved-label')}>처리 완료</span>}
                        {review?.status === 'PENDING' && <span className={cls('notification-review-status')}>작성 대기</span>}
                    </strong>
                    <time dateTime={n.createdAt}>{formatNotificationTime(n.createdAt)}</time>
                </header>
                <p>{n.body}</p>
                {review && (
                    <span className={cls('notification-review-meta')}>
                        {review.status === 'PENDING'
                            ? `작성 만료 ${formatNotificationTime(review.expiresAt)}`
                            : `${reviewStatusLabel(review.status)} · 리뷰 화면에서 상태를 확인할 수 있어요.`}
                    </span>
                )}
                <div className={cls('notification-item-actions')}>
                    {n.actionUrl ? (
                        <a href={n.actionUrl} onClick={open}>내용 확인 →</a>
                    ) : (
                        <span>이동할 수 없는 알림입니다.</span>
                    )}
                    {!n.resolved && (
                        <button type="button" disabled={pending} onClick={() => onToggleRead(n)}>
                            {n.read ? '안 읽음으로 변경' : '읽음 처리'}
                        </button>
                    )}
                    {review?.status === 'PENDING' && n.actionUrl && (
                        <a className={cls('button secondary small')} href={n.actionUrl} onClick={open}>
                            팀원 리뷰 작성
                        </a>
                    )}
                </div>
            </div>
            {!read && <span className={cls('notice-unread-dot')} aria-label="안 읽음" />}
        </article>
    );
}

/** 상태와 처리 함수를 받아 그리기만 한다. */
export function NotificationListView({ variant, list, unreadCount, onTab, onRetry, onMore, onToggleRead, onReadAll, onOpen }: NotificationListViewProps) {
    const readAll = (
        <Button variant="secondary" disabled={!unreadCount || list.status !== 'ready'} onClick={onReadAll}>
            <CheckCheck size={15} />
            모두 읽음
        </Button>
    );
    const summary = (
        <p role="status">
            안 읽은 알림 <b>{unreadCount}</b>개
        </p>
    );
    const content = (
        <>
            {variant === 'page' ? (
                <header className={cls('section-heading')}>
                    <div>
                        <div className={cls('eyebrow')}>YOUR UPDATES</div>
                        <h1>새로운 소식을 확인하세요.</h1>
                        {summary}
                    </div>
                    {readAll}
                </header>
            ) : (
                <div className={cls('notification-summary')}>
                    {summary}
                    {readAll}
                </div>
            )}
            <div className={cls('tabs notification-tabs')}>
                {tabs.map(t => (
                    <button key={t.value} type="button" className={cls(list.tab === t.value ? 'active' : '')}
                        aria-pressed={list.tab === t.value} onClick={() => onTab(t.value)}>
                        {t.label}
                    </button>
                ))}
            </div>
            {list.actionError && <p className={cls('notification-alert')} role="alert">{list.actionError}</p>}
            {list.status === 'error' ? (
                <div className={cls('notification-more')} role="alert">
                    <p>{list.message ?? '알림을 불러오지 못했어요.'}</p>
                    <Button variant="secondary" onClick={onRetry}>다시 불러오기</Button>
                </div>
            ) : list.status !== 'ready' ? (
                <p className={cls('empty-state')} role="status">알림을 불러오고 있어요.</p>
            ) : (
                <div className={cls('notification-list')}>
                    {list.items.map(n => (
                        <NotificationItem key={n.id} notification={n} pending={list.pending.includes(n.id)} onToggleRead={onToggleRead} onOpen={onOpen} />
                    ))}
                    {!list.items.length && <div className={cls('empty-state')}>표시할 알림이 없습니다.</div>}
                </div>
            )}
            {list.status === 'ready' && list.hasNext && (
                <div className={cls('notification-more')}>
                    {list.more === 'error' && <p role="alert">{list.message ?? '다음 알림을 불러오지 못했어요.'}</p>}
                    <Button variant="secondary" disabled={list.more === 'loading'} onClick={onMore}>
                        {list.more === 'loading' ? '불러오는 중…' : list.more === 'error' ? '다시 불러오기' : '더 보기'}
                    </Button>
                </div>
            )}
            {variant === 'page' && (
                <p className={cls('auth-hint')}>
                    프로젝트 지원·상태·기간·종료와 협업 리뷰 요청 알림입니다. 실제 푸시·메일은 전송하지 않습니다.
                </p>
            )}
        </>
    );
    return variant === 'page'
        ? <div className={cls('container notifications-page')}>{content}</div>
        : <div className={styles.scope}>{content}</div>;
}

/** 서버 알림 목록. 열 때마다 현재 탭의 첫 페이지를 서버 기준으로 다시 불러온다. onNavigate는 알림을 눌러 이동하기 직전에 부른다. */
export function NotificationList({ variant = 'page', onNavigate }: { variant?: 'page' | 'modal'; onNavigate?: () => void }) {
    const { user } = useAuth();
    const { active, unreadCount, list } = useNotifications();
    const navigate = useNavigation();
    useEffect(() => {
        if (active) void loadNotifications();
    }, [active]);
    if (!user)
        return (
            <div className={cls('container planned')}>
                <section className={cls('planned-card')}>
                    <h1>로그인 후 알림을 확인해 주세요.</h1>
                    <Link className={cls('button')} href="/login" onClick={onNavigate}>로그인</Link>
                </section>
            </div>
        );
    return (
        <NotificationListView
            variant={variant}
            list={list}
            unreadCount={unreadCount}
            onTab={tab => void loadNotifications(tab)}
            onRetry={() => void loadNotifications()}
            onMore={() => void loadMoreNotifications()}
            onToggleRead={n => void setNotificationRead(n.id, !n.read)}
            onReadAll={() => void readAllNotifications()}
            onOpen={n => void openNotification(n, href => {
                onNavigate?.();
                navigate(href);
            })}
        />
    );
}
