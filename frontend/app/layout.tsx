import './globals.css';
import type {Metadata} from 'next';
import {Suspense,type ReactNode} from 'react';
import {ClientProviders} from './ClientProviders';
export const metadata:Metadata={title:{default:'DevMozip · 데브모집',template:'%s · DevMozip'},description:'Mock 데이터로 개발자 추천과 프로젝트 협업을 시연하는 DevMozip 프로토타입'};
export default function RootLayout({children}:{children:ReactNode}){return <html lang="ko" data-scroll-behavior="smooth"><body><Suspense fallback={<div className="client-loading" role="status">화면을 준비하고 있어요…</div>}><ClientProviders>{children}</ClientProviders></Suspense></body></html>;}
