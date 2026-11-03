import type { Metadata } from 'next';
import type { ReactNode } from 'react';

export const metadata: Metadata = { title: 'AI 팀원 추천' };

export default function Layout({ children }: { children: ReactNode }) {
    return children;
}
