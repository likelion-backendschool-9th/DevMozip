'use client';
import { useState } from 'react';
import { Button } from '@/components/common/Button';
import { changeFollow, FollowRequestError } from '@/lib/profiles/followApi';
import type { FollowResult } from '@/types/followApi';

/**
 * 팔로우·언팔로우 버튼(WBS 9.1·9.2). 프로필 상단, 팔로워·팔로잉 목록, 개발자 탐색 카드(6.6)가 함께 쓴다.
 * 요청 중에는 막고, 버튼 상태는 부모가 응답의 최종 상태로 맞춘다.
 */
export function FollowButton({ slug, name, followed, onChange, onError, className }: {
    slug: string;
    name: string;
    followed: boolean;
    onChange: (result: FollowResult) => void;
    onError?: (message: string, status: number) => void;
    className?: string;
}) {
    const [pending, setPending] = useState(false);
    async function toggle() {
        if (pending) return;
        setPending(true);
        try {
            onChange(await changeFollow(slug, !followed));
        } catch (error) {
            onError?.(error instanceof Error ? error.message : '요청을 완료하지 못했어요.', error instanceof FollowRequestError ? error.status : 502);
        } finally {
            setPending(false);
        }
    }
    return <Button className={className} variant={followed ? 'secondary' : 'primary'} aria-pressed={followed} aria-busy={pending}
        aria-label={`${name} ${followed ? '언팔로우' : '팔로우'}`} disabled={pending} onClick={() => void toggle()}>
        {pending ? '처리 중…' : followed ? '언팔로우' : '팔로우'}
    </Button>;
}
