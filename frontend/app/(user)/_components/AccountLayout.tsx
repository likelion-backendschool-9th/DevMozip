'use client';
import styles from './AccountLayout.module.css';
import { useId, useState, type ReactNode } from 'react';
import Link from 'next/link';
import { useParams, usePathname } from 'next/navigation';
import { ActiveLink as NavLink } from '@/components/navigation/ActiveLink';
import { UserRound, Heart, Bookmark, Settings } from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { useOwnProfile } from './OwnProfileProvider';
import { accountMenu } from '@/mocks/account';

const icons = [UserRound, Heart, Bookmark, Settings];

/**
 * 개인 계정 화면 틀(왼쪽 개인 메뉴 + 본문). (user)/layout.tsx가 감싼다.
 * 로그인한 사용자의 개인 메뉴는 서버 프로필 조회 중에도 유지한다.
 * Mock 모드에서는 본인 프로필에 표시하고, 팔로우 목록에는 표시하지 않는다.
 */
export function AccountLayout({ children }: { children: ReactNode }) {
    const { user: current } = useAuth();
    const own = useOwnProfile();
    const pathname = usePathname();
    const { userId } = useParams<{ userId?: string }>();
    const [menuOpen, setMenuOpen] = useState(false);
    const menuId = useId();

    // 사이드바는 로그인한 사용자의 탐색 메뉴이며, 조회 대상의 수정 권한과는 별개다.
    const profilePage = !!userId && pathname === `/users/${userId}`;
    const showProfileMenu = profilePage && !!current && true;
    if (userId && !showProfileMenu) return children;

    if (!current)
        return (
            <div className={[styles.scope, 'container planned'].join(' ')}>
                <section className={[styles.scope, 'planned-card'].join(' ')}>
                    <h1>로그인 후 개인 계정을 관리해 주세요.</h1>
                    <p>본인의 좋아요·북마크와 계정 정보만 확인할 수 있습니다.</p>
                    <Link className={[styles.scope, 'button'].join(' ')} href="/login">
                        로그인하기
                    </Link>
                </section>
            </div>
        );

    return (
        <div className={[styles.scope, 'container personal-workspace'].join(' ')}>
            <aside className={[styles.scope, 'account-aside'].join(' ')} data-open={menuOpen}>
                <div className={[styles.scope, 'account-aside-heading'].join(' ')}>
                    <span>MY WORKSPACE</span>
                    <strong>{own.profile?.nickname || current.email}</strong>
                    <small>개인 계정 관리</small>
                </div>
                <button
                    type="button"
                    className={[styles.scope, 'account-menu-toggle'].join(' ')}
                    aria-expanded={menuOpen}
                    aria-controls={menuId}
                    onClick={() => setMenuOpen(v => !v)}
                >
                    개인 계정 메뉴 <span>{menuOpen ? '접기 −' : '펼치기 +'}</span>
                </button>
                <nav id={menuId} aria-label="개인 계정 관리">
                    {accountMenu.map((item, i) => {
                        const Icon = icons[i];
                        return (
                            <NavLink
                                onClick={() => setMenuOpen(false)}
                                key={item.key}
                                end
                                href={item.key === 'profile' ? (own.profile && pathname === `/users/${own.profile.tag}` ? `/users/${own.profile.tag}` : '/profile/me') : item.path}
                            >
                                <Icon size={17} />
                                <span>{item.label}</span>
                            </NavLink>
                        );
                    })}
                </nav>
                <p>나의 기록과 설정을 한곳에서 관리하세요.</p>
            </aside>
            <div className={[styles.scope, 'personal-main'].join(' ')}>{children}</div>
        </div>
    );
}
