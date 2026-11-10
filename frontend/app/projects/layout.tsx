import type { Metadata } from 'next';
import type { ReactNode } from 'react';

// 프로젝트 화면은 브라우저의 세션·Mock 모드 확인을 기다린 뒤 표시한다.
export const instant = false;

export const metadata: Metadata = { title: '프로젝트 모집' };

export default function Layout({ children }: { children: ReactNode }) {
    return children;
}
