'use client';
import styles from './AppLayout.module.css';
import { useEffect, useRef } from 'react';
import Link from 'next/link';
import { ActiveLink as NavLink } from '@/components/navigation/ActiveLink';
import { useNavigation as useNavigate, useCurrentLocation as useLocation } from '@/lib/navigation';
import { Bell, MessageSquare, ArrowUpRight, LogOut } from 'lucide-react';
import { navigation } from '@/mocks/routes';
import { routeTitle } from '@/lib/shared/routeService';
import { useAuth } from '@/lib/state/authStore';
import { usePrototype } from '@/lib/state/prototypeStore';
import { useMessaging } from '@/lib/state/messagingStore';
import { useProjects } from '@/lib/state/projectStore';
import { useHome } from '@/lib/state/homeStore';
import { OwnProfileProvider } from '@/app/(user)/_components/OwnProfileProvider';
import { HeaderProfileLink } from './HeaderProfileLink';
import { Toast } from './Toast';

const logo = '/devmozip-logo.svg';

export function AppLayout({ children }: { children?: React.ReactNode }) {
    return <OwnProfileProvider><AppShell>{children}</AppShell></OwnProfileProvider>;
}

function AppShell({ children }: { children?: React.ReactNode }) {
    const { unreadCount, unreadMessages } = useMessaging();
    const { notify } = usePrototype();
    const { reset: resetHome } = useHome();
    const { state: projectState } = useProjects();
    const { user, logout, administration, needsOnboarding } = useAuth();
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
                                <Link className={[styles.scope, "icon-button"].join(" ")} aria-label="알림" href="/notifications">
                                    <Bell size={20} />
                                    {unreadCount > 0 && (
                                        <span className={[styles.scope, "header-count"].join(" ")} aria-label={`안 읽은 알림 ${unreadCount}개`}>
                                            {unreadCount > 99 ? '99+' : unreadCount}
                                        </span>
                                    )}
                                </Link>
                                <Link className={[styles.scope, "icon-button"].join(" ")} aria-label="쪽지" href="/messages/chats">
                                    <MessageSquare size={20} />
                                    {unreadMessages > 0 && (
                                        <span className={[styles.scope, "header-count"].join(" ")} aria-label={`안 읽은 쪽지 ${unreadMessages}개`}>
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

            <div className={[styles.scope, "utility-bar"].join(" ")}>
                <div className={[styles.scope, "utility-inner"].join(" ")}>
                    <span>
                        <span className={[styles.scope, "status-dot"].join(" ")} />
                        Mock 프로토타입 <b>· 프론트엔드 데모</b>
                    </span>
                </div>
            </div>

            {user && administration.members[user.id]?.status === 'warned' && (
                <div className={[styles.scope, "member-warning"].join(" ")} role="status">
                    운영 안내 · {administration.members[user.id].reason}
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
                <span>© 2026 DevMozip · Mock 기반 프로토타입</span>
                <Link href="/subscription">
                    프리미엄 안내
                    <ArrowUpRight size={14} />
                </Link>
            </footer>

            <Toast />
        </>
    );
}
