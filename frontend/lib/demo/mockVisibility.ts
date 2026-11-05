/** 표시 설정만 저장하며 기존 도메인 데이터와 인증 저장소는 변경하지 않는다. */
export const MOCK_VISIBILITY_KEY = 'devmozip:mock-visibility:v1';
type PreferenceStorage = Pick<Storage, 'getItem' | 'setItem'>;
export function parseMockVisibility(raw: string | null): boolean {
    try {
        const value = JSON.parse(raw ?? 'null');
        return value?.version === 1 && typeof value.enabled === 'boolean' ? value.enabled : true;
    } catch { return true; }
}

export function createMockVisibilityStore(storage: () => PreferenceStorage | undefined) {
    let enabled = true;
    let loaded = false;
    const listeners = new Set<() => void>();
    function read() {
        try { return parseMockVisibility(storage()?.getItem(MOCK_VISIBILITY_KEY) ?? null); }
        catch { return true; }
    }
    const emit = () => listeners.forEach(listener => listener());
    return {
        getSnapshot() {
            if (!loaded) { enabled = read(); loaded = true; }
            return enabled;
        },
        subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener); }; },
        setEnabled(value: boolean) {
            enabled = value;
            loaded = true;
            let saved = false;
            try {
                const target = storage();
                if (target) { target.setItem(MOCK_VISIBILITY_KEY, JSON.stringify({ version: 1, enabled })); saved = true; }
            } catch { /* 저장 실패 시 현재 탭에서는 선택을 유지한다. */ }
            emit();
            return saved;
        },
        refresh() { enabled = read(); loaded = true; emit(); },
    };
}

/** 데이터 표시 모드는 API 선택과 무관하다. OFF는 별도 빈 작업 공간을 사용한다. */
export const mockVisibilityStore = createMockVisibilityStore(() => typeof localStorage === 'undefined' ? undefined : localStorage);
export const mockDataEnabled = () => mockVisibilityStore.getSnapshot();
export function demoStorageKey(key: string) { return mockDataEnabled() ? key : `devmozip:empty-mode:v1:${key}`; }
export const readDemoStorage = (key: string) => localStorage.getItem(demoStorageKey(key));
export const writeDemoStorage = (key: string, value: string) => localStorage.setItem(demoStorageKey(key), value);
export function visibleSeeds<T>(values: T[]): T[] { return mockDataEnabled() ? values : []; }
