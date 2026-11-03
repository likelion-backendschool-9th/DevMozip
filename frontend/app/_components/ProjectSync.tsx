'use client';
import { useEffect } from 'react';
import { useProfiles } from '@/lib/state/profileStore';
import { startProjectSync, syncProjects } from '@/lib/state/projectStore';

/**
 * 프로젝트 상태 동기화 — 화면은 그리지 않는다. app/layout.tsx에 둔다.
 * 프로필 리뷰가 바뀌면(리뷰 제출 → 리뷰 요청 사용 완료) 다시 맞추고, 1분마다 모집 기한을 처리한다.
 * 백엔드가 붙으면 서버 스케줄러(WBS 7.14)와 리뷰 저장 API(5.8)가 맡으므로 지운다.
 */
export function ProjectSync() {
    const { state: profiles } = useProfiles();

    useEffect(() => startProjectSync(), []);

    useEffect(() => {
        syncProjects();
    }, [profiles.reviews]);

    return null;
}
