import type { Metadata } from 'next';
import type { ReactNode } from 'react';

export const metadata: Metadata = { title: '내 프로젝트 워크스페이스' };

export default function Layout({ children }: { children: ReactNode }) {
    return children;
}
