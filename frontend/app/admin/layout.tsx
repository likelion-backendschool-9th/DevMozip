import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import { AdminShell } from './_components/AdminShell';

export const metadata: Metadata = { title: '관리자' };

export default function Layout({ children }: { children: ReactNode }) {
    return <AdminShell>{children}</AdminShell>;
}
