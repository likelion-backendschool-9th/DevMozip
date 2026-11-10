'use client';
// 관리자 서버 화면의 공통 표시: 불러오는 중·오류와 다시 시도·빈 목록·페이지 이동.
import type { ReactNode } from 'react';
import { adminStyles as styles } from './AdminShell';
import { Button } from '@/components/common/Button';
import type { AdminLoad } from '@/lib/administration/useAdminResource';

export const cls = (name: string) => [styles.scope, name].join(' ');

/** 불러오기 상태를 보여 주고, 준비되면 children(data)을 그린다. */
export function AdminLoadView<T>({ load, onRetry, children, loadingText = '목록을 불러오고 있어요.' }:
    { load: AdminLoad<T>; onRetry: () => void; children: (data: T) => ReactNode; loadingText?: string }) {
    if (load.status === 'loading') return <p role="status">{loadingText}</p>;
    if (load.status === 'error')
        return (
            <div className={cls('admin-alert')} role="alert">
                <span>{load.message}</span>
                <Button variant="secondary" onClick={onRetry}>다시 시도</Button>
            </div>
        );
    return <>{children(load.data)}</>;
}

export function AdminPager({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
    if (totalPages <= 1) return null;
    return (
        <nav className={cls('admin-pager')} aria-label="페이지">
            <Button variant="secondary" disabled={page === 0} onClick={() => onChange(page - 1)}>이전</Button>
            <span>{page + 1} / {totalPages}</span>
            <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>다음</Button>
        </nav>
    );
}

export function AdminActionError({ message }: { message: string }) {
    return message ? <p className={cls('admin-alert')} role="alert">{message}</p> : null;
}
