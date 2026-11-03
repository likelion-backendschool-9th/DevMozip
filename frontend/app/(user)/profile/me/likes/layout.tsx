import type { Metadata } from 'next';
import type { ReactNode } from 'react';

export const metadata: Metadata = { title: '좋아요한 글' };

export default function Layout({ children }: { children: ReactNode }) {
    return children;
}
