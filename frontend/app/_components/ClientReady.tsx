'use client';
import { useEffect, useState, useSyncExternalStore, type ReactNode } from 'react';
import { usePathname } from 'next/navigation';
import { syncServerSession } from '@/lib/state/authStore';

const subscribe = () => () => {};

export function ClientReady({ children }: { children: ReactNode }) {
    const pathname = usePathname();
    const mounted = useSyncExternalStore(subscribe, () => true, () => false);
    const [ready, setReady] = useState(false);
    const [error, setError] = useState(false);
    useEffect(() => {
        let active = true;
        async function sync() {
            try {
                const response = await fetch('/api/auth/session', { cache: 'no-store' });
                if (!response.ok) throw new Error('세션 확인 실패');
                const snapshot = await response.json();
                if (active) { syncServerSession(snapshot); setReady(true); setError(false); }
            } catch { if (active) { syncServerSession({ mode: 'server', identity: null }); setReady(true); setError(true); } }
        }
        void sync();
        window.addEventListener('focus', sync);
        return () => { active = false; window.removeEventListener('focus', sync); };
    }, [pathname]);
    if (!mounted || !ready) return <div className="client-loading" role="status">DevMozip 데모 데이터를 준비하고 있어요…</div>;
    return <>{error && <p role="alert">인증 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.</p>}{children}</>;
}
