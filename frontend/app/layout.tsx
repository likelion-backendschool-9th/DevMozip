import './globals.css';
import type { Metadata } from 'next';
import { Suspense, type ReactNode } from 'react';
import { AppLayout } from './_components/AppLayout';
import { ClientReady } from './_components/ClientReady';
import { MessagingSync } from './_components/MessagingSync';
import { ProjectSync } from './_components/ProjectSync';

export const metadata: Metadata = {
    title: {
        default: 'DevMozip · 데브모집',
        template: '%s · DevMozip',
    },
    description: 'Mock 데이터로 개발자 추천과 프로젝트 협업을 시연하는 DevMozip 프로토타입',
};

// 상태는 lib/state의 저장소가 들고 있어 Provider로 감싸지 않는다.
// ProjectSync · MessagingSync는 화면 없이 도메인 간 동기화만 맡는다(백엔드가 붙으면 지운다).
export default function RootLayout({ children }: { children: ReactNode }) {
    return (
        <html lang="ko" data-scroll-behavior="smooth">
            <body>
                <Suspense fallback={<div className="client-loading" role="status">화면을 준비하고 있어요…</div>}>
                    <ClientReady>
                        <ProjectSync />
                        <MessagingSync />
                        <AppLayout>{children}</AppLayout>
                    </ClientReady>
                </Suspense>
            </body>
        </html>
    );
}
