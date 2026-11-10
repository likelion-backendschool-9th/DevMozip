'use client';
import styles from './AdminShell.module.css';
import type { ReactNode } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { ShieldCheck } from 'lucide-react';
import { useNavigation as useNavigate } from '@/lib/navigation';
import { useAuth } from '@/lib/state/authStore';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';
import { adminMenu } from '@/mocks/administration';

/** 관리자 화면 공통 틀(권한 확인 · 제목 · 관리 메뉴). 메뉴별 내용은 각 page.tsx가 그린다 */
export function AdminShell({ children }: { children: ReactNode }) {
    const { user } = useAuth();
    const { enabled: mockEnabled } = useMockVisibility();
    const pathname = usePathname();
    const navigate = useNavigate();
    const tab = adminMenu.find(m => m.path === pathname)?.label;

    if (user?.role !== 'admin')
        return (
            <div className={[styles.scope, "container governance-denied"].join(" ")}>
                <ShieldCheck size={40} />
                <h1>관리자 전용 화면입니다</h1>
                <p>관리자 계정으로 로그인해 주세요.</p>
                <Link href="/subscription" className={[styles.scope, "button"].join(" ")}>요금제 보기</Link>
            </div>
        );

    return (
        <div className={[styles.scope, "container admin-page"].join(" ")}>
            <header className={[styles.scope, "admin-heading"].join(" ")}>
                <div>
                    <span className={[styles.scope, "eyebrow"].join(" ")}>WORKSPACE / ADMIN</span>
                    <h1><ShieldCheck /> 운영 관리</h1>
                    <p>서비스의 연결과 신뢰를 관리하세요.</p>
                </div>
                {/* 토글 OFF의 관리 화면은 서버 값을 쓰므로 Mock 안내는 ON일 때만 보여 준다 (docs/admin-plan.md 6.2) */}
                {mockEnabled && <span className={[styles.scope, "demo-pill"].join(" ")}>Mock 운영 데이터 · 서버 변경 없음</span>}
            </header>
            <div className={[styles.scope, "admin-workspace"].join(" ")}>
                <nav className={[styles.scope, "admin-navigation"].join(" ")} aria-label="관리 메뉴">
                    {adminMenu.map(m => (
                        <button
                            key={m.label}
                            className={[styles.scope, tab === m.label ? 'active' : ''].join(" ")}
                            aria-current={tab === m.label ? 'page' : undefined}
                            onClick={() => navigate(m.path)}
                        >
                            {m.label}
                        </button>
                    ))}
                </nav>
                {children}
            </div>
        </div>
    );
}

export { styles as adminStyles };
