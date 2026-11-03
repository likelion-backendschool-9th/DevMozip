import { useSyncExternalStore } from 'react';

// localStorage에서 읽은 값을 들고 있는 작은 외부 저장소입니다(화면 간 상태 공유용).
// 처음 읽을 때 load()로 한 번 불러오고 set()으로만 바뀌며, 바뀌면 구독 중인 컴포넌트에 알립니다.
// localStorage 쓰기는 저장 실패 안내가 필요하므로 각 서비스의 save 함수가 맡습니다.
// 서버 렌더링 · 하이드레이션 동안에는 ClientReady가 화면을 미루므로 브라우저에서만 읽습니다.
// 백엔드가 붙으면 API 값을 들고 있는 저장소로 바꿉니다.

export interface Source {
    /** 값이 바뀔 때마다 1씩 오른다 */
    version: () => number;
    subscribe: (listener: () => void) => () => void;
}

export interface LocalStore<T> extends Source {
    get: () => T;
    set: (value: T) => void;
}

export interface DerivedStore<T> {
    get: () => T;
    subscribe: (listener: () => void) => () => void;
}

const resets = new Set<() => void>();

export function createLocalStore<T>(load: () => T): LocalStore<T> {
    const listeners = new Set<() => void>();
    let value: T;
    let loaded = false;
    let version = 0;

    resets.add(() => {
        loaded = false;
        version++;
    });

    return {
        get: () => {
            if (!loaded) {
                value = load();
                loaded = true;
            }
            return value;
        },
        set: next => {
            value = next;
            loaded = true;
            version++;
            listeners.forEach(listener => listener());
        },
        version: () => version,
        subscribe: listener => {
            listeners.add(listener);
            return () => {
                listeners.delete(listener);
            };
        },
    };
}

/** 여러 저장소로 계산한 값 — sources 중 하나가 바뀔 때만 다시 계산하고, 그 전까지는 같은 참조를 돌려준다 */
export function derived<T>(sources: Source[], compute: () => T): DerivedStore<T> {
    let key = '';
    let cached: T;
    return {
        get: () => {
            const next = sources.map(source => source.version()).join();
            if (next !== key) {
                cached = compute();
                key = next;
            }
            return cached;
        },
        subscribe: listener => {
            const unsubscribes = sources.map(source => source.subscribe(listener));
            return () => unsubscribes.forEach(unsubscribe => unsubscribe());
        },
    };
}

export function useStore<T>(store: DerivedStore<T>): T {
    return useSyncExternalStore(store.subscribe, store.get, store.get);
}

/** 테스트에서 localStorage를 바꾼 뒤 모든 저장소를 다시 읽게 한다 */
export function resetLocalStores() {
    resets.forEach(reset => reset());
}
