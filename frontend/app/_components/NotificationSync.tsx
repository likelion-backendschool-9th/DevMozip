'use client';
import { useEffect } from 'react';
import { useAuth } from '@/lib/state/authStore';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';
import { startNotificationSync } from '@/lib/state/notificationStore';

/**
 * 서버 알림 배지 동기화 — 화면은 그리지 않는다. AppLayout에 둔다.
 * 로그인 회원이 Mock 토글을 끈 동안에만 60초마다 안 읽음 수를 조회하고, 토글·계정이 바뀌거나 로그아웃하면 멈추고 비운다.
 */
export function NotificationSync() {
    const { enabled } = useMockVisibility();
    const { user } = useAuth();
    const account = user?.email;
    useEffect(() => {
        if (enabled || !account) return;
        return startNotificationSync();
    }, [enabled, account]);
    return null;
}
