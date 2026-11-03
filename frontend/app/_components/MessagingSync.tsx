'use client';
import { useEffect } from 'react';
import { reconcileMessaging, startMessagingSync } from '@/lib/state/messagingStore';
import { useProfiles } from '@/lib/state/profileStore';
import { useProjects } from '@/lib/state/projectStore';

/**
 * 대화방 · 알림 동기화 — 화면은 그리지 않는다. app/layout.tsx에 둔다.
 * 프로젝트 팀원 · 프로필이 바뀌면(승인 → 단체 대화방 입장, 강퇴 → 퇴장) 다시 맞추고, 1분마다 반복한다.
 * 백엔드가 붙으면 승인 · 강퇴 API와 알림 이벤트(WBS 7.9, 7.11, 12.3)가 맡으므로 지운다.
 */
export function MessagingSync() {
    const { state: projects } = useProjects();
    const { state: profiles } = useProfiles();

    useEffect(() => {
        reconcileMessaging();
    }, [projects, profiles.profiles]);

    useEffect(() => startMessagingSync(), []);

    return null;
}
