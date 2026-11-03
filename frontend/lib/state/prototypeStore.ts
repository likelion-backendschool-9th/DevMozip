import { createLocalStore, derived, useStore } from '@/lib/localStore';
import { defaultSettings, loadSettings, saveSettings } from '@/lib/settings/settingsService';
import type { PrototypeSettings } from '@/types';

// 발표용 안내 표시 설정과 공통 토스트 메시지.
const settingsStore = createLocalStore(loadSettings);
const toastStore = createLocalStore(() => '');

/** 이 값이 바뀌면 다른 도메인의 계산 값도 다시 만든다(기존 Provider 중첩 순서) */
export const prototypeSources = [settingsStore, toastStore];

export function notify(message: string) {
    toastStore.set(message);
}

function update(next: PrototypeSettings, message: string) {
    settingsStore.set(next);
    notify(saveSettings(next) ? message : `${message} (브라우저 저장 공간을 사용할 수 없어 이번 세션에만 유지됩니다.)`);
}

const prototype = derived(prototypeSources, () => ({
    settings: settingsStore.get(),
    toast: toastStore.get(),
    notify,
    toggleGuide: () => update({ ...settingsStore.get(), showGuide: !settingsStore.get().showGuide }, '안내 표시 설정을 변경했습니다.'),
    reset: () => update({ ...defaultSettings }, '발표용 초기 설정으로 복원했습니다.'),
}));

export function usePrototype() {
    return useStore(prototype);
}
