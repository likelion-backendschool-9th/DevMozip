'use client';
import { useEffect, useState } from 'react';
import { useAuth } from '@/lib/state/authStore';
import { fetchSearch, SearchRequestError } from './searchApi';
import type { SearchResult } from '@/types/searchApi';

export type ExploreSearchState =
    | { status: 'idle' }
    | { status: 'loading'; previous: SearchResult | null }
    | { status: 'ready'; result: SearchResult }
    | { status: 'error'; error: SearchRequestError };

type Loaded = { key: string; query: string; identity: string; result: SearchResult | null; error: SearchRequestError | null };

/**
 * Mock 끄기의 탐색 결과 한 페이지. 조건·페이지·로그인 계정이 바뀌면 다시 받고, 이전 요청의 늦은 응답은 버린다.
 * 같은 조건을 다시 받는 동안(언팔로우 뒤 재조회)에는 이전 결과를 계속 보여 줄 수 있게 previous로 넘긴다.
 * @param query searchApiQuery로 만든 쿼리 문자열. null이면 요청하지 않는다(비로그인 팔로잉 탭 등).
 */
export function useExploreSearch(query: string | null) {
    const { user } = useAuth();
    const identity = user?.email ?? '';
    const [version, setVersion] = useState(0);
    const key = `${identity}\n${version}\n${query ?? ''}`;
    const [loaded, setLoaded] = useState<Loaded | null>(null);

    useEffect(() => {
        if (query === null) return;
        const controller = new AbortController();
        fetchSearch(new URLSearchParams(query), controller.signal).then(result => {
            if (!controller.signal.aborted) setLoaded({ key, query, identity, result, error: null });
        }).catch(error => {
            if (controller.signal.aborted) return;
            setLoaded({ key, query, identity, result: null,
                error: error instanceof SearchRequestError ? error : new SearchRequestError(502, '검색 서버에 연결하지 못했어요.') });
        });
        return () => controller.abort();
    }, [key, query, identity]);

    let state: ExploreSearchState;
    if (query === null) state = { status: 'idle' };
    else if (loaded?.key === key) state = loaded.result ? { status: 'ready', result: loaded.result } : { status: 'error', error: loaded.error! };
    else state = { status: 'loading', previous: loaded?.result && loaded.query === query && loaded.identity === identity ? loaded.result : null };

    return {
        state,
        reload: () => setVersion(value => value + 1),
        /** 받은 결과를 화면에서 바꾼다(팔로우 상태 반영). 다시 받으면 서버 값으로 바뀐다. */
        update: (change: (result: SearchResult) => SearchResult) =>
            setLoaded(current => current?.result ? { ...current, result: change(current.result) } : current),
    };
}
