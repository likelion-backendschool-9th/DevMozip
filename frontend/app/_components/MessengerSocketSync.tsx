'use client';
import { useEffect } from 'react';
import { useAuth } from '@/lib/state/authStore';
import { startMessengerSocket } from '@/lib/state/messengerSocketStore';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';

/**
 * 메신저 실시간 연결 — 화면은 그리지 않는다. app/layout.tsx에 둔다.
 * 로그인 회원이 Mock 토글을 끈 동안에만 연결한다. 로그아웃하거나 토글을 켜거나 계정이 바뀌면 닫는다.
 */
export function MessengerSocketSync() {
    const { enabled } = useMockVisibility();
    const { user } = useAuth();
    const account = user?.email;
    useEffect(() => {
        if (enabled || !account) return;
        return startMessengerSocket();
    }, [enabled, account]);
    return null;
}
