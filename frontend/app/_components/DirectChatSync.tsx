'use client';
import { useEffect } from 'react';
import { useAuth } from '@/lib/state/authStore';
import { startDirectChatSync } from '@/lib/state/directChatStore';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';
import { startProjectChatSync } from '@/lib/state/projectChatStore';

/**
 * 1:1과 프로젝트 채팅의 서버 동기화 — 화면은 그리지 않는다. app/layout.tsx에 둔다.
 * 로그인 회원이 Mock 토글을 끈 동안에만 배지·목록을 조회하고, 토글이나 계정이 바뀌면 불러온 데이터를 비운다.
 */
export function DirectChatSync() {
    const { enabled } = useMockVisibility();
    const { user } = useAuth();
    const account = user?.email;
    useEffect(() => {
        if (enabled || !account) return;
        const stopDirect = startDirectChatSync();
        const stopProject = startProjectChatSync();
        return () => {
            stopDirect();
            stopProject();
        };
    }, [enabled, account]);
    return null;
}
