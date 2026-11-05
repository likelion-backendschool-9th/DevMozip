import { describe, it, expect, vi } from 'vitest';
import { createMockVisibilityStore, MOCK_VISIBILITY_KEY, parseMockVisibility } from './mockVisibility';

function fixture() {
    const values = new Map<string, string>([['devmozip:profiles:v1', '{"saved":"keep"}'], ['devmozip:recruitment:v1', '{"project":"keep"}']]);
    const storage = { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => { values.set(key, value); } };
    return { values, storage, store: createMockVisibilityStore(() => storage) };
}

describe('Mock 데이터 표시 설정', () => {
    it('기존 사용자는 ON으로 시작하고 버전이 있는 설정만 읽는다', () => {
        for (const raw of [null, 'broken', '{}', '{"version":2,"enabled":false}', '{"version":1,"enabled":"false"}']) expect(parseMockVisibility(raw)).toBe(true);
        expect(parseMockVisibility('{"version":1,"enabled":false}')).toBe(false);
    });
    it('끄기·새로고침·켜기를 거쳐도 데모 데이터와 인증을 변경하지 않는다', () => {
        const { values, storage, store } = fixture();
        values.set('auth-cookie-representation', 'untouched');
        const before = new Map(values);
        expect(store.getSnapshot()).toBe(true);
        expect(store.setEnabled(false)).toBe(true);
        expect(createMockVisibilityStore(() => storage).getSnapshot()).toBe(false);
        expect(store.setEnabled(true)).toBe(true);
        for (const [key,value] of before) expect(values.get(key)).toBe(value);
        expect(values.size).toBe(before.size + 1);
    });
    it('저장 실패 시에도 현재 세션에서 전환하고 실패를 호출자에게 알린다', () => {
        const store = createMockVisibilityStore(() => ({ getItem: () => { throw Error('denied'); }, setItem: () => { throw Error('quota'); } }));
        expect(store.getSnapshot()).toBe(true);
        expect(store.setEnabled(false)).toBe(false);
        expect(store.getSnapshot()).toBe(false);
        expect(store.setEnabled(true)).toBe(false);
        expect(store.getSnapshot()).toBe(true);
    });
    it('외부 탭의 설정 변경·삭제를 반영하고 구독 해제를 지원한다', () => {
        const { values,store } = fixture();
        const listener = vi.fn(); const unsubscribe = store.subscribe(listener);
        values.set(MOCK_VISIBILITY_KEY,'{"version":1,"enabled":false}'); store.refresh();
        expect(store.getSnapshot()).toBe(false); expect(listener).toHaveBeenCalledOnce();
        unsubscribe(); values.delete(MOCK_VISIBILITY_KEY); store.refresh();
        expect(store.getSnapshot()).toBe(true); expect(listener).toHaveBeenCalledOnce();
    });
});
