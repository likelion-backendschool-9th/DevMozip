import type { Metadata } from 'next';
import type { ReactNode } from 'react';

export const metadata: Metadata = { title: '개인정보 및 계정 설정' };

export default function Layout({ children }: { children: ReactNode }) {
    return children;
}
