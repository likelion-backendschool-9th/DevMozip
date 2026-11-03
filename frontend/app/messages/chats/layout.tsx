import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import { ChatsLayout } from './_components/ChatsLayout';

export const metadata: Metadata = { title: '쪽지' };

export default function Layout({ children }: { children: ReactNode }) {
    return <ChatsLayout>{children}</ChatsLayout>;
}
