'use client';
import styles from './AppLayout.module.css';
import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { ActiveLink as NavLink } from '@/components/navigation/ActiveLink';
import { useNavigation as useNavigate, useCurrentLocation as useLocation } from '@/lib/navigation';
import { Bell, MessageSquare, ArrowUpRight, LogOut } from 'lucide-react';
import { navigation } from '@/mocks/routes';
import { routeTitle } from '@/lib/shared/routeService';
import { useAuth, useServerWarning } from '@/lib/state/authStore';
import { usePrototype } from '@/lib/state/prototypeStore';
import { useMessaging } from '@/lib/state/messagingStore';
import { useDirectChats } from '@/lib/state/directChatStore';
import { useProjectChats } from '@/lib/state/projectChatStore';
import { useProjects } from '@/lib/state/projectStore';
import { useHome } from '@/lib/state/homeStore';
import { OwnProfileProvider } from '@/app/(user)/_components/OwnProfileProvider';
import { HeaderProfileLink } from './HeaderProfileLink';
import { Toast } from './Toast';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';
import { useNotifications } from '@/lib/state/notificationStore';
import { NotificationModal } from '@/components/notifications/NotificationModal';
import { NotificationSync } from './NotificationSync';

const logo = '/devmozip-logo.svg';

export function AppLayout({ children }: { children?: React.ReactNode }) {
    return <OwnProfileProvider><AppShell>{children}</AppShell></OwnProfileProvider>;
}

function AppShell({ children }: { children?: React.ReactNode }) {
    const { enabled: mockEnabled } = useMockVisibility();
    const { unreadCount: mockUnreadNotices, unreadMessages: mockUnreadMessages } = useMessaging();
    const { unreadCount: serverUnreadNotices } = useNotifications();
    // 알림 배지: 토글 OFF는 서버 안 읽음 수(12.1), ON은 지금처럼 Mock 수를 센다.
    const unreadCount = mockEnabled ? mockUnreadNotices : serverUnreadNotices;
    // 공통 알림 모달(D1 B)은 주소를 바꾸지 않고 보던 화면 위에 연다.
    const [noticesOpen, setNoticesOpen] = useState(false);
    const { unread: serverDirectUnread } = useDirectChats();
    const { unread: serverProjectUnread } = useProjectChats();
    // 토글 OFF는 1:1(11.7)과 프로젝트 채팅(7.21)을 합산한다(D12). ON은 지금처럼 Mock 1:1만 센다.
    const unreadMessages = mockEnabled ? mockUnreadMessages : serverDirectUnread + serverProjectUnread;
    const unreadMessageLabel = mockEnabled ? '안 읽은 쪽지' : '안 읽은 메시지';
    const { notify } = usePrototype();
    const { reset: resetHome } = useHome();
    const { state: projectState } = useProjects();
    const { user, logout, administration, needsOnboarding } = useAuth();
    // 경고 안내: 토글 ON은 Mock 제재 기록, OFF는 서버가 확인한 경고 사유를 쓴다 (docs/admin-plan.md D8).
    const serverWarning = useServerWarning();
    const warning = mockEnabled
        ? (user && administration.members[user.id]?.status === 'warned' ? administration.members[user.id].reason : null)
        : (user ? serverWarning : null);
    const navigate = useNavigate();
    const location = useLocation();
    const main = useRef<HTMLElement>(null);

    useEffect(() => {
        window.scrollTo(0, 0);
        main.current?.focus({ preventScroll: true });
        document.title = routeTitle(location.pathname);
    }, [location.pathname]);

    return (
        <>
            <a className={[styles.scope, "skip-link"].join(" ")} href="#main">본문 바로가기</a>

            <header className={[styles.scope, "header"].join(" ")}>
                <div className={[styles.scope, "header-inner"].join(" ")}>
                    <Link className={[styles.scope, "logo"].join(" ")} href="/" aria-label="DevMozip 홈">
                        <img className={[styles.scope, "brand-logo"].join(" ")} src={logo} alt="DevMozip" width="96" height="64" />
                    </Link>
                    <nav aria-label="주 내비게이션">
                        {navigation.map(item => (
                            <NavLink key={item.to} href={item.to} end={item.end}>{item.label}</NavLink>
                        ))}
                    </nav>
                    <div className={[styles.scope, "header-actions"].join(" ")}>
                        {!user ? (
                            <>
                                <Link href="/login" className={[styles.scope, "login-link"].join(" ")}>로그인</Link>
                                <Link href="/signup" className={[styles.scope, "button small"].join(" ")}>시작하기</Link>
                            </>
                        ) : (
                            <>
                                <button type="button" className={[styles.scope, "icon-button"].join(" ")} aria-label="알림" aria-haspopup="dialog"
                                    aria-expanded={noticesOpen} onClick={() => setNoticesOpen(true)}>
                                    <Bell size={20} />
                                    {unreadCount > 0 && (
                                        <span className={[styles.scope, "header-count"].join(" ")} aria-label={`안 읽은 알림 ${unreadCount}개`}>
                                            {unreadCount > 99 ? '99+' : unreadCount}
                                        </span>
                                    )}
                                </button>
                                <Link className={[styles.scope, "icon-button"].join(" ")} aria-label="쪽지" href="/messages/chats">
                                    <MessageSquare size={20} />
                                    {unreadMessages > 0 && (
                                        <span className={[styles.scope, "header-count"].join(" ")} aria-label={`${unreadMessageLabel} ${unreadMessages}개`}>
                                            {unreadMessages > 99 ? '99+' : unreadMessages}
                                        </span>
                                    )}
                                </Link>
                                <HeaderProfileLink />
                                <button
                                    type="button"
                                    className={[styles.scope, "icon-button"].join(" ")}
                                    aria-label="로그아웃"
                                    title="로그아웃"
                                    onClick={() => {
                                        setNoticesOpen(false);
                                        logout();
                                        resetHome();
                                        navigate('/');
                                        notify('로그아웃되었습니다.');
                                    }}
                                >
                                    <LogOut size={20} />
                                </button>
                            </>
                        )}
                    </div>
                </div>
            </header>

            {warning !== null && (
                <div className={[styles.scope, "member-warning"].join(" ")} role="status">
                    운영 안내 · {warning || '운영 정책에 따라 경고를 받은 계정입니다.'}
                </div>
            )}
            {projectState.simulationDate && (
                <div className={[styles.scope, "account-state-banner"].join(" ")} role="status">
                    프로젝트 날짜 시뮬레이션 적용 중 · {new Date(projectState.simulationDate).toLocaleString('ko-KR')}{' '}
                    <Link href="/my-projects">날짜 변경/복원 →</Link>
                </div>
            )}
            {needsOnboarding && (
                <div className={[styles.scope, "account-state-banner"].join(" ")} role="status">
                    프로필 온보딩을 완료해 주세요. <Link href="/onboarding">이어서 작성 →</Link>
                </div>
            )}

            <main id="main" ref={main} tabIndex={-1}>{children}</main>

            <footer>
                <Link className={[styles.scope, "logo"].join(" ")} href="/" aria-label="DevMozip 홈">
                    <img className={[styles.scope, "brand-logo"].join(" ")} src={logo} alt="DevMozip" width="96" height="64" />
                </Link>
                <p>개발자의 다음 가능성을 연결합니다.</p>
                <span>© 2026 DevMozip</span>
                <Link href="/subscription">
                    프리미엄 안내
                    <ArrowUpRight size={14} />
                </Link>
            </footer>

            {user && noticesOpen && <NotificationModal onClose={() => setNoticesOpen(false)} />}
            <NotificationSync />
            <Toast />
        </>
    );
}
