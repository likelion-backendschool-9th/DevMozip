import { createLocalStore, derived, useStore } from '@/lib/localStore';

// 공통 토스트 메시지.
const toastStore = createLocalStore(() => '');

/** 이 값이 바뀌면 다른 도메인의 계산 값도 다시 만든다(기존 Provider 중첩 순서) */
export const prototypeSources = [toastStore];

export function notify(message: string) {
    toastStore.set(message);
}

const prototype = derived(prototypeSources, () => ({
    toast: toastStore.get(),
    notify,
}));

export function usePrototype() {
    return useStore(prototype);
}
