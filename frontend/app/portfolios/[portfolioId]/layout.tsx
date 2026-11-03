import type { Metadata } from 'next';
import type { ReactNode } from 'react';

export const metadata: Metadata = { title: '포트폴리오 상세' };

export default function Layout({ children }: { children: ReactNode }) {
    return children;
}
