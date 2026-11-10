'use client';
import { useSyncExternalStore } from 'react';
import { mockVisibilityStore, MOCK_VISIBILITY_KEY } from '@/lib/demo/mockVisibility';

import { resetMockStores } from '@/lib/localStore';
const store = mockVisibilityStore;
const serverSnapshot = () => false;
function subscribe(listener: () => void) {
    const unsubscribe = store.subscribe(listener);
    const onStorage = (event: StorageEvent) => {
        if (event.key === MOCK_VISIBILITY_KEY || event.key === null) { store.refresh(); resetMockStores(); }
    };
    window.addEventListener('storage', onStorage);
    return () => { unsubscribe(); window.removeEventListener('storage', onStorage); };
}
export function useMockVisibility() {
    return { enabled: useSyncExternalStore(subscribe, store.getSnapshot, serverSnapshot), setEnabled: (enabled: boolean) => { const saved = store.setEnabled(enabled); resetMockStores(); return saved; } };
}
