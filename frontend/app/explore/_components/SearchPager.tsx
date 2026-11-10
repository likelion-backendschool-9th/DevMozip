'use client';
import { Button } from '@/components/common/Button';
import styles from './SearchPager.module.css';

/** 탐색 결과 페이지 이동(/projects 목록과 같은 이전·다음 방식). 결과가 한 페이지 이하이면 보이지 않는다. page는 0부터다. */
export function SearchPager({ page, totalPages, disabled, onChange }: {
    page: number;
    totalPages: number;
    disabled?: boolean;
    onChange: (page: number) => void;
}) {
    if (totalPages <= 1) return null;
    return <nav className={styles.pager} aria-label="검색 결과 페이지">
        <Button variant="secondary" disabled={disabled || page === 0} onClick={() => onChange(Math.min(page - 1, totalPages - 1))}>이전</Button>
        <span aria-current="page"><b>{Math.min(page + 1, totalPages)}</b> / {totalPages}</span>
        <Button variant="secondary" disabled={disabled || page + 1 >= totalPages} onClick={() => onChange(page + 1)}>다음</Button>
    </nav>;
}
