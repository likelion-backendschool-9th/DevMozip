'use client';
import styles from './AppLayout.module.css';
import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { ActiveLink as NavLink } from '@/components/navigation/ActiveLink';
import { useNavigation as useNavigate, useCurrentLocation as useLocation } from '@/lib/navigation';
import { Bell, MessageSquare, ArrowUpRight, RotateCcw } from 'lucide-react';
import { navigation } from '@/mocks/routes';
import { routeTitle } from '@/lib/shared/routeService';
import { useAuth } from '@/lib/state/authStore';
import { DemoAccounts } from '@/components/auth/DemoAccounts';
import { usePrototype } from '@/lib/state/prototypeStore';
import { useMessaging } from '@/lib/state/messagingStore';
import { useProjects } from '@/lib/state/projectStore';
import { useProfiles } from '@/lib/state/profileStore';
import { useHome } from '@/lib/state/homeStore';
import { Modal } from '@/components/common/Modal';
import { Toast } from './Toast';
import { Button } from '@/components/common/Button';

const logo = '/devmozip-logo.svg';

export function AppLayout({ children }: {
    children?: React.ReactNode;
}) {
    const { unreadCount, unreadMessages, reset: resetMessaging } = useMessaging();
    const { settings, toggleGuide, reset, notify } = usePrototype();
    const { reset: resetHome, resetAll: resetAllHome } = useHome();
    const { reset: resetProfiles, state: followState } = useProfiles();
    const { reset: resetProjects, state: projectState } = useProjects();
    const { user, logout, resetAdministration, administration, resetAccountArea, accountUser, needsOnboarding } = useAuth();
    const [resetOpen, setResetOpen] = useState(false);
    const [demoOpen, setDemoOpen] = useState(false);
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
                                <Link
                                    className={[styles.scope, styles.profileLink, "icon-button header-profile-link"].join(" ")}
                                    href={`/users/${user.id}`}
                                    aria-label="내 프로필로 이동"
                                    title={`${user.nickname}님의 프로필`}
                                >
                                    <span className={[styles.scope, "small-avatar lavender"].join(" ")}>{user.nickname.slice(0, 2)}</span>
                                </Link>
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
                    <div>
                        <button onClick={() => setDemoOpen(true)}>데모 계정 전환</button>
                        <button onClick={toggleGuide}>{settings.showGuide ? '안내 숨기기' : '안내 보기'}</button>
                        <button onClick={() => setResetOpen(true)}>
                            <RotateCcw size={13} />
                            데모 초기화
                        </button>
                    </div>
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

            {demoOpen && (
                <Modal title="데모 계정으로 둘러보기" onClose={() => setDemoOpen(false)}>
                    <p>실제 인증 없이 일반 회원·프리미엄 회원·관리자를 체험합니다.</p>
                    <DemoAccounts onSelected={() => setDemoOpen(false)} />
                    {user && (
                        <Button
                            variant="secondary"
                            className={[styles.scope, "demo-logout"].join(" ")}
                            onClick={() => {
                                logout();
                                resetHome();
                                setDemoOpen(false);
                                navigate('/');
                                notify('로그아웃되었습니다.');
                            }}
                        >
                            로그아웃 상태로 전환
                        </Button>
                    )}
                </Modal>
            )}

            {resetOpen && (
                <Modal title="데모 데이터를 초기화할까요?" onClose={() => setResetOpen(false)}>
                    <p>
                        계정 상태, 추천 입력·사용량과 안내 설정을 초기값으로 되돌립니다.
                        프로필·포트폴리오·글·리뷰·팔로우·모집 공고·지원·북마크·신고·대화·알림 읽음·구독·회원 제재·신고 처리·리뷰 선택지도 초기화하며 가입한 Mock 계정은 유지됩니다.
                        계정 공개 설정·카카오 Mock 연동·온보딩 임시 입력도 초기화합니다.
                    </p>
                    <div className={[styles.scope, "modal-actions"].join(" ")}>
                        <Button variant="secondary" onClick={() => setResetOpen(false)}>취소</Button>
                        <Button
                            onClick={() => {
                                logout();
                                resetAllHome();
                                resetProfiles();
                                resetProjects();
                                resetMessaging();
                                resetAdministration();
                                resetAccountArea();
                                reset();
                                setResetOpen(false);
                                window.location.assign('/');
                            }}
                        >
                            초기화
                        </Button>
                    </div>
                </Modal>
            )}

            <Toast />
        </>
    );
}
