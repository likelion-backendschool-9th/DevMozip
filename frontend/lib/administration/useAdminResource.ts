'use client';
// 관리자 서버 화면의 불러오기·변경 상태 (docs/admin-plan.md 6.2 공통).
// 불러오기는 요청 경로가 바뀌거나 다시 시도하면 새로 하고, 이전 요청의 응답은 버린다.
import { useCallback, useEffect, useRef, useState } from 'react';
import { adminRequest } from './adminApi';

export type AdminLoad<T> = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; data: T };

const loadFailed = '목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.';

export function useAdminResource<T>(path: string, parse: (value: unknown) => T) {
    const [version, setVersion] = useState(0);
    const key = `${path}#${version}`;
    const [result, setResult] = useState<{ key: string; load: AdminLoad<T> } | null>(null);
    useEffect(() => {
        const abort = new AbortController();
        adminRequest(path, { signal: abort.signal }, parse)
            .then(data => { if (!abort.signal.aborted) setResult({ key, load: { status: 'ready', data } }); })
            .catch(error => {
                if (!abort.signal.aborted) setResult({ key, load: { status: 'error', message: error instanceof Error && error.message ? error.message : loadFailed } });
            });
        return () => abort.abort();
    }, [key, path, parse]);
    const load: AdminLoad<T> = result?.key === key ? result.load : { status: 'loading' };
    const reload = useCallback(() => setVersion(v => v + 1), []);
    return { load, reload };
}

/** 변경 요청 하나를 실행한다. 요청 중에는 다시 실행하지 않고, 실패하면 안내 문구를 남긴다. */
export function useAdminAction() {
    const lock = useRef(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const run = useCallback(async (task: () => Promise<unknown>) => {
        if (lock.current) return false;
        lock.current = true;
        setBusy(true);
        setError('');
        try {
            await task();
            return true;
        } catch (e) {
            setError(e instanceof Error && e.message ? e.message : '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.');
            return false;
        } finally {
            lock.current = false;
            setBusy(false);
        }
    }, []);
    const clearError = useCallback(() => setError(''), []);
    return { busy, error, run, clearError };
}
