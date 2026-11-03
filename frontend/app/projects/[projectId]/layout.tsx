import type { Metadata } from 'next';
import type { ReactNode } from 'react';

export const metadata: Metadata = { title: '프로젝트 상세' };

export default function Layout({ children }: { children: ReactNode }) {
    return children;
}
