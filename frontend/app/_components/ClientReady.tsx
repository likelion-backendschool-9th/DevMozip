'use client';
import { useSyncExternalStore, type ReactNode } from 'react';

const subscribe = () => () => {};

// 서버 HTML과 하이드레이션 렌더에서는 대기 문구를, 브라우저에 붙은 뒤에는 localStorage 저장소를 읽는 화면을 그린다.
export function ClientReady({ children }: { children: ReactNode }) {
    const ready = useSyncExternalStore(subscribe, () => true, () => false);
    if (!ready) return <div className="client-loading" role="status">DevMozip 데모 데이터를 준비하고 있어요…</div>;
    return children;
}
